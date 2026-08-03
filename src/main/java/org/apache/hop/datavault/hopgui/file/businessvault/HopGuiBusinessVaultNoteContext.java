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
package org.apache.hop.datavault.hopgui.file.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;

/** Context handler for clicks on a note in the Business Vault graph. */
public class HopGuiBusinessVaultNoteContext extends BaseGuiContextHandler
    implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiBusinessVaultNoteContext";

  private final BusinessVaultModel model;
  private final HopGuiBusinessVaultGraph businessVaultGraph;
  private final DvNote note;
  private final Point click;

  public HopGuiBusinessVaultNoteContext(
      BusinessVaultModel model,
      HopGuiBusinessVaultGraph businessVaultGraph,
      DvNote note,
      Point click) {
    this.model = model;
    this.businessVaultGraph = businessVaultGraph;
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
    GuiActionLambdaBuilder<HopGuiBusinessVaultNoteContext> lambdaBuilder =
        new GuiActionLambdaBuilder<>();
    List<GuiAction> pluginActions = getPluginActions(true);
    if (pluginActions != null) {
      for (GuiAction pluginAction : pluginActions) {
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

  public DvNote getNote() {
    return note;
  }

  public Point getClick() {
    return click;
  }
}
