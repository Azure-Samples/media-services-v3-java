// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.Arrays;

import com.azure.core.management.exception.ManagementException;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.naming.AuthenticationException;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.mediaservices.models.*;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.identity.ClientSecretCredentialBuilder;

public class OfflinePlayReadyAndWidevine {
    private static final String TRANSFORM_NAME = "MyTransform";
    private static final String CONTENT_KEY_POLICY_NAME = "DRMContentKeyPolicy";
    private static final String BASE_URI = "https://nimbuscdn-nimbuspm.streaming.mediaservices.windows.net/2b533311-b215-4409-80af-529c3e853622/";
    private static final String MP4_FILE_NAME = "Ignite-short.mp4";
    private static final String MULTI_DRM_CENC_STREAMING = "Predefined_MultiDrmCencStreaming";
    private static final String DEFAULT_STREAMING_ENDPOINT_NAME = "default";

    public static void main(String[] args) {
        ConfigWrapper config = new ConfigWrapper();
        runPlayReadyAndWidevineTest(config);

        config.close();
        System.exit(0);
    }

    /**
     * Run the sample.
     *
     * @param config This param is of type ConfigWrapper, which reads values from
     *               local configuration file.
     */
    private static void runPlayReadyAndWidevineTest(ConfigWrapper config) {
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
            List<TransformOutput> outputs = new ArrayList<>();
            outputs.add(new TransformOutput().withPreset(
                    new BuiltInStandardEncoderPreset().withPresetName(EncoderNamedPreset.CONTENT_AWARE_ENCODING)));

            // Create the transform.
            System.out.println("Creating a transform...");
            Transform transform = manager.transforms()
                    .define(TRANSFORM_NAME)
                    .withExistingMediaService(config.getResourceGroup(), config.getAccountName())
                    .withOutputs(outputs)
                    .create();
            System.out.println("Transform created");

            // Output from the encoding Job must be written to an Asset, so let's create
            // one. Note that we are using a unique asset name, there should not be a name
            // collision.
            System.out.println("Creating an output asset...");
            Asset outputAsset = manager.assets()
                    .define(outputAssetName)
                    .withExistingMediaService(config.getResourceGroup(), config.getAccountName())
                    .create();

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
                BlobContainerAsyncClient container = client
                        .getBlobContainerAsyncClient(config.getStorageContainerName());
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
                job = manager.jobs()
                        .get(config.getResourceGroup(), config.getAccountName(), transform.name(), jobName);
            } catch (Exception e) {
                System.out.println(
                        "Warning: Failed to connect to Event Hub, please refer README for Event Hub and storage settings.");
                // if Event Grid or Event Hub is not configured, We will fall-back on polling
                // instead.
                // Polling is not a recommended best practice for production applications
                // because of the latency it introduces.
                // Overuse of this API may trigger throttling. Developers should instead use
                // Event Grid.
                System.out.println("Failed to start Event Grid monitoring, will use polling job status instead...");
                job = waitForJobToFinish(manager, config.getResourceGroup(), config.getAccountName(),
                        transform.name(), jobName);
            }

            long elapsed = (System.currentTimeMillis() - startedTime) / 1000; // Elapsed time in seconds
            System.out.println("Job elapsed time: " + elapsed + " second(s).");

