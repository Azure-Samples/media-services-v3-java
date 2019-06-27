// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AacAudio;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AacAudioProfile;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Asset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AssetContainerPermission;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AssetContainerSas;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Codec;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Format;
import com.microsoft.azure.management.mediaservices.v2018_07_01.H264Layer;
import com.microsoft.azure.management.mediaservices.v2018_07_01.H264Video;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Job;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobInput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobInputAsset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobOutput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobOutputAsset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobState;
import com.microsoft.azure.management.mediaservices.v2018_07_01.ListContainerSasInput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.ListPathsResponse;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Mp4Format;
import com.microsoft.azure.management.mediaservices.v2018_07_01.OnErrorType;
import com.microsoft.azure.management.mediaservices.v2018_07_01.PngFormat;
import com.microsoft.azure.management.mediaservices.v2018_07_01.PngImage;
import com.microsoft.azure.management.mediaservices.v2018_07_01.PngLayer;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Priority;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StandardEncoderPreset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingEndpoint;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingEndpointResourceState;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingLocator;
import com.microsoft.azure.management.mediaservices.v2018_07_01.StreamingPath;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Transform;
import com.microsoft.azure.management.mediaservices.v2018_07_01.TransformOutput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.implementation.MediaManager;
import com.microsoft.azure.storage.ResultContinuation;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.rest.LogLevel;

import org.joda.time.DateTime;
import org.joda.time.Period;

public class EncodingWithMESCustomPreset {
    private static final String CUSTOM_TWO_LAYER_MP4_PNG = "Custom_TwoLayerMp4_Png";
    private static final String INPUT_MP4_RESOURCE = "video/ignite.mp4";
    private static final String OUTPUT_FOLDER_NAME = "Output";
    //private static final String BASE_URI = "https://nimbuscdn-nimbuspm.streaming.mediaservices.windows.net/2b533311-b215-4409-80af-529c3e853622/";
    //private static final String MP4_FILE_NAME = "Ignite-short.mp4";
    private static final String STREAMING_ENDPOINT_NAME = "se";

    public static void main(String[] args) {
        ConfigWrapper config = new ConfigWrapper();
        runEncodingWithMESCustomPreset(config);

        config.close();
        System.exit(0);
    }

    /**
     * Run the sample.
     * @param config The parm is of type ConfigWrapper. This class reads values from local configuration file
     */
    private static void runEncodingWithMESCustomPreset(ConfigWrapper config) {
        // Connect to media services, please see https://docs.microsoft.com/en-us/azure/media-services/latest/configure-connect-java-howto
        // for details.
        ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(config.getAadClientId(),
                config.getAadTenantId(), config.getAadSecret(), AzureEnvironment.AZURE);
        credentials.withDefaultSubscriptionId(config.getSubscriptionId());

        // Get the entry point to Azure Media resource management.
        MediaManager manager = MediaManager.configure().withLogLevel(LogLevel.BODY_AND_HEADERS)
                .authenticate(credentials, credentials.defaultSubscriptionId());
        // Signed in.

        // Creating a unique suffix so that we don't have name collisions if you run the
        // sample multiple times without cleaning up.
        UUID uuid = UUID.randomUUID();
        String uniqueness = uuid.toString();
        String jobName = "job-" + uniqueness;
        String locatorName = "locator-" + uniqueness;
        String outputAssetName = "output-" + uniqueness;
        String inputAssetName = "input-" + uniqueness;

        Scanner scanner = new Scanner(System.in);

        try {
            // Ensure that you have the desired encoding Transform. This is really a one
            // time setup operation.
            Transform transform = getOrCreateTransform(manager, config.getResourceGroup(), config.getAccountName(),
                    CUSTOM_TWO_LAYER_MP4_PNG);

            // Create a new input Asset and upload the specified local video file into it.
            Asset asset = createInputAsset(manager, config.getResourceGroup(), config.getAccountName(), inputAssetName,
                INPUT_MP4_RESOURCE);

            // Use the name of the created input asset to create the job input.
            JobInput jobInput = new JobInputAsset().withAssetName(inputAssetName);

            // Output from the encoding Job must be written to an Asset, so let's create one
            Asset outputAsset = createOutputAsset(manager, config.getResourceGroup(), config.getAccountName(),
                    outputAssetName);

            Job job = submitJob(manager, config.getResourceGroup(), config.getAccountName(),
                    CUSTOM_TWO_LAYER_MP4_PNG, jobName, inputAssetName, outputAsset.name());

            job = waitForJobToFinish(manager, config.getResourceGroup(), config.getAccountName(),
                    CUSTOM_TWO_LAYER_MP4_PNG, jobName);

            if (job.state() == JobState.FINISHED) {
                System.out.println("Job finished.");
                File outputFolder = new File(OUTPUT_FOLDER_NAME);
                if (outputFolder.exists() && !outputFolder.isDirectory()) {
                    outputFolder = new File(OUTPUT_FOLDER_NAME + uniqueness);
                }

                if (!outputFolder.exists()) {
                    outputFolder.mkdir();
                }

                downloadOutputAsset(manager, config.getResourceGroup(), config.getAccountName(), outputAsset.name(),
                        outputFolder);

                StreamingLocator locator = getStreamingLocator(manager, config.getResourceGroup(), config.getAccountName(), outputAsset.name(), locatorName);

                List<String> urls = getStreamingUrls(manager, config.getResourceGroup(), config.getAccountName(), locator.name());

                for (String url: urls) {
                    System.out.println(url);
                }
            }

            System.out.println("To try streaming, copy and paste the Streaming URL into the Azure Media Player at 'http://aka.ms/azuremediaplayer'.");
            System.out.println("When finished, press ENTER to cleanup.");
            System.out.flush();
            scanner.nextLine();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            System.out.println("Cleaning up...");
            if (scanner != null) {
                scanner.close();
            }

            cleanup(manager, config.getResourceGroup(), config.getAccountName(), CUSTOM_TWO_LAYER_MP4_PNG, jobName, inputAssetName,
            outputAssetName, locatorName);
        }
    }

