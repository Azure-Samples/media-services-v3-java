// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.Arrays;

import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.ListBlobsOptions;
import org.apache.commons.codec.binary.Base64;

import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ApiErrorException;
import com.microsoft.azure.management.mediaservices.v2020_05_01.Asset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.BuiltInStandardEncoderPreset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.CbcsDrmConfiguration;
import com.microsoft.azure.management.mediaservices.v2020_05_01.CommonEncryptionCbcs;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ContentKeyPolicy;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ContentKeyPolicyFairPlayConfiguration;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ContentKeyPolicyFairPlayOfflineRentalConfiguration;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ContentKeyPolicyFairPlayRentalAndLeaseKeyType;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ContentKeyPolicyOpenRestriction;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ContentKeyPolicyOption;
import com.microsoft.azure.management.mediaservices.v2020_05_01.DefaultKey;
import com.microsoft.azure.management.mediaservices.v2020_05_01.EnabledProtocols;
import com.microsoft.azure.management.mediaservices.v2020_05_01.EncoderNamedPreset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.Job;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobInputHttp;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobOutput;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobOutputAsset;
import com.microsoft.azure.management.mediaservices.v2020_05_01.JobState;
import com.microsoft.azure.management.mediaservices.v2020_05_01.ListPathsResponse;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingEndpoint;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingEndpointResourceState;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingLocator;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingPath;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingPolicy;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingPolicyContentKeys;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingPolicyFairPlayConfiguration;
import com.microsoft.azure.management.mediaservices.v2020_05_01.StreamingPolicyStreamingProtocol;
import com.microsoft.azure.management.mediaservices.v2020_05_01.Transform;
import com.microsoft.azure.management.mediaservices.v2020_05_01.TransformOutput;
import com.microsoft.azure.management.mediaservices.v2020_05_01.implementation.MediaManager;
import com.microsoft.rest.LogLevel;

public class OfflineFairPlay {
    private static final String ADAPTIVE_STREAMING_TRANSFORM_NAME = "MyTransformWithAdaptiveStreamingPreset";
    private static final String CONTENT_KEY_POLICY_NAME = "FairPlayContentKeyPolicy";
    private static final String BASE_URI = "https://nimbuscdn-nimbuspm.streaming.mediaservices.windows.net/2b533311-b215-4409-80af-529c3e853622/";
    private static final String MP4_FILE_NAME = "Ignite-short.mp4";
    private static final String DEFAULT_STREAMING_ENDPOINT_NAME = "se"; // Please change this to your Streaming Endpoint name.
    private static final String FAIRPLAY_STREAMING_POLICY_NAME = "FairPlayCustomStreamingPolicyName";

    public static void main(String[] args) {
        ConfigWrapper config = new ConfigWrapper();
        runOfflineFairPLayTest(config);

        config.close();
        System.exit(0);
    }

