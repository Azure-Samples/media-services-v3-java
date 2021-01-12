// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.messaging.eventhubs.models.*;
import com.azure.storage.blob.BlobContainerAsyncClient;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONArray;

import java.util.function.Consumer;

/**
 * Implementation of IEventProcessor to handle events from Event Hub.
 */
public class MediaServicesEventProcessor {
    private final Object MONITOR;
    private final String JOB_NAME;
    private final String LIVE_EVENT_NAME;
    private final String EVENT_HUB_CONN_STRING;
    private final String EVENT_HUB_NAME;
    private final BlobContainerAsyncClient BLOB_CONTAINER;
    private final Consumer<EventContext> eventContextConsumer =
            eventContext -> this.printEvent(eventContext.getPartitionContext(), eventContext.getEventData());
    private final Consumer<ErrorContext> errorContextConsumer =
            errorContext -> System.out.println("Partition " + errorContext.getPartitionContext().getPartitionId()
                    + " onError: " + errorContext.getThrowable().toString());
    private final Consumer<CloseContext> closeContextConsumer =
            closeContext -> System.out.println("Partition " + closeContext.getPartitionContext().getPartitionId()
                    + " is closing for reason " + closeContext.getCloseReason().toString());
    private final Consumer<InitializationContext> initializationContextConsumer =
            initializationContextConsumer -> System.out.println("Partition "
                    + initializationContextConsumer.getPartitionContext().getPartitionId() + " is opening");

    public MediaServicesEventProcessor(String jobName, Object monitor, String liveEventName,
                                       String eventHubConnectionString, String eventHubName,
                                       BlobContainerAsyncClient container) {

        this.EVENT_HUB_CONN_STRING = eventHubConnectionString;
        this.EVENT_HUB_NAME = eventHubName;
        this.BLOB_CONTAINER = container;

        if (jobName != null) {
            this.JOB_NAME = jobName.replaceAll("-", "");
        } else {
            this.JOB_NAME = null;
        }

        this.MONITOR = bulidEventProcessClient();
        monitor = this.MONITOR;

        if (liveEventName != null) {
            this.LIVE_EVENT_NAME = liveEventName.replaceAll("-", "");
        } else {
            this.LIVE_EVENT_NAME = null;
        }
    }

    public MediaServicesEventProcessor() {
        this.JOB_NAME = null;
        this.MONITOR = null;
        this.LIVE_EVENT_NAME = null;
        this.EVENT_HUB_NAME = null;
        this.EVENT_HUB_CONN_STRING = null;
        this.BLOB_CONTAINER = null;
    }

    public void stop() {
        if (this.MONITOR instanceof EventProcessorClient) {
            ((EventProcessorClient) this.MONITOR).stop();
        }
    }

    private EventProcessorClient bulidEventProcessClient() {
        return new EventProcessorClientBuilder()
                .connectionString(this.EVENT_HUB_CONN_STRING, this.EVENT_HUB_NAME)
                .checkpointStore(new BlobCheckpointStore(this.BLOB_CONTAINER))
                .consumerGroup("$Default")
                .processEvent(eventContextConsumer)
                .processError(errorContextConsumer)
                .processPartitionInitialization(initializationContextConsumer)
                .processPartitionClose(closeContextConsumer)
                .buildEventProcessorClient();
    }

