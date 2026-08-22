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
package org.apache.hop.datavault.executionmap;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Creates and deduplicates dataset leaf nodes in execution maps. */
public final class DatasetNodeSupport {

  public static final String DATASET_PATH_PREFIX = "dataset://";
  public static final String DATASET_KEY_SEPARATOR = "::";
  public static final String PROPERTY_CATALOG_CONNECTION = "catalogConnectionName";
  public static final String PROPERTY_TARGET_DATABASE = "datasetTargetDatabase";

  private DatasetNodeSupport() {}

  public static String datasetPath(String namespace, String name) {
    String ns = Utils.isEmpty(namespace) ? "default" : namespace;
    String table = Utils.isEmpty(name) ? "unknown" : name;
    return DATASET_PATH_PREFIX + ns + DATASET_KEY_SEPARATOR + table;
  }

  public static String qualifiedLabel(String namespace, String name) {
    if (Utils.isEmpty(namespace)) {
      return name;
    }
    if (Utils.isEmpty(name)) {
      return namespace;
    }
    return namespace + DATASET_KEY_SEPARATOR + name;
  }

  public static String resolveValue(IVariables variables, String value) {
    if (Utils.isEmpty(value) || variables == null) {
      return value;
    }
    return variables.resolve(value);
  }

  public static String getOrCreateDatasetNode(
      ExecutionMapContext context,
      ExecutionMapNodeType nodeType,
      String namespace,
      String datasetName,
      String datasetKind,
      String parentNodeId) {
    return getOrCreateDatasetNode(
        context, nodeType, namespace, datasetName, datasetKind, parentNodeId, null);
  }

  public static String getOrCreateDatasetNode(
      ExecutionMapContext context,
      ExecutionMapNodeType nodeType,
      String namespace,
      String datasetName,
      String datasetKind,
      String parentNodeId,
      String catalogConnectionName) {
    if (context == null || nodeType == null) {
      return null;
    }
    String resolvedNamespace = resolveValue(context.getVariables(), namespace);
    String resolvedName = resolveValue(context.getVariables(), datasetName);
    String resolvedCatalog = resolveValue(context.getVariables(), catalogConnectionName);
    String path = datasetPath(resolvedNamespace, resolvedName);
    String existing = context.existingNodeIdForPath(context.resolvePath(path));
    if (!Utils.isEmpty(existing)) {
      backfillCatalogConnection(context, existing, resolvedCatalog);
      return existing;
    }
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(nodeType);
    node.setName(qualifiedLabel(resolvedNamespace, resolvedName));
    node.setPath(path);
    node.setParentNodeId(parentNodeId);
    if (!Utils.isEmpty(datasetKind)) {
      node.setProperty("datasetKind", datasetKind);
    }
    node.setProperty("datasetNamespace", resolvedNamespace);
    node.setProperty("datasetName", resolvedName);
    if (!Utils.isEmpty(resolvedCatalog)) {
      node.setProperty(PROPERTY_CATALOG_CONNECTION, resolvedCatalog);
    }
    context.addNode(node);
    return node.getId();
  }

  private static void backfillCatalogConnection(
      ExecutionMapContext context, String nodeId, String catalogConnectionName) {
    if (context == null || Utils.isEmpty(nodeId) || Utils.isEmpty(catalogConnectionName)) {
      return;
    }
    ExecutionMapNode existing = context.getDocument().findNodeById(nodeId);
    if (existing != null && Utils.isEmpty(existing.getProperty(PROPERTY_CATALOG_CONNECTION))) {
      existing.setProperty(PROPERTY_CATALOG_CONNECTION, catalogConnectionName);
    }
  }

  public static boolean isModelNodeType(ExecutionMapNodeType nodeType) {
    if (nodeType == null) {
      return false;
    }
    return switch (nodeType) {
      case DATA_VAULT_MODEL, BUSINESS_VAULT_MODEL, DIMENSIONAL_MODEL -> true;
      default -> false;
    };
  }

  public static String resolveOriginModelNodeId(ExecutionMapContext context, String nodeId) {
    if (context == null || Utils.isEmpty(nodeId)) {
      return null;
    }
    String currentId = nodeId;
    while (!Utils.isEmpty(currentId)) {
      ExecutionMapNode node = context.getDocument().findNodeById(currentId);
      if (node == null) {
        return null;
      }
      if (isModelNodeType(node.getNodeType())) {
        return node.getId();
      }
      currentId = node.getParentNodeId();
    }
    return null;
  }

  public static void linkDatasetToModel(
      ExecutionMapContext context, String modelNodeId, String datasetNodeId, String label) {
    if (context == null || Utils.isEmpty(modelNodeId) || Utils.isEmpty(datasetNodeId)) {
      return;
    }
    ExecutionMapNode datasetNode = context.getDocument().findNodeById(datasetNodeId);
    if (datasetNode != null) {
      datasetNode.setParentNodeId(modelNodeId);
    }
    context.addEdgeIfAbsent(ExecutionMapEdgeType.CONTAINS, modelNodeId, datasetNodeId, label);
  }
}
