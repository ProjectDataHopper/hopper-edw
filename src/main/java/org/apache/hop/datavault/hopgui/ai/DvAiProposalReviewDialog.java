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
package org.apache.hop.datavault.hopgui.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.ai.DvAiProposal;
import org.apache.hop.datavault.ai.DvAiProposalApplier;
import org.apache.hop.datavault.ai.DvAiProposalValidator;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Review and selectively apply AI-proposed model changes. */
public class DvAiProposalReviewDialog {

  private static final Class<?> PKG = DvAiProposalReviewDialog.class;
  private static final String BLOCKED_ITEM_KEY = "blocked";

  private final Shell parent;
  private final List<DvAiProposal> proposals;
  private final Map<DvAiProposal, DvAiProposalValidator.ValidationResult> validationByProposal =
      new HashMap<>();
  private Shell shell;
  private Table wProposals;
  private Text wPreview;
  private boolean applied;
  private List<DvAiProposal> selectedProposals = List.of();

  public DvAiProposalReviewDialog(Shell parent, List<DvAiProposal> proposals) {
    this(parent, proposals, List.of());
  }

  public DvAiProposalReviewDialog(
      Shell parent,
      List<DvAiProposal> proposals,
      List<DvAiProposalValidator.ValidationResult> validationResults) {
    this.parent = parent;
    this.proposals = proposals != null ? proposals : List.of();
    if (validationResults != null) {
      for (DvAiProposalValidator.ValidationResult result : validationResults) {
        if (result != null && result.getProposal() != null) {
          validationByProposal.put(result.getProposal(), result);
        }
      }
    }
  }

  public boolean open() {
    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvAiProposalReviewDialog.Title"));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    Button wApply = new Button(shell, SWT.PUSH);
    wApply.setText(BaseMessages.getString(PKG, "DvAiProposalReviewDialog.Apply.Label"));
    wApply.addListener(SWT.Selection, e -> applySelected());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_AI_PROPOSAL_REVIEW);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wApply, wCancel}, margin, null);

    Label wlList = new Label(shell, SWT.LEFT);
    wlList.setText(BaseMessages.getString(PKG, "DvAiProposalReviewDialog.Proposals.Label"));
    PropsUi.setLook(wlList);
    FormData fdlList = new FormData();
    fdlList.left = new FormAttachment(0, margin);
    fdlList.top = new FormAttachment(0, margin);
    wlList.setLayoutData(fdlList);

    wProposals =
        new Table(
            shell,
            SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION | SWT.MULTI);
    PropsUi.setLook(wProposals);
    wProposals.setHeaderVisible(false);
    wProposals.setLinesVisible(true);
    TableColumn column = new TableColumn(wProposals, SWT.NONE);
    column.setResizable(true);
    column.setWidth(580);
    Label wlPreview = new Label(shell, SWT.LEFT);
    wlPreview.setText(BaseMessages.getString(PKG, "DvAiProposalReviewDialog.Preview.Label"));
    PropsUi.setLook(wlPreview);
    FormData fdlPreviewLabel = new FormData();
    fdlPreviewLabel.left = new FormAttachment(0, margin);
    fdlPreviewLabel.top = new FormAttachment(34, 0);
    wlPreview.setLayoutData(fdlPreviewLabel);

    FormData fdList = new FormData();
    fdList.left = new FormAttachment(0, margin);
    fdList.top = new FormAttachment(wlList, margin);
    fdList.right = new FormAttachment(100, -margin);
    fdList.bottom = new FormAttachment(wlPreview, -margin);
    wProposals.setLayoutData(fdList);

    for (DvAiProposal proposal : proposals) {
      DvAiProposalValidator.ValidationResult validation = validationByProposal.get(proposal);
      boolean blocked =
          validation != null && validation.getStatus() == DvAiProposalValidator.Status.BLOCKED;
      TableItem item = new TableItem(wProposals, SWT.NONE);
      item.setText(proposalLabel(proposal, validation));
      item.setChecked(!blocked);
      if (blocked) {
        item.setData(BLOCKED_ITEM_KEY, Boolean.TRUE);
      }
    }
    column.pack();

    wProposals.addListener(
        SWT.Selection,
        e -> {
          if (e.detail == SWT.CHECK) {
            TableItem item = (TableItem) e.item;
            if (item != null && Boolean.TRUE.equals(item.getData(BLOCKED_ITEM_KEY))) {
              item.setChecked(false);
            }
          }
          updatePreview();
        });

    wPreview =
        new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
    PropsUi.setLook(wPreview);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, margin);
    fdPreview.top = new FormAttachment(wlPreview, margin);
    fdPreview.bottom = new FormAttachment(wApply, -margin);
    fdPreview.right = new FormAttachment(100, -margin);
    wPreview.setLayoutData(fdPreview);

    updatePreview();

    BaseTransformDialog.setSize(shell);

    shell.open();
    while (!shell.isDisposed()) {
      if (!parent.getDisplay().readAndDispatch()) {
        parent.getDisplay().sleep();
      }
    }
    return applied;
  }

  /** Returns proposals the user confirmed; valid after {@link #open()} returns with apply. */
  public List<DvAiProposal> getSelectedProposals() {
    return selectedProposals;
  }

  private List<DvAiProposal> readSelectedFromWidgets() {
    List<DvAiProposal> selected = new ArrayList<>();
    if (wProposals == null || wProposals.isDisposed()) {
      return selected;
    }
    for (int i = 0; i < wProposals.getItemCount() && i < proposals.size(); i++) {
      TableItem item = wProposals.getItem(i);
      if (item.getChecked()) {
        selected.add(proposals.get(i));
      }
    }
    return selected;
  }

  private void updatePreview() {
    StringBuilder preview = new StringBuilder();
    List<DvAiProposal> selected = readSelectedFromWidgets();
    for (int i = 0; i < selected.size(); i++) {
      if (i > 0) {
        preview.append("\n\n---\n\n");
      }
      preview.append(DvAiProposalApplier.preview(selected.get(i)));
    }
    wPreview.setText(preview.toString());
  }

  private void applySelected() {
    selectedProposals = readSelectedFromWidgets();
    applied = !selectedProposals.isEmpty();

    WindowProperty windowProperty = new WindowProperty(shell);
    PropsUi.getInstance().setScreen(windowProperty);

    shell.dispose();
  }

  private void cancel() {
    applied = false;
    selectedProposals = List.of();
    shell.dispose();
  }

  private static String proposalLabel(
      DvAiProposal proposal, DvAiProposalValidator.ValidationResult validation) {
    String description =
        proposal.getDescription() != null ? proposal.getDescription() : proposal.getType().name();
    StringBuilder label = new StringBuilder();
    label.append('[').append(proposal.getRiskLevel()).append("] ").append(description);
    if (validation != null
        && validation.getStatus() == DvAiProposalValidator.Status.BLOCKED
        && !Utils.isEmpty(validation.getMessage())) {
      label.append(" — ").append(validation.getMessage());
    } else if (validation != null
        && validation.getStatus() == DvAiProposalValidator.Status.WARNING
        && !Utils.isEmpty(validation.getMessage())) {
      label.append(" (").append(validation.getMessage()).append(')');
    }
    return label.toString();
  }
}
