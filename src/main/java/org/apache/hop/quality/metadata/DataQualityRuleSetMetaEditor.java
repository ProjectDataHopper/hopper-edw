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
package org.apache.hop.quality.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualitySeverity;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Simple table editor for a data quality rule set. */
@GuiPlugin(description = "Editor for Data Quality Rule Set metadata")
public class DataQualityRuleSetMetaEditor extends MetadataEditor<DataQualityRuleSetMeta> {

  private static final Class<?> PKG = DataQualityRuleSetMetaEditor.class;

  private Text wName;
  private Text wDescription;
  private TableView wRules;

  public DataQualityRuleSetMetaEditor(
      HopGui hopGui,
      MetadataManager<DataQualityRuleSetMeta> manager,
      DataQualityRuleSetMeta metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "DataQualityRuleSetMetaEditor.Name.Label"));
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
        BaseMessages.getString(PKG, "DataQualityRuleSetMetaEditor.Description.Label"));
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

    Label wlRules = new Label(parent, SWT.LEFT);
    PropsUi.setLook(wlRules);
    wlRules.setText(BaseMessages.getString(PKG, "DataQualityRuleSetMetaEditor.Rules.Label"));
    FormData fdlRules = new FormData();
    fdlRules.top = new FormAttachment(wDescription, margin);
    fdlRules.left = new FormAttachment(0, 0);
    fdlRules.right = new FormAttachment(100, 0);
    wlRules.setLayoutData(fdlRules);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo("Id", ColumnInfo.COLUMN_TYPE_TEXT, false, false),
          new ColumnInfo("Name", ColumnInfo.COLUMN_TYPE_TEXT, false, false),
          new ColumnInfo(
              "Type",
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              java.util.Arrays.stream(DataQualityRuleType.values())
                  .map(Enum::name)
                  .toArray(String[]::new),
              false),
          new ColumnInfo("Field", ColumnInfo.COLUMN_TYPE_TEXT, false, false),
          new ColumnInfo(
              "Severity",
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              java.util.Arrays.stream(QualitySeverity.values())
                  .map(Enum::name)
                  .toArray(String[]::new),
              false),
          new ColumnInfo("Parameters", ColumnInfo.COLUMN_TYPE_TEXT, false, false),
          new ColumnInfo("Enabled", ColumnInfo.COLUMN_TYPE_CCOMBO, new String[] {"Y", "N"}, false)
        };

    wRules =
        new TableView(
            manager.getVariables(),
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            getMetadata().getRules().size(),
            false,
            null,
            props);
    FormData fdRules = new FormData();
    fdRules.top = new FormAttachment(wlRules, margin);
    fdRules.left = new FormAttachment(0, 0);
    fdRules.right = new FormAttachment(100, 0);
    fdRules.bottom = new FormAttachment(100, -margin);
    wRules.setLayoutData(fdRules);

    setWidgetsContent();
    wName.addModifyListener(e -> setChanged());
    wDescription.addModifyListener(e -> setChanged());
  }

  @Override
  public void setWidgetsContent() {
    DataQualityRuleSetMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wRules.clearAll(false);
    for (DataQualityRule rule : meta.getRules()) {
      if (rule == null) {
        continue;
      }
      TableItem item = new TableItem(wRules.table, SWT.NONE);
      item.setText(1, Const.NVL(rule.getId(), ""));
      item.setText(2, Const.NVL(rule.getName(), ""));
      item.setText(3, rule.getType() != null ? rule.getType().name() : "");
      item.setText(4, Const.NVL(rule.getFieldName(), ""));
      item.setText(5, rule.getSeverity() != null ? rule.getSeverity().name() : "");
      item.setText(6, formatParameters(rule.getParameters()));
      item.setText(7, rule.isEnabled() ? "Y" : "N");
    }
    wRules.optimizeTableView();
  }

  @Override
  public void getWidgetsContent(DataQualityRuleSetMeta meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    List<DataQualityRule> rules = new ArrayList<>();
    for (int i = 0; i < wRules.nrNonEmpty(); i++) {
      TableItem item = wRules.getNonEmpty(i);
      DataQualityRule rule = new DataQualityRule();
      rule.setId(item.getText(1));
      rule.setName(item.getText(2));
      try {
        rule.setType(DataQualityRuleType.valueOf(item.getText(3).trim()));
      } catch (Exception e) {
        rule.setType(DataQualityRuleType.NOT_NULL);
      }
      rule.setFieldName(item.getText(4));
      try {
        rule.setSeverity(QualitySeverity.valueOf(item.getText(5).trim()));
      } catch (Exception e) {
        rule.setSeverity(QualitySeverity.BLOCKING);
      }
      rule.setParameters(parseParameters(item.getText(6)));
      rule.setEnabled(!"N".equalsIgnoreCase(item.getText(7)));
      rule.ensureId();
      rules.add(rule);
    }
    meta.setRules(rules);
  }

  private static String formatParameters(Map<String, String> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      if (sb.length() > 0) {
        sb.append(';');
      }
      sb.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return sb.toString();
  }

  private static Map<String, String> parseParameters(String raw) {
    Map<String, String> map = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) {
      return map;
    }
    for (String part : raw.split("[;,]")) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int eq = trimmed.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
    }
    return map;
  }
}
