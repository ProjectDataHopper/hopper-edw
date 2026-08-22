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
package org.hopper.edw.datavault.metadata.targettypemapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingPatternSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Editor for project-level {@link TargetTypeMappingMeta} profiles. */
@GuiPlugin(description = "Editor for Target Type Mapping metadata")
public class TargetTypeMappingMetaEditor extends MetadataEditor<TargetTypeMappingMeta> {

  private static final Class<?> PKG = TargetTypeMappingMeta.class;

  private Text wName;
  private Text wDescription;
  private Combo wTargetDatabase;
  private TableView wRules;
  private Combo wPreviewHopType;
  private Text wPreviewLength;
  private Text wPreviewPrecision;
  private Text wPreviewFieldName;
  private Text wPreviewResult;
  private Composite parentComposite;

  public TargetTypeMappingMetaEditor(
      HopGui hopGui,
      MetadataManager<TargetTypeMappingMeta> manager,
      TargetTypeMappingMeta metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    this.parentComposite = parent;
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Name.Label"));
    FormData fdlName = new FormData();
    fdlName.top = new FormAttachment(0, margin);
    fdlName.left = new FormAttachment(0, 0);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.top = new FormAttachment(wlName, 0, SWT.CENTER);
    fdName.left = new FormAttachment(middle, 0);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    Label wlDescription = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDescription);
    wlDescription.setText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Description.Label"));
    FormData fdlDescription = new FormData();
    fdlDescription.top = new FormAttachment(wName, margin);
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.top = new FormAttachment(wlDescription, 0, SWT.CENTER);
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);

    Label wlTarget = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlTarget);
    wlTarget.setText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.TargetDatabase.Label"));
    wlTarget.setToolTipText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.TargetDatabase.ToolTip"));
    FormData fdlTarget = new FormData();
    fdlTarget.top = new FormAttachment(wDescription, margin);
    fdlTarget.left = new FormAttachment(0, 0);
    fdlTarget.right = new FormAttachment(middle, -margin);
    wlTarget.setLayoutData(fdlTarget);

    wTargetDatabase = new Combo(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTargetDatabase);
    wTargetDatabase.setToolTipText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.TargetDatabase.ToolTip"));
    populateTargetDatabases();
    FormData fdTarget = new FormData();
    fdTarget.top = new FormAttachment(wlTarget, 0, SWT.CENTER);
    fdTarget.left = new FormAttachment(middle, 0);
    fdTarget.right = new FormAttachment(100, 0);
    wTargetDatabase.setLayoutData(fdTarget);

    CTabFolder wTabFolder = new CTabFolder(parent, SWT.BORDER);
    PropsUi.setLook(wTabFolder);
    FormData fdTabs = new FormData();
    fdTabs.top = new FormAttachment(wTargetDatabase, margin);
    fdTabs.left = new FormAttachment(0, 0);
    fdTabs.right = new FormAttachment(100, 0);
    fdTabs.bottom = new FormAttachment(100, -margin);
    wTabFolder.setLayoutData(fdTabs);

    addRulesTab(wTabFolder, margin, props);
    addPreviewTab(wTabFolder, middle, margin);
    wTabFolder.setSelection(0);

    setWidgetsContent();
    wName.addModifyListener(e -> setChanged());
    wDescription.addModifyListener(e -> setChanged());
    wTargetDatabase.addModifyListener(e -> setChanged());
  }

  private void addRulesTab(CTabFolder tabFolder, int margin, PropsUi props) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Tab.Rules.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    FormLayout layout = new FormLayout();
    layout.marginWidth = margin;
    layout.marginHeight = margin;
    comp.setLayout(layout);
    tab.setControl(comp);

    Label wlRules = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlRules);
    wlRules.setText(BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Rules.Label"));
    wlRules.setToolTipText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Rules.ToolTip"));
    FormData fdlRules = new FormData();
    fdlRules.top = new FormAttachment(0, 0);
    fdlRules.left = new FormAttachment(0, 0);
    fdlRules.right = new FormAttachment(100, 0);
    wlRules.setLayoutData(fdlRules);

    Composite rulesHost = new Composite(comp, SWT.NONE);
    PropsUi.setLook(rulesHost);
    FormLayout rulesHostLayout = new FormLayout();
    rulesHostLayout.marginWidth = 0;
    rulesHostLayout.marginHeight = 0;
    rulesHost.setLayout(rulesHostLayout);
    FormData fdRulesHost = new FormData();
    fdRulesHost.top = new FormAttachment(wlRules, margin);
    fdRulesHost.left = new FormAttachment(0, 0);
    fdRulesHost.right = new FormAttachment(100, 0);
    fdRulesHost.bottom = new FormAttachment(100, 0);
    rulesHost.setLayoutData(fdRulesHost);

    String[] hopTypes = hopTypeChoices();
    String rulePatternTip =
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.RulePattern.ToolTip");
    String sqlTip =
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.TargetSqlType.ToolTip");
    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("Id", ColumnInfo.COLUMN_TYPE_TEXT, 80, null),
          col("Name", ColumnInfo.COLUMN_TYPE_TEXT, 100, null),
          colCcombo("Match hop type", hopTypes, 110),
          col("Min length", ColumnInfo.COLUMN_TYPE_TEXT, 80, null),
          col("Max length", ColumnInfo.COLUMN_TYPE_TEXT, 80, null),
          col("Min precision", ColumnInfo.COLUMN_TYPE_TEXT, 80, null),
          col("Max precision", ColumnInfo.COLUMN_TYPE_TEXT, 80, null),
          colCcombo("Length absent", new String[] {"Y", "N"}, 80),
          col("Match field name", ColumnInfo.COLUMN_TYPE_TEXT, 120, rulePatternTip),
          col("Target SQL type", ColumnInfo.COLUMN_TYPE_TEXT, 220, sqlTip),
          colCcombo("Enabled", new String[] {"Y", "N"}, 60)
        };

    wRules =
        new TableView(
            manager.getVariables(),
            rulesHost,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(1, getMetadata().getRules().size()),
            false,
            e -> {
              setChanged();
              refreshPreview();
            },
            props);
    FormData fdRules = new FormData();
    fdRules.top = new FormAttachment(0, 0);
    fdRules.left = new FormAttachment(0, 0);
    fdRules.right = new FormAttachment(100, 0);
    fdRules.bottom = new FormAttachment(100, 0);
    wRules.setLayoutData(fdRules);
  }

  private void addPreviewTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Tab.Preview.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    FormLayout layout = new FormLayout();
    layout.marginWidth = margin;
    layout.marginHeight = margin;
    comp.setLayout(layout);
    tab.setControl(comp);

    Label wlType = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlType);
    wlType.setText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Preview.HopType.Label"));
    FormData fdlType = new FormData();
    fdlType.top = new FormAttachment(0, 0);
    fdlType.left = new FormAttachment(0, 0);
    fdlType.right = new FormAttachment(middle, -margin);
    wlType.setLayoutData(fdlType);

    wPreviewHopType = new Combo(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPreviewHopType);
    wPreviewHopType.setItems(hopTypeChoices());
    wPreviewHopType.setText("String");
    FormData fdType = new FormData();
    fdType.top = new FormAttachment(wlType, 0, SWT.CENTER);
    fdType.left = new FormAttachment(middle, 0);
    fdType.right = new FormAttachment(100, 0);
    wPreviewHopType.setLayoutData(fdType);

    Label wlLength = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlLength);
    wlLength.setText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Preview.Length.Label"));
    FormData fdlLength = new FormData();
    fdlLength.top = new FormAttachment(wPreviewHopType, margin);
    fdlLength.left = new FormAttachment(0, 0);
    fdlLength.right = new FormAttachment(middle, -margin);
    wlLength.setLayoutData(fdlLength);

    wPreviewLength = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPreviewLength);
    wPreviewLength.setText("1");
    FormData fdLength = new FormData();
    fdLength.top = new FormAttachment(wlLength, 0, SWT.CENTER);
    fdLength.left = new FormAttachment(middle, 0);
    fdLength.right = new FormAttachment(100, 0);
    wPreviewLength.setLayoutData(fdLength);

    Label wlPrec = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlPrec);
    wlPrec.setText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Preview.Precision.Label"));
    FormData fdlPrec = new FormData();
    fdlPrec.top = new FormAttachment(wPreviewLength, margin);
    fdlPrec.left = new FormAttachment(0, 0);
    fdlPrec.right = new FormAttachment(middle, -margin);
    wlPrec.setLayoutData(fdlPrec);

    wPreviewPrecision = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPreviewPrecision);
    FormData fdPrec = new FormData();
    fdPrec.top = new FormAttachment(wlPrec, 0, SWT.CENTER);
    fdPrec.left = new FormAttachment(middle, 0);
    fdPrec.right = new FormAttachment(100, 0);
    wPreviewPrecision.setLayoutData(fdPrec);

    Label wlField = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlField);
    wlField.setText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Preview.FieldName.Label"));
    FormData fdlField = new FormData();
    fdlField.top = new FormAttachment(wPreviewPrecision, margin);
    fdlField.left = new FormAttachment(0, 0);
    fdlField.right = new FormAttachment(middle, -margin);
    wlField.setLayoutData(fdlField);

    wPreviewFieldName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPreviewFieldName);
    wPreviewFieldName.setText("sample");
    FormData fdField = new FormData();
    fdField.top = new FormAttachment(wlField, 0, SWT.CENTER);
    fdField.left = new FormAttachment(middle, 0);
    fdField.right = new FormAttachment(100, 0);
    wPreviewFieldName.setLayoutData(fdField);

    Label wlResult = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlResult);
    wlResult.setText(
        BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Preview.Result.Label"));
    FormData fdlResult = new FormData();
    fdlResult.top = new FormAttachment(wPreviewFieldName, margin);
    fdlResult.left = new FormAttachment(0, 0);
    fdlResult.right = new FormAttachment(middle, -margin);
    wlResult.setLayoutData(fdlResult);

    wPreviewResult = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wPreviewResult);
    FormData fdResult = new FormData();
    fdResult.top = new FormAttachment(wlResult, 0, SWT.CENTER);
    fdResult.left = new FormAttachment(middle, 0);
    fdResult.right = new FormAttachment(100, 0);
    wPreviewResult.setLayoutData(fdResult);

    wPreviewHopType.addModifyListener(e -> refreshPreview());
    wPreviewLength.addModifyListener(e -> refreshPreview());
    wPreviewPrecision.addModifyListener(e -> refreshPreview());
    wPreviewFieldName.addModifyListener(e -> refreshPreview());
  }

  @Override
  public void refreshOnDialogActivate() {
    if (parentComposite != null && !parentComposite.isDisposed()) {
      parentComposite.layout(true, true);
    }
    if (wRules != null && !wRules.isDisposed() && wRules.getParent() != null) {
      wRules.getParent().layout(true, true);
    }
    refreshPreview();
  }

  @Override
  protected Button createHelpButton(Shell shell) {
    return DialogHelpSupport.createHelpButton(shell, HelpTopics.TARGET_TYPE_MAPPING);
  }

  @Override
  public void setWidgetsContent() {
    TargetTypeMappingMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wTargetDatabase.setText(Const.NVL(meta.getTargetDatabase(), ""));

    wRules.table.removeAll();
    for (TargetTypeMappingRule rule : meta.getRules()) {
      if (rule == null) {
        continue;
      }
      TableItem item = new TableItem(wRules.table, SWT.NONE);
      item.setText(1, Const.NVL(rule.getId(), ""));
      item.setText(2, Const.NVL(rule.getName(), ""));
      item.setText(3, Const.NVL(rule.getMatchHopType(), ""));
      item.setText(4, Const.NVL(rule.getMatchMinLength(), ""));
      item.setText(5, Const.NVL(rule.getMatchMaxLength(), ""));
      item.setText(6, Const.NVL(rule.getMatchMinPrecision(), ""));
      item.setText(7, Const.NVL(rule.getMatchMaxPrecision(), ""));
      item.setText(8, rule.isMatchLengthAbsent() ? "Y" : "N");
      item.setText(9, Const.NVL(rule.getMatchFieldNamePattern(), ""));
      item.setText(10, Const.NVL(rule.getTargetSqlType(), ""));
      item.setText(11, rule.isEnabled() ? "Y" : "N");
    }
    if (wRules.table.getItemCount() == 0) {
      new TableItem(wRules.table, SWT.NONE);
    }
    wRules.setRowNums();
    wRules.optWidth(true);
    refreshPreview();
  }

  @Override
  public void getWidgetsContent(TargetTypeMappingMeta meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    meta.setTargetDatabase(wTargetDatabase.getText());
    meta.setRules(readRulesFromTable());
  }

  private List<TargetTypeMappingRule> readRulesFromTable() {
    List<TargetTypeMappingRule> rules = new ArrayList<>();
    if (wRules == null) {
      return rules;
    }
    for (int i = 0; i < wRules.nrNonEmpty(); i++) {
      TableItem item = wRules.getNonEmpty(i);
      TargetTypeMappingRule rule = new TargetTypeMappingRule();
      rule.setId(item.getText(1));
      rule.setName(item.getText(2));
      rule.setMatchHopType(item.getText(3));
      rule.setMatchMinLength(item.getText(4));
      rule.setMatchMaxLength(item.getText(5));
      rule.setMatchMinPrecision(item.getText(6));
      rule.setMatchMaxPrecision(item.getText(7));
      rule.setMatchLengthAbsent("Y".equalsIgnoreCase(item.getText(8)));
      rule.setMatchFieldNamePattern(item.getText(9));
      rule.setTargetSqlType(item.getText(10));
      rule.setEnabled(!"N".equalsIgnoreCase(item.getText(11)));
      if (Utils.isEmpty(rule.getId()) && !Utils.isEmpty(rule.getName())) {
        rule.setId(rule.getName().replace(' ', '_').toLowerCase(Locale.ROOT));
      }
      rules.add(rule);
    }
    return rules;
  }

  private void refreshPreview() {
    if (wPreviewResult == null || wPreviewResult.isDisposed()) {
      return;
    }
    TargetTypeMappingMeta snapshot = new TargetTypeMappingMeta(wName.getText());
    snapshot.setTargetDatabase(wTargetDatabase.getText());
    snapshot.setRules(readRulesFromTable());
    try {
      IValueMeta valueMeta =
          ValueMetaFactory.createValueMeta(
              Const.NVL(wPreviewFieldName.getText(), "sample"),
              DataTypeMappingPatternSupport.hopTypeId(wPreviewHopType.getText()));
      valueMeta.setLength(parsePreviewInt(wPreviewLength.getText()));
      valueMeta.setPrecision(parsePreviewInt(wPreviewPrecision.getText()));
      String sql =
          TargetTypeMappingResolver.resolveSqlType(valueMeta, snapshot, manager.getVariables());
      if (Utils.isEmpty(sql)) {
        wPreviewResult.setText(
            BaseMessages.getString(PKG, "TargetTypeMappingMetaEditor.Preview.NoMatch"));
      } else {
        wPreviewResult.setText(sql);
      }
    } catch (Exception e) {
      wPreviewResult.setText(Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
    }
  }

  private void populateTargetDatabases() {
    try {
      List<String> names =
          manager.getMetadataProvider().getSerializer(DatabaseMeta.class).listObjectNames();
      wTargetDatabase.setItems(names.toArray(new String[0]));
    } catch (Exception ignored) {
      // Editor still works without a connection list.
    }
  }

  private static int parsePreviewInt(String raw) {
    if (Utils.isEmpty(raw)) {
      return -1;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String[] hopTypeChoices() {
    String[] hopTypes = ValueMetaFactory.getValueMetaNames();
    if (hopTypes == null || hopTypes.length == 0) {
      return new String[] {
        "", "Number", "String", "Date", "Boolean", "Integer", "BigNumber", "Binary", "Timestamp"
      };
    }
    String[] withEmpty = new String[hopTypes.length + 1];
    withEmpty[0] = "";
    System.arraycopy(hopTypes, 0, withEmpty, 1, hopTypes.length);
    return withEmpty;
  }

  private static ColumnInfo col(String name, int type, int width, String tooltip) {
    ColumnInfo c = new ColumnInfo(name, type, false);
    c.setWidth(width);
    c.setUsingVariables(true);
    c.setAutoResize(false);
    if (!Utils.isEmpty(tooltip)) {
      c.setToolTip(tooltip);
    }
    return c;
  }

  private static ColumnInfo colCcombo(String name, String[] values, int width) {
    ColumnInfo c = new ColumnInfo(name, ColumnInfo.COLUMN_TYPE_CCOMBO, values);
    c.setWidth(width);
    c.setAutoResize(false);
    return c;
  }
}
