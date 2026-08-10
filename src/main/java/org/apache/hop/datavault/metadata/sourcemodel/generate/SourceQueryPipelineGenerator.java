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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.virtualization.sql.SourceModelSqlEngine;
import org.apache.hop.datavault.virtualization.sql.SourceModelSqlOptions;
import org.apache.hop.datavault.virtualization.sql.SourceModelSqlPlan;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.mergejoin.MergeJoinMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;

/**
 * Builds a Hop pipeline that materialises a {@link SourceQuery}.
 *
 * <ul>
 *   <li>SQL mode (same connection): single {@code TableInput} with generated SQL.
 *   <li>Pipeline mode: per-table inputs + sort + merge-join chain + select/rename.
 * </ul>
 */
public final class SourceQueryPipelineGenerator {

  private SourceQueryPipelineGenerator() {}

  public static PipelineMeta generate(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    SourceQueryGenerationMode mode =
        SourceQueryGenerationSupport.resolveEffectiveMode(model, query);
    if (mode == SourceQueryGenerationMode.FREE_SQL) {
      return generateFreeSql(model, query, variables, metadataProvider);
    }
    if (mode == SourceQueryGenerationMode.SQL) {
      return generateSqlTableInput(model, query, variables, metadataProvider);
    }
    return generateMergeJoinPipeline(model, query, variables, metadataProvider);
  }

