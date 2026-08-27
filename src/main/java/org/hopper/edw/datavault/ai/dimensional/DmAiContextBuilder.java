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
package org.hopper.edw.datavault.ai.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.ai.DvAiContextBuilder;
import org.hopper.edw.datavault.ai.DvTargetLoadAiConfigurationSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionLoadStrategySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.metrics.ExecutionInfoAiContextBuilder;
import org.hopper.edw.datavault.metrics.MetricsAiContextBuilder;

/** Assembles redacted context for dimensional model AI advisory calls. */
public final class DmAiContextBuilder {

  private static final int MAX_MODEL_XML_CHARS = 120_000;
  private static final int MAX_LOG_CHARS = 20_000;

  private DmAiContextBuilder() {}

  public static DmAiContextBundle build(
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DmAiRequest request)
      throws HopException {
    if (model == null) {
      throw new HopException("No dimensional model is open");
    }
    if (request == null || Utils.isEmpty(request.getUserPrompt())) {
      throw new HopException("Please enter a question for the AI advisor");
    }

    DmAiScenario scenario =
        request.getScenario() != null ? request.getScenario() : DmAiScenario.GENERAL;

    String modelXml = "";
    if (request.isIncludeModelXml()) {
      modelXml = truncate(XmlMetadataUtil.serializeObjectToXml(model), MAX_MODEL_XML_CHARS);
    }

    String checkResultsJson = "";
    if (request.isIncludeCheckResults() && metadataProvider != null) {
      checkResultsJson = serializeCheckResults(model.check(metadataProvider, variables));
    }

    String loadRunMetricsJson =
        MetricsAiContextBuilder.buildMetricsContext(
            request.isIncludeLoadRunMetrics(),
            scenario == DmAiScenario.PERFORMANCE_TUNING,
            request.getUserPrompt(),
            model.getName(),
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DM,
            metadataProvider,
            variables);
    String executionInfoJson =
        ExecutionInfoAiContextBuilder.buildExecutionInfoContext(
            request.isIncludeExecutionInfo(),
            scenario == DmAiScenario.PERFORMANCE_TUNING,
            request.getUserPrompt(),
            model.getName(),
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DM,
            metadataProvider,
            variables);

    return DmAiContextBundle.builder()
        .scenario(scenario)
        .userPrompt(request.getUserPrompt())
        .modelXml(modelXml)
        .modelSummaryJson(serializeModelSummary(model))
        .modelStructureJson(serializeModelStructure(model))
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

  public static String serializeModelStructure(DimensionalModel model) {
    StringBuilder json = new StringBuilder();
    json.append("{\"tables\":[");
    List<IDmTable> tables = model != null ? model.getTables() : List.of();
    for (int i = 0; i < tables.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      IDmTable table = tables.get(i);
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(table.getName()));
      json.append(",\"type\":")
          .append(DvAiContextBuilder.jsonString(String.valueOf(table.getTableType())));
      json.append(",\"targetTable\":").append(DvAiContextBuilder.jsonString(table.getTableName()));
      if (table instanceof DmDimension dimension) {
        json.append(",\"loadStrategy\":")
            .append(
                DvAiContextBuilder.jsonString(
                    DmDimensionLoadStrategySupport.resolveDisplayLabel(dimension)));
        DmSurrogateKeyStrategy strategy = DmSurrogateKeySupport.resolveStrategy(dimension);
        json.append(",\"surrogateKeyStrategy\":")
            .append(DvAiContextBuilder.jsonString(String.valueOf(strategy)));
        String surrogateField =
            DmSurrogateKeySupport.resolveSurrogateKeyField(
                dimension, model != null ? model.getConfigurationOrDefault() : null, null);
        if (!Utils.isEmpty(surrogateField)) {
          json.append(",\"surrogateKeyField\":")
              .append(DvAiContextBuilder.jsonString(surrogateField));
        }
        if (!Utils.isEmpty(dimension.getSurrogateKeySourceField())) {
          json.append(",\"surrogateKeySourceField\":")
              .append(DvAiContextBuilder.jsonString(dimension.getSurrogateKeySourceField()));
        }
        json.append(",\"naturalKeys\":[");
        appendNaturalKeys(json, dimension.getNaturalKeys());
        json.append("],\"attributes\":[");
        appendAttributes(json, dimension.getAttributes());
        json.append(']');
      } else if (table instanceof DmDimensionAlias alias) {
        json.append(",\"referencedDimension\":")
            .append(DvAiContextBuilder.jsonString(alias.getReferencedDimensionName()));
        if (!Utils.isEmpty(alias.getReferencedModelFilename())) {
          json.append(",\"referencedModelFilename\":")
              .append(DvAiContextBuilder.jsonString(alias.getReferencedModelFilename()));
          json.append(",\"external\":true");
        }
        json.append(",\"physicalTable\":")
            .append(
                DvAiContextBuilder.jsonString(
                    DmDimensionResolutionSupport.resolvePhysicalTableName(
                        model, alias.getName(), null)));
      } else if (table instanceof IDmFactLikeTable factLike) {
        json.append(",\"measures\":[");
        appendMeasures(json, factLike.getMeasuresOrEmpty());
        json.append("],\"dimensionRoles\":[");
        appendDimensionRoles(json, factLike.getDimensionRolesOrEmpty());
        json.append(']');
      }
      json.append('}');
    }
    json.append("]}");
    return json.toString();
  }

