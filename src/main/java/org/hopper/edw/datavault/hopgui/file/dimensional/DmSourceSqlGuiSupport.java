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
import org.apache.hop.core.Props;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.hopgui.dialog.ShowRowsDialog;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelinePreviewFactory;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.PreviewTableSettingsDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.pipeline.dialog.PipelinePreviewProgressDialog;
import org.eclipse.swt.widgets.Shell;

/** Preview and field discovery helpers for dimensional source SQL in Hop GUI dialogs. */
public final class DmSourceSqlGuiSupport {

  private static final Class<?> PKG = DmSourceSqlGuiSupport.class;
  private static final String PREVIEW_TRANSFORM_NAME = "Source SQL";

  private DmSourceSqlGuiSupport() {}

  public static List<String> resolveFieldNames(
      IVariables variables, DatabaseMeta databaseMeta, String sourceSql) throws HopException {
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.MissingConnection"));
    }
    String sql = variables != null ? variables.resolve(sourceSql) : sourceSql;
    if (Utils.isEmpty(sql)) {
      throw new HopException(BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.MissingSql"));
    }

    LoggingObject loggingObject = new LoggingObject("DmSourceSqlGuiSupport");
    try (Database database = new Database(loggingObject, variables, databaseMeta)) {
      database.connect();
      IRowMeta rowMeta = database.getQueryFields(sql, false);
      if (rowMeta == null || rowMeta.isEmpty()) {
        throw new HopException(BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.NoFields"));
      }
      List<String> fieldNames = new ArrayList<>();
      for (int i = 0; i < rowMeta.size(); i++) {
        String name = rowMeta.getValueMeta(i).getName();
        if (!Utils.isEmpty(name)) {
          fieldNames.add(name);
        }
      }
      if (fieldNames.isEmpty()) {
        throw new HopException(BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.NoFields"));
      }
      return fieldNames;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.ResolveFieldsFailed"), e);
    }
  }

  public static void previewSourceSql(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DatabaseMeta databaseMeta,
      String sourceSql) {
    try {
      if (databaseMeta == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.MissingConnection"));
      }
      String sql = variables != null ? variables.resolve(sourceSql) : sourceSql;
      if (Utils.isEmpty(sql)) {
        throw new HopException(
            BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.MissingSql"));
      }

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

      TableInputMeta tableInputMeta = new TableInputMeta();
      tableInputMeta.setConnection(databaseMeta.getName());
      DvSqlSupport.assignDisplaySql(tableInputMeta, sql);

      PipelineMeta previewMeta =
          PipelinePreviewFactory.generatePreviewPipeline(
              metadataProvider, tableInputMeta, PREVIEW_TRANSFORM_NAME);
      previewMeta.lookupReferencesAfterLoading();

      PipelinePreviewProgressDialog progressDialog =
          new PipelinePreviewProgressDialog(
              shell,
              previewVariables,
              previewMeta,
              new String[] {PREVIEW_TRANSFORM_NAME},
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
                  BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.PreviewData.Title"),
                  BaseMessages.getString(
                      PKG, "DmSourceSqlGuiSupport.PreviewData.Message", databaseMeta.getName()),
                  progressDialog.getPreviewRowsMeta(PREVIEW_TRANSFORM_NAME),
                  progressDialog.getPreviewRows(PREVIEW_TRANSFORM_NAME))
              .open();
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.PreviewTitle"),
          BaseMessages.getString(PKG, "DmSourceSqlGuiSupport.Error.PreviewMessage"),
          e instanceof HopException ? e : new HopException(e));
    }
  }
}
