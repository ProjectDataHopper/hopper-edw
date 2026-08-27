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
package org.hopper.edw.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvNote;

/**
 * Context handler for clicks on a note in the Data Vault graph. Allows {@code @GuiContextAction}
 * methods with {@code parentId = HopGuiVaultNoteContext.CONTEXT_ID}.
 */
public class HopGuiVaultNoteContext extends BaseGuiContextHandler implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiVaultNoteContext";

  private final DataVaultModel model;
  private final HopGuiVaultGraph vaultGraph;
  private final DvNote note;
  private final Point click;

  public HopGuiVaultNoteContext(
      DataVaultModel model, HopGuiVaultGraph vaultGraph, DvNote note, Point click) {
    this.model = model;
    this.vaultGraph = vaultGraph;
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
    GuiActionLambdaBuilder<HopGuiVaultNoteContext> lambdaBuilder = new GuiActionLambdaBuilder<>();

    List<GuiAction> pluginActions = getPluginActions(true);
    if (pluginActions != null) {
      for (GuiAction pluginAction : pluginActions) {
        actions.add(lambdaBuilder.createLambda(pluginAction, this, vaultGraph));
      }
    }

    return actions;
  }

  public DataVaultModel getModel() {
    return model;
  }

  public HopGuiVaultGraph getVaultGraph() {
    return vaultGraph;
  }

  public DvNote getNote() {
    return note;
  }

  public Point getClick() {
    return click;
  }
}
