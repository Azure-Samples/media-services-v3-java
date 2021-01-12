// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ApiErrorException;
import com.microsoft.azure.management.mediaservices.v2020_05_01.Asset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.AssetContainerPermission;
import com.microsoft.azure.management.mediaservices.v2020_05_01.AssetContainerSas;
import com.microsoft.azure.management.mediaservices.v2020_05_01.BuiltInStandardEncoderPreset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.EncoderNamedPreset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.Job;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobInput;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobInputHttp;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobOutput;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobOutputAsset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobState;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ListContainerSasInput;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ListPathsResponse;
import com.microsoft.azure.management.mediaservices.v2020_05_01.Preset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingEndpoint;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingEndpointResourceState;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingLocator;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingPath;
import com.microsoft.azure.management.mediaservices.v2020_05_01.Transform;
import com.microsoft.azure.management.mediaservices.v2020_05_01.TransformOutput;
import com.microsoft.azure.management.mediaservices.v2020_05_01.implementation.MediaManager;
import com.microsoft.rest.LogLevel;

import org.joda.time.DateTime;

public class EncodingWithMESPredefinedPreset {
    private static final String TRANSFORM_NAME = "AdaptiveBitrate";
    private static final String OUTPUT_FOLDER = "Output";
    private static final String BASE_URI = "https://nimbuscdn-nimbuspm.streaming.mediaservices.windows.net/2b533311-b215-4409-80af-529c3e853622/";
    private static final String MP4_FILE_NAME = "Ignite-short.mp4";
    private static final String INPUT_LABEL = "input1";

    // Please change this to your endpoint name
    private static final String STREAMING_ENDPOINT_NAME = "se";

    public static void main(String[] args) {
        // Please make sure you have set configuration in resources/conf/appsettings.json. For more information, see
        // https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to.
        ConfigWrapper config = new ConfigWrapper();
        runEncodingWithMESPredefinedPreset(config);

        config.close();
        System.exit(0);
    }

    /**
     * Run the sample.
     *
     * @param config This param is of type ConfigWrapper. This class reads values from local configuration file.
     */
    private static void runEncodingWithMESPredefinedPreset(ConfigWrapper config) {
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
        boolean stopEndpoint = false;

        Scanner scanner = new Scanner(System.in);
        try {
            // Ensure that you have customized encoding Transform with builtin preset. This is really a one time
            // setup operation.
            Transform adaptiveEncodeTransform = ensureTransformExists(manager, config.getResourceGroup(),
                    config.getAccountName(), TRANSFORM_NAME,
                    new BuiltInStandardEncoderPreset().withPresetName(EncoderNamedPreset.ADAPTIVE_STREAMING));

            // Create a JobInputHttp. The input to the Job is a HTTPS URL pointing to an MP4 file.
            List<String> files = new ArrayList<>();
            files.add(MP4_FILE_NAME);
            JobInputHttp input = new JobInputHttp().withBaseUri(BASE_URI);
            input.withFiles(files);
            input.withLabel(INPUT_LABEL);

            // Output from the encoding Job must be written to an Asset, so let's create one. Note that we
            // are using a unique asset name, there should not be a name collision.
            System.out.println("Creating an output asset...");
            Asset outputAsset = createAsset(manager, config.getResourceGroup(), config.getAccountName(),
                    outputAssetName);

            Job job = submitJob(manager, config.getResourceGroup(), config.getAccountName(), adaptiveEncodeTransform.name(), jobName,
                    input, outputAsset.name());

            long startedTime = System.currentTimeMillis();

            // In this demo code, we will poll for Job status. Polling is not a recommended best practice for production
            // applications because of the latency it introduces. Overuse of this API may trigger throttling. Developers
            // should instead use Event Grid. To see how to implement the event grid, see the sample
            // https://github.com/Azure-Samples/media-services-v3-java/tree/master/ContentProtection/BasicAESClearKey.
            job = waitForJobToFinish(manager, config.getResourceGroup(), config.getAccountName(), adaptiveEncodeTransform.name(),
                    jobName);

            long elapsed = (System.currentTimeMillis() - startedTime) / 1000; // Elapsed time in seconds
            System.out.println("Job elapsed time: " + elapsed + " second(s).");

            if (job.state() == JobState.FINISHED) {
                System.out.println("Job finished.");
                System.out.println();

                // Now that the content has been encoded, publish it for Streaming by creating
                // a StreamingLocator. 
                StreamingLocator locator = getStreamingLocator(manager, config.getResourceGroup(), config.getAccountName(),
                        outputAsset.name(), locatorName);

                StreamingEndpoint streamingEndpoint = manager.streamingEndpoints()
                        .getAsync(config.getResourceGroup(), config.getAccountName(), STREAMING_ENDPOINT_NAME)
                        .toBlocking().first();

                if (streamingEndpoint != null) {
                    // Start The Streaming Endpoint if it is not running.
                    if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                        System.out.println("Streaming endpoint was stopped, restarting it...");
                        manager.streamingEndpoints().startAsync(config.getResourceGroup(), config.getAccountName(), STREAMING_ENDPOINT_NAME).await();

                        // We started the endpoint, we should stop it in cleanup.
                        stopEndpoint = true;
                    }

                    System.out.println();
                    System.out.println("Streaming urls:");
                    List<String> urls = getStreamingUrls(manager, config.getResourceGroup(), config.getAccountName(), locator.name(), streamingEndpoint);

                    for (String url : urls) {
                        System.out.println("\t" + url);
                    }

                    System.out.println();
                    System.out.println("To stream, copy and paste the Streaming URL into the Azure Media Player at 'http://aka.ms/azuremediaplayer'.");
                    System.out.println("When finished, press ENTER to continue.");
                    System.out.println();
                    System.out.flush();
                    scanner.nextLine();

                    // Download output asset for verification.
                    System.out.println("Downloading output asset...");
                    System.out.println();
                    File outputFolder = new File(OUTPUT_FOLDER);
                    if (outputFolder.exists() && !outputFolder.isDirectory()) {
                        outputFolder = new File(OUTPUT_FOLDER + uniqueness);
                    }
                    if (!outputFolder.exists()) {
                        outputFolder.mkdir();
                    }

                    downloadResults(manager, config.getResourceGroup(), config.getAccountName(), outputAsset.name(),
                            outputFolder);

                    System.out.println("Done downloading. Please check the files at " + outputFolder.getAbsolutePath());
                } else {
                    System.out.println("Could not find streaming endpoint: " + STREAMING_ENDPOINT_NAME);
                }

                System.out.println("When finished, press ENTER to cleanup.");
                System.out.println();
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
            System.out.println("Cleaning up...");
            if (scanner != null) {
                scanner.close();
            }
            cleanup(manager, config.getResourceGroup(), config.getAccountName(), TRANSFORM_NAME, jobName,
                    outputAssetName, locatorName, stopEndpoint, STREAMING_ENDPOINT_NAME);
            System.out.println("Done.");
        }
    }

