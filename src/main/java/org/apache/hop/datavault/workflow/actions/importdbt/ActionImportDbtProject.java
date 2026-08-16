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
package org.apache.hop.datavault.workflow.actions.importdbt;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.dbt.DbtImportConflictPolicy;
import org.apache.hop.datavault.dbt.DbtImportDestination;
import org.apache.hop.datavault.dbt.DbtImportOptions;
import org.apache.hop.datavault.dbt.DbtImportResult;
import org.apache.hop.datavault.dbt.DbtImportService;
import org.apache.hop.datavault.dbt.DbtModelDraft;
import org.apache.hop.datavault.dbt.DbtProjectParser;
import org.apache.hop.datavault.dbt.DbtProjectScan;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.metadata.ModelXmlWriteSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvSqlModelPathSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * Headless dbt-core import: scan a project and write SQL business tables into one or more {@code
 * .hbv} files (and optionally a Jinja macro library).
 */
@Action(
    id = "IMPORT_DBT_PROJECT",
    name = "i18n::ActionImportDbtProject.Name",
    description = "i18n::ActionImportDbtProject.Description",
    image = "jinja-macro-library.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionImportDbtProject.Keywords",
    documentationUrl = "/workflow/actions/importdbtproject.html")
@GuiPlugin(description = "Import dbt project action")
@Getter
@Setter
public class ActionImportDbtProject extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionImportDbtProject.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "IMPORT_DBT_PROJECT_ACTION";

  public static final String RESULT_ATTR_REPORT = "dbtImportReportText";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionImportDbtProject.ProjectFolder.Label",
      toolTip = "i18n::ActionImportDbtProject.ProjectFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dbtProjectFolder;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::ActionImportDbtProject.TargetHbv.Label",
      toolTip = "i18n::ActionImportDbtProject.TargetHbv.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String targetHbvFilename;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getDestinationOptions",
      label = "i18n::ActionImportDbtProject.Destination.Label",
      toolTip = "i18n::ActionImportDbtProject.Destination.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String destination = DbtImportDestination.CURRENT_MODEL.name();

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionImportDbtProject.OutputFolder.Label",
      toolTip = "i18n::ActionImportDbtProject.OutputFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String outputFolder;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getConflictOptions",
      label = "i18n::ActionImportDbtProject.Conflict.Label",
      toolTip = "i18n::ActionImportDbtProject.Conflict.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String conflictPolicy = DbtImportConflictPolicy.SKIP.name();

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionImportDbtProject.ImportMacros.Label",
      toolTip = "i18n::ActionImportDbtProject.ImportMacros.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean importMacros = true;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionImportDbtProject.LibraryName.Label",
      toolTip = "i18n::ActionImportDbtProject.LibraryName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String libraryName;

  public ActionImportDbtProject() {
    super();
  }

  public ActionImportDbtProject(String name) {
    super(name, "");
  }

  @Override
  public Object clone() {
    return super.clone();
  }

  @Override
  public String getDialogClassName() {
    return ActionImportDbtProjectDialog.class.getName();
  }

  public List<String> getDestinationOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        DbtImportDestination.CURRENT_MODEL.name(),
        DbtImportDestination.NEW_MODEL.name(),
        DbtImportDestination.SPLIT_BY_FOLDER.name());
  }

  public List<String> getConflictOptions(ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        DbtImportConflictPolicy.SKIP.name(), DbtImportConflictPolicy.REPLACE.name());
  }

  @Override
  public Result execute(Result prevResult, int nr) throws HopException {
    Result result = prevResult != null ? prevResult : new Result();
    result.setResult(false);
    result.setNrErrors(0);

    String projectFolder = resolve(dbtProjectFolder);
    if (Utils.isEmpty(projectFolder)) {
      logError(BaseMessages.getString(PKG, "ActionImportDbtProject.Error.MissingProject"));
      result.setNrErrors(1);
      return result;
    }

    DbtImportDestination dest = parseDestination(resolve(destination));
    String targetHbv = resolve(targetHbvFilename);
    String outFolder = resolve(outputFolder);
    if (dest == DbtImportDestination.SPLIT_BY_FOLDER && Utils.isEmpty(outFolder)) {
      logError(BaseMessages.getString(PKG, "ActionImportDbtProject.Error.MissingOutputFolder"));
      result.setNrErrors(1);
      return result;
    }
    if (dest != DbtImportDestination.SPLIT_BY_FOLDER && Utils.isEmpty(targetHbv)) {
      logError(BaseMessages.getString(PKG, "ActionImportDbtProject.Error.MissingTargetHbv"));
      result.setNrErrors(1);
      return result;
    }

    DbtProjectScan scan;
    try {
      scan = DbtProjectParser.scan(projectFolder);
    } catch (HopException e) {
      logError(
          BaseMessages.getString(PKG, "ActionImportDbtProject.Error.ScanFailed", e.getMessage()),
          e);
      result.setNrErrors(1);
      return result;
    }

    DbtImportOptions options = new DbtImportOptions();
    options.setScan(scan);
    options.setProjectRoot(scan.getProjectRoot());
    options.setDestination(dest);
    options.setConflictPolicy(parseConflict(resolve(conflictPolicy)));
    options.setImportMacros(importMacros);
    options.setLibraryName(resolve(libraryName));
    options.setOutputFolder(outFolder);
    options.setNewModelFilename(targetHbv);
    options.setMetadataProvider(getMetadataProvider());
    options.setVariables(this);
    for (DbtModelDraft draft : scan.getModels()) {
      if (draft != null && draft.isImportable()) {
        options.getSelectedModels().add(draft);
      }
    }

    if (dest == DbtImportDestination.CURRENT_MODEL) {
      options.setCurrentModel(loadOrCreateModel(targetHbv));
    }

    try {
      DbtImportResult imported = DbtImportService.apply(options);
      if (dest == DbtImportDestination.CURRENT_MODEL && options.getCurrentModel() != null) {
        BusinessVaultModel model = options.getCurrentModel();
        model.setFilename(targetHbv);
        ModelXmlWriteSupport.writeModelXml(
            HopBusinessVaultFileType.XML_TAG, model, targetHbv, this);
        imported.getWrittenModelFiles().add(targetHbv);
      }
      String report = imported.reportText();
      logBasic(report);
      getExtensionDataMap().put(RESULT_ATTR_REPORT, report);
      if (getParentWorkflow() != null) {
        getParentWorkflow().getExtensionDataMap().put(RESULT_ATTR_REPORT, report);
      }
      result.setResult(true);
      return result;
    } catch (HopException e) {
      logError(
          BaseMessages.getString(PKG, "ActionImportDbtProject.Error.ImportFailed", e.getMessage()),
          e);
      result.setNrErrors(1);
      return result;
    }
  }

  BusinessVaultModel loadOrCreateModel(String filename) throws HopException {
    if (HopVfs.fileExists(filename)) {
      return BvSqlModelPathSupport.loadBusinessVaultModelUncached(filename, getMetadataProvider());
    }
    BusinessVaultModel model = new BusinessVaultModel();
    String base = filename.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    String name = slash >= 0 ? base.substring(slash + 1) : base;
    if (name.toLowerCase().endsWith(".hbv")) {
      name = name.substring(0, name.length() - 4);
    }
    model.setName(name);
    model.setFilename(filename);
    return model;
  }

  static DbtImportDestination parseDestination(String raw) {
    if (Utils.isEmpty(raw)) {
      return DbtImportDestination.CURRENT_MODEL;
    }
    try {
      return DbtImportDestination.valueOf(raw.trim());
    } catch (IllegalArgumentException e) {
      return DbtImportDestination.CURRENT_MODEL;
    }
  }

  static DbtImportConflictPolicy parseConflict(String raw) {
    if (Utils.isEmpty(raw)) {
      return DbtImportConflictPolicy.SKIP;
    }
    try {
      return DbtImportConflictPolicy.valueOf(raw.trim());
    } catch (IllegalArgumentException e) {
      return DbtImportConflictPolicy.SKIP;
    }
  }
}
