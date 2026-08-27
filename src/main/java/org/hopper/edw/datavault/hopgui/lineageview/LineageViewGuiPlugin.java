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
package org.hopper.edw.datavault.hopgui.lineageview;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopGuiBusinessVaultTableContext;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDimensionalTableContext;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiVaultTableContext;
import org.hopper.edw.datavault.lineage.LineageLayer;

/** Table context actions that open an unsaved Hop Lineage View. */
@GuiPlugin(description = "i18n::LineageViewGuiPlugin.Description")
public class LineageViewGuiPlugin {

  public static final String ACTION_ID_VAULT = "vault-graph-show-lineage";
  public static final String ACTION_ID_BV = "bv-graph-show-lineage";
  public static final String ACTION_ID_DM = "dm-graph-show-lineage";

  @GuiContextAction(
      id = ACTION_ID_VAULT,
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::LineageViewGuiPlugin.Action.Name",
      tooltip = "i18n::LineageViewGuiPlugin.Action.Tooltip",
      image = "lineage-view.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void showLineageFromVault(HopGuiVaultTableContext context) {
    if (context == null || context.getTable() == null || context.getModel() == null) {
      return;
    }
    LineageViewLaunchSupport.openFromTable(
        HopGui.getInstance(),
        context.getVaultGraph() != null ? context.getVaultGraph().getVariables() : null,
        LineageLayer.DV,
        context.getModel().getName(),
        context.getTable().getName(),
        context.getModel().getFilename(),
        context.getModel());
  }

  @GuiContextAction(
      id = ACTION_ID_BV,
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::LineageViewGuiPlugin.Action.Name",
      tooltip = "i18n::LineageViewGuiPlugin.Action.Tooltip",
      image = "lineage-view.svg",
      category = "Business Vault",
      categoryOrder = "4")
  public void showLineageFromBusinessVault(HopGuiBusinessVaultTableContext context) {
    if (context == null || context.getTable() == null || context.getModel() == null) {
      return;
    }
    LineageViewLaunchSupport.openFromTable(
        HopGui.getInstance(),
        context.getBusinessVaultGraph() != null
            ? context.getBusinessVaultGraph().getVariables()
            : null,
        LineageLayer.BV,
        context.getModel().getName(),
        context.getTable().getName(),
        context.getModel().getFilename(),
        context.getModel());
  }

  @GuiContextAction(
      id = ACTION_ID_DM,
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::LineageViewGuiPlugin.Action.Name",
      tooltip = "i18n::LineageViewGuiPlugin.Action.Tooltip",
      image = "lineage-view.svg",
      category = "Dimensional",
      categoryOrder = "6")
  public void showLineageFromDimensional(HopGuiDimensionalTableContext context) {
    if (context == null || context.getTable() == null || context.getModel() == null) {
      return;
    }
    LineageViewLaunchSupport.openFromTable(
        HopGui.getInstance(),
        context.getDimensionalGraph() != null ? context.getDimensionalGraph().getVariables() : null,
        LineageLayer.DM,
        context.getModel().getName(),
        context.getTable().getName(),
        context.getModel().getFilename(),
        context.getModel());
  }
}
