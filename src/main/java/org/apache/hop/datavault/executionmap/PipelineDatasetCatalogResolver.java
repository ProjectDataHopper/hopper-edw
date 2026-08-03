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
package org.apache.hop.datavault.executionmap;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/**
 * Resolves pipeline table references to catalog-backed dataset nodes when crawling under models.
 */
final class PipelineDatasetCatalogResolver {

  private static final Pattern GENERATED_PIPELINE_SOURCE_NAME =
      Pattern.compile("^(?:hub|sat|link|lnk|sts)-[^-]+-(.+)$");

  private PipelineDatasetCatalogResolver() {}

  static String resolveDatasetNodeId(
      ExecutionMapContext context,
      String originModelNodeId,
      String pipelineNodeId,
      ExecutionMapNodeType requestedNodeType,
      String connection,
      String tableName,
      String transformName) {
    if (context == null || Utils.isEmpty(originModelNodeId)) {
      return null;
    }
    ExecutionMapNode modelNode = context.getDocument().findNodeById(originModelNodeId);
    if (modelNode == null || !DatasetNodeSupport.isModelNodeType(modelNode.getNodeType())) {
      return null;
    }

    String resolvedConnection = DatasetNodeSupport.resolveValue(context.getVariables(), connection);
    String resolvedTable = DatasetNodeSupport.resolveValue(context.getVariables(), tableName);
    if (Utils.isEmpty(resolvedTable)) {
      return null;
    }

    ExecutionMapNodeType nodeType = resolveNodeType(requestedNodeType, transformName);
    String existingNodeId =
        findExistingDatasetNode(context, resolvedTable, nodeType, pipelineNodeId);
    if (!Utils.isEmpty(existingNodeId)) {
      return existingNodeId;
    }

    ResolvedDataset resolved =
        resolveCatalogDataset(
            context,
            modelNode,
            pipelineNodeId,
            nodeType,
            resolvedConnection,
            resolvedTable,
            transformName);
    if (resolved == null) {
      return null;
    }

    return DatasetNodeSupport.getOrCreateDatasetNode(
        context,
        resolved.nodeType(),
        resolved.namespace(),
        resolved.datasetName(),
        resolved.datasetKind(),
        pipelineNodeId,
        resolved.catalogConnectionName());
  }

  private static ExecutionMapNodeType resolveNodeType(
      ExecutionMapNodeType requestedNodeType, String transformName) {
    if (requestedNodeType != null) {
      return requestedNodeType;
    }
    if (isTargetReadTransform(transformName)) {
      return ExecutionMapNodeType.TARGET_DATASET;
    }
    return ExecutionMapNodeType.SOURCE_DATASET;
  }

  private static String findExistingDatasetNode(
      ExecutionMapContext context,
      String tableName,
      ExecutionMapNodeType preferredNodeType,
      String pipelineNodeId) {
    String sourceName = extractSourceNameFromPipeline(context, pipelineNodeId);
    return context.getDocument().getNodesOrEmpty().stream()
        .filter(node -> node != null && isDatasetNodeType(node.getNodeType()))
        .filter(
            node -> {
              String datasetName = node.getProperty("datasetName");
              if (Utils.isEmpty(datasetName)) {
                return false;
              }
              if (datasetName.equalsIgnoreCase(tableName)) {
                return true;
              }
              return !Utils.isEmpty(sourceName) && sourceName.equalsIgnoreCase(datasetName);
            })
        .filter(
            node ->
                preferredNodeType == null
                    || node.getNodeType() == preferredNodeType
                    || !hasCatalogNamespace(node))
        .max(datasetNodePreference(preferredNodeType))
        .map(ExecutionMapNode::getId)
        .orElse(null);
  }

  private static Comparator<ExecutionMapNode> datasetNodePreference(
      ExecutionMapNodeType preferredNodeType) {
    return Comparator.<ExecutionMapNode>comparingInt(node -> hasCatalogNamespace(node) ? 1 : 0)
        .thenComparingInt(
            node -> preferredNodeType != null && node.getNodeType() == preferredNodeType ? 1 : 0);
  }

  private static boolean hasCatalogNamespace(ExecutionMapNode node) {
    return ExecutionMapDatasetCatalogSupport.isCatalogNamespace(
        node.getProperty("datasetNamespace"));
  }

  private static boolean isDatasetNodeType(ExecutionMapNodeType nodeType) {
    return nodeType == ExecutionMapNodeType.SOURCE_DATASET
        || nodeType == ExecutionMapNodeType.TARGET_DATASET;
  }

