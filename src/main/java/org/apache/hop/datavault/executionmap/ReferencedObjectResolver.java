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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Resolves referenced artifacts from workflow actions and pipeline transforms. */
public final class ReferencedObjectResolver {

  private ReferencedObjectResolver() {}

  public static void resolveAction(
      ExecutionMapContext context, String actionNodeId, IAction action, String pluginId) {
    if (context == null || action == null || Utils.isEmpty(actionNodeId)) {
      return;
    }
    resolveReferencedObjects(context, actionNodeId, pluginId, action);
  }

  public static void resolveTransform(
      ExecutionMapContext context,
      String transformNodeId,
      ITransformMeta transformMeta,
      String pluginId) {
    if (context == null || transformMeta == null || Utils.isEmpty(transformNodeId)) {
      return;
    }
    resolveReferencedObjects(context, transformNodeId, pluginId, transformMeta);
  }

  private static void resolveReferencedObjects(
      ExecutionMapContext context, String fromNodeId, String pluginId, Object referenceSource) {
    String[] descriptions;
    boolean[] enabled;
    try {
      if (referenceSource instanceof IAction action) {
        descriptions = action.getReferencedObjectDescriptions();
        enabled = action.isReferencedObjectEnabled();
      } else if (referenceSource instanceof ITransformMeta transformMeta) {
        descriptions = transformMeta.getReferencedObjectDescriptions();
        enabled = transformMeta.isReferencedObjectEnabled();
      } else {
        return;
      }
    } catch (Exception e) {
      context.addWarning("Failed to read referenced objects for " + pluginId + ": " + e.getMessage());
      return;
    }

    if (descriptions == null || enabled == null) {
      return;
    }

    for (int i = 0; i < descriptions.length; i++) {
      if (i >= enabled.length || !enabled[i]) {
        continue;
      }
      try {
        IHasFilename loaded = loadReferencedObject(referenceSource, i, context);
        if (loaded == null) {
          continue;
        }
        dispatchLoadedArtifact(context, fromNodeId, loaded, descriptions[i]);
      } catch (Exception e) {
        context.addWarning(
            "Failed to load referenced object "
                + (i < descriptions.length ? descriptions[i] : String.valueOf(i))
                + " for "
                + pluginId
                + ": "
                + e.getMessage());
      }
    }
  }

  private static IHasFilename loadReferencedObject(
      Object referenceSource, int index, ExecutionMapContext context) throws HopException {
    if (referenceSource instanceof IAction action) {
      return action.loadReferencedObject(
          index, context.getMetadataProvider(), context.getVariables());
    }
    if (referenceSource instanceof ITransformMeta transformMeta) {
      return transformMeta.loadReferencedObject(
          index, context.getMetadataProvider(), context.getVariables());
    }
    return null;
  }

