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
package org.hopper.edw.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/** Editor for project-level {@link DataTypeMappingMeta} profiles. */
@GuiPlugin(description = "Editor for Data Type Mapping metadata")
public class DataTypeMappingMetaEditor extends MetadataEditor<DataTypeMappingMeta> {

  private static final Class<?> PKG = DataTypeMappingMeta.class;

  /** Scope source-kind codes shown in the multi-select list (order stable for UX). */
  private static final String[] SCOPE_SOURCE_KINDS = {
    "DATABASE", "CSV", "PARQUET", "ICEBERG", "JSON", "PIPELINE", "COMPOSITE"
  };

  /** Unscaled height for the source-kinds multi-select list (multiplied by native zoom). */
  private static final int SOURCE_KINDS_LIST_HEIGHT = 90;

  private Text wName;
  private Text wDescription;
  private org.eclipse.swt.widgets.List wSourceKinds;
  private TextVar wDatabasePattern;
  private TextVar wSchemaPattern;
  private TextVar wPathPattern;
  private TextVar wCatalogNamespacePattern;
  private TableView wRules;
  private Composite parentComposite;

  public DataTypeMappingMetaEditor(
      HopGui hopGui, MetadataManager<DataTypeMappingMeta> manager, DataTypeMappingMeta metadata) {
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
    wlName.setText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.Name.Label"));
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
        BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.Description.Label"));
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

    CTabFolder wTabFolder = new CTabFolder(parent, SWT.BORDER);
    PropsUi.setLook(wTabFolder);
    FormData fdTabs = new FormData();
    fdTabs.top = new FormAttachment(wDescription, margin);
    fdTabs.left = new FormAttachment(0, 0);
    fdTabs.right = new FormAttachment(100, 0);
    fdTabs.bottom = new FormAttachment(100, -margin);
    wTabFolder.setLayoutData(fdTabs);

    addScopeTab(wTabFolder, middle, margin);
    addRulesTab(wTabFolder, margin, props);
    wTabFolder.setSelection(0);

    setWidgetsContent();
    wName.addModifyListener(e -> setChanged());
    wDescription.addModifyListener(e -> setChanged());
    wSourceKinds.addListener(SWT.Selection, e -> setChanged());
    wDatabasePattern.addModifyListener(e -> setChanged());
    wSchemaPattern.addModifyListener(e -> setChanged());
    wPathPattern.addModifyListener(e -> setChanged());
    wCatalogNamespacePattern.addModifyListener(e -> setChanged());
  }

  private void addScopeTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.Tab.Scope.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    FormLayout layout = new FormLayout();
    layout.marginWidth = margin;
    layout.marginHeight = margin;
    comp.setLayout(layout);
    tab.setControl(comp);

