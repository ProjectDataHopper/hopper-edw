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
package org.hopper.edw.catalog.harvest;

import org.hopper.edw.catalog.harvest.SchemaHarvestCatalogApplySupport.ApplyOptions;
import org.hopper.edw.catalog.harvest.SchemaHarvestCatalogApplySupport.ApplyResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestSourceModelGenerator.GenerateOptions;
import org.hopper.edw.catalog.harvest.SchemaHarvestSourceModelGenerator.GenerateResult;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryPublisher;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HistoryConnection;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 * GUI entry points for applying harvest results to catalog contracts and generating .hsm models.
 */
public final class SchemaHarvestApplyGuiSupport {

  private static final Class<?> PKG = SchemaHarvestApplyGuiSupport.class;

  private SchemaHarvestApplyGuiSupport() {}

  public static void applyCatalogFksFromHarvest(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    try {
      HarvestResult harvest = loadHarvestForGroup(hopGui, group);
      if (harvest == null) {
        return;
      }
      String[] options =
          new String[] {
            BaseMessages.getString(PKG, "SchemaHarvestApply.Catalog.Option.FksOnly"),
            BaseMessages.getString(PKG, "SchemaHarvestApply.Catalog.Option.FieldsAndFks")
          };
      EnterSelectionDialog choice =
          new EnterSelectionDialog(
              shell,
              options,
              BaseMessages.getString(PKG, "SchemaHarvestApply.Catalog.Title"),
              BaseMessages.getString(
                  PKG, "SchemaHarvestApply.Catalog.Message", harvest.getHarvestRunId()));
      String selected = choice.open();
      if (selected == null) {
        return;
      }
      ApplyOptions applyOptions =
          selected.equals(options[1]) ? ApplyOptions.fieldsAndFks() : ApplyOptions.fksOnly();
      String catalogConnection = Const.NVL(group.getDataCatalogConnection(), "");
      if (!Utils.isEmpty(catalogConnection)) {
        catalogConnection = hopGui.getVariables().resolve(catalogConnection);
      }
      ApplyResult result =
          SchemaHarvestCatalogApplySupport.apply(
              harvest,
              catalogConnection,
              applyOptions,
              hopGui.getVariables(),
              hopGui.getMetadataProvider());
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "SchemaHarvestApply.Catalog.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG,
              "SchemaHarvestApply.Catalog.Result",
              Integer.toString(result.subjectsUpdated()),
              Integer.toString(result.foreignKeyConstraintsApplied()),
              Integer.toString(result.fieldsUpdated()),
              Integer.toString(result.subjectsSkipped())));
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestApply.Error.Title"),
          BaseMessages.getString(PKG, "SchemaHarvestApply.Error.Message"),
          e);
    }
  }

  public static void generateHsmFromHarvest(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    try {
      HarvestResult harvest = loadHarvestForGroup(hopGui, group);
      if (harvest == null) {
        return;
      }
      String defaultPath =
          "${PROJECT_HOME}/models/"
              + Const.NVL(group.getName(), "sources").replace(' ', '-')
              + "-from-harvest.hsm";
      EnterStringDialog pathDialog =
          new EnterStringDialog(
              shell,
              defaultPath,
              BaseMessages.getString(PKG, "SchemaHarvestApply.Hsm.Title"),
              BaseMessages.getString(PKG, "SchemaHarvestApply.Hsm.PathPrompt"),
              true,
              hopGui.getVariables());
      String path = pathDialog.open();
      if (Utils.isEmpty(path)) {
        return;
      }
      path = hopGui.getVariables().resolve(path.trim());

      SourceModel existing = null;
      try {
        if (org.apache.hop.core.vfs.HopVfs.fileExists(path)) {
          existing =
              SourceModelLoadSupport.load(
                  path, hopGui.getVariables(), hopGui.getMetadataProvider());
        }
      } catch (Exception ignored) {
        existing = null;
      }

      GenerateResult generated =
          SchemaHarvestSourceModelGenerator.generate(
              harvest,
              existing,
              new GenerateOptions(
                  existing != null,
                  true,
                  group.getName() + "-sources",
                  "From harvest " + harvest.getHarvestRunId()));
      SchemaHarvestSourceModelGenerator.save(generated.model(), path, hopGui.getVariables());

      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "SchemaHarvestApply.Hsm.Title"));
      String warnings =
          generated.warnings().isEmpty()
              ? ""
              : "\n"
                  + BaseMessages.getString(
                      PKG,
                      "SchemaHarvestApply.Hsm.Warnings",
                      Integer.toString(generated.warnings().size()));
      box.setMessage(
          BaseMessages.getString(
                  PKG,
                  "SchemaHarvestApply.Hsm.Result",
                  path,
                  Integer.toString(generated.tablesAdded()),
                  Integer.toString(generated.tablesUpdated()),
                  Integer.toString(generated.relationshipsAdded()))
              + warnings);
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestApply.Error.Title"),
          BaseMessages.getString(PKG, "SchemaHarvestApply.Error.Message"),
          e);
    }
  }

  private static HarvestResult loadHarvestForGroup(HopGui hopGui, ResourceDefinitionGroupMeta group)
      throws HopException {
    IVariables variables = hopGui.getVariables();
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
    String catalogConnection = Const.NVL(group.getDataCatalogConnection(), "");
    if (!Utils.isEmpty(catalogConnection)) {
      catalogConnection = variables.resolve(catalogConnection);
    }
    HistoryConnection history =
        SchemaHarvestHistoryReader.resolveConnection(
            null, null, catalogConnection, variables, metadataProvider);
    if (history == null) {
      info(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "SchemaHarvestApply.Error.Title"),
          SchemaHarvestHistoryReader.MSG_NOT_CONFIGURED);
      return null;
    }
    DatabaseMeta databaseMeta =
        SchemaHarvestHistoryReader.loadDatabaseMeta(history.databaseMetaName(), metadataProvider);
    if (databaseMeta == null) {
      throw new HopException("Harvest history database not found: " + history.databaseMetaName());
    }

    String runId = variables.getVariable(SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_RUN_ID);
    if (Utils.isEmpty(runId)) {
      String resolved =
          variables.resolve("${" + SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_RUN_ID + "}");
      if (!Utils.isEmpty(resolved) && !resolved.contains("${")) {
        runId = resolved;
      }
    }
    if (Utils.isEmpty(runId)) {
      runId =
          SchemaHarvestHistoryReader.findLatestRunId(
              databaseMeta, history.schemaName(), group.getName(), variables);
    }
    if (Utils.isEmpty(runId)) {
      info(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "SchemaHarvestApply.Error.Title"),
          SchemaHarvestHistoryReader.MSG_NO_HISTORY);
      return null;
    }
    return SchemaHarvestHistoryReader.loadHarvestResult(
        databaseMeta, history.schemaName(), runId, variables);
  }

  private static void info(Shell shell, String title, String message) {
    MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
    box.setText(title);
    box.setMessage(message);
    box.open();
  }
}
