// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AccountFilter;
import com.microsoft.azure.management.mediaservices.v2018_07_01.ApiErrorException;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Asset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AssetContainerPermission;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AssetContainerSas;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AssetFilter;
import com.microsoft.azure.management.mediaservices.v2018_07_01.BuiltInStandardEncoderPreset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.EncoderNamedPreset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.FilterTrackPropertyCompareOperation;
import com.microsoft.azure.management.mediaservices.v2018_07_01.FilterTrackPropertyCondition;
import com.microsoft.azure.management.mediaservices.v2018_07_01.FilterTrackPropertyType;
import com.microsoft.azure.management.mediaservices.v2018_07_01.FilterTrackSelection;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Job;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobInput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobInputAsset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobOutput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobOutputAsset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobState;
import com.microsoft.azure.management.mediaservices.v2018_07_01.ListContainerSasInput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.ListPathsResponse;
import com.microsoft.azure.management.mediaservices.v2018_07_01.PresentationTimeRange;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingEndpoint;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingEndpointResourceState;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingLocator;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingPath;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingPolicyStreamingProtocol;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Transform;
import com.microsoft.azure.management.mediaservices.v2018_07_01.TransformOutput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.implementation.MediaManager;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.rest.LogLevel;

import org.joda.time.DateTime;

public class AssetFilters {
    private static final String ADAPTIVE_STREAMING_TRANSFORM_NAME = "MyTransformWithAdaptiveStreamingPreset";
    private static final String INPUT_MP4_RESOURCE = "video/ignite.mp4";

    // Please change this to your endpoint name
    private static final String STREAMING_ENDPOINT_NAME = "se";

    public static void main(String[] args) {
        // Please make sure you have set configuration in resources/conf/appsettings.json. For more information, see
        // https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to.
        ConfigWrapper config = new ConfigWrapper();
        runAssetFiltersSample(config);

        config.close();
        System.exit(0);
    }