    /**
     * If the specified transform exists, get that transform. If the it does not
     * exist, creates a new transform with the specified output. In this case, the
     * output is set to encode a video using the passed in preset.
     *
     * @param manager       The entry point of Azure Media resource management
     * @param resourceGroup The name of the resource group within the Azure subscription
     * @param accountName   The Media Services account name
     * @param transformName The name of the transform
     * @param preset        The preset to be used in the transform
     * @return The transform found or created
     */
    private static Transform ensureTransformExists(MediaManager manager, String resourceGroup, String accountName,
                                                   String transformName, Preset preset) {
        Transform transform;
        try {
            // Does a Transform already exist with the desired name? Assume that an existing Transform with the desired name
            // also uses the same recipe or Preset for processing content.
            transform = manager.transforms()
                    .getAsync(resourceGroup, accountName, transformName)
                    .toBlocking()
                    .first();
        } catch (NoSuchElementException nse) {
            // Media Services V3 throws an exception when not found.
            transform = null;
        }

        if (transform == null) {
            List<TransformOutput> outputs = new ArrayList<>();
            outputs.add(new TransformOutput().withPreset(preset));

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
     *
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param assetName     The name of the asset to be created. It is known to be unique.
     * @return The asset created.
     */
    private static Asset createAsset(MediaManager manager, String resourceGroup, String accountName,
                                     String assetName) {
        return manager.assets()
                .define(assetName)
                .withExistingMediaservice(resourceGroup, accountName)
                .create();
    }

    /**
     * Create and submit a job.
     *
     * @param manager         The entry point of Azure Media resource management.
     * @param resourceGroup   The name of the resource group within the Azure subscription.
     * @param accountName     The Media Services account name.
     * @param transformName   The name of the transform.
     * @param jobName         The name of the job.
     * @param jobInput        The input to the job.
     * @param outputAssetName The name of the asset that the job writes to.
     * @return The job created.
     */
    private static Job submitJob(MediaManager manager, String resourceGroup, String accountName, String transformName,
                                 String jobName, JobInput jobInput, String outputAssetName) {
        System.out.println("Creating a job...");
        // First specify where the output(s) of the Job need to be written to
        List<JobOutput> jobOutputs = new ArrayList<>();
        jobOutputs.add(new JobOutputAsset().withAssetName(outputAssetName));

        Job job = manager.jobs().define(jobName)
                .withExistingTransform(resourceGroup, accountName, transformName)
                .withInput(jobInput)
                .withOutputs(jobOutputs)
                .create();

        return job;
    }

    /**
     * Polls Media Services for the status of the Job.
     *
     * @param manager       This is the entry point of Azure Media resource
     *                      management
     * @param resourceGroup The name of the resource group within the Azure
     *                      subscription
     * @param accountName   The Media Services account name
     * @param transformName The name of the transform
     * @param jobName       The name of the job submitted
     * @return The job
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
     * Use Media Service and Storage APIs to download the output files to a local folder
     *
     * @param manager       The entry point of Azure Media resource management
     * @param resourceGroup The name of the resource group within the Azure subscription
     * @param accountName   The Media Services account name
     * @param assetName     The asset name
     * @param outputFolder  The output folder for downloaded files.
     * @throws StorageException
     * @throws URISyntaxException
     * @throws IOException
     */
    private static void downloadResults(MediaManager manager, String resourceGroup, String accountName,
                                        String assetName, File outputFolder) throws URISyntaxException, IOException {
        ListContainerSasInput parameters = new ListContainerSasInput()
                .withPermissions(AssetContainerPermission.READ)
                .withExpiryTime(DateTime.now().plusHours(1));
        AssetContainerSas assetContainerSas = manager.assets()
                .listContainerSasAsync(resourceGroup, accountName, assetName, parameters).toBlocking().first();

        BlobContainerClient container =
                new BlobContainerClientBuilder()
                        .connectionString(assetContainerSas.assetContainerSasUrls().get(0))
                        .buildClient();

        File directory = new File(outputFolder, assetName);
        directory.mkdir();

        container.listBlobs().forEach(blobItem -> {
            BlobClient blob = container.getBlobClient(blobItem.getName());
            File downloadTo = new File(directory, blobItem.getName());
            blob.downloadToFile(downloadTo.getAbsolutePath());
        });

        System.out.println("Download complete.");
    }

    /**
     * Creates a StreamingLocator for the specified asset and with the specified streaming policy name.
     * Once the StreamingLocator is created the output asset is available to clients for playback.
     *
     * @param manager       The entry point of Azure Media resource management
     * @param resourceGroup The name of the resource group within the Azure subscription
     * @param accountName   The Media Services account name
     * @param assetName     The name of the output asset
     * @param locatorName   The StreamingLocator name (unique in this case)
     * @return The locator created
     */
    private static StreamingLocator getStreamingLocator(MediaManager manager, String resourceGroup, String accountName,
                                                        String assetName, String locatorName) {
        // Note that we are using one of the PredefinedStreamingPolicies which tell the Origin component
        // of Azure Media Services how to publish the content for streaming.
        System.out.println("Creating a streaming locator...");
        StreamingLocator locator = manager
                .streamingLocators().define(locatorName)
                .withExistingMediaservice(resourceGroup, accountName)
                .withAssetName(assetName)
                .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                .create();

        return locator;
    }

    /**
     * Checks if the streaming endpoint is in the running state, if not, starts it.
     *
     * @param manager           The entry point of Azure Media resource management
     * @param resourceGroup     The name of the resource group within the Azure subscription
     * @param accountName       The Media Services account name
     * @param locatorName       The name of the StreamingLocator that was created
     * @param streamingEndpoint The streaming endpoint.
     * @return List of streaming urls
     */
    private static List<String> getStreamingUrls(MediaManager manager, String resourceGroup, String accountName,
                                                 String locatorName, StreamingEndpoint streamingEndpoint) {
        List<String> streamingUrls = new ArrayList<>();

        ListPathsResponse paths = manager.streamingLocators().listPathsAsync(resourceGroup, accountName, locatorName)
                .toBlocking().first();

        for (StreamingPath path : paths.streamingPaths()) {
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append("https://")
                    .append(streamingEndpoint.hostName())
                    .append("/")
                    .append(path.paths().get(0));

            streamingUrls.add(uriBuilder.toString());
        }
        return streamingUrls;
    }

    /**
     * Cleanup
     *
     * @param manager               The entry point of Azure Media resource management.
     * @param resourceGroupName     The name of the resource group within the Azure subscription.
     * @param accountName           The Media Services account name.
     * @param transformName         The transform name.
     * @param jobName               The job name.
     * @param assetName             The asset name.
     * @param streamingLocatorName  The streaming locator name.
     * @param stopEndpoint          Stop endpoint if true, otherwise keep endpoint running.
     * @param streamingEndpointName The endpoint name.
     */
    private static void cleanup(MediaManager manager, String resourceGroupName, String accountName, String transformName, String jobName,
                                String assetName, String streamingLocatorName, boolean stopEndpoint, String streamingEndpointName) {
        if (manager == null) {
            return;
        }

        manager.jobs().deleteAsync(resourceGroupName, accountName, transformName, jobName).await();
        manager.assets().deleteAsync(resourceGroupName, accountName, assetName).await();

        manager.streamingLocators().deleteAsync(resourceGroupName, accountName, streamingLocatorName).await();

        if (stopEndpoint) {
            // Because we started the endpoint, we'll stop it.
            manager.streamingEndpoints().stopAsync(resourceGroupName, accountName, streamingEndpointName).await();
        } else {
            // We will keep the endpoint running because it was not started by this sample. Please note, There are costs to keep it running.
            // Please refer https://azure.microsoft.com/en-us/pricing/details/media-services/ for pricing.
            System.out.println("The endpoint '" + streamingEndpointName + "' is running. To halt further billing on the endpoint, please stop it in azure portal or AMS Explorer.");
        }
    }
}

