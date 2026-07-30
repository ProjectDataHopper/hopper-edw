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
 *
 */

package org.apache.hop.datavault.openlineage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.lineage.FieldContribution;
import org.apache.hop.datavault.lineage.FieldLineage;
import org.apache.hop.datavault.lineage.FieldTransform;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineage.LineageReason;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineage.TableLineage;
import org.apache.hop.datavault.lineage.TableSourceKind;
import org.apache.hop.datavault.lineage.TableSourceRef;

/**
 * Maps model-derived {@link LineageSnapshot} tables to OpenLineage COMPLETE {@code RunEvent}
 * documents (Jackson trees).
 *
 * <p>Each target table becomes one job with a <strong>unique</strong> run id (required by Marquez).
 * An optional export correlation id is stored on the hop_export run facet.
 */
public final class OpenLineageSnapshotMapper {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OpenLineageSnapshotMapper() {}

  /**
   * @param snapshot model lineage snapshot
   * @param jobNamespace base job namespace (project suffix applied from snapshot when present)
   * @param includeColumnLineage when true, emit columnLineage facets on outputs
   * @param exportRunId correlation id for the whole export (not used as OpenLineage runId)
   * @return one COMPLETE RunEvent object per target table
   */
  public static List<ObjectNode> toRunEvents(
      LineageSnapshot snapshot,
      String jobNamespace,
      boolean includeColumnLineage,
      String exportRunId) {
    return toRunEvents(snapshot, jobNamespace, null, includeColumnLineage, exportRunId);
  }

  /**
   * @param datasetNamespace when non-empty, overrides dataset namespaces for all inputs/outputs
   *     (otherwise Hop connection / catalog / staging names are used)
   */
  public static List<ObjectNode> toRunEvents(
      LineageSnapshot snapshot,
      String jobNamespace,
      String datasetNamespace,
      boolean includeColumnLineage,
      String exportRunId) {
    if (snapshot == null || snapshot.getTables().isEmpty()) {
      return List.of();
    }
    String ns = resolveJobNamespace(jobNamespace, snapshot.getProjectKey());
    String dsNs = Utils.isEmpty(datasetNamespace) ? null : datasetNamespace.trim();
    String correlationId = Utils.isEmpty(exportRunId) ? UUID.randomUUID().toString() : exportRunId;
    String eventTime = Instant.now().toString();
    String modelName = Utils.isEmpty(snapshot.getModelName()) ? "model" : snapshot.getModelName();
    LineageLayer layer =
        snapshot.getModelLayer() != null ? snapshot.getModelLayer() : LineageLayer.DV;

    List<ObjectNode> events = new ArrayList<>();
    for (TableLineage table : snapshot.getTables()) {
      if (table == null) {
        continue;
      }
      String physical =
          !Utils.isEmpty(table.getPhysicalTableName())
              ? table.getPhysicalTableName()
              : table.getLogicalName();
      if (Utils.isEmpty(physical)) {
        continue;
      }
      // Marquez requires a unique runId per job run; never reuse one UUID across jobs.
      String runId = UUID.randomUUID().toString();
      events.add(
          toRunEvent(
              table,
              ns,
              dsNs,
              layer,
              modelName,
              physical,
              runId,
              correlationId,
              eventTime,
              includeColumnLineage,
              snapshot));
    }
    enrichInputSchemasFromOutputs(events);
    return events;
  }

  public static ObjectNode toRunEvent(
      TableLineage table,
      String jobNamespace,
      LineageLayer layer,
      String modelName,
      String physicalTableName,
      String runId,
      String eventTime,
      boolean includeColumnLineage,
      LineageSnapshot snapshot) {
    return toRunEvent(
        table,
        jobNamespace,
        null,
        layer,
        modelName,
        physicalTableName,
        runId,
        null,
        eventTime,
        includeColumnLineage,
        snapshot);
  }

