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
package org.hopper.edw.datavault.opsanalytics;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;

/** Builds the operational analytics {@code .hsm} from {@link OpsAnalyticsCatalog}. */
public final class OpsAnalyticsSourceModelBuilder {

  public static final String MODEL_NAME = "ops-analytics";

  private OpsAnalyticsSourceModelBuilder() {}

  public static SourceModel build() {
    return build(OpsAnalyticsRequest.defaults());
  }

  public static SourceModel build(OpsAnalyticsRequest request) {
    OpsAnalyticsRequest effective = request != null ? request : OpsAnalyticsRequest.defaults();
    SourceModel model = new SourceModel();
    model.setName(MODEL_NAME);
    model.setDescription(
        "Operational analytics source model. Tables are the EDW OPS schema; "
            + "relationships are logical and are not created on the database.");

    int index = 0;
    for (OpsAnalyticsTable table : OpsAnalyticsCatalog.tables(effective.isIncludeExecutions())) {
      model.getTables().add(toTable(table, effective, index++));
    }
    for (OpsAnalyticsQuery query : OpsAnalyticsCatalog.queries()) {
      model.getQueries().add(toQuery(query, effective, index++));
    }
    int relationshipIndex = 1;
    for (OpsAnalyticsRelationship relationship :
        OpsAnalyticsCatalog.relationships(effective.isIncludeExecutions())) {
      model.getRelationships().add(toRelationship(relationship, relationshipIndex++));
    }
    return model;
  }

  private static SourceTable toTable(
      OpsAnalyticsTable source, OpsAnalyticsRequest request, int index) {
    SourceTable table = new SourceTable(source.name());
    table.setDescription(source.physicalTable());
    table.setCatalogSourceName(source.physicalTable());
    table.setDatabaseName(request.getConnectionName());
    table.setSchemaName(source.executionInfo() ? request.getExecutionSchema() : request.getSchemaName());
    table.setTableName(source.physicalTable());
    table.setLocation(location(index));
    for (OpsAnalyticsColumn column : source.columns()) {
      SourceColumn mapped = new SourceColumn(column.name());
      mapped.setHopType(column.hopType());
      if (column.length() > 0) {
        mapped.setLength(Integer.toString(column.length()));
      }
      mapped.setPrimaryKeyPosition(column.primaryKeyPosition());
      table.getColumns().add(mapped);
    }
    return table;
  }

  private static SourceQuery toQuery(
      OpsAnalyticsQuery source, OpsAnalyticsRequest request, int index) {
    SourceQuery query = new SourceQuery(source.name());
    query.setDescription("Conformed business key " + source.name());
    query.setGenerationMode(SourceQueryGenerationMode.FREE_SQL);
    query.setFreeSql(unionSql(source, request));
    query.setPublishedCatalogName(source.name());
    query.setDrivingTableName(source.branches().get(0).physicalTable());
    query.setLocation(location(index));
    for (OpsAnalyticsColumn key : source.keys()) {
      SourceQueryColumn column =
          new SourceQueryColumn(source.branches().get(0).physicalTable(), key.name(), key.name());
      column.setPrimaryKeyPosition(key.primaryKeyPosition());
      query.getColumns().add(column);
    }
    return query;
  }

  static String unionSql(OpsAnalyticsQuery query, OpsAnalyticsRequest request) {
    StringBuilder sql = new StringBuilder();
    List<String> keyNames = new ArrayList<>();
    for (OpsAnalyticsColumn key : query.keys()) {
      keyNames.add(key.name());
    }
    for (int i = 0; i < query.branches().size(); i++) {
      OpsAnalyticsQuery.Branch branch = query.branches().get(i);
      if (i > 0) {
        sql.append('\n').append("UNION").append('\n');
      }
      sql.append("SELECT DISTINCT ");
      for (int c = 0; c < keyNames.size(); c++) {
        if (c > 0) {
          sql.append(", ");
        }
        String physical = branch.columns().get(c);
        String alias = keyNames.get(c);
        sql.append(quote(request, physical));
        if (!physical.equals(alias)) {
          sql.append(" AS ").append(quote(request, alias));
        }
      }
      sql.append(" FROM ").append(qualifiedTable(request, branch.physicalTable()));
      sql.append(" WHERE ");
      for (int c = 0; c < branch.columns().size(); c++) {
        if (c > 0) {
          sql.append(" AND ");
        }
        sql.append(quote(request, branch.columns().get(c))).append(" IS NOT NULL");
      }
    }
    return sql.toString();
  }

  private static String qualifiedTable(OpsAnalyticsRequest request, String table) {
    DatabaseMeta databaseMeta = request.getDatabaseMeta();
    IVariables variables =
        request.getVariables() != null ? request.getVariables() : new Variables();
    String schema = request.getSchemaName();
    if (databaseMeta != null) {
      return databaseMeta.getQuotedSchemaTableCombination(variables, schema, table);
    }
    if (Utils.isEmpty(schema)) {
      return quote(request, table);
    }
    return quote(request, schema) + "." + quote(request, table);
  }

  private static String quote(OpsAnalyticsRequest request, String identifier) {
    if (request.getDatabaseMeta() != null) {
      return request.getDatabaseMeta().quoteField(identifier);
    }
    return "\"" + identifier + "\"";
  }

  private static SourceRelationship toRelationship(OpsAnalyticsRelationship source, int index) {
    SourceRelationship relationship = new SourceRelationship("ops_fk_" + index);
    relationship.setChildEndpointKind(source.childKind());
    relationship.setParentEndpointKind(source.parentKind());
    relationship.setChildTableName(source.childName());
    relationship.setParentTableName(source.parentName());
    relationship.getChildColumns().addAll(source.childColumns());
    relationship.getParentColumns().addAll(source.parentColumns());
    return relationship;
  }

  private static Point location(int index) {
    int column = index % 4;
    int row = index / 4;
    return new Point(40 + column * 260, 40 + row * 180);
  }
}