  private static void dispatchLoadedArtifact(
      ExecutionMapContext context, String fromNodeId, IHasFilename loaded, String description) {
    if (loaded instanceof DataVaultModel dvModel) {
      String modelNodeId =
          addModelNode(context, fromNodeId, ExecutionMapNodeType.DATA_VAULT_MODEL, dvModel);
      context.addEdge(ExecutionMapEdgeType.REFERENCES, fromNodeId, modelNodeId, description);
      ModelDatasetResolver.resolveDataVaultModel(context, modelNodeId, dvModel);
      ModelPipelineResolver.resolveDataVaultModel(context, modelNodeId, dvModel);
      return;
    }
    if (loaded instanceof BusinessVaultModel bvModel) {
      String modelNodeId =
          addModelNode(context, fromNodeId, ExecutionMapNodeType.BUSINESS_VAULT_MODEL, bvModel);
      context.addEdge(ExecutionMapEdgeType.REFERENCES, fromNodeId, modelNodeId, description);
      DataVaultModel dvModel = null;
      try {
        dvModel =
            BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
                bvModel, context.getVariables(), context.getMetadataProvider());
        if (dvModel != null && !dvModel.getTables().isEmpty()) {
          String dvLabel =
              !Utils.isEmpty(dvModel.getFilename())
                  ? dvModel.getFilename()
                  : "effective-dv-from-references";
          String dvNodeId =
              addModelNode(context, modelNodeId, ExecutionMapNodeType.DATA_VAULT_MODEL, dvModel);
          context.addEdge(ExecutionMapEdgeType.MODEL_LINK, modelNodeId, dvNodeId, dvLabel);
        }
      } catch (Exception e) {
        context.addWarning(
            "Failed to resolve Data Vault models for BV '"
                + bvModel.getName()
                + "': "
                + e.getMessage());
      }
      ModelDatasetResolver.resolveBusinessVaultModel(context, modelNodeId, bvModel, dvModel);
      ModelPipelineResolver.resolveBusinessVaultModel(context, modelNodeId, bvModel, dvModel);
      return;
    }
    if (loaded instanceof DimensionalModel dmModel) {
      String modelNodeId =
          addModelNode(context, fromNodeId, ExecutionMapNodeType.DIMENSIONAL_MODEL, dmModel);
      context.addEdge(ExecutionMapEdgeType.REFERENCES, fromNodeId, modelNodeId, description);
      ModelDatasetResolver.resolveDimensionalModel(context, modelNodeId, dmModel);
      ModelPipelineResolver.resolveDimensionalModel(context, modelNodeId, dmModel);
      return;
    }
    if (loaded instanceof WorkflowMeta workflowMeta) {
      String workflowNodeId =
          WorkflowCrawler.crawlWorkflow(
              context, workflowMeta.getFilename(), workflowMeta, false, fromNodeId);
      if (!Utils.isEmpty(workflowNodeId)) {
        context.addEdge(ExecutionMapEdgeType.REFERENCES, fromNodeId, workflowNodeId, description);
      }
      return;
    }
    if (loaded instanceof PipelineMeta pipelineMeta) {
      String pipelineNodeId =
          PipelineCrawler.crawlPipeline(
              context, pipelineMeta.getFilename(), pipelineMeta, false, fromNodeId);
      if (!Utils.isEmpty(pipelineNodeId)) {
        context.addEdge(ExecutionMapEdgeType.REFERENCES, fromNodeId, pipelineNodeId, description);
      }
      return;
    }
    if (loaded != null && !Utils.isEmpty(loaded.getFilename())) {
      String filename = loaded.getFilename();
      String lower = filename.toLowerCase();
      if (lower.endsWith(".hwf")) {
        String workflowNodeId = WorkflowCrawler.crawlWorkflow(context, filename, null, false, fromNodeId);
        context.addEdge(ExecutionMapEdgeType.REFERENCES, fromNodeId, workflowNodeId, description);
      } else if (lower.endsWith(".hpl")) {
        String pipelineNodeId = resolvePipelineFile(context, fromNodeId, filename);
        context.addEdge(ExecutionMapEdgeType.REFERENCES, fromNodeId, pipelineNodeId, description);
      }
    }
  }

  public static String resolvePipelineFile(
      ExecutionMapContext context, String parentNodeId, String pipelineFile) {
    return PipelineCrawler.crawlPipeline(context, pipelineFile, null, false, parentNodeId);
  }

  public static String addModelNode(
      ExecutionMapContext context,
      String parentNodeId,
      ExecutionMapNodeType nodeType,
      IHasFilename model) {
    String path = model != null ? model.getFilename() : null;
    String resolvedPath = context.resolvePath(path);
    String existing = context.existingNodeIdForPath(resolvedPath);
    if (!Utils.isEmpty(existing)) {
      linkModelToParent(context, parentNodeId, existing);
      return existing;
    }
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(nodeType);
    node.setName(model != null ? extractName(model) : path);
    node.setPath(ExecutionMapPathSupport.toStoredPath(resolvedPath, context.getVariables()));
    node.setParentNodeId(parentNodeId);
    context.addNode(node);
    linkModelToParent(context, parentNodeId, node.getId());
    return node.getId();
  }

  private static void linkModelToParent(
      ExecutionMapContext context, String parentNodeId, String modelNodeId) {
    if (context == null
        || Utils.isEmpty(parentNodeId)
        || Utils.isEmpty(modelNodeId)
        || parentNodeId.equals(modelNodeId)) {
      return;
    }
    context.addEdge(ExecutionMapEdgeType.CONTAINS, parentNodeId, modelNodeId, null);
  }

  private static String extractName(IHasFilename model) {
    if (model instanceof DataVaultModel dv && !Utils.isEmpty(dv.getName())) {
      return dv.getName();
    }
    if (model instanceof BusinessVaultModel bv && !Utils.isEmpty(bv.getName())) {
      return bv.getName();
    }
    if (model instanceof DimensionalModel dm && !Utils.isEmpty(dm.getName())) {
      return dm.getName();
    }
    String filename = model.getFilename();
    if (!Utils.isEmpty(filename)) {
      int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
      String base = slash >= 0 ? filename.substring(slash + 1) : filename;
      int dot = base.lastIndexOf('.');
      return dot > 0 ? base.substring(0, dot) : base;
    }
    return "artifact";
  }

  public static DataVaultModel loadDataVaultModel(
      String path, ExecutionMapContext context) throws HopException {
    return loadModel(path, HopVaultFileType.XML_TAG, DataVaultModel.class, context);
  }

  public static BusinessVaultModel loadBusinessVaultModel(
      String path, ExecutionMapContext context) throws HopException {
    return loadModel(path, HopBusinessVaultFileType.XML_TAG, BusinessVaultModel.class, context);
  }

  public static DimensionalModel loadDimensionalModel(
      String path, ExecutionMapContext context) throws HopException {
    return loadModel(path, HopDimensionalFileType.XML_TAG, DimensionalModel.class, context);
  }

  private static <T> T loadModel(
      String path, String xmlTag, Class<T> type, ExecutionMapContext context) throws HopException {
    try {
      String resolvedPath = context.resolvePath(path);
      Document document = XmlHandler.loadXmlFile(resolvedPath);
      Node rootNode = XmlHandler.getSubNode(document, xmlTag);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      T model = type.getDeclaredConstructor().newInstance();
      XmlMetadataUtil.deSerializeFromXml(
          rootNode, type, model, context.getMetadataProvider());
      if (model instanceof DataVaultModel dataVaultModel) {
        dataVaultModel.setFilename(resolvedPath);
      } else if (model instanceof BusinessVaultModel businessVaultModel) {
        businessVaultModel.setFilename(resolvedPath);
      } else if (model instanceof DimensionalModel dimensionalModel) {
        dimensionalModel.setFilename(resolvedPath);
      } else if (model instanceof ExecutionMapDocument executionMapDocument) {
        executionMapDocument.setFilename(resolvedPath);
      }
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load model file: " + path, e);
    }
  }
}