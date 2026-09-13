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
package org.hopper.edw.datavault.hopgui.ai;

import org.apache.hop.ai.advisor.AiAdvisorOpenRequest;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.edw.datavault.ai.DataVaultAiAdvisor;
import org.hopper.edw.datavault.ai.EdwAiAdvisorLocations;
import org.hopper.edw.datavault.ai.businessvault.BusinessVaultAiAdvisor;
import org.hopper.edw.datavault.ai.dimensional.DimensionalAiAdvisor;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;

/** Opens the Hop AI Assistant workbench bound to an EDW model. */
public final class EdwAiAdvisorOpenSupport {

  private static final Class<?> PKG = EdwAiAdvisorOpenSupport.class;

  private EdwAiAdvisorOpenSupport() {}

  public static void openDataVault(HopGui hopGui, DataVaultModel model, String focusNodeName) {
    if (model == null) {
      return;
    }
    AiAdvisorOpenRequest request = new AiAdvisorOpenRequest();
    request.setAdvisorPluginId(DataVaultAiAdvisor.ID);
    request.setLocation(EdwAiAdvisorLocations.DATA_VAULT_GRAPH);
    request.setAreaLabel(BaseMessages.getString(PKG, "EdwAiAdvisorOpenSupport.Area.DataVault"));
    request.setPreferFloatingWindow(true);
    request.setArtifact(model);
    request.setArtifactName(model.getName());
    request.setArtifactKind("data-vault");
    request.setTitle(model.getName());
    request.setFocusNodeName(focusNodeName);
    open(hopGui, request);
  }

  public static void openBusinessVault(
      HopGui hopGui, BusinessVaultModel model, String focusNodeName) {
    if (model == null) {
      return;
    }
    AiAdvisorOpenRequest request = new AiAdvisorOpenRequest();
    request.setAdvisorPluginId(BusinessVaultAiAdvisor.ID);
    request.setLocation(EdwAiAdvisorLocations.BUSINESS_VAULT_GRAPH);
    request.setAreaLabel(BaseMessages.getString(PKG, "EdwAiAdvisorOpenSupport.Area.BusinessVault"));
    request.setPreferFloatingWindow(true);
    request.setArtifact(model);
    request.setArtifactName(model.getName());
    request.setArtifactKind("business-vault");
    request.setTitle(model.getName());
    request.setFocusNodeName(focusNodeName);
    open(hopGui, request);
  }

  public static void openDimensional(HopGui hopGui, DimensionalModel model, String focusNodeName) {
    if (model == null) {
      return;
    }
    AiAdvisorOpenRequest request = new AiAdvisorOpenRequest();
    request.setAdvisorPluginId(DimensionalAiAdvisor.ID);
    request.setLocation(EdwAiAdvisorLocations.DIMENSIONAL_GRAPH);
    request.setAreaLabel(BaseMessages.getString(PKG, "EdwAiAdvisorOpenSupport.Area.Dimensional"));
    request.setPreferFloatingWindow(true);
    request.setArtifact(model);
    request.setArtifactName(model.getName());
    request.setArtifactKind("dimensional");
    request.setTitle(model.getName());
    request.setFocusNodeName(focusNodeName);
    open(hopGui, request);
  }

  private static void open(HopGui hopGui, AiAdvisorOpenRequest request) {
    if (hopGui == null) {
      return;
    }
    try {
      hopGui.openAiAdvisorSession(request);
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "EdwAiAdvisorOpenSupport.Error.Title"),
          BaseMessages.getString(PKG, "EdwAiAdvisorOpenSupport.Error.Message"),
          e);
    }
  }
}
