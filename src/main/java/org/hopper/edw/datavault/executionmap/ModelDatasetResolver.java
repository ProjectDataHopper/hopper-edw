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
package org.hopper.edw.datavault.executionmap;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.PipelineMeta;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.datavault.catalog.BvCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DmCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Adds dataset leaf nodes from DV/BV/DM model table and source metadata. */
public final class ModelDatasetResolver {

  private ModelDatasetResolver() {}

  public static void resolveDataVaultModel(
      ExecutionMapContext context, String modelNodeId, DataVaultModel model) {
    if (context == null
        || model == null
        || Utils.isEmpty(modelNodeId)
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    String catalogConnectionName = resolveDataVaultCatalogConnection(context, model);
    String catalogNamespace =
        DvCatalogNamespaces.projectModelsNamespace(context.getVariables(), model);
    String targetDatabase = model.getConfigurationOrDefault().getTargetDatabase();
    for (IDvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      addTargetDataset(
          context,
          modelNodeId,
          catalogNamespace,
          table.getName(),
          table.getName(),
          dvTableKind(table),
          targetDatabase,
          catalogConnectionName);
      addDvTableSources(context, modelNodeId, model, table, catalogConnectionName);
    }
  }

  public static void resolveBusinessVaultModel(
      ExecutionMapContext context, String modelNodeId, BusinessVaultModel model) {
    resolveBusinessVaultModel(context, modelNodeId, model, null);
  }

  public static void resolveBusinessVaultModel(
      ExecutionMapContext context,
      String modelNodeId,
      BusinessVaultModel model,
      DataVaultModel dvModel) {
    if (context == null
        || model == null
        || Utils.isEmpty(modelNodeId)
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    String catalogConnectionName = resolveBusinessVaultCatalogConnection(context, dvModel);
    String catalogNamespace =
        BvCatalogNamespaces.projectBusinessVaultModelsNamespace(context.getVariables(), model);
    String targetDatabase = model.getConfigurationOrDefault().getTargetDatabase();
    for (IBvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      addTargetDataset(
          context,
          modelNodeId,
          catalogNamespace,
          table.getName(),
          table.getName(),
          RecordDefinitionType.BV_TABLE.name(),
          targetDatabase,
          catalogConnectionName);
    }
  }

  public static void resolveDimensionalModel(
      ExecutionMapContext context, String modelNodeId, DimensionalModel model) {
    if (context == null
        || model == null
        || Utils.isEmpty(modelNodeId)
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    String catalogConnectionName = resolveDimensionalCatalogConnection(context, model);
    String catalogNamespace =
        DmCatalogNamespaces.projectDimensionalModelsNamespace(context.getVariables(), model);
    String targetDatabase = model.getConfigurationOrDefault().getTargetDatabase();
    for (IDmTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      addTargetDataset(
          context,
          modelNodeId,
          catalogNamespace,
          table.getName(),
          table.getName(),
          dmTableKind(table),
          targetDatabase,
          catalogConnectionName);
    }
  }

  public static void linkGeneratedPipeline(
      ExecutionMapContext context,
      String modelNodeId,
      String generatedPipelineNodeId,
      PipelineMeta pipelineMeta) {
    if (context == null
        || Utils.isEmpty(generatedPipelineNodeId)
        || pipelineMeta == null
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    PipelineDatasetResolver.resolvePipeline(context, generatedPipelineNodeId, pipelineMeta);
  }

  private static String resolveDataVaultCatalogConnection(
      ExecutionMapContext context, DataVaultModel model) {
    try {
      return DvSourceCatalogService.resolveCatalogConnection(
          model, context.getVariables(), context.getMetadataProvider());
    } catch (Exception e) {
      return null;
    }
  }

  private static String resolveBusinessVaultCatalogConnection(
      ExecutionMapContext context, DataVaultModel dvModel) {
    if (dvModel != null) {
      return resolveDataVaultCatalogConnection(context, dvModel);
    }
    try {
      return DvSourceCatalogService.resolvePreferredCatalogConnection(
          null, context.getVariables(), context.getMetadataProvider());
    } catch (Exception e) {
      return null;
    }
  }

  private static String resolveDimensionalCatalogConnection(
      ExecutionMapContext context, DimensionalModel model) {
    String configured =
        DatasetNodeSupport.resolveValue(
            context.getVariables(), model.getConfigurationOrDefault().getDataCatalogConnection());
    if (!Utils.isEmpty(configured)) {
      return configured;
    }
    try {
      return DvSourceCatalogService.resolvePreferredCatalogConnection(
          null, context.getVariables(), context.getMetadataProvider());
    } catch (Exception e) {
      return null;
    }
  }

  private static void addTargetDataset(
      ExecutionMapContext context,
      String modelNodeId,
      String catalogNamespace,
      String recordName,
      String edgeLabel,
      String kind,
      String targetDatabase,
      String catalogConnectionName) {
    if (Utils.isEmpty(recordName)) {
      return;
    }
    String datasetNodeId =
        DatasetNodeSupport.getOrCreateDatasetNode(
            context,
            ExecutionMapNodeType.TARGET_DATASET,
            catalogNamespace,
            recordName,
            kind,
            modelNodeId,
            catalogConnectionName);
    if (!Utils.isEmpty(datasetNodeId) && !Utils.isEmpty(targetDatabase)) {
      ExecutionMapNode datasetNode = context.getDocument().findNodeById(datasetNodeId);
      if (datasetNode != null) {
        datasetNode.setProperty(
            DatasetNodeSupport.PROPERTY_TARGET_DATABASE,
            DatasetNodeSupport.resolveValue(context.getVariables(), targetDatabase));
      }
    }
    if (!Utils.isEmpty(datasetNodeId)) {
      DatasetNodeSupport.linkDatasetToModel(context, modelNodeId, datasetNodeId, edgeLabel);
    }
  }

  private static void addDvTableSources(
      ExecutionMapContext context,
      String modelNodeId,
      DataVaultModel model,
      IDvTable table,
      String catalogConnectionName) {
    List<DataVaultSource> sources = new ArrayList<>();
    try {
      if (table instanceof DvHub hub) {
        if (hub.getRecordSources() != null) {
          for (String sourceRef : hub.getRecordSources()) {
            if (Utils.isEmpty(sourceRef)) {
              continue;
            }
            DataVaultSource source =
                DvSourceCatalogService.resolveSource(
                    sourceRef, model, context.getVariables(), context.getMetadataProvider());
            if (source != null) {
              sources.add(source);
            }
          }
        }
      } else if (table instanceof DvSatellite satellite) {
        DataVaultSource source =
            satellite.resolveRecordSource(
                context.getVariables(), context.getMetadataProvider(), model);
        if (source != null) {
          sources.add(source);
        }
      } else if (table instanceof DvLink link && link.getLinkHubSources() != null) {
        for (DvLink.DvLinkHubSource linkSource : link.getLinkHubSources()) {
          if (linkSource == null) {
            continue;
          }
          DataVaultSource source =
              linkSource.resolveSource(
                  context.getVariables(), context.getMetadataProvider(), model);
          if (source != null) {
            sources.add(source);
          }
        }
      }
    } catch (HopException e) {
      context.addWarning(
          "Failed to resolve DV sources for table '" + table.getName() + "': " + e.getMessage());
    }
    String sourceNamespace = DvCatalogNamespaces.projectSourcesNamespace(context.getVariables());
    for (DataVaultSource source : sources) {
      addDataVaultSourceDataset(
          context, modelNodeId, source, table.getName(), sourceNamespace, catalogConnectionName);
    }
  }

  private static String dvTableKind(IDvTable table) {
    if (table == null || table.getTableType() == null) {
      return RecordDefinitionType.PHYSICAL_TABLE.name();
    }
    return switch (table.getTableType()) {
      case HUB -> RecordDefinitionType.DV_HUB.name();
      case LINK -> RecordDefinitionType.DV_LINK.name();
      case SATELLITE -> RecordDefinitionType.DV_SATELLITE.name();
      case REFERENCE -> RecordDefinitionType.DV_REFERENCE.name();
      default -> RecordDefinitionType.PHYSICAL_TABLE.name();
    };
  }

  private static String dmTableKind(IDmTable table) {
    if (table == null || table.getTableType() == null) {
      return RecordDefinitionType.PHYSICAL_TABLE.name();
    }
    DmTableType tableType = table.getTableType();
    return switch (tableType) {
      case DIMENSION, DIMENSION_ALIAS, JUNK_DIMENSION, RANGE_DIMENSION, BRIDGE ->
          RecordDefinitionType.DIM_TABLE.name();
      case FACT,
              FACTLESS_FACT,
              PERIODIC_SNAPSHOT_FACT,
              ACCUMULATING_SNAPSHOT_FACT,
              AGGREGATE_FACT ->
          RecordDefinitionType.FACT_TABLE.name();
    };
  }

  private static void addDataVaultSourceDataset(
      ExecutionMapContext context,
      String modelNodeId,
      DataVaultSource source,
      String tableLabel,
      String catalogNamespace,
      String catalogConnectionName) {
    if (source == null || Utils.isEmpty(source.getName())) {
      return;
    }
    String kind = RecordDefinitionType.DV_SOURCE.name();
    String datasetNodeId =
        DatasetNodeSupport.getOrCreateDatasetNode(
            context,
            ExecutionMapNodeType.SOURCE_DATASET,
            catalogNamespace,
            source.getName(),
            kind,
            modelNodeId,
            catalogConnectionName);
    if (!Utils.isEmpty(datasetNodeId)) {
      DatasetNodeSupport.linkDatasetToModel(context, modelNodeId, datasetNodeId, tableLabel);
    }
  }
}
