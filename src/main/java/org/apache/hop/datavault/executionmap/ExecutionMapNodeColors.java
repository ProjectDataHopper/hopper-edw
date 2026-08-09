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

import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.ui.core.PropsUi;

/** Node fill colors for execution map painting. */
public final class ExecutionMapNodeColors {

  /** Dark navy fill for workflow nodes; readable with light text. */
  private static final int[] WORKFLOW_FILL_RGB_DARK = {30, 54, 88};

  /** Dark forest green fill for DV model and update nodes; readable with light text. */
  private static final int[] DV_FILL_RGB_DARK = {34, 58, 42};

  /** Dark amber fill for DM nodes; readable with light text in dark mode. */
  private static final int[] DM_FILL_RGB_DARK = {100, 62, 36};

  /** Dark sage fill for target dataset nodes (catalog records with physical tables). */
  private static final int[] TARGET_DATASET_FILL_RGB_DARK = {42, 72, 52};

  /** Dark slate fill for source dataset nodes. */
  private static final int[] SOURCE_DATASET_FILL_RGB_DARK = {42, 58, 78};

  /** Light blue fill for workflow and pipeline nodes; readable with dark text. */
  private static final int[] WORKFLOW_FILL_RGB_LIGHT = {214, 228, 248};

  /** Light green fill for DV model and update nodes; readable with dark text. */
  private static final int[] DV_FILL_RGB_LIGHT = {216, 236, 220};

  /** Light amber fill for DM nodes; readable with dark text. */
  private static final int[] DM_FILL_RGB_LIGHT = {252, 236, 214};

  /** Light sage fill for target dataset nodes. */
  private static final int[] TARGET_DATASET_FILL_RGB_LIGHT = {214, 236, 222};

  /** Light slate fill for source dataset nodes. */
  private static final int[] SOURCE_DATASET_FILL_RGB_LIGHT = {214, 228, 244};

  /** Light teal fill for business vault nodes. */
  private static final int[] BV_FILL_RGB_LIGHT = {220, 242, 248};

  /** Light yellow fill for executor and mapping nodes. */
  private static final int[] EXECUTOR_FILL_RGB_LIGHT = {255, 248, 210};

  private ExecutionMapNodeColors() {}

  public static int[] fillRgb(ExecutionMapNodeType nodeType) {
    return fillRgb(nodeType, isDarkMode());
  }

  static int[] fillRgb(ExecutionMapNodeType nodeType, boolean darkMode) {
    if (nodeType == null || isGeneratedPipelineNode(nodeType)) {
      return null;
    }
    if (darkMode) {
      return darkFillRgb(nodeType);
    }
    return lightFillRgb(nodeType);
  }

  public static void setFillBackground(IGc gc, ExecutionMapNode node) {
    if (gc == null) {
      return;
    }
    int[] rgb = node != null ? fillRgb(node.getNodeType()) : null;
    if (rgb != null) {
      gc.setBackground(rgb[0], rgb[1], rgb[2]);
      return;
    }
    gc.setBackground(resolveEnumFill(node));
  }

  public static EColor resolveEnumFill(ExecutionMapNode node) {
    if (node == null || node.getNodeType() == null) {
      return EColor.LIGHTGRAY;
    }
    return switch (node.getNodeType()) {
      case ROOT_WORKFLOW, WORKFLOW, ROOT_PIPELINE, PIPELINE -> EColor.BLUE;
      case DV_UPDATE, DATA_VAULT_MODEL -> EColor.GREEN;
      case BV_UPDATE, BUSINESS_VAULT_MODEL -> EColor.CRYSTAL;
      case DM_UPDATE, DM_PUBLISH, DIMENSIONAL_MODEL -> EColor.DARKGRAY;
      case RDG_UPDATE -> EColor.BLUE;
      case PIPELINE_EXECUTOR, WORKFLOW_EXECUTOR, MAPPING, META_INJECT -> EColor.YELLOW;
      case GENERATED_PIPELINE, ORCHESTRATOR_PIPELINE, BULK_MASTER_WORKFLOW -> EColor.LIGHTGRAY;
      case SOURCE_DATASET -> EColor.LIGHTBLUE;
      case TARGET_DATASET -> EColor.GREEN;
      default -> EColor.WHITE;
    };
  }

  private static boolean isGeneratedPipelineNode(ExecutionMapNodeType nodeType) {
    return switch (nodeType) {
      case GENERATED_PIPELINE, ORCHESTRATOR_PIPELINE, BULK_MASTER_WORKFLOW -> true;
      default -> false;
    };
  }

  private static int[] darkFillRgb(ExecutionMapNodeType nodeType) {
    return switch (nodeType) {
      case ROOT_WORKFLOW, WORKFLOW, RDG_UPDATE -> WORKFLOW_FILL_RGB_DARK;
      case DV_UPDATE, DATA_VAULT_MODEL -> DV_FILL_RGB_DARK;
      case DM_UPDATE, DM_PUBLISH, DIMENSIONAL_MODEL -> DM_FILL_RGB_DARK;
      case TARGET_DATASET -> TARGET_DATASET_FILL_RGB_DARK;
      case SOURCE_DATASET -> SOURCE_DATASET_FILL_RGB_DARK;
      default -> null;
    };
  }

  private static int[] lightFillRgb(ExecutionMapNodeType nodeType) {
    return switch (nodeType) {
      case ROOT_WORKFLOW, WORKFLOW, ROOT_PIPELINE, PIPELINE, RDG_UPDATE -> WORKFLOW_FILL_RGB_LIGHT;
      case DV_UPDATE, DATA_VAULT_MODEL -> DV_FILL_RGB_LIGHT;
      case BV_UPDATE, BUSINESS_VAULT_MODEL -> BV_FILL_RGB_LIGHT;
      case DM_UPDATE, DM_PUBLISH, DIMENSIONAL_MODEL -> DM_FILL_RGB_LIGHT;
      case PIPELINE_EXECUTOR, WORKFLOW_EXECUTOR, MAPPING, META_INJECT -> EXECUTOR_FILL_RGB_LIGHT;
      case TARGET_DATASET -> TARGET_DATASET_FILL_RGB_LIGHT;
      case SOURCE_DATASET -> SOURCE_DATASET_FILL_RGB_LIGHT;
      default -> null;
    };
  }

  private static boolean isDarkMode() {
    try {
      return PropsUi.getInstance().isDarkMode();
    } catch (Throwable ex) {
      return false;
    }
  }
}
