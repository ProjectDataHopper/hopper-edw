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
package org.hopper.edw.datavault.hopgui.opsanalytics;

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyPerspective;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsDialogSupport;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsDialogSupport.Probe;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsDialogSupport.ReplaceDecision;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsProjectBuilder;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsRequest;

/** Create operational analytics from the EDW Journey toolbar or the Tools menu. */
public class OpsAnalyticsDialog {

  private static final Class<?> PKG = OpsAnalyticsDialog.class;

  private final HopGui hopGui;
  private final OpsAnalyticsDialogOptions options = new OpsAnalyticsDialogOptions();
  private Shell shell;
  private GuiCompositeWidgets widgets;
  private boolean accepted;

  public OpsAnalyticsDialog(HopGui hopGui) {
    this.hopGui = hopGui;
  }

  public static void open(HopGui hopGui) {
    if (hopGui == null) {
      return;
    }
    try {
      new OpsAnalyticsDialog(hopGui).openInternal();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "OpsAnalyticsDialog.Error.Title"),
          BaseMessages.getString(PKG, "OpsAnalyticsDialog.Error.Message"),
          e);
    }
  }

  private void openInternal() throws HopException {
    shell = new Shell(hopGui.getShell(), BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "OpsAnalyticsDialog.Title"));
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);

    int margin = PropsUi.getMargin();
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.OPS_ANALYTICS);
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    widgets =
        GuiCompositeWidgets.addScrolledComposite(
            shell,
            hopGui.getVariables(),
            null,
            wOk,
            OpsAnalyticsDialogOptions.GUI_PLUGIN_ELEMENT_PARENT_ID,
            options);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    if (accepted) {
      generate();
    }
  }

  private void ok() {
    if (widgets == null || shell == null || shell.isDisposed()) {
      return;
    }
    widgets.getWidgetsContents(options, OpsAnalyticsDialogOptions.GUI_PLUGIN_ELEMENT_PARENT_ID);
    String errorKey = OpsAnalyticsDialogSupport.validate(options);
    if (errorKey != null) {
      message(SWT.ICON_WARNING | SWT.OK, BaseMessages.getString(PKG, errorKey));
      return;
    }
    try {
      boolean exists =
          OpsAnalyticsDialogSupport.folderExists(options.getOutputFolder(), hopGui.getVariables());
      ReplaceDecision decision =
          OpsAnalyticsDialogSupport.replaceDecision(exists, options.isReplaceExisting());
      if (decision == ReplaceDecision.REFUSE) {
        message(
            SWT.ICON_WARNING | SWT.OK,
            BaseMessages.getString(PKG, "OpsAnalyticsDialog.Replace.Refuse"));
        return;
      }
      if (decision == ReplaceDecision.CONFIRM) {
        int answer =
            message(
                SWT.ICON_QUESTION | SWT.YES | SWT.NO,
                BaseMessages.getString(PKG, "OpsAnalyticsDialog.Replace.Confirm"));
        if (answer != SWT.YES) {
          return;
        }
      }
      if (!confirmProbe()) {
        return;
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "OpsAnalyticsDialog.Error.Title"),
          BaseMessages.getString(PKG, "OpsAnalyticsDialog.Error.Message"),
          e);
      return;
    }
    accepted = true;
    shell.dispose();
  }

  private boolean confirmProbe() throws HopException {
    IHopMetadataProvider provider = hopGui.getMetadataProvider();
    IVariables variables = hopGui.getVariables();
    DatabaseMeta databaseMeta = null;
    if (provider != null) {
      databaseMeta = provider.getSerializer(DatabaseMeta.class).load(options.getConnectionName());
    }
    Probe probe =
        OpsAnalyticsDialogSupport.probe(
            databaseMeta, variables, options.getSchemaName(), options.isIncludeExecutions());
    if (probe.warning() != null) {
      int answer =
          message(
              SWT.ICON_WARNING | SWT.OK | SWT.CANCEL,
              BaseMessages.getString(PKG, "OpsAnalyticsDialog.Probe.Warning", probe.warning()));
      return answer == SWT.OK;
    }
    if (!probe.missingTables().isEmpty()) {
      int answer =
          message(
              SWT.ICON_WARNING | SWT.OK | SWT.CANCEL,
              BaseMessages.getString(
                  PKG,
                  "OpsAnalyticsDialog.Probe.Missing",
                  String.join(", ", probe.missingTables())));
      return answer == SWT.OK;
    }
    return true;
  }

  private void generate() throws HopException {
    OpsAnalyticsRequest request =
        OpsAnalyticsDialogSupport.toRequest(
            options, hopGui.getVariables(), hopGui.getMetadataProvider());
    var project =
        OpsAnalyticsProjectBuilder.write(
            request, hopGui.getVariables(), hopGui.getMetadataProvider());
    String source = hopGui.getVariables().resolve(project.sourceModel());
    try {
      hopGui.fileDelegate.fileOpen(source);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to open " + source, e);
    }
    EdwJourneyPerspective journey = EdwJourneyPerspective.getInstance();
    if (journey != null) {
      journey.select(request.getGroupName(), null);
    }
  }

  private int message(int style, String text) {
    MessageBox box = new MessageBox(shell, style);
    box.setText(BaseMessages.getString(PKG, "OpsAnalyticsDialog.Title"));
    box.setMessage(text);
    return box.open();
  }

  private void cancel() {
    accepted = false;
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }
}
