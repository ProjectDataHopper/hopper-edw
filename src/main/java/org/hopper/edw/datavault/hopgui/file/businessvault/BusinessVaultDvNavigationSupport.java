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
package org.hopper.edw.datavault.hopgui.file.businessvault;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.shared.CanvasSvgHelper;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvDvTableReference;

/** Opens a Data Vault model and navigates to a referenced table (linked or external path). */
public final class BusinessVaultDvNavigationSupport {

  private static final Class<?> PKG = BusinessVaultDvNavigationSupport.class;

  private BusinessVaultDvNavigationSupport() {}

  public static void navigateToDvTable(
      HopGui hopGui, BusinessVaultModel bvModel, String dvTableName, IVariables variables)
      throws HopException {
    navigateToDvTable(hopGui, bvModel, dvTableName, null, variables);
  }

  public static void navigateToDvTable(
      HopGui hopGui, BusinessVaultModel bvModel, BvDvTableReference reference, IVariables variables)
      throws HopException {
    if (reference == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultDvNavigationSupport.Error.MissingTableName"));
    }
    navigateToDvTable(
        hopGui,
        bvModel,
        reference.getDvTableName(),
        reference.getReferencedModelFilename(),
        variables);
  }

  public static void navigateToDvTable(
      HopGui hopGui,
      BusinessVaultModel bvModel,
      String dvTableName,
      String referencedModelFilename,
      IVariables variables)
      throws HopException {
    if (hopGui == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultDvNavigationSupport.Error.MissingHopGui"));
    }
    if (Utils.isEmpty(dvTableName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultDvNavigationSupport.Error.MissingTableName"));
    }

    String resolvedTableName = variables != null ? variables.resolve(dvTableName) : dvTableName;
    if (Utils.isEmpty(resolvedTableName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultDvNavigationSupport.Error.MissingTableName"));
    }

    String modelPath = referencedModelFilename;
    if (Utils.isEmpty(modelPath) && bvModel != null) {
      modelPath =
          BusinessVaultDvModelResolver.resolveModelPathForDvTable(bvModel, resolvedTableName);
    }
    if (Utils.isEmpty(modelPath)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BusinessVaultDvNavigationSupport.Error.MissingDataVaultModelPath"));
    }

    String referringBv = bvModel != null ? bvModel.getFilename() : null;
    DataVaultModel dvModel =
        DvModelLoadSupport.loadDataVaultModel(
            modelPath, referringBv, variables, hopGui.getMetadataProvider());
    IDvTable table = dvModel.findTable(resolvedTableName);
    if (table == null) {
      // Fall back to config-linked model if external path failed to contain the table.
      if (!Utils.isEmpty(referencedModelFilename)
          && bvModel != null
          && !Utils.isEmpty(bvModel.getDataVaultModelPath())) {
        dvModel =
            BusinessVaultDvModelResolver.loadReferencedModel(
                bvModel.getDataVaultModelPath(), variables, hopGui.getMetadataProvider());
        table = dvModel.findTable(resolvedTableName);
        modelPath = bvModel.getDataVaultModelPath();
      }
    }
    if (table == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BusinessVaultDvNavigationSupport.Error.TableNotFound",
              resolvedTableName,
              modelPath));
    }

    String resolvedPath = DvModelLoadSupport.resolveModelPath(modelPath, referringBv, variables);
    try {
      resolvedPath =
          HopVfs.normalize(variables != null ? variables.resolve(resolvedPath) : resolvedPath);
    } catch (Exception e) {
      resolvedPath = variables != null ? variables.resolve(modelPath) : modelPath;
    }

    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    hopGui.setActivePerspective(explorer);
    explorer.activate();

    HopVaultFileType fileType = new HopVaultFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, resolvedPath, variables);
    if (!(handler instanceof HopGuiVaultGraph vaultGraph)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BusinessVaultDvNavigationSupport.Error.UnexpectedFileHandler"));
    }
    vaultGraph.navigateToTable(resolvedTableName);
    // Hop Web: rebind the shared SVG canvas client to the opened tab immediately.
    forceWebCanvasRefresh(vaultGraph);
  }

  private static void forceWebCanvasRefresh(HopGuiVaultGraph vaultGraph) {
    if (!EnvironmentUtils.getInstance().isWeb()
        || vaultGraph == null
        || vaultGraph.isDisposed()
        || vaultGraph.getCanvas() == null
        || vaultGraph.getCanvas().isDisposed()) {
      return;
    }
    // Synchronous rebind first so a trailing mouse-up paint on the previous tab cannot win the
    // shared SVG client race. Then async paint to publish a fresh snapshot.
    CanvasSvgHelper.notifyCanvasReady(vaultGraph.getCanvas());
    vaultGraph
        .getDisplay()
        .asyncExec(
            () -> {
              if (vaultGraph.isDisposed()
                  || vaultGraph.getCanvas() == null
                  || vaultGraph.getCanvas().isDisposed()) {
                return;
              }
              vaultGraph.redraw();
              CanvasSvgHelper.notifyCanvasReady(vaultGraph.getCanvas());
            });
  }
}
