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
import java.util.function.Function;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.ai.DvAiProposal;
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

/** Review and selectively apply AI-proposed model changes (DV, BV, or DM). */
public class ModelAiProposalReviewDialog {

  public enum Status {
    OK,
    WARNING,
    BLOCKED
  }

  public record ValidationResult(DvAiProposal proposal, Status status, String message) {}

  private static final Class<?> PKG = ModelAiProposalReviewDialog.class;
  private static final String BLOCKED_ITEM_KEY = "blocked";

  private final Shell parent;
  private final List<DvAiProposal> proposals;
  private final Function<DvAiProposal, String> previewFn;
  private final Map<DvAiProposal, ValidationResult> validationByProposal = new HashMap<>();
  private Shell shell;
  private Table wProposals;
  private Text wPreview;
  private boolean applied;
  private List<DvAiProposal> selectedProposals = List.of();

  public ModelAiProposalReviewDialog(
      Shell parent,
      List<DvAiProposal> proposals,
      List<ValidationResult> validationResults,
      Function<DvAiProposal, String> previewFn) {
    this.parent = parent;
    this.proposals = proposals != null ? proposals : List.of();
    this.previewFn = previewFn != null ? previewFn : proposal -> "";
    if (validationResults != null) {
      for (ValidationResult result : validationResults) {
        if (result != null && result.proposal() != null) {
          validationByProposal.put(result.proposal(), result);
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
    DialogHelpSupport.createHelpButton(shell, HelpTopics.MODEL_AI_PROPOSAL_REVIEW);

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

    wPreview = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.READ_ONLY | SWT.WRAP);
    PropsUi.setLook(wPreview);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, margin);
    fdPreview.right = new FormAttachment(100, -margin);
    fdPreview.top = new FormAttachment(wlPreview, margin);
    fdPreview.bottom = new FormAttachment(wApply, -margin);
    wPreview.setLayoutData(fdPreview);

    FormData fdList = new FormData();
    fdList.left = new FormAttachment(0, margin);
    fdList.right = new FormAttachment(100, -margin);
    fdList.top = new FormAttachment(wlList, margin);
    fdList.bottom = new FormAttachment(wlPreview, -margin);
    wProposals.setLayoutData(fdList);

    wProposals.addListener(SWT.Selection, e -> updatePreview());

    for (DvAiProposal proposal : this.proposals) {
      TableItem item = new TableItem(wProposals, SWT.NONE);
      ValidationResult validation = validationByProposal.get(proposal);
      String label = formatProposalLabel(proposal, validation);
      item.setText(label);
      boolean blocked = validation != null && validation.status() == Status.BLOCKED;
      item.setChecked(!blocked);
      if (blocked) {
        item.setData(BLOCKED_ITEM_KEY, Boolean.TRUE);
        item.setGrayed(true);
      }
    }

    if (!this.proposals.isEmpty()) {
      wProposals.setSelection(0);
      updatePreview();
    }

    BaseTransformDialog.setSize(shell, 700, 520);
    shell.open();
    while (!shell.isDisposed()) {
      if (!parent.getDisplay().readAndDispatch()) {
        parent.getDisplay().sleep();
      }
    }
    return applied;
  }

  public List<DvAiProposal> getSelectedProposals() {
    return selectedProposals;
  }

  private void applySelected() {
    List<DvAiProposal> selected = new ArrayList<>();
    for (TableItem item : wProposals.getItems()) {
      if (item.getChecked() && item.getData(BLOCKED_ITEM_KEY) == null) {
        int index = wProposals.indexOf(item);
        if (index >= 0 && index < proposals.size()) {
          selected.add(proposals.get(index));
        }
      }
    }
    selectedProposals = selected;
    applied = true;
    dispose();
  }

  private void cancel() {
    applied = false;
    dispose();
  }

  private void dispose() {
    WindowProperty winProp = new WindowProperty(shell);
    PropsUi.getInstance().setSessionScreen(winProp);
    shell.dispose();
  }

  private void updatePreview() {
    List<DvAiProposal> selected = new ArrayList<>();
    for (TableItem item : wProposals.getSelection()) {
      int index = wProposals.indexOf(item);
      if (index >= 0 && index < proposals.size()) {
        selected.add(proposals.get(index));
      }
    }
    if (selected.isEmpty() && wProposals.getSelectionIndex() >= 0) {
      int index = wProposals.getSelectionIndex();
      if (index < proposals.size()) {
        selected.add(proposals.get(index));
      }
    }
    StringBuilder preview = new StringBuilder();
    for (int i = 0; i < selected.size(); i++) {
      if (i > 0) {
        preview.append("\n\n---\n\n");
      }
      preview.append(previewFn.apply(selected.get(i)));
      ValidationResult validation = validationByProposal.get(selected.get(i));
      if (validation != null && !Utils.isEmpty(validation.message())) {
        preview.append("\n\nValidation: ").append(validation.status());
        preview.append(" — ").append(validation.message());
      }
    }
    wPreview.setText(preview.toString());
  }

  private static String formatProposalLabel(DvAiProposal proposal, ValidationResult validation) {
    String description =
        proposal != null && !Utils.isEmpty(proposal.getDescription())
            ? proposal.getDescription()
            : proposal != null && proposal.getType() != null
                ? proposal.getType().name()
                : "Proposal";
    if (validation == null || validation.status() == Status.OK) {
      return description;
    }
    return "[" + validation.status() + "] " + description;
  }
}
