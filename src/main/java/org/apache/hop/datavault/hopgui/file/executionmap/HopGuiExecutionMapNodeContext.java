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
package org.apache.hop.datavault.hopgui.file.executionmap;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;

/** Context handler for clicks on execution map nodes. */
public class HopGuiExecutionMapNodeContext extends BaseGuiContextHandler
    implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiExecutionMapNodeContext";

  private final ExecutionMapDocument document;
  private final HopGuiExecutionMapGraph executionMapGraph;
  private final ExecutionMapNode node;
  private final Point click;

  public HopGuiExecutionMapNodeContext(
      ExecutionMapDocument document,
      HopGuiExecutionMapGraph executionMapGraph,
      ExecutionMapNode node,
      Point click) {
    this.document = document;
    this.executionMapGraph = executionMapGraph;
    this.node = node;
    this.click = click;
  }

  @Override
  public String getContextId() {
    return CONTEXT_ID;
  }

  @Override
  public List<GuiAction> getSupportedActions() {
    List<GuiAction> actions = new ArrayList<>();
    GuiActionLambdaBuilder<HopGuiExecutionMapNodeContext> lambdaBuilder =
        new GuiActionLambdaBuilder<>();
    List<GuiAction> pluginActions = getPluginActions(true);
    if (pluginActions != null) {
      for (GuiAction pluginAction : pluginActions) {
        actions.add(lambdaBuilder.createLambda(pluginAction, this, executionMapGraph));
      }
    }
    return actions;
  }

  public ExecutionMapDocument getDocument() {
    return document;
  }

  public HopGuiExecutionMapGraph getExecutionMapGraph() {
    return executionMapGraph;
  }

  public ExecutionMapNode getNode() {
    return node;
  }

  public Point getClick() {
    return click;
  }
}