  private static void appendNaturalKeys(StringBuilder json, List<DmNaturalKeyField> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    for (int i = 0; i < keys.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      DmNaturalKeyField key = keys.get(i);
      json.append("{\"fieldName\":").append(DvAiContextBuilder.jsonString(key.getFieldName()));
      json.append('}');
    }
  }

  private static void appendAttributes(StringBuilder json, List<DmDimensionAttribute> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return;
    }
    for (int i = 0; i < attributes.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      DmDimensionAttribute attribute = attributes.get(i);
      json.append("{\"fieldName\":")
          .append(DvAiContextBuilder.jsonString(attribute.getFieldName()));
      json.append('}');
    }
  }

  private static void appendMeasures(StringBuilder json, List<DmFactMeasure> measures) {
    if (measures == null || measures.isEmpty()) {
      return;
    }
    for (int i = 0; i < measures.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      DmFactMeasure measure = measures.get(i);
      json.append("{\"fieldName\":").append(DvAiContextBuilder.jsonString(measure.getFieldName()));
      json.append(",\"additive\":").append(measure.isAdditive());
      json.append('}');
    }
  }

  private static void appendDimensionRoles(StringBuilder json, List<DmFactDimensionRole> roles) {
    if (roles == null || roles.isEmpty()) {
      return;
    }
    for (int i = 0; i < roles.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      DmFactDimensionRole role = roles.get(i);
      json.append("{\"roleName\":").append(DvAiContextBuilder.jsonString(role.getRoleName()));
      json.append(",\"dimensionTable\":")
          .append(DvAiContextBuilder.jsonString(role.getDimensionTableName()));
      json.append('}');
    }
  }

  public static String serializeModelSummary(DimensionalModel model) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":").append(DvAiContextBuilder.jsonString(model.getName()));
    json.append(",\"tables\":[");
    List<IDmTable> tables = model.getTables();
    for (int i = 0; i < tables.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      IDmTable table = tables.get(i);
      json.append("{\"name\":").append(DvAiContextBuilder.jsonString(table.getName()));
      json.append(",\"type\":")
          .append(DvAiContextBuilder.jsonString(String.valueOf(table.getTableType())));
      json.append(",\"targetTable\":").append(DvAiContextBuilder.jsonString(table.getTableName()));
      json.append('}');
    }
    json.append("],\"configuration\":");
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    json.append("{\"targetDatabase\":")
        .append(DvAiContextBuilder.jsonString(config.getTargetDatabase()));
    json.append(",\"sourceDatabase\":")
        .append(DvAiContextBuilder.jsonString(config.getSourceDatabase()));
    json.append(",\"dimKeyField\":").append(DvAiContextBuilder.jsonString(config.getDimKeyField()));
    json.append(",\"versionField\":")
        .append(DvAiContextBuilder.jsonString(config.getVersionField()));
    json.append(",\"dateFromField\":")
        .append(DvAiContextBuilder.jsonString(config.getDateFromField()));
    json.append(",\"dateToField\":").append(DvAiContextBuilder.jsonString(config.getDateToField()));
    json.append(",\"loadDateField\":")
        .append(DvAiContextBuilder.jsonString(config.getLoadDateField()));
    json.append(",\"currentFlagField\":")
        .append(DvAiContextBuilder.jsonString(config.getCurrentFlagField()));
    DvTargetLoadAiConfigurationSupport.appendTargetLoadSummaryJson(json, config);
    json.append("}}");
    return json.toString();
  }

  private static String serializeHopMetadata(
      DimensionalModel model, IHopMetadataProvider metadataProvider, IVariables variables) {
    if (metadataProvider == null) {
      return "{}";
    }
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    StringBuilder json = new StringBuilder();
    json.append("{\"targetDatabase\":");
    json.append(describeDatabase(metadataProvider, variables, config.getTargetDatabase()));
    json.append(",\"sourceDatabase\":");
    json.append(describeDatabase(metadataProvider, variables, config.getSourceDatabase()));
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
