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
package org.apache.hop.datavault.workflow.actions.dimensionalpublish;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.publish.DvPublishModelSupport;
import org.apache.hop.datavault.metadata.dimensional.publish.DvPublishOptions;
import org.apache.hop.datavault.metadata.dimensional.publish.DvPublishResult;
import org.apache.hop.datavault.metadata.dimensional.publish.DvToDimensionalPublish;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/** Publishes a draft dimensional model (.hdm) from a Data Vault model (.hdv). */
@Action(
    id = "DIMENSIONAL_PUBLISH",
    name = "i18n::ActionDimensionalPublish.Name",
    description = "i18n::ActionDimensionalPublish.Description",
    image = "dimensional-model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionDimensionalPublish.Keywords",
    documentationUrl = "/workflow/actions/dimensionalpublish.html")
@GuiPlugin(description = "Dimensional Publish action")
@Getter
@Setter
public class ActionDimensionalPublish extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionDimensionalPublish.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DIMENSIONAL_PUBLISH_ACTION";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDimensionalPublish.DataVaultModel.Label",
      toolTip = "i18n::ActionDimensionalPublish.DataVaultModel.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dataVaultModelFile;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDimensionalPublish.OutputDimensionalModel.Label",
      toolTip = "i18n::ActionDimensionalPublish.OutputDimensionalModel.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String outputDimensionalModelFile;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalPublish.FailIfOutputExists.Label",
      toolTip = "i18n::ActionDimensionalPublish.FailIfOutputExists.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failIfOutputExists;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalPublish.LogWarnings.Label",
      toolTip = "i18n::ActionDimensionalPublish.LogWarnings.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean logWarnings = true;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalPublish.BridgeForTwoHubLinks.Label",
      toolTip = "i18n::ActionDimensionalPublish.BridgeForTwoHubLinks.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean bridgeForTwoHubLinks = true;

  public ActionDimensionalPublish(String name) {
    super(name, "");
  }

  public ActionDimensionalPublish() {
    this("");
  }

  public ActionDimensionalPublish(ActionDimensionalPublish meta) {
    super(meta.getName(), meta.getDescription());
    this.dataVaultModelFile = meta.dataVaultModelFile;
    this.outputDimensionalModelFile = meta.outputDimensionalModelFile;
    this.failIfOutputExists = meta.failIfOutputExists;
    this.logWarnings = meta.logWarnings;
    this.bridgeForTwoHubLinks = meta.bridgeForTwoHubLinks;
  }

  @Override
  public ActionDimensionalPublish clone() {
    return new ActionDimensionalPublish(this);
  }

  @Override
  public String getDialogClassName() {
    return ActionDimensionalPublishDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    try {
      String realDvFile = resolve(dataVaultModelFile);
      String realOutputFile = resolve(outputDimensionalModelFile);

      if (Utils.isEmpty(realDvFile)) {
        logError(BaseMessages.getString(PKG, "ActionDimensionalPublish.Error.NoDataVaultFile"));
        return result;
      }
      if (Utils.isEmpty(realOutputFile)) {
        logError(BaseMessages.getString(PKG, "ActionDimensionalPublish.Error.NoOutputFile"));
        return result;
      }

      FileObject outputFile = HopVfs.getFileObject(realOutputFile, getVariables());
      if (failIfOutputExists && outputFile.exists()) {
        logError(
            BaseMessages.getString(
                PKG, "ActionDimensionalPublish.Error.OutputExists", realOutputFile));
        return result;
      }

      DataVaultModel dvModel =
          DvPublishModelSupport.loadDataVaultModel(
              realDvFile, getVariables(), getMetadataProvider());

      DvPublishOptions options = DvPublishOptions.defaults();
      options.setBridgeForTwoHubLinks(bridgeForTwoHubLinks);
      DvPublishResult publishResult = DvToDimensionalPublish.publish(dvModel, options);
      DimensionalModel dimensionalModel = publishResult.getDimensionalModel();
      dimensionalModel.setFilename(realOutputFile);

      DvPublishModelSupport.saveDimensionalModel(
          dimensionalModel, realOutputFile, getVariables(), getMetadataProvider());

      if (logWarnings) {
        for (String warning : publishResult.getWarningsOrEmpty()) {
          logBasic(BaseMessages.getString(PKG, "ActionDimensionalPublish.Log.Warning", warning));
        }
      }

      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionDimensionalPublish.Log.Published",
              dvModel.getName(),
              dimensionalModel.getName(),
              realOutputFile,
              dimensionalModel.getTables().size(),
              publishResult.getWarningsOrEmpty().size()));

      result.setResult(true);
      result.setNrErrors(0);
      return result;
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionDimensionalPublish.Error.General"), e);
      result.setResult(false);
      result.setNrErrors(1);
      return result;
    }
  }

  @Override
  public String[] getReferencedObjectDescriptions() {
    return new String[] {
      BaseMessages.getString(PKG, "ActionDimensionalPublish.ReferencedObject.DvDescription"),
      BaseMessages.getString(PKG, "ActionDimensionalPublish.ReferencedObject.DmDescription"),
    };
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    return new boolean[] {
      StringUtils.isNotEmpty(dataVaultModelFile), StringUtils.isNotEmpty(outputDimensionalModelFile)
    };
  }

  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    if (index == 0) {
      return DvPublishModelSupport.loadDataVaultModel(
          variables.resolve(dataVaultModelFile), variables, metadataProvider);
    }
    if (index == 1 && !Utils.isEmpty(outputDimensionalModelFile)) {
      try {
        String realOutputFile = variables.resolve(outputDimensionalModelFile);
        FileObject file = HopVfs.getFileObject(realOutputFile, variables);
        if (!file.exists()) {
          return null;
        }
        return DvPublishModelSupport.loadDimensionalModel(
            realOutputFile, variables, metadataProvider);
      } catch (Exception e) {
        throw new HopException(
            BaseMessages.getString(PKG, "ActionDimensionalPublish.Error.General"), e);
      }
    }
    return null;
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  @Override
  public boolean isUnconditional() {
    return false;
  }

  @Override
  public boolean supportsDrillDown() {
    return true;
  }
}