  private static ResolvedDataset resolveCatalogDataset(
      ExecutionMapContext context,
      ExecutionMapNode modelNode,
      String pipelineNodeId,
      ExecutionMapNodeType nodeType,
      String connection,
      String tableName,
      String transformName) {
    boolean targetRead = isTargetReadTransform(transformName);
    boolean targetWrite = nodeType == ExecutionMapNodeType.TARGET_DATASET && !targetRead;
    boolean vaultTable = isVaultTableName(tableName);

    if (targetWrite
        || targetRead
        || (vaultTable && nodeType == ExecutionMapNodeType.SOURCE_DATASET)) {
      String namespace =
          ExecutionMapDatasetCatalogSupport.catalogNamespaceForModelNode(
              modelNode, context.getVariables());
      if (vaultTable && !ExecutionMapDatasetCatalogSupport.isCatalogNamespace(namespace)) {
        namespace = findVaultModelNamespace(context, tableName);
      }
      if (Utils.isEmpty(namespace)) {
        return null;
      }
      return new ResolvedDataset(
          ExecutionMapNodeType.TARGET_DATASET,
          namespace,
          tableName,
          inferDatasetKind(tableName),
          findCatalogConnection(context, tableName));
    }

    ExecutionMapNode provisional = new ExecutionMapNode();
    provisional.setNodeType(ExecutionMapNodeType.SOURCE_DATASET);
    String namespace =
        ExecutionMapDatasetCatalogSupport.catalogNamespaceForDatasetNode(
            provisional, modelNode, context.getVariables());
    if (Utils.isEmpty(namespace)) {
      return null;
    }

    String datasetName = extractSourceNameFromPipeline(context, pipelineNodeId);
    if (Utils.isEmpty(datasetName)) {
      datasetName = tableName;
    }

    return new ResolvedDataset(
        ExecutionMapNodeType.SOURCE_DATASET,
        namespace,
        datasetName,
        RecordDefinitionType.DV_SOURCE.name(),
        findCatalogConnection(context, datasetName));
  }

  private static String findVaultModelNamespace(ExecutionMapContext context, String tableName) {
    return context.getDocument().getNodesOrEmpty().stream()
        .filter(node -> node != null && node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET)
        .filter(node -> tableName.equalsIgnoreCase(node.getProperty("datasetName")))
        .filter(PipelineDatasetCatalogResolver::hasCatalogNamespace)
        .map(node -> node.getProperty("datasetNamespace"))
        .findFirst()
        .orElse(null);
  }

  private static String findCatalogConnection(ExecutionMapContext context, String datasetName) {
    return context.getDocument().getNodesOrEmpty().stream()
        .filter(node -> node != null && isDatasetNodeType(node.getNodeType()))
        .filter(node -> datasetName.equalsIgnoreCase(node.getProperty("datasetName")))
        .map(node -> node.getProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION))
        .filter(connection -> !Utils.isEmpty(connection))
        .findFirst()
        .orElse(null);
  }

  private static String extractSourceNameFromPipeline(
      ExecutionMapContext context, String pipelineNodeId) {
    if (context == null || Utils.isEmpty(pipelineNodeId)) {
      return null;
    }
    ExecutionMapNode pipelineNode = context.getDocument().findNodeById(pipelineNodeId);
    if (pipelineNode == null || Utils.isEmpty(pipelineNode.getName())) {
      return null;
    }
    Matcher matcher = GENERATED_PIPELINE_SOURCE_NAME.matcher(pipelineNode.getName().trim());
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return null;
  }

  private static boolean isTargetReadTransform(String transformName) {
    if (Utils.isEmpty(transformName)) {
      return false;
    }
    return transformName.trim().toLowerCase().startsWith("target_");
  }

  private static boolean isVaultTableName(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return false;
    }
    String normalized = tableName.trim().toLowerCase();
    return normalized.startsWith("hub_")
        || normalized.startsWith("sat_")
        || normalized.startsWith("lnk_")
        || normalized.startsWith("link_");
  }

  private static String inferDatasetKind(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return RecordDefinitionType.PHYSICAL_TABLE.name();
    }
    String normalized = tableName.trim().toLowerCase();
    if (normalized.startsWith("hub_")) {
      return RecordDefinitionType.DV_HUB.name();
    }
    if (normalized.startsWith("lnk_") || normalized.startsWith("link_")) {
      return RecordDefinitionType.DV_LINK.name();
    }
    if (normalized.startsWith("sat_")) {
      return RecordDefinitionType.DV_SATELLITE.name();
    }
    if (normalized.startsWith("d_")) {
      return RecordDefinitionType.DIM_TABLE.name();
    }
    if (normalized.startsWith("f_")) {
      return RecordDefinitionType.FACT_TABLE.name();
    }
    return RecordDefinitionType.PHYSICAL_TABLE.name();
  }

  private record ResolvedDataset(
      ExecutionMapNodeType nodeType,
      String namespace,
      String datasetName,
      String datasetKind,
      String catalogConnectionName) {}
}