  private static PipelineMeta generateFreeSql(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(query.getFreeSql())) {
      throw new HopException(
          "Query '" + query.getName() + "' is FREE_SQL mode but free SQL text is empty");
    }
    SourceModelSqlOptions options =
        SourceModelSqlOptions.builder()
            .pipelineName(
                "source-query-" + (Utils.isEmpty(query.getName()) ? "unnamed" : query.getName()))
            .build();
    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(model, query.getFreeSql(), variables, metadataProvider, options);
    return plan.pipelineMeta();
  }

  private static PipelineMeta generateSqlTableInput(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String connectionName = SourceQueryGenerationSupport.resolveSharedDatabaseName(model, query);
    if (Utils.isEmpty(connectionName)) {
      throw new HopException("No shared database connection for SQL generation");
    }
    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(variables.resolve(connectionName));
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' not found");
    }
    String sql = SourceQuerySqlGenerator.generate(model, query, databaseMeta, variables);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(
        "source-query-" + (Utils.isEmpty(query.getName()) ? "unnamed" : query.getName()));
    pipelineMeta.setMetadataProvider(metadataProvider);

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(databaseMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, sql);
    TransformMeta source = new TransformMeta("TableInput", "Query source", tableInputMeta);
    source.setLocation(100, 100);
    pipelineMeta.addTransform(source);
    return pipelineMeta;
  }

  private static PipelineMeta generateMergeJoinPipeline(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(
        "source-query-" + (Utils.isEmpty(query.getName()) ? "unnamed" : query.getName()));
    pipelineMeta.setMetadataProvider(metadataProvider);

    Map<String, String> aliases = SourceQuerySqlGenerator.assignAliases(model, query);
    String driving = query.getDrivingTableName().trim();
    SourceTable drivingTable = model.findTable(driving);
    if (drivingTable == null) {
      throw new HopException("Driving table '" + driving + "' not found");
    }

    int x = 100;
    int y = 100;
    TransformMeta stream =
        addDatabaseTableInput(
            pipelineMeta,
            model,
            query,
            drivingTable,
            aliases.get(driving),
            columnsForTable(query, driving),
            variables,
            metadataProvider,
            new Point(x, y));
    Set<String> inScope = new LinkedHashSet<>();
    inScope.add(driving);

    for (SourceQueryJoin join : query.getJoins()) {
      if (join == null || Utils.isEmpty(join.getTableName())) {
        continue;
      }
      String rightName = join.getTableName().trim();
      SourceTable rightTable = model.findTable(rightName);
      if (rightTable == null) {
        throw new HopException("Join table '" + rightName + "' not found");
      }
      SourceQueryJoinKeyResolver.ResolvedJoinKeys keys =
          SourceQueryJoinKeyResolver.resolve(model, join, inScope);

      x += 200;
      TransformMeta rightInput =
          addDatabaseTableInput(
              pipelineMeta,
              model,
              query,
              rightTable,
              aliases.get(rightName),
              columnsForTable(query, rightName),
              variables,
              metadataProvider,
              new Point(x, y + 150));

      // Sort both sides for Merge Join.
      List<String> leftSortKeys = qualifyKeys(keys.leftTables(), keys.leftColumns(), aliases);
      List<String> rightSortKeys = qualifyKeys(List.of(rightName), keys.rightColumns(), aliases);

      TransformMeta leftSorted = addSort(pipelineMeta, stream, leftSortKeys, new Point(x - 100, y));
      TransformMeta rightSorted =
          addSort(pipelineMeta, rightInput, rightSortKeys, new Point(x, y + 150));

      MergeJoinMeta mergeMeta = new MergeJoinMeta();
      mergeMeta.setJoinType(mergeJoinType(join.resolveJoinType().getCode()));
      mergeMeta.setKeyFields1(leftSortKeys);
      mergeMeta.setKeyFields2(rightSortKeys);
      mergeMeta.setLeftTransformName(leftSorted.getName());
      mergeMeta.setRightTransformName(rightSorted.getName());
      TransformMeta merge = new TransformMeta("MergeJoin", "Join " + rightName, mergeMeta);
      merge.setLocation(x + 100, y + 75);
      pipelineMeta.addTransform(merge);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(leftSorted, merge));
      pipelineMeta.addPipelineHop(new PipelineHopMeta(rightSorted, merge));
      stream = merge;
      inScope.add(rightName);
      x += 150;
    }

    // Final select to projected aliases only.
    TransformMeta select =
        addSelectProjection(pipelineMeta, stream, query, aliases, new Point(x + 150, y));
    return pipelineMeta;
  }

  private static TransformMeta addDatabaseTableInput(
      PipelineMeta pipelineMeta,
      SourceModel model,
      SourceQuery query,
      SourceTable table,
      String tableAlias,
      List<String> columns,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Point location)
      throws HopException {
    if (Utils.isEmpty(table.getDatabaseName())) {
      throw new HopException(
          "Table '"
              + table.getName()
              + "' has no database connection (file sources need PR later)");
    }
    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables.resolve(table.getDatabaseName()));
    if (databaseMeta == null) {
      throw new HopException("Database '" + table.getDatabaseName() + "' not found");
    }
    StringBuilder sql = new StringBuilder("SELECT ");
    if (columns.isEmpty()) {
      sql.append('*');
    } else {
      for (int i = 0; i < columns.size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        String col = columns.get(i);
        sql.append(databaseMeta.quoteField(col));
        // Prefix with table alias in output name for disambiguation before merge.
        sql.append(" AS ").append(databaseMeta.quoteField(tableAlias + "__" + col));
      }
    }
    sql.append(" FROM ")
        .append(
            databaseMeta.getQuotedSchemaTableCombination(
                variables, table.getSchemaName(), table.getTableName()));

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(databaseMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, sql.toString());
    TransformMeta transform =
        new TransformMeta("TableInput", "Read " + table.getName(), tableInputMeta);
    transform.setLocation(location.x, location.y);
    pipelineMeta.addTransform(transform);
    return transform;
  }

  private static TransformMeta addSort(
      PipelineMeta pipelineMeta, TransformMeta from, List<String> keys, Point location) {
    SortRowsMeta sortMeta = new SortRowsMeta();
    List<SortRowsField> fields = new ArrayList<>();
    for (String key : keys) {
      SortRowsField field = new SortRowsField();
      field.setFieldName(key);
      field.setAscending(true);
      field.setCaseSensitive(true);
      fields.add(field);
    }
    sortMeta.setSortFields(fields);
    TransformMeta sort = new TransformMeta("SortRows", "Sort " + from.getName(), sortMeta);
    sort.setLocation(location.x, location.y);
    pipelineMeta.addTransform(sort);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(from, sort));
    return sort;
  }

  private static TransformMeta addSelectProjection(
      PipelineMeta pipelineMeta,
      TransformMeta from,
      SourceQuery query,
      Map<String, String> aliases,
      Point location) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = new ArrayList<>();
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null || Utils.isEmpty(column.getColumnName())) {
        continue;
      }
      String tableAlias = aliases.get(column.getTableName().trim());
      String streamName = tableAlias + "__" + column.getColumnName().trim();
      SelectField field = new SelectField();
      field.setName(streamName);
      field.setRename(column.resolveAlias());
      selectFields.add(field);
    }
    selectMeta.getSelectOption().setSelectFields(selectFields);
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    TransformMeta select = new TransformMeta("SelectValues", "Project columns", selectMeta);
    select.setLocation(location.x, location.y);
    pipelineMeta.addTransform(select);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(from, select));
    return select;
  }

  private static List<String> columnsForTable(SourceQuery query, String tableName) {
    Set<String> cols = new LinkedHashSet<>();
    for (SourceQueryColumn column : query.getColumns()) {
      if (column != null
          && tableName.equals(column.getTableName())
          && !Utils.isEmpty(column.getColumnName())) {
        cols.add(column.getColumnName().trim());
      }
    }
    // Include join key columns even if not projected.
    for (SourceQueryJoin join : query.getJoins()) {
      if (join == null) {
        continue;
      }
      if (tableName.equals(join.getTableName())) {
        cols.addAll(join.getRightColumns());
      }
      for (int i = 0; i < join.getLeftTableNames().size(); i++) {
        if (tableName.equals(join.getLeftTableNames().get(i)) && i < join.getLeftColumns().size()) {
          cols.add(join.getLeftColumns().get(i));
        }
      }
    }
    return new ArrayList<>(cols);
  }

  private static List<String> qualifyKeys(
      List<String> tables, List<String> columns, Map<String, String> aliases) {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < columns.size(); i++) {
      String table = i < tables.size() ? tables.get(i) : tables.get(0);
      String alias = aliases.get(table);
      keys.add(alias + "__" + columns.get(i));
    }
    return keys;
  }

  private static String mergeJoinType(String code) {
    if (code == null) {
      return "LEFT OUTER";
    }
    return switch (code.toUpperCase()) {
      case "INNER" -> "INNER";
      case "RIGHT" -> "RIGHT OUTER";
      case "FULL" -> "FULL OUTER";
      default -> "LEFT OUTER";
    };
  }
}
