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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.Arrays;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.eventprocessorhost.EventProcessorHost;
import com.microsoft.azure.eventprocessorhost.EventProcessorOptions;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Asset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AssetContainerPermission;
import com.microsoft.azure.management.mediaservices.v2018_07_01.AssetContainerSas;
import com.microsoft.azure.management.mediaservices.v2018_07_01.InsightsType;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Job;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobInput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobInputAsset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobOutput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobOutputAsset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.JobState;
import com.microsoft.azure.management.mediaservices.v2018_07_01.ListContainerSasInput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Preset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.Transform;
import com.microsoft.azure.management.mediaservices.v2018_07_01.TransformOutput;
import com.microsoft.azure.management.mediaservices.v2018_07_01.VideoAnalyzerPreset;
import com.microsoft.azure.management.mediaservices.v2018_07_01.implementation.MediaManager;
import com.microsoft.azure.storage.ResultContinuation;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.rest.LogLevel;

import org.joda.time.DateTime;

public class VideoAnalyzer {
    private static final String VIDEO_ANALYZER_TRANSFORM_NAME = "MyVideoAnalyzerTransformName";
    private static final String INPUT_MP4_RESOURCE = "video/ignite.mp4";
    private static final String OUTPUT_FOLDER_NAME = "Output";

    // Please make sure you have set configurations in resources/conf/appsettings.json
    public static void main(String[] args) {
        ConfigWrapper config = new ConfigWrapper();
        runAnalyzer(config);

        config.close();
        System.exit(0);
    }