    /**
     * If the specified transform exists, return that transform. If the it does not
     * exist, creates a new transform with the specified output. In this case, the
     * output is set to encode a video using one of the built-in encoding presets.
     * 
     * @param manager       This is the entry poiint of Azure Media resource
     *                      management
     * @param resourceGroup The name of the resource group within the Azure
     *                      subscription
     * @param accountName   The Media Services account name
     * @param transformName The name of the transform
     * @return The transform found or created
     */
    private static Transform getOrCreateTransform(MediaManager manager, String resourceGroup, String accountName,
            String transformName) {
        Transform transform;
        try {
            transform = manager.transforms().getAsync(resourceGroup, accountName, transformName)
                .toBlocking().first();
        }
        catch (NoSuchElementException e) {
            transform = null;   // In case an exception is thrown
        }

        if (transform == null) {
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
                .withKeyFrameInterval(Period.seconds(2))    //Set the GOP interval to 2 seconds for both H264Layers
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
            formats.add(new Mp4Format().withFilenamePattern("Video-{Basename}-{Label}-{Bitrate}{Extension}"));
            formats.add(new PngFormat().withFilenamePattern("Thumbnail-{Basename}-{Index}{Extension}"));
            preset.withFormats(formats);

            transform = manager.transforms().define(transformName)
                .withExistingMediaservice(resourceGroup, accountName)
                .withOutputs(outputs)
                .withDescription("A simple custom encoding transform with 2 MP4 bitrates")
                .create();
        }

        return transform;
    }

