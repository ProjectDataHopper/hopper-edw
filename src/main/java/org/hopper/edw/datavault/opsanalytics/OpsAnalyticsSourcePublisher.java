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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogWriter;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorSupport;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSourceDeliveryType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.composite.DvCompositeSource;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourceTableCatalogPublisher;

/** Publishes the operational analytics source model into the project sources catalog. */
public final class OpsAnalyticsSourcePublisher {

  private OpsAnalyticsSourcePublisher() {}

  /**
   * Writes each table and conformed-key query as a {@code DV_SOURCE} in the project sources
   * namespace. An empty catalog connection uses {@code local-catalog}.
   */
  public static int publish(
      String filename,
      String catalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return publish(
        SourceModelLoadSupport.load(filename, variables, metadataProvider),
        catalogConnection,
        variables,
        metadataProvider);
  }

  public static int publish(
      SourceModel model,
      String catalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException("Source model is required");
    }
    String catalog =
        Utils.isEmpty(catalogConnection)
            ? DvSourceCatalogService.DEFAULT_SOURCE_CATALOG_CONNECTION
            : catalogConnection;
    int count = 0;
    for (SourceTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      SourceTableCatalogPublisher.publish(model, table, catalog, variables, metadataProvider);
      count++;
    }
    for (SourceQuery query : model.getQueries()) {
      if (query == null || Utils.isEmpty(query.getName())) {
        continue;
      }
      publishQuery(model, query, catalog, variables, metadataProvider);
      count++;
    }
    return count;
  }

  /**
   * Conformed-key queries are free SQL with a schema qualifier. The source-model SQL planner treats
   * that qualifier as a table name, so fields are taken from the query columns and the free SQL is
   * stored for Table Input.
   */
  private static void publishQuery(
      SourceModel model,
      SourceQuery query,
      String catalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String feedName =
        !Utils.isEmpty(query.getPublishedCatalogName())
            ? query.getPublishedCatalogName().trim()
            : query.getName().trim();
    List<SourceField> fields = fieldsFromColumns(model, query);
    if (fields.isEmpty()) {
      throw new HopException("Source query '" + query.getName() + "' has no columns to publish");
    }
    RecordSourceIndicatorOptions indicatorOptions =
        RecordSourceIndicatorSupport.resolveForTable(null, fields, feedName);

    DvCompositeSource composite = new DvCompositeSource();
    composite.setDescription(
        !Utils.isEmpty(query.getDescription())
            ? query.getDescription()
            : "Source query " + query.getName());
    composite.setFields(fields);
    String modelFilename = model.getFilename();
    if (Utils.isEmpty(modelFilename)) {
      modelFilename = model.getName();
    }
    composite.setSourceModelFilename(
        CatalogModelRegistrySupport.portableModelPath(modelFilename, variables));
    composite.setSourceQueryName(query.getName());
    if (!Utils.isEmpty(query.getFreeSql())) {
      composite.setGeneratedSql(query.getFreeSql());
    }

    DataVaultSource dataVaultSource = new DataVaultSource(feedName);
    dataVaultSource.setSource(composite);
    dataVaultSource.setSourceIndicator(indicatorOptions.getStaticValue());
    dataVaultSource.setSourceIndicatorField(indicatorOptions.getFieldName());
    dataVaultSource.setDeliveryType(DvSourceDeliveryType.CHANGES_ONLY);
    RecordDefinitionCatalogWriter.upsertDataVaultSource(
        dataVaultSource, catalogConnection, null, variables, metadataProvider, null, null, null);
    query.setPublishedCatalogName(feedName);
  }

  private static List<SourceField> fieldsFromColumns(SourceModel model, SourceQuery query) {
    List<SourceField> fields = new ArrayList<>();
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null || Utils.isEmpty(column.resolveAlias())) {
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
        field.setHopType(sourceColumn.getHopType());
        if (!Utils.isEmpty(sourceColumn.getLength())) {
          field.setLength(sourceColumn.getLength());
        }
      }
      if (column.getPrimaryKeyPosition() > 0) {
        field.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      }
      fields.add(field);
    }
    return fields;
  }
}
