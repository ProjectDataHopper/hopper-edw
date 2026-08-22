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
package org.hopper.edw.datavault.hopgui.resourcedefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.versioning.CatalogVersionGuiSupport;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.resourcedefinition.SchemaImpactSimulationResult;
import org.hopper.edw.datavault.resourcedefinition.SimulationStatus;
import org.hopper.edw.datavault.resourcedefinition.SourceUsage;
import org.hopper.edw.datavault.resourcedefinition.ValidationFindingFormatter;
import org.hopper.edw.datavault.resourcedefinition.ValidationOptions;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Interactive validation / impact results: compact issue summaries on top, selected-issue details
 * below with navigation and remediation entry points.
 */
public final class ResourceDefinitionValidationResultsDialog {

  private static final Class<?> PKG = ResourceDefinitionValidationResultsDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final ResourceDefinitionGroupMeta group;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  private ValidationReport report;
  private SchemaImpactSimulationResult simulation;
  private ValidationOptions options;
  private Shell shell;
  private Label wlSummary;
  private TableView wIssues;
  private Text wDetailHeader;
  private Text wDetailMessage;
  private Text wDetailImpact;
  private TableView wUsages;
  private Button wShowAcknowledged;
  private Button wOpenSource;
  private Button wOpenTarget;
  private Button wRemediation;
  private Button wAcknowledge;
  private Button wRevoke;

  private List<ValidationIssueRow> rows = List.of();
  private List<SourceUsage> detailUsages = List.of();
  private ValidationIssueRow selectedRow;

  public ResourceDefinitionValidationResultsDialog(
      Shell parent, HopGui hopGui, ResourceDefinitionGroupMeta group, ValidationReport report) {
    this(parent, hopGui, group, report, null);
  }

  public ResourceDefinitionValidationResultsDialog(
      Shell parent,
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      ValidationReport report,
      SchemaImpactSimulationResult simulation) {
    this(parent, hopGui, group, report, simulation, null);
  }