    /**
     * Creates a new input Asset and uploads the specified local video file into it.
     * 
     * @param manager           This is the entry point of Azure Media resource
     *                          management
     * @param resourceGroupName The name of the resource group within the Azure
     *                          subscription
     * @param accountName       The Media Services account name
     * @param assetName         The name of the asset where the media file to uploaded to.
     * @param mediaFile         The path of a media file to be uploaded into the asset
     * @return The asset
     */
    private static Asset createInputAsset(MediaManager manager, String resourceGroupName, String accountName,
            String assetName, String mediaFile) throws Exception {
        // In this example, we are assuming that the asset name is unique.
        // If you already have an asset with the desired name, use the Assets.Get method
        // to get the existing asset. In Media Services v3, the Get method on entities
        // returns null
        // if the entity doesn't exist (a case-insensitive check on the name).
        // Call Media Services API to create an Asset.
        // This method creates a container in storage for the Asset.
        // The files (blobs) associated with the asset will be stored in this container.

        /*
        Asset asset = manager.assets().getAsync(resourceGroupName, accountName, assetName).toBlocking().first();
        if (asset != null) {
            // This should not happen
            // throw new Exception("Asset " + assetName + " already exists.");
        }
        */

        // Call Media Services API to create an Asset.
        // This method creates a container in storage for the Asset.
        // The files (blobs) associated with the asset will be stored in this container.
        Asset asset = manager.assets().define(assetName).withExistingMediaservice(resourceGroupName, accountName).create();

        ListContainerSasInput parameters = new ListContainerSasInput()
                .withPermissions(AssetContainerPermission.READ_WRITE).withExpiryTime(DateTime.now().plusHours(4));

        // Use Media Services API to get back a response that contains
        // SAS URL for the Asset container into which to upload blobs.
        // That is where you would specify read-write permissions
        // and the exparation time for the SAS URL.
        AssetContainerSas response = manager.assets()
                .listContainerSasAsync(resourceGroupName, accountName, assetName, parameters).toBlocking().first();

        URI sasUri = new URI(response.assetContainerSasUrls().get(0));

        // Use Storage API to get a reference to the Asset container
        // that was created by calling Asset's CreateOrUpdate method.
        CloudBlobContainer container = new CloudBlobContainer(sasUri);

        // Uploading from a local file:
        String fileToUpload = EncodingWithMESCustomPreset.class.getClassLoader().getResource(mediaFile).getPath(); // If the file in CLASSPATH as a resource
        File file = new File(fileToUpload);
        CloudBlockBlob blob = container.getBlockBlobReference(file.getName());

        // Use Strorage API to upload the file into the container in storage.
        blob.uploadFromFile(fileToUpload);

        /* Or if param mediaFile is a url, the Uploading would be:
        URL url = new URL(mediaFile);
        String path = url.getPath();
        File file = new File(path);
        CloudBlockBlob blob = container.getBlockBlobReference(file.getName());
        blob.upload(url.openStream(), -1);
        ** End of uploading from a url */

        return asset;
    }

    /**
     * Creates an ouput asset. The output from the encoding Job must be written to
     * an Asset.
     * 
     * @param manager           This is the entry poiint of Azure Media resource
     *                          management
     * @param resourceGroupName The name of the resource group within the Azure
     *                          subscription
     * @param accountName       The Media Services account name
     * @param assetName         The output asset name
     * @return
     */
    private static Asset createOutputAsset(MediaManager manager, String resourceGroupName, String accountName,
            String assetName) {
        /*
        Asset outputAsset = manager.assets().getAsync(resourceGroupName, accountName, assetName).toBlocking().first();
        if (outputAsset != null) {
            // Name collision! This should not happen because the output asset name is
            // genereted using UUID
            throw new Exception("Ouptput asset " + assetName + " already exists.");
        }
        */

        // In this example, we are assuming that the asset name is unique.
        Asset outputAsset = manager.assets().define(assetName).withExistingMediaservice(resourceGroupName, accountName)
                .create();

        return outputAsset;
    }

    /**
     * Submits a request to Media Services to apply the specified Transform to a
     * given input video.
     * 
     * @param manager           This is the entry poiint of Azure Media resource
     *                          management
     * @param resourceGroupName The name of the resource group within the Azure
     *                          subscription
     * @param accountName       The Media Services account name
     * @param transformName     The name of the transform
     * @param jobName           The (unique) name of the job
     * @param inputAssetName    The name of the input asset
     * @param outputAssetName   The (unique) name of the output asset that will
     *                          store the result of the encoding job
     * @return The job created
     */
    private static Job submitJob(MediaManager manager, String resourceGroupName, String accountName,
            String transformName, String jobName, String inputAssetName, String outputAssetName) {
        JobInput jobInput = new JobInputAsset().withAssetName(inputAssetName);

        JobOutput output = new JobOutputAsset().withAssetName(outputAssetName);
        List<JobOutput> jobOutputs = new ArrayList<>();
        jobOutputs.add(output);

        Job job = manager.jobs().define(jobName).withExistingTransform(resourceGroupName, accountName, transformName)
                .withInput(jobInput).withOutputs(jobOutputs).create();

        return job;
    }

