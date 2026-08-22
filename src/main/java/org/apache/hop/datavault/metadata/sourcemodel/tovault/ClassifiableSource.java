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
package org.apache.hop.datavault.metadata.sourcemodel.tovault;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/** Unified view of a table, query, JSON, or pipeline card for source-to-vault classification. */
@Getter
public final class ClassifiableSource {

  private final SourceEndpointKind kind;
  private final String name;
  private final String catalogSourceName;
  private final List<String> primaryKeyNames;
  private final List<String> columnNames;
  private final List<SourceColumn> columns;

  private ClassifiableSource(
      SourceEndpointKind kind,
      String name,
      String catalogSourceName,
      List<String> primaryKeyNames,
      List<String> columnNames,
      List<SourceColumn> columns) {
    this.kind = kind;
    this.name = name;
    this.catalogSourceName = catalogSourceName;
    this.primaryKeyNames = primaryKeyNames;
    this.columnNames = columnNames;
    this.columns = columns;
  }

  public static ClassifiableSource fromTable(SourceTable table) {
    if (table == null || Utils.isEmpty(table.getName())) {
      return null;
    }
    List<SourceColumn> cols = new ArrayList<>(table.getColumns());
    return new ClassifiableSource(
        SourceEndpointKind.TABLE,
        table.getName(),
        !Utils.isEmpty(table.getCatalogSourceName())
            ? table.getCatalogSourceName()
            : table.getName(),
        SourceToVaultClassifier.pkNames(table),
        namesOf(cols),
        cols);
  }

  public static ClassifiableSource fromQuery(SourceQuery query) {
    if (query == null || Utils.isEmpty(query.getName())) {
      return null;
    }
    List<SourceColumn> cols = new ArrayList<>();
    List<String> pks = new ArrayList<>();
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null) {
        continue;
      }
      String alias = column.resolveAlias();
      if (Utils.isEmpty(alias)) {
        continue;
      }
      SourceColumn mapped = new SourceColumn(alias);
      mapped.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      cols.add(mapped);
      if (column.isPrimaryKey()) {
        pks.add(alias);
      }
    }
    String catalog =
        !Utils.isEmpty(query.getPublishedCatalogName())
            ? query.getPublishedCatalogName()
            : query.getName();
    return new ClassifiableSource(
        SourceEndpointKind.QUERY, query.getName(), catalog, pks, namesOf(cols), cols);
  }

  public static ClassifiableSource fromJson(SourceJson json) {
    if (json == null || Utils.isEmpty(json.getName())) {
      return null;
    }
    List<SourceColumn> cols = new ArrayList<>();
    List<String> pks = new ArrayList<>();
    for (SourceJsonField field : json.getFields()) {
      if (field == null) {
        continue;
      }
      String fieldName = field.resolveName();
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      SourceColumn mapped = new SourceColumn(fieldName);
      mapped.setHopType(field.getHopType());
      if (field.getLength() > 0) {
        mapped.setLength(Integer.toString(field.getLength()));
      }
      if (field.getPrecision() >= 0) {
        mapped.setPrecision(Integer.toString(field.getPrecision()));
      }
      mapped.setPrimaryKeyPosition(field.getPrimaryKeyPosition());
      cols.add(mapped);
      if (field.isPrimaryKey()) {
        pks.add(fieldName);
      }
    }
    String catalog =
        !Utils.isEmpty(json.getPublishedCatalogName())
            ? json.getPublishedCatalogName()
            : json.getName();
    return new ClassifiableSource(
        SourceEndpointKind.JSON, json.getName(), catalog, pks, namesOf(cols), cols);
  }

  public static ClassifiableSource fromPipeline(SourcePipeline pipeline) {
    if (pipeline == null || Utils.isEmpty(pipeline.getName())) {
      return null;
    }
    List<SourceColumn> cols = new ArrayList<>(pipeline.getFields());
    List<String> pks = new ArrayList<>();
    for (SourceColumn field : pipeline.primaryKeyFields()) {
      if (field != null && !Utils.isEmpty(field.getName())) {
        pks.add(field.getName());
      }
    }
    return new ClassifiableSource(
        SourceEndpointKind.PIPELINE,
        pipeline.getName(),
        pipeline.resolveCatalogSourceName(),
        pks,
        namesOf(cols),
        cols);
  }

  public static ClassifiableSource of(SourceModel model, SourceEndpointKind kind, String name) {
    if (model == null || Utils.isEmpty(name)) {
      return null;
    }
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    return switch (resolved) {
      case TABLE -> fromTable(model.findTable(name));
      case QUERY -> fromQuery(model.findQuery(name));
      case JSON -> fromJson(model.findJsonSource(name));
      case PIPELINE -> fromPipeline(model.findPipelineSource(name));
    };
  }

  public static List<ClassifiableSource> allIn(SourceModel model) {
    List<ClassifiableSource> sources = new ArrayList<>();
    if (model == null) {
      return sources;
    }
    for (SourceTable table : model.getTables()) {
      ClassifiableSource source = fromTable(table);
      if (source != null) {
        sources.add(source);
      }
    }
    for (SourceQuery query : model.getQueries()) {
      ClassifiableSource source = fromQuery(query);
      if (source != null && !source.columnNames.isEmpty()) {
        sources.add(source);
      }
    }
    for (SourceJson json : model.getJsonSources()) {
      ClassifiableSource source = fromJson(json);
      if (source != null && !source.columnNames.isEmpty()) {
        sources.add(source);
      }
    }
    for (SourcePipeline pipeline : model.getPipelineSources()) {
      ClassifiableSource source = fromPipeline(pipeline);
      if (source != null && !source.columnNames.isEmpty()) {
        sources.add(source);
      }
    }
    return sources;
  }

  public SourceColumn findColumn(String columnName) {
    if (Utils.isEmpty(columnName)) {
      return null;
    }
    for (SourceColumn column : columns) {
      if (column != null && columnName.equalsIgnoreCase(column.getName())) {
        return column;
      }
    }
    return null;
  }

  public boolean isTable() {
    return kind == SourceEndpointKind.TABLE;
  }

  public String key() {
    return SourceEndpointSupport.displayName(kind, name);
  }

  private static List<String> namesOf(List<SourceColumn> cols) {
    List<String> names = new ArrayList<>();
    for (SourceColumn column : cols) {
      if (column != null && !Utils.isEmpty(column.getName())) {
        names.add(column.getName());
      }
    }
    return names;
  }
}