    /**
     * Run the sample.
     * @param config This param is of type ConfigWrapper, which reads values from a
     *               local configuration file.
     */
    private static void runAnalyzer(ConfigWrapper config) {
        // Connect to media services, please see https://docs.microsoft.com/en-us/azure/media-services/latest/configure-connect-java-howto
        // for details.
        ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(config.getAadClientId(),
                config.getAadTenantId(), config.getAadSecret(), AzureEnvironment.AZURE);
        credentials.withDefaultSubscriptionId(config.getSubscriptionId());

        // Get MediaManager, the entry point to Azure Media resource management.
        MediaManager manager = MediaManager.configure().withLogLevel(LogLevel.BODY_AND_HEADERS)
                .authenticate(credentials, credentials.defaultSubscriptionId());
        // Signed in.

        // Create a unique suffix so that we don't have name collisions if you run the sample
        // multiple times without cleaning up.
        UUID uuid = UUID.randomUUID();
        String uniqueness = uuid.toString();
        String jobName = "job-" + uniqueness;
        String outputAssetName = "output-" + uniqueness;
        String inputAssetName = "input-" + uniqueness;

        Scanner scanner = new Scanner(System.in);

        try {
            // Create a video analyzer preset with both video and audio insights.
            Preset preset = new VideoAnalyzerPreset().withInsightsToExtract(InsightsType.VIDEO_INSIGHTS_ONLY);

            // Ensure that you have the desired encoding Transform. This is really a one
            // time setup operation.
            Transform videoAnalyzerTransform = getOrCreateTransform(manager, config.getResourceGroup(),
                    config.getAccountName(), VIDEO_ANALYZER_TRANSFORM_NAME, preset);

            // Create a new input Asset and upload the specified local video file into it.
            createInputAsset(manager, config.getResourceGroup(), config.getAccountName(), inputAssetName,
                    INPUT_MP4_RESOURCE);

            // Output from the encoding Job must be written to an Asset, so let's create one
            Asset outputAsset = createOutputAsset(manager, config.getResourceGroup(), config.getAccountName(),
                    outputAssetName);

            Job job = submitJob(manager, config.getResourceGroup(), config.getAccountName(),
                videoAnalyzerTransform.name(), jobName, inputAssetName, outputAsset.name());

            long startedTime = System.currentTimeMillis();
            
            EventProcessorHost eventProcessorHost = null;
            try {
                // First we will try to process Job events through Event Hub in real-time. If this fails for any reason,
                // we will fall-back on polling Job status instead.

                System.out.println("Creating an event processor host to process events from Event Hub...");

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
                    "$Default",         // The name of the consumer group to use when receiving from the Event Hub. DefaultConsumerGroupName is used here.
                    config.getEventHubConnectionString(),
                    storageConnectionString,
                    config.getStorageContainerName()
                );

                Object monitor = new Object();
                CompletableFuture<Void> registerResult = eventProcessorHost
                    .registerEventProcessorFactory(new MediaServicesEventProcessorFactory(jobName, monitor), EventProcessorOptions .getDefaultOptions());
                registerResult.get();

                // Define a task to wait for the job to finish.
                Callable<String> jobTask = () -> {
                    synchronized(monitor) {
                        monitor.wait();
                    }
                    return "Job";
                };

                // Define another task
                Callable<String> timeoutTask = () -> {
                    TimeUnit.MINUTES.sleep(30);
                    return "Timeout";
                };

                ExecutorService executor = Executors.newFixedThreadPool(2);
                List<Callable<String>> tasks = Arrays.asList(jobTask, timeoutTask);

                String result = executor.invokeAny(tasks);
                if (result.equalsIgnoreCase("Job")) {
                    // Job finished. Shutdown timeout.
                    executor.shutdownNow();
                }
                else {
                    // Timeout happened. Switch to polling method.
                    synchronized(monitor) {
                        monitor.notify();
                    }

                    throw new Exception("Timeout happened.");
                }

                // Get the latest status of the job.
                job = manager.jobs().getAsync(config.getResourceGroup(), config.getAccountName(), VIDEO_ANALYZER_TRANSFORM_NAME, jobName).toBlocking().first();
            }
            catch (Exception e)
            {
                // if Event Grid or Event Hub is not configured, We will fall-back on polling instead.
                // Polling is not a recommended best practice for production applications because of the latency it introduces.
                // Overuse of this API may trigger throttling. Developers should instead use Event Grid.
                job = waitForJobToFinish(manager, config.getResourceGroup(), config.getAccountName(),
                    VIDEO_ANALYZER_TRANSFORM_NAME, jobName);
            }
            finally {
                if (eventProcessorHost != null)
                {
                    System.out.println("Job final state received, unregistering event processor...");

                    // Disposes of the Event Processor Host.
                    eventProcessorHost.unregisterEventProcessor();
                    System.out.println();
                }
            }

            long elapsed = (System.currentTimeMillis() - startedTime) / 1000; // Elapsed time in seconds
            System.out.println("Job elapsed time: " + elapsed + " second(s).");

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

                // Download the result to output folder.
                downloadOutputAsset(manager, config.getResourceGroup(), config.getAccountName(), outputAsset.name(),
                        outputFolder);
            }

