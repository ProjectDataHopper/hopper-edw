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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorSupport;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes a {@link SourceTable} as a catalog {@code DV_SOURCE} of type {@code DATABASE}. */
public final class SourceTableCatalogPublisher {

  private static final Class<?> PKG = SourceTableCatalogPublisher.class;

  private SourceTableCatalogPublisher() {}

  public record PublishResult(String catalogName, String message) {}

  public static PublishResult publish(
      SourceModel model,
      SourceTable table,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null || table == null) {
      throw new HopException("Source model and table are required to publish");
    }
    if (Utils.isEmpty(table.getName())) {
      throw new HopException("Source table name is required to publish");
    }
    DvSourceType physical =
        table.getPhysicalType() != null ? table.getPhysicalType() : DvSourceType.DATABASE;
    if (physical != DvSourceType.DATABASE) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SourceTableCatalogPublisher.Error.UnsupportedPhysicalType", physical));
    }
    List<SourceField> fields = buildFieldsFromTable(table);
    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SourceTableCatalogPublisher.Error.EmptyColumns", table.getName()));
    }

    String catalogConnection = resolveCatalogConnection(model, catalogConnectionName, variables);
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceTableCatalogPublisher.Error.NoCatalogConnection"));
    }

    String feedName =
        !Utils.isEmpty(table.getCatalogSourceName())
            ? table.getCatalogSourceName().trim()
            : table.getName().trim();

    String connectionName = resolveDatabaseConnection(model, table, variables);
    if (Utils.isEmpty(connectionName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SourceTableCatalogPublisher.Error.NoDatabaseConnection", table.getName()));
    }

    String schemaName = Const.NVL(table.getSchemaName(), "");
    String physicalTable =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName().trim() : table.getName().trim();

    RecordSourceIndicatorOptions indicatorOptions =
        RecordSourceIndicatorSupport.resolveForTable(null, fields, feedName);
    DataVaultSource dataVaultSource =
        DvDatabaseSourceImportSupport.createDataVaultSource(
            feedName, connectionName, schemaName, physicalTable, fields, indicatorOptions);
    dataVaultSource.setDeliveryType(DvSourceDeliveryType.CHANGES_ONLY);

    RecordDefinitionCatalogWriter.upsertDataVaultSource(
        dataVaultSource, catalogConnection, null, variables, metadataProvider, null, null, null);

    table.setCatalogSourceName(feedName);
    return new PublishResult(feedName, "Published database feed '" + feedName + "'");
  }

  public static List<SourceField> buildFieldsFromTable(SourceTable table) {
    List<SourceField> fields = new ArrayList<>();
    if (table == null) {
      return fields;
    }
    for (SourceColumn column : table.getColumns()) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      SourceField field = new SourceField(column.getName().trim());
      field.setDescription(column.getDescription());
      field.setSourceDataType(column.getSourceDataType());
      field.setLength(column.getLength());
      field.setPrecision(column.getPrecision());
      field.setHopType(column.getHopType());
      if (column.getPrimaryKeyPosition() > 0) {
        field.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      }
      fields.add(field);
    }
    return fields;
  }

  public static String resolveCatalogFeedName(SourceTable table) {
    if (table == null) {
      return "";
    }
    if (!Utils.isEmpty(table.getCatalogSourceName())) {
      return table.getCatalogSourceName().trim();
    }
    return Const.NVL(table.getName(), "").trim();
  }

  private static String resolveDatabaseConnection(
      SourceModel model, SourceTable table, IVariables variables) {
    String connectionName = table != null ? Const.NVL(table.getDatabaseName(), "") : "";
    if (Utils.isEmpty(connectionName) && model != null) {
      connectionName = Const.NVL(model.getConfigurationOrDefault().getDefaultDatabase(), "");
    }
    if (variables != null && !Utils.isEmpty(connectionName)) {
      connectionName = variables.resolve(connectionName);
    }
    return connectionName;
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
