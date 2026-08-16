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
package org.apache.hop.datavault.hopgui.file.businessvault;

import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.dbt.DbtImportDestination;
import org.apache.hop.datavault.dbt.DbtImportOptions;
import org.apache.hop.datavault.dbt.DbtImportResult;
import org.apache.hop.datavault.dbt.DbtImportService;
import org.apache.hop.datavault.layout.ElkGraphLayout;
import org.apache.hop.datavault.layout.ElkLayout;
import org.apache.hop.datavault.layout.ElkLayoutAlgorithm;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;

/** Runs the dbt import dialog against the open Business Vault graph. */
public final class HopGuiDbtImportSupport {

  private static final Class<?> PKG = HopGuiDbtImportSupport.class;

  private HopGuiDbtImportSupport() {}

  public static void importDbtProject(HopGui hopGui, HopGuiBusinessVaultGraph graph) {
    if (hopGui == null || graph == null || graph.getModel() == null) {
      return;
    }
    BusinessVaultModel model = graph.getModel();
    String suggested = !Utils.isEmpty(model.getName()) ? model.getName() + "-macros" : "dbt-macros";
    HopGuiDbtImportDialog dialog =
        new HopGuiDbtImportDialog(hopGui.getShell(), graph.getVariables(), suggested);
    DbtImportOptions options = dialog.open();
    if (options == null) {
      return;
    }
    options.setCurrentModel(model);
    options.setMetadataProvider(hopGui.getMetadataProvider());
    options.setVariables(graph.getVariables());
    try {
      DbtImportResult[] holder = new DbtImportResult[1];
      graph.runUndoableModelChange(
          () -> {
            holder[0] = DbtImportService.apply(options);
            if (options.getDestination() == DbtImportDestination.CURRENT_MODEL
                && !model.getTables().isEmpty()) {
              ElkLayout layout = ElkLayout.createDefault();
              layout.setAlgorithm(ElkLayoutAlgorithm.RECT_PACKING);
              ElkGraphLayout.fromBusinessVaultModel(model, graph.getDataVaultModel())
                  .layout(layout);
            }
          });
      DbtImportResult result = holder[0];
      if (result != null) {
        showReport(hopGui, result);
        for (String filename : result.getWrittenModelFiles()) {
          if (!Utils.isEmpty(filename)) {
            hopGui.fileDelegate.fileOpen(filename);
          }
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDbtImportSupport.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private static void showReport(HopGui hopGui, DbtImportResult result) {
    MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
    box.setText(BaseMessages.getString(PKG, "HopGuiDbtImportSupport.Report.Title"));
    box.setMessage(result.reportText());
    box.open();
  }
}
