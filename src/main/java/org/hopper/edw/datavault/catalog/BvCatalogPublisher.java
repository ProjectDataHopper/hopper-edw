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
package org.hopper.edw.datavault.catalog;

import java.util.Date;
import lombok.Getter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvTableType;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Publishes Business Vault target table record definitions to a configured data catalog. */
public final class BvCatalogPublisher {

  private BvCatalogPublisher() {}

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
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName)
      throws HopException {
    return publish(
        catalogConnectionName, bvModel, dvModel, variables, metadataProvider, workflowName, null);
  }

  public static PublishResult publish(
      String catalogConnectionName,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName,
      CatalogPublishLog log)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName)) {
      throw new HopException("Data catalog connection name is required for publishing");
    }
    if (bvModel == null) {
      throw new HopException("Business Vault model is required for publishing");
    }

    String tableNamespace =
        BvCatalogNamespaces.projectBusinessVaultModelsNamespace(variables, bvModel);
    Date updatedAt = new Date();

    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    int tableCount = 0;
    int errorCount = 0;

    for (IBvTable table : bvModel.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      try {
        RecordDefinition definition =
            toTableRecordDefinition(
                table,
                bvModel,
                dvModel,
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
          log.logBasic("Published BV table record definition: " + definition.getKey());
        }
      } catch (Exception e) {
        errorCount++;
        if (log != null) {
          log.logError("Failed to publish BV table '" + table.getName() + "'", e);
        }
      }
    }

    try {
      CatalogModelRegistrySupport.registerBusinessVaultModel(
          catalogConnectionName, bvModel, variables, metadataProvider, workflowName);
      if (log != null) {
        log.logBasic(
            "Published BV model registry entry: "
                + CatalogModelRegistrySupport.modelRegistryKey(
                    variables,
                    BvCatalogNamespaces.resolveModelBasename(bvModel),
                    org.hopper.edw.catalog.model.RecordDefinitionType.BV_MODEL));
      }
    } catch (Exception e) {
      errorCount++;
      if (log != null) {
        log.logError("Failed to publish BV model registry entry", e);
      }
    }

    try {
      org.hopper.edw.datavault.lineage.LineageSnapshot lineageSnapshot =
          org.hopper.edw.datavault.lineage.BvModelLineageCollector.collect(bvModel, variables);
      org.hopper.edw.datavault.lineage.LineageCatalogPublisher.PublishResult lineageResult =
          org.hopper.edw.datavault.lineage.LineageCatalogPublisher.publish(
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
                + " lineage catalog record(s) for BV model");
      }
    } catch (Exception e) {
      errorCount++;
      if (log != null) {
        log.logError("Failed to publish BV lineage catalog records", e);
      }
    }

    return new PublishResult(tableCount, errorCount);
  }

  private static org.hopper.edw.datavault.lineage.LineageCatalogPublisher.CatalogPublishLog
      adaptLog(CatalogPublishLog log) {
    if (log == null) {
      return null;
    }
    return new org.hopper.edw.datavault.lineage.LineageCatalogPublisher.CatalogPublishLog() {
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
      IBvTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      String namespace,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName,
      IVariables originVariables)
      throws HopException {
    IRowMeta layout = table.getTargetTableLayout(metadataProvider, variables, bvModel, dvModel);
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, table.getName()));
    definition.setType(RecordDefinitionType.BV_TABLE);
    definition.setDescription(table.getDescription());
    definition.setOrigin(
        buildTableOrigin(table, bvModel, originVariables, updatedAt, workflowName));
    definition.setPhysicalTable(buildPhysicalTableRef(bvModel, table, metadataProvider));
    DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, layout, variables);
    BvTableType tableType = table.getTableType();
    if (tableType != null) {
      definition.getTags().add("BV " + tableType.name());
    }
    if (table instanceof BvBusinessTable businessTable) {
      definition.getTags().add("BV " + businessTable.getMaterializationOrDefault().getCode());
    }
    if (!Utils.isEmpty(bvModel.getName())) {
      definition.getTags().add(bvModel.getName());
    }
    return definition;
  }

  private static RecordOrigin buildTableOrigin(
      IBvTable table,
      BusinessVaultModel bvModel,
      IVariables variables,
      Date updatedAt,
      String workflowName) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType("BUSINESS_VAULT_MODEL");
    origin.setModelName(bvModel.getName());
    origin.setModelFilename(
        CatalogModelRegistrySupport.portableModelPath(bvModel.getFilename(), variables));
    origin.setModelElementName(table.getName());
    origin.setHopProject(BvCatalogNamespaces.resolveProjectKey(variables));
    origin.setUpdatedAt(updatedAt);
    origin.setLastWorkflow(workflowName);
    return origin;
  }

  private static PhysicalTableRef buildPhysicalTableRef(
      BusinessVaultModel bvModel, IBvTable table, IHopMetadataProvider metadataProvider) {
    BusinessVaultConfiguration config = bvModel.getConfigurationOrDefault();
    if (config == null || Utils.isEmpty(config.getTargetDatabase())) {
      return null;
    }
    PhysicalTableRef ref = new PhysicalTableRef();
    ref.setDatabaseMetaName(config.getTargetDatabase());
    ref.setTableName(table.getTableName());
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
