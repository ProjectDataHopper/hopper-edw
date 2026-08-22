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
package org.apache.hop.datavault.hopgui.lineageview;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.lineageview.HopLineageViewFileType;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineageview.HopLineageViewDocument;
import org.apache.hop.datavault.lineageview.LineageBackendSelectionSupport;
import org.apache.hop.datavault.lineageview.LineageViewSeedSupport;
import org.apache.hop.datavault.metadata.lineage.LineageBackendMeta;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;

/** Opens an unsaved lineage view from a model table. */
public final class LineageViewLaunchSupport {

  private static final Class<?> PKG = LineageViewGuiPlugin.class;

  private LineageViewLaunchSupport() {}

  public static void openFromTable(
      HopGui hopGui,
      IVariables variables,
      LineageLayer layer,
      String modelName,
      String logicalTable,
      String modelFilename,
      Object openModel) {
    if (hopGui == null || Utils.isEmpty(logicalTable)) {
      return;
    }
    try {
      String backendName = chooseBackendName(hopGui);
      if (Utils.isEmpty(backendName)) {
        return;
      }
      HopLineageViewDocument document =
          LineageViewSeedSupport.fromModelTable(layer, modelName, logicalTable, modelFilename);
      document.setBackendName(backendName);
      IHopMetadataSerializer<LineageBackendMeta> serializer =
          hopGui.getMetadataProvider().getSerializer(LineageBackendMeta.class);
      LineageBackendMeta backend = serializer != null ? serializer.load(backendName) : null;
      LineageViewSeedSupport.refreshOpenLineageIds(document, backend, variables);
      LineageSnapshot extra =
          LineageViewSeedSupport.collectOpenModel(
              layer, openModel, variables, hopGui.getMetadataProvider());
      List<LineageSnapshot> extras = extra != null ? List.of(extra) : List.of();
      new HopLineageViewFileType().addToExplorer(hopGui, document, null, extras);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "LineageViewGuiPlugin.Error.Title"),
          BaseMessages.getString(PKG, "LineageViewGuiPlugin.Error.Open"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  static String chooseBackendName(HopGui hopGui) throws HopException {
    IHopMetadataProvider provider = hopGui.getMetadataProvider();
    List<String> enabled =
        new ArrayList<>(LineageBackendSelectionSupport.listEnabledNames(provider));
    if (enabled.size() == 1) {
      return enabled.get(0);
    }
    if (enabled.isEmpty()) {
      MessageBox box = new MessageBox(hopGui.getShell(), SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "LineageViewGuiPlugin.Backend.Missing.Title"));
      box.setMessage(BaseMessages.getString(PKG, "LineageViewGuiPlugin.Backend.Missing.Message"));
      box.open();
      return null;
    }
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            hopGui.getShell(),
            enabled.toArray(String[]::new),
            BaseMessages.getString(PKG, "LineageViewGuiPlugin.Backend.Pick.Title"),
            BaseMessages.getString(PKG, "LineageViewGuiPlugin.Backend.Pick.Message"));
    return dialog.open();
  }
}
