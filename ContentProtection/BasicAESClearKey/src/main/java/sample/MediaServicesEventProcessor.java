// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import com.microsoft.azure.eventprocessorhost.IEventProcessor;
import com.microsoft.azure.eventprocessorhost.PartitionContext;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventprocessorhost.CloseReason;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONArray;

/**
 * Implementation of IEventProcessor to handle events from Event Hub.
 */
public class MediaServicesEventProcessor implements IEventProcessor {
    private final Object MONITOR;
    private final String JOB_NAME;
    private final String LIVE_EVENT_NAME;

    public MediaServicesEventProcessor(String jobName, Object monitor, String liveEventName)
    {
        if (jobName != null) {
            this.JOB_NAME = jobName.replaceAll("-", "");
        }
        else {
            this.JOB_NAME = null;
        }

        this.MONITOR = monitor;

        if (liveEventName != null) {
            this.LIVE_EVENT_NAME = liveEventName.replaceAll("-", "");
        }
        else {
            this.LIVE_EVENT_NAME = null;
        }
    }

    public MediaServicesEventProcessor() {
        this.JOB_NAME = null;
        this.MONITOR = null;
        this.LIVE_EVENT_NAME = null;
    }

    // OnOpen is called when a new event processor instance is created by the host. 
    @Override
    public void onOpen(PartitionContext context) throws Exception
    {
        System.out.println("Partition " + context.getPartitionId() + " is opening");
    }

    // OnClose is called when an event processor instance is being shut down. 
    @Override
    public void onClose(PartitionContext context, CloseReason reason) throws Exception
    {
        System.out.println("Partition " + context.getPartitionId() + " is closing for reason " + reason.toString());
    }

    // onError is called when an error occurs in EventProcessorHost code that is tied to this partition, such as a receiver failure.
    @Override
    public void onError(PartitionContext context, Throwable error)
    {
        System.out.println("Partition " + context.getPartitionId() + " onError: " + error.toString());
    }
    
    // onEvents is called when events are received on this partition of the Event Hub. 
    @Override
    public void onEvents(PartitionContext context, Iterable<EventData> events) throws Exception
    {
        for (EventData eventData: events) {
            printEvent(context, eventData);
        }

        context.checkpoint().get();
    }

    /**
     * Parse and print Media Services events.
     * @param eventData Event Hub event data.
     */
    private void printEvent(PartitionContext context, EventData eventData) {
        try
        {
            String data = new String(eventData.getBytes(), "UTF8");
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(data);
            if (obj instanceof JSONArray) {
                JSONArray jArr = (JSONArray)obj;
                for (Object element: jArr) {
                    if (element instanceof JSONObject) {
                        JSONObject jObj = (JSONObject)element;
                        String eventType = (String)jObj.get("eventType");
                        String subject = (String)jObj.get("subject");
                        String eventName = subject.replaceFirst("^.*/", "").replaceAll("-", "");
                        
                        // Only these events from registered job or live event.
                        if (!eventName.equals(JOB_NAME) && !eventName.equals(LIVE_EVENT_NAME))
                        {
                            return;
                        }

                        JSONObject jobOrLiveEventData = (JSONObject)jObj.get("data");
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
                                    eventType.equals("Microsoft.Media.JobErrored"))
                                {
                                    // Job finished, send a message.
                                    if (MONITOR != null)
                                    {
                                        synchronized(MONITOR) {
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
                                JSONObject outputObj = (JSONObject)jobOrLiveEventData.get("output");
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
                                    " StreamId: " + jobOrLiveEventData.get("streamId")  +
                                    " EncoderIp: " + jobOrLiveEventData.get("encoderIp") + 
                                    " EncoderPort: " + jobOrLiveEventData.get("encoderPort"));
                                break;

                            case "Microsoft.Media.LiveEventEncoderConnected":
                                System.out.println("LiveEvent encoder connected. IngestUrl: " + jobOrLiveEventData.get("ingestUrl") +
                                    " StreamId: " + jobOrLiveEventData.get("streamId")  +
                                    " EncoderIp: " + jobOrLiveEventData.get("encoderIp") + 
                                    " EncoderPort: " + jobOrLiveEventData.get("encoderPort"));
                                break;

                            case "Microsoft.Media.LiveEventEncoderDisconnected":
                                System.out.println("LiveEvent encoder disconnected. IngestUrl: " + jobOrLiveEventData.get("ingestUrl") +
                                    " StreamId: " + jobOrLiveEventData.get("streamId")  +
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
        }
        catch (Exception e)
        {
            System.out.println("Processing failed for an event: " + e.toString());
        }
    }
}
