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
package org.apache.hop.datavault.dbt;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.businessvault.BvSqlColumnNote;

/** Merges dbt v2 {@code models:} / {@code sources:} YAML into a lookup by model name. */
public final class DbtSchemaYamlParser {

  private DbtSchemaYamlParser() {}

  public static final class SchemaBundle {
    public final Map<String, DbtModelDraft> modelsByName = new LinkedHashMap<>();
    public final java.util.List<DbtSourceTable> sources = new java.util.ArrayList<>();
    public final java.util.List<DbtImportIssue> issues = new java.util.ArrayList<>();
  }

  public static void mergeFile(SchemaBundle bundle, String yamlText, String origin)
      throws HopException {
    Map<String, Object> root = DbtYamlMaps.parseYaml(yamlText, origin);
    mergeModels(bundle, DbtYamlMaps.asList(root.get("models")));
    mergeSources(bundle, DbtYamlMaps.asList(root.get("sources")));
  }

  private static void mergeModels(SchemaBundle bundle, java.util.List<Object> models) {
    for (Object item : models) {
      Map<String, Object> map = DbtYamlMaps.asMap(item);
      String name = DbtYamlMaps.childString(map, "name");
      if (Utils.isEmpty(name)) {
        continue;
      }
      DbtModelDraft draft =
          bundle.modelsByName.computeIfAbsent(
              name.toLowerCase(Locale.ROOT),
              key -> {
                DbtModelDraft created = new DbtModelDraft();
                created.setName(name);
                return created;
              });
      if (Utils.isEmpty(draft.getName())) {
        draft.setName(name);
      }
      String description = DbtYamlMaps.childString(map, "description");
      if (!Utils.isEmpty(description)) {
        draft.setDescription(description);
      }
      Map<String, Object> config = DbtYamlMaps.childMap(map, "config");
      applyConfig(draft, config);
      applyConfig(draft, map);
      for (Object columnObj : DbtYamlMaps.asList(map.get("columns"))) {
        Map<String, Object> column = DbtYamlMaps.asMap(columnObj);
        String colName = DbtYamlMaps.childString(column, "name");
        if (Utils.isEmpty(colName)) {
          continue;
        }
        draft
            .getColumnNotes()
            .add(new BvSqlColumnNote(colName, DbtYamlMaps.childString(column, "description")));
      }
    }
  }

  private static void applyConfig(DbtModelDraft draft, Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      return;
    }
    String materialized = first(config, "materialized", "+materialized");
    if (!Utils.isEmpty(materialized) && Utils.isEmpty(draft.getDbtMaterialized())) {
      draft.setDbtMaterialized(materialized);
    }
    String schema = first(config, "schema", "+schema");
    if (!Utils.isEmpty(schema) && Utils.isEmpty(draft.getSchemaName())) {
      draft.setSchemaName(schema);
    }
    String alias = first(config, "alias", "+alias");
    if (!Utils.isEmpty(alias) && Utils.isEmpty(draft.getTableName())) {
      draft.setTableName(alias);
    }
  }

  private static void mergeSources(SchemaBundle bundle, java.util.List<Object> sources) {
    for (Object item : sources) {
      Map<String, Object> source = DbtYamlMaps.asMap(item);
      String sourceName = DbtYamlMaps.childString(source, "name");
      if (Utils.isEmpty(sourceName)) {
        continue;
      }
      String schema = DbtYamlMaps.childString(source, "schema");
      String database = DbtYamlMaps.childString(source, "database");
      for (Object tableObj : DbtYamlMaps.asList(source.get("tables"))) {
        Map<String, Object> table = DbtYamlMaps.asMap(tableObj);
        String tableName = DbtYamlMaps.childString(table, "name");
        if (Utils.isEmpty(tableName)) {
          continue;
        }
        DbtSourceTable row = new DbtSourceTable();
        row.setSourceName(sourceName);
        row.setTableName(tableName);
        row.setSchemaName(schema);
        row.setDatabaseName(database);
        String description = DbtYamlMaps.childString(table, "description");
        if (Utils.isEmpty(description)) {
          description = DbtYamlMaps.childString(source, "description");
        }
        row.setDescription(description);
        bundle.sources.add(row);
      }
    }
  }

  private static String first(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      String value = DbtYamlMaps.childString(map, key);
      if (!Utils.isEmpty(value)) {
        return value;
      }
    }
    return null;
  }
}
