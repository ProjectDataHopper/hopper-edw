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
package org.apache.hop.datavault.transform.junkdimension;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.Props;
import org.apache.hop.core.SqlStatement;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.metadata.dimensional.DmJunkHashCodeStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmJunkSurrogateKeyStrategy;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.combinationlookup.CFields;
import org.apache.hop.pipeline.transforms.combinationlookup.KeyField;
import org.apache.hop.pipeline.transforms.combinationlookup.ReturnFields;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.DatabaseExplorerDialog;
import org.apache.hop.ui.core.database.dialog.SqlEditor;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class JunkDimensionDialog extends BaseTransformDialog {
  private static final Class<?> PKG = JunkDimensionMeta.class;

  private MetaSelectionLine<DatabaseMeta> wConnection;
  private TextVar wSchema;
  private TextVar wTable;
  private Text wCommit;
  private Text wCachesize;
  private Button wPreloadCache;
  private Button wReplace;
  private CCombo wLastUpdateField;
  private CCombo wTechnicalKeyField;
  private CCombo wTechnicalKeyOutputField;

  private TableView wKey;
  private ColumnInfo[] ciKey;

  private Combo wSurrogateKeyStrategy;
  private Text wSurrogateKeySourceField;
  private Combo wHashCodeStrategy;
  private Button wUseSurrogateKeyAsHashCodeField;
  private Text wHashField;

  private Label wlAutoinc;
  private Button wAutoinc;
  private Label wlTableMax;
  private Button wTableMax;
  private Label wlSeqButton;
  private Button wSeqButton;
  private Text wSeq;
  private Composite gTechGroup;
  private Label wlTechGroup;

  private CTabFolder wTabFolder;
  private ModifyListener lsMod;
  private ModifyListener lsTableMod;

  private final JunkDimensionMeta input;
  private DatabaseMeta databaseMeta;
  private final List<String> inputFields = new ArrayList<>();
  private final List<ColumnInfo> tableFieldColumns = new ArrayList<>();
  private String[] tableFieldNames = new String[0];

  public JunkDimensionDialog(
      Shell parent,
      IVariables variables,
      JunkDimensionMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "JunkDimensionDialog.Shell.Title"));

    lsMod = e -> input.setChanged();
    lsTableMod =
        e -> {
          input.setChanged();
          setTableFieldCombo();
        };
    SelectionListener lsSelection =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            input.setChanged();
            setTableFieldCombo();
          }
        };

    backupChanged = input.hasChanged();
    databaseMeta = resolveDatabase(input.getConnectionName());

    buildButtonBar().ok(e -> ok()).get(e -> get()).sql(e -> create()).cancel(e -> cancel()).build();

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wTransformName, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(wOk, -2 * margin);
    wTabFolder.setLayoutData(fdTabFolder);

    buildGeneralTab(lsSelection);
    buildKeysTab();
    buildHashingTab();
    wTabFolder.setSelection(0);

    loadInputFieldsAsync();
    getData();
    setTableFieldCombo();
    refreshHashingState();

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void buildGeneralTab(SelectionListener lsSelection) {
    CTabItem tab = new CTabItem(wTabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Tab.General.Label"));

    Composite comp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();

    wConnection = addConnectionLine(comp, null, databaseMeta, lsMod);
    wConnection.addSelectionListener(lsSelection);
    wConnection.addModifyListener(
        e -> {
          databaseMeta = resolveDatabase(wConnection.getText());
          setAutoincUse();
          setSequence();
          input.setChanged();
        });
    FormData fdConnection = (FormData) wConnection.getLayoutData();
    fdConnection.top = new FormAttachment(0, margin);

    Label wlSchema = new Label(comp, SWT.RIGHT);
    wlSchema.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.TargetSchema.Label"));
    PropsUi.setLook(wlSchema);
    FormData fdlSchema = new FormData();
    fdlSchema.left = new FormAttachment(0, 0);
    fdlSchema.right = new FormAttachment(middle, -margin);
    fdlSchema.top = new FormAttachment(wConnection, margin);
    wlSchema.setLayoutData(fdlSchema);

    Button wbSchema = new Button(comp, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbSchema);
    wbSchema.setText(BaseMessages.getString(PKG, "System.Button.Browse"));
    FormData fdbSchema = new FormData();
    fdbSchema.top = new FormAttachment(wConnection, margin);
    fdbSchema.right = new FormAttachment(100, 0);
    wbSchema.setLayoutData(fdbSchema);

    wSchema = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSchema);
    wSchema.addModifyListener(lsTableMod);
    FormData fdSchema = new FormData();
    fdSchema.left = new FormAttachment(middle, 0);
    fdSchema.top = new FormAttachment(wConnection, margin);
    fdSchema.right = new FormAttachment(wbSchema, -margin);
    wSchema.setLayoutData(fdSchema);

    Label wlTable = new Label(comp, SWT.RIGHT);
    wlTable.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Target.Label"));
    PropsUi.setLook(wlTable);
    FormData fdlTable = new FormData();
    fdlTable.left = new FormAttachment(0, 0);
    fdlTable.right = new FormAttachment(middle, -margin);
    fdlTable.top = new FormAttachment(wbSchema, margin);
    wlTable.setLayoutData(fdlTable);

    Button wbTable = new Button(comp, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbTable);
    wbTable.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.BrowseTable.Button"));
    FormData fdbTable = new FormData();
    fdbTable.right = new FormAttachment(100, 0);
    fdbTable.top = new FormAttachment(wbSchema, margin);
    wbTable.setLayoutData(fdbTable);

    wTable = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTable);
    wTable.addModifyListener(lsTableMod);
    FormData fdTable = new FormData();
    fdTable.left = new FormAttachment(middle, 0);
    fdTable.top = new FormAttachment(wbSchema, margin);
    fdTable.right = new FormAttachment(wbTable, -margin);
    wTable.setLayoutData(fdTable);

    Label wlCommit = new Label(comp, SWT.RIGHT);
    wlCommit.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Commitsize.Label"));
    PropsUi.setLook(wlCommit);
    FormData fdlCommit = new FormData();
    fdlCommit.left = new FormAttachment(0, 0);
    fdlCommit.right = new FormAttachment(middle, -margin);
    fdlCommit.top = new FormAttachment(wTable, margin);
    wlCommit.setLayoutData(fdlCommit);
    wCommit = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wCommit);
    wCommit.addModifyListener(lsMod);
    FormData fdCommit = new FormData();
    fdCommit.top = new FormAttachment(wTable, margin);
    fdCommit.left = new FormAttachment(middle, 0);
    fdCommit.right = new FormAttachment(middle + (100 - middle) / 3, -margin);
    wCommit.setLayoutData(fdCommit);

    Label wlCachesize = new Label(comp, SWT.RIGHT);
    wlCachesize.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Cachesize.Label"));
    PropsUi.setLook(wlCachesize);
    FormData fdlCachesize = new FormData();
    fdlCachesize.top = new FormAttachment(wTable, margin);
    fdlCachesize.left = new FormAttachment(wCommit, margin);
    fdlCachesize.right = new FormAttachment(middle + 2 * (100 - middle) / 3, -margin);
    wlCachesize.setLayoutData(fdlCachesize);
    wCachesize = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wCachesize);
    wCachesize.addModifyListener(lsMod);
    FormData fdCachesize = new FormData();
    fdCachesize.top = new FormAttachment(wTable, margin);
    fdCachesize.left = new FormAttachment(wlCachesize, margin);
    fdCachesize.right = new FormAttachment(100, 0);
    wCachesize.setLayoutData(fdCachesize);
    wCachesize.setToolTipText(BaseMessages.getString(PKG, "JunkDimensionDialog.Cachesize.ToolTip"));

    wPreloadCache = new Button(comp, SWT.CHECK);
    wPreloadCache.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.PreloadCache.Label"));
    PropsUi.setLook(wPreloadCache);
    FormData fdPreloadCache = new FormData();
    fdPreloadCache.top = new FormAttachment(wCachesize, margin);
    fdPreloadCache.left = new FormAttachment(middle, 0);
    fdPreloadCache.right = new FormAttachment(100, 0);
    wPreloadCache.setLayoutData(fdPreloadCache);
    wPreloadCache.addListener(SWT.Selection, e -> input.setChanged());

    wReplace = new Button(comp, SWT.CHECK);
    wReplace.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Replace.Label"));
    PropsUi.setLook(wReplace);
    FormData fdReplace = new FormData();
    fdReplace.top = new FormAttachment(wPreloadCache, margin);
    fdReplace.left = new FormAttachment(middle, 0);
    fdReplace.right = new FormAttachment(100, 0);
    wReplace.setLayoutData(fdReplace);
    wReplace.addListener(SWT.Selection, e -> input.setChanged());

    Label wlLastUpdateField = new Label(comp, SWT.RIGHT);
    wlLastUpdateField.setText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.LastUpdateField.Label"));
    PropsUi.setLook(wlLastUpdateField);
    FormData fdlLastUpdateField = new FormData();
    fdlLastUpdateField.left = new FormAttachment(0, 0);
    fdlLastUpdateField.right = new FormAttachment(middle, -margin);
    fdlLastUpdateField.top = new FormAttachment(wReplace, margin);
    wlLastUpdateField.setLayoutData(fdlLastUpdateField);
    wLastUpdateField = new CCombo(comp, SWT.BORDER);
    PropsUi.setLook(wLastUpdateField);
    wLastUpdateField.addModifyListener(lsMod);
    FormData fdLastUpdateField = new FormData();
    fdLastUpdateField.left = new FormAttachment(middle, 0);
    fdLastUpdateField.right = new FormAttachment(100, 0);
    fdLastUpdateField.top = new FormAttachment(wReplace, margin);
    wLastUpdateField.setLayoutData(fdLastUpdateField);

    Label wlTechnicalKeyField = new Label(comp, SWT.RIGHT);
    wlTechnicalKeyField.setText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.TechnicalKey.Label"));
    PropsUi.setLook(wlTechnicalKeyField);
    FormData fdlTechnicalKeyField = new FormData();
    fdlTechnicalKeyField.left = new FormAttachment(0, 0);
    fdlTechnicalKeyField.right = new FormAttachment(middle, -margin);
    fdlTechnicalKeyField.top = new FormAttachment(wLastUpdateField, margin);
    wlTechnicalKeyField.setLayoutData(fdlTechnicalKeyField);
    wTechnicalKeyField = new CCombo(comp, SWT.BORDER);
    PropsUi.setLook(wTechnicalKeyField);
    wTechnicalKeyField.addModifyListener(lsMod);
    wTechnicalKeyField.addModifyListener(
        e -> {
          if (wUseSurrogateKeyAsHashCodeField != null
              && wUseSurrogateKeyAsHashCodeField.getSelection()) {
            wHashField.setText(wTechnicalKeyField.getText());
          }
        });
    FormData fdTechnicalKeyField = new FormData();
    fdTechnicalKeyField.left = new FormAttachment(middle, 0);
    fdTechnicalKeyField.right = new FormAttachment(100, 0);
    fdTechnicalKeyField.top = new FormAttachment(wLastUpdateField, margin);
    wTechnicalKeyField.setLayoutData(fdTechnicalKeyField);

    Label wlTechnicalKeyOutputField = new Label(comp, SWT.RIGHT);
    wlTechnicalKeyOutputField.setText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.TechnicalKeyOutput.Label"));
    PropsUi.setLook(wlTechnicalKeyOutputField);
    FormData fdlTechnicalKeyOutputField = new FormData();
    fdlTechnicalKeyOutputField.left = new FormAttachment(0, 0);
    fdlTechnicalKeyOutputField.right = new FormAttachment(middle, -margin);
    fdlTechnicalKeyOutputField.top = new FormAttachment(wTechnicalKeyField, margin);
    wlTechnicalKeyOutputField.setLayoutData(fdlTechnicalKeyOutputField);
    wTechnicalKeyOutputField = new CCombo(comp, SWT.BORDER);
    PropsUi.setLook(wTechnicalKeyOutputField);
    wTechnicalKeyOutputField.addModifyListener(lsMod);
    FormData fdTechnicalKeyOutputField = new FormData();
    fdTechnicalKeyOutputField.left = new FormAttachment(middle, 0);
    fdTechnicalKeyOutputField.right = new FormAttachment(100, 0);
    fdTechnicalKeyOutputField.top = new FormAttachment(wTechnicalKeyField, margin);
    wTechnicalKeyOutputField.setLayoutData(fdTechnicalKeyOutputField);

    wbSchema.addListener(SWT.Selection, e -> getSchemaNames());
    wbTable.addListener(SWT.Selection, e -> getTableName());
  }

  private void buildKeysTab() {
    CTabItem tab = new CTabItem(wTabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Tab.Keys.Label"));

    Composite comp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlKey = new Label(comp, SWT.NONE);
    wlKey.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Keyfields.Label"));
    PropsUi.setLook(wlKey);
    FormData fdlKey = new FormData();
    fdlKey.left = new FormAttachment(0, 0);
    fdlKey.top = new FormAttachment(0, margin);
    fdlKey.right = new FormAttachment(100, 0);
    wlKey.setLayoutData(fdlKey);

    int nrKeyRows = Math.max(1, input.getFields().getKeyFields().size());
    ciKey = new ColumnInfo[2];
    ciKey[0] =
        new ColumnInfo(
            BaseMessages.getString(PKG, "JunkDimensionDialog.ColumnInfo.DimensionField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {},
            false);
    ciKey[1] =
        new ColumnInfo(
            BaseMessages.getString(PKG, "JunkDimensionDialog.ColumnInfo.FieldInStream"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {},
            false);
    tableFieldColumns.add(ciKey[0]);

    wKey =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL,
            ciKey,
            nrKeyRows,
            lsMod,
            props);

    FormData fdKey = new FormData();
    fdKey.left = new FormAttachment(0, 0);
    fdKey.top = new FormAttachment(wlKey, margin);
    fdKey.right = new FormAttachment(100, 0);
    fdKey.bottom = new FormAttachment(100, 0);
    wKey.setLayoutData(fdKey);
  }

  private void buildHashingTab() {
    CTabItem tab = new CTabItem(wTabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Tab.Hashing.Label"));

    Composite comp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();

    Label wlSurrogateKeyStrategy = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyStrategy.setText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.SurrogateKeyStrategy.Label"));
    PropsUi.setLook(wlSurrogateKeyStrategy);
    FormData fdlSurrogateKeyStrategy = new FormData();
    fdlSurrogateKeyStrategy.left = new FormAttachment(0, 0);
    fdlSurrogateKeyStrategy.right = new FormAttachment(middle, -margin);
    fdlSurrogateKeyStrategy.top = new FormAttachment(0, margin);
    wlSurrogateKeyStrategy.setLayoutData(fdlSurrogateKeyStrategy);

    wSurrogateKeyStrategy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyStrategy);
    EnumDialogSupport.populateCombo(wSurrogateKeyStrategy, DmJunkSurrogateKeyStrategy.class);
    wSurrogateKeyStrategy.addModifyListener(lsMod);
    wSurrogateKeyStrategy.addListener(SWT.Selection, e -> refreshHashingState());
    FormData fdSurrogateKeyStrategy = new FormData();
    fdSurrogateKeyStrategy.left = new FormAttachment(middle, 0);
    fdSurrogateKeyStrategy.top = new FormAttachment(0, margin);
    fdSurrogateKeyStrategy.right = new FormAttachment(100, 0);
    wSurrogateKeyStrategy.setLayoutData(fdSurrogateKeyStrategy);

    Label wlSurrogateKeySourceField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeySourceField.setText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.SurrogateKeySourceField.Label"));
    PropsUi.setLook(wlSurrogateKeySourceField);
    FormData fdlSurrogateKeySourceField = new FormData();
    fdlSurrogateKeySourceField.left = new FormAttachment(0, 0);
    fdlSurrogateKeySourceField.right = new FormAttachment(middle, -margin);
    fdlSurrogateKeySourceField.top = new FormAttachment(wSurrogateKeyStrategy, margin);
    wlSurrogateKeySourceField.setLayoutData(fdlSurrogateKeySourceField);

    wSurrogateKeySourceField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeySourceField);
    wSurrogateKeySourceField.addModifyListener(lsMod);
    FormData fdSurrogateKeySourceField = new FormData();
    fdSurrogateKeySourceField.left = new FormAttachment(middle, 0);
    fdSurrogateKeySourceField.top = new FormAttachment(wSurrogateKeyStrategy, margin);
    fdSurrogateKeySourceField.right = new FormAttachment(100, 0);
    wSurrogateKeySourceField.setLayoutData(fdSurrogateKeySourceField);

    Label wlHashCodeStrategy = new Label(comp, SWT.RIGHT);
    wlHashCodeStrategy.setText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.HashCodeStrategy.Label"));
    PropsUi.setLook(wlHashCodeStrategy);
    FormData fdlHashCodeStrategy = new FormData();
    fdlHashCodeStrategy.left = new FormAttachment(0, 0);
    fdlHashCodeStrategy.right = new FormAttachment(middle, -margin);
    fdlHashCodeStrategy.top = new FormAttachment(wSurrogateKeySourceField, margin);
    wlHashCodeStrategy.setLayoutData(fdlHashCodeStrategy);

    wHashCodeStrategy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wHashCodeStrategy);
    EnumDialogSupport.populateCombo(wHashCodeStrategy, DmJunkHashCodeStrategy.class);
    wHashCodeStrategy.addModifyListener(lsMod);
    wHashCodeStrategy.addListener(SWT.Selection, e -> refreshHashingState());
    FormData fdHashCodeStrategy = new FormData();
    fdHashCodeStrategy.left = new FormAttachment(middle, 0);
    fdHashCodeStrategy.top = new FormAttachment(wSurrogateKeySourceField, margin);
    fdHashCodeStrategy.right = new FormAttachment(100, 0);
    wHashCodeStrategy.setLayoutData(fdHashCodeStrategy);

    wUseSurrogateKeyAsHashCodeField = new Button(comp, SWT.CHECK);
    wUseSurrogateKeyAsHashCodeField.setText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.UseSurrogateKeyAsHashCodeField.Label"));
    wUseSurrogateKeyAsHashCodeField.setToolTipText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.UseSurrogateKeyAsHashCodeField.ToolTip"));
    PropsUi.setLook(wUseSurrogateKeyAsHashCodeField);
    FormData fdUseSurrogateKeyAsHashCodeField = new FormData();
    fdUseSurrogateKeyAsHashCodeField.left = new FormAttachment(middle, 0);
    fdUseSurrogateKeyAsHashCodeField.top = new FormAttachment(wHashCodeStrategy, margin);
    fdUseSurrogateKeyAsHashCodeField.right = new FormAttachment(100, 0);
    wUseSurrogateKeyAsHashCodeField.setLayoutData(fdUseSurrogateKeyAsHashCodeField);
    wUseSurrogateKeyAsHashCodeField.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            refreshHashingState();
            input.setChanged();
          }
        });

    Label wlHashField = new Label(comp, SWT.RIGHT);
    wlHashField.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Hashfield.Label"));
    PropsUi.setLook(wlHashField);
    FormData fdlHashField = new FormData();
    fdlHashField.left = new FormAttachment(0, 0);
    fdlHashField.right = new FormAttachment(middle, -margin);
    fdlHashField.top = new FormAttachment(wUseSurrogateKeyAsHashCodeField, margin);
    wlHashField.setLayoutData(fdlHashField);

    wHashField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHashField);
    wHashField.addModifyListener(lsMod);
    FormData fdHashField = new FormData();
    fdHashField.left = new FormAttachment(middle, 0);
    fdHashField.top = new FormAttachment(wUseSurrogateKeyAsHashCodeField, margin);
    fdHashField.right = new FormAttachment(100, 0);
    wHashField.setLayoutData(fdHashField);

    wlTechGroup = new Label(comp, SWT.RIGHT);
    wlTechGroup.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.TechGroup.Label"));
    PropsUi.setLook(wlTechGroup);
    FormData fdlTechGroup = new FormData();
    fdlTechGroup.left = new FormAttachment(0, 0);
    fdlTechGroup.right = new FormAttachment(middle, -margin);
    fdlTechGroup.top = new FormAttachment(wHashField, margin);
    wlTechGroup.setLayoutData(fdlTechGroup);

    gTechGroup = new Composite(comp, SWT.NONE);
    GridLayout gridLayout = new GridLayout(3, false);
    gTechGroup.setLayout(gridLayout);
    PropsUi.setLook(gTechGroup);
    FormData fdTechGroup = new FormData();
    fdTechGroup.left = new FormAttachment(middle, 0);
    fdTechGroup.top = new FormAttachment(wHashField, margin);
    fdTechGroup.right = new FormAttachment(100, 0);
    gTechGroup.setLayoutData(fdTechGroup);

    wTableMax = new Button(gTechGroup, SWT.RADIO);
    PropsUi.setLook(wTableMax);
    wTableMax.setSelection(false);
    wTableMax.setLayoutData(new GridData());
    wTableMax.setToolTipText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.TableMaximum.Tooltip", Const.CR));
    wlTableMax = new Label(gTechGroup, SWT.LEFT);
    wlTableMax.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.TableMaximum.Label"));
    PropsUi.setLook(wlTableMax);
    GridData gdlTableMax = new GridData(GridData.FILL_BOTH);
    gdlTableMax.horizontalSpan = 2;
    wlTableMax.setLayoutData(gdlTableMax);

    wSeqButton = new Button(gTechGroup, SWT.RADIO);
    PropsUi.setLook(wSeqButton);
    wSeqButton.setSelection(false);
    wSeqButton.setLayoutData(new GridData());
    wSeqButton.setToolTipText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.Sequence.Tooltip", Const.CR));
    wlSeqButton = new Label(gTechGroup, SWT.LEFT);
    wlSeqButton.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Sequence.Label"));
    PropsUi.setLook(wlSeqButton);
    wlSeqButton.setLayoutData(new GridData());

    wSeq = new Text(gTechGroup, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSeq);
    wSeq.addModifyListener(lsMod);
    wSeq.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    wSeq.addFocusListener(
        new FocusListener() {
          @Override
          public void focusGained(FocusEvent arg0) {
            input
                .getFields()
                .getReturnFields()
                .setTechKeyCreation(JunkDimensionMeta.CREATION_METHOD_SEQUENCE);
            wSeqButton.setSelection(true);
            wAutoinc.setSelection(false);
            wTableMax.setSelection(false);
          }

          @Override
          public void focusLost(FocusEvent arg0) {
            // no-op
          }
        });

    wAutoinc = new Button(gTechGroup, SWT.RADIO);
    PropsUi.setLook(wAutoinc);
    wAutoinc.setSelection(false);
    wAutoinc.setLayoutData(new GridData());
    wAutoinc.setToolTipText(
        BaseMessages.getString(PKG, "JunkDimensionDialog.AutoincButton.Tooltip", Const.CR));
    wlAutoinc = new Label(gTechGroup, SWT.LEFT);
    wlAutoinc.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.Autoincrement.Label"));
    PropsUi.setLook(wlAutoinc);
    wlAutoinc.setLayoutData(new GridData());

    setTableMax();
    setSequence();
    setAutoincUse();
  }

  private void refreshHashingState() {
    if (wSurrogateKeyStrategy == null || wSurrogateKeyStrategy.isDisposed()) {
      return;
    }
    DmJunkSurrogateKeyStrategy surrogateStrategy =
        EnumDialogSupport.readCombo(
            wSurrogateKeyStrategy,
            DmJunkSurrogateKeyStrategy.class,
            DmJunkSurrogateKeyStrategy.AUTO_INCREMENT);
    boolean autoIncrement = surrogateStrategy == DmJunkSurrogateKeyStrategy.AUTO_INCREMENT;
    boolean sourceStrategy = surrogateStrategy == DmJunkSurrogateKeyStrategy.USE_SOURCE_FIELD;

    if (wSurrogateKeySourceField != null && !wSurrogateKeySourceField.isDisposed()) {
      wSurrogateKeySourceField.setEnabled(sourceStrategy);
    }

    if (gTechGroup != null && !gTechGroup.isDisposed()) {
      gTechGroup.setVisible(autoIncrement);
      if (wlTechGroup != null && !wlTechGroup.isDisposed()) {
        wlTechGroup.setVisible(autoIncrement);
      }
      gTechGroup.getParent().layout();
    }

    DmJunkHashCodeStrategy hashStrategy =
        EnumDialogSupport.readCombo(
            wHashCodeStrategy, DmJunkHashCodeStrategy.class, DmJunkHashCodeStrategy.INTEGER_LEGACY);
    boolean usesHashColumn = hashStrategy.usesHashColumn();

    if (wUseSurrogateKeyAsHashCodeField != null && !wUseSurrogateKeyAsHashCodeField.isDisposed()) {
      wUseSurrogateKeyAsHashCodeField.setEnabled(usesHashColumn);
      if (!usesHashColumn) {
        wUseSurrogateKeyAsHashCodeField.setSelection(false);
      }
    }

    boolean useSurrogateAsHash =
        usesHashColumn
            && wUseSurrogateKeyAsHashCodeField != null
            && wUseSurrogateKeyAsHashCodeField.getSelection();

    Label wlHashField = null;
    for (Control child : wHashField.getParent().getChildren()) {
      if (child instanceof Label label
          && BaseMessages.getString(PKG, "JunkDimensionDialog.Hashfield.Label")
              .equals(label.getText())) {
        wlHashField = label;
        break;
      }
    }

    if (wHashField != null && !wHashField.isDisposed()) {
      wHashField.setEnabled(usesHashColumn && !useSurrogateAsHash);
      if (useSurrogateAsHash && wTechnicalKeyField != null) {
        wHashField.setText(Const.NVL(wTechnicalKeyField.getText(), ""));
      }
    }
    if (wlHashField != null && !wlHashField.isDisposed()) {
      wlHashField.setEnabled(usesHashColumn && !useSurrogateAsHash);
    }
  }

  private void loadInputFieldsAsync() {
    final Runnable runnable =
        () -> {
          TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
          if (transformMeta != null) {
            try {
              IRowMeta row = pipelineMeta.getPrevTransformFields(variables, transformMeta);
              for (int i = 0; i < row.size(); i++) {
                inputFields.add(row.getValueMeta(i).getName());
              }
              shell.getDisplay().asyncExec(this::setComboBoxes);
            } catch (HopException e) {
              logError(BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"));
            }
          }
        };
    new Thread(runnable).start();
  }

  private void setComboBoxes() {
    String[] fieldNames = ConstUi.sortFieldNames(inputFields);
    if (ciKey != null && ciKey.length > 1) {
      ciKey[1].setComboValues(fieldNames);
    }
    if (wTechnicalKeyOutputField != null && !wTechnicalKeyOutputField.isDisposed()) {
      wTechnicalKeyOutputField.setItems(fieldNames);
    }
  }

  private void setTableFieldCombo() {
    if (wTable == null
        || wTable.isDisposed()
        || wConnection == null
        || wConnection.isDisposed()
        || wSchema == null
        || wSchema.isDisposed()) {
      return;
    }
    shell.getDisplay().asyncExec(this::getTableFieldComboValues);
  }

  private void getTableFieldComboValues() {
    final String tableName = wTable.getText();
    if (StringUtils.isEmpty(tableName)) {
      return;
    }
    final String connectionName = wConnection.getText();
    final String schemaName = wSchema.getText();

    for (ColumnInfo colInfo : tableFieldColumns) {
      colInfo.setComboValues(new String[] {});
    }
    tableFieldNames = new String[0];

    DatabaseMeta dbMeta = resolveDatabase(connectionName);
    if (dbMeta == null) {
      return;
    }
    try (Database database = new Database(loggingObject, variables, dbMeta)) {
      database.connect();
      String schemaTable = dbMeta.getQuotedSchemaTableCombination(variables, schemaName, tableName);
      IRowMeta rowMeta = database.getTableFields(schemaTable);
      if (rowMeta != null) {
        String[] fieldNames = rowMeta.getFieldNames();
        if (fieldNames != null) {
          tableFieldNames = fieldNames;
          for (ColumnInfo colInfo : tableFieldColumns) {
            colInfo.setComboValues(fieldNames);
          }
          if (wLastUpdateField != null && !wLastUpdateField.isDisposed()) {
            wLastUpdateField.setItems(fieldNames);
          }
          if (wTechnicalKeyField != null && !wTechnicalKeyField.isDisposed()) {
            wTechnicalKeyField.setItems(fieldNames);
          }
        }
      }
    } catch (Exception e) {
      // ignore: combos stay empty
    }
  }

  public void setAutoincUse() {
    boolean enable =
        (databaseMeta == null)
            || (databaseMeta.supportsAutoinc() && databaseMeta.supportsAutoGeneratedKeys());

    wlAutoinc.setEnabled(enable);
    wAutoinc.setEnabled(enable);
    if (!enable && wAutoinc.getSelection()) {
      wAutoinc.setSelection(false);
      wSeqButton.setSelection(false);
      wTableMax.setSelection(true);
    }
  }

  public void setTableMax() {
    wlTableMax.setEnabled(true);
    wTableMax.setEnabled(true);
  }

  public void setSequence() {
    boolean seq = (databaseMeta == null) || databaseMeta.supportsSequences();
    wSeq.setEnabled(seq);
    wlSeqButton.setEnabled(seq);
    wSeqButton.setEnabled(seq);
    if (!seq && wSeqButton.getSelection()) {
      wAutoinc.setSelection(false);
      wSeqButton.setSelection(false);
      wTableMax.setSelection(true);
    }
  }

  private void getData() {
    CFields fields = input.getFields();
    List<KeyField> keyFields = fields.getKeyFields();
    ReturnFields returnFields = fields.getReturnFields();

    for (int i = 0; i < keyFields.size(); i++) {
      KeyField keyField = keyFields.get(i);
      TableItem item = wKey.table.getItem(i);
      item.setText(1, Const.NVL(keyField.getLookup(), ""));
      item.setText(2, Const.NVL(keyField.getName(), ""));
    }

    wPreloadCache.setSelection(input.isPreloadCache());
    wReplace.setSelection(input.isReplaceFields());
    wSchema.setText(Const.NVL(input.getSchemaName(), ""));
    wTable.setText(Const.NVL(input.getTableName(), ""));
    if (!Utils.isEmpty(input.getConnectionName())) {
      wConnection.setText(input.getConnectionName());
    }
    wCommit.setText("" + input.getCommitSize());
    wCachesize.setText("" + input.getCacheSize());
    wLastUpdateField.setText(Const.NVL(returnFields.getLastUpdateField(), ""));
    wTechnicalKeyField.setText(Const.NVL(returnFields.getTechnicalKeyField(), ""));
    wTechnicalKeyOutputField.setText(Const.NVL(input.resolveTechnicalKeyOutputField(), ""));
    wHashField.setText(Const.NVL(input.getHashField(), ""));

    EnumDialogSupport.selectCombo(wSurrogateKeyStrategy, input.resolveJunkSurrogateKeyStrategy());
    wSurrogateKeySourceField.setText(Const.NVL(input.getSurrogateKeySourceField(), ""));

    DmJunkHashCodeStrategy hashStrategy = JunkDimensionHashSupport.resolveStrategy(input);
    EnumDialogSupport.selectCombo(wHashCodeStrategy, hashStrategy);
    wUseSurrogateKeyAsHashCodeField.setSelection(input.isUseSurrogateKeyAsHashCodeField());

    String techKeyCreation = returnFields.getTechKeyCreation();
    if (techKeyCreation == null) {
      DatabaseMeta dbMeta = resolveDatabase(input.getConnectionName());
      if (dbMeta == null || !dbMeta.supportsAutoinc()) {
        returnFields.setUseAutoIncrement(false);
      }
      wAutoinc.setSelection(returnFields.isUseAutoIncrement());
      wSeqButton.setSelection(StringUtils.isNotEmpty(fields.getSequenceFrom()));
      if (!returnFields.isUseAutoIncrement() && StringUtils.isEmpty(fields.getSequenceFrom())) {
        wTableMax.setSelection(true);
      }
      if (dbMeta != null
          && dbMeta.supportsSequences()
          && StringUtils.isNotEmpty(fields.getSequenceFrom())) {
        wSeq.setText(fields.getSequenceFrom());
        returnFields.setUseAutoIncrement(false);
        wTableMax.setSelection(false);
      }
    } else {
      if (JunkDimensionMeta.CREATION_METHOD_AUTOINC.equals(techKeyCreation)) {
        wAutoinc.setSelection(true);
      } else if (JunkDimensionMeta.CREATION_METHOD_SEQUENCE.equals(techKeyCreation)) {
        wSeqButton.setSelection(true);
      } else {
        wTableMax.setSelection(true);
        returnFields.setTechKeyCreation(JunkDimensionMeta.CREATION_METHOD_TABLEMAX);
      }
      wSeq.setText(Const.NVL(fields.getSequenceFrom(), ""));
    }

    setAutoincUse();
    setSequence();
    setTableMax();
    wKey.optimizeTableView();
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void getInfo(JunkDimensionMeta in) {
    CFields fields = in.getFields();
    ReturnFields returnFields = fields.getReturnFields();

    fields.getKeyFields().clear();
    for (TableItem item : wKey.getNonEmptyItems()) {
      fields.getKeyFields().add(new KeyField(item.getText(2), item.getText(1)));
    }

    in.setPreloadCache(wPreloadCache.getSelection());
    in.setReplaceFields(wReplace.getSelection());
    in.setSchemaName(wSchema.getText());
    in.setTableName(wTable.getText());
    returnFields.setTechnicalKeyField(wTechnicalKeyField.getText());
    in.setTechnicalKeyOutputField(wTechnicalKeyOutputField.getText());

    DmJunkSurrogateKeyStrategy surrogateStrategy =
        EnumDialogSupport.readCombo(
            wSurrogateKeyStrategy,
            DmJunkSurrogateKeyStrategy.class,
            DmJunkSurrogateKeyStrategy.AUTO_INCREMENT);
    in.setJunkSurrogateKeyStrategy(surrogateStrategy.getCode());
    in.setSurrogateKeySourceField(wSurrogateKeySourceField.getText());

    DmJunkHashCodeStrategy hashStrategy =
        EnumDialogSupport.readCombo(
            wHashCodeStrategy, DmJunkHashCodeStrategy.class, DmJunkHashCodeStrategy.INTEGER_LEGACY);
    in.setHashCodeStrategy(hashStrategy.getCode());
    in.setUseHash(hashStrategy.usesHashColumn());
    in.setUseSurrogateKeyAsHashCodeField(wUseSurrogateKeyAsHashCodeField.getSelection());

    if (in.isUseSurrogateKeyAsHashCodeField()) {
      in.setHashField(wTechnicalKeyField.getText());
    } else {
      in.setHashField(wHashField.getText());
    }

    if (surrogateStrategy == DmJunkSurrogateKeyStrategy.AUTO_INCREMENT) {
      if (wAutoinc.getSelection()) {
        returnFields.setTechKeyCreation(JunkDimensionMeta.CREATION_METHOD_AUTOINC);
        returnFields.setUseAutoIncrement(true);
        fields.setSequenceFrom(null);
      } else if (wSeqButton.getSelection()) {
        returnFields.setTechKeyCreation(JunkDimensionMeta.CREATION_METHOD_SEQUENCE);
        returnFields.setUseAutoIncrement(false);
        fields.setSequenceFrom(wSeq.getText());
      } else {
        returnFields.setTechKeyCreation(JunkDimensionMeta.CREATION_METHOD_TABLEMAX);
        returnFields.setUseAutoIncrement(false);
        fields.setSequenceFrom(null);
      }
    } else {
      returnFields.setUseAutoIncrement(false);
      fields.setSequenceFrom(null);
      returnFields.setTechKeyCreation(JunkDimensionMeta.CREATION_METHOD_TABLEMAX);
    }

    in.setConnectionName(wConnection.getText());
    in.setCommitSize(Const.toInt(wCommit.getText(), 0));
    in.setCacheSize(Const.toInt(wCachesize.getText(), 0));
    returnFields.setLastUpdateField(wLastUpdateField.getText());
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    getInfo(input);
    transformName = wTransformName.getText();
    if (findDatabase(wConnection.getText()) == null) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "JunkDimensionDialog.NoValidConnection.DialogMessage"));
      mb.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.NoValidConnection.DialogTitle"));
      mb.open();
      return;
    }
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(backupChanged);
    dispose();
  }

  private void get() {
    wTabFolder.setSelection(1);
    try {
      IRowMeta r = pipelineMeta.getPrevTransformFields(variables, transformName);
      if (r != null && !r.isEmpty()) {
        BaseTransformDialog.getFieldsFromPrevious(
            r, wKey, 1, new int[] {1, 2}, new int[] {}, -1, -1, null);
      }
    } catch (HopException ke) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "JunkDimensionDialog.UnableToGetFieldsError.DialogTitle"),
          BaseMessages.getString(PKG, "JunkDimensionDialog.UnableToGetFieldsError.DialogMessage"),
          ke);
    }
  }

  private void create() {
    try {
      JunkDimensionMeta info = new JunkDimensionMeta();
      getInfo(info);
      String name = transformName;
      TransformMeta transformMeta =
          new TransformMeta(
              BaseMessages.getString(PKG, "JunkDimensionDialog.TransformMeta.Title"), name, info);
      IRowMeta prev = pipelineMeta.getPrevTransformFields(variables, transformName);
      SqlStatement sql =
          info.getSqlStatements(variables, pipelineMeta, transformMeta, prev, metadataProvider);
      if (!sql.hasError()) {
        if (sql.hasSql()) {
          SqlEditor sqledit =
              new SqlEditor(
                  shell,
                  SWT.NONE,
                  variables,
                  resolveDatabase(info.getConnectionName()),
                  DbCache.getInstance(),
                  sql.getSql());
          sqledit.open();
        } else {
          MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
          mb.setMessage(
              BaseMessages.getString(PKG, "JunkDimensionDialog.NoSQLNeeds.DialogMessage"));
          mb.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.NoSQLNeeds.DialogTitle"));
          mb.open();
        }
      } else {
        MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
        mb.setMessage(sql.getError());
        mb.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.SQLError.DialogTitle"));
        mb.open();
      }
    } catch (HopException ke) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "JunkDimensionDialog.UnableToCreateSQL.DialogTitle"),
          BaseMessages.getString(PKG, "JunkDimensionDialog.UnableToCreateSQL.DialogMessage"),
          ke);
    }
  }

  private void getSchemaNames() {
    DatabaseMeta dbMeta = resolveDatabase(wConnection.getText());
    if (dbMeta != null) {
      try (Database database = new Database(loggingObject, variables, dbMeta)) {
        database.connect();
        String[] schemas = database.getSchemas();
        if (schemas != null && schemas.length > 0) {
          schemas = Const.sortStrings(schemas);
          EnterSelectionDialog dialog =
              new EnterSelectionDialog(
                  shell,
                  schemas,
                  BaseMessages.getString(
                      PKG, "JunkDimensionDialog.AvailableSchemas.Title", wConnection.getText()),
                  BaseMessages.getString(
                      PKG, "JunkDimensionDialog.AvailableSchemas.Message", wConnection.getText()));
          String d = dialog.open();
          if (d != null) {
            wSchema.setText(Const.NVL(d, ""));
            setTableFieldCombo();
          }
        } else {
          MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
          mb.setMessage(BaseMessages.getString(PKG, "JunkDimensionDialog.NoSchema.Error"));
          mb.setText(BaseMessages.getString(PKG, "JunkDimensionDialog.GetSchemas.Error"));
          mb.open();
        }
      } catch (Exception e) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "System.Dialog.Error.Title"),
            BaseMessages.getString(PKG, "JunkDimensionDialog.ErrorGettingSchemas"),
            e);
      }
    }
  }

  private void getTableName() {
    String connectionName = wConnection.getText();
    if (StringUtils.isEmpty(connectionName)) {
      return;
    }
    DatabaseMeta dbMeta = resolveDatabase(connectionName);
    if (dbMeta != null) {
      DatabaseExplorerDialog std =
          new DatabaseExplorerDialog(
              shell, SWT.NONE, variables, dbMeta, pipelineMeta.getDatabases());
      std.setSelectedSchemaAndTable(wSchema.getText(), wTable.getText());
      if (std.open()) {
        wSchema.setText(Const.NVL(std.getSchemaName(), ""));
        wTable.setText(Const.NVL(std.getTableName(), ""));
        setTableFieldCombo();
      }
    } else {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "JunkDimensionDialog.ConnectionError2.DialogMessage"));
      mb.setText(BaseMessages.getString(PKG, "System.Dialog.Error.Title"));
      mb.open();
    }
  }

  private DatabaseMeta resolveDatabase(String name) {
    if (Utils.isEmpty(name)) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(DatabaseMeta.class).load(name);
    } catch (Exception e) {
      return null;
    }
  }

  private DatabaseMeta findDatabase(String name) {
    DatabaseMeta database = resolveDatabase(name);
    if (database == null && !Utils.isEmpty(name)) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "System.Dialog.Error.Title"),
          "Error looking up database connection " + name,
          new HopException("Connection not found: " + name));
    }
    return database;
  }
}
