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

import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.EnumDialogSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.hopgui.widget.MarkdownEditPreviewComposite;
import org.hopper.edw.datavault.metadata.dimensional.DmFieldDocumentation;
import org.hopper.edw.datavault.metadata.dimensional.DmScdUpdatePolicy;

/** Editor for a dimensional field: identity, required flag, and Markdown notes/requirements. */
public class HopGuiDmFieldEditorDialog {

  public enum Kind {
    ATTRIBUTE,
    NATURAL_KEY,
    MEASURE,
    DEGENERATE
  }

  public static final class Model {
    private Kind kind = Kind.ATTRIBUTE;
    private String fieldName;
    private String sourceFieldName;
    private DmScdUpdatePolicy scdUpdatePolicy = DmScdUpdatePolicy.TYPE1;
    private String previousFieldName;
    private boolean additive = true;
    private final DmFieldDocumentation documentation = new DmFieldDocumentation();

    public Kind getKind() {
      return kind;
    }

    public void setKind(Kind kind) {
      this.kind = kind == null ? Kind.ATTRIBUTE : kind;
    }

    public String getFieldName() {
      return fieldName;
    }

    public void setFieldName(String fieldName) {
      this.fieldName = fieldName;
    }

    public String getSourceFieldName() {
      return sourceFieldName;
    }

    public void setSourceFieldName(String sourceFieldName) {
      this.sourceFieldName = sourceFieldName;
    }

    public DmScdUpdatePolicy getScdUpdatePolicy() {
      return scdUpdatePolicy;
    }

    public void setScdUpdatePolicy(DmScdUpdatePolicy scdUpdatePolicy) {
      this.scdUpdatePolicy = scdUpdatePolicy;
    }

    public String getPreviousFieldName() {
      return previousFieldName;
    }

    public void setPreviousFieldName(String previousFieldName) {
      this.previousFieldName = previousFieldName;
    }

    public boolean isAdditive() {
      return additive;
    }

    public void setAdditive(boolean additive) {
      this.additive = additive;
    }

    public DmFieldDocumentation getDocumentation() {
      return documentation;
    }
  }

  private static final Class<?> PKG = HopGuiDmFieldEditorDialog.class;

  private final Shell parent;
  private final Model input;
  private Shell shell;
  private boolean ok;

  private Text wFieldName;
  private Text wSourceFieldName;
  private Combo wScdPolicy;
  private Text wPreviousFieldName;
  private Button wAdditive;
  private Button wRequired;
  private Text wDescription;
  private MarkdownEditPreviewComposite wNotes;
  private MarkdownEditPreviewComposite wRequirements;

  public HopGuiDmFieldEditorDialog(Shell parent, Model input) {
    this.parent = parent;
    this.input = input == null ? new Model() : input;
  }

