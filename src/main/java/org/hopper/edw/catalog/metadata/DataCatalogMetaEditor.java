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
package org.hopper.edw.catalog.metadata;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.spi.IDataCatalog;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

/** Editor for {@link DataCatalogMeta} with type-specific catalog configuration. */
@GuiPlugin(description = "Editor for Data Catalog metadata")
public class DataCatalogMetaEditor extends MetadataEditor<DataCatalogMeta> {

  private static final Class<?> PKG = DataCatalogMetaEditor.class;

  private Text wName;
  private Text wDescription;
  private Button wEnabled;
  private Combo wCatalogType;
  private Composite wCatalogSpecificComp;
  private GuiCompositeWidgets guiCompositeWidgets;
  private Map<String, IDataCatalog> catalogByType;
  private final AtomicBoolean busyChangingCatalogType = new AtomicBoolean(false);

  public DataCatalogMetaEditor(
      HopGui hopGui, MetadataManager<DataCatalogMeta> manager, DataCatalogMeta metadata) {
    super(hopGui, manager, metadata);
    catalogByType = populateCatalogMap();
    IDataCatalog current = getMetadata().getCatalogOrDefault();
    catalogByType.put(current.getPluginId(), current);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "DataCatalogMetaEditor.Name.Label"));
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
    Control lastControl = wName;

    Label wlDescription = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDescription);
    wlDescription.setText(BaseMessages.getString(PKG, "DataCatalogMetaEditor.Description.Label"));
    FormData fdlDescription = new FormData();
    fdlDescription.top = new FormAttachment(lastControl, margin);
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
    lastControl = wDescription;

    Label wlEnabled = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlEnabled);
    wlEnabled.setText(BaseMessages.getString(PKG, "DataCatalogMetaEditor.Enabled.Label"));
    FormData fdlEnabled = new FormData();
    fdlEnabled.top = new FormAttachment(lastControl, margin);
    fdlEnabled.left = new FormAttachment(0, 0);
    fdlEnabled.right = new FormAttachment(middle, -margin);
    wlEnabled.setLayoutData(fdlEnabled);

    wEnabled = new Button(parent, SWT.CHECK);
    PropsUi.setLook(wEnabled);
    FormData fdEnabled = new FormData();
    fdEnabled.top = new FormAttachment(wlEnabled, 0, SWT.CENTER);
    fdEnabled.left = new FormAttachment(middle, 0);
    wEnabled.setLayoutData(fdEnabled);
    lastControl = wEnabled;

    Label wlCatalogType = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlCatalogType);
    wlCatalogType.setText(BaseMessages.getString(PKG, "DataCatalogMetaEditor.Type.Label"));
    FormData fdlCatalogType = new FormData();
    fdlCatalogType.top = new FormAttachment(lastControl, margin);
    fdlCatalogType.left = new FormAttachment(0, 0);
    fdlCatalogType.right = new FormAttachment(middle, -margin);
    wlCatalogType.setLayoutData(fdlCatalogType);

    wCatalogType = new Combo(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    wCatalogType.setItems(getCatalogTypeLabels());
    PropsUi.setLook(wCatalogType);
    FormData fdCatalogType = new FormData();
    fdCatalogType.top = new FormAttachment(wlCatalogType, 0, SWT.CENTER);
    fdCatalogType.left = new FormAttachment(middle, 0);
    fdCatalogType.right = new FormAttachment(100, 0);
    wCatalogType.setLayoutData(fdCatalogType);
    lastControl = wCatalogType;

    wCatalogSpecificComp = new Composite(parent, SWT.BACKGROUND);
    wCatalogSpecificComp.setLayout(new FormLayout());
    FormData fdCatalogSpecificComp = new FormData();
    fdCatalogSpecificComp.left = new FormAttachment(0, 0);
    fdCatalogSpecificComp.right = new FormAttachment(100, 0);
    fdCatalogSpecificComp.top = new FormAttachment(lastControl, margin);
    fdCatalogSpecificComp.bottom = new FormAttachment(100, 0);
    wCatalogSpecificComp.setLayoutData(fdCatalogSpecificComp);
    PropsUi.setLook(wCatalogSpecificComp);

    addGuiCompositeWidgets();

    setWidgetsContent();
    resetChanged();

    Listener modifyListener = e -> setChanged();
    wName.addListener(SWT.Modify, modifyListener);
    wDescription.addListener(SWT.Modify, modifyListener);
    wEnabled.addListener(SWT.Selection, modifyListener);
    wCatalogType.addListener(SWT.Modify, modifyListener);
    wCatalogType.addListener(SWT.Modify, e -> changeCatalogType());
  }

  private Map<String, IDataCatalog> populateCatalogMap() {
    Map<String, IDataCatalog> map = new HashMap<>();
    for (String typeId : DataCatalogMetaObjectFactory.getKnownTypeIds()) {
      try {
        map.put(typeId, DataCatalogMetaObjectFactory.newCatalog(typeId));
      } catch (HopException e) {
        HopGui.getInstance()
            .getLog()
            .logError("Error instantiating data catalog type: " + typeId, e);
      }
    }
    return map;
  }

  private void changeCatalogType() {
    if (busyChangingCatalogType.get()) {
      return;
    }
    busyChangingCatalogType.set(true);

    try {
      DataCatalogMeta meta = getMetadata();
      String oldTypeId = meta.getCatalogOrDefault().getPluginId();
      String newTypeLabel = wCatalogType.getText();

      wCatalogType.setText(getTypeLabel(oldTypeId));
      getWidgetsContent(meta);

      catalogByType.put(oldTypeId, meta.getCatalogOrDefault());

      String newTypeId = getTypeId(newTypeLabel);
      wCatalogType.setText(getTypeLabel(newTypeId));

      IDataCatalog catalog = catalogByType.get(newTypeId);
      if (catalog == null) {
        catalog = DataCatalogMetaObjectFactory.newCatalog(newTypeId);
        catalogByType.put(newTypeId, catalog);
      }
      meta.setCatalog(catalog);

      addGuiCompositeWidgets();
      setWidgetsContent();
      wCatalogSpecificComp.getParent().layout(true, true);
    } catch (HopException e) {
      new ErrorDialog(getShell(), "Error", "Unable to change data catalog type", e);
    } finally {
      busyChangingCatalogType.set(false);
    }
  }

  private void addGuiCompositeWidgets() {
    for (Control child : wCatalogSpecificComp.getChildren()) {
      child.dispose();
    }

    IDataCatalog catalog = getMetadata().getCatalogOrDefault();
    guiCompositeWidgets = new GuiCompositeWidgets(manager.getVariables());
    guiCompositeWidgets.createCompositeWidgets(
        catalog, null, wCatalogSpecificComp, getGuiPluginElementParentId(catalog), null);
    guiCompositeWidgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            setChanged();
          }
        });
  }

  private static String getGuiPluginElementParentId(IDataCatalog catalog) {
    if (FileDataCatalog.PLUGIN_ID.equals(catalog.getPluginId())) {
      return FileDataCatalog.GUI_PLUGIN_ELEMENT_PARENT_ID;
    }
    throw new IllegalStateException("Unknown data catalog type: " + catalog.getPluginId());
  }

  private String[] getCatalogTypeLabels() {
    return DataCatalogMetaObjectFactory.getKnownTypeIds().stream()
        .map(this::getTypeLabel)
        .sorted(Comparator.comparing(String::toLowerCase))
        .toArray(String[]::new);
  }

  private String getTypeLabel(String typeId) {
    return BaseMessages.getString(PKG, "DataCatalogMetaEditor.Type." + typeId);
  }

  private String getTypeId(String label) {
    for (String typeId : DataCatalogMetaObjectFactory.getKnownTypeIds()) {
      if (getTypeLabel(typeId).equals(label)) {
        return typeId;
      }
    }
    return label;
  }

  @Override
  public void setWidgetsContent() {
    DataCatalogMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wEnabled.setSelection(meta.isEnabled());
    wCatalogType.setText(getTypeLabel(meta.getCatalogOrDefault().getPluginId()));
    if (guiCompositeWidgets != null) {
      IDataCatalog catalog = meta.getCatalogOrDefault();
      guiCompositeWidgets.setWidgetsContents(
          catalog, wCatalogSpecificComp, getGuiPluginElementParentId(catalog));
    }
  }

  @Override
  public void getWidgetsContent(DataCatalogMeta meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    meta.setEnabled(wEnabled.getSelection());

    String typeId = getTypeId(wCatalogType.getText());
    IDataCatalog catalog = meta.getCatalogOrDefault();
    if (!typeId.equals(catalog.getPluginId())) {
      catalog = catalogByType.get(typeId);
      if (catalog == null) {
        try {
          catalog = DataCatalogMetaObjectFactory.newCatalog(typeId);
          catalogByType.put(typeId, catalog);
        } catch (HopException e) {
          new ErrorDialog(getShell(), "Error", "Unable to resolve data catalog type", e);
          return;
        }
      }
      meta.setCatalog(catalog);
    }

    if (guiCompositeWidgets != null && !guiCompositeWidgets.getWidgetsMap().isEmpty()) {
      guiCompositeWidgets.getWidgetsContents(
          meta.getCatalogOrDefault(), getGuiPluginElementParentId(meta.getCatalogOrDefault()));
    }
  }

  @Override
  public boolean setFocus() {
    if (wName == null || wName.isDisposed()) {
      return false;
    }
    return wName.setFocus();
  }
}
