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
package org.apache.hop.datavault.hopgui.file.vault;

import java.io.File;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.dimensional.publish.DvPublishModelSupport;
import org.apache.hop.datavault.metadata.dimensional.publish.DvPublishOptions;
import org.apache.hop.datavault.metadata.dimensional.publish.DvPublishResult;
import org.apache.hop.datavault.metadata.dimensional.publish.DvToDimensionalPublish;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;

/** GUI helper for publishing a Data Vault model to a draft dimensional model. */
public final class HopGuiDvDimensionalPublishSupport {

  private static final Class<?> PKG = HopGuiDvDimensionalPublishSupport.class;

  private HopGuiDvDimensionalPublishSupport() {}

  public static void publishDraftDimensionalModel(HopGui hopGui, DataVaultModel dataVaultModel) {
    if (hopGui == null || dataVaultModel == null) {
      return;
    }
    try {
      IVariables variables = hopGui.getVariables();
      IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();

      String proposedName =
          Const.NVL(dataVaultModel.getName(), "dimensional-model")
              + "-draft"
              + HopDimensionalFileType.DIMENSIONAL_FILE_EXTENSION;
      String proposedFilename = variables.getVariable("user.home") + File.separator + proposedName;

      String outputFilename =
          BaseDialog.presentFileDialog(
              true,
              hopGui.getShell(),
              null,
              variables,
              HopVfs.getFileObject(proposedFilename),
              new String[] {"*" + HopDimensionalFileType.DIMENSIONAL_FILE_EXTENSION},
              new String[] {HopDimensionalFileType.DIMENSIONAL_FILE_TYPE_DESCRIPTION},
              true);
      if (outputFilename == null) {
        return;
      }

      String realFilename = variables.resolve(outputFilename);
      if (HopVfs.getFileObject(realFilename).exists()) {
        MessageBox box = new MessageBox(hopGui.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
        box.setText(BaseMessages.getString(PKG, "HopGuiDvDimensionalPublishSupport.Exists.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiDvDimensionalPublishSupport.Exists.Message"));
        if ((box.open() & SWT.YES) == 0) {
          return;
        }
      }

      DvPublishOptions options = DvPublishOptions.defaults();
      DvPublishResult publishResult = DvToDimensionalPublish.publish(dataVaultModel, options);
      DvPublishModelSupport.saveDimensionalModel(
          publishResult.getDimensionalModel(), realFilename, variables, metadataProvider);

      StringBuilder message =
          new StringBuilder(
              BaseMessages.getString(
                  PKG,
                  "HopGuiDvDimensionalPublishSupport.Success.Message",
                  publishResult.getDimensionalModel().getName(),
                  realFilename,
                  publishResult.getDimensionalModel().getTables().size()));

      if (!publishResult.getWarningsOrEmpty().isEmpty()) {
        message.append(Const.CR).append(Const.CR);
        message.append(
            BaseMessages.getString(
                PKG, "HopGuiDvDimensionalPublishSupport.Success.WarningsHeader"));
        for (String warning : publishResult.getWarningsOrEmpty()) {
          message.append(Const.CR).append("- ").append(warning);
        }
      }

      MessageBox success =
          new MessageBox(
              hopGui.getShell(),
              publishResult.getWarningsOrEmpty().isEmpty()
                  ? SWT.OK | SWT.ICON_INFORMATION
                  : SWT.OK | SWT.ICON_WARNING);
      success.setText(
          BaseMessages.getString(PKG, "HopGuiDvDimensionalPublishSupport.Success.Title"));
      success.setMessage(message.toString());
      success.open();

      if (!Utils.isEmpty(realFilename)) {
        hopGui.fileDelegate.fileOpen(realFilename);
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDvDimensionalPublishSupport.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDvDimensionalPublishSupport.Error.Message"),
          e);
    }
  }
}