    Label wlScope = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlScope);
    wlScope.setText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.Scope.Label"));
    String scopeTip = BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.SourceKinds.ToolTip");
    wlScope.setToolTipText(scopeTip);
    FormData fdlScope = new FormData();
    fdlScope.top = new FormAttachment(0, 0);
    fdlScope.left = new FormAttachment(0, 0);
    fdlScope.right = new FormAttachment(middle, -margin);
    wlScope.setLayoutData(fdlScope);

    wSourceKinds =
        new org.eclipse.swt.widgets.List(
            comp, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(wSourceKinds);
    wSourceKinds.setItems(SCOPE_SOURCE_KINDS);
    wSourceKinds.setToolTipText(scopeTip);
    FormData fdSourceKinds = new FormData();
    fdSourceKinds.top = new FormAttachment(wlScope, 0, SWT.TOP);
    fdSourceKinds.left = new FormAttachment(middle, 0);
    fdSourceKinds.right = new FormAttachment(100, 0);
    fdSourceKinds.height =
        (int) Math.round(SOURCE_KINDS_LIST_HEIGHT * PropsUi.getNativeZoomFactor());
    wSourceKinds.setLayoutData(fdSourceKinds);

    String patternTip = BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.Pattern.ToolTip");

    Label wlDb = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlDb);
    wlDb.setText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.DatabasePattern.Label"));
    wlDb.setToolTipText(patternTip);
    FormData fdlDb = new FormData();
    fdlDb.top = new FormAttachment(wSourceKinds, margin);
    fdlDb.left = new FormAttachment(0, 0);
    fdlDb.right = new FormAttachment(middle, -margin);
    wlDb.setLayoutData(fdlDb);

    wDatabasePattern =
        new TextVar(manager.getVariables(), comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDatabasePattern);
    wDatabasePattern.setToolTipText(patternTip);
    FormData fdDb = new FormData();
    fdDb.top = new FormAttachment(wlDb, 0, SWT.CENTER);
    fdDb.left = new FormAttachment(middle, 0);
    fdDb.right = new FormAttachment(100, 0);
    wDatabasePattern.setLayoutData(fdDb);

    Label wlSchema = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlSchema);
    wlSchema.setText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.SchemaPattern.Label"));
    wlSchema.setToolTipText(patternTip);
    FormData fdlSchema = new FormData();
    fdlSchema.top = new FormAttachment(wDatabasePattern, margin);
    fdlSchema.left = new FormAttachment(0, 0);
    fdlSchema.right = new FormAttachment(middle, -margin);
    wlSchema.setLayoutData(fdlSchema);

    wSchemaPattern = new TextVar(manager.getVariables(), comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSchemaPattern);
    wSchemaPattern.setToolTipText(patternTip);
    FormData fdSchema = new FormData();
    fdSchema.top = new FormAttachment(wlSchema, 0, SWT.CENTER);
    fdSchema.left = new FormAttachment(middle, 0);
    fdSchema.right = new FormAttachment(100, 0);
    wSchemaPattern.setLayoutData(fdSchema);

    Label wlPath = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlPath);
    wlPath.setText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.PathPattern.Label"));
    wlPath.setToolTipText(patternTip);
    FormData fdlPath = new FormData();
    fdlPath.top = new FormAttachment(wSchemaPattern, margin);
    fdlPath.left = new FormAttachment(0, 0);
    fdlPath.right = new FormAttachment(middle, -margin);
    wlPath.setLayoutData(fdlPath);

    wPathPattern = new TextVar(manager.getVariables(), comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPathPattern);
    wPathPattern.setToolTipText(patternTip);
    FormData fdPath = new FormData();
    fdPath.top = new FormAttachment(wlPath, 0, SWT.CENTER);
    fdPath.left = new FormAttachment(middle, 0);
    fdPath.right = new FormAttachment(100, 0);
    wPathPattern.setLayoutData(fdPath);

    Label wlNs = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlNs);
    wlNs.setText(
        BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.CatalogNamespacePattern.Label"));
    wlNs.setToolTipText(patternTip);
    FormData fdlNs = new FormData();
    fdlNs.top = new FormAttachment(wPathPattern, margin);
    fdlNs.left = new FormAttachment(0, 0);
    fdlNs.right = new FormAttachment(middle, -margin);
    wlNs.setLayoutData(fdlNs);

    wCatalogNamespacePattern =
        new TextVar(manager.getVariables(), comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wCatalogNamespacePattern);
    wCatalogNamespacePattern.setToolTipText(patternTip);
    FormData fdNs = new FormData();
    fdNs.top = new FormAttachment(wlNs, 0, SWT.CENTER);
    fdNs.left = new FormAttachment(middle, 0);
    fdNs.right = new FormAttachment(100, 0);
    wCatalogNamespacePattern.setLayoutData(fdNs);
  }

  private void addRulesTab(CTabFolder tabFolder, int margin, PropsUi props) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.Tab.Rules.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    FormLayout layout = new FormLayout();
    layout.marginWidth = margin;
    layout.marginHeight = margin;
    comp.setLayout(layout);
    tab.setControl(comp);

    Label wlRules = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlRules);
    wlRules.setText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.Rules.Label"));
    wlRules.setToolTipText(BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.Rules.ToolTip"));
    FormData fdlRules = new FormData();
    fdlRules.top = new FormAttachment(0, 0);
    fdlRules.left = new FormAttachment(0, 0);
    fdlRules.right = new FormAttachment(100, 0);
    wlRules.setLayoutData(fdlRules);

    // Intermediate host for TableView / TableEditor under nested MetadataEditorDialog.
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

    String[] hopTypes = ValueMetaFactory.getValueMetaNames();
    if (hopTypes == null || hopTypes.length == 0) {
      hopTypes =
          new String[] {
            "", "Number", "String", "Date", "Boolean", "Integer", "BigNumber", "Binary", "Timestamp"
          };
    }
    String rulePatternTip =
        BaseMessages.getString(PKG, "DataTypeMappingMetaEditor.RulePattern.ToolTip");
    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("Id", ColumnInfo.COLUMN_TYPE_TEXT, 80, null),
          col("Name", ColumnInfo.COLUMN_TYPE_TEXT, 100, null),
          colCcombo("Match hop type", hopTypes, 100),
          col("Match source type", ColumnInfo.COLUMN_TYPE_TEXT, 110, rulePatternTip),
          col("Match field name", ColumnInfo.COLUMN_TYPE_TEXT, 110, rulePatternTip),
          colCcombo("Length absent", new String[] {"Y", "N"}, 80),
          colCcombo("Target hop type", hopTypes, 100),
          col("Target length", ColumnInfo.COLUMN_TYPE_TEXT, 80, null),
          col("Target precision", ColumnInfo.COLUMN_TYPE_TEXT, 80, null),
          col("Target name", ColumnInfo.COLUMN_TYPE_TEXT, 100, null),
          col("Conversion mask", ColumnInfo.COLUMN_TYPE_TEXT, 120, null),
          col("Decimal", ColumnInfo.COLUMN_TYPE_TEXT, 50, null),
          col("Grouping", ColumnInfo.COLUMN_TYPE_TEXT, 50, null),
          col("Locale", ColumnInfo.COLUMN_TYPE_TEXT, 60, null),
          col("Time zone", ColumnInfo.COLUMN_TYPE_TEXT, 70, null),
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
            e -> setChanged(),
            props);
    FormData fdRules = new FormData();
    fdRules.top = new FormAttachment(0, 0);
    fdRules.left = new FormAttachment(0, 0);
    fdRules.right = new FormAttachment(100, 0);
    fdRules.bottom = new FormAttachment(100, 0);
    wRules.setLayoutData(fdRules);
  }

  @Override
  public void refreshOnDialogActivate() {
    // Ensure nested dialog has a full layout pass before TableView cell editors are opened.
    if (parentComposite != null && !parentComposite.isDisposed()) {
      parentComposite.layout(true, true);
    }
    if (wRules != null && !wRules.isDisposed() && wRules.getParent() != null) {
      wRules.getParent().layout(true, true);
    }
  }

  @Override
  protected Button createHelpButton(Shell shell) {
    return DialogHelpSupport.createHelpButton(shell, HelpTopics.DATA_TYPE_MAPPING);
  }

  @Override
  public void setWidgetsContent() {
    DataTypeMappingMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    DataTypeMappingScope scope = meta.getScope();
    selectSourceKinds(scope.getSourceKinds());
    wDatabasePattern.setText(Const.NVL(scope.getDatabaseNamePattern(), ""));
    wSchemaPattern.setText(Const.NVL(scope.getSchemaNamePattern(), ""));
    wPathPattern.setText(Const.NVL(scope.getPathPattern(), ""));
    wCatalogNamespacePattern.setText(Const.NVL(scope.getCatalogNamespacePattern(), ""));

    // Avoid TableView.clearAll(): asyncExec(edit) NPEs in nested MetadataEditorDialog.
    wRules.table.removeAll();
    for (DataTypeMappingRule rule : meta.getRules()) {
      if (rule == null) {
        continue;
      }
      TableItem item = new TableItem(wRules.table, SWT.NONE);
      item.setText(1, Const.NVL(rule.getId(), ""));
      item.setText(2, Const.NVL(rule.getName(), ""));
      item.setText(3, Const.NVL(rule.getMatchHopType(), ""));
      item.setText(4, Const.NVL(rule.getMatchSourceDataTypePattern(), ""));
      item.setText(5, Const.NVL(rule.getMatchFieldNamePattern(), ""));
      item.setText(6, rule.isMatchLengthAbsent() ? "Y" : "N");
      item.setText(
          7,
          rule.getTargetHopType() > IValueMeta.TYPE_NONE
              ? Const.NVL(DataTypeMappingPatternSupport.hopTypeName(rule.getTargetHopType()), "")
              : "");
      item.setText(8, Const.NVL(rule.getTargetLength(), ""));
      item.setText(9, Const.NVL(rule.getTargetPrecision(), ""));
      item.setText(10, Const.NVL(rule.getTargetFieldName(), ""));
      FieldConversionOptions conv = rule.getConversion();
      item.setText(11, Const.NVL(conv.getConversionMask(), ""));
      item.setText(12, Const.NVL(conv.getDecimalSymbol(), ""));
      item.setText(13, Const.NVL(conv.getGroupingSymbol(), ""));
      item.setText(14, Const.NVL(conv.getDateFormatLocale(), ""));
      item.setText(15, Const.NVL(conv.getDateFormatTimeZone(), ""));
      item.setText(16, rule.isEnabled() ? "Y" : "N");
    }
    if (wRules.table.getItemCount() == 0) {
      new TableItem(wRules.table, SWT.NONE);
    }
    wRules.setRowNums();
    wRules.optWidth(true);
  }

  @Override
  public void getWidgetsContent(DataTypeMappingMeta meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    DataTypeMappingScope scope = meta.getScope();
    scope.getSourceKinds().clear();
    scope.getSourceKinds().addAll(selectedSourceKinds());
    scope.setDatabaseNamePattern(wDatabasePattern.getText());
    scope.setSchemaNamePattern(wSchemaPattern.getText());
    scope.setPathPattern(wPathPattern.getText());
    scope.setCatalogNamespacePattern(wCatalogNamespacePattern.getText());

    java.util.List<DataTypeMappingRule> rules = new ArrayList<>();
    for (int i = 0; i < wRules.nrNonEmpty(); i++) {
      TableItem item = wRules.getNonEmpty(i);
      DataTypeMappingRule rule = new DataTypeMappingRule();
      rule.setId(item.getText(1));
      rule.setName(item.getText(2));
      rule.setMatchHopType(item.getText(3));
      rule.setMatchSourceDataTypePattern(item.getText(4));
      rule.setMatchFieldNamePattern(item.getText(5));
      rule.setMatchLengthAbsent("Y".equalsIgnoreCase(item.getText(6)));
      String targetType = item.getText(7);
      if (!Utils.isEmpty(targetType)) {
        int typeId = DataTypeMappingPatternSupport.hopTypeId(targetType.trim());
        rule.setTargetHopType(typeId > 0 ? typeId : IValueMeta.TYPE_NONE);
      } else {
        rule.setTargetHopType(IValueMeta.TYPE_NONE);
      }
      rule.setTargetLength(item.getText(8));
      rule.setTargetPrecision(item.getText(9));
      rule.setTargetFieldName(item.getText(10));
      FieldConversionOptions conv = rule.getConversion();
      conv.setConversionMask(item.getText(11));
      conv.setDecimalSymbol(item.getText(12));
      conv.setGroupingSymbol(item.getText(13));
      conv.setDateFormatLocale(item.getText(14));
      conv.setDateFormatTimeZone(item.getText(15));
      rule.setEnabled(!"N".equalsIgnoreCase(item.getText(16)));
      if (Utils.isEmpty(rule.getId()) && !Utils.isEmpty(rule.getName())) {
        rule.setId(rule.getName().replace(' ', '_').toLowerCase(Locale.ROOT));
      }
      rules.add(rule);
    }
    meta.setRules(rules);
  }

  private void selectSourceKinds(java.util.List<String> selected) {
    wSourceKinds.deselectAll();
    if (selected == null || selected.isEmpty()) {
      return;
    }
    Set<String> wanted = new HashSet<>();
    for (String kind : selected) {
      if (!Utils.isEmpty(kind)) {
        wanted.add(kind.trim().toUpperCase(Locale.ROOT));
      }
    }
    for (int i = 0; i < wSourceKinds.getItemCount(); i++) {
      if (wanted.contains(wSourceKinds.getItem(i).toUpperCase(Locale.ROOT))) {
        wSourceKinds.select(i);
      }
    }
  }

  private java.util.List<String> selectedSourceKinds() {
    java.util.List<String> kinds = new ArrayList<>();
    for (String item : wSourceKinds.getSelection()) {
      if (!Utils.isEmpty(item)) {
        kinds.add(item.trim().toUpperCase(Locale.ROOT));
      }
    }
    return kinds;
  }

  private static ColumnInfo col(String name, int type, int width, String tooltip) {
    ColumnInfo c = new ColumnInfo(name, type, false);
    c.setWidth(width);
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

  /** Expose source type codes for tests / docs alignment with {@link DvSourceType}. */
  static String[] scopeSourceKinds() {
    return SCOPE_SOURCE_KINDS.clone();
  }
}
