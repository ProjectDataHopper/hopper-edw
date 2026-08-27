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
package org.hopper.edw.catalog.hopgui.preview;

import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
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
import org.hopper.edw.catalog.hopgui.perspective.DataCatalogPerspective;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.datavault.hopgui.dialog.ShowRowsDialog;
import org.hopper.edw.datavault.metadata.DvSourcePreviewInputSupport;

/** Runs a record-definition preview using the standard Hop transform preview flow. */
public final class RecordDefinitionPreviewRunner {

  private static final Class<?> PKG = DataCatalogPerspective.class;

  private RecordDefinitionPreviewRunner() {}

  public static void run(
      Shell shell,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (!RecordDefinitionPreviewSupport.supportsPreview(definition)) {
      return;
    }

    try {
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

      DvSourcePreviewInputSupport.PreviewPipeline preview =
          RecordDefinitionPreviewSupport.buildPreviewPipeline(
              definition, previewVariables, metadataProvider, previewRows);

      PipelineMeta previewMeta = preview.pipelineMeta();
      String previewTransformName = preview.previewTransformName();

      PipelinePreviewProgressDialog progressDialog =
          new PipelinePreviewProgressDialog(
              shell,
              previewVariables,
              previewMeta,
              new String[] {previewTransformName},
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
                  BaseMessages.getString(PKG, "RecordDefinitionPreviewRunner.Preview.Title"),
                  BaseMessages.getString(
                      PKG,
                      "RecordDefinitionPreviewRunner.Preview.Message",
                      resolveDefinitionLabel(definition)),
                  progressDialog.getPreviewRowsMeta(previewTransformName),
                  progressDialog.getPreviewRows(previewTransformName))
              .open();
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "RecordDefinitionPreviewRunner.Error.Title"),
          BaseMessages.getString(PKG, "RecordDefinitionPreviewRunner.Error.Message"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private static String resolveDefinitionLabel(RecordDefinition definition) {
    if (definition == null) {
      return "";
    }
    RecordDefinitionKey key = definition.getKey();
    return key != null ? key.toString() : "";
  }
}
