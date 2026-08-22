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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.hop.core.Const;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingMeta;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingPatternSupport;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingResolver;
import org.hopper.edw.datavault.metadata.datatypemapping.EffectiveSourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.FieldConversionOptions;
import org.hopper.edw.datavault.metadata.datatypemapping.IDataTypeMappingTarget;
import org.hopper.edw.datavault.metadata.datatypemapping.PhysicalSourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceFieldTypeMapping;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Shared HSM dialog tab: attach project data type mapping profiles, field overrides, and preview
 * the effective layout.
 */
public final class SourceDataTypeMappingTab {

  private static final Class<?> PKG = HopGuiSourceTableDialog.class;

  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final Supplier<List<PhysicalSourceField>> physicalFieldsSupplier;

  private Text wProfileNames;
  private TableView wOverrides;
  private TableView wEffective;

  public SourceDataTypeMappingTab(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Supplier<List<PhysicalSourceField>> physicalFieldsSupplier) {
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.physicalFieldsSupplier = physicalFieldsSupplier;
  }

  public void addTab(CTabFolder tabFolder, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Tab.DataTypeMapping.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlProfiles = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlProfiles);
    wlProfiles.setText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Profiles.Label"));
    wlProfiles.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    // Right-aligned action buttons: Refresh | Edit | Select | Add
    Button wRefresh = new Button(comp, SWT.PUSH);
    PropsUi.setLook(wRefresh);
    wRefresh.setText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Refresh.Button"));
    wRefresh.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Refresh.ToolTip"));
    wRefresh.setLayoutData(new FormDataBuilder().right().top(wlProfiles, margin).result());
    wRefresh.addListener(SWT.Selection, e -> refreshEffective());

    Button wEdit = new Button(comp, SWT.PUSH);
    PropsUi.setLook(wEdit);
    wEdit.setText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Edit.Button"));
    wEdit.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Edit.ToolTip"));
    wEdit.setLayoutData(
        new FormDataBuilder().right(wRefresh, -margin).top(wlProfiles, margin).result());
    wEdit.addListener(SWT.Selection, e -> editProfiles());

    Button wSelect = new Button(comp, SWT.PUSH);
    PropsUi.setLook(wSelect);
    wSelect.setText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Select.Button"));
    wSelect.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Select.ToolTip"));
    wSelect.setLayoutData(
        new FormDataBuilder().right(wEdit, -margin).top(wlProfiles, margin).result());
    wSelect.addListener(SWT.Selection, e -> selectProfiles());

    Button wAdd = new Button(comp, SWT.PUSH);
    PropsUi.setLook(wAdd);
    wAdd.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Add.Button"));
    wAdd.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Add.ToolTip"));
    wAdd.setLayoutData(
        new FormDataBuilder().right(wSelect, -margin).top(wlProfiles, margin).result());
    wAdd.addListener(SWT.Selection, e -> addProfile());

