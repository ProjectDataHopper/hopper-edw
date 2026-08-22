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
package org.hopper.edw.datavault.metadata.lineage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
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

/** Editor for {@link LineageBackendMeta} with type-specific settings. */
@GuiPlugin(description = "Editor for Lineage Backend metadata")
public class LineageBackendMetaEditor extends MetadataEditor<LineageBackendMeta> {

  private static final Class<?> PKG = LineageBackendMetaEditor.class;

  private Text wName;
  private Text wDescription;
  private Button wEnabled;
  private Combo wType;
  private Button wTest;
  private Composite wSpecificComp;
  private GuiCompositeWidgets guiCompositeWidgets;
  private Map<String, ILineageBackendSettings> settingsByType;
  private final AtomicBoolean busyChangingType = new AtomicBoolean(false);

  public LineageBackendMetaEditor(
      HopGui hopGui, MetadataManager<LineageBackendMeta> manager, LineageBackendMeta metadata) {
    super(hopGui, manager, metadata);
    settingsByType = populateSettingsMap();
    ILineageBackendSettings current = getMetadata().getSettingsOrDefault();
    settingsByType.put(current.getPluginId(), current);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "LineageBackendMetaEditor.Name.Label"));
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
    wlDescription.setText(
        BaseMessages.getString(PKG, "LineageBackendMetaEditor.Description.Label"));
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
    wlEnabled.setText(BaseMessages.getString(PKG, "LineageBackendMetaEditor.Enabled.Label"));
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

    Label wlType = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlType);
    wlType.setText(BaseMessages.getString(PKG, "LineageBackendMetaEditor.Type.Label"));
    FormData fdlType = new FormData();
    fdlType.top = new FormAttachment(lastControl, margin);
    fdlType.left = new FormAttachment(0, 0);
    fdlType.right = new FormAttachment(middle, -margin);
    wlType.setLayoutData(fdlType);

    wType = new Combo(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    wType.setItems(getTypeLabels());
    PropsUi.setLook(wType);
    FormData fdType = new FormData();
    fdType.top = new FormAttachment(wlType, 0, SWT.CENTER);
    fdType.left = new FormAttachment(middle, 0);
    fdType.right = new FormAttachment(100, 0);
    wType.setLayoutData(fdType);
    lastControl = wType;

    wTest = new Button(parent, SWT.PUSH);
    PropsUi.setLook(wTest);
    wTest.setText(BaseMessages.getString(PKG, "LineageBackendMetaEditor.Test.Label"));
    FormData fdTest = new FormData();
    fdTest.top = new FormAttachment(lastControl, margin);
    fdTest.left = new FormAttachment(middle, 0);
    wTest.setLayoutData(fdTest);
    lastControl = wTest;

    wSpecificComp = new Composite(parent, SWT.BACKGROUND);
    wSpecificComp.setLayout(new FormLayout());
    FormData fdSpecific = new FormData();
    fdSpecific.left = new FormAttachment(0, 0);
    fdSpecific.right = new FormAttachment(100, 0);
    fdSpecific.top = new FormAttachment(lastControl, margin);
    fdSpecific.bottom = new FormAttachment(100, 0);
    wSpecificComp.setLayoutData(fdSpecific);
    PropsUi.setLook(wSpecificComp);

    addGuiCompositeWidgets();
    setWidgetsContent();
    resetChanged();

    Listener modifyListener = e -> setChanged();
    wName.addListener(SWT.Modify, modifyListener);
    wDescription.addListener(SWT.Modify, modifyListener);
    wEnabled.addListener(SWT.Selection, modifyListener);
    wType.addListener(SWT.Modify, modifyListener);
    wType.addListener(SWT.Modify, e -> changeType());
    wTest.addListener(SWT.Selection, e -> testConnection());
  }

  void testConnection() {
    try {
      LineageBackendMeta meta = getMetadata();
      getWidgetsContent(meta);
      LineageConnectionTestResult result =
          meta.getSettingsOrDefault()
              .testConnection(
                  manager.getVariables(), manager.getMetadataProvider(), hopGui.getLog());
      MessageBox box =
          new MessageBox(getShell(), result.isOk() ? SWT.ICON_INFORMATION : SWT.ICON_WARNING);
      box.setText(BaseMessages.getString(PKG, "LineageBackendMetaEditor.Test.Title"));
      box.setMessage(Const.NVL(result.getMessage(), ""));
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "LineageBackendMetaEditor.Test.Title"),
          BaseMessages.getString(PKG, "LineageBackendMetaEditor.Test.Failed"),
          e);
    }
  }

  private Map<String, ILineageBackendSettings> populateSettingsMap() {
    Map<String, ILineageBackendSettings> map = new HashMap<>();
    for (String typeId : LineageBackendSettingsFactory.getKnownTypeIds()) {
      try {
        map.put(typeId, LineageBackendSettingsFactory.newSettings(typeId));
      } catch (HopException e) {
        HopGui.getInstance()
            .getLog()
            .logError("Error instantiating lineage backend type: " + typeId, e);
      }
    }
    return map;
  }

  private void changeType() {
    if (busyChangingType.get()) {
      return;
    }
    busyChangingType.set(true);
    try {
      LineageBackendMeta meta = getMetadata();
      String oldTypeId = meta.getSettingsOrDefault().getPluginId();
      String newTypeLabel = wType.getText();

      wType.setText(getTypeLabel(oldTypeId));
      getWidgetsContent(meta);
      settingsByType.put(oldTypeId, meta.getSettingsOrDefault());

      String newTypeId = getTypeId(newTypeLabel);
      wType.setText(getTypeLabel(newTypeId));

      ILineageBackendSettings settings = settingsByType.get(newTypeId);
      if (settings == null) {
        settings = LineageBackendSettingsFactory.newSettings(newTypeId);
        settingsByType.put(newTypeId, settings);
      }
      meta.setSettings(settings);
      addGuiCompositeWidgets();
      setWidgetsContent();
      wSpecificComp.getParent().layout(true, true);
    } catch (HopException e) {
      new ErrorDialog(getShell(), "Error", "Unable to change lineage backend type", e);
    } finally {
      busyChangingType.set(false);
    }
  }

  private void addGuiCompositeWidgets() {
    for (Control child : wSpecificComp.getChildren()) {
      child.dispose();
    }
    ILineageBackendSettings settings = getMetadata().getSettingsOrDefault();
    guiCompositeWidgets = new GuiCompositeWidgets(manager.getVariables());
    guiCompositeWidgets.createCompositeWidgets(
        settings, null, wSpecificComp, getGuiPluginElementParentId(settings), null);
    guiCompositeWidgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            setChanged();
          }
        });
  }

  static String getGuiPluginElementParentId(ILineageBackendSettings settings) {
    if (settings instanceof MarquezBackendSettings) {
      return MarquezBackendSettings.GUI_PLUGIN_ELEMENT_PARENT_ID;
    }
    if (settings instanceof FileFolderBackendSettings) {
      return FileFolderBackendSettings.GUI_PLUGIN_ELEMENT_PARENT_ID;
    }
    if (settings instanceof LocalModelsBackendSettings) {
      return LocalModelsBackendSettings.GUI_PLUGIN_ELEMENT_PARENT_ID;
    }
    throw new IllegalStateException("Unknown lineage backend type: " + settings.getPluginId());
  }

  private String[] getTypeLabels() {
    return LineageBackendSettingsFactory.getKnownTypeIds().stream()
        .map(this::getTypeLabel)
        .sorted(Comparator.comparing(String::toLowerCase))
        .toArray(String[]::new);
  }

  private String getTypeLabel(String typeId) {
    return BaseMessages.getString(PKG, "LineageBackendMetaEditor.Type." + typeId);
  }

  private String getTypeId(String label) {
    for (String typeId : LineageBackendSettingsFactory.getKnownTypeIds()) {
      if (getTypeLabel(typeId).equals(label)) {
        return typeId;
      }
    }
    return label;
  }

  @Override
  public void setWidgetsContent() {
    LineageBackendMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wEnabled.setSelection(meta.isEnabled());
    wType.setText(getTypeLabel(meta.getSettingsOrDefault().getPluginId()));
    if (guiCompositeWidgets != null) {
      ILineageBackendSettings settings = meta.getSettingsOrDefault();
      guiCompositeWidgets.setWidgetsContents(
          settings, wSpecificComp, getGuiPluginElementParentId(settings));
    }
  }

  @Override
  public void getWidgetsContent(LineageBackendMeta meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    meta.setEnabled(wEnabled.getSelection());

    String typeId = getTypeId(wType.getText());
    ILineageBackendSettings settings = meta.getSettingsOrDefault();
    if (!typeId.equals(settings.getPluginId())) {
      settings = settingsByType.get(typeId);
      if (settings == null) {
        try {
          settings = LineageBackendSettingsFactory.newSettings(typeId);
          settingsByType.put(typeId, settings);
        } catch (HopException e) {
          new ErrorDialog(getShell(), "Error", "Unable to resolve lineage backend type", e);
          return;
        }
      }
      meta.setSettings(settings);
    }

    if (guiCompositeWidgets != null && !guiCompositeWidgets.getWidgetsMap().isEmpty()) {
      guiCompositeWidgets.getWidgetsContents(
          meta.getSettingsOrDefault(), getGuiPluginElementParentId(meta.getSettingsOrDefault()));
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
