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

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;

/** Crawls a Hop workflow and its nested references. */
public final class WorkflowCrawler {

  private WorkflowCrawler() {}

  public static String crawlWorkflow(
      ExecutionMapContext context,
      String workflowPath,
      WorkflowMeta loadedWorkflow,
      boolean root,
      String parentNodeId) {
    if (context == null) {
      return null;
    }
    String resolvedPath = loadedWorkflow != null ? context.resolvePath(loadedWorkflow.getFilename()) : context.resolvePath(workflowPath);
    String existing = context.existingNodeIdForPath(resolvedPath);
    if (!Utils.isEmpty(existing)) {
      if (!Utils.isEmpty(parentNodeId)) {
        context.addEdge(ExecutionMapEdgeType.CONTAINS, parentNodeId, existing, null);
      }
      return existing;
    }
    if (!context.beginArtifactVisit(resolvedPath)) {
      if (!Utils.isEmpty(existing)) {
        return existing;
      }
      context.addWarning("Skipped cyclic or deep workflow reference: " + resolvedPath);
      return null;
    }
    try {
      WorkflowMeta workflowMeta = loadedWorkflow;
      if (workflowMeta == null) {
        workflowMeta =
            new WorkflowMeta(context.getVariables(), resolvedPath, context.getMetadataProvider());
      }

      ExecutionMapNode workflowNode = new ExecutionMapNode();
      workflowNode.setNodeType(root ? ExecutionMapNodeType.ROOT_WORKFLOW : ExecutionMapNodeType.WORKFLOW);
      workflowNode.setName(workflowMeta.getName());
      workflowNode.setPath(
          ExecutionMapPathSupport.toStoredPath(resolvedPath, context.getVariables()));
      workflowNode.setParentNodeId(parentNodeId);
      workflowNode.setSnapshotId(
          context.captureWorkflowSnapshot(resolvedPath, workflowMeta.getXml(context.getVariables())));
      context.addNode(workflowNode);
      String workflowNodeId = workflowNode.getId();

      if (!Utils.isEmpty(parentNodeId)) {
        context.addEdge(ExecutionMapEdgeType.CONTAINS, parentNodeId, workflowNodeId, null);
      }

      boolean includeActions = context.getOptions().isIncludeWorkflowActions();
      Map<String, String> actionNodeIds = new HashMap<>();
      for (ActionMeta actionMeta : workflowMeta.getActions()) {
        if (actionMeta == null || Utils.isEmpty(actionMeta.getName())) {
          continue;
        }
        IAction action = actionMeta.getAction();
        String pluginId = action != null ? action.getPluginId() : null;
        String referenceFromId = workflowNodeId;
        if (includeActions) {
          ExecutionMapNode actionNode = new ExecutionMapNode();
          actionNode.setNodeType(ExecutionMapContext.mapActionPluginId(pluginId));
          actionNode.setName(actionMeta.getName());
          actionNode.setPluginId(pluginId);
          actionNode.setParentNodeId(workflowNodeId);
          context.addNode(actionNode);
          actionNodeIds.put(actionMeta.getName(), actionNode.getId());
          context.addEdge(ExecutionMapEdgeType.CONTAINS, workflowNodeId, actionNode.getId(), null);
          referenceFromId = actionNode.getId();
        }
        if (action != null) {
          ReferencedObjectResolver.resolveAction(context, referenceFromId, action, pluginId);
        }
      }

      if (includeActions) {
        for (WorkflowHopMeta hop : workflowMeta.getWorkflowHops()) {
          if (hop == null || hop.getFromAction() == null || hop.getToAction() == null) {
            continue;
          }
          String fromId = actionNodeIds.get(hop.getFromAction().getName());
          String toId = actionNodeIds.get(hop.getToAction().getName());
          if (!Utils.isEmpty(fromId) && !Utils.isEmpty(toId)) {
            context.addEdge(ExecutionMapEdgeType.HOP, fromId, toId, null);
          }
        }
      }
      return workflowNodeId;
    } catch (HopException e) {
      context.addWarning("Failed to crawl workflow " + resolvedPath + ": " + e.getMessage());
      return null;
    } finally {
      context.endArtifactVisit();
    }
  }
}