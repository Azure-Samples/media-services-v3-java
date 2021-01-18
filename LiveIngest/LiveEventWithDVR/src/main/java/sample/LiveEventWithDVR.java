// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.ListBlobsOptions;
import org.joda.time.Period;

import java.util.*;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.management.mediaservices.v2020_05_01.Asset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.AssetFilter;
import com.microsoft.azure.management.mediaservices.v2020_05_01.IPAccessControl;
import com.microsoft.azure.management.mediaservices.v2020_05_01.IPRange;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ListPathsResponse;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEvent;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEventEncoding;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEventEncodingType;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEventInput;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEventInputAccessControl;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEventInputProtocol;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEventPreview;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEventPreviewAccessControl;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveEventResourceState;
import com.microsoft.azure.management.mediaservices.v2020_05_01.LiveOutput;
import com.microsoft.azure.management.mediaservices.v2020_05_01.PresentationTimeRange;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamOptionsFlag;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingEndpoint;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingEndpointResourceState;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingLocator;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingPath;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingPolicyStreamingProtocol;
import com.microsoft.azure.management.mediaservices.v2020_05_01.implementation.MediaManager;
import com.microsoft.rest.LogLevel;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ApiErrorException;

public class LiveEventWithDVR {
    public static void main(String[] args) {
        // Please make sure you have set configuration in resources/conf/appsettings.json.
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
        String fullArchiveAssetName = "fullArchiveAsset-" + uuid.toString();
        String fullArchiveLiveOutputName = "fullArchiveLiveOutput-" + uuid.toString();
        String dvrStreamingLocatorName = "drvLocator-" + uuid.toString();
        String fullArchiveStreamingLocator = "fullLocator-" + uuid.toString();
        String drvAssetFilterName = "filter-" + uniqueness;
        String streamingEndpointName = "se";  // Change this to your Streaming Endpoint name.
        MediaServicesEventProcessor eventProcessorHost = null;
        boolean stopEndpoint = false;

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
                        ";AccountKey=" + config.getStorageAccountKey() + ";EndpointSuffix=core.windows.net";

                // Cleanup storage container. We will config Event Hub to use the storage container configured in appsettings.json.
                // All the blobs in <The container configured in appsettings.json>/$Default will be deleted.
                BlobServiceAsyncClient client = new BlobServiceClientBuilder()
                        .connectionString(storageConnectionString)
                        .buildAsyncClient();
                BlobContainerAsyncClient container = client.getBlobContainerAsyncClient(config.getStorageContainerName());
                container.listBlobs(
                        new ListBlobsOptions().setPrefix("$Default/"))
                        .subscribe(blobItem -> {
                            container.getBlobAsyncClient(blobItem.getName()).delete();
                        });

                // Create a new host to process events from an Event Hub.
                eventProcessorHost = new MediaServicesEventProcessor(null, null, liveEventName,
                        config.getEventHubConnectionString(), config.getEventHubName(),
                        container);

            } catch (Exception exception) {
                System.out.println("Failed to connect to Event Hub, please refer README for Event Hub and storage settings. Skipping event monitoring...");
                System.out.println(exception.getMessage());
            }

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
                    .withLocation(config.getRegion())
                    .withEncoding(new LiveEventEncoding().withEncodingType(LiveEventEncodingType.NONE).withPresetName(null))
                    .withUseStaticHostname(false)
                    .withDescription("Sample LiveEvent for testing")
                    .withPreview(liveEventPreview)
                    .withStreamOptions(streamOptions)
                    .create();

            // Get the input endpoint to configure the on premise encoder with
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
            System.out.println("Creating an asset named " + fullArchiveAssetName + ".");
            System.out.println();
            Asset fullArchiveAsset = manager.assets().define(fullArchiveAssetName)
                    .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                    .create();