    /**
     * Polls Media Services for the status of the Job.
     * 
     * @param manager       This is the entry poiint of Azure Media resource
     *                      management
     * @param resourceGroup The name of the resource group within the Azure
     *                      subscription
     * @param accountName   The Media Services account name
     * @param transformName The name of the transform
     * @param jobName       The name of the job you submitted
     * @return
     */
    private static Job waitForJobToFinish(MediaManager manager, String resourceGroup, String accountName,
            String transformName, String jobName) {
        final int SLEEP_INTERVAL = 60 * 1000;

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
     * Downloads the results from the specified output asset, so you can see what
     * you got.
     * 
     * @param manager       The entry poiint of Azure Media resource management
     * @param resourceGroup The name of the resource group within the Azure subscription
     * @param accountName   The Media Services account name
     * @param assetName     The output asset
     * @param outputFolder  The name of the folder into which to download the
     *                      results
     * @throws URISyntaxException
     * @throws StorageException
     * @throws IOException
     */
    private static void downloadOutputAsset(MediaManager manager, String resourceGroup, String accountName,
            String assetName, File outputFolder) throws URISyntaxException, StorageException, IOException {
        final int LIST_BLOBS_SEGMENT_MAX_RESULT = 5;
        
        ListContainerSasInput parameters = new ListContainerSasInput()
            .withPermissions(AssetContainerPermission.READ)
            .withExpiryTime(DateTime.now().plusHours(1));
        
        AssetContainerSas assetContainerSas = manager.assets()
            .listContainerSasAsync(resourceGroup, accountName, assetName, parameters)
            .toBlocking().first();

        URI containerSasUrl = new URI(assetContainerSas.assetContainerSasUrls().get(0));
        CloudBlobContainer container = new CloudBlobContainer(containerSasUrl);
        
        File directory = new File(outputFolder, assetName);
        directory.mkdirs();

        System.out.println("Downloading output results to " + directory.getPath() + "...");

        ResultContinuation continuationToken = null;

        do {
            ResultSegment<ListBlobItem> segment = container.listBlobsSegmented(null, true, EnumSet.noneOf(BlobListingDetails.class),
                LIST_BLOBS_SEGMENT_MAX_RESULT, continuationToken, null, null);

            for (ListBlobItem blobItem: segment.getResults()) {
                if (blobItem instanceof CloudBlockBlob) {
                    CloudBlockBlob blob = (CloudBlockBlob)blobItem;
                    File downloadTo = new File(directory, blob.getName());
                    blob.downloadToFile(downloadTo.getPath());
                }
            }
            continuationToken = segment.getContinuationToken();
        }
        while (continuationToken != null);

        System.out.println("Download complete.");
    }

    /**
     * Creates a StreamingLocator for the specified asset and with the specified streaming policy name.
     * Once the StreamingLocator is created the output asset is available to clients for playback.
     * @param manager       The entry poiint of Azure Media resource management
     * @param resourceGroup The name of the resource group within the Azure subscription
     * @param accountName   The Media Services account name
     * @param assetName     The name of the output asset
     * @param locatorName   The StreamingLocator name (unique in this case)
     * @return              The locator created
     */
    private static StreamingLocator getStreamingLocator(MediaManager manager, String resourceGroup, String accountName,
        String assetName, String locatorName) {
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
     * @param manager       The entry poiint of Azure Media resource management
     * @param resourceGroup The name of the resource group within the Azure subscription
     * @param accountName   The Media Services account name
     * @param locatorName   The name of the StreamingLocator that was created
     * @return              List of streaming urls
     */
    private static List<String> getStreamingUrls(MediaManager manager, String resourceGroup, String accountName,
        String locatorName) {
        List<String> streamingUrls = new ArrayList<>();

        StreamingEndpoint streamingEndpoint = manager.streamingEndpoints()
            .getAsync(resourceGroup, accountName, STREAMING_ENDPOINT_NAME)
            .toBlocking().first();

        if (streamingEndpoint != null) {
            if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                manager.streamingEndpoints().startAsync(resourceGroup, accountName, STREAMING_ENDPOINT_NAME).await();
            }
        }

        ListPathsResponse paths = manager.streamingLocators().listPathsAsync(resourceGroup, accountName, locatorName)
            .toBlocking().first();
        
        for (StreamingPath path: paths.streamingPaths()) {
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
     * @param manager               The entry poiint of Azure Media resource management
     * @param resourceGroupName     The name of the resource group within the Azure subscription
     * @param accountName           The Media Services account name
     * @param transformName         The transform name
     * @param jobName               The job name
     * @param inputAssetName        The input asset name
     * @param outputAssetName       The output asset name
     * @param streamingLocatorName  The streaming locator name
     */
    private static void cleanup(MediaManager manager, String resourceGroupName, String accountName, String transformName, String jobName,
        String inputAssetName, String outputAssetName, String streamingLocatorName) {
        if (manager == null) {
            return;
        }

        manager.jobs().deleteAsync(resourceGroupName, accountName, transformName, jobName).await();
        manager.assets().deleteAsync(resourceGroupName, accountName, inputAssetName).await();
        manager.assets().deleteAsync(resourceGroupName, accountName, outputAssetName).await();

        manager.streamingLocators().deleteAsync(resourceGroupName, accountName, streamingLocatorName).await();
    }
}