    /**
     * Run the sample.
     * @param config This param is of type ConfigWrapper. This class reads values from local configuration file.
     */
    private static void runAssetFiltersSample(ConfigWrapper config) {
        // Connect to media services, please see https://docs.microsoft.com/en-us/azure/media-services/latest/configure-connect-java-howto
        // for details.
        ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(config.getAadClientId(),
                config.getAadTenantId(), config.getAadSecret(), AzureEnvironment.AZURE);
        credentials.withDefaultSubscriptionId(config.getSubscriptionId());

        // MediaManager is the entry point to Azure Media resource management.
        MediaManager manager = MediaManager.configure().withLogLevel(LogLevel.BODY_AND_HEADERS)
                .authenticate(credentials, credentials.defaultSubscriptionId());
        // Signed in.

        // Creating a unique suffix so that we don't have name collisions if you run the sample
        // multiple times without cleaning up.
        UUID uuid = UUID.randomUUID();
        String uniqueness = uuid.toString();
        String jobName = "job-" + uniqueness.substring(0, 13);
        String locatorName = "locator-" + uniqueness;
        String outputAssetName = "output-" + uniqueness;
        String assetFilterName = "assetFilter-" + uniqueness;
        String accountFilterName = "accountFilter-" + uniqueness;
        String inputAssetName = "input-" + uniqueness;
        boolean stopEndpoint = false;

        Scanner scanner = new Scanner(System.in);
        try {
            // Ensure that you have customized encoding Transform with builtin preset. This is really a one time
            // setup operation.
            Transform transform = ensureTransformExists(manager, config.getResourceGroup(),
                    config.getAccountName(), ADAPTIVE_STREAMING_TRANSFORM_NAME);

            // Create a new input Asset and upload the specified local video file into it.
            Asset inputAsset = createInputAssetAndUploadVideo(manager, config.getResourceGroup(), config.getAccountName(), inputAssetName,
                INPUT_MP4_RESOURCE);

            // Output from the encoding Job must be written to an Asset, so let's create one. Note that we
            // are using a unique asset name, there should not be a name collision.
            Asset outputAsset = createAsset(manager, config.getResourceGroup(), config.getAccountName(),
                    outputAssetName);

            Job job = submitJob(manager, config.getResourceGroup(), config.getAccountName(), transform.name(), jobName,
            inputAsset.name(), outputAsset.name());

            long startedTime = System.currentTimeMillis();
            // In this demo code, we will poll for Job status.
            // Polling is not a recommended best practice for production applications because of the latency it introduces.
            // Overuse of this API may trigger throttling. Developers should instead use Event Grid.
            job = waitForJobToFinish(manager, config.getResourceGroup(), config.getAccountName(), transform.name(),
                    jobName);

            long elapsed = (System.currentTimeMillis() - startedTime) / 1000; // Elapsed time in seconds
            System.out.println("Job elapsed time: " + elapsed + " second(s).");

            if (job.state() == JobState.FINISHED) {
                System.out.println("Job finished.");
                System.out.println();

                // Now that the content has been encoded, publish it for Streaming by creating
                // a StreamingLocator. 
                System.out.println("Creating a streaming locator...\n");
                StreamingLocator locator = manager.streamingLocators().define(locatorName)
                    .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                    .withAssetName(outputAssetName)
                    .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                    .create();

                StreamingEndpoint streamingEndpoint = manager.streamingEndpoints()
                    .getAsync(config.getResourceGroup(), config.getAccountName(), STREAMING_ENDPOINT_NAME)
                    .toBlocking().first();

                if (streamingEndpoint != null) {
                    // Start The Streaming Endpoint if it is not running.
                    if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                        manager.streamingEndpoints().startAsync(config.getResourceGroup(), config.getAccountName(), STREAMING_ENDPOINT_NAME).await();

                        // We started the endpoint, we should stop it in cleanup.
                        stopEndpoint = true;
                    }
                }

                List<String> urls = getDashStreamingUrls(manager, config.getResourceGroup(), config.getAccountName(), locator.name(), streamingEndpoint);

                // Create an asset filter.
                // startTimestamp = 100000000 and endTimestamp = 300000000 using the default timescale will generate
                // a playlist that contains fragments from between 10 seconds and 30 seconds of the VoD presentation.
                // If a fragment straddles the boundary, the entire fragment will be included in the manifest.
                System.out.println("Creating an asset filter...\n");
                AssetFilter assetFilter = manager.assetFilters()
                    .define(assetFilterName)
                    .withExistingAsset(config.getResourceGroup(), config.getAccountName(), outputAsset.name())
                    .withPresentationTimeRange(new PresentationTimeRange()
                        .withStartTimestamp(100000000L)     // Starts at 10 seconds.
                        .withEndTimestamp(300000000L))      // Ends at 30 seconds.
                    .create();

                // Create an AccountFilter
                System.out.println("Creating an account filter...\n");
                AccountFilter accountFilter = createAccountFilter(manager, config.getResourceGroup(), config.getAccountName(), accountFilterName);

                System.out.println("We are going to use two different ways to show how to filter content. First, we will append the filters to the url(s).");
                System.out.println("Url(s) with filters:");
                for (String url: urls) {
                    if (url.endsWith(")")) {
                        System.out.println(url.replaceFirst("\\)$", ",filter="  + assetFilter.name() + ";" + accountFilter.name() + ")"));
                    }
                    else {
                        System.out.println(url + "(filter=" + assetFilter.name() + ";" + accountFilter.name() + ")");
                    }
                }

                System.out.println();
                System.out.println("Copy and paste the streaming URL into the Azure Media Player at 'http://aka.ms/azuremediaplayer'.");
                System.out.println("Please note that we have used two filters in the url(s), one trimmed the start and the end of the media ");
                System.out.println("and the other removed high resolution video tracks. To stream the original content, remove the filters ");
                System.out.println("from the url(s) and update player.");
                System.out.println("When finished, press ENTER to continue.");
                System.out.flush();
                scanner.nextLine();

                // Create a new StreamingLocator and associate filters with it.
                System.out.println("Next, we will associate the filters with a new streaming locator.");
                manager.streamingLocators().deleteAsync(config.getResourceGroup(), config.getAccountName(), locatorName).await(); // Delete the old streaming locator.
                List<String> filters = new ArrayList<>();
                filters.add(assetFilter.name());
                filters.add(accountFilter.name());
                System.out.println("Creating a new streaming locator...");
                locator = manager.streamingLocators().define(locatorName)
                    .withExistingMediaservice(config.getResourceGroup(), config.getAccountName())
                    .withAssetName(outputAssetName)
                    .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                    .withFilters(filters)           // Associate filters
                    .create();

                urls = getDashStreamingUrls(manager, config.getResourceGroup(), config.getAccountName(), locator.name(), streamingEndpoint);
                System.out.println("Since we have associated filters with the new streaming locator, No need to append filters to the url(s):");
                for (String url: urls) {
                    System.out.println(url);
                }
                System.out.println();
                System.out.println("Copy and paste the Streaming URL into the Azure Media Player at 'http://aka.ms/azuremediaplayer'.");
                System.out.println("When finished, press ENTER to continue.");
                System.out.flush();
                scanner.nextLine();
            } else if (job.state() == JobState.ERROR) {
                System.out.println("ERROR: Job finished with error message: " + job.outputs().get(0).error().message());
                System.out.println("ERROR:                   error details: "
                        + job.outputs().get(0).error().details().get(0).message());
            }
        } catch (Exception e) {
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
            System.out.println(e);
            e.printStackTrace();
            System.out.println();
        } finally {
            System.out.println("Cleaning up...");
            if (scanner != null) {
                scanner.close();
            }
            cleanup(manager, config.getResourceGroup(), config.getAccountName(), ADAPTIVE_STREAMING_TRANSFORM_NAME, jobName,
                inputAssetName, outputAssetName, accountFilterName, locatorName, stopEndpoint, STREAMING_ENDPOINT_NAME);
                System.out.println("Done.");
        }
    }

    /**
     * 
     * @param manager           The entry point of Azure Media resource management.
     * @param resourceGroup     The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param accountFilterName The AccountFilter name.
     * @return                  The AccountFilter created.
     */
    private static AccountFilter createAccountFilter(MediaManager manager, String resourceGroup, String accountName, String accountFilterName) {
        // Create tracks.
        List<FilterTrackPropertyCondition> audioConditions = new ArrayList<>();
        audioConditions.add(new FilterTrackPropertyCondition()
            .withProperty(FilterTrackPropertyType.TYPE)
            .withValue("Audio")
            .withOperation(FilterTrackPropertyCompareOperation.EQUAL));
        audioConditions.add(new FilterTrackPropertyCondition()
            .withProperty(FilterTrackPropertyType.FOUR_CC)
            .withValue("EC-3")
            .withOperation(FilterTrackPropertyCompareOperation.NOT_EQUAL));
        List<FilterTrackPropertyCondition> videoConditions = new ArrayList<>();
        videoConditions.add(new FilterTrackPropertyCondition()
            .withProperty(FilterTrackPropertyType.TYPE)
            .withValue("Video")
            .withOperation(FilterTrackPropertyCompareOperation.EQUAL));
        videoConditions.add(new FilterTrackPropertyCondition()
            .withProperty(FilterTrackPropertyType.BITRATE)
            .withValue("0-1000000")
            .withOperation(FilterTrackPropertyCompareOperation.EQUAL));
        List<FilterTrackSelection> includedTracks = new ArrayList<>();
        includedTracks.add(new FilterTrackSelection()
            .withTrackSelections(audioConditions));
        includedTracks.add(new FilterTrackSelection()
            .withTrackSelections(videoConditions));
            
        AccountFilter accountFilter = manager.accountFilters()
            .define(accountFilterName)
            .withExistingMediaservice(resourceGroup, accountName)
            .withTracks(includedTracks)
            .create();

        return accountFilter;
    }

    /**
     * If the specified transform exists, get that transform. If the it does not
     * exist, creates a new transform with the specified output. In this case, the
     * output is set to encode a video using the passed in preset.
     * 
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param transformName The name of the transform.
     * @param preset        The preset to be used in the transform.
     * @return              The transform found or created.
     */
    private static Transform ensureTransformExists(MediaManager manager, String resourceGroup, String accountName,
            String transformName) {
        Transform transform;
        try {
            // Does a Transform already exist with the desired name? Assume that an existing Transform with the desired name
            // also uses the same recipe or Preset for processing content.
            transform = manager.transforms()
                .getAsync(resourceGroup, accountName, transformName)
                .toBlocking()
                .first();
        }
        catch(NoSuchElementException nse)
        {
            // Media Services V3 throws an exception when not found.
            transform = null;
        }

        if (transform == null) {
            List<TransformOutput> outputs = new ArrayList<>();
            outputs.add(new TransformOutput().withPreset(new BuiltInStandardEncoderPreset().withPresetName(EncoderNamedPreset.ADAPTIVE_STREAMING)));

            // Create the transform.
            transform = manager.transforms()
                .define(transformName)
                .withExistingMediaservice(resourceGroup, accountName)
                .withOutputs(outputs)
                .create();
        }

        return transform;
    }

    /**
     * Create an asset.
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param assetName     The name of the asset to be created. It is known to be unique.
     * @return              The asset created.
     */
    private static Asset createAsset(MediaManager manager, String resourceGroup, String accountName,
            String assetName) {
        Asset asset;
        try {
            asset = manager.assets().getAsync(resourceGroup, accountName, assetName).toBlocking().first();
        }
        catch (NoSuchElementException nse) {
            asset = null;
        }

        if (asset == null) {
            asset = manager.assets().define(assetName)
                .withExistingMediaservice(resourceGroup, accountName)
                .create();
        }
        else {
            // The asset already exists and we are going to overwrite it. In your application, if you don't want to overwrite
            // an existing asset, use an unique name.
            System.out.println("Warning: The asset named " + assetName + "already exists. It will be overwritten.");
        }

        return asset;
    }

    /**
     * Create and submit a job.
     * @param manager           The entry point of Azure Media resource management.
     * @param resourceGroup     The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param transformName     The name of the transform.
     * @param jobName           The name of the job.
     * @param jobInput          The input to the job.
     * @param outputAssetName   The name of the asset that the job writes to.
     * @return                  The job created.
     */
    private static Job submitJob(MediaManager manager, String resourceGroup, String accountName, String transformName,
            String jobName, String inputAssetName, String outputAssetName) {
        // Use the name of the created input asset to create the job input.
        JobInput jobInput = new JobInputAsset().withAssetName(inputAssetName);

        // Specify where the output(s) of the Job need to be written to
        List<JobOutput> jobOutputs = new ArrayList<>();
        jobOutputs.add(new JobOutputAsset().withAssetName(outputAssetName));

        Job job;
        try {
            System.out.println("Creating a job...");
            job = manager.jobs().define(jobName)
                .withExistingTransform(resourceGroup, accountName, transformName)
                .withInput(jobInput)
                .withOutputs(jobOutputs)
                .create();
        }
        catch (ApiErrorException exception) {
            System.out.println("ERROR: API call failed with error code " + exception.body().error().code() +
                " and message '" + exception.body().error().message() + "'");
            throw exception;
        }

        return job;
    }

    /**
     * Polls Media Services for the status of the Job.
     * 
     * @param manager       This is the entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param transformName The name of the transform.
     * @param jobName       The name of the job submitted.
     * @return              The job.
     */
    private static Job waitForJobToFinish(MediaManager manager, String resourceGroup, String accountName,
            String transformName, String jobName) {
        final int SLEEP_INTERVAL = 10 * 1000;

        Job job = null;
        boolean exit = false;

        do {
            job = manager.jobs().getAsync(resourceGroup, accountName, transformName, jobName).toBlocking().first();

            if (job.state() == JobState.FINISHED || job.state() == JobState.ERROR || job.state() == JobState.CANCELED) {
                exit = true;
            } else {
                System.out.println("Job is " + job.state());

                int i = 0;
                for (JobOutput output : job.outputs()) {
                    System.out.print("\tJobOutput[" + i++ + "] is " + output.state() + ".");
                    if (output.state() == JobState.PROCESSING) {
                        System.out.print("  Progress: " + output.progress());
                    }
                    System.out.println();
                }

                try {
                    Thread.sleep(SLEEP_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (!exit);

        return job;
    }

    /**
     * Checks if the streaming endpoint is in the running state, if not, starts it.
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param locatorName   The name of the StreamingLocator that was created.
     * @param streamingEndpoint The streaming endpoint.
     * @return              List of streaming urls.
     */
    private static List<String> getDashStreamingUrls(MediaManager manager, String resourceGroup, String accountName,
        String locatorName, StreamingEndpoint streamingEndpoint) {
        List<String> streamingUrls = new ArrayList<>();

        ListPathsResponse paths = manager.streamingLocators().listPathsAsync(resourceGroup, accountName, locatorName)
            .toBlocking().first();
        
        for (StreamingPath path: paths.streamingPaths()) {
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append("https://")
                .append(streamingEndpoint.hostName())
                .append("/")
                .append(path.paths().get(0));

            if (path.streamingProtocol() == StreamingPolicyStreamingProtocol.DASH) {
                streamingUrls.add(uriBuilder.toString());
            }
        }
        return streamingUrls;
    }

    /**
     * Cleanup 
     * @param manager               The entry point of Azure Media resource management.
     * @param resourceGroupName     The name of the resource group within the Azure subscription.
     * @param accountName           The Media Services account name.
     * @param transformName         The transform name.
     * @param jobName               The job name.
     * @param inputAssetName        The input asset name.
     * @param outputAssetName       The output asset name.
     * @param accountFilterName     The AccountFilter name.
     * @param streamingLocatorName  The streaming locator name.
     * @param stopEndpoint          Stop endpoint if true, otherwise keep endpoint running.
     * @param streamingEndpointName The endpoint name.
     */
    private static void cleanup(MediaManager manager, String resourceGroupName, String accountName, String transformName, String jobName,
        String inputAssetName, String outputAssetName, String accountFilterName, String streamingLocatorName, boolean stopEndpoint,
        String streamingEndpointName) {
        if (manager == null) {
            return;
        }

        manager.jobs().deleteAsync(resourceGroupName, accountName, transformName, jobName).await();
        manager.assets().deleteAsync(resourceGroupName, accountName, inputAssetName).await();
        manager.assets().deleteAsync(resourceGroupName, accountName, outputAssetName).await();
        manager.accountFilters().deleteAsync(resourceGroupName, accountName, accountFilterName).await();
        manager.streamingLocators().deleteAsync(resourceGroupName, accountName, streamingLocatorName).await();

        if (stopEndpoint) {
            // Because we started the endpoint, we'll stop it.
            manager.streamingEndpoints().stopAsync(resourceGroupName, accountName, streamingEndpointName).await();
        }
        else {
            // We will keep the endpoint running because it was not started by this sample. Please note, There are costs to keep it running.
            // Please refer https://azure.microsoft.com/en-us/pricing/details/media-services/ for pricing.
            System.out.println("The endpoint '" + streamingEndpointName + "' is running. To halt further billing on the endpoint, please stop it in azure portal or AMS Explorer.");
        }
    }

    /**
     * Creates a new input Asset and uploads the specified local video file into it.
     * 
     * @param manager           This is the entry point of Azure Media resource management.
     * @param resourceGroupName The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param assetName         The name of the asset where the media file to uploaded to.
     * @param mediaFile         The path of a media file to be uploaded into the asset.
     * @return                  The asset.
     */
    private static Asset createInputAssetAndUploadVideo(MediaManager manager, String resourceGroupName, String accountName,
            String assetName, String mediaFile) throws Exception {
        Asset asset;
        try {
            // In this example, we are assuming that the asset name is unique.
            // If you already have an asset with the desired name, use the Assets.getAsync method
            // to get the existing asset.
            asset = manager.assets().getAsync(resourceGroupName, accountName, assetName).toBlocking().first();
        }
        catch (NoSuchElementException nse) {
            asset = null;
        }

        if (asset == null) {
            System.out.println("Creating an input asset...");
            // Call Media Services API to create an Asset.
            // This method creates a container in storage for the Asset.
            // The files (blobs) associated with the asset will be stored in this container.
            asset = manager.assets().define(assetName).withExistingMediaservice(resourceGroupName, accountName).create();
        }
        else {
            // The asset already exists and we are going to overwrite it. In your application, if you don't want to overwrite
            // an existing asset, use an unique name.
            System.out.println("Warning: The asset named " + assetName + "already exists. It will be overwritten.");
        }
        
        // Use Media Services API to get back a response that contains
        // SAS URL for the Asset container into which to upload blobs.
        // That is where you would specify read-write permissions
        // and the expiration time for the SAS URL.
        ListContainerSasInput parameters = new ListContainerSasInput()
                .withPermissions(AssetContainerPermission.READ_WRITE).withExpiryTime(DateTime.now().plusHours(4));
        AssetContainerSas response = manager.assets()
                .listContainerSasAsync(resourceGroupName, accountName, assetName, parameters).toBlocking().first();
        URI sasUri = new URI(response.assetContainerSasUrls().get(0));

        // Use Storage API to get a reference to the Asset container
        // that was created by calling Asset's create method.
        CloudBlobContainer container = new CloudBlobContainer(sasUri);

        // Uploading from a local file:
        String fileToUpload = AssetFilters.class.getClassLoader().getResource(mediaFile).getPath(); // The file is a resource in CLASSPATH.
        File file = new File(fileToUpload);
        CloudBlockBlob blob = container.getBlockBlobReference(file.getName());

        // Use Storage API to upload the file into the container in storage.
        System.out.println("Uploading a media file to the asset...");
        blob.uploadFromFile(fileToUpload);

        return asset;
    }
}
