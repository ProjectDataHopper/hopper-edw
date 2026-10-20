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
package org.hopper.edw.datavault.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.hopgui.ai.EdwAiBoundModel;
import org.hopper.edw.datavault.hopgui.ai.EdwAiBoundModel.CatalogCoachSource;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvModelCheckOptions;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metrics.ExecutionInfoAiContextBuilder;
import org.hopper.edw.datavault.metrics.MetricsAiContextBuilder;

/** Assembles redacted context for AI advisory calls. */
public final class DvAiContextBuilder {

  private static final int MAX_MODEL_XML_CHARS = 120_000;
  private static final int MAX_LOG_CHARS = 20_000;

  /** Coach-sized bound so a full catalog does not land in one question. */
  static final int MAX_CATALOG_SOURCES_IN_PROMPT = 20;

  private DvAiContextBuilder() {}

  public static DvAiContextBundle build(
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DvAiRequest request)
      throws HopException {
    if (model == null) {
      throw new HopException("No Data Vault model is open");
    }
    if (request == null || Utils.isEmpty(request.getUserPrompt())) {
      throw new HopException("Please enter a question for the AI advisor");
    }

    DvAiScenario scenario =
        request.getScenario() != null ? request.getScenario() : DvAiScenario.GENERAL;

    String modelXml = "";
    if (request.isIncludeModelXml()) {
      modelXml = truncate(XmlMetadataUtil.serializeObjectToXml(model), MAX_MODEL_XML_CHARS);
    }

    String checkResultsJson = "";
    if (request.isIncludeCheckResults() && metadataProvider != null) {
      List<ICheckResult> results =
          model.check(metadataProvider, variables, DvModelCheckOptions.defaults());
      checkResultsJson = serializeCheckResults(results);
    }

    String recordDefinitionsJson = "";
    if (request.isIncludeCatalogSources()) {
      recordDefinitionsJson =
          serializeRecordDefinitions(model, metadataProvider, variables, request);
    }

    String loadRunMetricsJson =
        MetricsAiContextBuilder.buildMetricsContext(
            request.isIncludeLoadRunMetrics(),
            scenario == DvAiScenario.PERFORMANCE_TUNING,
            request.getUserPrompt(),
            model.getName(),
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
            metadataProvider,
            variables);
    String executionInfoJson =
        ExecutionInfoAiContextBuilder.buildExecutionInfoContext(
            request.isIncludeExecutionInfo(),
            scenario == DvAiScenario.PERFORMANCE_TUNING,
            request.getUserPrompt(),
            model.getName(),
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
            metadataProvider,
            variables);

    return DvAiContextBundle.builder()
        .scenario(scenario)
        .userPrompt(request.getUserPrompt())
        .modelXml(modelXml)
        .modelSummaryJson(serializeModelSummary(model, variables))
        .modelStructureJson(serializeModelStructure(model))
        .recordDefinitionsJson(recordDefinitionsJson)
        .hopMetadataJson(serializeHopMetadata(model, metadataProvider, variables))
        .checkResultsJson(checkResultsJson)
        .loadRunMetricsJson(loadRunMetricsJson)
        .executionInfoJson(executionInfoJson)
        .sourceClassificationJson(
            SourceToVaultAiContextBuilder.buildSourceClassificationContext(
                model, variables, metadataProvider))
        .logsExcerpt(truncate(request.getLogsExcerpt(), MAX_LOG_CHARS))
        .followUp(request.isFollowUp())
        .appliedChangeSummaries(
            request.getAppliedChangeSummaries() != null
                ? request.getAppliedChangeSummaries()
                : List.of())
        .build();
  }

