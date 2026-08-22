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
package org.hopper.edw.datavault.metrics;

import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** Aggregated workflow-level load overview for one vault update execution. */
@Value
@Builder
public class WorkflowLoadOverviewReport {

  public static final int DEFAULT_MAX_PIPELINES_PER_MODEL = 50;

  String overviewId;
  String workflowExecutionId;
  String rootWorkflowName;
  String metricsWorkflowName;
  Date startedAt;
  Date finishedAt;
  long durationMs;
  int modelCount;
  int pipelineCount;
  int insightCount;
  long totalSourceRowsRead;
  long totalTargetRowsInserted;
  long totalErrors;
  boolean success;

  @Singular("model")
  List<ModelEntry> models;

  @Value
  @Builder
  public static class ModelEntry {
    int sequenceNo;
    String loadRunId;
    String modelType;
    String modelName;
    int pipelineCount;
    long sourceRowsRead;
    long targetRowsRead;
    long targetRowsInserted;
    long errors;
    long durationMs;
    int insightCount;
    boolean success;
    Date startedAt;
    Date finishedAt;

    @Singular("pipeline")
    List<PipelineEntry> pipelines;

    @Singular("insight")
    List<InsightEntry> insights;
  }

  @Value
  @Builder
  public static class PipelineEntry {
    String pipelineName;
    String elementType;
    String elementName;
    String sourceName;
    long sourceRowsRead;
    long targetRowsRead;
    long targetRowsInserted;
    long errors;
    long durationMs;
  }

  @Value
  @Builder
  public static class InsightEntry {
    String severity;
    String code;
    String message;
    String elementName;
    String relatedElementName;
  }
}