            System.out.println("Press ENTER to continue.");
            scanner.nextLine();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }

            cleanup(manager, config.getResourceGroup(), config.getAccountName(), VIDEO_ANALYZER_TRANSFORM_NAME, jobName,
                    inputAssetName, outputAssetName);
        }
    }

    /**
     * If the specified transform exists, get that transform. If it does not
     * exist, creates a new transform with the specified output. In this case, the
     * output is set to encode a video using the encoding preset created earlier.
     * 
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param transformName The name of the transform.
     * @param preset        The preset.
     * @return The transform found or created.
     */
    private static Transform getOrCreateTransform(MediaManager manager, String resourceGroup, String accountName,
            String transformName, Preset preset) {
        Transform transform;
        try {
            // Does a Transform already exist with the desired name? Assume that an existing
            // Transform with the desired name
            transform = manager.transforms().getAsync(resourceGroup, accountName, transformName).toBlocking().first();
        } catch (NoSuchElementException e) {
            transform = null; // In case an exception is thrown
        }
        
        if (transform == null) {
            TransformOutput transformOutput = new TransformOutput().withPreset(preset);
            List<TransformOutput> outputs = new ArrayList<TransformOutput>();
            outputs.add(transformOutput);

            // Create the Transform with the outputs defined above
            System.out.println("Creating a transform...");
            transform = manager.transforms().define(transformName)
                .withExistingMediaservice(resourceGroup, accountName)
                .withOutputs(outputs)
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
     * @param assetName         The asset name.
     * @param InputMP4FileName  The file you want to upload into the asset.
     * @return The asset.
     */
    private static Asset createInputAsset(MediaManager manager, String resourceGroupName, String accountName,
            String assetName, String videoResource) throws Exception {
        // In this example, we are assuming that the asset name is unique.
        // Call Media Services API to create an Asset.
        // This method creates a container in storage for the Asset.
        // The files (blobs) associated with the asset will be stored in this container.
        System.out.println("Creating an input asset...");
        Asset asset = manager.assets().define(assetName).withExistingMediaservice(resourceGroupName, accountName)
                .create();

        ListContainerSasInput parameters = new ListContainerSasInput()
                .withPermissions(AssetContainerPermission.READ_WRITE).withExpiryTime(DateTime.now().plusHours(4));

        // Use Media Services API to get back a response that contains
        // SAS URL for the Asset container into which to upload blobs.
        // That is where you would specify read-write permissions
        // and the expiration time for the SAS URL.
        AssetContainerSas response = manager.assets()
                .listContainerSasAsync(resourceGroupName, accountName, assetName, parameters).toBlocking().first();

        URI sasUri = new URI(response.assetContainerSasUrls().get(0));

        // Use Storage API to get a reference to the Asset container.
        // That was created by calling Asset's create() method.
        CloudBlobContainer container = new CloudBlobContainer(sasUri);

        String fileToUpload = VideoAnalyzer.class.getClassLoader().getResource(videoResource).getPath();
        File file = new File(fileToUpload);
        CloudBlockBlob blob = container.getBlockBlobReference(file.getName());

        // Use Storage API to upload the file into the container in storage.
        System.out.println("Uploading a media file to the asset...");
        blob.uploadFromFile(fileToUpload);

        return asset;
    }

    /**
     * Creates an output asset. The output from the encoding Job must be written to
     * an Asset.
     * 
     * @param manager           This is the entry point of Azure Media resource
     *                          management.
     * @param resourceGroupName The name of the resource group within the Azure
     *                          subscription.
     * @param accountName       The Media Services account name.
     * @param assetName         The output asset name.
     * @return
     */
    private static Asset createOutputAsset(MediaManager manager, String resourceGroupName, String accountName,
            String assetName) {
        // In this example, we are assuming that the asset name is unique.
        System.out.println("Creating an output asset...");
        Asset outputAsset = manager.assets().define(assetName).withExistingMediaservice(resourceGroupName, accountName)
                .create();

        return outputAsset;
    }

    /**
     * Submits a request to Media Services to apply the specified Transform to a
     * given input video.
     * 
     * @param manager           This is the entry point of Azure Media resource
     *                          management.
     * @param resourceGroupName The name of the resource group within the Azure
     *                          subscription.
     * @param accountName       The Media Services account name.
     * @param transformName     The name of the transform.
     * @param jobName           The (unique) name of the job.
     * @param inputAssetName    The name of the input asset.
     * @param outputAssetName   The (unique) name of the output asset that will
     *                          store the result of the encoding job.
     * @return The job created
     */
    private static Job submitJob(MediaManager manager, String resourceGroupName, String accountName,
            String transformName, String jobName, String inputAssetName, String outputAssetName) {
        JobInput jobInput = new JobInputAsset().withAssetName(inputAssetName);

        // Call Media Services API to create a JobOutput and add it to a list.
        JobOutput output = new JobOutputAsset().withAssetName(outputAssetName);
        List<JobOutput> jobOutputs = new ArrayList<>();
        jobOutputs.add(output);

        // Call Media Services API to create the job.
        System.out.println("Creating a job...");
        Job job = manager.jobs().define(jobName)
            .withExistingTransform(resourceGroupName, accountName, transformName)
            .withInput(jobInput)
            .withOutputs(jobOutputs)
            .create();

        return job;
    }

    /**
     * Polls Media Services for the status of the Job.
     * 
     * @param manager       This is the entry point of Azure Media resource
     *                      management.
     * @param resourceGroup The name of the resource group within the Azure
     *                      subscription.
     * @param accountName   The Media Services account name.
     * @param transformName The name of the transform.
     * @param jobName       The name of the job you submitted.
     * @return              The job object.
     */
    private static Job waitForJobToFinish(MediaManager manager, String resourceGroup, String accountName,
            String transformName, String jobName) {
        final int SLEEP_INTERVAL = 60 * 1000;   // 1 minute.

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
     * Downloads the results from the specified output asset, so you can see what you get.
     * 
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param assetName     The output asset.
     * @param outputFolder  The name of the folder into which to download the results.
     * @throws URISyntaxException
     * @throws StorageException
     * @throws IOException
     */
    private static void downloadOutputAsset(MediaManager manager, String resourceGroup, String accountName,
            String assetName, File outputFolder) throws URISyntaxException, StorageException, IOException {
        // Specify read permission and 1 hour expiration time for the SAS URL.
        ListContainerSasInput parameters = new ListContainerSasInput()
            .withPermissions(AssetContainerPermission.READ)
            .withExpiryTime(DateTime.now().plusHours(1));

        // Call Media Services API to get SAS URLs.
        AssetContainerSas assetContainerSas = manager.assets()
            .listContainerSasAsync(resourceGroup, accountName, assetName, parameters)
            .toBlocking().first();

        // Use Storage API to get a reference to the Asset container.
        URI containerSasUrl = new URI(assetContainerSas.assetContainerSasUrls().get(0));
        CloudBlobContainer container = new CloudBlobContainer(containerSasUrl);

        File directory = new File(outputFolder, assetName);
        directory.mkdirs();

        System.out.println("Downloading output results to " + directory.getPath() + "...");
        System.out.println();

        // A continuation token for listing operations. Continuation tokens are used in methods that return a ResultSegment object.
        ResultContinuation continuationToken = null;

        do {
            // A non-negative integer value that indicates the maximum number of results to be returned at a time,
            // up to the per-operation limit of 5000. If this value is null, the maximum possible number of results
            // will be returned, up to 5000.
            Integer LIST_BLOBS_SEGMENT_MAX_RESULT = null;
            ResultSegment<ListBlobItem> segment = container.listBlobsSegmented(null, true,
                    EnumSet.noneOf(BlobListingDetails.class), LIST_BLOBS_SEGMENT_MAX_RESULT, continuationToken, null,
                    null);

            for (ListBlobItem blobItem : segment.getResults()) {
                if (blobItem instanceof CloudBlockBlob) {
                    CloudBlockBlob blob = (CloudBlockBlob) blobItem;
                    File downloadTo = new File(directory, blob.getName());

                    // Use Storage API to download the file.
                    blob.downloadToFile(downloadTo.getPath());
                }
            }
            continuationToken = segment.getContinuationToken();
        } while (continuationToken != null);

        System.out.println("Downloading completed.");
        System.out.println("Please check the result files in " + directory.getPath() + ".");
        System.out.println();
    }

    /**
     * Cleanup
     * 
     * @param manager           The entry point of Azure Media resource management.
     * @param resourceGroupName The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param transformName     The transform name.
     * @param jobName           The job name.
     * @param inputAssetName    The input asset name.
     * @param outputAssetName   The output asset name.
     */
    private static void cleanup(MediaManager manager, String resourceGroupName, String accountName,
            String transformName, String jobName, String inputAssetName, String outputAssetName) {
        if (manager == null) {
            return;
        }

        manager.jobs().deleteAsync(resourceGroupName, accountName, transformName, jobName).await();
        manager.assets().deleteAsync(resourceGroupName, accountName, inputAssetName).await();
        manager.assets().deleteAsync(resourceGroupName, accountName, outputAssetName).await();
    }
}