  public static String serializeModelStructure(DataVaultModel model) {
    StringBuilder json = new StringBuilder();
    json.append("{\"tables\":[");
    List<IDvTable> tables = model != null ? model.getTables() : List.of();
    for (int i = 0; i < tables.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      IDvTable table = tables.get(i);
      json.append("{\"name\":").append(jsonString(table.getName()));
      json.append(",\"type\":").append(jsonString(String.valueOf(table.getTableType())));
      json.append(",\"targetTable\":").append(jsonString(table.getTableName()));
      if (table instanceof DvHub hub) {
        json.append(",\"recordSources\":[");
        appendStringArray(json, hub.getRecordSources());
        json.append("],\"businessKeys\":[");
        appendBusinessKeys(json, hub.getBusinessKeys());
        json.append(']');
      } else if (table instanceof DvLink link) {
        json.append(",\"hubNames\":[");
        appendStringArray(json, link.getHubNames());
        json.append("],\"drivingKeyNames\":[");
        appendStringArray(json, link.getDrivingKeyNames());
        json.append("],\"hasDescriptiveAttributes\":").append(link.isHasDescriptiveAttributes());
        json.append(",\"linkSatelliteNames\":[");
        appendStringArray(json, link.getLinkSatelliteNames());
        json.append(']');
      } else if (table instanceof DvSatellite satellite) {
        json.append(",\"hubName\":").append(jsonString(satellite.getHubName()));
        json.append(",\"linkName\":").append(jsonString(satellite.getLinkName()));
        json.append(",\"recordSource\":").append(jsonString(satellite.getRecordSource()));
        json.append(",\"drivingKey\":").append(jsonString(satellite.getDrivingKey()));
        json.append(",\"attributes\":[");
        appendSatelliteAttributes(json, satellite.getAttributes());
        json.append(']');
      }
      json.append('}');
    }
    json.append("]}");
    return json.toString();
  }

