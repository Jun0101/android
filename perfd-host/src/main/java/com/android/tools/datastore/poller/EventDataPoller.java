/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.datastore.poller;

import com.android.tools.datastore.database.EventsTable;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import io.grpc.StatusRuntimeException;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public class EventDataPoller extends PollRunner implements PollRunner.PollingCallback {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private EventServiceGrpc.EventServiceBlockingStub myEventPollingService;
  private int myProcessId = -1;
  private EventsTable myEventsTable;

  public EventDataPoller(int processId, EventsTable eventTable, EventServiceGrpc.EventServiceBlockingStub pollingService) {
    super(POLLING_DELAY_NS);
    myProcessId = processId;
    myEventsTable = eventTable;
    myEventPollingService = pollingService;
    setPollingCallback(this);
  }

  @Override
  public void poll() throws StatusRuntimeException {
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);

    // Query for and cache activity data that has changed since our last polling.
    EventProfiler.ActivityDataResponse activityResponse = myEventPollingService.getActivityData(dataRequestBuilder.build());
    for (EventProfiler.ActivityData data : activityResponse.getDataList()) {
      long id = data.getHash();
      EventProfiler.ActivityData cached_data = myEventsTable.findActivityDataOrNull(data.getProcessId(), id);
      if (cached_data != null) {
        EventProfiler.ActivityData.Builder builder = cached_data.toBuilder();
        // Perfd may return states that we already have cached. This checks for that and only adds unique ones.
        for (EventProfiler.ActivityStateData state : data.getStateChangesList()) {
          if (!cached_data.getStateChangesList().contains(state)) {
            builder.addStateChanges(state);
          }
          if (state.getTimestamp() > myDataRequestStartTimestampNs) {
            myDataRequestStartTimestampNs = state.getTimestamp();
          }
        }
        myEventsTable.insertOrReplace(id, builder.build());
      }
      else {
        myEventsTable.insertOrReplace(id, data);
        for (EventProfiler.ActivityStateData state : data.getStateChangesList()) {
          if (state.getTimestamp() > myDataRequestStartTimestampNs) {
            myDataRequestStartTimestampNs = state.getTimestamp();
          }
        }
      }
    }

    // Poll for system event data. If we have a duplicate event then we replace it with the incomming one.
    // we replace the event as the event information may have changed, eg now it has an uptime where previously it didn't
    EventProfiler.SystemDataResponse systemResponse = myEventPollingService.getSystemData(dataRequestBuilder.build());
    for (EventProfiler.SystemData data : systemResponse.getDataList()) {
      long id = data.getEventId();
      myEventsTable.insertOrReplace(id, data);
    }
  }
}
