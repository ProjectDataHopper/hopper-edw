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

package org.apache.hop.datavault.metadata.sourcemodel.publish;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionCatalogWriter;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.CatalogModelRegistrySupport;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorSupport;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.composite.DvCompositeSource;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQueryGenerationSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQuerySqlGenerator;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Publishes a {@link SourceQuery} as a catalog {@code DV_SOURCE} of type {@code COMPOSITE}.
 */
public final class SourceQueryCatalogPublisher {

  private static final Class<?> PKG = SourceQueryCatalogPublisher.class;

  private SourceQueryCatalogPublisher() {}

  public record PublishResult(String catalogName, boolean usedSqlCache, String message) {}

  public static PublishResult publish(
      SourceModel model,
      SourceQuery query,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null || query == null) {
      throw new HopException("Source model and query are required to publish");
    }
    if (Utils.isEmpty(query.getName())) {
      throw new HopException("Source query name is required to publish");
    }
    if (query.getColumns().isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SourceQueryCatalogPublisher.Error.EmptyProjection", query.getName()));
    }

    String catalogConnection = resolveCatalogConnection(model, catalogConnectionName, variables);
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceQueryCatalogPublisher.Error.NoCatalogConnection"));
    }

    String feedName =
        !Utils.isEmpty(query.getPublishedCatalogName())
            ? query.getPublishedCatalogName().trim()
            : query.getName().trim();

    List<SourceField> fields = buildFieldsFromProjection(model, query);
    RecordSourceIndicatorOptions indicatorOptions =
        RecordSourceIndicatorSupport.resolveForTable(null, fields, feedName);

    DvCompositeSource composite = new DvCompositeSource();
    composite.setDescription(
        !Utils.isEmpty(query.getDescription())
            ? query.getDescription()
            : "Source query " + query.getName() + " from " + Const.NVL(model.getName(), "source model"));
    composite.setFields(fields);
    String modelFilename = model.getFilename();
    if (Utils.isEmpty(modelFilename)) {
      modelFilename = model.getName();
    }
    composite.setSourceModelFilename(
        CatalogModelRegistrySupport.portableModelPath(modelFilename, variables));
    composite.setSourceQueryName(query.getName());

    // Cache SQL when single-connection generation is available.
    if (SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, query)) {
      String dbName = SourceQueryGenerationSupport.resolveSharedDatabaseName(model, query);
      DatabaseMeta databaseMeta =
          metadataProvider
              .getSerializer(DatabaseMeta.class)
              .load(variables != null ? variables.resolve(dbName) : dbName);
      if (databaseMeta != null) {
        composite.setGeneratedSql(
            SourceQuerySqlGenerator.generate(model, query, databaseMeta, variables));
      }
    }

    DataVaultSource dataVaultSource = new DataVaultSource(feedName);
    dataVaultSource.setSource(composite);
    dataVaultSource.setSourceIndicator(indicatorOptions.getStaticValue());
    dataVaultSource.setSourceIndicatorField(indicatorOptions.getFieldName());
    dataVaultSource.setDeliveryType(DvSourceDeliveryType.CHANGES_ONLY);

    RecordDefinitionCatalogWriter.upsertDataVaultSource(
        dataVaultSource, catalogConnection, null, variables, metadataProvider, null, null, null);

    query.setPublishedCatalogName(feedName);
    return new PublishResult(
        feedName,
        !Utils.isEmpty(composite.getGeneratedSql()),
        "Published composite feed '" + feedName + "'");
  }

  public static List<PublishResult> publishAll(
      SourceModel model,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<PublishResult> results = new ArrayList<>();
    if (model == null) {
      return results;
    }
    for (SourceQuery query : model.getQueries()) {
      if (query == null || Utils.isEmpty(query.getName())) {
        continue;
      }
      if (query.getColumns().isEmpty()) {
        continue;
      }
      results.add(publish(model, query, catalogConnectionName, variables, metadataProvider));
    }
    return results;
  }

  public static List<SourceField> buildFieldsFromProjection(SourceModel model, SourceQuery query) {
    List<SourceField> fields = new ArrayList<>();
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null || Utils.isEmpty(column.getColumnName())) {
        continue;
      }
      SourceField field = new SourceField(column.resolveAlias());
      SourceTable table =
          model != null && !Utils.isEmpty(column.getTableName())
              ? model.findTable(column.getTableName())
              : null;
      SourceColumn sourceColumn =
          table != null ? table.findColumn(column.getColumnName()) : null;
      if (sourceColumn != null) {
        field.setDescription(sourceColumn.getDescription());
        field.setSourceDataType(sourceColumn.getSourceDataType());
        field.setLength(sourceColumn.getLength());
        field.setPrecision(sourceColumn.getPrecision());
        field.setHopType(sourceColumn.getHopType());
      } else {
        field.setHopType(2); // String default when type unknown
      }
      // Logical feed grain wins; fall back to physical PK when projected under natural name.
      if (column.isPrimaryKey()) {
        field.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      } else if (sourceColumn != null
          && sourceColumn.isPrimaryKey()
          && column.resolveAlias().equalsIgnoreCase(sourceColumn.getName())) {
        field.setPrimaryKeyPosition(sourceColumn.getPrimaryKeyPosition());
      }
      fields.add(field);
    }
    return fields;
  }

  private static String resolveCatalogConnection(
      SourceModel model, String override, IVariables variables) {
    String catalogConnection = Const.NVL(override, "");
    if (variables != null) {
      catalogConnection = variables.resolve(catalogConnection);
    }
    if (Utils.isEmpty(catalogConnection) && model != null) {
      catalogConnection = Const.NVL(model.getConfigurationOrDefault().getCatalogConnection(), "");
      if (variables != null) {
        catalogConnection = variables.resolve(catalogConnection);
      }
    }
    return catalogConnection;
  }
}
