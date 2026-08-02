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

package org.apache.hop.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;

/** Context handler for clicks on a source table card. */
public class HopGuiSourceTableContext extends BaseGuiContextHandler implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiSourceTableContext";

  private final SourceModel model;
  private final HopGuiSourceModelGraph sourceModelGraph;
  private final SourceTable table;
  private final Point click;

  public HopGuiSourceTableContext(
      SourceModel model,
      HopGuiSourceModelGraph sourceModelGraph,
      SourceTable table,
      Point click) {
    this.model = model;
    this.sourceModelGraph = sourceModelGraph;
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
    GuiActionLambdaBuilder<HopGuiSourceTableContext> lambdaBuilder = new GuiActionLambdaBuilder<>();
    List<GuiAction> pluginActions = getPluginActions(true);
    if (pluginActions != null) {
      for (GuiAction pluginAction : pluginActions) {
        actions.add(lambdaBuilder.createLambda(pluginAction, this, sourceModelGraph));
      }
    }
    return actions;
  }

  public SourceModel getModel() {
    return model;
  }

  public HopGuiSourceModelGraph getSourceModelGraph() {
    return sourceModelGraph;
  }

  public SourceTable getTable() {
    return table;
  }

  public Point getClick() {
    return click;
  }
}
