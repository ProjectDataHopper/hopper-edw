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
package org.apache.hop.datavault.hopgui.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyledTextComp;
import org.apache.hop.datavault.metrics.live.PipelineLiveMetrics;
import org.apache.hop.datavault.metrics.live.TransformLiveMetrics;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveRegistry;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveSnapshot;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/** Shows a live snapshot of an in-flight model update run. */
public final class UpdateRunLiveAnalysisDialog {

  private static final Class<?> PKG = UpdateRunLiveAnalysisDialog.class;

  private final Shell parent;
  private final IVariables variables;
  private final String metricsRunId;

  private Shell shell;
  private MarkdownStyledTextComp wSummary;
  private UpdateRunLiveSnapshot currentSnapshot;

  private UpdateRunLiveAnalysisDialog(Shell parent, IVariables variables, String metricsRunId) {
    this.parent = parent;
    this.variables = variables;
    this.metricsRunId = metricsRunId;
  }

  public static void open(Shell parent, IVariables variables, String metricsRunId) {
    if (parent == null || parent.isDisposed() || Utils.isEmpty(metricsRunId)) {
      return;
    }
    Optional<UpdateRunLiveSnapshot> snapshot = UpdateRunLiveRegistry.findByRunId(metricsRunId);
    if (snapshot.isEmpty()) {
      MessageBox box = new MessageBox(parent, SWT.ICON_INFORMATION | SWT.OK);
      box.setMessage(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.NoActiveRun"));
      box.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Title"));
      box.open();
      return;
    }
    new UpdateRunLiveAnalysisDialog(parent, variables, metricsRunId).openDialog(snapshot.get());
  }

  private void openDialog(UpdateRunLiveSnapshot snapshot) {
    currentSnapshot = snapshot;

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    shell.setImage(GuiResource.getInstance().getImageHop());
    shell.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Title"));
    PropsUi.setLook(shell);
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    Button wRefresh = new Button(shell, SWT.PUSH);
    wRefresh.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Refresh"));
    PropsUi.setLook(wRefresh);
    wRefresh.addListener(SWT.Selection, event -> refreshSnapshot());

    Button wCopy = new Button(shell, SWT.PUSH);
    wCopy.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.CopyDiagnostics"));
    PropsUi.setLook(wCopy);
    wCopy.addListener(SWT.Selection, event -> copyDiagnostics());

    Button wTransforms = new Button(shell, SWT.PUSH);
    wTransforms.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.ViewTransforms"));
    PropsUi.setLook(wTransforms);
    wTransforms.addListener(SWT.Selection, event -> showTransforms());

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    PropsUi.setLook(wClose);
    wClose.addListener(SWT.Selection, event -> shell.dispose());

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wRefresh, wCopy, wTransforms, wClose}, margin, null);

    wSummary = new MarkdownStyledTextComp(shell, SWT.NONE);
    updateSummaryText();
    FormData fdSummary = new FormData();
    fdSummary.left = new FormAttachment(0, margin);
    fdSummary.right = new FormAttachment(100, -margin);
    fdSummary.top = new FormAttachment(0, margin);
    fdSummary.bottom = new FormAttachment(wRefresh, -margin);
    wSummary.setLayoutData(fdSummary);

    shell.addListener(SWT.Close, event -> shell.dispose());

    BaseTransformDialog.setSize(shell, 700, 480);
    shell.open();

    Display display = parent.getDisplay();
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

  private void refreshSnapshot() {
    Optional<UpdateRunLiveSnapshot> snapshot = UpdateRunLiveRegistry.findByRunId(metricsRunId);
    if (snapshot.isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setMessage(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.NoActiveRun"));
      box.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Title"));
      box.open();
      return;
    }
    currentSnapshot = snapshot.get();
    updateSummaryText();
  }

  private void updateSummaryText() {
    if (wSummary == null || wSummary.isDisposed() || currentSnapshot == null) {
      return;
    }
    wSummary.setMarkdown(UpdateRunLiveDiagnosticsFormatter.formatMarkdown(currentSnapshot));
    wSummary.scrollToTop();
  }

  private void copyDiagnostics() {
    if (currentSnapshot == null) {
      return;
    }
    String text = UpdateRunLiveDiagnosticsFormatter.formatMarkdown(currentSnapshot);
    Clipboard clipboard = new Clipboard(shell.getDisplay());
    clipboard.setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
    clipboard.dispose();
  }

  private void showTransforms() {
    if (currentSnapshot == null) {
      return;
    }
    try {
      IRowMeta rowMeta = buildTransformRowMeta();
      List<Object[]> rows = buildTransformRows(currentSnapshot, rowMeta);
      new ShowRowsDialog(
              shell,
              variables,
              BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Transforms.Title"),
              BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Transforms.Message"),
              rowMeta,
              rows)
          .open();
    } catch (HopException e) {
      MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      box.setMessage(Const.getStackTracker(e));
      box.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Title"));
      box.open();
    }
  }

  private static IRowMeta buildTransformRowMeta() throws HopException {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("Pipeline", IValueMeta.TYPE_STRING));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("Table", IValueMeta.TYPE_STRING));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("Transform", IValueMeta.TYPE_STRING));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("Plugin", IValueMeta.TYPE_STRING));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("Running", IValueMeta.TYPE_BOOLEAN));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("RowsIn", IValueMeta.TYPE_INTEGER));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("RowsOut", IValueMeta.TYPE_INTEGER));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("BufferIn", IValueMeta.TYPE_INTEGER));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("BufferOut", IValueMeta.TYPE_INTEGER));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("StallSec", IValueMeta.TYPE_INTEGER));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("Status", IValueMeta.TYPE_STRING));
    return rowMeta;
  }

  private static List<Object[]> buildTransformRows(
      UpdateRunLiveSnapshot snapshot, IRowMeta rowMeta) {
    List<Object[]> rows = new ArrayList<>();
    if (snapshot.getPipelines() == null) {
      return rows;
    }
    for (PipelineLiveMetrics pipeline : snapshot.getPipelines()) {
      if (pipeline == null || pipeline.getTransforms() == null) {
        continue;
      }
      for (TransformLiveMetrics transform : pipeline.getTransforms()) {
        if (transform == null) {
          continue;
        }
        Object[] row = new Object[rowMeta.size()];
        row[0] = pipeline.getPipelineName();
        row[1] = pipeline.getElementName();
        row[2] = transform.getTransformName();
        row[3] = transform.getPluginId();
        row[4] = transform.isRunning();
        row[5] = transform.getRowsRead();
        row[6] = transform.getRowsWritten();
        row[7] = transform.getBufferIn();
        row[8] = transform.getBufferOut();
        row[9] = transform.getSecondsSinceLastProgress();
        row[10] = transform.getStatus();
        rows.add(row);
      }
    }
    return rows;
  }
}
