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
package org.apache.hop.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmSourcePipelineSupport;
import org.apache.hop.datavault.metadata.pipeline.DvPipelineSourceSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.PreviewTableSettingsDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.pipeline.dialog.PipelinePreviewProgressDialog;
import org.eclipse.swt.widgets.Shell;

/** Preview and field discovery helpers for dimensional pipeline sources in Hop GUI dialogs. */
public final class DmSourcePipelineGuiSupport {

  private static final Class<?> PKG = DmSourcePipelineGuiSupport.class;

  private DmSourcePipelineGuiSupport() {}

  public static List<String> resolveFieldNames(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String pipelineFile,
      String transformName)
      throws HopException {
    DmSourceConfiguration source = new DmSourceConfiguration();
    source.setSourcePipelineFile(pipelineFile);
    source.setSourcePipelineTransform(transformName);
    IRowMeta rowMeta =
        DmSourcePipelineSupport.resolveSourceRowMeta(source, variables, metadataProvider);
    if (rowMeta == null || rowMeta.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.Error.NoFields"));
    }
    List<String> fieldNames = new ArrayList<>();
    for (int i = 0; i < rowMeta.size(); i++) {
      String name = rowMeta.getValueMeta(i).getName();
      if (!Utils.isEmpty(name)) {
        fieldNames.add(name);
      }
    }
    if (fieldNames.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.Error.NoFields"));
    }
    return fieldNames;
  }

  public static List<String> listTransformNames(
      IVariables variables, IHopMetadataProvider metadataProvider, String pipelineFile)
      throws HopException {
    String resolvedFile = variables != null ? variables.resolve(pipelineFile) : pipelineFile;
    return DmSourcePipelineSupport.listTransformNames(resolvedFile, variables, metadataProvider);
  }

  public static void previewSourcePipelineData(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String pipelineFile,
      String transformName) {
    try {
      validatePipelinePreviewInput(pipelineFile, transformName);

      PropsUi props = PropsUi.getInstance();
      int defaultRows = props.getDefaultPreviewSize();
      PreviewTableSettingsDialog settingsDialog =
          new PreviewTableSettingsDialog(shell, Math.max(1, defaultRows), variables, true);
      PreviewTableSettingsDialog.Settings settings = settingsDialog.open();
      if (settings == null) {
        return;
      }
      int previewRows = settings.rowLimit > 0 ? settings.rowLimit : Math.max(1, defaultRows);
      IVariables previewVariables = settingsDialog.getPreviewExecutionVariables();
      if (previewVariables == null) {
        Variables fallback = new Variables();
        if (variables != null) {
          fallback.copyFrom(variables);
        }
        previewVariables = fallback;
      }

      String resolvedFile = variables != null ? variables.resolve(pipelineFile) : pipelineFile;
      PipelineMeta sourcePipeline =
          DmSourcePipelineSupport.loadSourcePipelineMeta(resolvedFile, variables, metadataProvider);
      if (sourcePipeline.findTransform(transformName) == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DmSourcePipelineGuiSupport.Error.UnknownTransform",
                transformName,
                resolvedFile));
      }
      sourcePipeline.lookupReferencesAfterLoading();

      // PipelinePreviewProgressDialog does not copy parameter definitions onto the engine before
      // prepareExecution, so named-parameter defaults (e.g. RETAIL_CSV_WAVE=demo) never become
      // variables. Activate them on the preview variable space — same effect as Hop GUI
      // design-time preview after copyParametersFromDefinitions + activateParameters.
      DvPipelineSourceSupport.activatePipelineParameterDefaults(sourcePipeline, previewVariables);
      sourcePipeline.setInternalHopVariables(previewVariables);

      PipelinePreviewProgressDialog progressDialog =
          new PipelinePreviewProgressDialog(
              shell,
              previewVariables,
              sourcePipeline,
              new String[] {transformName},
              new int[] {previewRows});
      progressDialog.open();

      Pipeline pipeline = progressDialog.getPipeline();
      String loggingText = progressDialog.getLoggingText();

      if (!progressDialog.isCancelled()) {
        if (pipeline.getResult() != null && pipeline.getResult().getNrErrors() > 0) {
          EnterTextDialog etd =
              new EnterTextDialog(
                  shell,
                  BaseMessages.getString(Props.class, "System.Dialog.PreviewError.Title"),
                  BaseMessages.getString(Props.class, "System.Dialog.PreviewError.Message"),
                  loggingText,
                  true);
          etd.setReadOnly();
          etd.open();
        } else {
          new ShowRowsDialog(
                  shell,
                  variables,
                  BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.PreviewData.Title"),
                  BaseMessages.getString(
                      PKG,
                      "DmSourcePipelineGuiSupport.PreviewData.Message",
                      transformName,
                      resolvedFile),
                  progressDialog.getPreviewRowsMeta(transformName),
                  progressDialog.getPreviewRows(transformName))
              .open();
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.Error.PreviewDataTitle"),
          BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.Error.PreviewDataMessage"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  public static void previewSourcePipelineFields(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String pipelineFile,
      String transformName) {
    try {
      validatePipelinePreviewInput(pipelineFile, transformName);
      List<String> fieldNames =
          resolveFieldNames(variables, metadataProvider, pipelineFile, transformName);
      String message =
          String.join(
              System.lineSeparator(), fieldNames.stream().map(name -> "- " + name).toList());
      EnterTextDialog dialog =
          new EnterTextDialog(
              shell,
              BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.PreviewFields.Title"),
              BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.PreviewFields.Message"),
              message,
              true);
      dialog.setReadOnly();
      dialog.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.Error.PreviewTitle"),
          BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.Error.PreviewMessage"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private static void validatePipelinePreviewInput(String pipelineFile, String transformName)
      throws HopException {
    if (Utils.isEmpty(pipelineFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.Error.MissingPipelineFile"));
    }
    if (Utils.isEmpty(transformName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineGuiSupport.Error.MissingTransform"));
    }
  }
}
