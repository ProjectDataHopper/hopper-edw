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

import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Maps execution map node types to icon resources and display labels. */
public final class ExecutionMapNodeIconSupport {

  private ExecutionMapNodeIconSupport() {}

  public static String getIconPath(ExecutionMapNodeType nodeType) {
    if (nodeType == null) {
      return "execution-map.svg";
    }
    return switch (nodeType) {
      case ROOT_WORKFLOW, WORKFLOW -> "ui/images/workflow.svg";
      case ROOT_PIPELINE,
              PIPELINE,
              PIPELINE_FILE,
              GENERATED_PIPELINE,
              ORCHESTRATOR_PIPELINE,
              BULK_MASTER_WORKFLOW ->
          "ui/images/pipeline.svg";
      case DATA_VAULT_MODEL, DV_UPDATE -> "datavault-model.svg";
      case BUSINESS_VAULT_MODEL, BV_UPDATE -> "business-vault-model.svg";
      case DIMENSIONAL_MODEL, DM_UPDATE, DM_PUBLISH -> "dimensional-model.svg";
      case RDG_UPDATE -> "resource-definition-group.svg";
      case WORKFLOW_ACTION -> "action.svg";
      case PIPELINE_TRANSFORM, PIPELINE_EXECUTOR, WORKFLOW_EXECUTOR, MAPPING, META_INJECT ->
          "transform.svg";
      case SOURCE_DATASET, TARGET_DATASET -> "data-catalog.svg";
      default -> "execution-map.svg";
    };
  }

  public static ClassLoader getIconClassLoader(ExecutionMapNodeType nodeType) {
    if (nodeType == null) {
      return ExecutionMapNodeIconSupport.class.getClassLoader();
    }
    return switch (nodeType) {
      case ROOT_WORKFLOW,
              WORKFLOW,
              ROOT_PIPELINE,
              PIPELINE,
              PIPELINE_FILE,
              GENERATED_PIPELINE,
              ORCHESTRATOR_PIPELINE,
              BULK_MASTER_WORKFLOW ->
          org.apache.hop.ui.hopgui.HopGui.class.getClassLoader();
      default -> ExecutionMapNodeIconSupport.class.getClassLoader();
    };
  }

  public static String getTypeLabel(ExecutionMapNode node) {
    if (node == null || node.getNodeType() == null) {
      return "";
    }
    return switch (node.getNodeType()) {
      case ROOT_WORKFLOW -> "Root Workflow";
      case WORKFLOW -> "Workflow";
      case ROOT_PIPELINE -> "Root Pipeline";
      case PIPELINE -> "Pipeline";
      case WORKFLOW_ACTION -> "Action";
      case DV_UPDATE -> "DV Update";
      case BV_UPDATE -> "BV Update";
      case DM_UPDATE -> "DM Update";
      case DM_PUBLISH -> "DM Publish";
      case RDG_UPDATE -> "Update resource definition group";
      case PIPELINE_TRANSFORM -> "Transform";
      case PIPELINE_EXECUTOR -> "Pipeline Executor";
      case WORKFLOW_EXECUTOR -> "Workflow Executor";
      case MAPPING -> "Mapping";
      case META_INJECT -> "Meta Inject";
      case DATA_VAULT_MODEL -> "Data Vault Model";
      case BUSINESS_VAULT_MODEL -> "Business Vault Model";
      case DIMENSIONAL_MODEL -> "Dimensional Model";
      case GENERATED_PIPELINE -> "Generated Pipeline";
      case ORCHESTRATOR_PIPELINE -> "Orchestrator";
      case SOURCE_DATASET -> "Source Dataset";
      case TARGET_DATASET -> "Target Dataset";
      case PIPELINE_FILE -> "Pipeline File";
      default -> node.getNodeType().name();
    };
  }
}
