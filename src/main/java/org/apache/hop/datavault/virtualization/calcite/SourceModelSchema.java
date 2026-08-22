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
package org.apache.hop.datavault.virtualization.calcite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/**
 * Calcite schema exposing source model cards as SQL tables (logical canvas names).
 *
 * <p>Includes DATABASE {@link SourceTable}s, named {@link SourceQuery}s (as virtual tables), {@link
 * SourceJson} extractions, and {@link SourcePipeline} feeds.
 */
public class SourceModelSchema extends AbstractSchema {

  private final SourceModel model;
  private final Map<String, Table> tableMap;

  public SourceModelSchema(SourceModel model) {
    this.model = model;
    this.tableMap = buildTableMap(model);
  }

  public SourceModel getModel() {
    return model;
  }

  @Override
  protected Map<String, Table> getTableMap() {
    return tableMap;
  }

  public Table findTable(String name) {
    if (Utils.isEmpty(name)) {
      return null;
    }
    Table table = tableMap.get(name);
    if (table != null) {
      return table;
    }
    String upper = name.toUpperCase(Locale.ROOT);
    for (Map.Entry<String, Table> entry : tableMap.entrySet()) {
      if (entry.getKey() != null && entry.getKey().toUpperCase(Locale.ROOT).equals(upper)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static Map<String, Table> buildTableMap(SourceModel model) {
    Map<String, Table> map = new LinkedHashMap<>();
    if (model == null) {
      return Collections.unmodifiableMap(map);
    }
    for (SourceTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      register(map, table.getName(), new SourceModelTable(table));
    }
    // Named source queries as virtual tables (FROM feed_customer_enriched).
    for (SourceQuery query : model.getQueries()) {
      if (query == null || Utils.isEmpty(query.getName())) {
        continue;
      }
      SourceModelQueryTable queryTable = new SourceModelQueryTable(query);
      register(map, query.getName(), queryTable);
      if (!Utils.isEmpty(query.getPublishedCatalogName())
          && !query.getPublishedCatalogName().trim().equalsIgnoreCase(query.getName().trim())) {
        register(map, query.getPublishedCatalogName().trim(), queryTable);
      }
    }
    for (SourceJson json : model.getJsonSources()) {
      if (json == null || Utils.isEmpty(json.getName())) {
        continue;
      }
      register(map, json.getName(), new SourceModelJsonTable(json));
    }
    for (SourcePipeline pipeline : model.getPipelineSources()) {
      if (pipeline == null || Utils.isEmpty(pipeline.getName())) {
        continue;
      }
      register(map, pipeline.getName(), new SourceModelPipelineTable(pipeline));
    }
    return Collections.unmodifiableMap(map);
  }

  private static void register(Map<String, Table> map, String name, Table table) {
    map.put(name, table);
    map.putIfAbsent(name.toLowerCase(Locale.ROOT), table);
    map.putIfAbsent(name.toUpperCase(Locale.ROOT), table);
  }
}
