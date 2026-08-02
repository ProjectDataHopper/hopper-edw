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

package org.apache.hop.datavault.hopgui.resourcedefinition;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.versioning.CatalogVersionEntry;
import org.apache.hop.catalog.versioning.CatalogVersionService;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFileWriter.ReportFormat;
import org.apache.hop.datavault.resourcedefinition.ValidationOptions;
import org.apache.hop.datavault.resourcedefinition.ValidationOptions.BaselineKind;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Asks the DV administrator what baseline (truth) and which check axes to use before validating a
 * resource definition group. Validation itself never mutates the baseline.
 */
public final class ResourceDefinitionValidationOptionsDialog {

  private static final Class<?> PKG = ResourceDefinitionValidationOptionsDialog.class;

  /** Last choices in this Hop GUI session (re-validate convenience). */
  private static ValidationOptions lastOptions;

  private final Shell parent;
  private final HopGui hopGui;
  private final ResourceDefinitionGroupMeta group;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  private Shell shell;
  private Button wBaselineWorking;
  private Button wBaselineVersion;
  private ComboVar wBaselineTag;
  private Button wCheckLive;
  private Button wCheckCatalogVersion;
  private Button wCheckModels;
  private Button wCheckTargetDb;
  private Button wExpectAutoCreate;
  private Button wIncludeImpact;
  private Button wWriteReport;
  private TextVar wReportPath;
  private TextVar wReportBaseName;
  private ValidationOptions result;

