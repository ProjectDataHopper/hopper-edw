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
 *
 */

package org.apache.hop.datavault.openlineage;

import org.apache.hop.catalog.model.PhysicalFileRef;
import org.apache.hop.catalog.model.PhysicalIcebergTableRef;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.lineage.FieldContribution;
import org.apache.hop.datavault.lineage.TableLineage;
import org.apache.hop.datavault.lineage.TableSourceKind;
import org.apache.hop.datavault.lineage.TableSourceRef;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves physical dataset locations from model lineage + catalog + DatabaseMeta. */
public final class OpenLineageDatasetLocationResolver {

  private OpenLineageDatasetLocationResolver() {}

  public static DatasetLocation forTargetTable(
      TableLineage table, OpenLineageLocationContext context) {
    if (table == null) {
      return null;
    }
    String connection = table.getTargetDatabaseMetaName();
    String schema = table.getSchemaName();
    String tableName =
        !Utils.isEmpty(table.getPhysicalTableName())
            ? table.getPhysicalTableName()
            : table.getLogicalName();
    if (Utils.isEmpty(tableName) && Utils.isEmpty(connection)) {
      return null;
    }
    String uri = resolveJdbcUri(connection, context);
    return DatasetLocation.builder()
        .kind(DatasetLocationKind.DATABASE)
        .connectionName(connection)
        .schemaName(schema)
        .tableName(tableName)
        .dataSourceName(!Utils.isEmpty(connection) ? connection : "DATABASE")
        .uri(uri)
        .build();
  }

  public static DatasetLocation forTableSource(
      TableSourceRef source, TableLineage consumingTable, OpenLineageLocationContext context) {
    if (source == null || source.getKind() == null) {
      return null;
    }
    return switch (source.getKind()) {
      case DV_SOURCE ->
          forCatalogSource(
              source.getName(), source.getCatalogKey(), context);
      case DV_TABLE, BV_TABLE, DM_TABLE ->
          forParentTable(
              source.getName(),
              source.getPhysicalRef(),
              consumingTable,
              context);
      case CONFIG -> stagingLocation(source.getName());
    };
  }

  public static DatasetLocation forContribution(
      FieldContribution contribution,
      TableLineage consumingTable,
      OpenLineageLocationContext context) {
    if (contribution == null || contribution.getSourceKind() == null) {
      return null;
    }
    TableSourceKind kind = contribution.getSourceKind();
    if (kind == TableSourceKind.CONFIG) {
      if ("DataVaultConfiguration".equals(contribution.getSourceName())
          || "model".equals(contribution.getSourceName())) {
        return null;
      }
      // Technical pseudo-sources are useful in hop_location only; still emit a safe location.
      return stagingLocation(contribution.getSourceName());
    }
    if (kind == TableSourceKind.DV_SOURCE) {
      return forCatalogSource(
          contribution.getSourceName(), contribution.getSourceCatalogKey(), context);
    }
    return forParentTable(
        contribution.getSourceName(), null, consumingTable, context);
  }

  public static DatasetLocation forCatalogSource(
      String sourceName, String catalogKey, OpenLineageLocationContext context) {
    if (context == null || Utils.isEmpty(sourceName)) {
      return null;
    }
    String cacheKey = "src:" + nvl(catalogKey) + ":" + sourceName;
    return context.cached(
        cacheKey,
        () -> resolveCatalogSource(sourceName, catalogKey, context));
  }

  private static DatasetLocation resolveCatalogSource(
      String sourceName, String catalogKey, OpenLineageLocationContext context) {
    IHopMetadataProvider metadataProvider = context.getMetadataProvider();
    IVariables variables = context.getVariables();
    if (metadataProvider == null || Utils.isEmpty(context.getCatalogConnection())) {
      return DatasetLocation.builder()
          .kind(DatasetLocationKind.UNKNOWN)
          .dataSourceName("catalog")
          .tableName(sourceName)
          .uri("hop://catalog/" + sourceName)
          .build();
    }
    try {
      String namespace;
      String name = sourceName;
      if (!Utils.isEmpty(catalogKey) && catalogKey.contains("/")) {
        int slash = catalogKey.lastIndexOf('/');
        namespace = catalogKey.substring(0, slash);
        name = catalogKey.substring(slash + 1);
      } else if (!Utils.isEmpty(catalogKey)) {
        namespace = catalogKey;
      } else {
        // Best-effort: registry still needs a namespace; project sources often use hop/.../sources
        namespace = null;
      }
      RecordDefinition definition = null;
      if (!Utils.isEmpty(namespace)) {
        definition =
            RecordDefinitionRegistry.getInstance()
                .read(
                    context.getCatalogConnection(),
                    new RecordDefinitionKey(namespace, name),
                    variables,
                    metadataProvider);
      }
      if (definition == null) {
        return DatasetLocation.builder()
            .kind(DatasetLocationKind.UNKNOWN)
            .dataSourceName("catalog")
            .tableName(sourceName)
            .uri("hop://catalog/" + sourceName)
            .build();
      }
      return fromRecordDefinition(definition, context);
    } catch (Exception e) {
      return DatasetLocation.builder()
          .kind(DatasetLocationKind.UNKNOWN)
          .dataSourceName("catalog")
          .tableName(sourceName)
          .uri("hop://catalog/" + sourceName)
          .build();
    }
  }

