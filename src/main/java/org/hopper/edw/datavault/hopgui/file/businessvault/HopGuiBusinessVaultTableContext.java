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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Context handler for clicks on a Business Vault table on the canvas. */
public class HopGuiBusinessVaultTableContext extends BaseGuiContextHandler
    implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiBusinessVaultTableContext";

  private final BusinessVaultModel model;
  private final HopGuiBusinessVaultGraph businessVaultGraph;
  private final IBvTable table;
  private final Point click;

  public HopGuiBusinessVaultTableContext(
      BusinessVaultModel model,
      HopGuiBusinessVaultGraph businessVaultGraph,
      IBvTable table,
      Point click) {
    this.model = model;
    this.businessVaultGraph = businessVaultGraph;
    this.table = table;
    this.click = click;
  }

  @Override
  public String getContextId() {
    return CONTEXT_ID;
  }

  @Override
  public List<GuiAction> getSupportedActions() {
    List<GuiAction> actions = new ArrayList<>();
    GuiActionLambdaBuilder<HopGuiBusinessVaultTableContext> lambdaBuilder =
        new GuiActionLambdaBuilder<>();
    List<GuiAction> pluginActions = getPluginActions(true);
    if (pluginActions != null) {
      for (GuiAction pluginAction : pluginActions) {
        if ("bv-graph-show-build-pipeline".equals(pluginAction.getId())
            && !(table instanceof BvScd2Table || table instanceof BvPitTable)) {
          continue;
        }
        actions.add(lambdaBuilder.createLambda(pluginAction, this, businessVaultGraph));
      }
    }
    return actions;
  }

  public BusinessVaultModel getModel() {
    return model;
  }

  public HopGuiBusinessVaultGraph getBusinessVaultGraph() {
    return businessVaultGraph;
  }

  public IBvTable getTable() {
    return table;
  }

  public Point getClick() {
    return click;
  }
}
