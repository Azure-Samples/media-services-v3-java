// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import com.microsoft.azure.eventprocessorhost.IEventProcessorFactory;
import com.microsoft.azure.eventprocessorhost.PartitionContext;

/**
 * Factory class for creating custom EventProcessor.
 */
public class MediaServicesEventProcessorFactory implements IEventProcessorFactory<MediaServicesEventProcessor> {
    private final Object MONITOR;
    private final String JOB_NAME;
    private final String LIVE_EVENT_NAME;

    /**
     * Called by job related samples.
     * @param jobName   The job name.
     * @param monitor   the object the main thread wait on.
     */
    public MediaServicesEventProcessorFactory(String jobName, Object monitor) {
        this.JOB_NAME = jobName;
        this.MONITOR = monitor;
        this.LIVE_EVENT_NAME = null;
    }

    /**
     * Called by LiveEvent samples.
     * @param liveEventName
     */
    public MediaServicesEventProcessorFactory(String liveEventName) {
        this.JOB_NAME = null;
        this.MONITOR = null;
        this.LIVE_EVENT_NAME = liveEventName;
    }

    /**
     * Call back for EventProcessorHost
     */
    @Override
    public MediaServicesEventProcessor createEventProcessor(PartitionContext context) throws Exception {
        return new MediaServicesEventProcessor(JOB_NAME, MONITOR, LIVE_EVENT_NAME);
    }
}
