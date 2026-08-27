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
package org.hopper.edw.datavault.metadata.database;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSourceFieldMappingSupport;
import org.hopper.edw.datavault.metadata.DvSourcePipelineBuilder;
import org.hopper.edw.datavault.metadata.DvSqlOrderBySupport;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.jspecify.annotations.NonNull;

@Getter
@Setter
public abstract class DvDatabaseSourcePipelineBuilder extends DvSourcePipelineBuilder {
  protected DvDatabaseSource source;
  protected DatabaseMeta sourceDbMeta;

  protected DvDatabaseSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      IDvTable dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
    source = (DvDatabaseSource) dvSource;
  }

  @Override
  public void build() throws HopException {
    sourceDbMeta = loadDatabaseMeta(variables.resolve(source.getDatabaseName()));

    // We're mainly reading the business keys from the source database
    //
    String querySql = getSql();

    // We can generate a Table Input transform for this.
    //
    Point location = new Point(startPoint.x, startPoint.y);
    String sourceTransformName = calculateTransformName("source ", source);
    TransformMeta sourceTransformMeta =
        createTableInput(sourceTransformName, sourceDbMeta, querySql, location);
    pipelineMeta.addTransform(sourceTransformMeta);

    resultTransform = addRecordSourceFieldRenameIfNeeded(sourceTransformMeta, location);
  }

  /**
   * Some JDBC drivers (e.g. SingleStore) expose the physical column name instead of the SQL alias
   * for record source fields. Rename in-stream when source and target field names differ.
   */
  private TransformMeta addRecordSourceFieldRenameIfNeeded(
      TransformMeta sourceTransform, Point location) throws HopException {
    String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
    if (StringUtils.isEmpty(sourceFieldName)) {
      return sourceTransform;
    }

    String targetSourceFieldName = findTargetSourceFieldName(dvTable);
    if (sourceFieldName.equals(targetSourceFieldName)) {
      return sourceTransform;
    }
    // Other databases honour SQL column aliases; SingleStore JDBC exposes the physical name.
    if (sourceDbMeta == null || !"SINGLESTORE".equalsIgnoreCase(sourceDbMeta.getPluginId())) {
      return sourceTransform;
    }

    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    SelectMetadataChange rename = new SelectMetadataChange();
    rename.setName(sourceFieldName);
    rename.setRename(targetSourceFieldName);
    selectMeta.getSelectOption().getMeta().add(rename);

    TransformMeta renameTransform =
        new TransformMeta("SelectValues", "rename record source", selectMeta);
    renameTransform.setLocation(location.x + 160, location.y);
    pipelineMeta.addTransform(renameTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(sourceTransform, renameTransform));
    return renameTransform;
  }

  protected abstract String getSql() throws HopException;

  protected void appendComma(StringBuilder sql) {
    sql.append(", ");
  }

  protected void appendFields(StringBuilder sql, List<String> quotedFields) {
    sql.append(String.join(", ", quotedFields));
  }

  protected @NonNull List<String> getQuotedPkFields(DvHub hub, DatabaseMeta sourceDbMeta)
      throws HopException {
    if (hub.getBusinessKeys().isEmpty()) {
      throw new HopException("Please specify at least one business key in Hub " + hub.getName());
    }
    List<String> pkQuotedFields = new ArrayList<>();
    String sourceName = variables.resolve(recordSource.getName());

    for (BusinessKey key : hub.getBusinessKeysForSource(sourceName, variables)) {
      if (key == null) {
        continue;
      }
      // Composite: N source parts; multipartite/single: one source field (dual-read list or scalar)
      for (String part : key.resolveSourceParts()) {
        if (StringUtils.isNotEmpty(part)) {
          String quotedPk = sourceDbMeta.quoteField(variables.resolve(part));
          pkQuotedFields.add(quotedPk);
        }
      }
    }
    if (pkQuotedFields.isEmpty()) {
      throw new HopException(
          "Please specify at least one business key mapped to record source "
              + sourceName
              + " in Hub "
              + hub.getName());
    }
    return pkQuotedFields;
  }

  protected void appendOrderByPk(
      StringBuilder sql,
      List<BusinessKey> businessKeys,
      List<String> pkQuotedFields,
      DatabaseMeta databaseMeta) {
    appendOrderByPk(sql, businessKeys, pkQuotedFields, databaseMeta, null);
  }

  protected void appendOrderByPk(
      StringBuilder sql,
      List<BusinessKey> businessKeys,
      List<String> pkQuotedFields,
      DatabaseMeta databaseMeta,
      org.hopper.edw.datavault.metadata.DvSqlOrderByCollationSupport.Session collationSession) {
    DvSqlOrderBySupport.appendOrderBy(
        sql,
        businessKeys,
        pkQuotedFields,
        databaseMeta,
        configuration,
        variables,
        collationSession);
  }

  protected void appendFrom(DatabaseMeta sourceDbMeta, DvDatabaseSource source, StringBuilder sql) {
    String schemaTable =
        sourceDbMeta.getQuotedSchemaTableCombination(
            variables, source.getSchemaName(), source.getTableName());

    sql.append(" FROM ");
    sql.append(schemaTable);
  }

  protected void appendSourceField(IDvTable table, StringBuilder sql, DatabaseMeta sourceDbMeta)
      throws HopException {
    // Add source indicator (record source) column.
    // First we need to determine the target name of this record source column.
    //
    String targetSourceFieldName = findTargetSourceFieldName(table);

    // Now we need to determine the actual value of the source
    //
    // - If there is a static sourceIndicator in DataVaultSource: use resolved constant value,
    // aliased.
    // - If there is a sourceIndicatorField in DataVaultSource: include the source column, aliased
    // to the (per-hub or
    // config) record source field name.
    //
    String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      sql.append(sourceDbMeta.quoteField(sourceFieldName));
    } else {
      // We pick a static value
      //
      String sourceIndicator = recordSource.getSourceIndicator();
      if (StringUtils.isEmpty(sourceIndicator)) {
        throw new HopException(
            "Please specify a static source indicator or a field in source "
                + recordSource.getName());
      }
      sql.append("'").append(sourceIndicator).append("'");
    }
    sql.append(" AS ").append(sourceDbMeta.quoteField(targetSourceFieldName));
  }

  protected @NonNull String findTargetSourceFieldName(IDvTable table) throws HopException {
    return DvSourceFieldMappingSupport.findTargetSourceFieldName(configuration, null, table);
  }

  protected String calculateTransformName(String prefix, DvDatabaseSource source) {
    StringBuilder name = new StringBuilder();
    name.append(prefix);
    name.append(" ");
    name.append(source.getDatabaseName());
    name.append(".");
    if (StringUtils.isNotEmpty(source.getSchemaName())) {
      name.append(source.getSchemaName());
      name.append(".");
    }
    name.append(source.getTableName());

    return name.toString();
  }

  protected TransformMeta createTableInput(
      String sourceTransformName, DatabaseMeta sourceDbMeta, String querySql, Point location) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(sourceDbMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, querySql);

    TransformMeta transformMeta =
        new TransformMeta("TableInput", sourceTransformName, tableInputMeta);
    transformMeta.setLocation(location.x, location.y);

    return transformMeta;
  }
}
