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

package org.apache.hop.datavault.executionmap;

import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.BvCatalogNamespaces;
import org.apache.hop.datavault.catalog.DmCatalogNamespaces;
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Resolves dataset nodes to catalog {@link RecordDefinitionKey} values. */
public final class ExecutionMapDatasetCatalogSupport {

  private ExecutionMapDatasetCatalogSupport() {}

  public static boolean isCatalogNamespace(String namespace) {
    return !Utils.isEmpty(namespace) && namespace.startsWith("hop/");
  }

  public static RecordDefinitionKey resolveDatasetRecordKey(
      ExecutionMapNode node, ExecutionMapDocument document, IVariables variables) {
    RecordDefinitionKey key = resolveDatasetRecordKey(node);
    if (key == null) {
      return null;
    }
    if (isCatalogNamespace(key.getNamespace())) {
      return key;
    }
    String inferredNamespace = inferCatalogNamespaceFromAncestors(node, document, variables);
    if (!Utils.isEmpty(inferredNamespace)) {
      return new RecordDefinitionKey(inferredNamespace, key.getName());
    }
    return key;
  }

  public static RecordDefinitionKey resolveDatasetRecordKey(ExecutionMapNode node) {
    if (node == null) {
      return null;
    }
    String namespace = node.getProperty("datasetNamespace");
    String name = node.getProperty("datasetName");
    if (!Utils.isEmpty(namespace) && !Utils.isEmpty(name)) {
      return new RecordDefinitionKey(namespace, name);
    }
    return parseDatasetPath(node.getPath());
  }

  public static RecordDefinitionKey parseDatasetPath(String path) {
    if (Utils.isEmpty(path)) {
      return null;
    }
    String prefix = DatasetNodeSupport.DATASET_PATH_PREFIX;
    if (!path.regionMatches(true, 0, prefix, 0, prefix.length())) {
      return null;
    }
    String remainder = path.substring(prefix.length());
    String keySeparator = DatasetNodeSupport.DATASET_KEY_SEPARATOR;
    int separator = remainder.indexOf(keySeparator);
    if (separator > 0 && separator < remainder.length() - keySeparator.length()) {
      String namespace = remainder.substring(0, separator);
      String name = remainder.substring(separator + keySeparator.length());
      if (!Utils.isEmpty(namespace) && !Utils.isEmpty(name)) {
        return new RecordDefinitionKey(namespace, name);
      }
    }
    int slash = remainder.lastIndexOf('/');
    if (slash > 0 && slash < remainder.length() - 1) {
      String namespace = remainder.substring(0, slash);
      String name = remainder.substring(slash + 1);
      if (!Utils.isEmpty(namespace) && !Utils.isEmpty(name)) {
        return new RecordDefinitionKey(namespace, name);
      }
    }
    return null;
  }

  static String inferCatalogNamespaceFromAncestors(
      ExecutionMapNode node, ExecutionMapDocument document, IVariables variables) {
    if (node == null || document == null) {
      return null;
    }
    String parentNodeId = node.getParentNodeId();
    while (!Utils.isEmpty(parentNodeId)) {
      ExecutionMapNode parent = document.findNodeById(parentNodeId);
      if (parent == null) {
        return null;
      }
      String namespace = catalogNamespaceForDatasetNode(node, parent, variables);
      if (!Utils.isEmpty(namespace)) {
        return namespace;
      }
      parentNodeId = parent.getParentNodeId();
    }
    return null;
  }

  static String catalogNamespaceForDatasetNode(
      ExecutionMapNode datasetNode, ExecutionMapNode modelNode, IVariables variables) {
    if (datasetNode != null
        && datasetNode.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET) {
      String sourcesNamespace = sourcesNamespaceForModelNode(modelNode, variables);
      if (!Utils.isEmpty(sourcesNamespace)) {
        return sourcesNamespace;
      }
    }
    return catalogNamespaceForModelNode(modelNode, variables);
  }

  static String sourcesNamespaceForModelNode(ExecutionMapNode modelNode, IVariables variables) {
    if (modelNode == null || modelNode.getNodeType() == null) {
      return null;
    }
    return switch (modelNode.getNodeType()) {
      case DATA_VAULT_MODEL -> DvCatalogNamespaces.projectSourcesNamespace(variables);
      default -> null;
    };
  }

  static String catalogNamespaceForModelNode(ExecutionMapNode modelNode, IVariables variables) {
    if (modelNode == null || modelNode.getNodeType() == null) {
      return null;
    }
    return switch (modelNode.getNodeType()) {
      case DIMENSIONAL_MODEL -> {
        DimensionalModel model = new DimensionalModel();
        model.setName(modelNode.getName());
        model.setFilename(
            ExecutionMapPathSupport.toResolvedPath(modelNode.getPath(), variables));
        yield DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, model);
      }
      case DATA_VAULT_MODEL -> {
        DataVaultModel model = new DataVaultModel();
        model.setName(modelNode.getName());
        model.setFilename(
            ExecutionMapPathSupport.toResolvedPath(modelNode.getPath(), variables));
        yield DvCatalogNamespaces.projectModelsNamespace(variables, model);
      }
      case BUSINESS_VAULT_MODEL -> {
        BusinessVaultModel model = new BusinessVaultModel();
        model.setName(modelNode.getName());
        model.setFilename(
            ExecutionMapPathSupport.toResolvedPath(modelNode.getPath(), variables));
        yield BvCatalogNamespaces.projectBusinessVaultModelsNamespace(variables, model);
      }
      default -> null;
    };
  }
}