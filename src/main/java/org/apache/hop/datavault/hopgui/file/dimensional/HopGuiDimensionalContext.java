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
package org.apache.hop.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;

/** Context handler for background clicks on a dimensional model graph canvas. */
public class HopGuiDimensionalContext extends BaseGuiContextHandler implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiDimensionalContext";

  private final DimensionalModel model;
  private final HopGuiDimensionalModelGraph dimensionalGraph;
  private final Point click;

  public HopGuiDimensionalContext(
      DimensionalModel model, HopGuiDimensionalModelGraph dimensionalGraph, Point click) {
    this.model = model;
    this.dimensionalGraph = dimensionalGraph;
    this.click = click;
  }

  @Override
  public String getContextId() {
    return CONTEXT_ID;
  }

  @Override
  public List<GuiAction> getSupportedActions() {
    List<GuiAction> actions = new ArrayList<>();
    GuiActionLambdaBuilder<HopGuiDimensionalContext> lambdaBuilder = new GuiActionLambdaBuilder<>();
    List<GuiAction> pluginActions = getPluginActions(true);
    if (pluginActions != null) {
      for (GuiAction pluginAction : pluginActions) {
        actions.add(lambdaBuilder.createLambda(pluginAction, this, dimensionalGraph));
      }
    }
    return actions;
  }

  public DimensionalModel getModel() {
    return model;
  }

  public HopGuiDimensionalModelGraph getDimensionalGraph() {
    return dimensionalGraph;
  }

  public Point getClick() {
    return click;
  }
}
