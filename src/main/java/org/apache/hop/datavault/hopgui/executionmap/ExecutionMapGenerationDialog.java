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
package org.apache.hop.datavault.hopgui.executionmap;

import lombok.Getter;
import org.apache.hop.datavault.executionmap.CrawlOptions;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/** Collects crawl options before generating or refreshing an execution map. */
public class ExecutionMapGenerationDialog {
  private static final Class<?> PKG = ExecutionMapGenerationDialog.class;

  public enum Purpose {
    GENERATE,
    REFRESH
  }

  @Getter
  public static final class Result {
    private final CrawlOptions crawlOptions;
    private final boolean exportLineage;

    public Result(CrawlOptions crawlOptions, boolean exportLineage) {
      this.crawlOptions = crawlOptions;
      this.exportLineage = exportLineage;
    }
  }

  private final Shell parent;
  private final Purpose purpose;

  private Shell shell;
  private Button wIncludeGeneratedPipelines;
  private Button wIncludeDatasetNodes;
  private Button wCaptureSnapshots;
  private Button wFollowNestedWorkflows;
  private Button wFollowNestedPipelines;
  private Button wIncludeWorkflowActions;
  private Button wIncludePipelineTransforms;
  private Button wExportLineage;

  private Result result;
  private int margin;

  public ExecutionMapGenerationDialog(Shell parent, Purpose purpose) {
    this.parent = parent;
    this.purpose = purpose != null ? purpose : Purpose.GENERATE;
  }

  public static Result open(Shell parent, Purpose purpose) {
    ExecutionMapGenerationDialog dialog = new ExecutionMapGenerationDialog(parent, purpose);
    return dialog.openInternal() ? dialog.result : null;
  }

  private boolean openInternal() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            purpose == Purpose.REFRESH
                ? "ExecutionMapGenerationDialog.Title.Refresh"
                : "ExecutionMapGenerationDialog.Title.Generate"));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    margin = PropsUi.getMargin();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.EXECUTION_MAP_GENERATION);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Label wlIntro = new Label(shell, SWT.WRAP);
    wlIntro.setText(
        BaseMessages.getString(
            PKG,
            purpose == Purpose.REFRESH
                ? "ExecutionMapGenerationDialog.Intro.Refresh"
                : "ExecutionMapGenerationDialog.Intro.Generate"));
    PropsUi.setLook(wlIntro);
    wlIntro.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    Control last = wlIntro;

    last =
        addSectionLabel(
            last, BaseMessages.getString(PKG, "ExecutionMapGenerationDialog.Section.Content"));
    last =
        addCheckbox(
            last,
            "ExecutionMapGenerationDialog.IncludeGeneratedPipelines",
            "ExecutionMapGenerationDialog.IncludeGeneratedPipelines.ToolTip",
            true);
    wIncludeGeneratedPipelines = (Button) last;
    last =
        addCheckbox(
            last,
            "ExecutionMapGenerationDialog.IncludeDatasetNodes",
            "ExecutionMapGenerationDialog.IncludeDatasetNodes.ToolTip",
            true);
    wIncludeDatasetNodes = (Button) last;
    last =
        addCheckbox(
            last,
            "ExecutionMapGenerationDialog.CaptureSnapshots",
            "ExecutionMapGenerationDialog.CaptureSnapshots.ToolTip",
            true);
    wCaptureSnapshots = (Button) last;

    last =
        addSectionLabel(
            last, BaseMessages.getString(PKG, "ExecutionMapGenerationDialog.Section.Traversal"));
    last =
        addCheckbox(
            last,
            "ExecutionMapGenerationDialog.FollowNestedWorkflows",
            "ExecutionMapGenerationDialog.FollowNestedWorkflows.ToolTip",
            true);
    wFollowNestedWorkflows = (Button) last;
    last =
        addCheckbox(
            last,
            "ExecutionMapGenerationDialog.FollowNestedPipelines",
            "ExecutionMapGenerationDialog.FollowNestedPipelines.ToolTip",
            true);
    wFollowNestedPipelines = (Button) last;

    last =
        addSectionLabel(
            last, BaseMessages.getString(PKG, "ExecutionMapGenerationDialog.Section.Detail"));
    last =
        addCheckbox(
            last,
            "ExecutionMapGenerationDialog.IncludeWorkflowActions",
            "ExecutionMapGenerationDialog.IncludeWorkflowActions.ToolTip",
            false);
    wIncludeWorkflowActions = (Button) last;
    last =
        addCheckbox(
            last,
            "ExecutionMapGenerationDialog.IncludePipelineTransforms",
            "ExecutionMapGenerationDialog.IncludePipelineTransforms.ToolTip",
            false);
    wIncludePipelineTransforms = (Button) last;

    if (purpose == Purpose.GENERATE) {
      last =
          addSectionLabel(
              last, BaseMessages.getString(PKG, "ExecutionMapGenerationDialog.Section.Output"));
      last =
          addCheckbox(
              last,
              "ExecutionMapGenerationDialog.ExportLineage",
              "ExecutionMapGenerationDialog.ExportLineage.ToolTip",
              false);
      wExportLineage = (Button) last;
    }

    shell.layout(true, true);
    BaseTransformDialog.setSize(shell, 520, purpose == Purpose.GENERATE ? 460 : 420);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return result != null;
  }

  private Control addSectionLabel(Control top, String text) {
    Label label = new Label(shell, SWT.LEFT);
    label.setText(text);
    PropsUi.setLook(label);
    label.setLayoutData(new FormDataBuilder().left().top(top, 2 * margin).right().result());
    return label;
  }

  private Control addCheckbox(Control top, String labelKey, String tooltipKey, boolean selected) {
    Button button = new Button(shell, SWT.CHECK);
    button.setText(BaseMessages.getString(PKG, labelKey));
    button.setToolTipText(BaseMessages.getString(PKG, tooltipKey));
    button.setSelection(selected);
    PropsUi.setLook(button);
    button.setLayoutData(new FormDataBuilder().left().top(top, margin).right().result());
    return button;
  }

  private void ok() {
    result =
        new Result(
            CrawlOptions.builder()
                .includeGeneratedPipelines(wIncludeGeneratedPipelines.getSelection())
                .includeDatasetNodes(wIncludeDatasetNodes.getSelection())
                .captureSnapshots(wCaptureSnapshots.getSelection())
                .followNestedWorkflows(wFollowNestedWorkflows.getSelection())
                .followNestedPipelines(wFollowNestedPipelines.getSelection())
                .includeWorkflowActions(wIncludeWorkflowActions.getSelection())
                .includePipelineTransforms(wIncludePipelineTransforms.getSelection())
                .build(),
            wExportLineage != null && wExportLineage.getSelection());
    dispose();
  }

  private void cancel() {
    result = null;
    dispose();
  }

  private void dispose() {
    shell.dispose();
  }
}