    /**
     * Parse and print Media Services events.
     *
     * @param context   partition-related information.
     * @param eventData Event Hub event data.
     */
    private void printEvent(PartitionContext context, EventData eventData) {
        try {
            String data = new String(eventData.getBody(), "UTF8");
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(data);
            if (obj instanceof JSONArray) {
                JSONArray jArr = (JSONArray) obj;
                for (Object element : jArr) {
                    if (element instanceof JSONObject) {
                        JSONObject jObj = (JSONObject) element;
                        String eventType = (String) jObj.get("eventType");
                        String subject = (String) jObj.get("subject");
                        String eventName = subject.replaceFirst("^.*/", "").replaceAll("-", "");

                        // Only these events from registered job or live event.
                        if (!eventName.equals(JOB_NAME) && !eventName.equals(LIVE_EVENT_NAME)) {
                            return;
                        }

                        JSONObject jobOrLiveEventData = (JSONObject) jObj.get("data");
                        switch (eventType) {
                            // Job state change events
                            case "Microsoft.Media.JobStateChange":
                            case "Microsoft.Media.JobScheduled":
                            case "Microsoft.Media.JobProcessing":
                            case "Microsoft.Media.JobCanceling":
                            case "Microsoft.Media.JobFinished":
                            case "Microsoft.Media.JobCanceled":
                            case "Microsoft.Media.JobErrored":
                                System.out.println("Job state changed for JobId: " + eventName +
                                        " PreviousState: " + jobOrLiveEventData.get("previousState") +
                                        ", State: " + jobOrLiveEventData.get("state"));
                                if (eventType.equals("Microsoft.Media.JobFinished") || eventType.equals("Microsoft.Media.JobCanceled") ||
                                        eventType.equals("Microsoft.Media.JobErrored")) {
                                    // Job finished, send a message.
                                    if (MONITOR != null) {
                                        synchronized (MONITOR) {
                                            MONITOR.notify();
                                        }
                                    }
                                }
                                break;

                            // Job output state change events
                            case "Microsoft.Media.JobOutputStateChange":
                            case "Microsoft.Media.JobOutputScheduled":
                            case "Microsoft.Media.JobOutputProcessing":
                            case "Microsoft.Media.JobOutputCanceling":
                            case "Microsoft.Media.JobOutputFinished":
                            case "Microsoft.Media.JobOutputCanceled":
                            case "Microsoft.Media.JobOutputErrored":
                                JSONObject outputObj = (JSONObject) jobOrLiveEventData.get("output");
                                System.out.println("Job output state changed for JobId:" + eventName +
                                        " PreviousState: " + jobOrLiveEventData.get("previousState") +
                                        ", State: " + outputObj.get("state") + " Progress: " + outputObj.get("progress") + "%");
                                break;

                            // Job output progress event
                            case "Microsoft.Media.JobOutputProgress":
                                System.out.println("Job output progress changed for JobId: " + eventName +
                                        " Progress: " + jobOrLiveEventData.get("progress") + "%");
                                break;

                            // LiveEvent Stream-level events
                            case "Microsoft.Media.LiveEventConnectionRejected":
                                System.out.println("LiveEvent connection rejected. IngestUrl: " + jobOrLiveEventData.get("ingestUrl") +
                                        " StreamId: " + jobOrLiveEventData.get("streamId") +
                                        " EncoderIp: " + jobOrLiveEventData.get("encoderIp") +
                                        " EncoderPort: " + jobOrLiveEventData.get("encoderPort"));
                                break;

                            case "Microsoft.Media.LiveEventEncoderConnected":
                                System.out.println("LiveEvent encoder connected. IngestUrl: " + jobOrLiveEventData.get("ingestUrl") +
                                        " StreamId: " + jobOrLiveEventData.get("streamId") +
                                        " EncoderIp: " + jobOrLiveEventData.get("encoderIp") +
                                        " EncoderPort: " + jobOrLiveEventData.get("encoderPort"));
                                break;

                            case "Microsoft.Media.LiveEventEncoderDisconnected":
                                System.out.println("LiveEvent encoder disconnected. IngestUrl: " + jobOrLiveEventData.get("ingestUrl") +
                                        " StreamId: " + jobOrLiveEventData.get("streamId") +
                                        " EncoderIp: " + jobOrLiveEventData.get("encoderIp") +
                                        " EncoderPort: " + jobOrLiveEventData.get("encoderPort"));
                                break;

                            // LiveEvent Track-level events
                            case "Microsoft.Media.LiveEventIncomingDataChunkDropped":
                                System.out.println("LiveEvent data chunk dropped. LiveEventId: " + eventName +
                                        " ResultCode: " + jobOrLiveEventData.get("resultCode"));
                                break;

                            case "Microsoft.Media.LiveEventIncomingStreamReceived":
                                System.out.println("LiveEvent incoming stream received. IngestUrl: " + jobOrLiveEventData.get("ingestUrl") +
                                        " EncoderIp: " + jobOrLiveEventData.get("encoderIp") +
                                        " EncoderPort: " + jobOrLiveEventData.get("encoderPort"));
                                break;

                            case "Microsoft.Media.LiveEventIncomingStreamsOutOfSync":
                                System.out.println("LiveEvent incoming audio and video streams are out of sync. LiveEventId: " + eventName);
                                break;

                            case "Microsoft.Media.LiveEventIncomingVideoStreamsOutOfSync":
                                System.out.println("LiveEvent incoming video streams are out of sync. LiveEventId: " + eventName);
                                break;

                            case "Microsoft.Media.LiveEventIngestHeartbeat":
                                System.out.println("LiveEvent ingest heart beat. TrackType: " + jobOrLiveEventData.get("trackType") +
                                        " State: " + jobOrLiveEventData.get("state") +
                                        " Healthy: " + jobOrLiveEventData.get("healthy"));
                                break;

                            case "Microsoft.Media.LiveEventTrackDiscontinuityDetected":
                                System.out.println("LiveEvent discontinuity in the incoming track detected. LiveEventId: " + eventName +
                                        " TrackType: " + jobOrLiveEventData.get("trackType") +
                                        " Discontinuity gap: " + jobOrLiveEventData.get("discontinuityGap"));
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Processing failed for an event: " + e.toString());
        }
    }
}