    wProfileNames = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wProfileNames);
    wProfileNames.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Profiles.ToolTip"));
    wProfileNames.setLayoutData(
        new FormDataBuilder().left().top(wlProfiles, margin).right(wAdd, -margin).result());

    Label wlOverrides = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlOverrides);
    wlOverrides.setText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Overrides.Label"));
    wlOverrides.setLayoutData(
        new FormDataBuilder().left().top(wProfileNames, margin).right().result());

    String[] hopTypes = {
      "", "String", "Integer", "Number", "BigNumber", "Date", "Timestamp", "Boolean", "Binary"
    };
    ColumnInfo[] overrideCols =
        new ColumnInfo[] {
          new ColumnInfo("Source field", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Target name", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Target type", ColumnInfo.COLUMN_TYPE_CCOMBO, hopTypes),
          new ColumnInfo("Length", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Precision", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Conversion mask", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Decimal", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Grouping", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Locale", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Time zone", ColumnInfo.COLUMN_TYPE_TEXT, false),
          new ColumnInfo("Enabled", ColumnInfo.COLUMN_TYPE_CCOMBO, new String[] {"Y", "N"})
        };

    wOverrides =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            overrideCols,
            1,
            null,
            PropsUi.getInstance());
    wOverrides.setLayoutData(
        new FormDataBuilder().left().top(wlOverrides, margin).right().bottom(55, 0).result());

    Label wlEffective = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlEffective);
    wlEffective.setText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Effective.Label"));
    wlEffective.setLayoutData(
        new FormDataBuilder().left().top(wOverrides, margin).right().result());

    ColumnInfo[] effectiveCols =
        new ColumnInfo[] {
          new ColumnInfo("Source", ColumnInfo.COLUMN_TYPE_TEXT, false, true),
          new ColumnInfo("Effective name", ColumnInfo.COLUMN_TYPE_TEXT, false, true),
          new ColumnInfo("Type", ColumnInfo.COLUMN_TYPE_TEXT, false, true),
          new ColumnInfo("Length", ColumnInfo.COLUMN_TYPE_TEXT, false, true),
          new ColumnInfo("Precision", ColumnInfo.COLUMN_TYPE_TEXT, false, true),
          new ColumnInfo("Mask", ColumnInfo.COLUMN_TYPE_TEXT, false, true),
          new ColumnInfo("Provenance", ColumnInfo.COLUMN_TYPE_TEXT, false, true)
        };

    wEffective =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            effectiveCols,
            1,
            null,
            PropsUi.getInstance());
    wEffective.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlEffective, margin)
            .right()
            .bottom(100, -margin)
            .result());
  }

  public void loadFrom(IDataTypeMappingTarget target) {
    if (target == null) {
      wProfileNames.setText("");
      wOverrides.clearAll(false);
      wEffective.clearAll(false);
      return;
    }
    wProfileNames.setText(String.join(", ", target.getDataTypeMappingNames()));
    wOverrides.clearAll(false);
    for (SourceFieldTypeMapping mapping : target.getFieldTypeMappings()) {
      if (mapping == null || Utils.isEmpty(mapping.getSourceFieldName())) {
        continue;
      }
      TableItem item = new TableItem(wOverrides.table, SWT.NONE);
      item.setText(1, Const.NVL(mapping.getSourceFieldName(), ""));
      item.setText(2, Const.NVL(mapping.getTargetFieldName(), ""));
      item.setText(
          3,
          mapping.getTargetHopType() > IValueMeta.TYPE_NONE
              ? Const.NVL(DataTypeMappingPatternSupport.hopTypeName(mapping.getTargetHopType()), "")
              : "");
      item.setText(4, Const.NVL(mapping.getLength(), ""));
      item.setText(5, Const.NVL(mapping.getPrecision(), ""));
      FieldConversionOptions conv = mapping.getConversion();
      item.setText(6, Const.NVL(conv.getConversionMask(), ""));
      item.setText(7, Const.NVL(conv.getDecimalSymbol(), ""));
      item.setText(8, Const.NVL(conv.getGroupingSymbol(), ""));
      item.setText(9, Const.NVL(conv.getDateFormatLocale(), ""));
      item.setText(10, Const.NVL(conv.getDateFormatTimeZone(), ""));
      item.setText(11, mapping.isDisabled() ? "N" : "Y");
    }
    wOverrides.optimizeTableView();
    refreshEffective();
  }

  public void saveTo(IDataTypeMappingTarget target) {
    if (target == null) {
      return;
    }
    target.setDataTypeMappingNames(parseProfileNames());
    target.setFieldTypeMappings(readOverrides());
  }

  private void addProfile() {
    try {
      // MetadataManager.newMetadata() always parents the dialog on HopGui shell, which breaks
      // TableView cell editors under an already-open source-model dialog. Open with our shell.
      MetadataManager<DataTypeMappingMeta> manager = createMetadataManager();
      DataTypeMappingMeta element = new DataTypeMappingMeta();
      var editor = manager.createEditor(element);
      org.apache.hop.ui.core.metadata.MetadataEditorDialog dialog =
          new org.apache.hop.ui.core.metadata.MetadataEditorDialog(shell(), editor);
      String name = dialog.open();
      if (Utils.isEmpty(name)) {
        return;
      }
      List<String> names = parseProfileNames();
      if (!names.contains(name)) {
        names.add(name);
      }
      setProfileNames(names);
      refreshEffective();
    } catch (Exception e) {
      new ErrorDialog(
          shell(),
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Add.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Add.Error.Message"),
          e);
    }
  }

  private void selectProfiles() {
    try {
      MetadataManager<DataTypeMappingMeta> manager = createMetadataManager();
      List<String> available = manager.getNames();
      if (available == null || available.isEmpty()) {
        MessageBox box = new MessageBox(shell(), SWT.ICON_INFORMATION | SWT.OK);
        box.setText(
            BaseMessages.getString(
                PKG, "HopGuiSourceTableDialog.DataTypeMapping.Select.Empty.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiSourceTableDialog.DataTypeMapping.Select.Empty.Message"));
        box.open();
        return;
      }
      String[] choices = available.toArray(new String[0]);
      EnterSelectionDialog dialog =
          new EnterSelectionDialog(
              shell(),
              choices,
              BaseMessages.getString(
                  PKG, "HopGuiSourceTableDialog.DataTypeMapping.Select.Dialog.Title"),
              BaseMessages.getString(
                  PKG, "HopGuiSourceTableDialog.DataTypeMapping.Select.Dialog.Message"));
      dialog.setMulti(true);
      // Pre-select currently attached profiles when possible.
      List<String> current = parseProfileNames();
      if (!current.isEmpty()) {
        List<Integer> preselected = new ArrayList<>();
        for (int i = 0; i < choices.length; i++) {
          if (current.contains(choices[i])) {
            preselected.add(i);
          }
        }
        if (!preselected.isEmpty()) {
          dialog.setSelectedNrs(preselected);
        }
      }
      if (dialog.open() == null) {
        return;
      }
      int[] indices = dialog.getSelectionIndeces();
      if (indices == null || indices.length == 0) {
        return;
      }
      // Preserve selection order from the dialog indices.
      List<String> selected = new ArrayList<>();
      Set<String> seen = new LinkedHashSet<>();
      for (int index : indices) {
        if (index >= 0 && index < choices.length && seen.add(choices[index])) {
          selected.add(choices[index]);
        }
      }
      setProfileNames(selected);
      refreshEffective();
    } catch (Exception e) {
      new ErrorDialog(
          shell(),
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Select.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiSourceTableDialog.DataTypeMapping.Select.Error.Message"),
          e);
    }
  }

  private void editProfiles() {
    try {
      MetadataManager<DataTypeMappingMeta> manager = createMetadataManager();
      List<String> attached = parseProfileNames();
      String toEdit = null;
      if (attached.size() == 1) {
        toEdit = attached.get(0);
      } else if (attached.size() > 1) {
        String[] choices = attached.toArray(new String[0]);
        EnterSelectionDialog dialog =
            new EnterSelectionDialog(
                shell(),
                choices,
                BaseMessages.getString(
                    PKG, "HopGuiSourceTableDialog.DataTypeMapping.Edit.Dialog.Title"),
                BaseMessages.getString(
                    PKG, "HopGuiSourceTableDialog.DataTypeMapping.Edit.Dialog.Message"));
        toEdit = dialog.open();
      } else {
        List<String> available = manager.getNames();
        if (available == null || available.isEmpty()) {
          addProfile();
          return;
        }
        String[] choices = available.toArray(new String[0]);
        EnterSelectionDialog dialog =
            new EnterSelectionDialog(
                shell(),
                choices,
                BaseMessages.getString(
                    PKG, "HopGuiSourceTableDialog.DataTypeMapping.Edit.Dialog.Title"),
                BaseMessages.getString(
                    PKG, "HopGuiSourceTableDialog.DataTypeMapping.Edit.Dialog.Message"));
        toEdit = dialog.open();
      }
      if (Utils.isEmpty(toEdit)) {
        return;
      }
      // Open with this dialog shell as parent (same nested-dialog fix as Add).
      DataTypeMappingMeta element = manager.loadElement(toEdit);
      if (element == null) {
        return;
      }
      var editor = manager.createEditor(element);
      org.apache.hop.ui.core.metadata.MetadataEditorDialog dialog =
          new org.apache.hop.ui.core.metadata.MetadataEditorDialog(shell(), editor);
      dialog.open();
      refreshEffective();
    } catch (Exception e) {
      new ErrorDialog(
          shell(),
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Edit.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DataTypeMapping.Edit.Error.Message"),
          e);
    }
  }

  private MetadataManager<DataTypeMappingMeta> createMetadataManager() {
    return new MetadataManager<>(variables, metadataProvider, DataTypeMappingMeta.class, shell());
  }

  private Shell shell() {
    if (wProfileNames != null && !wProfileNames.isDisposed()) {
      return wProfileNames.getShell();
    }
    if (wEffective != null && !wEffective.isDisposed()) {
      return wEffective.getShell();
    }
    return null;
  }

  private List<String> parseProfileNames() {
    List<String> names = new ArrayList<>();
    if (wProfileNames == null || Utils.isEmpty(wProfileNames.getText())) {
      return names;
    }
    for (String part : wProfileNames.getText().split("[,;]")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        names.add(trimmed);
      }
    }
    return names;
  }

  private void setProfileNames(List<String> names) {
    if (wProfileNames == null || wProfileNames.isDisposed()) {
      return;
    }
    if (names == null || names.isEmpty()) {
      wProfileNames.setText("");
    } else {
      wProfileNames.setText(String.join(", ", names));
    }
  }

  public List<SourceFieldTypeMapping> readOverrides() {
    List<SourceFieldTypeMapping> list = new ArrayList<>();
    for (int i = 0; i < wOverrides.nrNonEmpty(); i++) {
      TableItem item = wOverrides.getNonEmpty(i);
      String sourceName = item.getText(1);
      if (Utils.isEmpty(sourceName)) {
        continue;
      }
      SourceFieldTypeMapping mapping = new SourceFieldTypeMapping(sourceName.trim());
      mapping.setTargetFieldName(item.getText(2));
      int typeId = DataTypeMappingPatternSupport.hopTypeId(item.getText(3));
      mapping.setTargetHopType(typeId > 0 ? typeId : IValueMeta.TYPE_NONE);
      mapping.setLength(item.getText(4));
      mapping.setPrecision(item.getText(5));
      FieldConversionOptions conv = mapping.getConversion();
      conv.setConversionMask(item.getText(6));
      conv.setDecimalSymbol(item.getText(7));
      conv.setGroupingSymbol(item.getText(8));
      conv.setDateFormatLocale(item.getText(9));
      conv.setDateFormatTimeZone(item.getText(10));
      mapping.setDisabled("N".equalsIgnoreCase(item.getText(11)));
      list.add(mapping);
    }
    return list;
  }

  public void refreshEffective() {
    try {
      List<PhysicalSourceField> physical =
          physicalFieldsSupplier != null ? physicalFieldsSupplier.get() : List.of();
      List<String> profileNames = parseProfileNames();
      List<DataTypeMappingMeta> profiles =
          DataTypeMappingResolver.loadProfiles(metadataProvider, profileNames);
      List<EffectiveSourceField> effective =
          DataTypeMappingResolver.resolveAll(physical, profiles, readOverrides());
      wEffective.clearAll(false);
      for (EffectiveSourceField field : effective) {
        if (field == null) {
          continue;
        }
        TableItem item = new TableItem(wEffective.table, SWT.NONE);
        item.setText(1, Const.NVL(field.getSourceFieldName(), ""));
        item.setText(2, Const.NVL(field.getEffectiveFieldName(), ""));
        item.setText(
            3, Const.NVL(DataTypeMappingPatternSupport.hopTypeName(field.effectiveHopType()), ""));
        item.setText(4, Const.NVL(field.getLength(), ""));
        item.setText(5, Const.NVL(field.getPrecision(), ""));
        item.setText(6, Const.NVL(field.getConversion().getConversionMask(), ""));
        item.setText(7, String.join(", ", field.getProvenance()));
      }
      wEffective.optimizeTableView();
    } catch (Exception e) {
      new ErrorDialog(
          wEffective.getShell(),
          BaseMessages.getString(
              PKG, "HopGuiSourceTableDialog.DataTypeMapping.Refresh.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiSourceTableDialog.DataTypeMapping.Refresh.Error.Message"),
          e);
    }
  }
}