            // Create an AssetFilter for StreamingLocator
            AssetFilter drvAssetFilter = manager.assetFilters().define(drvAssetFilterName)
                    .withExistingAsset(config.getResourceGroup(), config.getAccountName(), fullArchiveAssetName)
                    .withPresentationTimeRange(new PresentationTimeRange()
                            .withForceEndTimestamp(false)
                            // 300 seconds sliding window
                            .withPresentationWindowDuration(3000000000L)
                            // This value defines the latest live position that a client can seek back to 10 seconds, must be smaller than sliding window.
                            .withLiveBackoffDuration(100000000L))
                    .create();

            String manifestName = "output";
            manager.liveOutputs().define(fullArchiveLiveOutputName)
                    .withExistingLiveEvent(config.getResourceGroup(), config.getAccountName(), liveEventName)
                    // withArchiveWindowLength: Can be set from 3 minutes to 25 hours. content that falls outside of ArchiveWindowLength
                    // is continuously discarded from storage and is non-recoverable. For a full event archive, set to the maximum, 25 hours.
                    .withArchiveWindowLength(Period.hours(25))
                    .withAssetName(fullArchiveAsset.name())
                    .withManifestName(manifestName)
                    .withDescription("Sample LiveOutput for testing")
                    .create();

            // Create the StreamingLocator
            System.out.println("Creating a streaming locator named " + dvrStreamingLocatorName);
            System.out.println();

            List<String> assetFilters = new ArrayList<>();
            assetFilters.add(drvAssetFilter.name());
            StreamingLocator streamingLocator = manager.streamingLocators().define(dvrStreamingLocatorName)
                    .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                    .withAssetName(fullArchiveAsset.name())
                    .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                    .withFilters(assetFilters)  // Associate filters with StreamingLocator
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