  private static void appendStringArray(StringBuilder json, List<String> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append(jsonString(values.get(i)));
    }
  }

  private static void appendBusinessKeys(StringBuilder json, List<BusinessKey> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    for (int i = 0; i < keys.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      BusinessKey key = keys.get(i);
      json.append("{\"name\":").append(jsonString(key.getName()));
      json.append(",\"recordSourceName\":").append(jsonString(key.getRecordSourceName()));
      json.append(",\"sourceFieldName\":").append(jsonString(key.getSourceFieldName()));
      json.append('}');
    }
  }

  private static void appendSatelliteAttributes(
      StringBuilder json, List<SatelliteAttribute> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return;
    }
    for (int i = 0; i < attributes.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      SatelliteAttribute attribute = attributes.get(i);
      json.append("{\"name\":").append(jsonString(attribute.getName()));
      json.append(",\"dataType\":").append(jsonString(attribute.getDataType()));
      json.append('}');
    }
  }

  private static String serializeModelSummary(DataVaultModel model, IVariables variables) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":").append(jsonString(model.getName()));
    json.append(",\"tables\":[");
    List<IDvTable> tables = model.getTables();
    for (int i = 0; i < tables.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      IDvTable table = tables.get(i);
      json.append("{\"name\":").append(jsonString(table.getName()));
      json.append(",\"type\":").append(jsonString(String.valueOf(table.getTableType())));
      json.append(",\"targetTable\":").append(jsonString(table.getTableName()));
      json.append('}');
    }
    json.append("],\"configuration\":");
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    json.append("{\"targetDatabase\":").append(jsonString(config.getTargetDatabase()));
    json.append(",\"dataCatalogConnection\":")
        .append(jsonString(config.getDataCatalogConnection()));
    json.append(",\"sortRowsSize\":").append(jsonString(config.getSortRowsSize()));
    DvTargetLoadAiConfigurationSupport.appendTargetLoadSummaryJson(json, config);
    json.append("}}");
    return json.toString();
  }

  private static String serializeHopMetadata(
      DataVaultModel model, IHopMetadataProvider metadataProvider, IVariables variables) {
    if (metadataProvider == null) {
      return "{}";
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
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
        return jsonString(resolved);
      }
      return "{\"name\":"
          + jsonString(databaseMeta.getName())
          + ",\"type\":"
          + jsonString(databaseMeta.getPluginId())
          + ",\"host\":"
          + jsonString(databaseMeta.getHostname())
          + ",\"database\":"
          + jsonString(databaseMeta.getDatabaseName())
          + "}";
    } catch (Exception e) {
      return jsonString(name);
    }
  }

  private static String serializeRecordDefinitions(
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DvAiRequest request) {
    return serializeCatalogSources(model, metadataProvider, variables, request);
  }

  /**
   * Record-definition JSON for a same-loader or foreign Data Vault model. Explicit picker ids win,
   * then Coach records, then sources already bound on tables. At most {@link
   * #MAX_CATALOG_SOURCES_IN_PROMPT} sources are attached.
   */
  static String serializeCatalogSources(
      Object model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DvAiRequest request) {
    Map<String, CatalogCoachSource> coachByName = new LinkedHashMap<>();
    for (CatalogCoachSource source : EdwAiBoundModel.coachCatalogSources(model)) {
      if (source != null && !Utils.isEmpty(source.recordName())) {
        coachByName.putIfAbsent(source.recordName(), source);
      }
    }
    String catalogConnection =
        EdwAiBoundModel.catalogConnectionName(
            model, DvSourceCatalogService.DEFAULT_SOURCE_CATALOG_CONNECTION);
    if (variables != null && !Utils.isEmpty(catalogConnection)) {
      catalogConnection = variables.resolve(catalogConnection);
    }
    String namespace = DvSourceCatalogService.projectSourcesNamespace(variables);

    int totalAvailable = 0;
    if (metadataProvider != null && !Utils.isEmpty(catalogConnection)) {
      try {
        // Session-cached name list (registry); avoid ad-hoc full ref copies per AI turn.
        totalAvailable =
            DvSourceCatalogService.listSourceNames(catalogConnection, variables, metadataProvider)
                .size();
      } catch (Exception ignored) {
        // Ignore listing errors for metadata envelope
      }
    }

    List<String> boundNames =
        model instanceof DataVaultModel dataVaultModel
            ? collectSourceNamesFromModel(dataVaultModel).stream().toList()
            : reflectBoundSourceNames(model);
    List<String> sourceNames =
        capCatalogSources(
            resolveSelectedCatalogSourceNames(
                boundNames, request, new ArrayList<>(coachByName.keySet())));

    StringBuilder json = new StringBuilder();
    json.append("{\"catalogConnection\":").append(jsonString(catalogConnection));
    json.append(",\"namespace\":").append(jsonString(namespace));
    json.append(",\"totalAvailable\":").append(totalAvailable);
    json.append(",\"selected\":").append(sourceNames.size());
    json.append(",\"sources\":[");
    int index = 0;
    for (String sourceName : sourceNames) {
      if (index++ > 0) {
        json.append(',');
      }
      CatalogCoachSource coach = coachByName.get(sourceName);
      String entryConnection = catalogConnection;
      String entryNamespace = namespace;
      if (coach != null && !Utils.isEmpty(coach.catalogConnection())) {
        entryConnection = coach.catalogConnection();
      }
      if (coach != null && !Utils.isEmpty(coach.recordNamespace())) {
        entryNamespace = coach.recordNamespace();
      }
      if (variables != null) {
        if (!Utils.isEmpty(entryConnection)) {
          entryConnection = variables.resolve(entryConnection);
        }
        if (!Utils.isEmpty(entryNamespace)) {
          entryNamespace = variables.resolve(entryNamespace);
        }
      }
      json.append(
          serializeRecordDefinitionEntry(
              sourceName, entryConnection, entryNamespace, variables, metadataProvider));
    }
    json.append("]}");
    return json.toString();
  }

  private static String serializeRecordDefinitionEntry(
      String sourceName,
      String catalogConnection,
      String namespace,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      return serializeRecordDefinitionEntry(
          sourceName,
          loadRecordDefinition(
              sourceName, catalogConnection, namespace, variables, metadataProvider));
    } catch (Exception e) {
      StringBuilder entry = new StringBuilder();
      entry.append("{\"name\":").append(jsonString(sourceName));
      entry.append(",\"loaded\":false");
      entry.append(",\"error\":").append(jsonString(e.getMessage()));
      entry.append(",\"fields\":[]}");
      return entry.toString();
    }
  }

  /** One catalog record, including field length and key position when the layout has them. */
  static String serializeRecordDefinitionEntry(String sourceName, RecordDefinition definition) {
    StringBuilder entry = new StringBuilder();
    entry.append("{\"name\":").append(jsonString(sourceName));
    if (definition == null) {
      entry.append(",\"loaded\":false,\"fields\":[]}");
      return entry.toString();
    }
    entry.append(",\"loaded\":true");
    entry.append(",\"description\":").append(jsonString(definition.getDescription()));
    entry.append(",\"fields\":[");
    appendFieldJson(entry, definition);
    entry.append("]}");
    return entry.toString();
  }

  private static void appendFieldJson(StringBuilder entry, RecordDefinition definition) {
    List<CatalogSourceField> catalogFields = catalogFields(definition);
    if (!catalogFields.isEmpty()) {
      for (int f = 0; f < catalogFields.size(); f++) {
        if (f > 0) {
          entry.append(',');
        }
        CatalogSourceField field = catalogFields.get(f);
        entry.append("{\"name\":").append(jsonString(field.getName()));
        entry.append(",\"type\":").append(jsonString(hopTypeName(field)));
        if (!Utils.isEmpty(field.getLength())) {
          entry.append(",\"length\":").append(jsonString(field.getLength()));
        }
        if (field.getPrimaryKeyPosition() > 0) {
          entry.append(",\"keyPosition\":").append(field.getPrimaryKeyPosition());
        }
        entry.append('}');
      }
      return;
    }
    if (definition.getFields() == null) {
      return;
    }
    List<IValueMeta> fields = definition.getFields().getValueMetaList();
    for (int f = 0; f < fields.size(); f++) {
      if (f > 0) {
        entry.append(',');
      }
      IValueMeta valueMeta = fields.get(f);
      entry.append("{\"name\":").append(jsonString(valueMeta.getName()));
      entry.append(",\"type\":").append(jsonString(valueMeta.getTypeDesc()));
      entry.append('}');
    }
  }

  private static List<CatalogSourceField> catalogFields(RecordDefinition definition) {
    DvSourceRecord dvSource = definition == null ? null : definition.getDvSource();
    if (dvSource == null || dvSource.getFields() == null) {
      return List.of();
    }
    List<CatalogSourceField> fields = new ArrayList<>();
    for (CatalogSourceField field : dvSource.getFields()) {
      if (field != null && !Utils.isEmpty(field.getName())) {
        fields.add(field);
      }
    }
    return fields;
  }

  private static String hopTypeName(CatalogSourceField field) {
    String name = null;
    try {
      name = ValueMetaFactory.getValueMetaName(field.getHopType());
    } catch (RuntimeException ignored) {
      // Plugin registry may be unavailable; fall back to the catalog source type.
    }
    if (name != null && !"-".equals(name)) {
      return name;
    }
    if (!Utils.isEmpty(field.getSourceDataType())) {
      return field.getSourceDataType();
    }
    return name == null ? "" : name;
  }

  static List<String> resolveSelectedCatalogSourceNames(DataVaultModel model, DvAiRequest request) {
    return resolveSelectedCatalogSourceNames(
        model == null ? List.of() : collectSourceNamesFromModel(model).stream().toList(),
        request,
        null);
  }

  /**
   * Explicit picker ids, otherwise Coach record names, otherwise sources already on the model. An
   * explicit list that is present but filters down to nothing stays empty.
   */
  static List<String> resolveSelectedCatalogSourceNames(
      DataVaultModel model, DvAiRequest request, List<String> coachNames) {
    return resolveSelectedCatalogSourceNames(
        model == null ? List.of() : collectSourceNamesFromModel(model).stream().toList(),
        request,
        coachNames);
  }

  private static List<String> resolveSelectedCatalogSourceNames(
      List<String> modelSourceNames, DvAiRequest request, List<String> coachNames) {
    if (request != null
        && request.getCatalogSourceNames() != null
        && !request.getCatalogSourceNames().isEmpty()) {
      return filterNames(request.getCatalogSourceNames());
    }
    List<String> coach = filterNames(coachNames);
    if (!coach.isEmpty()) {
      return coach;
    }
    return filterNames(modelSourceNames);
  }

  private static List<String> filterNames(List<String> names) {
    if (names == null || names.isEmpty()) {
      return List.of();
    }
    return names.stream().filter(name -> !Utils.isEmpty(name)).toList();
  }

  private static List<String> capCatalogSources(List<String> names) {
    if (names.size() <= MAX_CATALOG_SOURCES_IN_PROMPT) {
      return names;
    }
    return List.copyOf(names.subList(0, MAX_CATALOG_SOURCES_IN_PROMPT));
  }

  private static RecordDefinition loadRecordDefinition(
      String sourceName,
      String catalogConnection,
      String namespace,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String resolvedName = variables != null ? variables.resolve(sourceName) : sourceName;
    String resolvedNamespace =
        !Utils.isEmpty(namespace)
            ? namespace
            : DvSourceCatalogService.projectSourcesNamespace(variables);
    return RecordDefinitionRegistry.getInstance()
        .read(
            catalogConnection,
            new RecordDefinitionKey(resolvedNamespace, resolvedName),
            variables,
            metadataProvider);
  }

  private static List<String> reflectBoundSourceNames(Object model) {
    Set<String> names = new LinkedHashSet<>();
    Object tables = invokeGetter(model, "getTables");
    if (!(tables instanceof Iterable<?> iterable)) {
      return List.of();
    }
    for (Object table : iterable) {
      addReflectedNames(names, invokeGetter(table, "getRecordSources"));
      addReflectedName(names, invokeGetter(table, "getRecordSource"));
      Object linkSources = invokeGetter(table, "getLinkHubSources");
      if (linkSources instanceof Iterable<?> links) {
        for (Object link : links) {
          addReflectedName(names, invokeGetter(link, "getSourceName"));
        }
      }
    }
    names.removeIf(Utils::isEmpty);
    return List.copyOf(names);
  }

  private static void addReflectedNames(Set<String> names, Object values) {
    if (values instanceof Iterable<?> iterable) {
      for (Object value : iterable) {
        addReflectedName(names, value);
      }
    }
  }

  private static void addReflectedName(Set<String> names, Object value) {
    if (value instanceof String text) {
      names.add(text);
    }
  }

  private static Object invokeGetter(Object target, String method) {
    if (target == null) {
      return null;
    }
    try {
      return target.getClass().getMethod(method).invoke(target);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  public static Set<String> collectSourceNamesFromModel(DataVaultModel model) {
    Set<String> names = new LinkedHashSet<>();
    for (IDvTable table : model.getTables()) {
      if (table instanceof DvHub hub && hub.getRecordSources() != null) {
        names.addAll(hub.getRecordSources());
      } else if (table instanceof DvSatellite satellite
          && !Utils.isEmpty(satellite.getRecordSource())) {
        names.add(satellite.getRecordSource());
      } else if (table instanceof DvLink link && link.getLinkHubSources() != null) {
        for (DvLink.DvLinkHubSource source : link.getLinkHubSources()) {
          if (source != null && !Utils.isEmpty(source.getSourceName())) {
            names.add(source.getSourceName());
          }
        }
      }
    }
    names.removeIf(Utils::isEmpty);
    return names;
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
      json.append("{\"type\":").append(jsonString(type));
      json.append(",\"text\":").append(jsonString(result.getText()));
      json.append(",\"source\":").append(jsonString(formatCheckSource(result)));
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

  public static String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder();
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '"' -> sb.append("\\\"");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
    return sb.toString();
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
