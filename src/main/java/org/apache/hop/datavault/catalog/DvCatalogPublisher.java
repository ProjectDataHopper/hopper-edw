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

package org.apache.hop.datavault.catalog;

import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import org.apache.hop.catalog.model.CatalogCustomProperty;
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
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes Data Vault model target table record definitions to a configured data catalog. */
public final class DvCatalogPublisher {

  private DvCatalogPublisher() {}

  /** Optional logging callbacks used by workflow actions and smoke tests. */
  public interface CatalogPublishLog {
    default void logBasic(String message) {}

    default void logError(String message, Throwable throwable) {}
  }

  @Getter
  public static final class PublishResult {
    private final int tableCount;
    private final int sourceCount;
    private final int errorCount;

    public PublishResult(int tableCount, int sourceCount, int errorCount) {
      this.tableCount = tableCount;
      this.sourceCount = sourceCount;
      this.errorCount = errorCount;
    }

    public int getPublishedCount() {
      return tableCount + sourceCount;
    }

    public boolean isSuccess() {
      return errorCount == 0;
    }
  }

  public static PublishResult publish(
      String catalogConnectionName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName)
      throws HopException {
    return publish(catalogConnectionName, model, variables, metadataProvider, workflowName, null);
  }

  public static PublishResult publish(
      String catalogConnectionName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName,
      CatalogPublishLog log)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName)) {
      throw new HopException("Data catalog connection name is required for publishing");
    }
    if (model == null) {
      throw new HopException("Data Vault model is required for publishing");
    }

    String tableNamespace = DvCatalogNamespaces.projectModelsNamespace(variables, model);
    Date updatedAt = new Date();

    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    int tableCount = 0;
    int sourceCount = 0;
    int errorCount = 0;

    for (IDvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      try {
        RecordDefinition definition =
            toTableRecordDefinition(
                table, model, tableNamespace, variables, metadataProvider, updatedAt, workflowName);
        upsertDefinition(
            registry, catalogConnectionName, definition, variables, metadataProvider, updatedAt);
        tableCount++;
        if (log != null) {
          log.logBasic("Published DV table record definition: " + definition.getKey());
        }
      } catch (Exception e) {
        errorCount++;
        if (log != null) {
          log.logError("Failed to publish DV table '" + table.getName() + "'", e);
        }
      }
    }

    // DV source record definitions are maintained in the source catalog (local-catalog).
    // Publishing a model only snapshots target table layouts under hop/project/models/.

    try {
      CatalogModelRegistrySupport.registerDataVaultModel(
          catalogConnectionName, model, variables, metadataProvider, workflowName);
      if (log != null) {
        log.logBasic(
            "Published DV model registry entry: "
                + CatalogModelRegistrySupport.modelRegistryKey(
                    variables,
                    DvCatalogNamespaces.resolveModelBasename(model),
                    org.apache.hop.catalog.model.RecordDefinitionType.DV_MODEL));
      }
    } catch (Exception e) {
      errorCount++;
      if (log != null) {
        log.logError("Failed to publish DV model registry entry", e);
      }
    }

    try {
      org.apache.hop.datavault.lineage.LineageSnapshot lineageSnapshot =
          org.apache.hop.datavault.lineage.DvModelLineageCollector.collect(
              model, variables, metadataProvider, catalogConnectionName);
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
                + " lineage catalog record(s) for DV model");
      }
    } catch (Exception e) {
      errorCount++;
      if (log != null) {
        log.logError("Failed to publish DV lineage catalog records", e);
      }
    }

    return new PublishResult(tableCount, sourceCount, errorCount);
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
      IDvTable table,
      DataVaultModel model,
      String namespace,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName)
      throws HopException {
    IRowMeta layout = table.getTargetTableLayout(metadataProvider, variables, model);
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, table.getName()));
    definition.setType(mapTableType(table.getTableType()));
    definition.setDescription(table.getDescription());
    definition.setFields(layout);
    definition.setOrigin(buildTableOrigin(table, model, variables, updatedAt, workflowName));
    definition.setPhysicalTable(buildPhysicalTableRef(model, table, metadataProvider));
    definition.getTags().add("DV " + table.getTableType().name());
    if (!Utils.isEmpty(model.getName())) {
      definition.getTags().add(model.getName());
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    if (config != null && !Utils.isEmpty(config.getRecordSourceField())) {
      definition
          .getCustomProperties()
          .put("recordSourceField", CatalogCustomProperty.string(config.getRecordSourceField()));
    }
    return definition;
  }

  private static RecordDefinitionType mapTableType(DvTableType tableType) {
    if (tableType == null) {
      return RecordDefinitionType.UNKNOWN;
    }
    return switch (tableType) {
      case HUB -> RecordDefinitionType.DV_HUB;
      case LINK -> RecordDefinitionType.DV_LINK;
      case SATELLITE -> RecordDefinitionType.DV_SATELLITE;
      case TABLE_REFERENCE -> RecordDefinitionType.UNKNOWN;
    };
  }

  private static RecordOrigin buildTableOrigin(
      IDvTable table,
      DataVaultModel model,
      IVariables variables,
      Date updatedAt,
      String workflowName) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType("DATA_VAULT_MODEL");
    origin.setModelName(model.getName());
    origin.setModelFilename(CatalogModelRegistrySupport.portableModelPath(model.getFilename(), variables));
    origin.setModelElementName(table.getName());
    origin.setHopProject(resolveProjectKey(variables));
    origin.setUpdatedAt(updatedAt);
    origin.setLastWorkflow(workflowName);
    return origin;
  }

  private static PhysicalTableRef buildPhysicalTableRef(
      DataVaultModel model, IDvTable table, IHopMetadataProvider metadataProvider) {
    DataVaultConfiguration config = model.getConfigurationOrDefault();
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

  static Set<DataVaultSource> collectReferencedSources(
      DataVaultModel model, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    Set<String> sourceNames = collectReferencedSourceNames(model);
    Set<DataVaultSource> sources = new LinkedHashSet<>();
    for (String sourceName : sourceNames) {
      addSourceByName(sources, sourceName, model, variables, metadataProvider);
    }
    return sources;
  }

  static Set<String> collectReferencedSourceNames(DataVaultModel model) {
    Set<String> sourceNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (IDvTable table : model.getTables()) {
      if (table instanceof DvHub hub) {
        if (hub.getRecordSources() != null) {
          for (String sourceName : hub.getRecordSources()) {
            if (!Utils.isEmpty(sourceName)) {
              sourceNames.add(sourceName);
            }
          }
        }
      } else if (table instanceof DvSatellite satellite) {
        if (!Utils.isEmpty(satellite.getRecordSourceName())) {
          sourceNames.add(satellite.getRecordSourceName());
        }
      } else if (table instanceof DvLink link) {
        if (link.getLinkHubSources() != null) {
          for (DvLink.DvLinkHubSource linkSource : link.getLinkHubSources()) {
            if (!Utils.isEmpty(linkSource.getSourceName())) {
              sourceNames.add(linkSource.getSourceName());
            }
          }
        }
        if (link.getLinkSatelliteSources() != null) {
          for (DvLink.DvLinkSatelliteSource linkSource : link.getLinkSatelliteSources()) {
            if (!Utils.isEmpty(linkSource.getSourceName())) {
              sourceNames.add(linkSource.getSourceName());
            }
          }
        }
      }
    }
    return sourceNames;
  }

  private static void addSourceByName(
      Set<DataVaultSource> sources,
      String sourceName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(sourceName)) {
      return;
    }
    DataVaultSource source =
        DvSourceCatalogService.resolveSource(sourceName, model, variables, metadataProvider);
    if (source != null) {
      sources.add(source);
    }
  }

  static String resolveProjectKey(IVariables variables) {
    if (variables != null) {
      String projectHome = variables.resolve("${PROJECT_HOME}");
      if (!Utils.isEmpty(projectHome) && !projectHome.contains("${")) {
        Path path = Path.of(projectHome).getFileName();
        if (path != null) {
          return sanitizeKeySegment(path.toString());
        }
      }
    }
    return "default";
  }

  static String resolveModelBasename(DataVaultModel model) {
    String filename = model.getFilename();
    if (!Utils.isEmpty(filename)) {
      String base = Path.of(filename).getFileName().toString();
      int dot = base.lastIndexOf('.');
      if (dot > 0) {
        return sanitizeKeySegment(base.substring(0, dot));
      }
      return sanitizeKeySegment(base);
    }
    return sanitizeKeySegment(model.getName());
  }

  private static String sanitizeKeySegment(String value) {
    if (Utils.isEmpty(value)) {
      return "unknown";
    }
    return value.replace('\\', '_').replace('/', '_').replace(' ', '_');
  }
}