    /**
     * Run the sample.
     *
     * @param config This param is of type ConfigWrapper, which reads values from
     *               local configuration file.
     */
    private static void runOfflineFairPLayTest(ConfigWrapper config) {
        // Connect to media services, please see https://docs.microsoft.com/en-us/azure/media-services/latest/configure-connect-java-howto
        // for details.
        ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(config.getAadClientId(),
                config.getAadTenantId(), config.getAadSecret(), AzureEnvironment.AZURE);
        credentials.withDefaultSubscriptionId(config.getSubscriptionId());

        // Get MediaManager, the entry point to Azure Media resource management.
        MediaManager manager = MediaManager.configure().withLogLevel(LogLevel.BODY_AND_HEADERS)
                .authenticate(credentials, credentials.defaultSubscriptionId());
        // Signed in.

        // Create a unique suffix so that we don't have name collisions if you run the
        // sample multiple times without cleaning up.
        UUID uuid = UUID.randomUUID();
        String uniqueness = uuid.toString();
        String jobName = "job-" + uniqueness;
        String outputAssetName = "output-" + uniqueness;
        String locatorName = "locator-" + uniqueness;

        MediaServicesEventProcessor eventProcessorHost = null;
        boolean stopEndpoint = false;

        Scanner scanner = new Scanner(System.in);

        try {
            // Ensure that you have the desired encoding Transform. This is really a one
            // time setup operation.
            Transform transform = getOrCreateTransform(manager, config.getResourceGroup(), config.getAccountName(),
                    ADAPTIVE_STREAMING_TRANSFORM_NAME);

            // Output from the encoding Job must be written to an Asset, so let's create one
            Asset outputAsset = createOutputAsset(manager, config.getResourceGroup(), config.getAccountName(),
                    outputAssetName);

            Job job = submitJob(manager, config.getResourceGroup(), config.getAccountName(),
                    transform.name(), outputAsset.name(), jobName);

            long startedTime = System.currentTimeMillis();

            try {
                // First we will try to process Job events through Event Hub in real-time. If this fails for any reason,
                // we will fall-back on polling Job status instead.

                System.out.println();
                System.out.println("Creating an event processor host to process events from Event Hub...");

                String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=" +
                        config.getStorageAccountName() +
                        ";AccountKey=" + config.getStorageAccountKey() + ";EndpointSuffix=core.windows.net";

                // Cleanup storage container. We will config Event Hub to use the storage container configured in appsettings.json.
                // All the blobs in <The container configured in appsettings.json> will be deleted.
                BlobServiceAsyncClient client = new BlobServiceClientBuilder()
                        .connectionString(storageConnectionString)
                        .buildAsyncClient();
                BlobContainerAsyncClient container = client.getBlobContainerAsyncClient(config.getStorageContainerName());
                container.listBlobs().subscribe(blobItem -> {
                            container.getBlobAsyncClient(blobItem.getName()).delete();
                        });

                // Create a new host to process events from an Event Hub.
                Object monitor = new Object();
                eventProcessorHost = new MediaServicesEventProcessor(jobName, monitor, null,
                        config.getEventHubConnectionString(), config.getEventHubName(),
                        container);

                // Define a task to wait for the job to finish.
                Callable<String> jobTask = () -> {
                    synchronized (monitor) {
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
                } else {
                    // Timeout happened. Switch to polling method.
                    synchronized (monitor) {
                        monitor.notify();
                    }

                    throw new Exception("Timeout happened.");
                }

                // Get the latest status of the job.
                job = manager.jobs().getAsync(config.getResourceGroup(), config.getAccountName(), transform.name(), jobName).toBlocking().first();
            } catch (Exception e) {
                System.out.println("Warning: Failed to connect to Event Hub, please see README for Event Hub and storage settings.");
                // if Event Grid or Event Hub is not configured, We will fall-back on polling instead.
                // Polling is not a recommended best practice for production applications because of the latency it introduces.
                // Overuse of this API may trigger throttling. Developers should instead use Event Grid.
                System.out.println("Failed to start Event Grid monitoring, will use polling job status instead...");
                job = waitForJobToFinish(manager, config.getResourceGroup(), config.getAccountName(),
                        transform.name(), jobName);
            }

            long elapsed = (System.currentTimeMillis() - startedTime) / 1000; // Elapsed time in seconds
            System.out.println("Job elapsed time: " + elapsed + " second(s).");

            if (job.state() == JobState.FINISHED) {
                // Create the content key policy that configures how the content key is delivered
                // to end clients via the Key Delivery component of Azure Media Services.
                ContentKeyPolicy policy = getOrCreateContentKeyPolicy(manager, config, CONTENT_KEY_POLICY_NAME);

                StreamingLocator locator = createStreamingLocator(manager, config.getResourceGroup(), config.getAccountName(),
                        outputAssetName, locatorName, policy.name());

                StreamingEndpoint streamingEndpoint = manager.streamingEndpoints()
                        .getAsync(config.getResourceGroup(), config.getAccountName(), DEFAULT_STREAMING_ENDPOINT_NAME)
                        .toBlocking().first();

                if (streamingEndpoint != null) {
                    // Start The Streaming Endpoint if it is not running.
                    if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                        manager.streamingEndpoints().startAsync(config.getResourceGroup(), config.getAccountName(), DEFAULT_STREAMING_ENDPOINT_NAME).await();

                        // We started the endpoint, we should stop it in cleanup.
                        stopEndpoint = true;
                    }

                    String hlsPath = getHlsStreamingUrl(manager, config.getResourceGroup(), config.getAccountName(), locator.name(), streamingEndpoint);

                    System.out.println();
                    System.out.println("HLS url can be played on your Apple device:");
                    System.out.println(hlsPath);
                    System.out.println();
                } else {
                    System.out.println("Could not find streaming endpoint: " + DEFAULT_STREAMING_ENDPOINT_NAME);
                }

                System.out.println("When finished testing press ENTER to cleanup.");
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
            System.out.println("Cleaning up...");
            if (scanner != null) {
                scanner.close();
            }

            if (eventProcessorHost != null) {
                eventProcessorHost.stop();
                eventProcessorHost = null;
            }

            cleanup(manager, config.getResourceGroup(), config.getAccountName(), ADAPTIVE_STREAMING_TRANSFORM_NAME, jobName,
                    outputAssetName, locatorName, CONTENT_KEY_POLICY_NAME, stopEndpoint, DEFAULT_STREAMING_ENDPOINT_NAME);
        }
    }

    /**
     * If the specified transform exists, get that transform. If the it does not
     * exist, creates a new transform with the specified output. In this case, the
     * output is set to encode a video using one of the built-in encoding presets.
     *
     * @param manager       The entry point of Azure Media resource management.
     * @param resourceGroup The name of the resource group within the Azure subscription.
     * @param accountName   The Media Services account name.
     * @param transformName The name of the transform.
     * @return The transform found or created.
     */
    private static Transform getOrCreateTransform(MediaManager manager, String resourceGroup, String accountName,
                                                  String transformName) {
        Transform transform;
        try {
            // Does a Transform already exist with the desired name? Assume that an existing
            // Transform with the desired name.
            transform = manager.transforms().getAsync(resourceGroup, accountName, transformName).toBlocking().first();
        } catch (NoSuchElementException e) {
            transform = null; // Media Services V3 throws an exception when not found.
        }

        if (transform == null) {
            // Start by defining the desired outputs.
            BuiltInStandardEncoderPreset preset = new BuiltInStandardEncoderPreset()
                    .withPresetName(EncoderNamedPreset.ADAPTIVE_STREAMING);
            TransformOutput transformOutput = new TransformOutput().withPreset(preset);
            List<TransformOutput> outputs = new ArrayList<TransformOutput>();
            outputs.add(transformOutput);

            // Create the Transform with the output defined above.
            System.out.println("Creating a transform...");
            transform = manager.transforms().define(transformName).withExistingMediaservice(resourceGroup, accountName)
                    .withOutputs(outputs).create();
        }

        return transform;
    }

    /**
     * Creates an output asset. The output from the encoding Job must be written to
     * an Asset.
     *
     * @param manager           This is the entry point of Azure Media resource management.
     * @param resourceGroupName The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param assetName         The output asset name.
     * @return The output asset created.
     */
    private static Asset createOutputAsset(MediaManager manager, String resourceGroupName, String accountName,
                                           String assetName) {
        Asset outputAsset;
        try {
            outputAsset = manager.assets().getAsync(resourceGroupName, accountName, assetName).toBlocking().first();
        } catch (NoSuchElementException nse) {
            outputAsset = null;
        }

        if (outputAsset == null) {
            System.out.println("Creating an output asset...");
            outputAsset = manager.assets().define(assetName)
                    .withExistingMediaservice(resourceGroupName, accountName)
                    .create();
        } else {
            // The asset already exists and we are going to overwrite it. In your application, if you don't want to overwrite
            // an existing asset, use an unique name.
            System.out.println("Warning: The asset named " + assetName + "already exists. It will be overwritten.");
        }

        return outputAsset;
    }

    /**
     * Submits a request to Media Services to apply the specified Transform to a
     * given input video.
     *
     * @param manager           This is the entry point of Azure Media resource management.
     * @param resourceGroupName The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param transformName     The name of the transform.
     * @param outputAssetName   The (unique) name of the output asset that will
     *                          store the result of the encoding job.
     * @param jobName           The (unique) name of the job.
     * @return The job created.
     */
    private static Job submitJob(MediaManager manager, String resourceGroupName, String accountName,
                                 String transformName, String outputAssetName, String jobName) {
        // This example shows how to encode from any HTTPs source URL - a new feature of the v3 API.
        // Change the URL to any accessible HTTPs URL or SAS URL from Azure.
        List<String> files = new ArrayList<>();
        files.add(MP4_FILE_NAME);
        JobInputHttp jobInput = new JobInputHttp().withBaseUri(BASE_URI);
        jobInput.withFiles(files);

        JobOutput output = new JobOutputAsset().withAssetName(outputAssetName);
        List<JobOutput> jobOutputs = new ArrayList<>();
        jobOutputs.add(output);

        // In this example, we are assuming that the job name is unique.
        // If you already have a job with the desired name, use the Jobs.Get method
        // to get the existing job. In Media Services v3, the Get method on entities returns null
        // if the entity doesn't exist (a case-insensitive check on the name).
        Job job;
        try {
            System.out.println("Creating a job...");
            job = manager.jobs().define(jobName)
                    .withExistingTransform(resourceGroupName, accountName, transformName)
                    .withInput(jobInput)
                    .withOutputs(jobOutputs)
                    .create();
        } catch (ApiErrorException exception) {
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
     * @param jobName       The name of the job you submitted.
     * @return The job.
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
     * Create the content key policy that configures how the content key is
     * delivered to end clients via the Key Delivery component of Azure Media
     * Services.
     *
     * @param manager              The entry point of Azure Media resource
     *                             management.
     * @param contentKeyPolicyName The name of the content key policy resource.
     * @return The content key policy.
     * @throws IOException
     * @throws FileNotFoundException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    private static ContentKeyPolicy getOrCreateContentKeyPolicy(MediaManager manager, ConfigWrapper config,
                                                                String contentKeyPolicyName) throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
            FileNotFoundException, IOException {
        ContentKeyPolicy policy;
        try {
            // Get the policy if exists.
            policy = manager.contentKeyPolicies().getAsync(config.getResourceGroup(), config.getAccountName(), contentKeyPolicyName)
                    .toBlocking().first();
        } catch (NoSuchElementException e) {
            policy = null;
        }

        if (policy == null) {
            ContentKeyPolicyOpenRestriction restriction = new ContentKeyPolicyOpenRestriction();

            // Create a configuration for FairPlay licenses.
            ContentKeyPolicyFairPlayConfiguration fairPlayConfig = configureFairPlayLicenseTemplate(config.getAskHex(),
                    config.getFairPlayPfxPath(), config.getFairPlayPfxPassword());

            List<ContentKeyPolicyOption> options = new ArrayList<>();

            options.add(new ContentKeyPolicyOption()
                    .withConfiguration(fairPlayConfig)
                    .withRestriction(restriction));

            // Content Key Policy does not exist, create one.
            System.out.println("Creating a content key policy...");
            policy = manager.contentKeyPolicies().define(contentKeyPolicyName)
                    .withExistingMediaservice(config.getResourceGroup(), config.getAccountName()).withOptions(options).create();
        }

        return policy;
    }

    /***
     * Configure a FairPlay license template.
     * @param askHex                The ASK Hex string.
     * @param fairPlayPfxPath       The path of the PFX file.
     * @param fairPlayPfxPassword   The password of the PFX.
     * @return ContentKeyPolicyFairPlayConfiguration
     * @throws KeyStoreException
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     */
    private static ContentKeyPolicyFairPlayConfiguration configureFairPlayLicenseTemplate(String askHex,
                                                                                          String fairPlayPfxPath, String fairPlayPfxPassword) throws IOException {

        byte[] askBytes = hexStringToBytes(askHex);

        File pfxFile = new File(fairPlayPfxPath);
        InputStream inputStream = new FileInputStream(pfxFile);
        byte[] buf = new byte[(int) pfxFile.length()];
        inputStream.read(buf, 0, buf.length);
        inputStream.close();

        String certBase64 = Base64.encodeBase64String(buf);

        ContentKeyPolicyFairPlayConfiguration objContentKeyPolicyPlayReadyConfiguration = new ContentKeyPolicyFairPlayConfiguration()
                .withAsk(askBytes)
                .withFairPlayPfx(certBase64)
                .withFairPlayPfxPassword(fairPlayPfxPassword)
                .withRentalAndLeaseKeyType(ContentKeyPolicyFairPlayRentalAndLeaseKeyType.DUAL_EXPIRY)
                .withRentalDuration(0)
                .withOfflineRentalConfiguration(new ContentKeyPolicyFairPlayOfflineRentalConfiguration()
                        .withStorageDurationSeconds(300000)
                        .withPlaybackDurationSeconds(500000));

        return objContentKeyPolicyPlayReadyConfiguration;
    }

    /***
     * Create a streaming locator.
     * @param manager               The entry point of Azure Media resource management.
     * @param resourceGroup         The name of the resource group within the Azure subscription.
     * @param accountName           The Media Services account name.
     * @param assetName             The name of the asset to be streamed.
     * @param locatorName           The locator name.
     * @param contentPolicyName     The content key policy name.
     * @return StreamingLocator
     */
    private static StreamingLocator createStreamingLocator(MediaManager manager, String resourceGroup, String accountName,
                                                           String assetName, String locatorName, String contentPolicyName) {
        StreamingPolicy customStreamingPolicy = getOrCreateCustomStreamingPloliyForFairPlay(manager, resourceGroup,
                accountName, FAIRPLAY_STREAMING_POLICY_NAME);

        System.out.println("Creating a streaming locator...");
        StreamingLocator locator = manager.streamingLocators().define(locatorName)
                .withExistingMediaservice(resourceGroup, accountName)
                .withAssetName(assetName)
                .withStreamingPolicyName(customStreamingPolicy.name())
                .withDefaultContentKeyPolicyName(contentPolicyName)
                .create();

        return locator;
    }

    /***
     * Get or create a custom FairPlay streaming policy.
     * @param manager               The entry point of Azure Media resource management.
     * @param resourceGroup         The name of the resource group within the Azure subscription.
     * @param accountName           The Media Services account name.
     * @param streamingPolicyName   The name of the streaming policy.
     * @return StreamingPolicy
     */
    private static StreamingPolicy getOrCreateCustomStreamingPloliyForFairPlay(MediaManager manager, String resourceGroup,
                                                                               String accountName, String streamingPolicyName) {
        StreamingPolicy streamingPolicy;
        try {
            streamingPolicy = manager.streamingPolicies().getAsync(resourceGroup, accountName, streamingPolicyName).toBlocking().first();
        } catch (NoSuchElementException e) {
            streamingPolicy = null;
        }

        if (streamingPolicy == null) {
            streamingPolicy = manager.streamingPolicies().define(streamingPolicyName)
                    .withExistingMediaservice(resourceGroup, accountName)
                    .withCommonEncryptionCbcs(new CommonEncryptionCbcs()
                            .withDrm(new CbcsDrmConfiguration()
                                    .withFairPlay(new StreamingPolicyFairPlayConfiguration()
                                            .withAllowPersistentLicense(true)))
                            .withEnabledProtocols(new EnabledProtocols()
                                    .withHls(true)
                                    .withDash(true))    // Even though DASH under CBCS is not supported for either CSF or CMAF, HLS-CMAF-CBCS uses DASH-CBCS fragments in its HLS playlist
                            .withContentKeys(new StreamingPolicyContentKeys()
                                    .withDefaultKey(new DefaultKey()
                                            .withLabel("CBCS_DefaultKeyLabel")
                                    )
                            )
                    )
                    .create();
        }

        return streamingPolicy;
    }

    /**
     * Builds the HLS streaming URL.
     *
     * @param manager           The entry point of Azure Media resource management.
     * @param resourceGroup     The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param locatorName       The name of the StreamingLocator that was created.
     * @param streamingEndpoint The streaming endpoint.
     * @return HLS url.
     */
    private static String getHlsStreamingUrl(MediaManager manager, String resourceGroup, String accountName, String locatorName,
                                             StreamingEndpoint streamingEndpoint) {
        String hlsPath = "";

        ListPathsResponse paths = manager.streamingLocators()
                .listPathsAsync(resourceGroup, accountName, locatorName)
                .toBlocking().first();

        for (StreamingPath path : paths.streamingPaths()) {
            if (path.paths().size() > 0) {
                StringBuilder uriBuilder = new StringBuilder();
                uriBuilder.append("https://").append(streamingEndpoint.hostName());

                // Look for just the HLS path and generate a URL for Apple device to playback the encrypted content.
                if (path.streamingProtocol() == StreamingPolicyStreamingProtocol.HLS) {
                    uriBuilder.append("/").append(path.paths().get(0));
                    hlsPath = uriBuilder.toString();
                }
            }
        }

        return hlsPath;
    }

    /**
     * Deletes the jobs and assets that were created.
     * Generally, you should clean up everything except objects
     * that you are planning to reuse (typically, you will reuse Transforms, and you will persist StreamingLocators).
     *
     * @param manager               The entry point of Azure Media resource management.
     * @param resourceGroup         The name of the resource group within the Azure subscription.
     * @param accountName           The Media Services account name.
     * @param transformName         The transform name.
     * @param jobName               The job name.
     * @param assetName             The asset name.
     * @param locatorName           The name of the StreamingLocator that was created.
     * @param contentKeyPolicyName  The content key policy name.
     * @param stopEndpoint          Stop endpoint if true, otherwise keep endpoint running.
     * @param streamingEndpointName The endpoint name.
     */
    private static void cleanup(MediaManager manager, String resourceGroup, String accountName, String transformName, String jobName,
                                String assetName, String locatorName, String contentKeyPolicyName, boolean stopEndpoint, String streamingEndpointName) {
        if (manager == null) {
            return;
        }

        manager.jobs().deleteAsync(resourceGroup, accountName, transformName, jobName).await();
        manager.assets().deleteAsync(resourceGroup, accountName, assetName).await();

        manager.streamingLocators().deleteAsync(resourceGroup, accountName, locatorName).await();
        manager.contentKeyPolicies().deleteAsync(resourceGroup, accountName, contentKeyPolicyName).await();

        if (stopEndpoint) {
            // Because we started the endpoint, we'll stop it.
            manager.streamingEndpoints().stopAsync(resourceGroup, accountName, streamingEndpointName).await();
        } else {
            // We will keep the endpoint running because it was not started by this sample. Please note, There are costs to keep it running.
            // Please refer https://azure.microsoft.com/en-us/pricing/details/media-services/ for pricing.
            System.out.println("The endpoint ''" + streamingEndpointName + "'' is running. To halt further billing on the endpoint, please stop it in azure portal or AMS Explorer.");
        }
    }

    private static byte[] hexStringToBytes(String hexString) {
        if (hexString == null) {
            return null;
        }

        byte[] bytes = new byte[(hexString.length() + 1) / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hexString.substring(2 * i, 2 * i + 2), 16);
        }

        return bytes;
    }
}
