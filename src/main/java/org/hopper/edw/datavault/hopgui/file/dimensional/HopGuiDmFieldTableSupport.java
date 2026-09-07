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

import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.datavault.hopgui.EnumDialogSupport;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDmFieldEditorDialog.Kind;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDmFieldEditorDialog.Model;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmFieldDocumentation;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmScdUpdatePolicy;

/** TableView helpers for dimensional field documentation (Markdown notes and requirements). */
public final class HopGuiDmFieldTableSupport {

  private static final Class<?> PKG = HopGuiDmFieldEditorDialog.class;
  static final String DOCUMENTATION_DATA_KEY = "dm-field-documentation";

  private HopGuiDmFieldTableSupport() {}

  public static ColumnInfo docsColumn() {
    ColumnInfo column =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Column.Docs"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    column.setReadOnly(true);
    column.setToolTip(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Column.Docs.ToolTip"));
    return column;
  }

  public static void attachDocumentation(
      TableItem item, int docsColumn, DmFieldDocumentation documentation) {
    if (item == null) {
      return;
    }
    DmFieldDocumentation copy = DmFieldDocumentation.copyOf(documentation);
    item.setData(DOCUMENTATION_DATA_KEY, copy);
    if (docsColumn > 0) {
      item.setText(docsColumn, copy.gridSummary());
    }
  }

  public static DmFieldDocumentation documentationOf(TableItem item) {
    if (item == null) {
      return new DmFieldDocumentation();
    }
    Object data = item.getData(DOCUMENTATION_DATA_KEY);
    if (data instanceof DmFieldDocumentation documentation) {
      return documentation.copy();
    }
    return new DmFieldDocumentation();
  }

  public static void populateAttribute(TableItem item, DmDimensionAttribute attribute) {
    if (item == null || attribute == null) {
      return;
    }
    String sourceField =
        Utils.isEmpty(attribute.getSourceFieldName())
            ? ConstOrEmpty(attribute.getFieldName())
            : attribute.getSourceFieldName();
    item.setText(1, sourceField);
    item.setText(2, ConstOrEmpty(attribute.getFieldName()));
    if (attribute.getScdUpdatePolicy() != null) {
      item.setText(3, EnumDialogSupport.descriptionOf(attribute.getScdUpdatePolicy()));
    }
    if (!Utils.isEmpty(attribute.getPreviousFieldName())) {
      item.setText(4, attribute.getPreviousFieldName());
    }
    attachDocumentation(item, 5, attribute.getDocumentation());
  }

  public static DmDimensionAttribute readAttribute(TableItem item) {
    if (item == null) {
      return null;
    }
    String targetFieldName = item.getText(2);
    if (Utils.isEmpty(targetFieldName)) {
      return null;
    }
    String sourceFieldName = item.getText(1);
    if (Utils.isEmpty(sourceFieldName)) {
      sourceFieldName = targetFieldName;
    }
    DmScdUpdatePolicy policy =
        EnumDialogSupport.lookupText(
            item.getText(3), DmScdUpdatePolicy.class, DmScdUpdatePolicy.TYPE1);
    DmDimensionAttribute attribute = new DmDimensionAttribute(targetFieldName, policy);
    if (!sourceFieldName.equals(targetFieldName)) {
      attribute.setSourceFieldName(sourceFieldName);
    }
    if (!Utils.isEmpty(item.getText(4))) {
      attribute.setPreviousFieldName(item.getText(4));
    }
    attribute.setDocumentation(documentationOf(item));
    return attribute;
  }

  public static void populateNaturalKey(TableItem item, DmNaturalKeyField field) {
    if (item == null || field == null || Utils.isEmpty(field.getFieldName())) {
      return;
    }
    item.setText(1, field.getFieldName());
    attachDocumentation(item, 2, field.getDocumentation());
  }

  public static DmNaturalKeyField readNaturalKey(TableItem item) {
    if (item == null || Utils.isEmpty(item.getText(1))) {
      return null;
    }
    DmNaturalKeyField field = new DmNaturalKeyField(item.getText(1));
    field.setDocumentation(documentationOf(item));
    return field;
  }

  public static void populateMeasure(TableItem item, DmFactMeasure measure) {
    if (item == null || measure == null || Utils.isEmpty(measure.getFieldName())) {
      return;
    }
    item.setText(1, measure.getFieldName());
    item.setText(2, measure.isAdditive() ? "Y" : "N");
    attachDocumentation(item, 3, measure.getDocumentation());
  }

  public static DmFactMeasure readMeasure(TableItem item) {
    if (item == null || Utils.isEmpty(item.getText(1))) {
      return null;
    }
    boolean additive = !"N".equalsIgnoreCase(item.getText(2));
    DmFactMeasure measure = new DmFactMeasure(item.getText(1), additive);
    measure.setDocumentation(documentationOf(item));
    return measure;
  }

  public static void populateDegenerate(TableItem item, DmFactDegenerateDimension degenerate) {
    if (item == null || degenerate == null || Utils.isEmpty(degenerate.getFieldName())) {
      return;
    }
    item.setText(1, degenerate.getFieldName());
    attachDocumentation(item, 2, degenerate.getDocumentation());
  }

  public static DmFactDegenerateDimension readDegenerate(TableItem item) {
    if (item == null || Utils.isEmpty(item.getText(1))) {
      return null;
    }
    DmFactDegenerateDimension degenerate = new DmFactDegenerateDimension(item.getText(1));
    degenerate.setDocumentation(documentationOf(item));
    return degenerate;
  }

  public static boolean editSelectedField(Shell parent, TableView tableView, Kind kind) {
    if (parent == null || tableView == null || tableView.table == null) {
      return false;
    }
    TableItem[] selection = tableView.table.getSelection();
    if (selection.length == 0) {
      MessageBox box = new MessageBox(parent, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.NoSelection.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.NoSelection.Message"));
      box.open();
      return false;
    }
    TableItem item = selection[0];
    Model model = modelFromItem(item, kind);
    HopGuiDmFieldEditorDialog dialog = new HopGuiDmFieldEditorDialog(parent, model);
    Model result = dialog.open();
    if (result == null) {
      return false;
    }
    applyModel(item, result);
    tableView.optimizeTableView();
    return true;
  }

  public static void wireDefaultSelection(Shell parent, TableView tableView, Kind kind) {
    if (tableView == null || tableView.table == null) {
      return;
    }
    tableView.table.addListener(
        SWT.DefaultSelection, event -> editSelectedField(parent, tableView, kind));
  }

  static Model modelFromItem(TableItem item, Kind kind) {
    Model model = new Model();
    model.setKind(kind);
    switch (kind) {
      case ATTRIBUTE -> {
        DmDimensionAttribute attribute = readAttribute(item);
        if (attribute != null) {
          model.setFieldName(attribute.getFieldName());
          model.setSourceFieldName(attribute.getSourceFieldName());
          model.setScdUpdatePolicy(attribute.getScdUpdatePolicy());
          model.setPreviousFieldName(attribute.getPreviousFieldName());
          copyDocs(attribute.getDocumentation(), model.getDocumentation());
        } else {
          model.setFieldName(item.getText(2));
          model.setSourceFieldName(item.getText(1));
        }
      }
      case MEASURE -> {
        DmFactMeasure measure = readMeasure(item);
        if (measure != null) {
          model.setFieldName(measure.getFieldName());
          model.setAdditive(measure.isAdditive());
          copyDocs(measure.getDocumentation(), model.getDocumentation());
        } else {
          model.setFieldName(item.getText(1));
        }
      }
      case NATURAL_KEY, DEGENERATE -> {
        model.setFieldName(item.getText(1));
        copyDocs(documentationOf(item), model.getDocumentation());
      }
    }
    return model;
  }

  static void applyModel(TableItem item, Model model) {
    if (item == null || model == null) {
      return;
    }
    switch (model.getKind()) {
      case ATTRIBUTE -> {
        item.setText(
            1, ConstOrEmpty(firstNonEmpty(model.getSourceFieldName(), model.getFieldName())));
        item.setText(2, ConstOrEmpty(model.getFieldName()));
        if (model.getScdUpdatePolicy() != null) {
          item.setText(3, EnumDialogSupport.descriptionOf(model.getScdUpdatePolicy()));
        }
        item.setText(4, ConstOrEmpty(model.getPreviousFieldName()));
        attachDocumentation(item, 5, model.getDocumentation());
      }
      case MEASURE -> {
        item.setText(1, ConstOrEmpty(model.getFieldName()));
        item.setText(2, model.isAdditive() ? "Y" : "N");
        attachDocumentation(item, 3, model.getDocumentation());
      }
      case NATURAL_KEY, DEGENERATE -> {
        item.setText(1, ConstOrEmpty(model.getFieldName()));
        attachDocumentation(item, 2, model.getDocumentation());
      }
    }
  }

  private static void copyDocs(DmFieldDocumentation source, DmFieldDocumentation target) {
    if (source == null || target == null) {
      return;
    }
    target.setDescription(source.getDescription());
    target.setNotes(source.getNotes());
    target.setRequirements(source.getRequirements());
    target.setRequired(source.getRequired());
  }

  private static String firstNonEmpty(String first, String second) {
    return Utils.isEmpty(first) ? second : first;
  }

  private static String ConstOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
