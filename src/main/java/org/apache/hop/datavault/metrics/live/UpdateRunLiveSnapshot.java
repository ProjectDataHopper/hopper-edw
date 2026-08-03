/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metrics.live;

import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Immutable view of one in-flight model update run for GUI and logging. */
@Value
@Builder(toBuilder = true)
public class UpdateRunLiveSnapshot {
  String metricsRunId;
  String modelName;
  String modelFilename;
  String stagingFolder;
  String workflowFilename;
  String workflowName;
  String actionName;
  Date startedAt;
  Date updatedAt;
  UpdateRunLiveState overallState;
  String currentElementName;
  String currentElementType;
  String tooltipText;
  List<PipelineLiveMetrics> pipelines;
  UpdateRunLiveBottleneck primaryBottleneck;
}
