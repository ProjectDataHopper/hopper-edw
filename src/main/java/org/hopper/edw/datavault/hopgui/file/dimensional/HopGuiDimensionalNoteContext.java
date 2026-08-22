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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;

/** Context handler for clicks on a note in the dimensional model graph. */
public class HopGuiDimensionalNoteContext extends BaseGuiContextHandler
    implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiDimensionalNoteContext";

  private final DimensionalModel model;
  private final HopGuiDimensionalModelGraph dimensionalGraph;
  private final DvNote note;
  private final Point click;

  public HopGuiDimensionalNoteContext(
      DimensionalModel model,
      HopGuiDimensionalModelGraph dimensionalGraph,
      DvNote note,
      Point click) {
    this.model = model;
    this.dimensionalGraph = dimensionalGraph;
    this.note = note;
    this.click = click;
  }

  @Override
  public String getContextId() {
    return CONTEXT_ID;
  }

  @Override
  public List<GuiAction> getSupportedActions() {
    List<GuiAction> actions = new ArrayList<>();
    GuiActionLambdaBuilder<HopGuiDimensionalNoteContext> lambdaBuilder =
        new GuiActionLambdaBuilder<>();
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

  public DvNote getNote() {
    return note;
  }

  public Point getClick() {
    return click;
  }
}