  public static ObjectNode toRunEvent(
      TableLineage table,
      String jobNamespace,
      String datasetNamespace,
      LineageLayer layer,
      String modelName,
      String physicalTableName,
      String runId,
      String exportCorrelationId,
      String eventTime,
      boolean includeColumnLineage,
      LineageSnapshot snapshot) {
    ObjectNode event = MAPPER.createObjectNode();
    event.put("eventType", "COMPLETE");
    event.put("eventTime", eventTime != null ? eventTime : Instant.now().toString());
    event.put("producer", OpenLineageConstants.PRODUCER);
    event.put("schemaURL", "https://openlineage.io/spec/2-0-2/OpenLineage.json#/$defs/RunEvent");

    ObjectNode run = MAPPER.createObjectNode();
    run.put("runId", Utils.isEmpty(runId) ? UUID.randomUUID().toString() : runId);
    ObjectNode runFacets = MAPPER.createObjectNode();
    ObjectNode hopExport = MAPPER.createObjectNode();
    hopExport.put("_producer", OpenLineageConstants.PRODUCER);
    hopExport.put("_schemaURL", "https://github.com/mattcasters/hop-data-vault#hop-export-facet");
    hopExport.put("modelLayer", layer != null ? layer.name() : LineageLayer.DV.name());
    hopExport.put("modelName", modelName);
    if (!Utils.isEmpty(exportCorrelationId)) {
      hopExport.put("exportRunId", exportCorrelationId);
    }
    if (snapshot != null && !Utils.isEmpty(snapshot.getModelFilename())) {
      hopExport.put("modelFilename", snapshot.getModelFilename());
    }
    if (!Utils.isEmpty(table.getTableType())) {
      hopExport.put("tableType", table.getTableType());
    }
    if (!Utils.isEmpty(table.getLogicalName())) {
      hopExport.put("logicalName", table.getLogicalName());
    }
    runFacets.set("hop_export", hopExport);
    run.set("facets", runFacets);
    event.set("run", run);

    ObjectNode job = MAPPER.createObjectNode();
    job.put("namespace", jobNamespace);
    job.put(
        "name",
        layer.name().toLowerCase()
            + "/"
            + sanitizePathSegment(modelName)
            + "/"
            + physicalTableName);
    event.set("job", job);

    // Collect input datasets and per-input field names (for schema facets on sources).
    Map<String, DatasetKey> inputKeys = new LinkedHashMap<>();
    Map<String, Map<String, String>> inputFieldTypes = new LinkedHashMap<>();

    for (TableSourceRef source : table.getSources()) {
      DatasetKey key =
          applyDatasetNamespace(datasetKeyFromTableSource(source, table), datasetNamespace);
      if (key != null) {
        inputKeys.putIfAbsent(key.id(), key);
        inputFieldTypes.computeIfAbsent(key.id(), ignored -> new LinkedHashMap<>());
      }
    }
    for (FieldLineage field : table.getFields()) {
      if (field == null) {
        continue;
      }
      for (FieldContribution contribution : field.getContributions()) {
        DatasetKey key =
            applyDatasetNamespace(
                datasetKeyFromContribution(contribution, table), datasetNamespace);
        if (key == null) {
          continue;
        }
        inputKeys.putIfAbsent(key.id(), key);
        Map<String, String> fields =
            inputFieldTypes.computeIfAbsent(key.id(), ignored -> new LinkedHashMap<>());
        if (!Utils.isEmpty(contribution.getSourceFieldName())) {
          fields.putIfAbsent(contribution.getSourceFieldName(), null);
        }
      }
    }

    ArrayNode inputs = MAPPER.createArrayNode();
    for (DatasetKey key : inputKeys.values()) {
      ObjectNode input = datasetNode(key);
      Map<String, String> fields = inputFieldTypes.get(key.id());
      if (fields != null && !fields.isEmpty()) {
        ObjectNode facets = MAPPER.createObjectNode();
        facets.set("schema", schemaFacetFromNames(fields));
        input.set("facets", facets);
      }
      inputs.add(input);
    }
    event.set("inputs", inputs);

    DatasetKey outputKey = applyDatasetNamespace(outputDatasetKey(table), datasetNamespace);
    ObjectNode output = datasetNode(outputKey);
    // Always attach output schema when fields are known so Marquez lists dataset columns.
    // Column lineage is optional on top of schema.
    if (!table.getFields().isEmpty()) {
      ObjectNode facets = MAPPER.createObjectNode();
      facets.set("schema", schemaFacet(table));
      if (includeColumnLineage) {
        facets.set("columnLineage", columnLineageFacet(table, datasetNamespace));
      }
      output.set("facets", facets);
    }
    ArrayNode outputs = MAPPER.createArrayNode();
    outputs.add(output);
    event.set("outputs", outputs);

    return event;
  }

