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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.apache.hop.quality.history.DataQualityHistoryReader.QualityRunSummary;

/** Last-run OPS facts for one resource definition group. Missing OPS is empty, not a failure. */
public record EdwJourneyOpsOverlay(
    String unavailableReason,
    HarvestRunSummary harvest,
    QualityRunSummary sourceQuality,
    QualityRunSummary targetQuality,
    LoadOverviewSummary load,
    List<ModelLoadSummary> modelLoads,
    List<EdwJourneyProblem> problems) {

  public EdwJourneyOpsOverlay {
    modelLoads = modelLoads != null ? List.copyOf(modelLoads) : List.of();
    problems = problems != null ? List.copyOf(problems) : List.of();
  }

  public static EdwJourneyOpsOverlay empty() {
    return new EdwJourneyOpsOverlay(null, null, null, null, null, List.of(), List.of());
  }

  public static EdwJourneyOpsOverlay unavailable(String reason) {
    return new EdwJourneyOpsOverlay(reason, null, null, null, null, List.of(), List.of());
  }

  public boolean hasAnyRun() {
    return harvest != null
        || sourceQuality != null
        || targetQuality != null
        || load != null
        || !modelLoads.isEmpty();
  }

  public record LoadOverviewSummary(
      String workflowExecutionId,
      String rootWorkflowName,
      Date finishedAt,
      Long durationMs,
      Long modelCount,
      Long errors,
      Boolean success) {}

  public record ModelLoadSummary(
      String opsModelType,
      String modelName,
      Long durationMs,
      Long errors,
      Boolean success,
      Date finishedAt) {}

  public record EdwJourneyProblem(String severity, String source, String subject, String message) {}
}
