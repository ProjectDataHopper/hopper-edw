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
package org.apache.hop.datavault.lineage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.model.CatalogCustomProperty;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Loads a baseline {@link LineageSnapshot} from previously published lineage sibling catalog
 * records.
 */
public final class LineageCatalogBaselineLoader {

  private static final ObjectMapper JSON = new ObjectMapper();

  private LineageCatalogBaselineLoader() {}

  /**
   * @return reconstructed snapshot, or {@code null} when no lineage records exist for the model
   */
  public static LineageSnapshot load(
      String catalogConnectionName,
      LineageLayer layer,
      String modelBasename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName) || Utils.isEmpty(modelBasename)) {
      return null;
    }
    String namespace =
        LineageCatalogNamespaces.projectLineageNamespace(variables, layer, modelBasename);
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNamespacePrefix(namespace);
    query.getTags().add(LineageCatalogNamespaces.TAG_LINEAGE);

    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    List<RecordDefinitionRef> refs =
        registry.list(catalogConnectionName, query, variables, metadataProvider);
    if (refs == null || refs.isEmpty()) {
      return null;
    }

    LineageSnapshot snapshot = new LineageSnapshot();
    snapshot.setModelLayer(layer);
    snapshot.setModelName(modelBasename);
    snapshot.setProjectKey(
        org.apache.hop.datavault.catalog.DvCatalogNamespaces.resolveProjectKey(variables));
    snapshot.setCatalogConnection(catalogConnectionName);

    for (RecordDefinitionRef ref : refs) {
      if (ref == null || ref.getKey() == null || Utils.isEmpty(ref.getKey().getName())) {
        continue;
      }
      if (LineageCatalogNamespaces.SNAPSHOT_RECORD_NAME.equals(ref.getKey().getName())) {
        continue;
      }
      RecordDefinition definition =
          registry.read(catalogConnectionName, ref.getKey(), variables, metadataProvider);
      if (definition == null) {
        continue;
      }
      TableLineage table = fromDefinition(definition);
      if (table != null) {
        snapshot.addTable(table);
      }
    }
    return snapshot.getTables().isEmpty() ? null : snapshot;
  }

  static TableLineage fromDefinition(RecordDefinition definition) throws HopException {
    if (definition == null || definition.getKey() == null) {
      return null;
    }
    TableLineage table = new TableLineage();
    table.setLogicalName(definition.getKey().getName());
    table.setDescription(definition.getDescription());
    Map<String, CatalogCustomProperty> props = definition.getCustomProperties();
    if (props != null) {
      table.setPhysicalTableName(prop(props, LineageCatalogNamespaces.PROP_PHYSICAL_TABLE));
      table.setTableType(prop(props, LineageCatalogNamespaces.PROP_TABLE_TYPE));
      String layer = prop(props, LineageCatalogNamespaces.PROP_LAYER);
      if (!Utils.isEmpty(layer)) {
        try {
          table.setLayer(LineageLayer.valueOf(layer));
        } catch (IllegalArgumentException ignored) {
          table.setLayer(LineageLayer.DV);
        }
      }
      try {
        parseReasons(prop(props, LineageCatalogNamespaces.PROP_TABLE_REASONS_JSON), table);
        parseSources(prop(props, LineageCatalogNamespaces.PROP_SOURCES_JSON), table);
        parseFields(prop(props, LineageCatalogNamespaces.PROP_FIELDS_JSON), table);
      } catch (Exception e) {
        throw new HopException("Failed to parse lineage catalog record " + definition.getKey(), e);
      }
    }
    if (Utils.isEmpty(table.getPhysicalTableName())) {
      table.setPhysicalTableName(table.getLogicalName());
    }
    return table;
  }

  private static void parseReasons(String json, TableLineage table) throws Exception {
    if (Utils.isEmpty(json)) {
      return;
    }
    List<Map<String, Object>> list =
        JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    for (Map<String, Object> map : list) {
      LineageReasonCode code = parseEnum(LineageReasonCode.class, str(map.get("code")));
      LineageConfidence confidence = parseEnum(LineageConfidence.class, str(map.get("confidence")));
      if (code == null) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, String> evidence =
          map.get("evidence") instanceof Map ? (Map<String, String>) map.get("evidence") : Map.of();
      table.addReason(new LineageReason(code, str(map.get("message")), confidence, evidence));
    }
  }

  private static void parseSources(String json, TableLineage table) throws Exception {
    if (Utils.isEmpty(json)) {
      return;
    }
    List<Map<String, Object>> list =
        JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    for (Map<String, Object> map : list) {
      TableSourceRef ref = new TableSourceRef();
      ref.setKind(parseEnum(TableSourceKind.class, str(map.get("kind"))));
      ref.setName(str(map.get("name")));
      ref.setCatalogKey(str(map.get("catalogKey")));
      ref.setPhysicalRef(str(map.get("physicalRef")));
      TableSourceRole role = parseEnum(TableSourceRole.class, str(map.get("role")));
      if (role != null) {
        ref.setRole(role);
      }
      table.addSource(ref);
    }
  }

  private static void parseFields(String json, TableLineage table) throws Exception {
    if (Utils.isEmpty(json)) {
      return;
    }
    List<Map<String, Object>> list =
        JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    for (Map<String, Object> map : list) {
      FieldLineage field = new FieldLineage(str(map.get("targetFieldName")));
      field.setDataType(str(map.get("dataType")));
      field.setLength(str(map.get("length")));
      field.setPrecision(str(map.get("precision")));
      field.setTechnical(
          Boolean.TRUE.equals(map.get("technical"))
              || "true".equalsIgnoreCase(str(map.get("technical"))));
      Object contribObj = map.get("contributions");
      if (contribObj instanceof List<?> contribList) {
        for (Object item : contribList) {
          if (!(item instanceof Map<?, ?> raw)) {
            continue;
          }
          @SuppressWarnings("unchecked")
          Map<String, Object> c = (Map<String, Object>) raw;
          FieldContribution contribution = new FieldContribution();
          contribution.setSourceKind(parseEnum(TableSourceKind.class, str(c.get("sourceKind"))));
          contribution.setSourceName(str(c.get("sourceName")));
          contribution.setSourceCatalogKey(str(c.get("sourceCatalogKey")));
          contribution.setSourceFieldName(str(c.get("sourceFieldName")));
          FieldTransform transform = parseEnum(FieldTransform.class, str(c.get("transform")));
          if (transform != null) {
            contribution.setTransform(transform);
          }
          Object reasonsObj = c.get("reasons");
          if (reasonsObj instanceof List<?> reasonList) {
            for (Object rItem : reasonList) {
              if (!(rItem instanceof Map<?, ?> rRaw)) {
                continue;
              }
              @SuppressWarnings("unchecked")
              Map<String, Object> r = (Map<String, Object>) rRaw;
              LineageReasonCode code = parseEnum(LineageReasonCode.class, str(r.get("code")));
              if (code == null) {
                continue;
              }
              LineageConfidence confidence =
                  parseEnum(LineageConfidence.class, str(r.get("confidence")));
              @SuppressWarnings("unchecked")
              Map<String, String> evidence =
                  r.get("evidence") instanceof Map
                      ? (Map<String, String>) r.get("evidence")
                      : Map.of();
              contribution.addReason(
                  new LineageReason(code, str(r.get("message")), confidence, evidence));
            }
          }
          field.addContribution(contribution);
        }
      }
      table.addField(field);
    }
  }

  private static String prop(Map<String, CatalogCustomProperty> props, String key) {
    CatalogCustomProperty property = props.get(key);
    return property != null ? property.getValue() : null;
  }

  private static String str(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
    if (Utils.isEmpty(name)) {
      return null;
    }
    try {
      return Enum.valueOf(type, name);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
