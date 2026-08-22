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
package org.apache.hop.datavault.catalog;

import java.util.Date;
import lombok.Getter;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes dimensional model target table record definitions to a configured data catalog. */
public final class DmCatalogPublisher {

  private DmCatalogPublisher() {}

  /** Optional logging callbacks used by workflow actions and smoke tests. */
  public interface CatalogPublishLog {
    default void logBasic(String message) {}

    default void logError(String message, Throwable throwable) {}
  }

  @Getter
  public static final class PublishResult {
    private final int tableCount;
    private final int errorCount;

    public PublishResult(int tableCount, int errorCount) {
      this.tableCount = tableCount;
      this.errorCount = errorCount;
    }

    public boolean isSuccess() {
      return errorCount == 0;
    }
  }

  public static PublishResult publish(
      String catalogConnectionName,
      DimensionalModel dmModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName)
      throws HopException {
    return publish(catalogConnectionName, dmModel, variables, metadataProvider, workflowName, null);
  }

  public static PublishResult publish(
      String catalogConnectionName,
      DimensionalModel dmModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName,
      CatalogPublishLog log)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName)) {
      throw new HopException("Data catalog connection name is required for publishing");
    }
    if (dmModel == null) {
      throw new HopException("Dimensional model is required for publishing");
    }

    String tableNamespace =
        DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, dmModel);
    Date updatedAt = new Date();

    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    int tableCount = 0;
    int errorCount = 0;

    for (IDmTable table : dmModel.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      try {
        RecordDefinition definition =
            toTableRecordDefinition(
                table,
                dmModel,
                tableNamespace,
                variables,
                metadataProvider,
                updatedAt,
                workflowName,
                variables);
        upsertDefinition(
            registry, catalogConnectionName, definition, variables, metadataProvider, updatedAt);
        tableCount++;
        if (log != null) {
          log.logBasic("Published DM table record definition: " + definition.getKey());
        }
      } catch (Exception e) {
        errorCount++;
        if (log != null) {
          log.logError("Failed to publish DM table '" + table.getName() + "'", e);
        }
      }
    }

    try {
      CatalogModelRegistrySupport.registerDimensionalModel(
          catalogConnectionName, dmModel, variables, metadataProvider, workflowName);
      if (log != null) {
        log.logBasic(
            "Published DM model registry entry: "
                + CatalogModelRegistrySupport.modelRegistryKey(
                    variables,
                    DmCatalogNamespaces.resolveModelBasename(dmModel),
                    org.apache.hop.catalog.model.RecordDefinitionType.DM_MODEL));
      }
    } catch (Exception e) {
      errorCount++;
      if (log != null) {
        log.logError("Failed to publish DM model registry entry", e);
      }
    }

    try {
      org.apache.hop.datavault.lineage.LineageSnapshot lineageSnapshot =
          org.apache.hop.datavault.lineage.DmModelLineageCollector.collect(
              dmModel, variables, metadataProvider);
      org.apache.hop.datavault.lineage.LineageCatalogPublisher.PublishResult lineageResult =
          org.apache.hop.datavault.lineage.LineageCatalogPublisher.publish(
              catalogConnectionName,
              lineageSnapshot,
              variables,
              metadataProvider,
              workflowName,
              adaptLog(log));
      errorCount += lineageResult.getErrorCount();
      if (log != null) {
        log.logBasic(
            "Published "
                + lineageResult.getLineageRecordCount()
                + " lineage catalog record(s) for DM model");
      }
    } catch (Exception e) {
      errorCount++;
      if (log != null) {
        log.logError("Failed to publish DM lineage catalog records", e);
      }
    }

    return new PublishResult(tableCount, errorCount);
  }

  private static org.apache.hop.datavault.lineage.LineageCatalogPublisher.CatalogPublishLog
      adaptLog(CatalogPublishLog log) {
    if (log == null) {
      return null;
    }
    return new org.apache.hop.datavault.lineage.LineageCatalogPublisher.CatalogPublishLog() {
      @Override
      public void logBasic(String message) {
        log.logBasic(message);
      }

      @Override
      public void logError(String message, Throwable throwable) {
        log.logError(message, throwable);
      }
    };
  }

  private static void upsertDefinition(
      RecordDefinitionRegistry registry,
      String catalogConnectionName,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt)
      throws HopException {
    definition.validate();
    RecordDefinition existing =
        registry.read(catalogConnectionName, definition.getKey(), variables, metadataProvider);
    mergeOriginCreatedAt(definition, existing, updatedAt);
    CatalogPublishMergeSupport.mergePreservedCatalogFields(definition, existing);
    registry.upsert(catalogConnectionName, definition, variables, metadataProvider);
  }

  private static void mergeOriginCreatedAt(
      RecordDefinition definition, RecordDefinition existing, Date updatedAt) {
    if (definition.getOrigin() == null) {
      return;
    }
    if (existing != null
        && existing.getOrigin() != null
        && existing.getOrigin().getCreatedAt() != null) {
      definition.getOrigin().setCreatedAt(existing.getOrigin().getCreatedAt());
    } else {
      definition.getOrigin().setCreatedAt(updatedAt);
    }
  }

  static RecordDefinition toTableRecordDefinition(
      IDmTable table,
      DimensionalModel dmModel,
      String namespace,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName,
      IVariables originVariables)
      throws HopException {
    IRowMeta layout = table.getTargetTableLayout(metadataProvider, variables, dmModel);
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, table.getName()));
    definition.setType(mapTableType(table.getTableType()));
    definition.setDescription(table.getDescription());
    definition.setOrigin(
        buildTableOrigin(table, dmModel, originVariables, updatedAt, workflowName));
    definition.setPhysicalTable(buildPhysicalTableRef(dmModel, table, metadataProvider));
    DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, layout, variables);
    DmTableType tableType = table.getTableType();
    if (tableType != null) {
      definition.getTags().add("DM " + tableType.name());
    }
    if (!Utils.isEmpty(dmModel.getName())) {
      definition.getTags().add(dmModel.getName());
    }
    return definition;
  }

  private static RecordDefinitionType mapTableType(DmTableType tableType) {
    if (tableType == null) {
      return RecordDefinitionType.UNKNOWN;
    }
    return switch (tableType) {
      case DIMENSION, DIMENSION_ALIAS, JUNK_DIMENSION, RANGE_DIMENSION, BRIDGE ->
          RecordDefinitionType.DIM_TABLE;
      case FACT,
              FACTLESS_FACT,
              PERIODIC_SNAPSHOT_FACT,
              ACCUMULATING_SNAPSHOT_FACT,
              AGGREGATE_FACT ->
          RecordDefinitionType.FACT_TABLE;
    };
  }

  private static RecordOrigin buildTableOrigin(
      IDmTable table,
      DimensionalModel dmModel,
      IVariables variables,
      Date updatedAt,
      String workflowName) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType("DIMENSIONAL_MODEL");
    origin.setModelName(dmModel.getName());
    origin.setModelFilename(
        CatalogModelRegistrySupport.portableModelPath(dmModel.getFilename(), variables));
    origin.setModelElementName(table.getName());
    origin.setHopProject(DmCatalogNamespaces.resolveProjectKey(variables));
    origin.setUpdatedAt(updatedAt);
    origin.setLastWorkflow(workflowName);
    return origin;
  }

  private static PhysicalTableRef buildPhysicalTableRef(
      DimensionalModel dmModel, IDmTable table, IHopMetadataProvider metadataProvider) {
    DimensionalConfiguration config = dmModel.getConfigurationOrDefault();
    if (config == null || Utils.isEmpty(config.getTargetDatabase())) {
      return null;
    }
    PhysicalTableRef ref = new PhysicalTableRef();
    ref.setDatabaseMetaName(config.getTargetDatabase());
    String tableName =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    ref.setTableName(tableName);
    try {
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(config.getTargetDatabase());
      if (databaseMeta != null && !Utils.isEmpty(databaseMeta.getPreferredSchemaName())) {
        ref.setSchemaName(databaseMeta.getPreferredSchemaName());
      }
    } catch (HopException ignored) {
      // Schema is optional on the physical table reference.
    }
    return ref;
  }
}
