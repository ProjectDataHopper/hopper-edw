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
package org.apache.hop.datavault.hopgui.resourcedefinition;

import java.util.List;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.resourcedefinition.RemediationProposalApplySupport;
import org.apache.hop.datavault.resourcedefinition.RemediationProposalApplySupport.ApplyResult;
import org.apache.hop.datavault.resourcedefinition.RemediationProposalApplySupport.ProposalContext;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Remediation proposals dialog for a single validation issue: read-only summary list on top, full
 * proposal details below (~70%), with always-visible Apply / Close footer buttons.
 */
public final class ResourceDefinitionIssueDialog {

  private static final Class<?> PKG = ResourceDefinitionIssueDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final ResourceDefinitionGroupMeta group;
  private final RecordDefinitionValidation validation;
  private final ValidationIssue issue;
  private final boolean acknowledged;
  private final Runnable onChanged;

  private RecordDefinition definition;
  private Shell shell;
  private TableView wProposals;
  private Text wDetailBody;
  private Button wApplyProposal;
  private List<RemediationProposal> proposals = List.of();

  public ResourceDefinitionIssueDialog(
      Shell parent,
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      RecordDefinitionValidation validation,
      ValidationIssue issue,
      boolean acknowledged,
      Runnable onChanged) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.group = group;
    this.validation = validation;
    this.issue = issue;
    this.acknowledged = acknowledged;
    this.onChanged = onChanged;
  }

  public void open() {
    try {
      definition =
          ValidationIssueGuiActions.loadDefinition(
              validation, hopGui.getVariables(), hopGui.getMetadataProvider());
    } catch (HopException e) {
      new ErrorDialog(
          parent,
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.LoadDefinition"),
          e);
      return;
    }

    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle() | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            "ResourceDefinitionIssueDialog.Shell.Title",
            validation.key() != null ? validation.key().getName() : "?"));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    // Footer first so content can attach above always-visible buttons.
    wApplyProposal = new Button(shell, SWT.PUSH);
    wApplyProposal.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.ApplyProposal.Label"));
    wApplyProposal.addListener(SWT.Selection, e -> applySelectedProposal());
    wApplyProposal.setEnabled(false);

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.RESOURCE_DEFINITION_ISSUE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wApplyProposal, wClose}, margin, null);

    Label wlHeader = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlHeader);
    wlHeader.setText(buildHeaderText());
    FormData fdlHeader = new FormData();
    fdlHeader.left = new FormAttachment(0, margin);
    fdlHeader.right = new FormAttachment(100, -margin);
    fdlHeader.top = new FormAttachment(0, margin);
    wlHeader.setLayoutData(fdlHeader);

    Label wlProposals = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlProposals);
    wlProposals.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Proposals.Label"));
    FormData fdlProposals = new FormData();
    fdlProposals.left = new FormAttachment(0, margin);
    fdlProposals.top = new FormAttachment(wlHeader, margin);
    wlProposals.setLayoutData(fdlProposals);

    SashForm sash = new SashForm(shell, SWT.VERTICAL);
    PropsUi.setLook(sash);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, margin);
    fdSash.right = new FormAttachment(100, -margin);
    fdSash.top = new FormAttachment(wlProposals, margin / 2);
    fdSash.bottom = new FormAttachment(wApplyProposal, -2 * margin);
    sash.setLayoutData(fdSash);

    buildSummaryTable(sash);
    buildDetailsPane(sash);
    sash.setWeights(30, 70);

    populateProposals();
    wProposals.getTable().addListener(SWT.Selection, e -> showSelectedProposalDetails());
    wProposals.getTable().addListener(SWT.DefaultSelection, e -> applySelectedProposal());

    shell.setMinimumSize(720, 520);
    BaseTransformDialog.setSize(shell, 800, 600);
    shell.open();
  }

  private void buildSummaryTable(Composite parent) {
    ColumnInfo summaryColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Proposals.Summary.Column"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    summaryColumn.setReadOnly(true);

    wProposals =
        new TableView(
            hopGui.getVariables(),
            parent,
            SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER,
            new ColumnInfo[] {summaryColumn},
            1,
            null,
            PropsUi.getInstance());
    wProposals.setReadonly(true);
  }

  private void buildDetailsPane(Composite parent) {
    Composite details = new Composite(parent, SWT.NONE);
    PropsUi.setLook(details);
    details.setLayout(new FormLayout());
    int margin = PropsUi.getMargin();

    Label wlDetails = new Label(details, SWT.LEFT);
    PropsUi.setLook(wlDetails);
    wlDetails.setText(BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Details.Label"));
    FormData fdlDetails = new FormData();
    fdlDetails.left = new FormAttachment(0, 0);
    fdlDetails.top = new FormAttachment(0, 0);
    fdlDetails.right = new FormAttachment(100, 0);
    wlDetails.setLayoutData(fdlDetails);

    wDetailBody =
        new Text(details, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
    PropsUi.setLook(wDetailBody);
    FormData fdBody = new FormData();
    fdBody.left = new FormAttachment(0, 0);
    fdBody.right = new FormAttachment(100, 0);
    fdBody.top = new FormAttachment(wlDetails, margin / 2);
    fdBody.bottom = new FormAttachment(100, 0);
    wDetailBody.setLayoutData(fdBody);

    clearDetails();
  }

  private void populateProposals() {
    proposals =
        issue.proposals() != null
            ? issue.proposals().stream().filter(p -> p != null).toList()
            : List.of();

    wProposals.clearAll(false);
    for (int i = 0; i < proposals.size(); i++) {
      RemediationProposal proposal = proposals.get(i);
      TableItem item =
          i == 0
              ? wProposals.getTable().getItem(0)
              : new TableItem(wProposals.getTable(), SWT.NONE);
      item.setText(1, Const.NVL(proposal.summary(), ""));
    }
    wProposals.optimizeTableView();
    if (!proposals.isEmpty()) {
      wProposals.getTable().setSelection(0);
      showSelectedProposalDetails();
    } else {
      clearDetails();
    }
  }

  private void showSelectedProposalDetails() {
    int index = wProposals.getTable().getSelectionIndex();
    if (index < 0 || index >= proposals.size()) {
      clearDetails();
      return;
    }
    RemediationProposal proposal = proposals.get(index);
    String typeName = proposal.type() != null ? proposal.type().name() : "";
    String details = Const.NVL(proposal.details(), "");
    wDetailBody.setText(
        BaseMessages.getString(
            PKG,
            "ResourceDefinitionIssueDialog.Details.Body",
            Const.NVL(proposal.summary(), ""),
            typeName,
            details));

    boolean canApply =
        proposal.type() != null && proposal.type() != ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED;
    wApplyProposal.setEnabled(canApply);
  }

  private void clearDetails() {
    if (wDetailBody != null && !wDetailBody.isDisposed()) {
      wDetailBody.setText(
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Details.NoneSelected"));
    }
    if (wApplyProposal != null && !wApplyProposal.isDisposed()) {
      wApplyProposal.setEnabled(false);
    }
  }

  private String buildHeaderText() {
    String keyLabel =
        validation.key() != null
            ? validation.key().getNamespace() + "/" + validation.key().getName()
            : "?";
    String header =
        BaseMessages.getString(
            PKG,
            "ResourceDefinitionIssueDialog.Header.Short",
            issue.severity() != null ? issue.severity().name() : "",
            issue.kind() != null ? issue.kind().name() : "",
            Const.NVL(issue.fieldName(), ""),
            Const.NVL(issue.message(), ""),
            keyLabel);
    if (acknowledged) {
      header =
          header
              + "\n"
              + BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Acknowledged.Note");
    }
    return header;
  }

  private void applySelectedProposal() {
    int selectionIndex = wProposals.getTable().getSelectionIndex();
    if (selectionIndex < 0 || selectionIndex >= proposals.size()) {
      return;
    }
    RemediationProposal proposal = proposals.get(selectionIndex);
    if (proposal.type() == ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED) {
      return;
    }
    if (!confirmDestructiveApply(proposal)) {
      return;
    }

    String remediationName = null;
    if (proposal.type() == ProposalType.UPDATE_TARGET_COLUMN_LENGTH
        || proposal.type() == ProposalType.ALIGN_MODELS_TO_BASELINE
        || proposal.type() == ProposalType.GENERATE_TARGET_DDL_PACKAGE) {
      remediationName = askRemediationName();
      if (Utils.isEmpty(remediationName)) {
        return;
      }
    }

    // Catalog is never rewritten for expand-models remediation. Version tag only for legacy ignore.
    String baselineVersionTag = null;
    if (proposal.type() == ProposalType.IGNORE_SOURCE_DRIFT) {
      baselineVersionTag = askBaselineVersionTag();
      if (Utils.isEmpty(baselineVersionTag)) {
        return;
      }
    }

    try {
      definition =
          ValidationIssueGuiActions.loadDefinition(
              validation, hopGui.getVariables(), hopGui.getMetadataProvider());
      ApplyResult result =
          RemediationProposalApplySupport.apply(
              new ProposalContext(
                  hopGui,
                  group,
                  definition,
                  validation,
                  issue,
                  proposal,
                  hopGui.getVariables(),
                  hopGui.getMetadataProvider(),
                  remediationName,
                  baselineVersionTag));
      if (onChanged != null) {
        onChanged.run();
      }
      showApplyResult(result);
      // Keep the dialog open after apply so the admin can re-read the report text if needed.
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.ApplyProposal"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private String askRemediationName() {
    String suggested =
        "accept-"
            + (issue != null && !Utils.isEmpty(issue.fieldName()) ? issue.fieldName() : "field");
    EnterStringDialog dialog =
        new EnterStringDialog(
            shell,
            suggested,
            BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.RemediationName.Title"),
            BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.RemediationName.Message"));
    return dialog.open();
  }

  /**
   * Required catalog version tag used as the only legal source of field values when rejecting live
   * source drift (never models or target tables).
   */
  private String askBaselineVersionTag() {
    EnterStringDialog dialog =
        new EnterStringDialog(
            shell,
            "",
            BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.BaselineVersion.Title"),
            BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.BaselineVersion.Message"));
    dialog.setMandatory(true);
    return dialog.open();
  }

  private String askOptionalBaselineVersionTag() {
    EnterStringDialog dialog =
        new EnterStringDialog(
            shell,
            "",
            BaseMessages.getString(
                PKG, "ResourceDefinitionIssueDialog.BaselineVersion.Optional.Title"),
            BaseMessages.getString(
                PKG, "ResourceDefinitionIssueDialog.BaselineVersion.Optional.Message"));
    String tag = dialog.open();
    return tag != null ? tag.trim() : null;
  }

  private void showApplyResult(ApplyResult result) {
    if (result == null) {
      return;
    }
    int style =
        result.status() == RemediationProposalApplySupport.ApplyStatus.APPLIED
            ? SWT.ICON_INFORMATION
            : SWT.ICON_WARNING;
    MessageBox box = new MessageBox(shell, style | SWT.OK | SWT.PRIMARY_MODAL);
    box.setText(BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.ApplyResult.Title"));
    box.setMessage(Const.NVL(result.message(), result.status().name()));
    box.open();
  }

  /**
   * Confirmation for proposals that change catalog contracts, model metadata, or generate DDL
   * workflows. Lists known model usages and states that physical DDL is not executed immediately.
   */
  private boolean confirmDestructiveApply(RemediationProposal proposal) {
    boolean destructive =
        proposal.type() == ProposalType.REFRESH_CATALOG_CONTRACT
            || proposal.type() == ProposalType.UPDATE_TARGET_COLUMN_LENGTH
            || proposal.type() == ProposalType.ALIGN_MODELS_TO_BASELINE
            || proposal.type() == ProposalType.IGNORE_SOURCE_DRIFT
            || proposal.type() == ProposalType.GENERATE_TARGET_DDL_PACKAGE
            || proposal.type() == ProposalType.EXTEND_EXISTING_SATELLITE
            || proposal.type() == ProposalType.ADD_NEW_SATELLITE;
    if (!destructive) {
      return true;
    }

    StringBuilder message = new StringBuilder();
    message
        .append(
            BaseMessages.getString(
                PKG, "ResourceDefinitionIssueDialog.ConfirmApply.Message", proposal.summary()))
        .append('\n');

    if (proposal.type() == ProposalType.IGNORE_SOURCE_DRIFT) {
      message
          .append('\n')
          .append(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionIssueDialog.ConfirmApply.IgnoreDriftDanger"))
          .append('\n');
    } else if (proposal.type() == ProposalType.UPDATE_TARGET_COLUMN_LENGTH
        || proposal.type() == ProposalType.ALIGN_MODELS_TO_BASELINE) {
      message
          .append('\n')
          .append(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionIssueDialog.ConfirmApply.AlignModelsExplain"))
          .append('\n');
    }

    if (issue.severity() == IssueSeverity.BLOCKING
        && proposal.type() != ProposalType.IGNORE_SOURCE_DRIFT) {
      message
          .append('\n')
          .append(
              BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.ConfirmApply.Blocking"))
          .append('\n');
    }
    String usageTargets = formatUsageTargets();
    if (!Utils.isEmpty(usageTargets)) {
      message
          .append('\n')
          .append(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionIssueDialog.ConfirmApply.Targets", usageTargets))
          .append('\n');
    }
    if (!Utils.isEmpty(issue.downstreamImpact())) {
      message
          .append('\n')
          .append(
              BaseMessages.getString(
                  PKG,
                  "ResourceDefinitionIssueDialog.ConfirmApply.Impact",
                  issue.downstreamImpact()))
          .append('\n');
    }
    if (proposal.type() == ProposalType.UPDATE_TARGET_COLUMN_LENGTH
        || proposal.type() == ProposalType.ALIGN_MODELS_TO_BASELINE
        || proposal.type() == ProposalType.GENERATE_TARGET_DDL_PACKAGE) {
      message
          .append('\n')
          .append(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionIssueDialog.ConfirmApply.NoPhysicalExecute"))
          .append('\n');
    }
    message
        .append('\n')
        .append(
            BaseMessages.getString(
                PKG, "ResourceDefinitionIssueDialog.ConfirmApply.AcceptDestructive"));

    MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO | SWT.PRIMARY_MODAL);
    box.setText(BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.ConfirmApply.Title"));
    box.setMessage(message.toString());
    return box.open() == SWT.YES;
  }

  private String formatUsageTargets() {
    if (validation == null || validation.usages() == null || validation.usages().isEmpty()) {
      return "";
    }
    String fieldName = issue != null ? issue.fieldName() : null;
    StringBuilder builder = new StringBuilder();
    for (org.apache.hop.datavault.resourcedefinition.SourceUsage usage : validation.usages()) {
      if (usage == null) {
        continue;
      }
      if (!Utils.isEmpty(fieldName)
          && usage.mappedFields() != null
          && !usage.mappedFields().isEmpty()
          && usage.mappedFields().stream().noneMatch(fieldName::equalsIgnoreCase)) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder
          .append("- ")
          .append(Const.NVL(usage.modelType(), "?"))
          .append(" / ")
          .append(Const.NVL(usage.modelName(), "?"))
          .append(" / ")
          .append(Const.NVL(usage.modelElementName(), "?"));
    }
    return builder.toString();
  }
}