  public ResourceDefinitionValidationOptionsDialog(
      Shell parent, HopGui hopGui, ResourceDefinitionGroupMeta group) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.group = group;
    this.variables = hopGui != null ? hopGui.getVariables() : null;
    this.metadataProvider = hopGui != null ? hopGui.getMetadataProvider() : null;
  }

  /**
   * @return chosen options, or {@code null} if cancelled
   */
  public ValidationOptions open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle() | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            "ResourceDefinitionValidationOptionsDialog.Shell.Title",
            group != null ? Const.NVL(group.getName(), "") : ""));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> ok());

    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.RESOURCE_DEFINITION_VALIDATION_OPTIONS);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wValidate, wCancel}, margin, null);

    Label wlIntro = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlIntro);
    wlIntro.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Intro"));
    FormData fdlIntro = new FormData();
    fdlIntro.left = new FormAttachment(0, margin);
    fdlIntro.right = new FormAttachment(100, -margin);
    fdlIntro.top = new FormAttachment(0, margin);
    wlIntro.setLayoutData(fdlIntro);

    Group gBaseline = new Group(shell, SWT.SHADOW_ETCHED_IN);
    PropsUi.setLook(gBaseline);
    gBaseline.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Baseline.Group"));
    gBaseline.setLayout(new FormLayout());
    FormData fdBaseline = new FormData();
    fdBaseline.left = new FormAttachment(0, margin);
    fdBaseline.right = new FormAttachment(100, -margin);
    fdBaseline.top = new FormAttachment(wlIntro, margin);
    gBaseline.setLayoutData(fdBaseline);

    wBaselineWorking = new Button(gBaseline, SWT.RADIO);
    PropsUi.setLook(wBaselineWorking);
    wBaselineWorking.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Baseline.Working.Label"));
    FormData fdWorking = new FormData();
    fdWorking.left = new FormAttachment(0, margin);
    fdWorking.top = new FormAttachment(0, margin);
    fdWorking.right = new FormAttachment(100, -margin);
    wBaselineWorking.setLayoutData(fdWorking);

    wBaselineVersion = new Button(gBaseline, SWT.RADIO);
    PropsUi.setLook(wBaselineVersion);
    wBaselineVersion.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Baseline.Version.Label"));
    FormData fdVersion = new FormData();
    fdVersion.left = new FormAttachment(0, margin);
    fdVersion.top = new FormAttachment(wBaselineWorking, margin);
    fdVersion.right = new FormAttachment(100, -margin);
    wBaselineVersion.setLayoutData(fdVersion);

    Label wlTag = new Label(gBaseline, SWT.RIGHT);
    PropsUi.setLook(wlTag);
    wlTag.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Baseline.Tag.Label"));
    FormData fdlTag = new FormData();
    fdlTag.left = new FormAttachment(0, margin);
    fdlTag.top = new FormAttachment(wBaselineVersion, margin);
    wlTag.setLayoutData(fdlTag);

    wBaselineTag = new ComboVar(variables, gBaseline, SWT.BORDER);
    PropsUi.setLook(wBaselineTag);
    FormData fdTag = new FormData();
    fdTag.left = new FormAttachment(wlTag, margin);
    fdTag.top = new FormAttachment(wBaselineVersion, margin);
    fdTag.right = new FormAttachment(100, -margin);
    wBaselineTag.setLayoutData(fdTag);

    Label wlBaselineNote = new Label(gBaseline, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlBaselineNote);
    wlBaselineNote.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Baseline.Note"));
    FormData fdlNote = new FormData();
    fdlNote.left = new FormAttachment(0, margin);
    fdlNote.right = new FormAttachment(100, -margin);
    fdlNote.top = new FormAttachment(wBaselineTag, margin);
    fdlNote.bottom = new FormAttachment(100, -margin);
    wlBaselineNote.setLayoutData(fdlNote);

    Group gAxes = new Group(shell, SWT.SHADOW_ETCHED_IN);
    PropsUi.setLook(gAxes);
    gAxes.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Axes.Group"));
    gAxes.setLayout(new FormLayout());
    FormData fdAxes = new FormData();
    fdAxes.left = new FormAttachment(0, margin);
    fdAxes.right = new FormAttachment(100, -margin);
    fdAxes.top = new FormAttachment(gBaseline, margin);
    gAxes.setLayoutData(fdAxes);

    wCheckLive = new Button(gAxes, SWT.CHECK);
    PropsUi.setLook(wCheckLive);
    wCheckLive.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Axes.Live.Label"));
    FormData fdLive = new FormData();
    fdLive.left = new FormAttachment(0, margin);
    fdLive.top = new FormAttachment(0, margin);
    fdLive.right = new FormAttachment(100, -margin);
    wCheckLive.setLayoutData(fdLive);

    wCheckCatalogVersion = new Button(gAxes, SWT.CHECK);
    PropsUi.setLook(wCheckCatalogVersion);
    wCheckCatalogVersion.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Axes.CatalogVersion.Label"));
    FormData fdCatVer = new FormData();
    fdCatVer.left = new FormAttachment(0, margin);
    fdCatVer.top = new FormAttachment(wCheckLive, margin / 2);
    fdCatVer.right = new FormAttachment(100, -margin);
    wCheckCatalogVersion.setLayoutData(fdCatVer);

    wCheckModels = new Button(gAxes, SWT.CHECK);
    PropsUi.setLook(wCheckModels);
    wCheckModels.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Axes.Models.Label"));
    FormData fdModels = new FormData();
    fdModels.left = new FormAttachment(0, margin);
    fdModels.top = new FormAttachment(wCheckCatalogVersion, margin / 2);
    fdModels.right = new FormAttachment(100, -margin);
    wCheckModels.setLayoutData(fdModels);

    wCheckTargetDb = new Button(gAxes, SWT.CHECK);
    PropsUi.setLook(wCheckTargetDb);
    wCheckTargetDb.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Axes.TargetDb.Label"));
    FormData fdTargetDb = new FormData();
    fdTargetDb.left = new FormAttachment(0, margin);
    fdTargetDb.top = new FormAttachment(wCheckModels, margin / 2);
    fdTargetDb.right = new FormAttachment(100, -margin);
    wCheckTargetDb.setLayoutData(fdTargetDb);

    wExpectAutoCreate = new Button(gAxes, SWT.CHECK);
    PropsUi.setLook(wExpectAutoCreate);
    wExpectAutoCreate.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Axes.ExpectAutoCreate.Label"));
    wExpectAutoCreate.setToolTipText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Axes.ExpectAutoCreate.ToolTip"));
    FormData fdAutoCreate = new FormData();
    fdAutoCreate.left = new FormAttachment(0, margin * 3);
    fdAutoCreate.top = new FormAttachment(wCheckTargetDb, margin / 2);
    fdAutoCreate.right = new FormAttachment(100, -margin);
    wExpectAutoCreate.setLayoutData(fdAutoCreate);

    wIncludeImpact = new Button(gAxes, SWT.CHECK);
    PropsUi.setLook(wIncludeImpact);
    wIncludeImpact.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Axes.Impact.Label"));
    FormData fdImpact = new FormData();
    fdImpact.left = new FormAttachment(0, margin);
    fdImpact.top = new FormAttachment(wExpectAutoCreate, margin / 2);
    fdImpact.right = new FormAttachment(100, -margin);
    fdImpact.bottom = new FormAttachment(100, -margin);
    wIncludeImpact.setLayoutData(fdImpact);

    Group gReport = new Group(shell, SWT.SHADOW_ETCHED_IN);
    PropsUi.setLook(gReport);
    gReport.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Report.Group"));
    gReport.setLayout(new FormLayout());
    FormData fdReport = new FormData();
    fdReport.left = new FormAttachment(0, margin);
    fdReport.right = new FormAttachment(100, -margin);
    fdReport.top = new FormAttachment(gAxes, margin);
    fdReport.bottom = new FormAttachment(wValidate, -2 * margin);
    gReport.setLayoutData(fdReport);

    wWriteReport = new Button(gReport, SWT.CHECK);
    PropsUi.setLook(wWriteReport);
    wWriteReport.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Report.Write.Label"));
    FormData fdWrite = new FormData();
    fdWrite.left = new FormAttachment(0, margin);
    fdWrite.top = new FormAttachment(0, margin);
    fdWrite.right = new FormAttachment(100, -margin);
    wWriteReport.setLayoutData(fdWrite);

    Label wlPath = new Label(gReport, SWT.RIGHT);
    PropsUi.setLook(wlPath);
    wlPath.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationOptionsDialog.Report.Path.Label"));
    FormData fdlPath = new FormData();
    fdlPath.left = new FormAttachment(0, margin);
    fdlPath.top = new FormAttachment(wWriteReport, margin);
    wlPath.setLayoutData(fdlPath);

    wReportPath = new TextVar(variables, gReport, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wReportPath);
    FormData fdPath = new FormData();
    fdPath.left = new FormAttachment(wlPath, margin);
    fdPath.top = new FormAttachment(wWriteReport, margin);
    fdPath.right = new FormAttachment(100, -margin);
    wReportPath.setLayoutData(fdPath);

    Label wlBase = new Label(gReport, SWT.RIGHT);
    PropsUi.setLook(wlBase);
    wlBase.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationOptionsDialog.Report.BaseName.Label"));
    FormData fdlBase = new FormData();
    fdlBase.left = new FormAttachment(0, margin);
    fdlBase.top = new FormAttachment(wReportPath, margin);
    wlBase.setLayoutData(fdlBase);

    wReportBaseName = new TextVar(variables, gReport, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wReportBaseName);
    FormData fdBase = new FormData();
    fdBase.left = new FormAttachment(wlBase, margin);
    fdBase.top = new FormAttachment(wReportPath, margin);
    fdBase.right = new FormAttachment(100, -margin);
    fdBase.bottom = new FormAttachment(100, -margin);
    wReportBaseName.setLayoutData(fdBase);

    SelectionAdapter enableListener =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            enableFields();
          }
        };
    wBaselineWorking.addSelectionListener(enableListener);
    wBaselineVersion.addSelectionListener(enableListener);
    wWriteReport.addSelectionListener(enableListener);
    wCheckTargetDb.addSelectionListener(enableListener);

    populateVersionTags();
    applyInitialValues(lastOptions != null ? lastOptions : ValidationOptions.defaults());
    enableFields();

    shell.setMinimumSize(560, 520);
    BaseTransformDialog.setSize(shell, 640, 600);
    shell.open();
    while (!shell.isDisposed()) {
      if (!shell.getDisplay().readAndDispatch()) {
        shell.getDisplay().sleep();
      }
    }
    return result;
  }

  public static ValidationOptions lastOptions() {
    return lastOptions;
  }

  private void populateVersionTags() {
    List<String> tags = new ArrayList<>();
    try {
      String connection = resolveCatalogConnection();
      if (!Utils.isEmpty(connection) && variables != null && metadataProvider != null) {
        for (CatalogVersionEntry entry :
            CatalogVersionService.listVersions(connection, variables, metadataProvider)) {
          if (entry != null && !Utils.isEmpty(entry.getTag())) {
            tags.add(entry.getTag().trim());
          }
        }
      }
    } catch (Exception ignored) {
      // Combo stays empty; admin can still type a tag.
    }
    wBaselineTag.setItems(tags.toArray(new String[0]));
  }

  private String resolveCatalogConnection() {
    if (group != null && !Utils.isEmpty(group.getDataCatalogConnection())) {
      return variables != null
          ? variables.resolve(group.getDataCatalogConnection())
          : group.getDataCatalogConnection();
    }
    try {
      return DvSourceCatalogService.resolvePreferredCatalogConnection(
          null, variables, metadataProvider);
    } catch (Exception e) {
      return null;
    }
  }

  private void applyInitialValues(ValidationOptions options) {
    if (options == null) {
      options = ValidationOptions.defaults();
    }
    if (options.baselineKind() == BaselineKind.CATALOG_VERSION) {
      wBaselineVersion.setSelection(true);
      wBaselineWorking.setSelection(false);
    } else {
      wBaselineWorking.setSelection(true);
      wBaselineVersion.setSelection(false);
    }
    wBaselineTag.setText(Const.NVL(options.baselineVersionTag(), ""));
    wCheckLive.setSelection(options.checkLiveSources());
    wCheckCatalogVersion.setSelection(options.checkCatalogVsVersion());
    wCheckModels.setSelection(options.checkTargetModels());
    wCheckTargetDb.setSelection(options.checkTargetDatabases());
    wExpectAutoCreate.setSelection(options.expectAutomaticTargetTableCreation());
    wIncludeImpact.setSelection(options.includeImpact());
    wWriteReport.setSelection(options.writeReport());
    wReportPath.setText(
        Const.NVL(
            options.reportOutputPath(),
            "${PROJECT_HOME}/work/reports"));
    wReportBaseName.setText(
        Const.NVL(
            options.reportFileBaseName(),
            group != null && !Utils.isEmpty(group.getName())
                ? group.getName() + "-schema-validation"
                : "schema-validation"));
  }

  private void enableFields() {
    boolean versionBaseline = wBaselineVersion.getSelection();
    wBaselineTag.setEnabled(versionBaseline || wCheckCatalogVersion.getSelection());
    boolean write = wWriteReport.getSelection();
    wReportPath.setEnabled(write);
    wReportBaseName.setEnabled(write);
    boolean targetDb = wCheckTargetDb.getSelection();
    wExpectAutoCreate.setEnabled(targetDb);
    if (!targetDb) {
      wExpectAutoCreate.setSelection(false);
    }
  }

  private void ok() {
    boolean versionBaseline = wBaselineVersion.getSelection();
    String tag = Const.NVL(wBaselineTag.getText(), "").trim();
    if (versionBaseline && Utils.isEmpty(tag)) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
      box.setText(
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationOptionsDialog.Error.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationOptionsDialog.Error.MissingVersionTag"));
      box.open();
      return;
    }
    if (!wCheckLive.getSelection()
        && !wCheckCatalogVersion.getSelection()
        && !wCheckModels.getSelection()
        && !wCheckTargetDb.getSelection()) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
      box.setText(
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationOptionsDialog.Error.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationOptionsDialog.Error.NoAxes"));
      box.open();
      return;
    }
    if (wCheckCatalogVersion.getSelection() && Utils.isEmpty(tag)) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
      box.setText(
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationOptionsDialog.Error.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationOptionsDialog.Error.MissingVersionTagForAxis"));
      box.open();
      return;
    }

    result =
        new ValidationOptions(
            versionBaseline ? BaselineKind.CATALOG_VERSION : BaselineKind.WORKING_CATALOG,
            Utils.isEmpty(tag) ? null : tag,
            null,
            wCheckLive.getSelection(),
            wCheckCatalogVersion.getSelection(),
            wCheckModels.getSelection(),
            wCheckTargetDb.getSelection(),
            wCheckTargetDb.getSelection() && wExpectAutoCreate.getSelection(),
            wIncludeImpact.getSelection(),
            wWriteReport.getSelection(),
            Const.NVL(wReportPath.getText(), "").trim(),
            Const.NVL(wReportBaseName.getText(), "").trim(),
            ReportFormat.BOTH);
    lastOptions = result;
    shell.dispose();
  }

  private void cancel() {
    result = null;
    shell.dispose();
  }
}
