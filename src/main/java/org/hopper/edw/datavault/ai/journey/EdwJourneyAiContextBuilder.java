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
package org.hopper.edw.datavault.ai.journey;

import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.ai.DvAiContextBuilder;
import org.hopper.edw.datavault.ai.EdwChatOnlyAdvisorSupport;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.EdwJourneyProblem;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyPerspective;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.OutputRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.WorkflowRef;

/** Compact EDW Journey JSON for chat-only AI Help. */
public final class EdwJourneyAiContextBuilder {

  private EdwJourneyAiContextBuilder() {}

  public static EdwJourneySnapshot snapshotFrom(AiAdvisorRequest request) {
    EdwJourneySnapshot live = liveSnapshot();
    if (live != null && live.hasGroup()) {
      return live;
    }
    if (request != null && request.getArtifact() instanceof EdwJourneySnapshot snapshot) {
      return snapshot;
    }
    return EdwJourneySnapshot.empty();
  }

  public static String snapshotJson(EdwJourneySnapshot snapshot, String focusNodeName) {
    EdwJourneySnapshot safe = snapshot != null ? snapshot : EdwJourneySnapshot.empty();
    StringBuilder json = new StringBuilder();
    json.append("{\"groupName\":").append(DvAiContextBuilder.jsonString(safe.groupName()));
    json.append(",\"focusNode\":").append(DvAiContextBuilder.jsonString(focusNodeName));
    json.append(",\"sourceModels\":");
    appendModels(json, safe.sourceModels());
    json.append(",\"dataVaultModels\":");
    appendModels(json, safe.dataVaultModels());
    json.append(",\"businessVaultModels\":");
    appendModels(json, safe.businessVaultModels());
    json.append(",\"dimensionalModels\":");
    appendModels(json, safe.dimensionalModels());
    json.append(",\"workflows\":[");
    boolean firstWf = true;
    for (WorkflowRef workflow : safe.workflows()) {
      if (workflow == null) {
        continue;
      }
      if (!firstWf) {
        json.append(',');
      }
      firstWf = false;
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(workflow.workflowName()));
      json.append(",\"path\":").append(DvAiContextBuilder.jsonString(workflow.storedPath()));
      json.append('}');
    }
    json.append("],\"executionMaps\":");
    appendOutputs(json, safe.executionMaps());
    json.append(",\"lineageViews\":");
    appendOutputs(json, safe.lineageViews());
    json.append(",\"warnings\":[");
    boolean firstWarn = true;
    for (String warning : safe.warnings()) {
      if (Utils.isEmpty(warning)) {
        continue;
      }
      if (!firstWarn) {
        json.append(',');
      }
      firstWarn = false;
      json.append(DvAiContextBuilder.jsonString(warning));
    }
    json.append("]}");
    return json.toString();
  }

  public static String opsJson(AiAdvisorRequest request) {
    EdwJourneyOpsOverlay overlay = liveOps();
    if (overlay == null) {
      return EdwChatOnlyAdvisorSupport.attributeString(
          request, EdwChatOnlyAdvisorSupport.ATTR_OPS_JSON);
    }
    return opsJson(overlay);
  }

  public static String opsJson(EdwJourneyOpsOverlay overlay) {
    if (overlay == null) {
      return "";
    }
    StringBuilder json = new StringBuilder();
    json.append("{\"unavailableReason\":")
        .append(DvAiContextBuilder.jsonString(overlay.unavailableReason()));
    json.append(",\"hasAnyRun\":").append(overlay.hasAnyRun());
    LoadOverviewSummary load = overlay.load();
    if (load != null) {
      json.append(",\"load\":{\"workflow\":")
          .append(DvAiContextBuilder.jsonString(load.rootWorkflowName()));
      json.append(",\"success\":").append(load.success());
      json.append(",\"errors\":").append(load.errors());
      json.append(",\"durationMs\":").append(load.durationMs());
      json.append('}');
    }
    json.append(",\"modelLoads\":[");
    boolean first = true;
    for (ModelLoadSummary modelLoad : overlay.modelLoads()) {
      if (modelLoad == null) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"type\":").append(DvAiContextBuilder.jsonString(modelLoad.opsModelType()));
      json.append(",\"name\":").append(DvAiContextBuilder.jsonString(modelLoad.modelName()));
      json.append(",\"success\":").append(modelLoad.success());
      json.append(",\"errors\":").append(modelLoad.errors());
      json.append(",\"durationMs\":").append(modelLoad.durationMs());
      json.append('}');
    }
    json.append("],\"problems\":[");
    first = true;
    for (EdwJourneyProblem problem : overlay.problems()) {
      if (problem == null) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"severity\":").append(DvAiContextBuilder.jsonString(problem.severity()));
      json.append(",\"source\":").append(DvAiContextBuilder.jsonString(problem.source()));
      json.append(",\"subject\":").append(DvAiContextBuilder.jsonString(problem.subject()));
      json.append(",\"message\":").append(DvAiContextBuilder.jsonString(problem.message()));
      json.append('}');
    }
    json.append("]}");
    return json.toString();
  }

  public static String userPrompt(AiAdvisorRequest request, String snapshotJson, String opsJson)
      throws HopException {
    EdwChatOnlyAdvisorSupport.requireQuestion(request);
    StringBuilder prompt = new StringBuilder();
    prompt.append("User question:\n").append(request.getUserPrompt()).append("\n\n");
    if (!Utils.isEmpty(request.getFocusNodeName())) {
      prompt.append("Focus node: ").append(request.getFocusNodeName()).append("\n\n");
    }
    prompt.append("EDW Journey snapshot JSON:\n").append(snapshotJson).append("\n\n");
    if (!Utils.isEmpty(opsJson)) {
      prompt.append("Last-run OPS overlay JSON:\n").append(opsJson).append("\n\n");
    }
    return prompt.toString();
  }

  private static void appendModels(StringBuilder json, java.util.List<ModelRef> models) {
    json.append('[');
    boolean first = true;
    for (ModelRef model : models) {
      if (model == null) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(model.displayName()));
      json.append(",\"path\":").append(DvAiContextBuilder.jsonString(model.storedPath()));
      json.append(",\"type\":").append(DvAiContextBuilder.jsonString(model.modelType()));
      json.append(",\"tableCount\":")
          .append(model.tableNames() != null ? model.tableNames().size() : 0);
      json.append('}');
    }
    json.append(']');
  }

  private static void appendOutputs(StringBuilder json, java.util.List<OutputRef> outputs) {
    json.append('[');
    boolean first = true;
    for (OutputRef output : outputs) {
      if (output == null) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(output.displayName()));
      json.append(",\"path\":").append(DvAiContextBuilder.jsonString(output.storedPath()));
      json.append('}');
    }
    json.append(']');
  }

  private static EdwJourneySnapshot liveSnapshot() {
    try {
      EdwJourneyPerspective perspective = EdwJourneyPerspective.getInstance();
      return perspective != null ? perspective.getSnapshot() : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static EdwJourneyOpsOverlay liveOps() {
    try {
      EdwJourneyPerspective perspective = EdwJourneyPerspective.getInstance();
      return perspective != null ? perspective.getOpsOverlay() : null;
    } catch (Throwable ignored) {
      return null;
    }
  }
}
