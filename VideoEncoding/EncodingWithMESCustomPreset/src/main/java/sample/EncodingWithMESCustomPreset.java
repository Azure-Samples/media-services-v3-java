// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import javax.naming.AuthenticationException;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.mediaservices.models.*;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.identity.ClientSecretCredentialBuilder;

public class EncodingWithMESCustomPreset {
    private static final String CUSTOM_TWO_LAYER_MP4_PNG = "Custom_TwoLayerMp4_Png";
    private static final String INPUT_MP4_RESOURCE = "video/ignite.mp4";
    private static final String OUTPUT_FOLDER_NAME = "Output";

    // Please change this to your endpoint name
    private static final String STREAMING_ENDPOINT_NAME = "default";

    // Please make sure you have set configurations in resources/conf/appsettings.json
    public static void main(String[] args) {
        ConfigWrapper config = new ConfigWrapper();
        runEncodingWithMESCustomPreset(config);

        config.close();
        System.exit(0);
    }

    /**
     * Run the sample.
     *
     * @param config The param is of type ConfigWrapper. This class reads values from local configuration file.
     */
    private static void runEncodingWithMESCustomPreset(ConfigWrapper config) {
        // Connect to media services, please see https://docs.microsoft.com/en-us/azure/media-services/latest/configure-connect-java-howto
        // for details.
        TokenCredential credential = new ClientSecretCredentialBuilder()
                .clientId(config.getAadClientId())
                .clientSecret(config.getAadSecret())
                .tenantId(config.getAadTenantId())
                .build();
        AzureProfile profile = new AzureProfile(config.getAadTenantId(), config.getSubscriptionId(),
                com.azure.core.management.AzureEnvironment.AZURE);

        // MediaServiceManager is the entry point to Azure Media resource management.
        MediaServicesManager manager = MediaServicesManager.configure()
                .withLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS))
                .authenticate(credential, profile);

        // Creating a unique suffix so that we don't have name collisions if you run the
        // sample multiple times without cleaning up.
        UUID uuid = UUID.randomUUID();
        String uniqueness = uuid.toString();
        String jobName = "job-" + uniqueness;
        String locatorName = "locator-" + uniqueness;
        String outputAssetName = "output-" + uniqueness;
        String inputAssetName = "input-" + uniqueness;
        boolean stopEndpoint = false;

        Scanner scanner = new Scanner(System.in);

        try {
            // Ensure that you have the desired encoding Transform. This is really a one time setup operation.
            Transform transform = createCustomTransform(manager, config.getResourceGroup(), config.getAccountName(),
                    CUSTOM_TWO_LAYER_MP4_PNG);

            // Create a new input Asset and upload the specified local video file into it.
            Asset asset = createInputAsset(manager, config.getResourceGroup(), config.getAccountName(), inputAssetName,
                    INPUT_MP4_RESOURCE);

            // Output from the encoding Job must be written to an Asset, so let's create one
            Asset outputAsset = manager.assets()
                    .define(outputAssetName)
                    .withExistingMediaService(config.getResourceGroup(), config.getAccountName())
                    .create();

            Job job = submitJob(manager, config.getResourceGroup(), config.getAccountName(),
                    transform.name(), jobName, asset.name(), outputAsset.name());

            // In this demo code, we will poll for Job status. Polling is not a recommended best practice for production
            // applications because of the latency it introduces. Overuse of this API may trigger throttling. Developers
            // should instead use Event Grid. To see how to implement the event grid, see the sample
            // https://github.com/Azure-Samples/media-services-v3-java/tree/master/ContentProtection/BasicAESClearKey.
            System.out.println();
            job = waitForJobToFinish(manager, config.getResourceGroup(), config.getAccountName(),
                    transform.name(), jobName);

            if (job.state() == JobState.FINISHED) {
                System.out.println("Job finished.");
                System.out.println();
                File outputFolder = new File(OUTPUT_FOLDER_NAME);
                if (outputFolder.exists() && !outputFolder.isDirectory()) {
                    outputFolder = new File(OUTPUT_FOLDER_NAME + uniqueness);
                }

                if (!outputFolder.exists()) {
                    outputFolder.mkdir();
                }

                downloadOutputAsset(manager, config.getResourceGroup(), config.getAccountName(), outputAsset.name(),
                        outputFolder);
                System.out.println("Please check the files at " + outputFolder.getAbsolutePath());
                System.out.println("When finished, press ENTER to continue.");
                System.out.println();
                System.out.flush();
                scanner.nextLine();

                StreamingLocator locator = createStreamingLocator(manager, config.getResourceGroup(), config.getAccountName(), outputAsset.name(), locatorName);

                StreamingEndpoint streamingEndpoint = manager.streamingEndpoints()
                        .get(config.getResourceGroup(), config.getAccountName(), STREAMING_ENDPOINT_NAME);

                if (streamingEndpoint != null) {
                    // Start The Streaming Endpoint if it is not running.
                    if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                        manager.streamingEndpoints().start(config.getResourceGroup(), config.getAccountName(), STREAMING_ENDPOINT_NAME);

                        // We started the endpoint, we should stop it in cleanup.
                        stopEndpoint = true;
                    }
                }

                List<String> urls = getStreamingUrls(manager, config.getResourceGroup(), config.getAccountName(), locator.name(), streamingEndpoint);

                System.out.println();
                System.out.println("Streaming urls:");
                for (String url : urls) {
                    System.out.println(url);
                }
            }

            System.out.println();
            System.out.println("To stream, copy and paste the Streaming URL into the Azure Media Player at 'http://aka.ms/azuremediaplayer'.");
            System.out.println("When finished, press ENTER to cleanup.");
            System.out.flush();
            scanner.nextLine();
        } catch (Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof AuthenticationException) {
                    System.out.println("ERROR: Authentication error, please check your account settings in appsettings.json.");
                    break;
                } else if (cause instanceof ManagementException) {
                    ManagementException apiException = (ManagementException) cause;
                    System.out.println("ERROR: " + apiException.getValue().getMessage());
                    break;
                }
                else {
                    System.out.println("Error: " + cause);
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

            cleanup(manager, config.getResourceGroup(), config.getAccountName(), CUSTOM_TWO_LAYER_MP4_PNG, jobName, inputAssetName,
                    outputAssetName, locatorName, stopEndpoint, STREAMING_ENDPOINT_NAME);

            System.out.println("Done.");
        }
    }

    /**
     * If the specified transform exists, return that transform. If the it does not
     * exist, creates a new transform with the specified output. In this case, the
     * output is set to encode a video using a custom preset.
     *
     * @param manager       This is the entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param transformName The name of the transform.
     * @return The transform found or created.
     */
    private static Transform createCustomTransform(MediaServicesManager manager, String resourceGroup, String accountName,
                                                   String transformName) {
        Transform transform;
        try {
            // Does a transform already exist with the desired name? Assume that an existing Transform with the desired name
            // also uses the same recipe or preset for processing content.
            transform = manager.transforms().get(resourceGroup, accountName, transformName);
        } catch (ManagementException e) {
            transform = null;
        }

        if (transform == null) {
            System.out.println("Creating a custom transform...");
            // Create a new Transform Outputs List - this defines the set of outputs for the Transform
            List<TransformOutput> outputs = new ArrayList<>();

            // Create a new TransformOutput with a custom Standard Encoder Preset
            // This demonstrates how to create custom codec and layer output settings
            TransformOutput transformOutput = new TransformOutput();

            // Add it to output list.
            outputs.add(transformOutput);

            // Create a customer preset and add it to transform output
            StandardEncoderPreset preset = new StandardEncoderPreset();
            transformOutput.withPreset(preset)
                    .withOnError(OnErrorType.STOP_PROCESSING_JOB)
                    .withRelativePriority(Priority.NORMAL);

            // Create codecs for the preset and add it to the preset
            List<Codec> codecs = new ArrayList<>();
            preset.withCodecs(codecs);

            // Add an AAC Audio layer for the audio encoding
            codecs.add(new AacAudio()
                    .withProfile(AacAudioProfile.AAC_LC)
                    .withChannels(2)
                    .withSamplingRate(48000)
                    .withBitrate(128000));

            // Next, add a H264Video with two layers, HD and SD for the video encoding
            List<H264Layer> layers = new ArrayList<>();
            // Add H264Layers, one at HD and the other at SD. Assign a label that you can use for the output filename
            H264Layer hdLayer = new H264Layer();
            hdLayer.withBitrate(1000000)    // Units are in bits per second
                    .withWidth("1280")
                    .withHeight("720")
                    .withLabel("HD");           // This label is used to modify the file name in the output formats
            H264Layer sdLayer = new H264Layer();
            sdLayer.withBitrate(600000)
                    .withWidth("640")
                    .withHeight("360")
                    .withLabel("SD");
            layers.add(hdLayer);
            layers.add(sdLayer);

            codecs.add(new H264Video()      // Add a H264Video to codecs
                    .withLayers(layers)         // Add the 2 layers
                    .withKeyFrameInterval(Duration.ofSeconds(2))    //Set the GOP interval to 2 seconds for both H264Layers
            );

            // Also generate a set of PNG thumbnails
            List<PngLayer> pngLayers = new ArrayList<>();
            PngLayer pngLayer = new PngLayer();
            pngLayer.withWidth("50%");
            pngLayer.withHeight("50%");
            pngLayers.add(pngLayer);
            codecs.add(new PngImage()
                    .withLayers(pngLayers)
                    .withStart("25%")
                    .withStep("25%")
                    .withRange("80%"));

            // Specify the format for the output files - one for video+audio, and another for the thumbnails
            List<Format> formats = new ArrayList<>();
            // Mux the H.264 video and AAC audio into MP4 files, using basename, label, bitrate and extension macros
            // Note that since you have multiple H264Layers defined above, you have to use a macro that produces unique names per H264Layer
            // Either {Label} or {Bitrate} should suffice
            formats.add(new Mp4Format().withFilenamePattern("Video-{Basename}-{Label}-{Bitrate}{Extension}"));
            formats.add(new PngFormat().withFilenamePattern("Thumbnail-{Basename}-{Index}{Extension}"));
            preset.withFormats(formats);

            // Create the custom Transform with the outputs defined above
            transform = manager.transforms().define(transformName)
                    .withExistingMediaService(resourceGroup, accountName)
                    .withOutputs(outputs)
                    .withDescription("A simple custom encoding transform with 2 MP4 bitrates")
                    .create();
        }

        return transform;
    }

    /**
     * Creates a new input Asset and uploads the specified local video file into it.
     *
     * @param manager           This is the entry point of Azure Media resource management.
     * @param resourceGroupName The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param assetName         The name of the asset where the media file to uploaded to.
     * @param mediaFile         The path of a media file to be uploaded into the asset.
     * @return The asset.
     */
        private static Asset createInputAsset(MediaServicesManager manager, String resourceGroupName, String accountName,
                String assetName, String mediaFile) throws Exception {

            System.out.println("Creating an input asset...");
            // Call Media Services API to create an Asset.
            // This method creates a container in storage for the Asset.
            // The files (blobs) associated with the asset will be stored in this container.
            Asset asset = manager.assets().define(assetName).withExistingMediaService(resourceGroupName, accountName)
                    .create();
            ListContainerSasInput parameters = new ListContainerSasInput()
                    .withPermissions(AssetContainerPermission.READ_WRITE).withExpiryTime(OffsetDateTime.now().plusHours(4));
            AssetContainerSas response = manager.assets()
                    .listContainerSas(resourceGroupName, accountName, assetName, parameters);

            // Use Storage API to get a reference to the Asset container
            // that was created by calling Asset's create method.
            BlobContainerClient container = new BlobContainerClientBuilder()
                    .endpoint(response.assetContainerSasUrls().get(0))
                    .buildClient();

            // Uploading from a local file:
            URI fileToUpload = EncodingWithMESCustomPreset.class.getClassLoader().getResource(mediaFile).toURI(); // The file is a
            // resource in
            // CLASSPATH.
            File file = new File(fileToUpload);
            BlobClient blob = container.getBlobClient(file.getName());

            // Use Storage API to upload the file into the container in storage.
            System.out.println("Uploading a media file to the asset...");
            blob.uploadFromFile(file.getPath());

            return asset;
    }

    /**
     * Submits a request to Media Services to apply the specified Transform to a given input video.
     *
     * @param manager           This is the entry point of Azure Media resource management.
     * @param resourceGroupName The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param transformName     The name of the transform.
     * @param jobName           The (unique) name of the job.
     * @param inputAssetName    The name of the input asset.
     * @param outputAssetName   The (unique) name of the output asset that will.
     *                          store the result of the encoding job.
     * @return The job created.
     */
    private static Job submitJob(MediaServicesManager manager, String resourceGroupName, String accountName,
                                 String transformName, String jobName, String inputAssetName, String outputAssetName) {
        JobInput jobInput = new JobInputAsset().withAssetName(inputAssetName);

        JobOutput output = new JobOutputAsset().withAssetName(outputAssetName);
        List<JobOutput> jobOutputs = new ArrayList<>();
        jobOutputs.add(output);

        // In this example, we are assuming that the job name is unique.
        // If you already have a job with the desired name, use the Jobs.get method
        // to get the existing job.
        Job job;
        try {
            System.out.println("Creating a job...");
            job = manager.jobs().define(jobName).withExistingTransform(resourceGroupName, accountName, transformName)
                    .withInput(jobInput).withOutputs(jobOutputs).create();
        } catch (ManagementException exception) {
            System.out.println("Failed to create job.");
            System.out.println("ERROR: API call failed with error code '" + exception.getValue().getCode() + "' and message " +
                    exception.getValue().getMessage());
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
     * @param jobName       The name of the job you submitted.
     * @return The job.
     */
    private static Job waitForJobToFinish(MediaServicesManager manager, String resourceGroup, String accountName,
                                          String transformName, String jobName) {
        final int SLEEP_INTERVAL = 30 * 1000;

        Job job = null;
        boolean exit = false;

        do {
            job = manager.jobs().get(resourceGroup, accountName, transformName, jobName);

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
     * Downloads the results from the specified output asset, so you can see what
     * you got.
     *
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param assetName     The output asset.
     * @param outputFolder  The name of the folder into which to download the results.
     * @throws URISyntaxException
     * @throws IOException
     */
    private static void downloadOutputAsset(MediaServicesManager manager, String resourceGroup, String accountName,
                                            String assetName, File outputFolder) throws URISyntaxException, IOException {
        final int LIST_BLOBS_SEGMENT_MAX_RESULT = 5;

        ListContainerSasInput parameters = new ListContainerSasInput()
                .withPermissions(AssetContainerPermission.READ)
                .withExpiryTime(OffsetDateTime.now().plusHours(1));

        AssetContainerSas assetContainerSas = manager.assets()
                .listContainerSas(resourceGroup, accountName, assetName, parameters);

        BlobContainerClient container =
                new BlobContainerClientBuilder()
                        .endpoint(assetContainerSas.assetContainerSasUrls().get(0))
                        .buildClient();

        File directory = new File(outputFolder, assetName);
        directory.mkdirs();

        System.out.println("Downloading output results to " + directory.getPath() + "...");

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
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param assetName     The name of the output asset.
     * @param locatorName   The StreamingLocator name (unique in this case).
     * @return The locator created.
     */
    private static StreamingLocator createStreamingLocator(MediaServicesManager manager, String resourceGroup, String accountName,
                                                           String assetName, String locatorName) {
        StreamingLocator locator = manager
                .streamingLocators().define(locatorName)
                .withExistingMediaService(resourceGroup, accountName)
                .withAssetName(assetName)
                .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                .create();

        return locator;
    }

    /**
     * Checks if the streaming endpoint is in the running state, if not, starts it.
     *
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param locatorName   The name of the StreamingLocator that was created.
     * @return List of streaming urls.
     */
    private static List<String> getStreamingUrls(MediaServicesManager manager, String resourceGroup, String accountName,
                                                 String locatorName, StreamingEndpoint streamingEndpoint) {
        List<String> streamingUrls = new ArrayList<>();

        ListPathsResponse paths = manager.streamingLocators().listPaths(resourceGroup, accountName, locatorName);

        for (StreamingPath path : paths.streamingPaths()) {
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append("https://")
                    .append(streamingEndpoint.hostname())
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
     * @param inputAssetName        The input asset name.
     * @param outputAssetName       The output asset name.
     * @param streamingLocatorName  The streaming locator name.
     * @param stopEndpoint          Stop endpoint if true, otherwise keep endpoint running.
     * @param streamingEndpointName The endpoint name.
     */
    private static void cleanup(MediaServicesManager manager, String resourceGroupName, String accountName, String transformName, String jobName,
                                String inputAssetName, String outputAssetName, String streamingLocatorName, boolean stopEndpoint, String streamingEndpointName) {
        if (manager == null) {
            return;
        }

        manager.jobs().delete(resourceGroupName, accountName, transformName, jobName);
        manager.assets().delete(resourceGroupName, accountName, inputAssetName);
        manager.assets().delete(resourceGroupName, accountName, outputAssetName);
        manager.streamingLocators().delete(resourceGroupName, accountName, streamingLocatorName);

        if (stopEndpoint) {
            // Because we started the endpoint, we'll stop it.
            manager.streamingEndpoints().stop(resourceGroupName, accountName, streamingEndpointName);
        } else {
            // We will keep the endpoint running because it was not started by this sample. Please note, There are costs to keep it running.
            // Please refer https://azure.microsoft.com/en-us/pricing/details/media-services/ for pricing.
            System.out.println("The endpoint '" + streamingEndpointName + "' is running. To halt further billing on the endpoint, please stop it in azure portal or AMS Explorer.");
        }
    }
}