  /**
   * After all events in an export are built, copy output schemas onto input dataset references that
   * share the same namespace/name (e.g. a DV hub used as input to a BV job).
   */
  public static void enrichInputSchemasFromOutputs(List<ObjectNode> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    Map<String, ArrayNode> outputSchemas = new LinkedHashMap<>();
    for (ObjectNode event : events) {
      if (event == null) {
        continue;
      }
      var outputsNode = event.get("outputs");
      if (outputsNode == null || !outputsNode.isArray()) {
        continue;
      }
      ArrayNode outputs = (ArrayNode) outputsNode;
      for (int i = 0; i < outputs.size(); i++) {
        var outNode = outputs.get(i);
        if (outNode == null || !outNode.isObject()) {
          continue;
        }
        ObjectNode out = (ObjectNode) outNode;
        String key = datasetId(out.path("namespace").asText(), out.path("name").asText());
        var fieldsNode = out.path("facets").path("schema").path("fields");
        if (fieldsNode != null && fieldsNode.isArray() && fieldsNode.size() > 0) {
          outputSchemas.put(key, (ArrayNode) fieldsNode);
        }
      }
    }
    if (outputSchemas.isEmpty()) {
      return;
    }
    for (ObjectNode event : events) {
      if (event == null) {
        continue;
      }
      var inputsNode = event.get("inputs");
      if (inputsNode == null || !inputsNode.isArray()) {
        continue;
      }
      ArrayNode inputs = (ArrayNode) inputsNode;
      for (int i = 0; i < inputs.size(); i++) {
        var inNode = inputs.get(i);
        if (inNode == null || !inNode.isObject()) {
          continue;
        }
        ObjectNode in = (ObjectNode) inNode;
        String key = datasetId(in.path("namespace").asText(), in.path("name").asText());
        ArrayNode known = outputSchemas.get(key);
        if (known == null) {
          continue;
        }
        var existingNode = in.path("facets").path("schema").path("fields");
        if (existingNode != null && existingNode.isArray() && existingNode.size() > 0) {
          ArrayNode existing = (ArrayNode) existingNode;
          // Merge missing names from the known output schema.
          Set<String> have = new LinkedHashSet<>();
          for (int f = 0; f < existing.size(); f++) {
            have.add(existing.get(f).path("name").asText());
          }
          for (int f = 0; f < known.size(); f++) {
            String name = known.get(f).path("name").asText();
            if (!Utils.isEmpty(name) && !have.contains(name)) {
              existing.add(known.get(f).deepCopy());
            }
          }
          continue;
        }
        ObjectNode facets =
            in.has("facets") && in.get("facets").isObject()
                ? (ObjectNode) in.get("facets")
                : MAPPER.createObjectNode();
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("_producer", OpenLineageConstants.PRODUCER);
        schema.put("_schemaURL", OpenLineageConstants.SCHEMA_FACET_URL);
        schema.set("fields", known.deepCopy());
        facets.set("schema", schema);
        in.set("facets", facets);
      }
    }
  }

