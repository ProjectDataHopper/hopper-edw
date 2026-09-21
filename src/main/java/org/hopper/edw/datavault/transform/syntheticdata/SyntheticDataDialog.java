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
package org.hopper.edw.datavault.transform.syntheticdata;

import org.apache.hop.core.Const;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/** Dialog for the synthetic data transform. Scalar options use grouped widgets; grids are extra groups. */
public class SyntheticDataDialog extends BaseTransformDialog {

  private static final Class<?> PKG = SyntheticDataMeta.class;

  private static final String[] GENERATORS =
      new String[] {
        "CONSTANT",
        "SEQUENCE",
        "SEQUENCE_IN_PARENT",
        "SEQUENCE_IN_GROUP",
        "CHOICE",
        "INT_RANGE",
        "NUMBER_RANGE",
        "NUMBER_EXPR",
        "TEMPLATE",
        "DATE_OFFSET",
        "COPY",
        "COPY_SIBLING",
        "LOOKUP",
        "REFERENCE",
        "UUID",
        "MOD_HASH",
        "JSON",
        "CHILD_AGGREGATE"
      };

  private final SyntheticDataMeta input;
  private GuiCompositeWidgets widgets;
  private TableView wPopulations;
  private TableView wPaths;
  private TableView wFields;
  private TableView wOverrides;
  private TableView wXmlNodes;
  private boolean loading;

