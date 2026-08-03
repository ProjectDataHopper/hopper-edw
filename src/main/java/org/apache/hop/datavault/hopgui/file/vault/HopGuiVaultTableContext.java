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
package org.apache.hop.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;

/**
 * Context handler for clicks on a specific table (DvTable) icon body in the Data Vault graph (as
 * opposed to the name part which directly edits, or background). Allows @GuiContextAction annotated
 * methods (in HopGuiVaultGraph) with parentId = HopGuiVaultTableContext.CONTEXT_ID to contribute
 * actions (e.g. Edit, Delete).
 */
public class HopGuiVaultTableContext extends BaseGuiContextHandler implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiVaultTableContext";

  private final DataVaultModel model;
  private final HopGuiVaultGraph vaultGraph;
  private final IDvTable table;
  private final Point click;

  public HopGuiVaultTableContext(
      DataVaultModel model, HopGuiVaultGraph vaultGraph, IDvTable table, Point click) {
    this.model = model;
    this.vaultGraph = vaultGraph;
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
    GuiActionLambdaBuilder<HopGuiVaultTableContext> lambdaBuilder = new GuiActionLambdaBuilder<>();

    // Get the actions from @GuiContextAction annotations (in HopGuiVaultGraph)
    // that specify parentId = HopGuiVaultTableContext.CONTEXT_ID .
    // We manually wrap with lambdas that invoke on the *real* vaultGraph instance we hold.
    List<GuiAction> pluginActions = getPluginActions(true);
    if (pluginActions != null) {
      for (GuiAction pluginAction : pluginActions) {
        actions.add(lambdaBuilder.createLambda(pluginAction, this, vaultGraph));
      }
    }

    return actions;
  }

  /**
   * Gets model
   *
   * @return value of model
   */
  public DataVaultModel getModel() {
    return model;
  }

  /**
   * Gets vaultGraph
   *
   * @return value of vaultGraph
   */
  public HopGuiVaultGraph getVaultGraph() {
    return vaultGraph;
  }

  /**
   * Gets table
   *
   * @return value of table
   */
  public IDvTable getTable() {
    return table;
  }

  /**
   * Gets click
   *
   * @return value of click
   */
  public Point getClick() {
    return click;
  }
}
