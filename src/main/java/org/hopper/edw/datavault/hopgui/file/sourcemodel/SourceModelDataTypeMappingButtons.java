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
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataEditorDialog;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingMeta;
import org.hopper.edw.datavault.metadata.sourcemodel.DefaultDataTypeMappingNames;
import org.hopper.edw.datavault.metadata.sourcemodel.DefaultDataTypeMappingNames.EditAction;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelConfiguration;

/**
 * Select and Edit buttons to the right of the default data type mappings field. The annotated form
 * lays a button out on its own row, so these controls are attached after the widgets exist.
 */
public final class SourceModelDataTypeMappingButtons {

  private static final Class<?> PKG = SourceModelConfiguration.class;
  private static final String ATTACHED = SourceModelDataTypeMappingButtons.class.getName();

  private SourceModelDataTypeMappingButtons() {}

  public static void attach(
      GuiCompositeWidgets widgets, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (widgets == null || widgets.getWidgetsMap() == null) {
      return;
    }
    Control field =
        widgets.getWidgetsMap().get(SourceModelConfiguration.WIDGET_DEFAULT_DATA_TYPE_MAPPINGS);
    if (!isTextField(field) || field.isDisposed() || field.getData(ATTACHED) != null) {
      return;
    }
    if (!(field.getLayoutData() instanceof FormData fdField) || field.getParent() == null) {
      return;
    }
    field.setData(ATTACHED, Boolean.TRUE);

    Composite parent = field.getParent();
    int margin = PropsUi.getMargin();

    Button wEdit = new Button(parent, SWT.PUSH);
    PropsUi.setLook(wEdit);
    wEdit.setText(
        BaseMessages.getString(PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Edit.Button"));
    wEdit.setToolTipText(
        BaseMessages.getString(
            PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Edit.ToolTip"));
    FormData fdEdit = new FormData();
    fdEdit.right = new FormAttachment(100, 0);
    fdEdit.top = new FormAttachment(field, 0, SWT.CENTER);
    wEdit.setLayoutData(fdEdit);
    wEdit.addListener(SWT.Selection, e -> edit(field, variables, metadataProvider));

    Button wSelect = new Button(parent, SWT.PUSH);
    PropsUi.setLook(wSelect);
    wSelect.setText(
        BaseMessages.getString(
            PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Select.Button"));
    wSelect.setToolTipText(
        BaseMessages.getString(
            PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Select.ToolTip"));
    FormData fdSelect = new FormData();
    fdSelect.right = new FormAttachment(wEdit, -margin);
    fdSelect.top = new FormAttachment(field, 0, SWT.CENTER);
    wSelect.setLayoutData(fdSelect);
    wSelect.addListener(SWT.Selection, e -> select(field, variables, metadataProvider));

    fdField.right = new FormAttachment(wSelect, -margin);
    parent.layout(true, true);
  }

  private static void select(
      Control field, IVariables variables, IHopMetadataProvider metadataProvider) {
    try {
      MetadataManager<DataTypeMappingMeta> manager = manager(field, variables, metadataProvider);
      List<String> available = manager.getNames();
      if (available == null || available.isEmpty()) {
        MessageBox box = new MessageBox(field.getShell(), SWT.ICON_INFORMATION | SWT.OK);
        box.setText(
            BaseMessages.getString(
                PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Select.Empty.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Select.Empty.Message"));
        box.open();
        return;
      }
      String[] choices = available.toArray(new String[0]);
      EnterSelectionDialog dialog =
          new EnterSelectionDialog(
              field.getShell(),
              choices,
              BaseMessages.getString(
                  PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Select.Dialog.Title"),
              BaseMessages.getString(
                  PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Select.Dialog.Message"));
      dialog.setMulti(true);
      List<String> current = DefaultDataTypeMappingNames.parse(read(field));
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
      List<String> selected = new ArrayList<>();
      Set<String> seen = new LinkedHashSet<>();
      for (int index : indices) {
        if (index >= 0 && index < choices.length && seen.add(choices[index])) {
          selected.add(choices[index]);
        }
      }
      write(field, DefaultDataTypeMappingNames.format(selected));
    } catch (Exception e) {
      new ErrorDialog(
          field.getShell(),
          BaseMessages.getString(
              PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Select.Error.Title"),
          BaseMessages.getString(
              PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Select.Error.Message"),
          e);
    }
  }

  private static void edit(
      Control field, IVariables variables, IHopMetadataProvider metadataProvider) {
    try {
      List<String> selected = DefaultDataTypeMappingNames.parse(read(field));
      EditAction action = DefaultDataTypeMappingNames.editAction(selected);
      MetadataManager<DataTypeMappingMeta> manager = manager(field, variables, metadataProvider);
      if (action == EditAction.CREATE) {
        createAndAttach(field, manager);
        return;
      }
      String name = selected.get(0);
      if (action == EditAction.ASK) {
        name =
            ask(
                field.getShell(),
                selected,
                "SourceModelConfiguration.DefaultDataTypeMappings.Edit.Dialog.Title",
                "SourceModelConfiguration.DefaultDataTypeMappings.Edit.Dialog.Message");
        if (Utils.isEmpty(name)) {
          return;
        }
      }
      openExisting(field, manager, name);
    } catch (Exception e) {
      new ErrorDialog(
          field.getShell(),
          BaseMessages.getString(
              PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Edit.Error.Title"),
          BaseMessages.getString(
              PKG, "SourceModelConfiguration.DefaultDataTypeMappings.Edit.Error.Message"),
          e);
    }
  }

  private static void createAndAttach(Control field, MetadataManager<DataTypeMappingMeta> manager)
      throws Exception {
    String saved = openEditor(field.getShell(), manager, new DataTypeMappingMeta());
    if (Utils.isEmpty(saved)) {
      return;
    }
    write(
        field,
        DefaultDataTypeMappingNames.format(
            DefaultDataTypeMappingNames.add(DefaultDataTypeMappingNames.parse(read(field)), saved)));
  }

  private static void openExisting(
      Control field, MetadataManager<DataTypeMappingMeta> manager, String name) throws Exception {
    DataTypeMappingMeta element = manager.loadElement(name);
    if (element == null) {
      element = new DataTypeMappingMeta();
      element.setName(name);
    }
    String saved = openEditor(field.getShell(), manager, element);
    if (Utils.isEmpty(saved) || saved.equals(name)) {
      return;
    }
    write(
        field,
        DefaultDataTypeMappingNames.format(
            DefaultDataTypeMappingNames.rename(
                DefaultDataTypeMappingNames.parse(read(field)), name, saved)));
  }

  private static String openEditor(
      Shell shell, MetadataManager<DataTypeMappingMeta> manager, DataTypeMappingMeta element)
      throws Exception {
    MetadataEditor<DataTypeMappingMeta> editor = manager.createEditor(element);
    return new MetadataEditorDialog(shell, editor).open();
  }

  private static String ask(Shell shell, List<String> choices, String titleKey, String messageKey) {
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            shell,
            choices.toArray(new String[0]),
            BaseMessages.getString(PKG, titleKey),
            BaseMessages.getString(PKG, messageKey));
    return dialog.open();
  }

  private static MetadataManager<DataTypeMappingMeta> manager(
      Control field, IVariables variables, IHopMetadataProvider metadataProvider) {
    return new MetadataManager<>(variables, metadataProvider, DataTypeMappingMeta.class, field.getShell());
  }

  private static boolean isTextField(Control field) {
    return field instanceof Text || field instanceof TextVar;
  }

  private static String read(Control field) {
    if (field instanceof TextVar textVar) {
      return textVar.getText();
    }
    if (field instanceof Text text) {
      return text.getText();
    }
    return "";
  }

  private static void write(Control field, String value) {
    String text = value == null ? "" : value;
    if (field instanceof TextVar textVar) {
      textVar.setText(text);
    } else if (field instanceof Text plain) {
      plain.setText(text);
    }
  }
}
