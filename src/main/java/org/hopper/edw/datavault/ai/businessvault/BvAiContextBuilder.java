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
package org.hopper.edw.datavault.ai.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.ai.DvAiContextBuilder;
import org.hopper.edw.datavault.ai.DvTargetLoadAiConfigurationSupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2SatelliteConfig;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metrics.ExecutionInfoAiContextBuilder;
import org.hopper.edw.datavault.metrics.MetricsAiContextBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;

/** Assembles redacted context for Business Vault AI advisory calls. */
public final class BvAiContextBuilder {

  private static final int MAX_MODEL_XML_CHARS = 120_000;
  private static final int MAX_LOG_CHARS = 20_000;

  private BvAiContextBuilder() {}

  public static BvAiContextBundle build(
      BusinessVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BvAiRequest request)
      throws HopException {
    if (model == null) {
      throw new HopException("No Business Vault model is open");
    }
    if (request == null || Utils.isEmpty(request.getUserPrompt())) {
      throw new HopException("Please enter a question for the AI advisor");
    }

    BvAiScenario scenario =
        request.getScenario() != null ? request.getScenario() : BvAiScenario.GENERAL;

    String modelXml = "";
    if (request.isIncludeModelXml()) {
      modelXml = truncate(XmlMetadataUtil.serializeObjectToXml(model), MAX_MODEL_XML_CHARS);
    }

    String checkResultsJson = "";
    if (request.isIncludeCheckResults() && metadataProvider != null) {
      checkResultsJson = serializeCheckResults(model.check(metadataProvider, variables));
    }

    String linkedDvModelStructureJson = "";
    if (request.isIncludeLinkedDvModel()) {
      linkedDvModelStructureJson =
          serializeLinkedDvModelStructure(model, variables, metadataProvider);
    }

    String loadRunMetricsJson =
        MetricsAiContextBuilder.buildMetricsContext(
            request.isIncludeLoadRunMetrics(),
            scenario == BvAiScenario.PERFORMANCE_TUNING,
            request.getUserPrompt(),
            model.getName(),
            GeneratedPipelineMetadataConstants.MODEL_TYPE_BV,
            metadataProvider,
            variables);
    String executionInfoJson =
        ExecutionInfoAiContextBuilder.buildExecutionInfoContext(
            request.isIncludeExecutionInfo(),
            scenario == BvAiScenario.PERFORMANCE_TUNING,
            request.getUserPrompt(),
            model.getName(),
            GeneratedPipelineMetadataConstants.MODEL_TYPE_BV,
            metadataProvider,
            variables);

    return BvAiContextBundle.builder()
        .scenario(scenario)
        .userPrompt(request.getUserPrompt())
        .modelXml(modelXml)
        .modelSummaryJson(serializeModelSummary(model))
        .modelStructureJson(serializeModelStructure(model))
        .linkedDvModelStructureJson(linkedDvModelStructureJson)
        .hopMetadataJson(serializeHopMetadata(model, metadataProvider, variables))
        .checkResultsJson(checkResultsJson)
        .loadRunMetricsJson(loadRunMetricsJson)
        .executionInfoJson(executionInfoJson)
        .logsExcerpt(truncate(request.getLogsExcerpt(), MAX_LOG_CHARS))
        .followUp(request.isFollowUp())
        .appliedChangeSummaries(
            request.getAppliedChangeSummaries() != null
                ? request.getAppliedChangeSummaries()
                : List.of())
        .build();
  }

