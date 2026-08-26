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
package org.hopper.edw.datavault.metadata.sourcemodel.service;

import org.apache.hop.core.Const;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/** GUI editor for {@link SourceModelService} metadata. */
public class SourceModelServiceEditor extends MetadataEditor<SourceModelService> {

  private static final Class<?> PKG = SourceModelService.class;

  private Text wName;
  private Button wEnabled;
  private Text wDescription;
  private TextVar wModelFilename;
  private Text wDefaultRowLimit;
  private Text wMaxRowLimit;
  private Button wAllowSchemaMetadata;

  public SourceModelServiceEditor(
      HopGui hopGui, MetadataManager<SourceModelService> manager, SourceModelService metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  protected Button createHelpButton(Shell shell) {
    return DialogHelpSupport.createHelpButton(shell, HelpTopics.SOURCE_MODEL_SERVICE);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wIcon = new Label(parent, SWT.RIGHT);
    wIcon.setImage(getImage());
    FormData fdlIcon = new FormData();
    fdlIcon.top = new FormAttachment(0, 0);
    fdlIcon.right = new FormAttachment(100, 0);
    wIcon.setLayoutData(fdlIcon);
    PropsUi.setLook(wIcon);

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "SourceModelServiceEditor.Name.Label"));
    FormData fdlName = new FormData();
    fdlName.top = new FormAttachment(wIcon, margin);
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
    Control last = wName;

    Label spacer = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
    FormData fdSpacer = new FormData();
    fdSpacer.left = new FormAttachment(0, 0);
    fdSpacer.top = new FormAttachment(last, 15);
    fdSpacer.right = new FormAttachment(100, 0);
    spacer.setLayoutData(fdSpacer);
    last = spacer;