  public static String toPrettyJson(ObjectNode event) throws Exception {
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(event);
  }

  public static String toCompactJson(ObjectNode event) throws Exception {
    return MAPPER.writeValueAsString(event);
  }

  static String resolveJobNamespace(String configured, String projectKey) {
    String base =
        Utils.isEmpty(configured) ? OpenLineageConstants.DEFAULT_JOB_NAMESPACE : configured.trim();
    if (!Utils.isEmpty(projectKey) && !base.contains("/")) {
      return base + "/" + projectKey;
    }
    return base;
  }

  static String sanitizePathSegment(String value) {
    if (Utils.isEmpty(value)) {
      return "unknown";
    }
    return value.replaceAll("[\\\\/\\s]+", "-");
  }

  static String fileNameForEvent(ObjectNode event) {
    String jobName =
        event.path("job").path("name").asText("event").replace('/', '_').replace('\\', '_');
    return jobName + ".json";
  }

  private static ObjectNode datasetNode(DatasetKey key) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("namespace", key.namespace());
    node.put("name", key.name());
    return node;
  }

  private static ObjectNode schemaFacet(TableLineage table) {
    ObjectNode facet = MAPPER.createObjectNode();
    facet.put("_producer", OpenLineageConstants.PRODUCER);
    facet.put("_schemaURL", OpenLineageConstants.SCHEMA_FACET_URL);
    ArrayNode fields = MAPPER.createArrayNode();
    for (FieldLineage field : table.getFields()) {
      if (field == null || Utils.isEmpty(field.getTargetFieldName())) {
        continue;
      }
      ObjectNode f = MAPPER.createObjectNode();
      f.put("name", field.getTargetFieldName());
      if (!Utils.isEmpty(field.getDataType())) {
        f.put("type", field.getDataType());
      }
      fields.add(f);
    }
    facet.set("fields", fields);
    return facet;
  }

  private static ObjectNode schemaFacetFromNames(Map<String, String> fieldTypes) {
    ObjectNode facet = MAPPER.createObjectNode();
    facet.put("_producer", OpenLineageConstants.PRODUCER);
    facet.put("_schemaURL", OpenLineageConstants.SCHEMA_FACET_URL);
    ArrayNode fields = MAPPER.createArrayNode();
    for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
      if (Utils.isEmpty(e.getKey())) {
        continue;
      }
      ObjectNode f = MAPPER.createObjectNode();
      f.put("name", e.getKey());
      if (!Utils.isEmpty(e.getValue())) {
        f.put("type", e.getValue());
      }
      fields.add(f);
    }
    facet.set("fields", fields);
    return facet;
  }

  private static ObjectNode columnLineageFacet(TableLineage table, String datasetNamespace) {
    ObjectNode facet = MAPPER.createObjectNode();
    facet.put("_producer", OpenLineageConstants.PRODUCER);
    facet.put("_schemaURL", OpenLineageConstants.COLUMN_LINEAGE_FACET_URL);
    ObjectNode fields = MAPPER.createObjectNode();
    for (FieldLineage field : table.getFields()) {
      if (field == null || Utils.isEmpty(field.getTargetFieldName())) {
        continue;
      }
      ArrayNode inputFields = MAPPER.createArrayNode();
      Set<String> seen = new LinkedHashSet<>();
      for (FieldContribution contribution : field.getContributions()) {
        if (contribution == null) {
          continue;
        }
        DatasetKey key =
            applyDatasetNamespace(datasetKeyFromContribution(contribution, table), datasetNamespace);
        if (key == null || Utils.isEmpty(contribution.getSourceFieldName())) {
          continue;
        }
        String dedupe = key.id() + "#" + contribution.getSourceFieldName();
        if (!seen.add(dedupe)) {
          continue;
        }
        ObjectNode inputField = MAPPER.createObjectNode();
        inputField.put("namespace", key.namespace());
        inputField.put("name", key.name());
        inputField.put("field", contribution.getSourceFieldName());
        inputField.set("transformations", transformations(contribution));
        inputFields.add(inputField);
      }
      ObjectNode fieldNode = MAPPER.createObjectNode();
      fieldNode.set("inputFields", inputFields);
      fields.set(field.getTargetFieldName(), fieldNode);
    }
    facet.set("fields", fields);
    facet.set("dataset", MAPPER.createArrayNode());
    return facet;
  }

  /**
   * When {@code datasetNamespace} is set, force every dataset into that OpenLineage namespace
   * (dataset <em>name</em> is unchanged). Empty/null keeps connection/catalog/staging namespaces.
   */
  static DatasetKey applyDatasetNamespace(DatasetKey key, String datasetNamespace) {
    if (key == null) {
      return null;
    }
    if (Utils.isEmpty(datasetNamespace)) {
      return key;
    }
    return new DatasetKey(datasetNamespace.trim(), key.name());
  }

  private static ArrayNode transformations(FieldContribution contribution) {
    ArrayNode array = MAPPER.createArrayNode();
    ObjectNode t = MAPPER.createObjectNode();
    FieldTransform transform =
        contribution.getTransform() != null ? contribution.getTransform() : FieldTransform.IDENTITY;
    switch (transform) {
      case IDENTITY -> {
        t.put("type", "DIRECT");
        t.put("subtype", "IDENTITY");
        t.put("masking", false);
      }
      case RENAME -> {
        t.put("type", "DIRECT");
        t.put("subtype", "IDENTITY");
        t.put("masking", false);
      }
      case HASH_INPUT -> {
        t.put("type", "DIRECT");
        t.put("subtype", "TRANSFORMATION");
        t.put("masking", true);
      }
      case CONSTANT -> {
        t.put("type", "DIRECT");
        t.put("subtype", "TRANSFORMATION");
        t.put("masking", false);
      }
      case DERIVED, NONE -> {
        t.put("type", "DIRECT");
        t.put("subtype", "TRANSFORMATION");
        t.put("masking", false);
      }
    }
    t.put("description", reasonDescription(contribution));
    array.add(t);
    return array;
  }

  private static String reasonDescription(FieldContribution contribution) {
    List<String> parts = new ArrayList<>();
    if (contribution.getTransform() != null) {
      parts.add(contribution.getTransform().name());
    }
    for (LineageReason reason : contribution.getReasons()) {
      if (reason == null) {
        continue;
      }
      if (reason.getCode() != null) {
        parts.add(reason.getCode().name());
      }
      if (!Utils.isEmpty(reason.getMessage()) && parts.size() < 4) {
        parts.add(reason.getMessage());
      }
    }
    return String.join("; ", parts);
  }

  private static DatasetKey outputDatasetKey(TableLineage table) {
    String namespace = resolveOutputNamespace(table);
    String name =
        physicalName(table.getSchemaName(), table.getPhysicalTableName(), table.getLogicalName());
    return new DatasetKey(namespace, name);
  }

  /**
   * Prefer Hop target connection name; fall back to layer-qualified names so BV/DM targets are
   * stable datasets even when configuration omits a connection.
   */
  private static String resolveOutputNamespace(TableLineage table) {
    if (!Utils.isEmpty(table.getTargetDatabaseMetaName())) {
      return table.getTargetDatabaseMetaName();
    }
    LineageLayer layer = table.getLayer() != null ? table.getLayer() : LineageLayer.DV;
    return switch (layer) {
      case BV -> "business-vault";
      case DM -> "dimensional";
      case CROSS -> "cross";
      default -> "data-vault";
    };
  }

  private static DatasetKey datasetKeyFromTableSource(TableSourceRef source, TableLineage table) {
    if (source == null || source.getKind() == null) {
      return null;
    }
    return switch (source.getKind()) {
      case DV_SOURCE -> {
        String ns =
            !Utils.isEmpty(source.getCatalogKey())
                ? namespaceFromCatalogKey(source.getCatalogKey())
                : "catalog";
        String name =
            !Utils.isEmpty(source.getPhysicalRef()) ? source.getPhysicalRef() : source.getName();
        if (Utils.isEmpty(name)) {
          yield null;
        }
        yield new DatasetKey(ns, name);
      }
      case DV_TABLE, BV_TABLE, DM_TABLE -> {
        // Prefer the target connection of the consuming table so parent vault tables align with
        // their own export events when connections match; otherwise use a stable layer namespace.
        String ns = resolveParentTableNamespace(source.getKind(), table);
        String name =
            !Utils.isEmpty(source.getPhysicalRef()) ? source.getPhysicalRef() : source.getName();
        if (Utils.isEmpty(name)) {
          yield null;
        }
        yield new DatasetKey(ns, name);
      }
      case CONFIG -> {
        // Staging SQL / technical config sources: still register a dataset so lineage is visible.
        if (Utils.isEmpty(source.getName())) {
          yield null;
        }
        yield new DatasetKey("staging", source.getName());
      }
    };
  }

  private static DatasetKey datasetKeyFromContribution(
      FieldContribution contribution, TableLineage table) {
    if (contribution == null || contribution.getSourceKind() == null) {
      return null;
    }
    TableSourceKind kind = contribution.getSourceKind();
    if (kind == TableSourceKind.CONFIG) {
      if (Utils.isEmpty(contribution.getSourceName())) {
        return null;
      }
      // Skip pure technical config noise (standard columns from DataVaultConfiguration).
      if ("DataVaultConfiguration".equals(contribution.getSourceName())
          || "model".equals(contribution.getSourceName())) {
        return null;
      }
      return new DatasetKey("staging", contribution.getSourceName());
    }
    if (kind == TableSourceKind.DV_SOURCE) {
      String ns =
          !Utils.isEmpty(contribution.getSourceCatalogKey())
              ? namespaceFromCatalogKey(contribution.getSourceCatalogKey())
              : "catalog";
      String name = contribution.getSourceName();
      if (Utils.isEmpty(name)) {
        return null;
      }
      return new DatasetKey(ns, name);
    }
    String ns = resolveParentTableNamespace(kind, table);
    String name = contribution.getSourceName();
    if (Utils.isEmpty(name)) {
      return null;
    }
    return new DatasetKey(ns, name);
  }

  private static String resolveParentTableNamespace(TableSourceKind kind, TableLineage table) {
    if (!Utils.isEmpty(table.getTargetDatabaseMetaName())) {
      // When BV/DM share the same EDW connection as DV, parent tables match output namespaces.
      return table.getTargetDatabaseMetaName();
    }
    if (kind == null) {
      return "data-vault";
    }
    return switch (kind) {
      case BV_TABLE -> "business-vault";
      case DM_TABLE -> "dimensional";
      case DV_TABLE -> "data-vault";
      default -> kind.name().toLowerCase();
    };
  }

  private static String namespaceFromCatalogKey(String catalogKey) {
    int slash = catalogKey.indexOf('/');
    if (slash > 0) {
      return catalogKey.substring(0, slash);
    }
    return "catalog";
  }

  private static String physicalName(String schema, String physical, String logical) {
    String table = !Utils.isEmpty(physical) ? physical : logical;
    if (Utils.isEmpty(table)) {
      return "unknown";
    }
    if (!Utils.isEmpty(schema)) {
      return schema + "." + table;
    }
    return table;
  }

  private static String datasetId(String namespace, String name) {
    return (namespace != null ? namespace : "") + "\0" + (name != null ? name : "");
  }

  private record DatasetKey(String namespace, String name) {
    String id() {
      return namespace + "\0" + name;
    }
  }
}