            if (job.state() == JobState.FINISHED) {
                // Create the content key policy that configures how the content key is delivered
                // to end clients via the Key Delivery component of Azure Media Services.
                ContentKeyPolicy policy = ensureContentKeyPolicyExists(manager, config.getResourceGroup(),
                        config.getAccountName(), CONTENT_KEY_POLICY_NAME);

                StreamingLocator locator = manager.streamingLocators().define(locatorName)
                        .withExistingMediaService(config.getResourceGroup(), config.getAccountName())
                        .withAssetName(outputAssetName)
                        .withStreamingPolicyName(MULTI_DRM_CENC_STREAMING)
                        .withDefaultContentKeyPolicyName(policy.name())
                        .create();

                StreamingEndpoint streamingEndpoint = manager.streamingEndpoints()
                        .get(config.getResourceGroup(), config.getAccountName(), DEFAULT_STREAMING_ENDPOINT_NAME);

                if (streamingEndpoint != null) {
                    // Start The Streaming Endpoint if it is not running.
                    if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                        manager.streamingEndpoints().start(config.getResourceGroup(), config.getAccountName(),
                                DEFAULT_STREAMING_ENDPOINT_NAME);

                        // We started the endpoint, we should stop it in cleanup.
                        stopEndpoint = true;
                    }

                    String dashPath = getDASHStreamingUrl(manager, config.getResourceGroup(), config.getAccountName(),
                            locator.name(), streamingEndpoint);

                    System.out.println();
                    System.out.println(
                            "Copy and paste the following URL in your browser to play back the file in the Azure Media Player.");
                    System.out.println("You can use Edge/IE11 for PlayReady.");
                    System.out.println();

                    System.out.println("https://ampdemo.azureedge.net/?url=" + dashPath);
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

            cleanup(manager, config.getResourceGroup(), config.getAccountName(), TRANSFORM_NAME,
                    jobName,
                    outputAssetName, locatorName, CONTENT_KEY_POLICY_NAME, stopEndpoint,
                    DEFAULT_STREAMING_ENDPOINT_NAME);
        }
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
    private static Job submitJob(MediaServicesManager manager, String resourceGroupName, String accountName,
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
            System.out.println("Creating a job...");
            job = manager.jobs().define(jobName)
                    .withExistingTransform(resourceGroupName, accountName, transformName)
                    .withInput(jobInput)
                    .withOutputs(jobOutputs)
                    .create();
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
        final int SLEEP_INTERVAL = 60 * 1000;

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
     * Create the content key policy that configures how the content key is delivered
     * to end clients via the Key Delivery component of Azure Media Services.
     *
     * @param manager              The entry point of Azure Media resource management.
     * @param resourceGroup        The name of the resource group within the Azure subscription.
     * @param accountName          The Media Services account name.
     * @param contentKeyPolicyName The name of the content key policy resource.
     * @return The content key policy.
     * @throws JsonProcessingException
     */
    private static ContentKeyPolicy ensureContentKeyPolicyExists(MediaServicesManager manager, String resourceGroup,
                                                                 String accountName, String contentKeyPolicyName)  throws JsonProcessingException {
        ContentKeyPolicy policy;
        try {
            // Get the policy if exists.
            policy = manager.contentKeyPolicies().get(resourceGroup, accountName, contentKeyPolicyName);
        } catch (ManagementException e) {
            policy = null;
        }

        if (policy == null) {
            ContentKeyPolicyOpenRestriction restriction = new ContentKeyPolicyOpenRestriction();

            // Create a configuration for PlayReady licenses.
            ContentKeyPolicyPlayReadyConfiguration playReadyConfig = configurePlayReadyLicenseTemplate();
            ContentKeyPolicyWidevineConfiguration widevineConfig = configureWidevineLicenseTemplate();

            List<ContentKeyPolicyOption> options = new ArrayList<>();

            options.add(new ContentKeyPolicyOption()
                    .withConfiguration(playReadyConfig)
                    .withRestriction(restriction));
            options.add(new ContentKeyPolicyOption()
                    .withConfiguration(widevineConfig)
                    .withRestriction(restriction));

            // Content Key Policy does not exist, create one.
            System.out.println("Creating a content key policy...");
            policy = manager.contentKeyPolicies().define(contentKeyPolicyName)
                    .withExistingMediaService(resourceGroup, accountName).withOptions(options).create();
        }

        return policy;
    }

    /**
     * Configures PlayReady license template.
     *
     * @return ContentKeyPolicyPlayReadyConfiguration
     */
    private static ContentKeyPolicyPlayReadyConfiguration configurePlayReadyLicenseTemplate() {
        ContentKeyPolicyPlayReadyLicense objContentKeyPolicyPlayReadyLicense = new ContentKeyPolicyPlayReadyLicense()
                .withAllowTestDevices(true)
                .withBeginDate(OffsetDateTime.now())
                .withContentKeyLocation(new ContentKeyPolicyPlayReadyContentEncryptionKeyFromHeader())
                .withContentType(ContentKeyPolicyPlayReadyContentType.ULTRA_VIOLET_STREAMING)
                .withLicenseType(ContentKeyPolicyPlayReadyLicenseType.PERSISTENT)
                .withPlayRight(new ContentKeyPolicyPlayReadyPlayRight()
                        .withImageConstraintForAnalogComponentVideoRestriction(true)
                        .withExplicitAnalogTelevisionOutputRestriction(
                                new ContentKeyPolicyPlayReadyExplicitAnalogTelevisionRestriction()
                                        .withBestEffort(true)
                                        .withConfigurationData(2))
                        .withAllowPassingVideoContentToUnknownOutput(ContentKeyPolicyPlayReadyUnknownOutputPassingOption.ALLOWED));

        List<ContentKeyPolicyPlayReadyLicense> licenses = new ArrayList<>();
        licenses.add(objContentKeyPolicyPlayReadyLicense);

        ContentKeyPolicyPlayReadyConfiguration objContentKeyPolicyPlayReadyConfiguration = new ContentKeyPolicyPlayReadyConfiguration()
                .withLicenses(licenses);

        return objContentKeyPolicyPlayReadyConfiguration;
    }

    /**
     * Builds the streaming URLs.
     *
     * @param manager           The entry point of Azure Media resource management.
     * @param resourceGroup     The name of the resource group within the Azure subscription.
     * @param accountName       The Media Services account name.
     * @param locatorName       The name of the StreamingLocator that was created.
     * @param streamingEndpoint The streaming endpoint.
     * @return DASH url.
     */
    private static String getDASHStreamingUrl(MediaServicesManager manager, String resourceGroup, String accountName, String locatorName, StreamingEndpoint streamingEndpoint) {
        String dashPath = "";

        ListPathsResponse paths = manager.streamingLocators()
                .listPaths(resourceGroup, accountName, locatorName);

        for (StreamingPath path : paths.streamingPaths()) {
            if (path.paths().size() > 0) {
                StringBuilder uriBuilder = new StringBuilder();
                uriBuilder.append("https://").append(streamingEndpoint.hostname());

                // Look for just the DASH path and generate a URL for the Azure Media Player to playback the content with the AES token to decrypt.
                if (path.streamingProtocol() == StreamingPolicyStreamingProtocol.DASH) {
                    uriBuilder.append("/").append(path.paths().get(0));
                    dashPath = uriBuilder.toString();
                }
            }
        }

        return dashPath;
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
    public static void cleanup(MediaServicesManager manager, String resourceGroup, String accountName,
                               String transformName,
                               String jobName,
                               String assetName, String locatorName, String contentKeyPolicyName, boolean stopEndpoint,
                               String streamingEndpointName) {
        if (manager == null) {
            return;
        }

        manager.jobs().delete(resourceGroup, accountName, transformName, jobName);
        manager.assets().delete(resourceGroup, accountName, assetName);

        manager.streamingLocators().delete(resourceGroup, accountName, locatorName);
        manager.contentKeyPolicies().delete(resourceGroup, accountName, contentKeyPolicyName);

        if (stopEndpoint) {
            // Because we started the endpoint, we'll stop it.
            manager.streamingEndpoints().stop(resourceGroup, accountName, streamingEndpointName);
        } else {
            // We will keep the endpoint running because it was not started by this sample. Please note, There are costs to keep it running.
            // Please refer https://azure.microsoft.com/en-us/pricing/details/media-services/ for pricing.
            System.out.println("The endpoint ''" + streamingEndpointName + "'' is running. To halt further billing on the endpoint, please stop it in azure portal or AMS Explorer.");
        }
    }

    /**
     * Configures Widevine license template.
     *
     * @return ContentKeyPolicyWidevineConfiguration
     * @throws JsonProcessingException
     */
    private static ContentKeyPolicyWidevineConfiguration configureWidevineLicenseTemplate()
            throws JsonProcessingException {
        WidevineTemplate template = new WidevineTemplate();
        template.setAllowedTrackTypes("SD_HD");

        ContentKeySpec contentKeySpec = new ContentKeySpec();
        contentKeySpec.setTrackType("SD");
        contentKeySpec.setSecurityLevel(1);

        OutputProtection outputProtection = new OutputProtection();
        outputProtection.setHdcp("HDCP_NONE");
        contentKeySpec.setRequiredOutputProtection(outputProtection);

        List<ContentKeySpec> contentKeySpecs = new ArrayList<>();
        contentKeySpecs.add(contentKeySpec);
        template.setContentKeySpecs(contentKeySpecs);

        PolicyOverrides policyOverrides = new PolicyOverrides();
        policyOverrides.setCanPlay(true);
        policyOverrides.setCanPersist(true);
        policyOverrides.setCanRenew(false);
        policyOverrides.setRentalDurationSeconds(2592000);
        policyOverrides.setPlaybackDurationSeconds(10800);
        policyOverrides.setLicenseDurationSeconds(604800);
        template.setPolicyOverrides(policyOverrides);

        ObjectMapper mapper = new ObjectMapper();
        String strTemplate = mapper.writeValueAsString(template);
        ContentKeyPolicyWidevineConfiguration objContentKeyPolicyWidevineConfiguration = new ContentKeyPolicyWidevineConfiguration()
                .withWidevineTemplate(strTemplate);

        return objContentKeyPolicyWidevineConfiguration;
    }
}
