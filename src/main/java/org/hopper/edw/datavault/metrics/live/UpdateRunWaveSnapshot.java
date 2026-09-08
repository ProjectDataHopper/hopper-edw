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
package org.hopper.edw.datavault.metrics.live;

import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Immutable view of a resource-group update wave for the workflow canvas and metrics dialog. */
@Value
@Builder(toBuilder = true)
public class UpdateRunWaveSnapshot {
  String waveId;
  String resourceGroupName;
  String workflowFilename;
  String workflowName;
  String actionName;
  String workflowExecutionId;
  Date startedAt;
  Date updatedAt;
  UpdateRunWavePhase phase;
  UpdateRunLiveState overallState;
  String tooltipText;
  int modelsTotal;
  int modelsCompleted;
  int modelsFailed;
  int validatingTotal;
  List<UpdateRunWaveModelProgress> models;
  UpdateRunLiveSnapshot currentLiveSnapshot;
}
