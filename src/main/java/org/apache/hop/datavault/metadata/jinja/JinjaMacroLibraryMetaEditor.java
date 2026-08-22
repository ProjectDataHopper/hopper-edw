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
package org.apache.hop.datavault.metadata.jinja;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.jinja.BvSqlJinjaSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.StyledTextComp;
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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Editor for project-level {@link JinjaMacroLibraryMeta} libraries. */
@GuiPlugin(description = "Editor for Jinja Macro Library metadata")
public class JinjaMacroLibraryMetaEditor extends MetadataEditor<JinjaMacroLibraryMeta> {

  private static final Class<?> PKG = JinjaMacroLibraryMeta.class;

  private Text wName;
  private Text wDescription;
  private Text wPackageName;
  private Button wEnabled;
  private TableView wVars;
  private TableView wMacros;
  private StyledTextComp wMacroSource;
  private Text wTestSnippet;
  private StyledTextComp wTestResult;
  private int lastMacroIndex = -1;

  public JinjaMacroLibraryMetaEditor(
      HopGui hopGui,
      MetadataManager<JinjaMacroLibraryMeta> manager,
      JinjaMacroLibraryMeta metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Name.Label"));
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
        BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Description.Label"));
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

    Label wlPackage = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlPackage);
    wlPackage.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Package.Label"));
    FormData fdlPackage = new FormData();
    fdlPackage.top = new FormAttachment(wDescription, margin);
    fdlPackage.left = new FormAttachment(0, 0);
    fdlPackage.right = new FormAttachment(middle, -margin);
    wlPackage.setLayoutData(fdlPackage);

