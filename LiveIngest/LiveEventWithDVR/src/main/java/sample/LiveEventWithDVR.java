// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import org.joda.time.Period;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Asset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AssetFilter;
import com.microsoft.azure.management.mediaservices.v2018_07_01.IPAccessControl;
import com.microsoft.azure.management.mediaservices.v2018_07_01.IPRange;
import com.microsoft.azure.management.mediaservices.v2018_07_01.ListPathsResponse;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEvent;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEventEncoding;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEventEncodingType;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEventInput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEventInputAccessControl;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEventInputProtocol;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEventPreview;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEventPreviewAccessControl;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveEventResourceState;
import com.microsoft.azure.management.mediaservices.v2018_07_01.LiveOutput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.PresentationTimeRange;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamOptionsFlag;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingEndpoint;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingEndpointResourceState;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingLocator;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingPath;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingPolicyStreamingProtocol;
import com.microsoft.azure.management.mediaservices.v2018_07_01.implementation.MediaManager;
import com.microsoft.rest.LogLevel;

/**
 * Please make sure you have set configuration in resources/conf/appsettings.json
 */
public class LiveEventWithDVR {
    public static void main(String[] args) {
        ConfigWrapper config = new ConfigWrapper();
        runLiveEvent(config);

        config.close();
        System.exit(0);
    }

    /**
     * Runs the Live Event sample.
     * 
     * @param config The parm is of type ConfigWrapper. This class reads values from local configuration file.
     * @since 05/22/2019
     */
    private static void runLiveEvent(ConfigWrapper config) {
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

            // When autostart is set to true, the Live Event will be started after creation. 
            // That means, the billing starts as soon as the Live Event starts running. 
            // You must explicitly call Stop on the Live Event resource to halt further billing.
            // The following operation can sometimes take awhile. Please be patient.
            // Set EncodingType to STANDARD to enable a transcoding LiveEvent, and NONE to enable a pass-through LiveEvent
            System.out.println("Creating the LiveEvent, please be patient this can take time...");
            LiveEvent liveEvent = manager.liveEvents().define(liveEventName)
                .withExistingMediaservice(config.getResourceGroup(), config.getAccountName()).withAutoStart(true)
                .withInput(new LiveEventInput().withStreamingProtocol(LiveEventInputProtocol.RTMP).withAccessControl(liveEventInputAccess))
                .withEncoding(new LiveEventEncoding().withEncodingType(LiveEventEncodingType.NONE).withPresetName(null))
                .withLocation(config.getRegion())
                .withVanityUrl(false)
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
            System.out.println("Press enter to continue...");
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
                    // This value defines the latest live position that a client can seek back to 30 seconds, must be smaller than sliding window.
                    .withLiveBackoffDuration(300000000L))
                .create();

            String manifestName = "output";
            LiveOutput fullArchiveLiveOutput = manager.liveOutputs().define(fullArchiveLiveOutputName)
                .withExistingLiveEvent(config.getResourceGroup(), config.getAccountName(), liveEventName)
                // withArchiveWindowLength: Can be set from 3 minutes to 25 hours. content that falls outside of ArchiveWindowLength
                // is continuously discarded from storage and is non-recoverable. For a full event archive, set to the maximum, 25 hours.
                .withArchiveWindowLength(Period.hours(25))
                .withAssetName(fullArchiveAsset.name())
                .withManifestName(manifestName)
                .create();

            // Create the StreamingLocator
            System.out.println("Creating a streaming locator named " + dvrStreamingLocatorName);
            System.out.println();
            
            List<String> assetFilters = new ArrayList<>();
            assetFilters.add(drvAssetFilterName);
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
            }

            System.out.println("The urls to stream the LiveEvent from a client:");
            System.out.println();
            
            // Print the urls for the LiveEvent.
            printPaths(config, manager, dvrStreamingLocatorName, streamingEndpoint);

            System.out.println("If you see an error in Azure Media Player, wait a few moments and try the url again.");
            System.out.println("Continue experimenting with the stream until you are ready to finish.");
            System.out.println("Press ENTER to stop the LiveOutput...");
            System.out.flush();
            scanner.nextLine();

            System.out.println("Stopping the LiveEvent...");
            CleanupLiveEventAndOutput(manager, config.getResourceGroup(), config.getAccountName(), liveEventName);
            System.out.println("The LiveEvent has ended.");
            System.out.println();

            // Create a StreamingLocator for the full archive.
            StreamingLocator fullStreamingLocator = manager.streamingLocators().define(fullArchiveStreamingLocator)
                .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                .withAssetName(fullArchiveAsset.name())
                .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                .create();
            
            System.out.println("To playback the full event from a client, Use the following urls:");
            System.out.println();

            // Print urls for the full archive.
            printPaths(config, manager, fullArchiveStreamingLocator, streamingEndpoint);
            System.out.println("Press ENTER to finish.");
            System.out.println();
            System.out.flush();
            scanner.nextLine();

        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * Build and print streaming URLs.
     * @param config                The configuration.
     * @param manager               The entry poiint of Azure Media resource management.
     * @param streamingLocatorName  The locator name.
     * @param streamingEndpoint     The streaming endpoint.
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
        }
    }

    /**
     * Cleanup LiveEvent
     * @param manager               The entry poiint of Azure Media resource management
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
