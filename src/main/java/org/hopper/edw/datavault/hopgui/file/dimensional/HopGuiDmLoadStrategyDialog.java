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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.metadata.dimensional.DmLoadStrategy;
import org.hopper.edw.datavault.metadata.dimensional.DmLoadStrategyType;
import org.hopper.edw.datavault.metadata.dimensional.DmLoadValueBinding;

/** Edits the source load strategy stored on a dimensional table. */
public class HopGuiDmLoadStrategyDialog {

  private static final Class<?> PKG = DmLoadStrategy.class;

  private final Shell parent;
  private final IVariables variables;
  private final DmLoadStrategy strategy;
  private Shell shell;
  private GuiCompositeWidgets widgets;
  private boolean accepted;

  public HopGuiDmLoadStrategyDialog(Shell parent, IVariables variables, DmLoadStrategy strategy) {
    this.parent = parent;
    this.variables = variables;
    this.strategy = strategy;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DmLoadStrategy.Dialog.Title"));
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
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    widgets =
        GuiCompositeWidgets.addScrolledComposite(
            shell, variables, null, wOk, DmLoadStrategy.GUI_PLUGIN_ELEMENT_PARENT_ID, strategy);
    widgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetsCreated(GuiCompositeWidgets compositeWidgets) {
            refreshEnabled(compositeWidgets);
          }

          @Override
          public void widgetsPopulated(GuiCompositeWidgets compositeWidgets) {
            refreshEnabled(compositeWidgets);
          }

          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            refreshEnabled(compositeWidgets);
          }
        });
    refreshEnabled(widgets);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return accepted;
  }

  private void ok() {
    if (widgets != null) {
      widgets.getWidgetsContents(strategy, DmLoadStrategy.GUI_PLUGIN_ELEMENT_PARENT_ID);
    }
    accepted = true;
    dispose();
  }

  private void cancel() {
    accepted = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }

  private void refreshEnabled(GuiCompositeWidgets compositeWidgets) {
    if (compositeWidgets == null || compositeWidgets.getWidgetsMap() == null) {
      return;
    }
    DmLoadStrategyType type =
        DmLoadStrategyType.lookupDescription(comboText(compositeWidgets, "strategy"));
    DmLoadValueBinding binding =
        DmLoadValueBinding.lookupDescription(comboText(compositeWidgets, "valueBinding"));
    boolean baseline = type.limitsExtract();
    setEnabled(compositeWidgets, "baselineKind", baseline);
    setEnabled(compositeWidgets, "baselineColumn", baseline);
    boolean jdbc = binding.includesJdbcParameters();
    setEnabled(compositeWidgets, "jdbcModeParameter", jdbc);
    setEnabled(compositeWidgets, "jdbcFromParameter", jdbc);
    setEnabled(compositeWidgets, "jdbcToParameter", jdbc);
    boolean late = type == DmLoadStrategyType.INCREMENTAL_LATE_ARRIVING;
    setEnabled(compositeWidgets, "lateArrivingUnit", late);
    setEnabled(compositeWidgets, "lateArrivingCount", late);
  }

  private static void setEnabled(GuiCompositeWidgets compositeWidgets, String id, boolean enabled) {
    Control widget = compositeWidgets.getWidgetsMap().get(id);
    if (widget != null && !widget.isDisposed()) {
      widget.setEnabled(enabled);
    }
    if (compositeWidgets.getLabelsMap() != null) {
      Control label = compositeWidgets.getLabelsMap().get(id);
      if (label != null && !label.isDisposed()) {
        label.setEnabled(enabled);
      }
    }
  }

  private static String comboText(GuiCompositeWidgets compositeWidgets, String id) {
    Control control = compositeWidgets.getWidgetsMap().get(id);
    if (control instanceof Combo combo && !combo.isDisposed()) {
      return combo.getText();
    }
    return "";
  }
}
