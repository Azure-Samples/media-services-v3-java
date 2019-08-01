// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import org.joda.time.Period;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.eventprocessorhost.EventProcessorHost;
import com.microsoft.azure.eventprocessorhost.EventProcessorOptions;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.Asset;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.IPAccessControl;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.IPRange;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.ListPathsResponse;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEvent;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventEncoding;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventEncodingType;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventInput;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventInputAccessControl;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventInputProtocol;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventPreview;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventPreviewAccessControl;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventResourceState;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveOutput;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.StreamOptionsFlag;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.StreamingEndpoint;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.StreamingEndpointResourceState;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.StreamingLocator;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.StreamingPath;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.implementation.MediaManager;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.LiveEventTranscription;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.rest.LogLevel;
import com.microsoft.azure.management.mediaservices.v2019_05_01_preview.ApiErrorException;

/**
 * Please make sure you have set configuration in resources/conf/appsettings.json
 */
public class LiveEventWithTranscription {
    public static void main(String[] args) {
        ConfigWrapper config = new ConfigWrapper();
        runLiveEvent(config);

        config.close();
        System.exit(0);
    }

    /**
     * Runs the Live Event sample.
     * 
     * @param config This param is of type ConfigWrapper, which reads values from local configuration file.
     */
    private static void runLiveEvent(ConfigWrapper config) {
        // Connect to media services, please see https://docs.microsoft.com/en-us/azure/media-services/latest/configure-connect-java-howto
        // for details.
        ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(config.getAadClientId(),
                config.getAadTenantId(), config.getAadSecret(), AzureEnvironment.AZURE);
        credentials.withDefaultSubscriptionId(config.getSubscriptionId());

        MediaManager manager = MediaManager.configure().withLogLevel(LogLevel.BODY_AND_HEADERS)
                .authenticate(credentials, credentials.defaultSubscriptionId());
        // Signed in.

        // Creating a unique suffix so that we don't have name collisions if you run the sample
        // multiple times without cleaning up.
        UUID uuid = UUID.randomUUID();
        String uniqueness = uuid.toString().substring(0, 13);
        String liveEventName = "liveevent-" + uniqueness;
        String archiveAssetName = "archiveAsset-" + uuid.toString();
        String archiveLiveOutputName = "archiveLiveOutput-" + uuid.toString();
        String streamingLocatorName = "drvLocator-" + uuid.toString();
        String streamingEndpointName = "se";  // Change this to your Streaming Endpoint name.
        EventProcessorHost eventProcessorHost = null;

        Scanner scanner = new Scanner(System.in);

        try {
            // Create a LiveEvent
            System.out.println("Creating a live event named " + liveEventName + ".\n");

            // Note: When creating a LiveEvent, you can specify allowed IP addresses in one of the following formats:                 
            //       IpV4 address with 4 numbers
            //       CIDR address range
            IPRange allAllowIPRange = new IPRange().withName("AllowAll").withAddress("0.0.0.0").withSubnetPrefixLength(0);
            List<IPRange> listIPRanges = new ArrayList<>();
            listIPRanges.add(allAllowIPRange);

            // Create the LiveEvent input IP access control.
            LiveEventInputAccessControl liveEventInputAccess = new LiveEventInputAccessControl();
            liveEventInputAccess.withIp(new IPAccessControl().withAllow(listIPRanges));

            // Create the LiveEvent Preview IP access control
            LiveEventPreview liveEventPreview = new LiveEventPreview();
            liveEventPreview.withAccessControl(new LiveEventPreviewAccessControl().withIp(new IPAccessControl().withAllow(listIPRanges)));

            // Set this to Default or Low Latency
            // When using Low Latency mode, you must configure the Azure Media Player to use the 
            // quick start heuristic profile or you won't notice the change. 
            // In the AMP player client side JS options, set -  heuristicProfile: "Low Latency Heuristic Profile". 
            // To use low latency optimally, you should tune your encoder settings down to 1 second GOP size instead of 2 seconds.
            List<StreamOptionsFlag> streamOptions = new ArrayList<>();
            streamOptions.add(StreamOptionsFlag.LOW_LATENCY);

            // Start monitoring LiveEvent events.
            try {
                System.out.println("Starting monitoring LiveEvent events...");
                String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=" +
                    config.getStorageAccountName() +
                    ";AccountKey=" + config.getStorageAccountKey();

                // Cleanup storage container. We will config Event Hub to use the storage container configured in appsettings.json.
                // All the blobs in <The container configured in appsettings.json>/$Default will be deleted.
                CloudStorageAccount account = CloudStorageAccount.parse(storageConnectionString);
                CloudBlobClient client = account.createCloudBlobClient();
                CloudBlobContainer container = client.getContainerReference(config.getStorageContainerName());
                for (ListBlobItem item : container.listBlobs("$Default/", true)) {
                    if (item instanceof CloudBlob) {
                        CloudBlob blob = (CloudBlob) item;
                        blob.delete();
                    }
                }

                // Create a new host to process events from an Event Hub.
                eventProcessorHost = new EventProcessorHost(
                    EventProcessorHost.createHostName(null),
                    config.getEventHubName(),
                    "$Default",         // DefaultConsumerGroupName, the name of the consumer group to use when receiving from the Event Hub.
                    config.getEventHubConnectionString(),
                    storageConnectionString,
                    config.getStorageContainerName()
                );

                CompletableFuture<Void> registerResult = eventProcessorHost
                    .registerEventProcessorFactory(new MediaServicesEventProcessorFactory(liveEventName), EventProcessorOptions.getDefaultOptions());
                registerResult.get();
            }
            catch (Exception exception)
            {
                System.out.println("Failed to connect to Event Hub, please refer README for Event Hub and storage settings. Skipping event monitoring...");
            }

            List<LiveEventTranscription> transcriptions = new ArrayList<>();
            LiveEventTranscription liveEventTranscription = new LiveEventTranscription()
                .withLanguage("en-US");
            transcriptions.add(liveEventTranscription);

            // When autostart is set to true, the Live Event will be started after creation. 
            // That means, the billing starts as soon as the Live Event starts running. 
            // You must explicitly call Stop on the Live Event resource to halt further billing.
            // The following operation can sometimes take awhile. Please be patient.
            // Set EncodingType to STANDARD to enable a transcoding LiveEvent, and NONE to enable a pass-through LiveEvent
            System.out.println("Creating the LiveEvent, please be patient this can take time...");
            LiveEvent liveEvent = manager.liveEvents().define(liveEventName)
                .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                .withAutoStart(true)
                .withInput(new LiveEventInput().withStreamingProtocol(LiveEventInputProtocol.RTMP).withAccessControl(liveEventInputAccess))
                .withEncoding(new LiveEventEncoding().withEncodingType(LiveEventEncodingType.NONE).withPresetName(null))
                .withLocation(config.getRegion())
                .withVanityUrl(false)
                .withDescription("Sample LiveEvent for testing")
                .withPreview(liveEventPreview)
                .withStreamOptions(streamOptions)
                .withTranscriptions(transcriptions)     // Set transcriptions
                .create();

            // Get the input endpoint to configure the on premise encoder with
            System.out.println();
            String ingestUrl = liveEvent.input().endpoints().get(0).url();
            System.out.println("The ingest url to configure the on premise encoder with is:");
            System.out.println("\t" + ingestUrl);
            System.out.println();

            // Use the previewEndpoint to preview and verify
            // that the input from the encoder is actually being received
            String previewEndpoint = liveEvent.preview().endpoints().get(0).url();
            System.out.println("The preview url is:");
            System.out.println("\t" + previewEndpoint);
            System.out.println();

            System.out.println("Open the live preview in your browser and use the Azure Media Player to monitor the preview playback:");
            System.out.println("\thttps://ampdemo.azureedge.net/?url=" + previewEndpoint + "&heuristicprofile=lowlatency");
            System.out.println();

            System.out.println("Start the live stream now, sending the input to the ingest url and verify that it is arriving with the preview url.");
            System.out.println("IMPORTANT TIP!: Make ABSOLUTELY CERTAIN that the video is flowing to the Preview URL before continuing!");
            System.out.println();
            System.out.println("*********************************");
            System.out.println("* Press enter to continue...    *");
            System.out.println("*********************************");
            System.out.flush();
            scanner.nextLine();
            
            // Create an unique asset for the LiveOutput to use
            System.out.println("Creating an asset named " + archiveAssetName + ".");
            System.out.println();
            Asset fullArchiveAsset = manager.assets().define(archiveAssetName)
                .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                .create();

            String manifestName = "output";
            manager.liveOutputs().define(archiveLiveOutputName)
                .withExistingLiveEvent(config.getResourceGroup(), config.getAccountName(), liveEventName)
                // withArchiveWindowLength: Can be set from 3 minutes to 25 hours. content that falls outside of ArchiveWindowLength
                // is continuously discarded from storage and is non-recoverable. For a full event archive, set to the maximum, 25 hours.
                .withArchiveWindowLength(Period.hours(25))
                .withAssetName(fullArchiveAsset.name())
                .withManifestName(manifestName)
                .withDescription("Sample LiveOutput for testing")
                .create();

            // Create the StreamingLocator
            System.out.println("Creating a streaming locator named " + streamingLocatorName);
            System.out.println();
            
            StreamingLocator streamingLocator = manager.streamingLocators().define(streamingLocatorName)
                .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                .withAssetName(fullArchiveAsset.name())
                .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                .create();

            // Get a Streaming Endpoint on the account, the Streaming Endpoint must exist.
            StreamingEndpoint streamingEndpoint = manager.streamingEndpoints()
                .getAsync(config.getResourceGroup(), config.getAccountName(), streamingEndpointName)
                .toBlocking().first();
            if (streamingEndpoint == null) {
                throw new Exception("Streaming Endpoint " + streamingEndpointName + " does not exist.");
            }

            // If the Streaming Endpoint is not running, start it.
            if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                System.out.println("Streaming Endpoint was Stopped, restarting now...");
                manager.streamingEndpoints()
                    .startAsync(config.getResourceGroup(), config.getAccountName(), streamingEndpointName)
                    .await();
            }

            System.out.println("The urls to stream the LiveEvent from a client:");
            System.out.println();
            
            // Print the urls for the LiveEvent.
            printPaths(config, manager, streamingLocator.name(), streamingEndpoint);

            System.out.println("**********************************************************************************");
            System.out.println("* Continue experimenting with the stream until you are ready to finish.          *");
            System.out.println("* Press ENTER to stop the LiveOutput...                                          *");
            System.out.println("**********************************************************************************");
            System.out.flush();
            scanner.nextLine();

            System.out.println("Stopping the LiveEvent...");
            CleanupLiveEventAndOutput(manager, config.getResourceGroup(), config.getAccountName(), liveEventName);
            System.out.println("The LiveEvent has ended.");
            System.out.println();

            System.out.println("Press ENTER to finish.");
            System.out.println();
            System.out.flush();
            scanner.nextLine();

        } 
        catch (Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof AuthenticationException) {
                    System.out.println("ERROR: Authentication error, please check your account settings in appsettings.json.");
                    break;
                }
                else if (cause instanceof ApiErrorException) {
                    ApiErrorException apiException = (ApiErrorException) cause;
                    System.out.println("ERROR: " + apiException.body().error().message());
                    break;
                }
                cause = cause.getCause();
            }
            System.out.println();
            e.printStackTrace();
            System.out.println();
        } finally {
            if (scanner != null) {
                scanner.close();
            }

            if (eventProcessorHost != null) {
                eventProcessorHost.unregisterEventProcessor();
                eventProcessorHost = null;
            }
        }
    }

    /**
     * Build and print streaming URLs.
     * @param config                The configuration.
     * @param manager               The entry point of Azure Media resource management.
     * @param streamingLocatorName  The locator name.
     * @param streamingEndpoint     The streaming endpoint.
     */
    private static void printPaths(ConfigWrapper config, MediaManager manager, String streamingLocatorName,
            StreamingEndpoint streamingEndpoint) {
        ListPathsResponse paths = manager.streamingLocators()
                .listPathsAsync(config.getResourceGroup(), config.getAccountName(), streamingLocatorName)
                .toBlocking().first();

        StringBuilder stringBuilder = new StringBuilder();
        for (StreamingPath streamingPath : paths.streamingPaths()) {
            if (streamingPath.paths().size() > 0) {
                stringBuilder.append(
                        "\t" + streamingPath.streamingProtocol() + "-" + streamingPath.encryptionScheme() + "\n");
                String strStreamingUlr = "https://" + streamingEndpoint.hostName() + "/" + streamingPath.paths().get(0);
                stringBuilder.append("\t\t" + strStreamingUlr + "\n");
            }
        }

        if (stringBuilder.length() > 0) {
            System.out.println(stringBuilder.toString());

            System.out.println("Open Azure Media Player at https://ampdemo.azureedge.net/");
            System.out.println("In the Setup tab, copy/paste one of the above urls to the URL field and check Advanced Options.");
            System.out.println("Then click Add Captions Track, type en-US in Caption Locale TextBox and click Update Player.");
            System.out.println("Make sure you have turned on the Closed Captioning in the Media Player.");
            System.out.println();
        }
        else {
            System.out.println("No Streaming Paths were detected.  Has the Stream been started?");
        }
    }

    /**
     * Cleanup LiveEvent
     * @param manager               The entry point of Azure Media resource management
     * @param resourceGroupName     The name of the resource group within the Azure subscription
     * @param accountName           The Media Services account name
     * @param liveEventName         The name of the LiveEvent
     * @param liveOutputName        The LiveOutput name
     */
    private static void CleanupLiveEventAndOutput(MediaManager manager, String resourceGroup, String accountName, String liveEventName) {
        // Cleanup LiveOutput first
        Iterator<LiveOutput> iter = manager.liveOutputs().listAsync(resourceGroup, accountName, liveEventName).toBlocking().getIterator();
        while (iter.hasNext()) {
            LiveOutput liveOutput = iter.next();
            manager.liveOutputs().deleteAsync(resourceGroup, accountName, liveEventName, liveOutput.name()).await();
        }

        LiveEvent liveEvent = manager.liveEvents().getAsync(resourceGroup, accountName, liveEventName).toBlocking().first();
        if (liveEvent != null) {
            if (liveEvent.resourceState() == LiveEventResourceState.RUNNING) {
                manager.liveEvents().stopAsync(resourceGroup, accountName, liveEventName).await();
            }
            manager.liveEvents().deleteAsync(resourceGroup, accountName, liveEventName).await();
        }
    }
}
