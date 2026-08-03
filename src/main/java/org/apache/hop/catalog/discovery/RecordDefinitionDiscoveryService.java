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
package org.apache.hop.catalog.discovery;

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.datavault.metadata.file.CsvFileMetadataDiscovery;
import org.apache.hop.datavault.metadata.file.ParquetFileMetadataDiscovery;
import org.apache.hop.datavault.metadata.iceberg.IcebergTableMetadataDiscovery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourceQueryCatalogPublisher;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Discovers field layouts from physical database tables and file sources. */
public final class RecordDefinitionDiscoveryService {

  private static final Class<?> PKG = RecordDefinitionDiscoveryService.class;

  private static final ILoggingObject LOGGING_OBJECT =
      new SimpleLoggingObject("RecordDefinitionDiscovery", LoggingObjectType.GENERAL, null);

  private RecordDefinitionDiscoveryService() {}

  public record DiscoveryResult(
      List<SourceField> fields, CsvFileMetadataDiscovery.DiscoveryResult csvDiscovery) {}

  public static DiscoveryResult discover(
      DvSourceType sourceType,
      PhysicalSourceRef physicalRef,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (sourceType == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionDiscoveryService.Error.UnsupportedSourceType", "null"));
    }
    return switch (sourceType) {
      case DATABASE -> discoverDatabase(physicalRef, variables, metadataProvider);
      case CSV -> discoverCsv(physicalRef, variables, metadataProvider);
      case PARQUET -> discoverParquet(physicalRef, variables, metadataProvider);
      case ICEBERG -> discoverIceberg(physicalRef, variables);
      case COMPOSITE -> discoverComposite(physicalRef, variables, metadataProvider);
    };
  }

  /**
   * Rediscover a COMPOSITE feed from its {@code .hsm} source model query projection (same path as
   * publish).
   */
  private static DiscoveryResult discoverComposite(
      PhysicalSourceRef physicalRef, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (physicalRef == null
        || Utils.isEmpty(physicalRef.getCompositeSourceModelFilename())
        || Utils.isEmpty(physicalRef.getCompositeSourceQueryName())) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionDiscoveryService.Error.MissingCompositeRef"));
    }
    String modelFile =
        variables != null
            ? variables.resolve(physicalRef.getCompositeSourceModelFilename())
            : physicalRef.getCompositeSourceModelFilename();
    String queryName =
        variables != null
            ? variables.resolve(physicalRef.getCompositeSourceQueryName())
            : physicalRef.getCompositeSourceQueryName();
    SourceModel model = SourceModelLoadSupport.load(modelFile, variables, metadataProvider);
    SourceQuery query = model.findQuery(queryName);
    if (query == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionDiscoveryService.Error.QueryNotFound", queryName, modelFile));
    }
    List<SourceField> fields = SourceQueryCatalogPublisher.buildFieldsFromProjection(model, query);
    if (fields == null || fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionDiscoveryService.Error.EmptyCompositeProjection", queryName));
    }
    return new DiscoveryResult(fields, null);
  }

  private static DiscoveryResult discoverDatabase(
      PhysicalSourceRef physicalRef, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    String connectionName = Const.NVL(physicalRef.getDatabaseConnectionName(), "").trim();
    String tableName = Const.NVL(physicalRef.getTableName(), "").trim();
    if (Utils.isEmpty(connectionName) || Utils.isEmpty(tableName)) {
      throw new HopException(
          "Database connection name and table name are required for database discovery.");
    }

    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      throw new HopException("Error loading database connection '" + connectionName + "'", e);
    }
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' was not found.");
    }

    String schemaName = Const.NVL(physicalRef.getSchemaName(), "");
    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      List<SourceField> fields =
          DvDatabaseSourceImportSupport.importFieldsFromTable(db, variables, schemaName, tableName);
      return new DiscoveryResult(fields, null);
    } catch (Exception e) {
      throw new HopException("Error discovering database table fields.", e);
    }
  }

  private static DiscoveryResult discoverCsv(
      PhysicalSourceRef physicalRef, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    String resolvedFile = physicalRef.resolveDiscoveryFilePath(variables);
    CsvFileMetadataDiscovery.DiscoveryResult csvDiscovery =
        CsvFileMetadataDiscovery.discover(resolvedFile, variables, metadataProvider);
    return new DiscoveryResult(csvDiscovery.fields(), csvDiscovery);
  }

  private static DiscoveryResult discoverParquet(
      PhysicalSourceRef physicalRef, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    String resolvedFile = physicalRef.resolveDiscoveryFilePath(variables);
    ParquetFileMetadataDiscovery.DiscoveryResult parquetDiscovery =
        ParquetFileMetadataDiscovery.discover(resolvedFile, variables, metadataProvider);
    return new DiscoveryResult(parquetDiscovery.fields(), null);
  }

  private static DiscoveryResult discoverIceberg(
      PhysicalSourceRef physicalRef, IVariables variables) throws HopException {
    IcebergTableMetadataDiscovery.DiscoveryResult icebergDiscovery =
        IcebergTableMetadataDiscovery.discover(physicalRef, variables);
    return new DiscoveryResult(icebergDiscovery.fields(), null);
  }
}