  public SyntheticDataDialog(
      Shell parent, IVariables variables, SyntheticDataMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "SyntheticData.Name"));
    changed = input.hasChanged();
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();
    DialogHelpSupport.installLocalHelpButton(shell, HelpTopics.SYNTHETIC_DATA);

    widgets =
        GuiCompositeWidgets.addScrolledComposite(
            shell,
            variables,
            wTransformName,
            wOk,
            SyntheticDataMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
            input,
            compositeWidgets -> {
              widgets = compositeWidgets;
              compositeWidgets.registerExtraGroup(
                  BaseMessages.getString(PKG, "SyntheticData.Group.Populations"),
                  "0300",
                  null,
                  this::addPopulations);
              compositeWidgets.registerExtraGroup(
                  BaseMessages.getString(PKG, "SyntheticData.Group.Paths"),
                  "0400",
                  null,
                  this::addPaths);
              compositeWidgets.registerExtraGroup(
                  BaseMessages.getString(PKG, "SyntheticData.Group.Fields"),
                  "0500",
                  null,
                  this::addFields);
              compositeWidgets.registerExtraGroup(
                  BaseMessages.getString(PKG, "SyntheticData.Group.Overrides"),
                  "0600",
                  null,
                  this::addOverrides);
              compositeWidgets.registerExtraGroup(
                  BaseMessages.getString(PKG, "SyntheticData.Group.Xml"),
                  "0700",
                  null,
                  this::addXmlNodes);
            });
    widgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            if (!loading) {
              input.setChanged();
            }
            enableFields();
          }
        });

    loading = true;
    populateTables();
    loading = false;
    enableFields();
    input.setChanged(changed);
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return transformName;
  }

  private void addPopulations(Composite parent) {
    Label label = label(parent, "SyntheticData.Populations.Label");
    ColumnInfo[] columns =
        new ColumnInfo[] {
          column("SyntheticData.Column.Kind", new String[] {"RANGE", "SAMPLE"}),
          column("SyntheticData.Column.Start"),
          column("SyntheticData.Column.Count"),
          column("SyntheticData.Column.From"),
          column("SyntheticData.Column.To")
        };
    wPopulations = table(parent, label, columns, input.getPopulations().size());
  }

  private void addPaths(Composite parent) {
    Label label = label(parent, "SyntheticData.Paths.Label");
    ColumnInfo[] columns =
        new ColumnInfo[] {
          column("SyntheticData.Column.When"),
          column("SyntheticData.Column.Probability"),
          column("SyntheticData.Column.Steps"),
          column("SyntheticData.Column.ElseSteps"),
          column("SyntheticData.Column.ExtraStep"),
          column("SyntheticData.Column.ExtraProbability")
        };
    wPaths = table(parent, label, columns, input.getPaths().size());
  }

  private void addFields(Composite parent) {
    Label label = label(parent, "SyntheticData.Fields.Label");
    ColumnInfo[] columns =
        new ColumnInfo[] {
          column("SyntheticData.Column.Name"),
          column("SyntheticData.Column.Type", ValueMetaFactory.getValueMetaNames()),
          column("SyntheticData.Column.Generator", GENERATORS),
          column("SyntheticData.Column.Arguments"),
          column("SyntheticData.Column.Publish", new String[] {"Y", "N"})
        };
    wFields = table(parent, label, columns, input.getFields().size());
  }

  private void addOverrides(Composite parent) {
    Label label = label(parent, "SyntheticData.Overrides.Label");
    ColumnInfo[] columns =
        new ColumnInfo[] {
          column("SyntheticData.Column.ParentIndex"),
          column("SyntheticData.Column.ParentOp", new String[] {"=", "<="}),
          column("SyntheticData.Column.ChildIndex"),
          column("SyntheticData.Column.ChildOp", new String[] {"=", "<="}),
          column("SyntheticData.Column.Field"),
          column("SyntheticData.Column.Generator", GENERATORS),
          column("SyntheticData.Column.Arguments"),
          column("SyntheticData.Column.ForceChildCount")
        };
    wOverrides = table(parent, label, columns, input.getOverrides().size());
  }

  private void addXmlNodes(Composite parent) {
    Label label = label(parent, "SyntheticData.XmlNodes.Label");
    ColumnInfo[] columns =
        new ColumnInfo[] {
          column("SyntheticData.Column.Id"),
          column("SyntheticData.Column.ParentId"),
          column("SyntheticData.Column.NodeType", new String[] {"GROUP", "ELEMENT", "WRAPPER", "ROW"}),
          column("SyntheticData.Column.Element"),
          column("SyntheticData.Column.GroupField"),
          column("SyntheticData.Column.Attributes")
        };
    wXmlNodes = table(parent, label, columns, input.getXmlNodes().size());
  }

  private Label label(Composite parent, String key) {
    Label label = new Label(parent, SWT.LEFT);
    label.setText(BaseMessages.getString(PKG, key));
    PropsUi.setLook(label);
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
    return new ColumnInfo(BaseMessages.getString(PKG, key), ColumnInfo.COLUMN_TYPE_CCOMBO, values);
  }

  private TableView table(Composite parent, Label label, ColumnInfo[] columns, int rows) {
    TableView table =
        new TableView(
            variables,
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            rows,
            false,
            e -> {
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

  private void populateTables() {
    fill(
        wPopulations,
        input.getPopulations(),
        population ->
            new String[] {
              population.getKind(),
              population.getStart(),
              population.getCount(),
              population.getFrom(),
              population.getTo()
            });
    fill(
        wPaths,
        input.getPaths(),
        path ->
            new String[] {
              path.getWhen(),
              path.getProbability(),
              path.getSteps(),
              path.getElseSteps(),
              path.getExtraStep(),
              path.getExtraProbability()
            });
    fill(
        wFields,
        input.getFields(),
        field ->
            new String[] {
              field.getName(),
              field.getHopType(),
              field.getGenerator(),
              field.getArguments(),
              field.isPublish() ? "Y" : "N"
            });
    fill(
        wOverrides,
        input.getOverrides(),
        override ->
            new String[] {
              override.getParentIndex(),
              override.getParentOp(),
              override.getChildIndex(),
              override.getChildOp(),
              override.getField(),
              override.getGenerator(),
              override.getArguments(),
              override.getForceChildCount()
            });
    fill(
        wXmlNodes,
        input.getXmlNodes(),
        node ->
            new String[] {
              node.getId(),
              node.getParentId(),
              node.getNodeType(),
              node.getElement(),
              node.getGroupField(),
              node.getAttributes()
            });
  }

  private <T> void fill(TableView table, java.util.List<T> values, java.util.function.Function<T, String[]> cells) {
    if (values == null || values.isEmpty()) {
      return;
    }
    table.table.removeAll();
    for (T value : values) {
      TableItem item = new TableItem(table.table, SWT.NONE);
      String[] text = cells.apply(value);
      for (int i = 0; i < text.length; i++) {
        item.setText(i + 1, Const.NVL(text[i], ""));
      }
    }
    table.optimizeTableView();
  }

  private void enableFields() {
    String mode = widgetText(SyntheticDataMeta.WIDGET_MODE);
    boolean parents =
        "PER_PARENT".equals(mode) || "PATHS".equals(mode) || "HIERARCHY".equals(mode);
    if (wPaths != null) {
      wPaths.setEnabled("PATHS".equals(mode));
    }
    if (wPopulations != null) {
      wPopulations.setEnabled("COMBINE".equals(mode));
    }
    if (wXmlNodes != null) {
      wXmlNodes.setEnabled("XML".equalsIgnoreCase(widgetText(SyntheticDataMeta.WIDGET_DOCUMENT_FORMAT)));
    }
    setEnabled(SyntheticDataMeta.WIDGET_PARENTS, parents);
    setEnabled(SyntheticDataMeta.WIDGET_CHILDREN, "HIERARCHY".equals(mode));
    setEnabled(SyntheticDataMeta.WIDGET_PAIRS_LEFT, "UNIQUE_PAIRS".equals(mode));
    setEnabled(SyntheticDataMeta.WIDGET_PAIRS_RIGHT, "UNIQUE_PAIRS".equals(mode));
  }

  private void setEnabled(String widgetId, boolean enabled) {
    if (widgets == null || widgets.getWidgetsMap() == null) {
      return;
    }
    Control control = widgets.getWidgetsMap().get(widgetId);
    if (control != null) {
      control.setEnabled(enabled);
    }
  }

  private String widgetText(String widgetId) {
    if (widgets == null || widgets.getWidgetsMap() == null) {
      return "";
    }
    Control control = widgets.getWidgetsMap().get(widgetId);
    if (control instanceof Combo combo) {
      return combo.getText();
    }
    if (control instanceof CCombo combo) {
      return combo.getText();
    }
    return "";
  }

  private void ok() {
    widgets.getWidgetsContents(input, SyntheticDataMeta.GUI_PLUGIN_ELEMENT_PARENT_ID);
    readTables();
    transformName = wTransformName.getText();
    input.setChanged();
    dispose();
  }

  private void readTables() {
    input.getPopulations().clear();
    for (int i = 0; i < wPopulations.nrNonEmpty(); i++) {
      TableItem item = wPopulations.getNonEmpty(i);
      SyntheticPopulation population = new SyntheticPopulation();
      population.setKind(item.getText(1));
      population.setStart(item.getText(2));
      population.setCount(item.getText(3));
      population.setFrom(item.getText(4));
      population.setTo(item.getText(5));
      input.getPopulations().add(population);
    }
    input.getPaths().clear();
    for (int i = 0; i < wPaths.nrNonEmpty(); i++) {
      TableItem item = wPaths.getNonEmpty(i);
      SyntheticPath path = new SyntheticPath();
      path.setWhen(item.getText(1));
      path.setProbability(item.getText(2));
      path.setSteps(item.getText(3));
      path.setElseSteps(item.getText(4));
      path.setExtraStep(item.getText(5));
      path.setExtraProbability(item.getText(6));
      input.getPaths().add(path);
    }
    input.getFields().clear();
    for (int i = 0; i < wFields.nrNonEmpty(); i++) {
      TableItem item = wFields.getNonEmpty(i);
      SyntheticField field = new SyntheticField();
      field.setName(item.getText(1));
      field.setHopType(item.getText(2));
      field.setGenerator(item.getText(3));
      field.setArguments(item.getText(4));
      field.setPublish(!"N".equalsIgnoreCase(item.getText(5)));
      input.getFields().add(field);
    }
    input.getOverrides().clear();
    for (int i = 0; i < wOverrides.nrNonEmpty(); i++) {
      TableItem item = wOverrides.getNonEmpty(i);
      SyntheticOverride override = new SyntheticOverride();
      override.setParentIndex(item.getText(1));
      override.setParentOp(item.getText(2));
      override.setChildIndex(item.getText(3));
      override.setChildOp(item.getText(4));
      override.setField(item.getText(5));
      override.setGenerator(item.getText(6));
      override.setArguments(item.getText(7));
      override.setForceChildCount(item.getText(8));
      input.getOverrides().add(override);
    }
    input.getXmlNodes().clear();
    for (int i = 0; i < wXmlNodes.nrNonEmpty(); i++) {
      TableItem item = wXmlNodes.getNonEmpty(i);
      SyntheticXmlNode node = new SyntheticXmlNode();
      node.setId(item.getText(1));
      node.setParentId(item.getText(2));
      node.setNodeType(item.getText(3));
      node.setElement(item.getText(4));
      node.setGroupField(item.getText(5));
      node.setAttributes(item.getText(6));
      input.getXmlNodes().add(node);
    }
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
