/*
 * Copyright 2026 i-Bridge bv
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
package org.hopper.edw.datavault.hopgui.metrics;

import lombok.AllArgsConstructor;
import lombok.Value;

/** Marker stored on workflow graph custom area owners for live update badge clicks. */
@Value
@AllArgsConstructor
public class UpdateRunLiveAreaOwnerData {

  /** Parent id used when identifying the live-update badge drawn area. */
  public static final String AREA_DRAWN_LIVE_UPDATE_BADGE = "Drawn_LiveUpdateBadge";

  String metricsRunId;
  String waveId;
  String workflowFilename;
  String actionName;
  boolean historyPreferred;

  public UpdateRunLiveAreaOwnerData(String metricsRunId) {
    this(metricsRunId, null, null, null, false);
  }

  public static UpdateRunLiveAreaOwnerData forWave(
      String waveId, String workflowFilename, String actionName, boolean historyPreferred) {
    return new UpdateRunLiveAreaOwnerData(
        waveId, waveId, workflowFilename, actionName, historyPreferred);
  }
}
