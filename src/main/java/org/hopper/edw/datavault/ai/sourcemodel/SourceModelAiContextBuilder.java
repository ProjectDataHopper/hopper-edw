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
package org.hopper.edw.datavault.ai.sourcemodel;

import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.ai.DvAiContextBuilder;
import org.hopper.edw.datavault.ai.EdwAiInclusions;
import org.hopper.edw.datavault.ai.EdwChatOnlyAdvisorSupport;
import org.hopper.edw.datavault.ai.SourceToVaultAiContextBuilder;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassification;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassifier;

/** Compact redacted source-model JSON for chat-only AI Help. */
public final class SourceModelAiContextBuilder {

  private SourceModelAiContextBuilder() {}

  public static String structureJson(SourceModel model, String focusNodeName) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":")
        .append(DvAiContextBuilder.jsonString(model != null ? model.getName() : null));
    json.append(",\"focusNode\":").append(DvAiContextBuilder.jsonString(focusNodeName));
    json.append(",\"tables\":[");
    if (model != null) {
      appendTables(json, model.getTables());
    }
    json.append("],\"relationships\":[");
    if (model != null) {
      appendRelationships(json, model.getRelationships());
    }
    json.append("],\"queries\":[");
    if (model != null) {
      appendNamed(json, model.getQueries());
    }
    json.append("],\"jsonSources\":[");
    if (model != null) {
      appendNamed(json, model.getJsonSources());
    }
    json.append("],\"pipelineSources\":[");
    if (model != null) {
      appendNamed(json, model.getPipelineSources());
    }
    json.append("]}");
    return json.toString();
  }

  public static String classificationJson(SourceModel model) {
    SourceToVaultClassification classification = SourceToVaultClassifier.classify(model);
    return SourceToVaultAiContextBuilder.serializeClassification(
        model != null ? model.getFilename() : null,
        model != null ? model.getName() : null,
        classification);
  }

  public static String checkResultsJson(
      SourceModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      AiAdvisorRequest request) {
    if (request == null || !request.inclusionEnabled(EdwAiInclusions.CHECKS) || model == null) {
      return "";
    }
    try {
      List<ICheckResult> remarks = model.check(metadataProvider, variables);
      return EdwChatOnlyAdvisorSupport.serializeCheckResults(remarks);
    } catch (Exception e) {
      return "{\"error\":" + DvAiContextBuilder.jsonString(e.getMessage()) + "}";
    }
  }

  public static String userPrompt(
      AiAdvisorRequest request,
      String structureJson,
      String classificationJson,
      String checkResultsJson)
      throws HopException {
    EdwChatOnlyAdvisorSupport.requireQuestion(request);
    StringBuilder prompt = new StringBuilder();
    prompt.append("User question:\n").append(request.getUserPrompt()).append("\n\n");
    if (!Utils.isEmpty(request.getFocusNodeName())) {
      prompt.append("Focus node: ").append(request.getFocusNodeName()).append("\n\n");
    }
    prompt.append("Source model structure JSON:\n").append(structureJson).append("\n\n");
    if (!request.isFollowUp()) {
      prompt
          .append("Generate Data Vault classification JSON:\n")
          .append(classificationJson)
          .append("\n\n");
    }
    if (!Utils.isEmpty(checkResultsJson)) {
      prompt.append("Model check results JSON:\n").append(checkResultsJson).append("\n\n");
    }
    if (!Utils.isEmpty(request.getLogExcerpt())) {
      prompt.append("Log excerpt:\n").append(request.getLogExcerpt()).append("\n\n");
    }
    return prompt.toString();
  }

  private static void appendTables(StringBuilder json, List<SourceTable> tables) {
    boolean first = true;
    for (SourceTable table : tables) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(table.getName()));
      json.append(",\"tableName\":").append(DvAiContextBuilder.jsonString(table.getTableName()));
      json.append(",\"columns\":[");
      boolean firstCol = true;
      for (SourceColumn column : table.getColumns()) {
        if (column == null || Utils.isEmpty(column.getName())) {
          continue;
        }
        if (!firstCol) {
          json.append(',');
        }
        firstCol = false;
        json.append("{\"name\":").append(DvAiContextBuilder.jsonString(column.getName()));
        json.append(",\"pk\":").append(column.isPrimaryKey());
        if (!Utils.isEmpty(column.getSourceDataType())) {
          json.append(",\"type\":")
              .append(DvAiContextBuilder.jsonString(column.getSourceDataType()));
        }
        json.append('}');
      }
      json.append("]}");
    }
  }

  private static void appendRelationships(
      StringBuilder json, List<SourceRelationship> relationships) {
    boolean first = true;
    for (SourceRelationship relationship : relationships) {
      if (relationship == null) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(relationship.getName()));
      json.append(",\"child\":")
          .append(DvAiContextBuilder.jsonString(relationship.getChildTableName()));
      json.append(",\"parent\":")
          .append(DvAiContextBuilder.jsonString(relationship.getParentTableName()));
      json.append('}');
    }
  }

  private static void appendNamed(
      StringBuilder json, List<? extends org.apache.hop.metadata.api.IHasName> items) {
    boolean first = true;
    for (org.apache.hop.metadata.api.IHasName item : items) {
      if (item == null || Utils.isEmpty(item.getName())) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(item.getName())).append('}');
    }
  }
}
