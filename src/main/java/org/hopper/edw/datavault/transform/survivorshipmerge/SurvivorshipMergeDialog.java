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
package org.hopper.edw.datavault.transform.survivorshipmerge;

import org.apache.hop.core.Const;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

/** Dialog for {@link SurvivorshipMergeMeta}. Keys and rules sit in groups above the buttons. */
public class SurvivorshipMergeDialog extends BaseTransformDialog {

  private static final Class<?> PKG = SurvivorshipMergeMeta.class;

  private final SurvivorshipMergeMeta input;
  private GuiCompositeWidgets widgets;
  private TableView wKeys;
  private TableView wRules;
  private boolean loading;

  public SurvivorshipMergeDialog(
      Shell parent, IVariables variables, SurvivorshipMergeMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "SurvivorshipMerge.Name"));
    changed = input.hasChanged();
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();
    widgets =
        GuiCompositeWidgets.addScrolledComposite(
            shell,
            variables,
            wTransformName,
            wOk,
            SurvivorshipMergeMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
            input,
            compositeWidgets -> {
              widgets = compositeWidgets;
              compositeWidgets.registerExtraGroup(
                  BaseMessages.getString(PKG, "SurvivorshipMerge.Keys.Label"),
                  "0400",
                  null,
                  this::addKeys);
              compositeWidgets.registerExtraGroup(
                  BaseMessages.getString(PKG, "SurvivorshipMerge.Rules.Label"),
                  "0500",
                  null,
                  this::addRules);
            });
    loading = true;
    fillKeys();
    fillRules();
    loading = false;
    input.setChanged(changed);
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return transformName;
  }

  private void addKeys(Composite parent) {
    Label label = label(parent, "SurvivorshipMerge.Keys.Label");
    wKeys =
        table(
            parent,
            label,
            new ColumnInfo[] {column("SurvivorshipMerge.Column.Key")},
            input.getKeys() == null ? 1 : input.getKeys().size());
  }

  private void addRules(Composite parent) {
    Label label = label(parent, "SurvivorshipMerge.Rules.Label");
    wRules =
        table(
            parent,
            label,
            new ColumnInfo[] {
              column("SurvivorshipMerge.Column.Field"),
              column("SurvivorshipMerge.Column.Source"),
              column("SurvivorshipMerge.Column.Rank"),
              column(
                  "SurvivorshipMerge.Column.NullPolicy", new String[] {"", "inherit", "apply"}),
              column(
                  "SurvivorshipMerge.Column.Operation",
                  new String[] {"", "upsert", "delete", "seed"}),
              column("SurvivorshipMerge.Column.PresentFlag")
            },
            input.getRules() == null ? 1 : input.getRules().size());
  }

  private void fillKeys() {
    wKeys.clearAll(false);
    if (input.getKeys() == null) {
      return;
    }
    for (SurvivorshipMergeKey key : input.getKeys()) {
      if (key == null) {
        continue;
      }
      TableItem item = new TableItem(wKeys.table, SWT.NONE);
      item.setText(1, Const.NVL(key.getFieldName(), ""));
    }
    wKeys.optimizeTableView();
  }

  private void fillRules() {
    wRules.clearAll(false);
    if (input.getRules() == null) {
      return;
    }
    for (SurvivorshipMergeRule rule : input.getRules()) {
      if (rule == null) {
        continue;
      }
      TableItem item = new TableItem(wRules.table, SWT.NONE);
      item.setText(1, Const.NVL(rule.getFieldName(), ""));
      item.setText(2, Const.NVL(rule.getSourceId(), ""));
      item.setText(3, Const.NVL(rule.getRank(), ""));
      item.setText(4, Const.NVL(rule.getNullPolicy(), ""));
      item.setText(5, Const.NVL(rule.getOperation(), ""));
      item.setText(6, Const.NVL(rule.getPresentFlagField(), ""));
    }
    wRules.optimizeTableView();
  }

  private void ok() {
    widgets.getWidgetsContents(input, SurvivorshipMergeMeta.GUI_PLUGIN_ELEMENT_PARENT_ID);
    if (input.getKeys() == null) {
      input.setKeys(new java.util.ArrayList<>());
    }
    if (input.getRules() == null) {
      input.setRules(new java.util.ArrayList<>());
    }
    input.getKeys().clear();
    for (TableItem item : wKeys.getNonEmptyItems()) {
      String name = Const.trim(item.getText(1));
      if (name != null && !name.isEmpty()) {
        input.getKeys().add(new SurvivorshipMergeKey(name));
      }
    }
    input.getRules().clear();
    for (TableItem item : wRules.getNonEmptyItems()) {
      String fieldName = Const.trim(item.getText(1));
      String source = Const.trim(item.getText(2));
      if (fieldName == null || fieldName.isEmpty() || source == null || source.isEmpty()) {
        continue;
      }
      input
          .getRules()
          .add(
              new SurvivorshipMergeRule(
                  fieldName,
                  source,
                  Const.trim(item.getText(3)),
                  Const.trim(item.getText(4)),
                  Const.trim(item.getText(5)),
                  Const.trim(item.getText(6))));
    }
    transformName = wTransformName.getText();
    input.setChanged();
    dispose();
  }

  private void cancel() {
    input.setChanged(changed);
    transformName = null;
    dispose();
  }

  private Label label(Composite parent, String key) {
    Label label = new Label(parent, SWT.LEFT);
    label.setText(BaseMessages.getString(PKG, key));
    org.apache.hop.ui.core.PropsUi.setLook(label);
    FormData data = new FormData();
    data.left = new FormAttachment(0, 0);
    data.top = new FormAttachment(0, 0);
    data.right = new FormAttachment(100, 0);
    label.setLayoutData(data);
    return label;
  }

  private ColumnInfo column(String key) {
    return new ColumnInfo(BaseMessages.getString(PKG, key), ColumnInfo.COLUMN_TYPE_TEXT, false);
  }

  private ColumnInfo column(String key, String[] values) {
    return new ColumnInfo(BaseMessages.getString(PKG, key), ColumnInfo.COLUMN_TYPE_CCOMBO, values, false);
  }

  private TableView table(Composite parent, Label label, ColumnInfo[] columns, int rows) {
    TableView table =
        new TableView(
            variables,
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(rows, 1),
            false,
            event -> {
              if (!loading) {
                input.setChanged();
              }
            },
            props);
    FormData data = new FormData();
    data.left = new FormAttachment(0, 0);
    data.top = new FormAttachment(label, margin);
    data.right = new FormAttachment(100, 0);
    data.bottom = new FormAttachment(100, 0);
    table.setLayoutData(data);
    return table;
  }
}