  public ResourceDefinitionValidationResultsDialog(
      Shell parent,
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      ValidationReport report,
      SchemaImpactSimulationResult simulation,
      ValidationOptions options) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.group = group;
    this.variables = hopGui.getVariables();
    this.metadataProvider = hopGui.getMetadataProvider();
    this.report = report;
    this.simulation = simulation;
    this.options = options;
  }

  public ResourceDefinitionValidationResultsDialog(
      Shell parent,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ValidationReport report) {
    this(parent, variables, metadataProvider, report, null);
  }

  public ResourceDefinitionValidationResultsDialog(
      Shell parent,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ValidationReport report,
      SchemaImpactSimulationResult simulation) {
    this.parent = parent;
    this.hopGui = HopGui.getInstance();
    this.group = null;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.report = report;
    this.simulation = simulation;
  }

  public void open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle() | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationResultsDialog.Shell.Title", report.getGroupName()));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    wlSummary = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlSummary);
    FormData fdlSummary = new FormData();
    fdlSummary.left = new FormAttachment(0, margin);
    fdlSummary.right = new FormAttachment(100, -margin);
    fdlSummary.top = new FormAttachment(0, margin);
    wlSummary.setLayoutData(fdlSummary);

    Label wlHint = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlHint);
    wlHint.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Hint.SelectIssue"));
    FormData fdlHint = new FormData();
    fdlHint.left = new FormAttachment(0, margin);
    fdlHint.right = new FormAttachment(100, -margin);
    fdlHint.top = new FormAttachment(wlSummary, margin / 2);
    wlHint.setLayoutData(fdlHint);

    // Footer buttons first so sash can attach above them
    wShowAcknowledged = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wShowAcknowledged);
    wShowAcknowledged.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationResultsDialog.ShowAcknowledged.Label"));
    wShowAcknowledged.addListener(SWT.Selection, e -> refreshTable());

    Button wRevalidate = new Button(shell, SWT.PUSH);
    wRevalidate.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Revalidate.Label"));
    wRevalidate.setEnabled(group != null);
    wRevalidate.addListener(SWT.Selection, e -> revalidate());

    Button wTagVersion = new Button(shell, SWT.PUSH);
    wTagVersion.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.TagVersion.Label"));
    wTagVersion.setEnabled(group != null && hopGui != null);
    wTagVersion.addListener(SWT.Selection, e -> tagCatalogVersion());

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.RESOURCE_DEFINITION_VALIDATION_RESULTS);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wShowAcknowledged, wRevalidate, wTagVersion, wClose}, margin, null);

    SashForm sash = new SashForm(shell, SWT.VERTICAL);
    PropsUi.setLook(sash);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, margin);
    fdSash.right = new FormAttachment(100, -margin);
    fdSash.top = new FormAttachment(wlHint, margin);
    fdSash.bottom = new FormAttachment(wClose, -2 * margin);
    sash.setLayoutData(fdSash);

    buildIssuesTable(sash);
    buildDetailsPane(sash);
    sash.setWeights(30, 70);

    wIssues.getTable().addListener(SWT.Selection, e -> showSelectedIssueDetails());
    wIssues.getTable().addListener(SWT.DefaultSelection, e -> openRemediationProposals());
    wUsages.getTable().addListener(SWT.Selection, e -> updateDetailButtonStates());
    wUsages.getTable().addListener(SWT.DefaultSelection, e -> openSelectedTargetUsage());

    refreshTable();
    shell.setMinimumSize(900, 640);
    BaseTransformDialog.setSize(shell, 1000, 720);
    shell.open();
  }

  private void buildIssuesTable(Composite parent) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Column.Severity"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Column.RecordDefinition"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Column.Field"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Column.Kind"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Column.Proposals"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              true)
        };
    wIssues =
        new TableView(
            variables,
            parent,
            SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER,
            columns,
            1,
            true,
            null,
            PropsUi.getInstance());
    wIssues.setReadonly(true);
  }

  private void buildDetailsPane(Composite parent) {
    Composite details = new Composite(parent, SWT.NONE);
    PropsUi.setLook(details);
    details.setLayout(new FormLayout());
    int margin = PropsUi.getMargin();

    Label wlDetails = new Label(details, SWT.LEFT);
    PropsUi.setLook(wlDetails);
    wlDetails.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Details.Label"));
    FormData fdlDetails = new FormData();
    fdlDetails.left = new FormAttachment(0, 0);
    fdlDetails.top = new FormAttachment(0, 0);
    fdlDetails.right = new FormAttachment(100, 0);
    wlDetails.setLayoutData(fdlDetails);

    wDetailHeader =
        new Text(details, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
    PropsUi.setLook(wDetailHeader);
    FormData fdHeader = new FormData();
    fdHeader.left = new FormAttachment(0, 0);
    fdHeader.right = new FormAttachment(100, 0);
    fdHeader.top = new FormAttachment(wlDetails, margin / 2);
    fdHeader.height = (int) (60 * PropsUi.getNativeZoomFactor());
    wDetailHeader.setLayoutData(fdHeader);

    Label wlMessage = new Label(details, SWT.LEFT);
    PropsUi.setLook(wlMessage);
    wlMessage.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Message.Label"));
    FormData fdlMessage = new FormData();
    fdlMessage.left = new FormAttachment(0, 0);
    fdlMessage.top = new FormAttachment(wDetailHeader, margin);
    wlMessage.setLayoutData(fdlMessage);

    wDetailMessage =
        new Text(details, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
    PropsUi.setLook(wDetailMessage);
    FormData fdMessage = new FormData();
    fdMessage.left = new FormAttachment(0, 0);
    fdMessage.right = new FormAttachment(100, 0);
    fdMessage.top = new FormAttachment(wlMessage, margin / 2);
    fdMessage.height = (int) (60 * PropsUi.getNativeZoomFactor());
    wDetailMessage.setLayoutData(fdMessage);

    Label wlImpact = new Label(details, SWT.LEFT);
    PropsUi.setLook(wlImpact);
    wlImpact.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationResultsDialog.DownstreamImpact.Label"));
    FormData fdlImpact = new FormData();
    fdlImpact.left = new FormAttachment(0, 0);
    fdlImpact.top = new FormAttachment(wDetailMessage, margin);
    wlImpact.setLayoutData(fdlImpact);

    wDetailImpact =
        new Text(details, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
    PropsUi.setLook(wDetailImpact);
    FormData fdImpact = new FormData();
    fdImpact.left = new FormAttachment(0, 0);
    fdImpact.right = new FormAttachment(100, 0);
    fdImpact.top = new FormAttachment(wlImpact, margin / 2);
    fdImpact.height = (int) (60 * PropsUi.getNativeZoomFactor());
    wDetailImpact.setLayoutData(fdImpact);

    Label wlUsages = new Label(details, SWT.LEFT);
    PropsUi.setLook(wlUsages);
    wlUsages.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Usages.Label"));
    FormData fdlUsages = new FormData();
    fdlUsages.left = new FormAttachment(0, 0);
    fdlUsages.top = new FormAttachment(wDetailImpact, margin);
    wlUsages.setLayoutData(fdlUsages);

    wOpenSource = new Button(details, SWT.PUSH);
    wOpenSource.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.OpenSource.Label"));
    wOpenSource.setToolTipText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationResultsDialog.OpenSource.ToolTip"));
    wOpenSource.addListener(SWT.Selection, e -> openSourceInCatalog());

    wOpenTarget = new Button(details, SWT.PUSH);
    wOpenTarget.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.OpenTarget.Label"));
    wOpenTarget.setToolTipText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationResultsDialog.OpenTarget.ToolTip"));
    wOpenTarget.addListener(SWT.Selection, e -> openSelectedTargetUsage());

    wRemediation = new Button(details, SWT.PUSH);
    wRemediation.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Remediation.Label"));
    wRemediation.setToolTipText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationResultsDialog.Remediation.ToolTip"));
    wRemediation.addListener(SWT.Selection, e -> openRemediationProposals());

    wAcknowledge = new Button(details, SWT.PUSH);
    wAcknowledge.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Acknowledge.Label"));
    wAcknowledge.addListener(SWT.Selection, e -> acknowledgeSelected());

    wRevoke = new Button(details, SWT.PUSH);
    wRevoke.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Revoke.Label"));
    wRevoke.addListener(SWT.Selection, e -> revokeSelected());

    BaseTransformDialog.positionBottomButtons(
        details,
        new Button[] {wOpenSource, wOpenTarget, wRemediation, wAcknowledge, wRevoke},
        margin,
        null);

    ColumnInfo[] usageColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Usages.Column.ModelType"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Usages.Column.Model"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Usages.Column.Element"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    wUsages =
        new TableView(
            variables,
            details,
            SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER,
            usageColumns,
            1,
            null,
            PropsUi.getInstance());
    FormData fdUsages = new FormData();
    fdUsages.left = new FormAttachment(0, 0);
    fdUsages.right = new FormAttachment(100, 0);
    fdUsages.top = new FormAttachment(wlUsages, margin / 2);
    fdUsages.bottom = new FormAttachment(wOpenSource, -margin);
    wUsages.setLayoutData(fdUsages);

    clearDetails();
  }

  private void refreshTable() {
    boolean showAcknowledged = wShowAcknowledged.getSelection();
    String previousIssueId =
        selectedRow != null && selectedRow.issue() != null ? selectedRow.issue().issueId() : null;
    rows = buildRows(report, showAcknowledged);
    updateSummary(showAcknowledged);

    Display display = shell.getDisplay();
    Color blockingFg = display.getSystemColor(SWT.COLOR_RED);
    Color warningFg = display.getSystemColor(SWT.COLOR_DARK_YELLOW);
    Color ackFg = display.getSystemColor(SWT.COLOR_DARK_GRAY);

    wIssues.clearAll(false);
    int restoreIndex = -1;
    for (int i = 0; i < rows.size(); i++) {
      ValidationIssueRow row = rows.get(i);
      TableItem item =
          i == 0 ? wIssues.getTable().getItem(0) : new TableItem(wIssues.getTable(), SWT.NONE);
      item.setText(1, formatSeverity(row));
      item.setText(2, formatRecordDefinition(row.validation()));
      String fieldOrFinding = Const.NVL(row.issue().fieldName(), "");
      if (Utils.isEmpty(fieldOrFinding)) {
        fieldOrFinding =
            ValidationFindingFormatter.shortTitle(Const.NVL(row.issue().message(), ""));
        if (fieldOrFinding.length() > 80) {
          fieldOrFinding = fieldOrFinding.substring(0, 77) + "...";
        }
      }
      item.setText(3, fieldOrFinding);
      item.setText(4, row.issue().kind() != null ? row.issue().kind().name() : "");
      item.setText(
          5,
          Integer.toString(row.issue().proposals() != null ? row.issue().proposals().size() : 0));
      if (row.acknowledged()) {
        item.setForeground(ackFg);
      } else if (row.issue().severity() == IssueSeverity.BLOCKING) {
        item.setForeground(blockingFg);
      } else if (row.issue().severity() == IssueSeverity.WARNING) {
        item.setForeground(warningFg);
      }
      if (previousIssueId != null
          && row.issue() != null
          && previousIssueId.equals(row.issue().issueId())) {
        restoreIndex = i;
      }
    }
    wIssues.optimizeTableView();
    if (rows.isEmpty()) {
      clearDetails();
      return;
    }
    if (restoreIndex < 0) {
      restoreIndex = 0;
    }
    wIssues.getTable().setSelection(restoreIndex);
    showSelectedIssueDetails();
  }

  private void showSelectedIssueDetails() {
    int index = wIssues.getTable().getSelectionIndex();
    if (index < 0 || index >= rows.size()) {
      clearDetails();
      return;
    }
    selectedRow = rows.get(index);
    ValidationIssue issue = selectedRow.issue();
    RecordDefinitionValidation validation = selectedRow.validation();

    String keyLabel = formatRecordDefinition(validation);
    String catalog = Const.NVL(validation.catalogConnection(), "");
    String sourceType = Const.NVL(validation.sourceType(), "");
    String ackLabel =
        selectedRow.acknowledged()
            ? BaseMessages.getString(
                PKG, "ResourceDefinitionValidationResultsDialog.Acknowledged.Yes")
            : BaseMessages.getString(
                PKG, "ResourceDefinitionValidationResultsDialog.Acknowledged.No");

    wDetailHeader.setText(
        BaseMessages.getString(
            PKG,
            "ResourceDefinitionValidationResultsDialog.Details.Header",
            issue.severity() != null ? issue.severity().name() : "",
            issue.kind() != null ? issue.kind().name() : "",
            Const.NVL(issue.fieldName(), ""),
            keyLabel,
            sourceType,
            catalog,
            ackLabel));
    wDetailMessage.setText(Const.NVL(issue.message(), ""));
    wDetailImpact.setText(
        Utils.isEmpty(issue.downstreamImpact())
            ? BaseMessages.getString(
                PKG, "ResourceDefinitionValidationResultsDialog.DownstreamImpact.None")
            : issue.downstreamImpact());

    detailUsages = ValidationIssueGuiActions.filterUsages(validation, issue);
    populateUsages(detailUsages);
    updateDetailButtonStates();
  }

  private void populateUsages(List<SourceUsage> usages) {
    wUsages.clearAll(false);
    for (int i = 0; i < usages.size(); i++) {
      SourceUsage usage = usages.get(i);
      TableItem item =
          i == 0 ? wUsages.getTable().getItem(0) : new TableItem(wUsages.getTable(), SWT.NONE);
      item.setText(1, Const.NVL(usage.modelType(), ""));
      item.setText(2, Const.NVL(usage.modelName(), ""));
      item.setText(3, Const.NVL(usage.modelElementName(), ""));
    }
    wUsages.optimizeTableView();
    if (!usages.isEmpty()) {
      wUsages.getTable().setSelection(0);
    }
  }

  private void clearDetails() {
    selectedRow = null;
    detailUsages = List.of();
    if (wDetailHeader != null && !wDetailHeader.isDisposed()) {
      wDetailHeader.setText(
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationResultsDialog.Details.NoneSelected"));
    }
    if (wDetailMessage != null && !wDetailMessage.isDisposed()) {
      wDetailMessage.setText("");
    }
    if (wDetailImpact != null && !wDetailImpact.isDisposed()) {
      wDetailImpact.setText("");
    }
    if (wUsages != null && !wUsages.isDisposed()) {
      wUsages.clearAll(false);
      wUsages.optimizeTableView();
    }
    updateDetailButtonStates();
  }

  private void updateDetailButtonStates() {
    boolean hasSelection = selectedRow != null && selectedRow.issue() != null;
    boolean hasKey =
        hasSelection
            && selectedRow.validation() != null
            && selectedRow.validation().key() != null
            && !Utils.isEmpty(selectedRow.validation().catalogConnection());
    boolean hasProposals =
        hasSelection
            && selectedRow.issue().proposals() != null
            && !selectedRow.issue().proposals().isEmpty();
    boolean hasUsageSelection =
        hasSelection
            && wUsages != null
            && !wUsages.isDisposed()
            && wUsages.getTable().getSelectionIndex() >= 0
            && wUsages.getTable().getSelectionIndex() < detailUsages.size();

    if (wOpenSource != null && !wOpenSource.isDisposed()) {
      wOpenSource.setEnabled(hasKey);
    }
    if (wOpenTarget != null && !wOpenTarget.isDisposed()) {
      wOpenTarget.setEnabled(hasUsageSelection && hopGui != null);
    }
    if (wRemediation != null && !wRemediation.isDisposed()) {
      wRemediation.setEnabled(hasProposals);
    }
    if (wAcknowledge != null && !wAcknowledge.isDisposed()) {
      wAcknowledge.setEnabled(hasSelection && !selectedRow.acknowledged());
    }
    if (wRevoke != null && !wRevoke.isDisposed()) {
      wRevoke.setEnabled(hasSelection && selectedRow.acknowledged());
    }
  }

  private void openSourceInCatalog() {
    if (selectedRow == null) {
      return;
    }
    try {
      ValidationIssueGuiActions.openSourceInCatalog(selectedRow.validation());
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Error.OpenSource"),
          e);
    }
  }

  private void openSelectedTargetUsage() {
    if (selectedRow == null || hopGui == null) {
      return;
    }
    int index = wUsages.getTable().getSelectionIndex();
    if (index < 0 || index >= detailUsages.size()) {
      return;
    }
    try {
      ValidationIssueGuiActions.openTargetUsage(hopGui, detailUsages.get(index), variables);
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Error.OpenTarget"),
          e);
    }
  }

  private void openRemediationProposals() {
    if (selectedRow == null) {
      return;
    }
    ResourceDefinitionIssueDialog dialog =
        new ResourceDefinitionIssueDialog(
            shell,
            hopGui,
            group,
            selectedRow.validation(),
            selectedRow.issue(),
            selectedRow.acknowledged(),
            this::revalidateQuietly);
    dialog.open();
  }

  private void acknowledgeSelected() {
    if (selectedRow == null || selectedRow.issue() == null) {
      return;
    }
    AcknowledgeValidationIssueDialog dialog =
        new AcknowledgeValidationIssueDialog(shell, selectedRow.issue().message());
    if (!dialog.openConfirmed()) {
      return;
    }
    try {
      ValidationIssueGuiActions.acknowledge(
          selectedRow.validation(),
          selectedRow.issue(),
          dialog.getComment(),
          variables,
          metadataProvider);
      revalidateQuietly();
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Error.Title"),
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationResultsDialog.Error.Acknowledge"),
          e);
    }
  }

  private void revokeSelected() {
    if (selectedRow == null || selectedRow.issue() == null) {
      return;
    }
    try {
      ValidationIssueGuiActions.revoke(
          selectedRow.validation(), selectedRow.issue(), variables, metadataProvider);
      revalidateQuietly();
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Error.Revoke"),
          e);
    }
  }

  private void updateSummary(boolean showAcknowledged) {
    int blocking = 0;
    int warnings = 0;
    for (ValidationIssueRow row : rows) {
      if (row.acknowledged()) {
        continue;
      }
      if (row.issue().severity() == IssueSeverity.BLOCKING) {
        blocking++;
      } else if (row.issue().severity() == IssueSeverity.WARNING) {
        warnings++;
      }
    }
    int hiddenAcknowledged = showAcknowledged ? 0 : report.getAcknowledgedIssueCount();
    String statusBanner = formatStatusBanner();
    String context = formatValidationContext();
    wlSummary.setText(
        statusBanner
            + "  |  "
            + BaseMessages.getString(
                PKG,
                "ResourceDefinitionValidationResultsDialog.Summary.Detailed",
                report.getTotalDefinitions(),
                rows.size(),
                blocking,
                warnings,
                hiddenAcknowledged,
                report.hasBlockingIssues()
                    ? BaseMessages.getString(
                        PKG, "ResourceDefinitionValidationResultsDialog.HasBlocking")
                    : BaseMessages.getString(
                        PKG, "ResourceDefinitionValidationResultsDialog.NoBlocking"))
            + (Utils.isEmpty(context) ? "" : "\n" + context));
  }

  private String formatValidationContext() {
    StringBuilder builder = new StringBuilder();
    if (simulation != null) {
      builder.append(
          ValidationFindingFormatter.describeCompareContext(
              simulation.compareMode(),
              simulation.baselineVersionUsed(),
              simulation.catalogVersionUsed()));
    }
    if (options != null) {
      if (!builder.isEmpty()) {
        builder.append("  |  ");
      }
      builder
          .append(
              BaseMessages.getString(
                  PKG,
                  "ResourceDefinitionValidationResultsDialog.Context.Baseline",
                  options.describeBaseline()))
          .append("  |  ")
          .append(
              BaseMessages.getString(
                  PKG,
                  "ResourceDefinitionValidationResultsDialog.Context.Axes",
                  options.describeAxes()));
      return builder.toString();
    }
    if (simulation == null) {
      return "";
    }
    if (Utils.isEmpty(simulation.baselineVersionUsed())
        && Utils.isEmpty(simulation.catalogVersionUsed())) {
      if (!builder.isEmpty()) {
        builder.append("  |  ");
      }
      builder.append(
          BaseMessages.getString(
              PKG, "ResourceDefinitionValidationResultsDialog.Context.WorkingCatalog"));
    }
    return builder.toString();
  }

  private String formatStatusBanner() {
    SimulationStatus status =
        simulation != null
            ? simulation.status()
            : (report.hasBlockingIssues()
                ? SimulationStatus.CRITICAL_BLOCKED
                : report.getIssueCount() > 0 ? SimulationStatus.WARNING : SimulationStatus.PASS);
    return switch (status) {
      case CRITICAL_BLOCKED ->
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Status.Critical");
      case WARNING ->
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Status.Warning");
      case PASS ->
          BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Status.Pass");
    };
  }

  private static List<ValidationIssueRow> buildRows(
      ValidationReport report, boolean showAcknowledged) {
    List<ValidationIssueRow> rows = new ArrayList<>();
    for (RecordDefinitionValidation validation : report.getRecordValidations()) {
      List<ValidationIssue> issues =
          showAcknowledged ? validation.allIssues() : validation.issues();
      if (issues == null || issues.isEmpty()) {
        continue;
      }
      for (ValidationIssue issue : issues) {
        if (issue == null) {
          continue;
        }
        boolean acknowledged =
            ValidationIssueRow.isAcknowledged(validation, issue, showAcknowledged);
        if (!showAcknowledged && acknowledged) {
          continue;
        }
        rows.add(new ValidationIssueRow(validation, issue, acknowledged));
      }
    }
    rows.sort(
        Comparator.comparingInt(ValidationIssueRow::severityRank)
            .thenComparing(row -> formatRecordDefinition(row.validation()))
            .thenComparing(row -> Const.NVL(row.issue().fieldName(), "")));
    return rows;
  }

  private static String formatRecordDefinition(RecordDefinitionValidation validation) {
    if (validation == null || validation.key() == null) {
      return "?";
    }
    return validation.key().getNamespace() + "/" + validation.key().getName();
  }

  private static String formatSeverity(ValidationIssueRow row) {
    if (row.acknowledged()) {
      return row.issue().severity() + " (ack)";
    }
    return row.issue().severity().name();
  }

  private void tagCatalogVersion() {
    if (group == null || hopGui == null) {
      return;
    }
    CatalogVersionGuiSupport.tagVersionFromGroup(hopGui, group);
  }

  private void revalidate() {
    if (group == null || hopGui == null) {
      return;
    }
    try {
      ResourceDefinitionValidationOptionsDialog optionsDialog =
          new ResourceDefinitionValidationOptionsDialog(shell, hopGui, group);
      ValidationOptions chosen = optionsDialog.open();
      if (chosen == null) {
        return;
      }
      options = chosen;
      simulation =
          ResourceDefinitionValidationGuiSupport.runSimulation(
              group, options, variables, metadataProvider);
      report = simulation.validationReport();
      refreshTable();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Message"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private void revalidateQuietly() {
    if (group == null) {
      refreshTable();
      return;
    }
    try {
      ValidationOptions effective =
          options != null ? options : ResourceDefinitionValidationOptionsDialog.lastOptions();
      if (effective == null) {
        effective = ValidationOptions.defaults();
      }
      simulation =
          ResourceDefinitionValidationGuiSupport.runSimulation(
              group, effective, variables, metadataProvider);
      report = simulation.validationReport();
      refreshTable();
    } catch (Exception ignored) {
      refreshTable();
    }
  }
}