                // Since we started the endpoint, we should stop it in cleanup.
                stopEndpoint = true;
            }

            System.out.println("The urls to stream the LiveEvent from a client:");
            System.out.println();

            // Print the urls for the LiveEvent.
            printPaths(config, manager, streamingLocator.name(), streamingEndpoint);

            System.out.println("**********************************************************************************");
            System.out.println("* If you see an error in Azure Media Player, wait a few moments and try again.   *");
            System.out.println("* Continue experimenting with the stream until you are ready to finish.          *");
            System.out.println("* Press ENTER to stop the LiveOutput...                                          *");
            System.out.println("**********************************************************************************");
            System.out.flush();
            scanner.nextLine();

            System.out.println("Cleaning up LiveEvent and output...");
            CleanupLiveEventAndOutput(manager, config.getResourceGroup(), config.getAccountName(), liveEventName);
            System.out.println("The LiveEvent has ended.");
            System.out.println();

            // If we started the endpoint, we'll stop it. Otherwise, we'll keep the endpoint running and print urls
            // that can be played even after this sample ends.
            if (!stopEndpoint) {
                // Create a StreamingLocator for the full archive.
                StreamingLocator fullStreamingLocator = manager.streamingLocators().define(fullArchiveStreamingLocator)
                        .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                        .withAssetName(fullArchiveAsset.name())
                        .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                        .create();

                System.out.println("To playback the full event from a client, Use the following urls:");
                System.out.println();

                // Print urls for the full archive.
                printPaths(config, manager, fullStreamingLocator.name(), streamingEndpoint);
                System.out.println("Press ENTER to finish.");
                System.out.println();
                System.out.flush();
                scanner.nextLine();
            }
        } catch (Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof AuthenticationException) {
                    System.out.println("ERROR: Authentication error, please check your account settings in appsettings.json.");
                    break;
                } else if (cause instanceof ApiErrorException) {
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
            CleanupLiveEventAndOutput(manager, config.getResourceGroup(), config.getAccountName(), liveEventName);
            cleanupLocator(manager, config.getResourceGroup(), config.getAccountName(), dvrStreamingLocatorName);

            if (stopEndpoint) {
                // Because we started the endpoint, we'll stop it.
                manager.streamingEndpoints().stopAsync(config.getResourceGroup(), config.getAccountName(), streamingEndpointName).await();
            } else {
                // We will keep the endpoint running because it was not started by us. There are costs to keep it running.
                // Please refer https://azure.microsoft.com/en-us/pricing/details/media-services/ for pricing.
                System.out.println("The endpoint " + streamingEndpointName + "is running. To halt further billing on the endpoint, please stop it in azure portal or AMS Explorer.");
            }

            if (scanner != null) {
                scanner.close();
            }

            if (eventProcessorHost != null) {
                eventProcessorHost.stop();
                eventProcessorHost = null;
            }
        }
    }

    /**
     * Build and print streaming URLs.
     *
     * @param config               The configuration.
     * @param manager              The entry point of Azure Media resource management.
     * @param streamingLocatorName The locator name.
     * @param streamingEndpoint    The streaming endpoint.
     */
    private static void printPaths(ConfigWrapper config, MediaManager manager, String streamingLocatorName,
                                   StreamingEndpoint streamingEndpoint) {
        ListPathsResponse paths = manager.streamingLocators()
                .listPathsAsync(config.getResourceGroup(), config.getAccountName(), streamingLocatorName)
                .toBlocking().first();

        StringBuilder stringBuilder = new StringBuilder();
        String playerPath = "";
        for (StreamingPath streamingPath : paths.streamingPaths()) {
            if (streamingPath.paths().size() > 0) {
                stringBuilder.append(
                        "\t" + streamingPath.streamingProtocol() + "-" + streamingPath.encryptionScheme() + "\n");
                String strStreamingUlr = "https://" + streamingEndpoint.hostName() + "/" + streamingPath.paths().get(0);
                stringBuilder.append("\t\t" + strStreamingUlr + "\n");

                if (streamingPath.streamingProtocol() == StreamingPolicyStreamingProtocol.DASH) {
                    playerPath = strStreamingUlr;
                }
            }
        }

        if (stringBuilder.length() > 0) {
            System.out.println(stringBuilder.toString());

            System.out.println("Open the following URL to playback in the Azure Media Player");
            System.out.println("\t https://ampdemo.azureedge.net/?url=" + playerPath + "&heuristicprofile=lowlatency");
            System.out.println();
        } else {
            System.out.println("No Streaming Paths were detected.  Has the Stream been started?");
        }
    }

    /**
     * Cleanup LiveEvent
     *
     * @param manager       The entry point of Azure Media resource management
     * @param resourceGroup The name of the resource group within the Azure subscription
     * @param accountName   The Media Services account name
     * @param liveEventName The name of the LiveEvent
     */
    private static void CleanupLiveEventAndOutput(MediaManager manager, String resourceGroup, String accountName, String liveEventName) {
        LiveEvent liveEvent;
        try {
            liveEvent = manager.liveEvents().getAsync(resourceGroup, accountName, liveEventName).toBlocking().first();
        } catch (NoSuchElementException e) {
            liveEvent = null;
        }
        if (liveEvent == null) {
            return;
        }

        // Cleanup LiveOutput first
        Iterator<LiveOutput> iter = manager.liveOutputs().listAsync(resourceGroup, accountName, liveEventName).toBlocking().getIterator();
        iter.forEachRemaining(liveOutput -> manager.liveOutputs().deleteAsync(resourceGroup, accountName, liveEventName, liveOutput.name()).await());

        if (liveEvent.resourceState() == LiveEventResourceState.RUNNING) {
            manager.liveEvents().stopAsync(resourceGroup, accountName, liveEventName).await();
        }
        manager.liveEvents().deleteAsync(resourceGroup, accountName, liveEventName).await();
    }

    private static void cleanupLocator(MediaManager manager, String resourceGroup, String accountName, String streamingLocatorName) {
        try {
            manager.streamingLocators().deleteAsync(resourceGroup, accountName, streamingLocatorName).await();
        } catch (ApiErrorException e) {
            System.out.println("ApiErrorException");
            System.out.println("\tCode: " + e.body().error().code());
            System.out.println("\tMessage: " + e.body().error().message());
        }
    }
}
