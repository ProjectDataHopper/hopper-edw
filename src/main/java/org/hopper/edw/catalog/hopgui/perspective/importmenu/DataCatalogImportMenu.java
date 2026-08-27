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
package org.hopper.edw.catalog.hopgui.perspective.importmenu;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.hopper.edw.catalog.hopgui.perspective.DataCatalogPerspective;
import org.hopper.edw.datavault.hopgui.GuiBusySupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;

/** Shows the import menu for the Data Catalog perspective. */
public final class DataCatalogImportMenu {

  public static final String CONTEXT_ID = "DataCatalogImportContext";

  private static final Class<?> PKG = DataCatalogPerspective.class;

  private DataCatalogImportMenu() {}

  public static void open(
      HopGui hopGui,
      DataVaultModel model,
      String preferredCatalogConnectionName,
      Runnable onComplete) {
    DataCatalogImportContext context =
        new DataCatalogImportContext(
            hopGui.getShell(),
            hopGui,
            hopGui.getVariables(),
            hopGui.getMetadataProvider(),
            model,
            preferredCatalogConnectionName,
            onComplete);

    List<GuiAction> actions = buildActions(context);
    if (actions.isEmpty()) {
      return;
    }

    GuiContextUtil.getInstance()
        .handleActionSelection(
            hopGui.getShell(),
            BaseMessages.getString(PKG, "DataCatalogPerspective.Import.Menu.Message"),
            new IGuiContextHandler() {
              @Override
              public String getContextId() {
                return CONTEXT_ID;
              }

              @Override
              public List<GuiAction> getSupportedActions() {
                return actions;
              }
            });
  }

  private static List<GuiAction> buildActions(DataCatalogImportContext context) {
    List<GuiAction> actions = new ArrayList<>();
    for (DataCatalogImporter importer : DataCatalogImporterRegistry.getImporters()) {
      Class<?> messagePackage = importer.getClass();
      String label = BaseMessages.getString(messagePackage, importer.getLabelMessageKey());
      String tooltip = BaseMessages.getString(messagePackage, importer.getTooltipMessageKey());
      String image = Const.NVL(importer.getImage(), "ui/images/add.svg");
      actions.add(
          new GuiAction(
              importer.getId(),
              GuiActionType.Custom,
              label,
              tooltip,
              image,
              (shiftClicked, controlClicked, parameters) ->
                  GuiBusySupport.showWhile(
                      context.getShell(),
                      () -> {
                        importer.execute(context);
                        if (context.getOnComplete() != null) {
                          context.getOnComplete().run();
                        }
                      })));
    }
    return actions;
  }
}