  static DatasetLocation fromRecordDefinition(
      RecordDefinition definition, OpenLineageLocationContext context) {
    if (definition == null) {
      return null;
    }
    PhysicalTableRef table = definition.getPhysicalTable();
    if (table != null
        && (!Utils.isEmpty(table.getTableName()) || !Utils.isEmpty(table.getDatabaseMetaName()))) {
      String connection = table.getDatabaseMetaName();
      return DatasetLocation.builder()
          .kind(DatasetLocationKind.DATABASE)
          .connectionName(connection)
          .schemaName(table.getSchemaName())
          .tableName(table.getTableName())
          .dataSourceName(!Utils.isEmpty(connection) ? connection : "DATABASE")
          .uri(resolveJdbcUri(connection, context))
          .build();
    }
    PhysicalIcebergTableRef iceberg = definition.getPhysicalIcebergTable();
    if (iceberg != null) {
      String tableName = iceberg.getTableName();
      String ns = iceberg.getNamespace();
      String uri = buildIcebergUri(iceberg);
      return DatasetLocation.builder()
          .kind(DatasetLocationKind.ICEBERG)
          .catalogUri(iceberg.getCatalogUri())
          .warehouse(iceberg.getWarehouse())
          .icebergNamespace(ns)
          .icebergTableName(tableName)
          .branch(iceberg.getBranch())
          .snapshotId(iceberg.getSnapshotId())
          .dataSourceName("ICEBERG")
          .uri(uri)
          .build();
    }
    PhysicalFileRef file = definition.getPhysicalFile();
    if (file != null) {
      DatasetLocationKind kind = DatasetLocationKind.CSV;
      DvSourceRecord dvSource = definition.getDvSource();
      if (dvSource != null && !Utils.isEmpty(dvSource.getSourceType())) {
        String st = dvSource.getSourceType().toUpperCase();
        if (st.contains("PARQUET")) {
          kind = DatasetLocationKind.PARQUET;
        } else if (st.contains("CSV")) {
          kind = DatasetLocationKind.CSV;
        }
      }
      String folder = file.getFolder();
      String mask = file.getIncludeFileMask();
      return DatasetLocation.builder()
          .kind(kind)
          .folder(folder)
          .includeFileMask(mask)
          .excludeFileMask(file.getExcludeFileMask())
          .includeSubfolders(file.isIncludeSubfolders())
          .dataSourceName(kind.name())
          .uri(OpenLineageDatasetFacetSupport.toFileUri(folder, mask))
          .build();
    }
    return DatasetLocation.builder()
        .kind(DatasetLocationKind.UNKNOWN)
        .dataSourceName("catalog")
        .tableName(definition.getKey() != null ? definition.getKey().getName() : null)
        .build();
  }

  private static DatasetLocation forParentTable(
      String logicalName,
      String physicalRef,
      TableLineage consumingTable,
      OpenLineageLocationContext context) {
    String tableName = !Utils.isEmpty(physicalRef) ? physicalRef : logicalName;
    if (Utils.isEmpty(tableName)) {
      return null;
    }
    String connection =
        consumingTable != null ? consumingTable.getTargetDatabaseMetaName() : null;
    String schema = consumingTable != null ? consumingTable.getSchemaName() : null;
    return DatasetLocation.builder()
        .kind(DatasetLocationKind.DATABASE)
        .connectionName(connection)
        .schemaName(schema)
        .tableName(tableName)
        .dataSourceName(!Utils.isEmpty(connection) ? connection : "DATABASE")
        .uri(resolveJdbcUri(connection, context))
        .build();
  }

  private static String resolveJdbcUri(String connectionName, OpenLineageLocationContext context) {
    if (Utils.isEmpty(connectionName)
        || context == null
        || context.getMetadataProvider() == null) {
      return !Utils.isEmpty(connectionName) ? "hop://connection/" + connectionName : null;
    }
    try {
      DatabaseMeta databaseMeta =
          context.getMetadataProvider().getSerializer(DatabaseMeta.class).load(connectionName);
      if (databaseMeta == null) {
        return "hop://connection/" + connectionName;
      }
      IVariables variables =
          context.getVariables() != null
              ? context.getVariables()
              : new org.apache.hop.core.variables.Variables();
      String url = databaseMeta.getURL(variables);
      return OpenLineageDatasetFacetSupport.stripCredentials(url);
    } catch (Exception e) {
      return "hop://connection/" + connectionName;
    }
  }

  private static String buildIcebergUri(PhysicalIcebergTableRef iceberg) {
    if (iceberg == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    if (!Utils.isEmpty(iceberg.getCatalogUri())) {
      sb.append(iceberg.getCatalogUri());
    } else if (!Utils.isEmpty(iceberg.getWarehouse())) {
      sb.append(iceberg.getWarehouse());
    } else {
      sb.append("iceberg://");
    }
    if (!Utils.isEmpty(iceberg.getNamespace()) || !Utils.isEmpty(iceberg.getTableName())) {
      if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') {
        sb.append('/');
      }
      if (!Utils.isEmpty(iceberg.getNamespace())) {
        sb.append(iceberg.getNamespace());
        if (!Utils.isEmpty(iceberg.getTableName())) {
          sb.append('.');
        }
      }
      if (!Utils.isEmpty(iceberg.getTableName())) {
        sb.append(iceberg.getTableName());
      }
    }
    return sb.toString();
  }

  private static DatasetLocation stagingLocation(String label) {
    if (Utils.isEmpty(label)) {
      return null;
    }
    return DatasetLocation.builder()
        .kind(DatasetLocationKind.STAGING)
        // Unique dataSource name — never the bare token "STAGING" (Marquez source PK).
        .dataSourceName("staging:" + label)
        .tableName(label)
        .uri("hop://staging/" + label.replace(' ', '_'))
        .build();
  }

  private static String nvl(String value) {
    return value != null ? value : "";
  }
}
