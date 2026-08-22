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
package org.apache.hop.datavault.metrics.live;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.util.Utils;

/** Builds immutable live-update snapshots from sampled pipeline metrics. */
final class UpdateRunLiveSnapshotBuilder {

  private UpdateRunLiveSnapshotBuilder() {}

  static UpdateRunLiveSnapshot build(
      UpdateRunLiveRunContext context,
      UpdateRunLiveStallDetector stallDetector,
      List<PipelineLiveMetrics> rawPipelines,
      boolean executionFinished,
      long executionErrors,
      String fallbackElementName) {
    long nowMillis = System.currentTimeMillis();
    List<PipelineLiveMetrics> pipelines = new ArrayList<>();
    UpdateRunLiveState overallState = UpdateRunLiveState.RUNNING;
    String currentElementName = null;
    String currentElementType = null;

    if (rawPipelines != null) {
      for (PipelineLiveMetrics pipeline : rawPipelines) {
        List<TransformLiveMetrics> transforms =
            stallDetector.annotateTransforms(
                pipeline.getPipelineName(), pipeline.getTransforms(), nowMillis);
        UpdateRunLiveState pipelineState = pipeline.getState();
        if (pipelineState == UpdateRunLiveState.RUNNING
            && stallDetector.isPipelineStalled(transforms)) {
          pipelineState = UpdateRunLiveState.STALLED;
        }
        if (pipelineState == UpdateRunLiveState.STALLED) {
          overallState = UpdateRunLiveState.STALLED;
        } else if (pipelineState == UpdateRunLiveState.FAILED) {
          overallState = UpdateRunLiveState.FAILED;
        }
        if (currentElementName == null
            && (pipelineState == UpdateRunLiveState.RUNNING
                || pipelineState == UpdateRunLiveState.STALLED)) {
          currentElementName = pipeline.getElementName();
          currentElementType = pipeline.getElementType();
        }
        pipelines.add(
            PipelineLiveMetrics.builder()
                .pipelineName(pipeline.getPipelineName())
                .elementType(pipeline.getElementType())
                .elementName(pipeline.getElementName())
                .sourceName(pipeline.getSourceName())
                .state(pipelineState)
                .transforms(transforms)
                .build());
      }
    }

    if (Utils.isEmpty(currentElementName) && !Utils.isEmpty(fallbackElementName)) {
      currentElementName = fallbackElementName;
    }

    if (executionFinished && overallState != UpdateRunLiveState.FAILED) {
      overallState = executionErrors > 0 ? UpdateRunLiveState.FAILED : UpdateRunLiveState.COMPLETED;
    }

    UpdateRunLiveBottleneck bottleneck = UpdateRunLiveClassifier.classifyRun(pipelines);
    String tooltipText =
        UpdateRunLiveMonitor.buildTooltipText(
            currentElementName, context.getModelName(), overallState);

    return UpdateRunLiveSnapshot.builder()
        .metricsRunId(context.getMetricsRunId())
        .modelName(context.getModelName())
        .modelFilename(context.getModelFilename())
        .stagingFolder(context.getStagingFolder())
        .workflowFilename(context.getWorkflowFilename())
        .workflowName(context.getWorkflowName())
        .actionName(context.getActionName())
        .startedAt(context.getStartedAt())
        .updatedAt(new Date(nowMillis))
        .overallState(overallState)
        .currentElementName(currentElementName)
        .currentElementType(currentElementType)
        .tooltipText(tooltipText)
        .pipelines(List.copyOf(pipelines))
        .primaryBottleneck(bottleneck)
        .build();
  }
}
