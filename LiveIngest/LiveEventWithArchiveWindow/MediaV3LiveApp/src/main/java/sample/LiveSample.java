package sample;

import org.joda.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Asset;
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
public class LiveSample {
    public static void main(String[] args) {
        ConfigWrapper config = new ConfigWrapper();
        runSample(config);

        config.close();
        System.exit(0);
    }

    /**
     * Runs the Live Event sample.
     * 
     * @param config The parm is of type ConfigWrapper. This class reads values from local configuration file.
     * @since 05/22/2019
     */
    private static void runSample(ConfigWrapper config) {
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
        String assetName = "archiveAsset" + uuid.toString();
        String liveOutputName = "liveOutput" + uuid.toString();
        String streamingLocatorName = "streamingLocator" + uuid.toString();
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
            // quick start hueristic profile or you won't notice the change. 
            // In the AMP player client side JS options, set -  heuristicProfile: "Low Latency Heuristic Profile". 
            // To use low latency optimally, you should tune your encoder settings down to 1 second GOP size instead of 2 seconds.
            List<StreamOptionsFlag> streamOptions = new ArrayList<>();
            streamOptions.add(StreamOptionsFlag.LOW_LATENCY);

            // When autostart is set to true, the Live Event will be started after creation. 
            // That means, the billing starts as soon as the Live Event starts running. 
            // You must explicitly call Stop on the Live Event resource to halt further billing.
            // The following operation can sometimes take awhile. Be patient.
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
            System.out.println("IMPORTANT TIP!: Make ABSOLUTLEY CERTAIN that the video is flowing to the Preview URL before continuing!");
            System.out.println("Press enter to continue...");
            System.out.flush();
            scanner.nextLine();

            // Create an unique asset for the LiveOutput to use
            System.out.println("Creating an asset named " + assetName);
            System.out.println();
            //Asset asset = manager.assets().getAsync(config.getResourceGroup(), config.getAccountName(), assetName).toBlocking().first();
            //if (asset != null) {
                // This should not happen
            //    throw new Exception("Asset " + assetName + " already exists.");
            //}
            Asset asset = manager.assets().define(assetName).withExistingMediaservice(config.getResourceGroup(), config.getAccountName()).create();

            // Create the LiveOutput
            String manifestName = "output";
            System.out.println("Creating a live output named " + liveOutputName);
            System.out.println();

            LiveOutput liveOutput = manager.liveOutputs().define(liveOutputName)
                .withExistingLiveEvent(config.getResourceGroup(), config.getAccountName(), liveEventName)
                .withArchiveWindowLength(Period.minutes(10))
                .withAssetName(asset.name())
                .withManifestName(manifestName)
                .create();


            // Create the StreamingLocator
            System.out.println("Creating a streaming locator named " + streamingLocatorName);
            System.out.println();
            
            StreamingLocator streamingLocator = manager.streamingLocators().define(streamingLocatorName)
                .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                .withAssetName(asset.name())
                .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                .create();

            // Get a Streaming Endpoint on the account, the streaming endpoint must exist.
            StreamingEndpoint streamingEndpoint = manager.streamingEndpoints()
                .getAsync(config.getResourceGroup(), config.getAccountName(), streamingEndpointName)
                .toBlocking().last();
            if (streamingEndpoint == null) {
                throw new Exception("Streaming Endpoint " + streamingEndpointName + " does not exist.");
            }

            // If it's not running, Start it.
            if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                System.out.println("Streaming Endpoint was Stopped, restarting now...");
                manager.streamingEndpoints()
                    .startAsync(config.getResourceGroup(), config.getAccountName(), streamingEndpointName)
                    .await();
            }

            // Get the url to stream the output
            ListPathsResponse paths = manager.streamingLocators()
                .listPathsAsync(config.getResourceGroup(), config.getAccountName(), streamingLocatorName)
                .toBlocking().last();
            System.out.println("The urls to stream the output from a client:");
            System.out.println();
            StringBuilder stringBuilder = new StringBuilder();
            String playerPath = "";
            for (StreamingPath streamingPath: paths.streamingPaths()) {
                if (streamingPath.paths().size() > 0) {
                    stringBuilder.append("\t" + streamingPath.streamingProtocol() + "-" + streamingPath.encryptionScheme() + "\n");
                    String strStreamingUlr = "https://" + streamingEndpoint.hostName() + "/" + streamingPath.paths().get(0);
                    stringBuilder.append("\t\t" + strStreamingUlr + "\n");

                    if (streamingPath.streamingProtocol() == StreamingPolicyStreamingProtocol.DASH) {
                        playerPath = strStreamingUlr;
                    }
                }
            }

            if (stringBuilder.length() > 0) {
                System.out.println(stringBuilder.toString());
                System.out.println("Open the following URL to playback the published,recording LiveOutput in the Azure Media Player");
                System.out.println("\t https://ampdemo.azureedge.net/?url=" + playerPath + "&heuristicprofile=lowlatency");
                System.out.println();

                System.out.println("Continue experimenting with the stream until you are ready to finish.");
                System.out.println("Press enter to stop the LiveOutput...");
                System.out.flush();
                scanner.nextLine();
            }
        }
        catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
        finally {
            System.out.println("Cleaning up...");
            CleanupLiveEventAndOutput(manager, config.getResourceGroup(), config.getAccountName(), liveEventName, liveOutputName);
            CleanupLocatorandAsset(manager, config.getResourceGroup(), config.getAccountName(), streamingLocatorName, assetName);
            if (scanner != null) {
                scanner.close();
            }
        }
    }

        /**
     * Cleanup 
     * @param manager               The entry poiint of Azure Media resource management
     * @param resourceGroupName     The name of the resource group within the Azure subscription
     * @param accountName           The Media Services account name
     * @param liveEventName         The name of the LiveEvent
     * @param liveOutputName        The LiveOutput name
     */
    private static void CleanupLiveEventAndOutput(MediaManager manager, String resourceGroup, String accountName, String liveEventName, String liveOutputName) {
        // Cleanup LiveOutput first
        LiveOutput liveOutput = manager.liveOutputs().getAsync(resourceGroup, accountName, liveEventName, liveOutputName).toBlocking().last();
        if (liveOutput != null) {
            manager.liveOutputs().deleteAsync(resourceGroup, accountName, liveEventName, liveOutputName).await();
        }

        LiveEvent liveEvent = manager.liveEvents().getAsync(resourceGroup, accountName, liveEventName).toBlocking().last();
        if (liveEvent != null) {
            if (liveEvent.resourceState() == LiveEventResourceState.RUNNING) {
                manager.liveEvents().stopAsync(resourceGroup, accountName, liveEventName).await();
            }
            manager.liveEvents().deleteAsync(resourceGroup, accountName, liveEventName).await();
        }
    }

    /**
     * Cleanup 
     * @param manager               The entry poiint of Azure Media resource management
     * @param resourceGroupName     The name of the resource group within the Azure subscription
     * @param accountName           The Media Services account name
     * @param streamingLocatorName  The streaming locator name
     * @param assetName             The asset name
     */
    private static void CleanupLocatorandAsset(MediaManager manager, String resourceGroup, String accountName, String streamingLocatorName, String assetName) {
        manager.streamingLocators().deleteAsync(resourceGroup, accountName, streamingLocatorName).await();
        manager.assets().deleteAsync(resourceGroup, accountName, assetName).await();
    }
}