  public static String serializeModelStructure(BusinessVaultModel model) {
    StringBuilder json = new StringBuilder();
    json.append("{\"tables\":[");
    List<IBvTable> tables = model != null ? model.getTables() : List.of();
    for (int i = 0; i < tables.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      IBvTable table = tables.get(i);
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(table.getName()));
      json.append(",\"type\":")
          .append(DvAiContextBuilder.jsonString(String.valueOf(table.getTableType())));
      json.append(",\"targetTable\":").append(DvAiContextBuilder.jsonString(table.getTableName()));
      json.append(",\"derivatives\":[");
      appendDerivatives(json, table.getDerivatives());
      json.append(']');
      if (table instanceof BvScd2Table scd2) {
        json.append(",\"buildMode\":")
            .append(DvAiContextBuilder.jsonString(String.valueOf(scd2.getBuildModeOrDefault())));
        json.append(",\"incrementalWatermarkField\":")
            .append(DvAiContextBuilder.jsonString(scd2.getIncrementalWatermarkField()));
        json.append(",\"satelliteConfigs\":[");
        appendSatelliteConfigs(json, scd2.getSatelliteConfigs());
        json.append(']');
      } else if (table instanceof BvPitTable pit) {
        json.append(",\"snapshotDateField\":")
            .append(DvAiContextBuilder.jsonString(pit.getSnapshotDateField()));
      }
      json.append('}');
    }
    json.append("]}");
    return json.toString();
  }

  private static void appendDerivatives(StringBuilder json, List<BvDerivativeRef> derivatives) {
    if (derivatives == null || derivatives.isEmpty()) {
      return;
    }
    for (int i = 0; i < derivatives.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      BvDerivativeRef ref = derivatives.get(i);
      json.append("{\"dvTableName\":").append(DvAiContextBuilder.jsonString(ref.getDvTableName()));
      json.append(",\"dvTableType\":")
          .append(DvAiContextBuilder.jsonString(String.valueOf(ref.getDvTableType())));
      json.append('}');
    }
  }

  private static void appendSatelliteConfigs(
      StringBuilder json, List<BvScd2SatelliteConfig> configs) {
    if (configs == null || configs.isEmpty()) {
      return;
    }
    for (int i = 0; i < configs.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      BvScd2SatelliteConfig config = configs.get(i);
      json.append("{\"satelliteName\":")
          .append(DvAiContextBuilder.jsonString(config.getSatelliteName()));
      json.append(",\"functionalTimestampField\":")
          .append(DvAiContextBuilder.jsonString(config.getFunctionalTimestampField()));
      json.append('}');
    }
  }

  public static String serializeModelSummary(BusinessVaultModel model) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":").append(DvAiContextBuilder.jsonString(model.getName()));
    json.append(",\"dataVaultModelPath\":")
        .append(DvAiContextBuilder.jsonString(model.getDataVaultModelPath()));
    json.append(",\"tables\":[");
    List<IBvTable> tables = model.getTables();
    for (int i = 0; i < tables.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      IBvTable table = tables.get(i);
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(table.getName()));
      json.append(",\"type\":")
          .append(DvAiContextBuilder.jsonString(String.valueOf(table.getTableType())));
      json.append(",\"targetTable\":").append(DvAiContextBuilder.jsonString(table.getTableName()));
      json.append('}');
    }
    json.append("],\"configuration\":");
    BusinessVaultConfiguration config = model.getConfigurationOrDefault();
    json.append("{\"targetDatabase\":")
        .append(DvAiContextBuilder.jsonString(config.getTargetDatabase()));
    json.append(",\"functionalTimestampField\":")
        .append(DvAiContextBuilder.jsonString(config.getFunctionalTimestampField()));
    json.append(",\"loadDateFieldFallback\":")
        .append(DvAiContextBuilder.jsonString(config.getLoadDateFieldFallback()));
    json.append(",\"validFromField\":")
        .append(DvAiContextBuilder.jsonString(config.getValidFromField()));
    json.append(",\"validToField\":")
        .append(DvAiContextBuilder.jsonString(config.getValidToField()));
    DvTargetLoadAiConfigurationSupport.appendTargetLoadSummaryJson(json, config);
    json.append("}}");
    return json.toString();
  }

  private static String serializeLinkedDvModelStructure(
      BusinessVaultModel model, IVariables variables, IHopMetadataProvider metadataProvider) {
    try {
      DataVaultModel dvModel =
          BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
              model, variables, metadataProvider);
      if (dvModel == null || dvModel.getTables().isEmpty()) {
        return "{}";
      }
      return DvAiContextBuilder.serializeModelStructure(dvModel);
    } catch (Exception e) {
      return "{\"loaded\":false,\"error\":" + DvAiContextBuilder.jsonString(e.getMessage()) + "}";
    }
  }

  private static String serializeHopMetadata(
      BusinessVaultModel model, IHopMetadataProvider metadataProvider, IVariables variables) {
    if (metadataProvider == null) {
      return "{}";
    }
    BusinessVaultConfiguration config = model.getConfigurationOrDefault();
    StringBuilder json = new StringBuilder();
    json.append("{\"targetDatabase\":");
    json.append(describeDatabase(metadataProvider, variables, config.getTargetDatabase()));
    json.append('}');
    return json.toString();
  }

  private static String describeDatabase(
      IHopMetadataProvider metadataProvider, IVariables variables, String name) {
    if (Utils.isEmpty(name)) {
      return "null";
    }
    try {
      String resolved = variables != null ? variables.resolve(name) : name;
      DatabaseMeta databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(resolved);
      if (databaseMeta == null) {
        return DvAiContextBuilder.jsonString(resolved);
      }
      return "{\"name\":"
          + DvAiContextBuilder.jsonString(databaseMeta.getName())
          + ",\"type\":"
          + DvAiContextBuilder.jsonString(databaseMeta.getPluginId())
          + ",\"host\":"
          + DvAiContextBuilder.jsonString(databaseMeta.getHostname())
          + ",\"database\":"
          + DvAiContextBuilder.jsonString(databaseMeta.getDatabaseName())
          + "}";
    } catch (Exception e) {
      return DvAiContextBuilder.jsonString(name);
    }
  }

  private static String serializeCheckResults(List<ICheckResult> results) {
    StringBuilder json = new StringBuilder();
    json.append("{\"results\":[");
    List<ICheckResult> safe = results != null ? results : new ArrayList<>();
    for (int i = 0; i < safe.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      ICheckResult result = safe.get(i);
      String type =
          result.getType() == ICheckResult.TYPE_RESULT_ERROR
              ? "ERROR"
              : result.getType() == ICheckResult.TYPE_RESULT_OK
                  ? "OK"
                  : result.getType() == ICheckResult.TYPE_RESULT_WARNING ? "WARNING" : "INFO";
      json.append("{\"type\":").append(DvAiContextBuilder.jsonString(type));
      json.append(",\"text\":").append(DvAiContextBuilder.jsonString(result.getText()));
      json.append(",\"source\":").append(DvAiContextBuilder.jsonString(formatCheckSource(result)));
      json.append('}');
    }
    json.append("]}");
    return json.toString();
  }

  private static String formatCheckSource(ICheckResult result) {
    if (result.getSourceInfo() == null) {
      return "";
    }
    String name = result.getSourceInfo().getName();
    String description = result.getSourceInfo().getDescription();
    if (Utils.isEmpty(name)) {
      return description != null ? description : "";
    }
    if (Utils.isEmpty(description)) {
      return name;
    }
    return name + ": " + description;
  }

  private static String truncate(String value, int maxChars) {
    if (value == null) {
      return "";
    }
    if (value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, maxChars) + "\n... [truncated]";
  }
}
