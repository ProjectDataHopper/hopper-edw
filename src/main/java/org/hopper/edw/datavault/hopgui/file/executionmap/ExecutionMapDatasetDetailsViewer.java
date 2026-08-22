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
package org.hopper.edw.datavault.hopgui.file.executionmap;

import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.executionmap.DatasetNodeSupport;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.eclipse.swt.widgets.Shell;

/** Read-only viewer for dataset node metadata in execution maps. */
public final class ExecutionMapDatasetDetailsViewer {

  private static final Class<?> PKG = HopGuiExecutionMapGraph.class;

  private ExecutionMapDatasetDetailsViewer() {}

  public static boolean isDatasetNode(ExecutionMapNode node) {
    if (node == null || node.getNodeType() == null) {
      return false;
    }
    return node.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET
        || node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET;
  }

  public static String describeDataset(ExecutionMapNode node) {
    if (node == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    if (node.getNodeType() != null) {
      builder.append("Type: ").append(node.getNodeType().name());
    }
    if (!Utils.isEmpty(node.getName())) {
      builder.append(System.lineSeparator()).append("Name: ").append(node.getName());
    }
    if (!Utils.isEmpty(node.getPath())) {
      builder.append(System.lineSeparator()).append("Path: ").append(node.getPath());
    }
    appendProperty(builder, "Kind", node.getProperty("datasetKind"));
    appendProperty(builder, "Namespace", node.getProperty("datasetNamespace"));
    appendProperty(builder, "Dataset", node.getProperty("datasetName"));
    appendProperty(
        builder, "Catalog", node.getProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION));
    appendProperty(
        builder, "Target database", node.getProperty(DatasetNodeSupport.PROPERTY_TARGET_DATABASE));
    return builder.toString();
  }

  public static void showDetails(Shell shell, ExecutionMapNode node) {
    if (shell == null || !isDatasetNode(node)) {
      return;
    }
    EnterTextDialog dialog =
        new EnterTextDialog(
            shell,
            BaseMessages.getString(PKG, "ExecutionMapDatasetDetailsViewer.Dialog.Title"),
            BaseMessages.getString(
                PKG, "ExecutionMapDatasetDetailsViewer.Dialog.Message", node.getName()),
            describeDataset(node),
            true);
    dialog.setReadOnly();
    dialog.open();
  }

  private static void appendProperty(StringBuilder builder, String label, String value) {
    if (!Utils.isEmpty(value)) {
      builder.append(System.lineSeparator()).append(label).append(": ").append(value);
    }
  }
}