    Label wlEnabled = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlEnabled);
    wlEnabled.setText(BaseMessages.getString(PKG, "SourceModelServiceEditor.Enabled.Label"));
    FormData fdlEnabled = new FormData();
    fdlEnabled.left = new FormAttachment(0, 0);
    fdlEnabled.right = new FormAttachment(middle, -margin);
    fdlEnabled.top = new FormAttachment(last, margin);
    wlEnabled.setLayoutData(fdlEnabled);
    wEnabled = new Button(parent, SWT.CHECK | SWT.LEFT);
    PropsUi.setLook(wEnabled);
    FormData fdEnabled = new FormData();
    fdEnabled.left = new FormAttachment(middle, 0);
    fdEnabled.top = new FormAttachment(wlEnabled, 0, SWT.CENTER);
    wEnabled.setLayoutData(fdEnabled);
    last = wlEnabled;

    Label wlDescription = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDescription);
    wlDescription.setText(
        BaseMessages.getString(PKG, "SourceModelServiceEditor.Description.Label"));
    FormData fdlDescription = new FormData();
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.right = new FormAttachment(middle, -margin);
    fdlDescription.top = new FormAttachment(last, 2 * margin);
    wlDescription.setLayoutData(fdlDescription);
    wDescription = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.right = new FormAttachment(100, 0);
    fdDescription.top = new FormAttachment(wlDescription, 0, SWT.CENTER);
    wDescription.setLayoutData(fdDescription);
    last = wlDescription;

    Label wlFilename = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlFilename);
    wlFilename.setText(BaseMessages.getString(PKG, "SourceModelServiceEditor.ModelFilename.Label"));
    FormData fdlFilename = new FormData();
    fdlFilename.left = new FormAttachment(0, 0);
    fdlFilename.right = new FormAttachment(middle, -margin);
    fdlFilename.top = new FormAttachment(last, 2 * margin);
    wlFilename.setLayoutData(fdlFilename);

    Button wbbFilename = new Button(parent, SWT.PUSH);
    PropsUi.setLook(wbbFilename);
    wbbFilename.setText("Browse…");
    FormData fdbFilename = new FormData();
    fdbFilename.right = new FormAttachment(100, 0);
    fdbFilename.top = new FormAttachment(wlFilename, 0, SWT.CENTER);
    wbbFilename.setLayoutData(fdbFilename);
    wbbFilename.addListener(
        SWT.Selection,
        e -> {
          String path =
              BaseDialog.presentFileDialog(
                  parent.getShell(),
                  wModelFilename,
                  manager.getVariables(),
                  new String[] {"*.hsm", "*"},
                  new String[] {"Source model (*.hsm)", "All files"},
                  true);
          if (path != null) {
            setChanged();
          }
        });

    wModelFilename =
        new TextVar(manager.getVariables(), parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelFilename);
    wModelFilename.setToolTipText(
        BaseMessages.getString(PKG, "SourceModelServiceEditor.ModelFilename.Tooltip"));
    FormData fdFilename = new FormData();
    fdFilename.left = new FormAttachment(middle, 0);
    fdFilename.right = new FormAttachment(wbbFilename, -margin);
    fdFilename.top = new FormAttachment(wlFilename, 0, SWT.CENTER);
    wModelFilename.setLayoutData(fdFilename);
    last = wlFilename;

    Label wlDefault = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDefault);
    wlDefault.setText(
        BaseMessages.getString(PKG, "SourceModelServiceEditor.DefaultRowLimit.Label"));
    FormData fdlDefault = new FormData();
    fdlDefault.left = new FormAttachment(0, 0);
    fdlDefault.right = new FormAttachment(middle, -margin);
    fdlDefault.top = new FormAttachment(last, 2 * margin);
    wlDefault.setLayoutData(fdlDefault);
    wDefaultRowLimit = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDefaultRowLimit);
    wDefaultRowLimit.setToolTipText(
        BaseMessages.getString(PKG, "SourceModelServiceEditor.DefaultRowLimit.Tooltip"));
    FormData fdDefault = new FormData();
    fdDefault.left = new FormAttachment(middle, 0);
    fdDefault.right = new FormAttachment(100, 0);
    fdDefault.top = new FormAttachment(wlDefault, 0, SWT.CENTER);
    wDefaultRowLimit.setLayoutData(fdDefault);
    last = wlDefault;

    Label wlMax = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlMax);
    wlMax.setText(BaseMessages.getString(PKG, "SourceModelServiceEditor.MaxRowLimit.Label"));
    FormData fdlMax = new FormData();
    fdlMax.left = new FormAttachment(0, 0);
    fdlMax.right = new FormAttachment(middle, -margin);
    fdlMax.top = new FormAttachment(last, 2 * margin);
    wlMax.setLayoutData(fdlMax);
    wMaxRowLimit = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wMaxRowLimit);
    wMaxRowLimit.setToolTipText(
        BaseMessages.getString(PKG, "SourceModelServiceEditor.MaxRowLimit.Tooltip"));
    FormData fdMax = new FormData();
    fdMax.left = new FormAttachment(middle, 0);
    fdMax.right = new FormAttachment(100, 0);
    fdMax.top = new FormAttachment(wlMax, 0, SWT.CENTER);
    wMaxRowLimit.setLayoutData(fdMax);
    last = wlMax;

    Label wlMeta = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlMeta);
    wlMeta.setText(
        BaseMessages.getString(PKG, "SourceModelServiceEditor.AllowSchemaMetadata.Label"));
    FormData fdlMeta = new FormData();
    fdlMeta.left = new FormAttachment(0, 0);
    fdlMeta.right = new FormAttachment(middle, -margin);
    fdlMeta.top = new FormAttachment(last, 2 * margin);
    wlMeta.setLayoutData(fdlMeta);
    wAllowSchemaMetadata = new Button(parent, SWT.CHECK | SWT.LEFT);
    PropsUi.setLook(wAllowSchemaMetadata);
    wAllowSchemaMetadata.setToolTipText(
        BaseMessages.getString(PKG, "SourceModelServiceEditor.AllowSchemaMetadata.Tooltip"));
    FormData fdMeta = new FormData();
    fdMeta.left = new FormAttachment(middle, 0);
    fdMeta.top = new FormAttachment(wlMeta, 0, SWT.CENTER);
    wAllowSchemaMetadata.setLayoutData(fdMeta);
    last = wlMeta;

    Label wlHelp = new Label(parent, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlHelp);
    wlHelp.setText(BaseMessages.getString(PKG, "SourceModelServiceEditor.Help.Label"));
    FormData fdHelp = new FormData();
    fdHelp.left = new FormAttachment(0, 0);
    fdHelp.right = new FormAttachment(100, 0);
    fdHelp.top = new FormAttachment(last, 3 * margin);
    wlHelp.setLayoutData(fdHelp);

    setWidgetsContent();
    Listener mod = e -> setChanged();
    wName.addModifyListener(e -> setChanged());
    wDescription.addModifyListener(e -> setChanged());
    wModelFilename.addModifyListener(e -> setChanged());
    wDefaultRowLimit.addModifyListener(e -> setChanged());
    wMaxRowLimit.addModifyListener(e -> setChanged());
    wEnabled.addListener(SWT.Selection, mod);
    wAllowSchemaMetadata.addListener(SWT.Selection, mod);
  }

  @Override
  public void setWidgetsContent() {
    SourceModelService meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wEnabled.setSelection(meta.isEnabled());
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wModelFilename.setText(Const.NVL(meta.getModelFilename(), ""));
    wDefaultRowLimit.setText(Integer.toString(meta.getDefaultRowLimit()));
    wMaxRowLimit.setText(Integer.toString(meta.getMaxRowLimit()));
    wAllowSchemaMetadata.setSelection(meta.isAllowSchemaMetadata());
  }

  @Override
  public void getWidgetsContent(SourceModelService meta) {
    meta.setName(wName.getText());
    meta.setEnabled(wEnabled.getSelection());
    meta.setDescription(wDescription.getText());
    meta.setModelFilename(wModelFilename.getText());
    meta.setDefaultRowLimit(parseInt(wDefaultRowLimit.getText(), 10_000));
    meta.setMaxRowLimit(parseInt(wMaxRowLimit.getText(), 100_000));
    meta.setAllowSchemaMetadata(wAllowSchemaMetadata.getSelection());
  }

  private static int parseInt(String text, int defaultValue) {
    try {
      return Integer.parseInt(Const.NVL(text, "").trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
