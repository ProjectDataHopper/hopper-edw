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
package org.apache.hop.datavault.hopgui.file.lineageview;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.datavault.lineageview.HopLineageViewDocument;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;

/** Context handler for clicks on lineage view nodes. */
@Getter
public class HopGuiLineageViewNodeContext extends BaseGuiContextHandler
    implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiLineageViewNodeContext";

  private final HopLineageViewDocument document;
  private final HopGuiLineageViewGraph lineageViewGraph;
  private final LineageNode node;
  private final Point click;

  public HopGuiLineageViewNodeContext(
      HopLineageViewDocument document,
      HopGuiLineageViewGraph lineageViewGraph,
      LineageNode node,
      Point click) {
    this.document = document;
    this.lineageViewGraph = lineageViewGraph;
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
    GuiActionLambdaBuilder<HopGuiLineageViewNodeContext> lambdaBuilder =
        new GuiActionLambdaBuilder<>();
    List<GuiAction> pluginActions = getPluginActions(true);
    if (pluginActions != null) {
      for (GuiAction pluginAction : pluginActions) {
        actions.add(lambdaBuilder.createLambda(pluginAction, this, lineageViewGraph));
      }
    }
    return actions;
  }
}
