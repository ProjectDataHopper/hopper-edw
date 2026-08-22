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
package org.apache.hop.datavault.workflow.actions.beginvaultupdate;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metrics.VaultUpdateExecutionSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/** Starts a correlated vault update run by assigning a workflow execution id. */
@Action(
    id = "BEGIN_VAULT_UPDATE",
    name = "i18n::ActionBeginVaultUpdate.Name",
    description = "i18n::ActionBeginVaultUpdate.Description",
    image = "datavault-model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionBeginVaultUpdate.Keywords",
    documentationUrl = "/workflow/actions/beginvaultupdate.html")
@GuiPlugin(description = "Begin Vault Update action")
@Getter
@Setter
public class ActionBeginVaultUpdate extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionBeginVaultUpdate.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "BEGIN_VAULT_UPDATE_ACTION";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionBeginVaultUpdate.ExecutionIdVariable.Label",
      toolTip = "i18n::ActionBeginVaultUpdate.ExecutionIdVariable.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String workflowExecutionIdVariable;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBeginVaultUpdate.UseWorkflowLogChannelId.Label",
      toolTip = "i18n::ActionBeginVaultUpdate.UseWorkflowLogChannelId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean useWorkflowLogChannelId;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBeginVaultUpdate.ReuseExistingExecutionId.Label",
      toolTip = "i18n::ActionBeginVaultUpdate.ReuseExistingExecutionId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean reuseExistingExecutionId;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBeginVaultUpdate.LogToWorkflow.Label",
      toolTip = "i18n::ActionBeginVaultUpdate.LogToWorkflow.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean logToWorkflow = true;

  public ActionBeginVaultUpdate() {
    super();
  }

  public ActionBeginVaultUpdate(ActionBeginVaultUpdate meta) {
    super(meta);
    this.workflowExecutionIdVariable = meta.workflowExecutionIdVariable;
    this.useWorkflowLogChannelId = meta.useWorkflowLogChannelId;
    this.reuseExistingExecutionId = meta.reuseExistingExecutionId;
    this.logToWorkflow = meta.logToWorkflow;
  }

  @Override
  public String getDialogClassName() {
    return ActionBeginVaultUpdateDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(true);
    result.setNrErrors(0);

    String executionId =
        VaultUpdateExecutionSupport.beginExecution(
            getParentWorkflow(),
            resolveExecutionIdVariable(),
            reuseExistingExecutionId,
            useWorkflowLogChannelId);
    if (Utils.isEmpty(executionId)) {
      if (useWorkflowLogChannelId) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "ActionBeginVaultUpdate.Error.MissingWorkflowLogChannelId"));
      }
      throw new HopException(
          BaseMessages.getString(PKG, "ActionBeginVaultUpdate.Error.MissingExecutionId"));
    }

    if (logToWorkflow) {
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionBeginVaultUpdate.Log.Started",
              executionId,
              resolveExecutionIdVariable()));
    }

    result.setLogText(
        BaseMessages.getString(PKG, "ActionBeginVaultUpdate.Result.Started", executionId));
    return result;
  }

  private String resolveExecutionIdVariable() {
    return Utils.isEmpty(workflowExecutionIdVariable)
        ? VaultUpdateExecutionSupport.defaultExecutionIdVariableName()
        : workflowExecutionIdVariable;
  }

  @Override
  public IAction clone() {
    return new ActionBeginVaultUpdate(this);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }
}