  public Model open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Title"));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.DM_FIELD);
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wOk, -2 * margin).result());

    addFieldTab(wTabFolder, margin);
    addMarkdownTab(
        wTabFolder,
        margin,
        BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Tab.Notes.Label"),
        BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Tab.Notes.ToolTip"),
        true);
    addMarkdownTab(
        wTabFolder,
        margin,
        BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Tab.Requirements.Label"),
        BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Tab.Requirements.ToolTip"),
        false);

    wTabFolder.setSelection(0);
    getData();
    BaseTransformDialog.setSize(shell, 640, 560);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok ? input : null;
  }

  private void addFieldTab(CTabFolder folder, int margin) {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            folder,
            BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Tab.Field.Label"),
            BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Tab.Field.ToolTip"));
    int middle = PropsUi.getInstance().getMiddlePct();
    Kind kind = input.getKind();

    Label wlFieldName = new Label(comp, SWT.RIGHT);
    wlFieldName.setText(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.FieldName.Label"));
    PropsUi.setLook(wlFieldName);
    wlFieldName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wFieldName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFieldName);
    wFieldName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    org.eclipse.swt.widgets.Control last = wFieldName;
    if (kind == Kind.ATTRIBUTE) {
      Label wlSource = new Label(comp, SWT.RIGHT);
      wlSource.setText(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.SourceField.Label"));
      PropsUi.setLook(wlSource);
      wlSource.setLayoutData(
          new FormDataBuilder().left().top(last, margin).right(middle, -margin).result());
      wSourceFieldName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
      PropsUi.setLook(wSourceFieldName);
      wSourceFieldName.setLayoutData(
          new FormDataBuilder().left(middle, 0).top(last, margin).right().result());
      last = wSourceFieldName;

      Label wlScd = new Label(comp, SWT.RIGHT);
      wlScd.setText(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.ScdPolicy.Label"));
      PropsUi.setLook(wlScd);
      wlScd.setLayoutData(
          new FormDataBuilder().left().top(last, margin).right(middle, -margin).result());
      wScdPolicy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
      PropsUi.setLook(wScdPolicy);
      EnumDialogSupport.populateCombo(wScdPolicy, DmScdUpdatePolicy.class);
      wScdPolicy.setLayoutData(
          new FormDataBuilder().left(middle, 0).top(last, margin).right().result());
      last = wScdPolicy;

      Label wlPrev = new Label(comp, SWT.RIGHT);
      wlPrev.setText(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.PreviousField.Label"));
      PropsUi.setLook(wlPrev);
      wlPrev.setLayoutData(
          new FormDataBuilder().left().top(last, margin).right(middle, -margin).result());
      wPreviousFieldName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
      PropsUi.setLook(wPreviousFieldName);
      wPreviousFieldName.setLayoutData(
          new FormDataBuilder().left(middle, 0).top(last, margin).right().result());
      last = wPreviousFieldName;
    }

    if (kind == Kind.MEASURE) {
      wAdditive = new Button(comp, SWT.CHECK);
      wAdditive.setText(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Additive.Label"));
      PropsUi.setLook(wAdditive);
      wAdditive.setLayoutData(
          new FormDataBuilder().left(middle, 0).top(last, margin).right().result());
      last = wAdditive;
    }

    wRequired = new Button(comp, SWT.CHECK);
    wRequired.setText(BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Required.Label"));
    wRequired.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Required.ToolTip"));
    PropsUi.setLook(wRequired);
    wRequired.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(last, margin).right().result());
    last = wRequired;

    Label wlDescription = new Label(comp, SWT.RIGHT);
    wlDescription.setText(
        BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Description.Label"));
    wlDescription.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Description.ToolTip"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(last, margin).right(middle, -margin).result());
    wDescription = new Text(comp, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(last, margin).right().bottom().result());
  }

  private void addMarkdownTab(
      CTabFolder folder, int margin, String label, String toolTip, boolean notes) {
    CTabItem item = new CTabItem(folder, SWT.NONE);
    item.setText(label);
    item.setToolTipText(toolTip);
    Composite comp = new Composite(folder, SWT.NONE);
    PropsUi.setLook(comp);
    FormLayout layout = new FormLayout();
    layout.marginWidth = margin;
    layout.marginHeight = margin;
    comp.setLayout(layout);
    MarkdownEditPreviewComposite editor =
        new MarkdownEditPreviewComposite(
            comp,
            SWT.NONE,
            BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Markdown.Edit"),
            BaseMessages.getString(PKG, "HopGuiDmFieldEditorDialog.Markdown.Preview"));
    editor.setLayoutData(new FormDataBuilder().left().top().right().bottom().result());
    item.setControl(comp);
    if (notes) {
      wNotes = editor;
    } else {
      wRequirements = editor;
    }
  }

  private void getData() {
    wFieldName.setText(Const.NVL(input.getFieldName(), ""));
    if (wSourceFieldName != null) {
      wSourceFieldName.setText(Const.NVL(input.getSourceFieldName(), ""));
    }
    if (wScdPolicy != null) {
      EnumDialogSupport.selectCombo(
          wScdPolicy,
          input.getScdUpdatePolicy() != null
              ? input.getScdUpdatePolicy()
              : DmScdUpdatePolicy.TYPE1);
    }
    if (wPreviousFieldName != null) {
      wPreviousFieldName.setText(Const.NVL(input.getPreviousFieldName(), ""));
    }
    if (wAdditive != null) {
      wAdditive.setSelection(input.isAdditive());
    }
    DmFieldDocumentation docs = input.getDocumentation();
    wRequired.setSelection(docs.isRequired());
    wDescription.setText(Const.NVL(docs.getDescription(), ""));
    wNotes.setText(Const.NVL(docs.getNotes(), ""));
    wRequirements.setText(Const.NVL(docs.getRequirements(), ""));
  }

  private void ok() {
    if (Utils.isEmpty(wFieldName.getText())) {
      return;
    }
    input.setFieldName(wFieldName.getText());
    if (wSourceFieldName != null) {
      input.setSourceFieldName(wSourceFieldName.getText());
    }
    if (wScdPolicy != null) {
      input.setScdUpdatePolicy(
          EnumDialogSupport.readCombo(
              wScdPolicy, DmScdUpdatePolicy.class, DmScdUpdatePolicy.TYPE1));
    }
    if (wPreviousFieldName != null) {
      input.setPreviousFieldName(wPreviousFieldName.getText());
    }
    if (wAdditive != null) {
      input.setAdditive(wAdditive.getSelection());
    }
    DmFieldDocumentation docs = input.getDocumentation();
    docs.setRequiredFlag(wRequired.getSelection());
    docs.setDescription(wDescription.getText());
    docs.setNotes(wNotes.getText());
    docs.setRequirements(wRequirements.getText());
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
    shell.dispose();
  }
}
