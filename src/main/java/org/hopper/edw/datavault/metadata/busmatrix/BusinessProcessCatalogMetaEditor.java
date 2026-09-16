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
package org.hopper.edw.datavault.metadata.busmatrix;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPlugin;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.hopper.edw.datavault.naming.EdwNamingWidgetSupport;

/** Editor for {@link BusinessProcessCatalogMeta}. */
@GuiPlugin(description = "Editor for Business Process Catalog metadata")
public class BusinessProcessCatalogMetaEditor extends MetadataEditor<BusinessProcessCatalogMeta> {

  private static final Class<?> PKG = BusinessProcessCatalogMeta.class;

  private Text wName;
  private Text wDescription;
  private TableView wDomains;
  private TableView wLevel1;
  private TableView wLevel2;
  private TableView wLevel3;

  public BusinessProcessCatalogMetaEditor(
      HopGui hopGui,
      MetadataManager<BusinessProcessCatalogMeta> manager,
      BusinessProcessCatalogMeta metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  protected Button createHelpButton(Shell shell) {
    return DialogHelpSupport.createHelpButton(shell, HelpTopics.BUSINESS_PROCESS_CATALOG);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "BusinessProcessCatalogMetaEditor.Name.Label"));
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
    EdwNamingWidgetSupport.enableAndLayout(
        wName, hopGui.getVariables(), EdwNamingSchemeTypes.HOP_METADATA, fdName);

    Label wlDescription = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDescription);
    wlDescription.setText(
        BaseMessages.getString(PKG, "BusinessProcessCatalogMetaEditor.Description.Label"));
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

    CTabFolder tabFolder = new CTabFolder(parent, SWT.BORDER);
    FormData fdTabs = new FormData();
    fdTabs.top = new FormAttachment(wDescription, margin);
    fdTabs.left = new FormAttachment(0, 0);
    fdTabs.right = new FormAttachment(100, 0);
    fdTabs.bottom = new FormAttachment(100, -margin);
    tabFolder.setLayoutData(fdTabs);

    wDomains = createTermTab(tabFolder, "Domains", null);
    wLevel1 = createTermTab(tabFolder, "Level1", wDomains);
    wLevel2 = createTermTab(tabFolder, "Level2", wLevel1);
    wLevel3 = createTermTab(tabFolder, "Level3", wLevel2);
    tabFolder.setSelection(0);

    setWidgetsContent();
    resetChanged();
    Listener modifyListener = e -> setChanged();
    wName.addListener(SWT.Modify, modifyListener);
    wDescription.addListener(SWT.Modify, modifyListener);
  }

  private TableView createTermTab(CTabFolder folder, String key, TableView parentTable) {
    CTabItem tab = new CTabItem(folder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "BusinessProcessCatalogMetaEditor." + key + ".Tab"));

    Composite comp = new Composite(folder, SWT.NONE);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    ColumnInfo nameCol =
        new ColumnInfo(
            BaseMessages.getString(PKG, "BusinessProcessCatalogMetaEditor.Name.Column"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false,
            false);
    ColumnInfo parentCol;
    if (parentTable == null) {
      parentCol =
          new ColumnInfo(
              BaseMessages.getString(PKG, "BusinessProcessCatalogMetaEditor.Parent.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true);
    } else {
      parentCol =
          new ColumnInfo(
              BaseMessages.getString(PKG, "BusinessProcessCatalogMetaEditor.Parent.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[0],
              false);
      parentCol.setComboValueSupplier(() -> namesFrom(parentTable));
    }
    ColumnInfo descriptionCol =
        new ColumnInfo(
            BaseMessages.getString(PKG, "BusinessProcessCatalogMetaEditor.Description.Column"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false,
            false);
    TableView table =
        new TableView(
            manager.getVariables(),
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            new ColumnInfo[] {nameCol, parentCol, descriptionCol},
            1,
            false,
            e -> setChanged(),
            PropsUi.getInstance());
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    fd.bottom = new FormAttachment(100, 0);
    table.setLayoutData(fd);
    return table;
  }

  @Override
  public void setWidgetsContent() {
    BusinessProcessCatalogMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    fillTable(wDomains, meta.getDomains());
    fillTable(wLevel1, meta.getLevel1());
    fillTable(wLevel2, meta.getLevel2());
    fillTable(wLevel3, meta.getLevel3());
  }

  @Override
  public void getWidgetsContent(BusinessProcessCatalogMeta meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    meta.setDomains(readTable(wDomains));
    meta.setLevel1(readTable(wLevel1));
    meta.setLevel2(readTable(wLevel2));
    meta.setLevel3(readTable(wLevel3));
  }

  private static void fillTable(TableView table, List<BusinessProcessTerm> terms) {
    table.clearAll(false);
    if (terms != null) {
      for (BusinessProcessTerm term : terms) {
        if (term == null) {
          continue;
        }
        TableItem item = new TableItem(table.table, SWT.NONE);
        item.setText(1, Const.NVL(term.getName(), ""));
        item.setText(2, Const.NVL(term.getParentName(), ""));
        item.setText(3, Const.NVL(term.getDescription(), ""));
      }
    }
    table.optimizeTableView();
  }

  private static String[] namesFrom(TableView table) {
    if (table == null || table.table == null || table.table.isDisposed()) {
      return new String[0];
    }
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (int i = 0; i < table.nrNonEmpty(); i++) {
      String name = table.getNonEmpty(i).getText(1);
      if (name != null && !name.isBlank()) {
        names.add(name.trim());
      }
    }
    return names.toArray(String[]::new);
  }

  private static List<BusinessProcessTerm> readTable(TableView table) {
    List<BusinessProcessTerm> terms = new ArrayList<>();
    for (int i = 0; i < table.nrNonEmpty(); i++) {
      TableItem item = table.getNonEmpty(i);
      String name = item.getText(1);
      if (name == null || name.isBlank()) {
        continue;
      }
      terms.add(new BusinessProcessTerm(name.trim(), item.getText(3), item.getText(2)));
    }
    return terms;
  }
}