    wPackageName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPackageName);
    FormData fdPackage = new FormData();
    fdPackage.top = new FormAttachment(wlPackage, 0, SWT.CENTER);
    fdPackage.left = new FormAttachment(middle, 0);
    fdPackage.right = new FormAttachment(70, 0);
    wPackageName.setLayoutData(fdPackage);

    wEnabled = new Button(parent, SWT.CHECK);
    PropsUi.setLook(wEnabled);
    wEnabled.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Enabled.Label"));
    FormData fdEnabled = new FormData();
    fdEnabled.top = new FormAttachment(wlPackage, 0, SWT.CENTER);
    fdEnabled.left = new FormAttachment(wPackageName, margin);
    fdEnabled.right = new FormAttachment(100, 0);
    wEnabled.setLayoutData(fdEnabled);

    CTabFolder wTabFolder = new CTabFolder(parent, SWT.BORDER);
    PropsUi.setLook(wTabFolder);
    FormData fdTabs = new FormData();
    fdTabs.top = new FormAttachment(wPackageName, margin);
    fdTabs.left = new FormAttachment(0, 0);
    fdTabs.right = new FormAttachment(100, 0);
    fdTabs.bottom = new FormAttachment(100, -margin);
    wTabFolder.setLayoutData(fdTabs);

    addMacrosTab(wTabFolder, margin, props);
    addVarsTab(wTabFolder, margin, props);
    wTabFolder.setSelection(0);

    setWidgetsContent();
    wName.addModifyListener(e -> setChanged());
    wDescription.addModifyListener(e -> setChanged());
    wPackageName.addModifyListener(e -> setChanged());
    wEnabled.addListener(SWT.Selection, e -> setChanged());
  }

  private void addVarsTab(CTabFolder tabFolder, int margin, PropsUi props) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Tab.Vars.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    FormLayout layout = new FormLayout();
    layout.marginWidth = margin;
    layout.marginHeight = margin;
    comp.setLayout(layout);
    tab.setControl(comp);

    Label wlVars = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlVars);
    wlVars.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Vars.Label"));
    FormData fdlVars = new FormData();
    fdlVars.top = new FormAttachment(0, 0);
    fdlVars.left = new FormAttachment(0, 0);
    fdlVars.right = new FormAttachment(100, 0);
    wlVars.setLayoutData(fdlVars);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Vars.Column.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Vars.Column.Value"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    wVars =
        new TableView(
            manager.getVariables(),
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            getMetadata().getVars().size(),
            false,
            e -> setChanged(),
            props);
    FormData fdVars = new FormData();
    fdVars.top = new FormAttachment(wlVars, margin);
    fdVars.left = new FormAttachment(0, 0);
    fdVars.right = new FormAttachment(100, 0);
    fdVars.bottom = new FormAttachment(100, 0);
    wVars.setLayoutData(fdVars);
  }

  private void addMacrosTab(CTabFolder tabFolder, int margin, PropsUi props) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Tab.Macros.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    FormLayout layout = new FormLayout();
    layout.marginWidth = margin;
    layout.marginHeight = margin;
    comp.setLayout(layout);
    tab.setControl(comp);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Macros.Column.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Macros.Column.Description"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Macros.Column.Origin"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    wMacros =
        new TableView(
            manager.getVariables(),
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE,
            columns,
            getMetadata().getMacros().size(),
            false,
            e -> setChanged(),
            props);
    FormData fdMacros = new FormData();
    fdMacros.top = new FormAttachment(0, 0);
    fdMacros.left = new FormAttachment(0, 0);
    fdMacros.right = new FormAttachment(100, 0);
    fdMacros.bottom = new FormAttachment(45, 0);
    wMacros.setLayoutData(fdMacros);
    wMacros.table.addListener(SWT.Selection, e -> onMacroSelected());

    Label wlSource = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlSource);
    wlSource.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.Source.Label"));
    FormData fdlSource = new FormData();
    fdlSource.top = new FormAttachment(wMacros, margin);
    fdlSource.left = new FormAttachment(0, 0);
    fdlSource.right = new FormAttachment(100, 0);
    wlSource.setLayoutData(fdlSource);

    wMacroSource =
        new StyledTextComp(
            manager.getVariables(),
            comp,
            SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    PropsUi.setLook(wMacroSource, org.apache.hop.core.Props.WIDGET_STYLE_FIXED);
    FormData fdSource = new FormData();
    fdSource.top = new FormAttachment(wlSource, margin);
    fdSource.left = new FormAttachment(0, 0);
    fdSource.right = new FormAttachment(100, 0);
    fdSource.bottom = new FormAttachment(70, 0);
    wMacroSource.setLayoutData(fdSource);
    wMacroSource.addModifyListener(e -> setChanged());

    Label wlSnippet = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlSnippet);
    wlSnippet.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.TestSnippet.Label"));
    FormData fdlSnippet = new FormData();
    fdlSnippet.top = new FormAttachment(wMacroSource, margin);
    fdlSnippet.left = new FormAttachment(0, 0);
    fdlSnippet.right = new FormAttachment(20, 0);
    wlSnippet.setLayoutData(fdlSnippet);

    wTestSnippet = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTestSnippet);
    FormData fdSnippet = new FormData();
    fdSnippet.top = new FormAttachment(wlSnippet, 0, SWT.CENTER);
    fdSnippet.left = new FormAttachment(wlSnippet, margin);
    fdSnippet.right = new FormAttachment(80, 0);
    wTestSnippet.setLayoutData(fdSnippet);

    Button wTest = new Button(comp, SWT.PUSH);
    PropsUi.setLook(wTest);
    wTest.setText(BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.TestRender.Label"));
    FormData fdTest = new FormData();
    fdTest.top = new FormAttachment(wTestSnippet, 0, SWT.CENTER);
    fdTest.left = new FormAttachment(wTestSnippet, margin);
    fdTest.right = new FormAttachment(100, 0);
    wTest.setLayoutData(fdTest);
    wTest.addListener(SWT.Selection, e -> testRender());

    wTestResult =
        new StyledTextComp(
            manager.getVariables(),
            comp,
            SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
    PropsUi.setLook(wTestResult, org.apache.hop.core.Props.WIDGET_STYLE_FIXED);
    FormData fdResult = new FormData();
    fdResult.top = new FormAttachment(wTestSnippet, margin);
    fdResult.left = new FormAttachment(0, 0);
    fdResult.right = new FormAttachment(100, 0);
    fdResult.bottom = new FormAttachment(100, 0);
    wTestResult.setLayoutData(fdResult);
  }

  private void onMacroSelected() {
    flushMacroSource();
    int index = wMacros.getSelectionIndex();
    lastMacroIndex = index;
    if (index < 0 || index >= getMetadata().getMacros().size()) {
      wMacroSource.setText("");
      return;
    }
    JinjaMacroDefinition macro = getMetadata().getMacros().get(index);
    wMacroSource.setText(Const.NVL(macro != null ? macro.getJinjaSource() : "", ""));
    if (macro != null && !Utils.isEmpty(macro.getName()) && Utils.isEmpty(wTestSnippet.getText())) {
      wTestSnippet.setText("{{ " + macro.getName() + "() }}");
    }
  }

  private void flushMacroSource() {
    if (lastMacroIndex < 0 || lastMacroIndex >= getMetadata().getMacros().size()) {
      return;
    }
    JinjaMacroDefinition macro = getMetadata().getMacros().get(lastMacroIndex);
    if (macro != null) {
      macro.setJinjaSource(wMacroSource.getText());
    }
  }

  private void testRender() {
    try {
      flushMacroSource();
      JinjaMacroLibraryMeta draft = new JinjaMacroLibraryMeta();
      getWidgetsContent(draft);
      String snippet = wTestSnippet.getText();
      if (Utils.isEmpty(snippet)) {
        snippet = "{{ test() }}";
      }
      Map<String, String> vars = new LinkedHashMap<>();
      for (JinjaMacroVar var : draft.getVars()) {
        if (var != null && !Utils.isEmpty(var.getName())) {
          vars.put(var.getName(), Const.NVL(var.getValue(), ""));
        }
      }
      String rendered =
          BvSqlJinjaSupport.renderSnippet(snippet, draft.getMacros(), vars, manager.getVariables());
      wTestResult.setText(Const.NVL(rendered, ""));
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "JinjaMacroLibraryMetaEditor.TestRender.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  @Override
  protected Button createHelpButton(Shell shell) {
    return DialogHelpSupport.createHelpButton(shell, HelpTopics.JINJA_MACRO_LIBRARY);
  }

  @Override
  public void setWidgetsContent() {
    JinjaMacroLibraryMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wPackageName.setText(Const.NVL(meta.getPackageName(), ""));
    wEnabled.setSelection(meta.isEnabled());

    wVars.clearAll(false);
    for (JinjaMacroVar var : meta.getVars()) {
      if (var == null) {
        continue;
      }
      TableItem item = new TableItem(wVars.table, SWT.NONE);
      item.setText(1, Const.NVL(var.getName(), ""));
      item.setText(2, Const.NVL(var.getValue(), ""));
    }
    wVars.optimizeTableView();

    wMacros.clearAll(false);
    for (JinjaMacroDefinition macro : meta.getMacros()) {
      if (macro == null) {
        continue;
      }
      TableItem item = new TableItem(wMacros.table, SWT.NONE);
      item.setText(1, Const.NVL(macro.getName(), ""));
      item.setText(2, Const.NVL(macro.getDescription(), ""));
      item.setText(3, Const.NVL(macro.getOriginPath(), ""));
    }
    wMacros.optimizeTableView();
    lastMacroIndex = -1;
    if (!meta.getMacros().isEmpty()) {
      wMacros.table.setSelection(0);
      onMacroSelected();
    } else {
      wMacroSource.setText("");
    }
  }

  @Override
  public void getWidgetsContent(JinjaMacroLibraryMeta meta) {
    flushMacroSource();
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    meta.setPackageName(wPackageName.getText());
    meta.setEnabled(wEnabled.getSelection());

    List<JinjaMacroVar> vars = new ArrayList<>();
    for (int i = 0; i < wVars.nrNonEmpty(); i++) {
      TableItem item = wVars.getNonEmpty(i);
      String name = item.getText(1);
      if (Utils.isEmpty(name)) {
        continue;
      }
      vars.add(new JinjaMacroVar(name, item.getText(2)));
    }
    meta.setVars(vars);

    List<JinjaMacroDefinition> existing = new ArrayList<>(getMetadata().getMacros());
    List<JinjaMacroDefinition> macros = new ArrayList<>();
    for (int i = 0; i < wMacros.nrNonEmpty(); i++) {
      TableItem item = wMacros.getNonEmpty(i);
      String name = item.getText(1);
      if (Utils.isEmpty(name) && Utils.isEmpty(item.getText(3))) {
        continue;
      }
      JinjaMacroDefinition macro =
          i < existing.size() && existing.get(i) != null
              ? existing.get(i)
              : new JinjaMacroDefinition();
      macro.setName(name);
      macro.setDescription(item.getText(2));
      macro.setOriginPath(item.getText(3));
      if (i == lastMacroIndex) {
        macro.setJinjaSource(wMacroSource.getText());
      }
      macros.add(macro);
    }
    meta.setMacros(macros);
  }
}
