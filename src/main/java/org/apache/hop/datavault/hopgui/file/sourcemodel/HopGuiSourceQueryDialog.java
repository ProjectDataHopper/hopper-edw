/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationshipLifecycleSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryValidationSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQueryGenerationSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQueryPreviewSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQueryRelationSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQuerySqlGenerator;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.SQLStyledTextComp;
import org.apache.hop.ui.core.widget.StyledTextComp;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextComposite;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to define a multi-table {@link SourceQuery} (graph-aware joins, projection, WHERE, SQL
 * preview).
 */
public class HopGuiSourceQueryDialog {

  private static final Class<?> PKG = HopGuiSourceQueryDialog.class;

  private static final int JOIN_COL_TABLE = 1;
  private static final int JOIN_COL_TYPE = 2;
  private static final int JOIN_COL_KEYS = 3;
  private static final int JOIN_COL_RELATIONSHIP = 4;
  private static final int JOIN_COL_LEFT = 5;
  private static final int JOIN_COL_RIGHT = 6;

  private static final int PROJ_COL_TABLE = 1;
  private static final int PROJ_COL_COLUMN = 2;
  private static final int PROJ_COL_ALIAS = 3;
  private static final int PROJ_COL_KEY = 4;

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final SourceModel model;
  private final SourceQuery input;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private Text wPublishedCatalogName;
  private Combo wDrivingTable;
  private Combo wGenerationMode;
  private TableView wJoins;
  private TableView wColumns;
  private TextComposite wWhere;
  private TextComposite wSqlPreview;
  private CTabItem sqlTab;
  private ColumnInfo colJoinTable;
  private ColumnInfo colJoinRelationship;
  private ColumnInfo colProjTable;
  private ColumnInfo colProjColumn;

  private boolean ok;

  public HopGuiSourceQueryDialog(
      Shell parent,
      SourceQuery query,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.input = query;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiSourceQueryDialog.Title", Const.NVL(input.getName(), "")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Validate.Button"));
    wValidate.addListener(SWT.Selection, e -> validateDefinition());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    Button wPreview = new Button(shell, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Button"));
    wPreview.addListener(SWT.Selection, e -> previewData());
    Button wGenerateSql = new Button(shell, SWT.PUSH);
    wGenerateSql.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.GenerateSql.Button"));
    wGenerateSql.addListener(SWT.Selection, e -> refreshSqlPreview());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DATABASE_TABLES_OPTIONS);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel, wPreview, wGenerateSql}, margin, null);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top()
            .right()
            .bottom(new FormAttachment(wOk, -margin))
            .result());

    addGeneralTab(wTabFolder, middle, margin);
    addJoinsTab(wTabFolder, margin);
    addColumnsTab(wTabFolder, margin);
    addSqlTab(wTabFolder, margin);

    wTabFolder.addListener(
        SWT.Selection,
        e -> {
          if (sqlTab != null && wTabFolder.getSelection() == sqlTab) {
            refreshSqlPreview();
          }
        });

    wTabFolder.setSelection(0);
    getData();
    refreshResolvedKeyLabels();
    refreshJoinTableComboValues();
    refreshProjectionTableComboValues();

    BaseTransformDialog.setSize(shell, 900, 680);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void addGeneralTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Tab.General.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlName = new Label(comp, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(comp, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());
    wDescription = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    Label wlPublished = new Label(comp, SWT.RIGHT);
    wlPublished.setText(
        BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.PublishedCatalogName.Label"));
    PropsUi.setLook(wlPublished);
    wlPublished.setLayoutData(
        new FormDataBuilder().left().top(wDescription, margin).right(middle, -margin).result());
    wPublishedCatalogName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPublishedCatalogName);
    wPublishedCatalogName.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.PublishedCatalogName.ToolTip"));
    wPublishedCatalogName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wDescription, margin).right().result());

    Label wlDriving = new Label(comp, SWT.RIGHT);
    wlDriving.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.DrivingTable.Label"));
    PropsUi.setLook(wlDriving);
    wlDriving.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wPublishedCatalogName, margin)
            .right(middle, -margin)
            .result());
    wDrivingTable = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wDrivingTable);
    wDrivingTable.setItems(tableNames());
    wDrivingTable.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wPublishedCatalogName, margin).right().result());
    wDrivingTable.addListener(
        SWT.Selection,
        e -> {
          refreshJoinTableComboValues();
          refreshProjectionTableComboValues();
          refreshResolvedKeyLabels();
        });

    Label wlMode = new Label(comp, SWT.RIGHT);
    wlMode.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.GenerationMode.Label"));
    PropsUi.setLook(wlMode);
    wlMode.setLayoutData(
        new FormDataBuilder().left().top(wDrivingTable, margin).right(middle, -margin).result());
    wGenerationMode = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wGenerationMode);
    wGenerationMode.setItems(SourceQueryGenerationMode.getDescriptions());
    wGenerationMode.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wDrivingTable, margin).right().result());

    Label wlWhere = new Label(comp, SWT.LEFT);
    wlWhere.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Where.Label"));
    PropsUi.setLook(wlWhere);
    wlWhere.setLayoutData(
        new FormDataBuilder().left().top(wGenerationMode, margin * 2).right().result());
    wWhere =
        new StyledTextComp(
            variables, comp, SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    PropsUi.setLook(wWhere, Props.WIDGET_STYLE_FIXED);
    wWhere.setLayoutData(
        new FormDataBuilder().left().top(wlWhere, margin).right().bottom(100, -margin).result());
  }

  private void addJoinsTab(CTabFolder tabFolder, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Tab.Joins.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Button wAddRelated = new Button(comp, SWT.PUSH);
    wAddRelated.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.AddRelated.Button"));
    wAddRelated.setLayoutData(new FormDataBuilder().left().top(0, margin).result());
    wAddRelated.addListener(SWT.Selection, e -> addRelatedTables());

    Button wRefreshKeys = new Button(comp, SWT.PUSH);
    wRefreshKeys.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.RefreshKeys.Button"));
    wRefreshKeys.setLayoutData(
        new FormDataBuilder().left(wAddRelated, margin).top(0, margin).result());
    wRefreshKeys.addListener(SWT.Selection, e -> refreshResolvedKeyLabels());

    Label wlHint = new Label(comp, SWT.LEFT);
    wlHint.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.Hint"));
    PropsUi.setLook(wlHint);
    wlHint.setLayoutData(
        new FormDataBuilder().left(wRefreshKeys, margin * 2).top(0, margin + 4).right().result());

    colJoinTable =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.Table"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            tableNames());
    colJoinTable.setComboValuesSelectionListener((item, rowNr, colNr) -> preferredJoinTableNames());

    ColumnInfo colJoinType =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.JoinType"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            SourceJoinType.getDescriptions());

    ColumnInfo colKeys =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.ResolvedKeys"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    colKeys.setReadOnly(true);
    colKeys.setToolTip(
        BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.ResolvedKeys.ToolTip"));

    colJoinRelationship =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.Relationship"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            relationshipNames());
    colJoinRelationship.setComboValuesSelectionListener(
        (item, rowNr, colNr) -> relationshipNamesForRow(item));
    colJoinRelationship.setToolTip(
        BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.Relationship.ToolTip"));

    ColumnInfo colLeft =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.LeftColumns"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[0]);
    colLeft.setComboValuesSelectionListener((item, rowNr, colNr) -> leftColumnChoices(item));
    colLeft.setToolTip(
        BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.LeftColumns.ToolTip"));

    ColumnInfo colRight =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.RightColumns"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[0]);
    colRight.setComboValuesSelectionListener((item, rowNr, colNr) -> rightColumnChoices(item));
    colRight.setToolTip(
        BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Joins.RightColumns.ToolTip"));

    ColumnInfo[] columns =
        new ColumnInfo[] {
          colJoinTable, colJoinType, colKeys, colJoinRelationship, colLeft, colRight
        };
    wJoins =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(input.getJoins().size(), 1),
            null,
            PropsUi.getInstance());
    wJoins.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wAddRelated, margin)
            .right()
            .bottom(100, -margin)
            .result());
  }

  private void addColumnsTab(CTabFolder tabFolder, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Tab.Columns.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Button wGetColumns = new Button(comp, SWT.PUSH);
    wGetColumns.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.GetColumns.Button"));
    wGetColumns.setLayoutData(new FormDataBuilder().left().top(0, margin).result());
    wGetColumns.addListener(SWT.Selection, e -> getColumnsFromParticipants(false));

    Button wGetKeys = new Button(comp, SWT.PUSH);
    wGetKeys.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.GetPrimaryKeys.Button"));
    wGetKeys.setLayoutData(new FormDataBuilder().left(wGetColumns, margin).top(0, margin).result());
    wGetKeys.addListener(SWT.Selection, e -> getColumnsFromParticipants(true));

    colProjTable =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Columns.Table"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            participantTableNames());
    colProjTable.setComboValuesSelectionListener((item, rowNr, colNr) -> participantTableNames());

    colProjColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Columns.Column"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[0]);
    colProjColumn.setComboValuesSelectionListener(
        (item, rowNr, colNr) -> columnNamesForTable(item.getText(PROJ_COL_TABLE)));

    ColumnInfo colAlias =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Columns.Alias"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);

    ColumnInfo colKey =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Columns.Key"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            keyPositionChoices());
    colKey.setToolTip(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Columns.Key.ToolTip"));

    ColumnInfo[] columns = new ColumnInfo[] {colProjTable, colProjColumn, colAlias, colKey};
    wColumns =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(input.getColumns().size(), 1),
            null,
            PropsUi.getInstance());
    wColumns.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wGetColumns, margin)
            .right()
            .bottom(100, -margin)
            .result());
  }

  private void addSqlTab(CTabFolder tabFolder, int margin) {
    sqlTab = new CTabItem(tabFolder, SWT.NONE);
    sqlTab.setFont(GuiResource.getInstance().getFontDefault());
    sqlTab.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Tab.Sql.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    sqlTab.setControl(comp);

    // Match ActionSqlDialog.wSql: SQLStyledTextComp + explicit addLineStyleListener(keywords).
    int style = SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
    if (EnvironmentUtils.getInstance().isWeb()) {
      wSqlPreview = new StyledTextComp(variables, comp, style);
    } else {
      wSqlPreview = new SQLStyledTextComp(variables, comp, style);
    }
    wSqlPreview.addLineStyleListener(getSqlReservedWords());
    PropsUi.setLook(wSqlPreview, Props.WIDGET_STYLE_FIXED);
    wSqlPreview.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(100, -margin).result());
  }

  /**
   * Database reserved words for {@link SQLStyledTextComp} highlighting (same approach as {@code
   * ActionSqlDialog#getSqlReservedWords}).
   */
  private List<String> getSqlReservedWords() {
    try {
      SourceQuery working = workingQueryFromDialogSafe();
      if (!SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, working)) {
        return List.of();
      }
      String connectionName =
          SourceQueryGenerationSupport.resolveSharedDatabaseName(model, working);
      if (Utils.isEmpty(connectionName)) {
        return List.of();
      }
      String resolved = variables.resolve(connectionName);
      if (resolved.startsWith("${")) {
        return List.of();
      }
      DatabaseMeta databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(resolved);
      if (databaseMeta == null || databaseMeta.getReservedWords() == null) {
        return List.of();
      }
      return java.util.Arrays.stream(databaseMeta.getReservedWords()).toList();
    } catch (Exception e) {
      return List.of();
    }
  }

  private SourceQuery workingQueryFromDialogSafe() {
    try {
      return workingQueryFromDialog();
    } catch (Exception e) {
      return input != null ? input : new SourceQuery();
    }
  }

  private String[] tableNames() {
    List<String> names = new ArrayList<>();
    for (SourceTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        names.add(table.getName());
      }
    }
    return names.toArray(new String[0]);
  }

  private String[] participantTableNames() {
    SourceQuery working = workingQueryFromDialog();
    List<String> names = SourceQueryGenerationSupport.participantTableNames(working);
    if (names.isEmpty()) {
      return tableNames();
    }
    return names.toArray(new String[0]);
  }

  private String[] preferredJoinTableNames() {
    Set<String> inScope =
        SourceQueryRelationSupport.inScopeFromDrivingAndJoins(
            wDrivingTable.getText(), readJoinsWithoutKeys());
    List<String> related = SourceQueryRelationSupport.relatedTableNames(model, inScope);
    LinkedHashSet<String> ordered = new LinkedHashSet<>(related);
    for (String name : tableNames()) {
      if (!inScope.contains(name)) {
        ordered.add(name);
      }
    }
    return ordered.toArray(new String[0]);
  }

  private String[] relationshipNames() {
    List<String> names = new ArrayList<>();
    names.add("");
    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship != null && !Utils.isEmpty(relationship.getName())) {
        names.add(relationship.getName());
      }
    }
    return names.toArray(new String[0]);
  }

  private String[] relationshipNamesForRow(TableItem item) {
    List<String> names = new ArrayList<>();
    names.add("");
    Set<String> inScope =
        SourceQueryRelationSupport.inScopeFromDrivingAndJoins(
            wDrivingTable.getText(), readJoinsWithoutKeys());
    inScope.remove(item.getText(JOIN_COL_TABLE));
    for (SourceRelationship relationship :
        SourceQueryRelationSupport.relationshipsTo(model, item.getText(JOIN_COL_TABLE), inScope)) {
      if (!Utils.isEmpty(relationship.getName())) {
        names.add(relationship.getName());
      }
    }
    if (names.size() == 1) {
      return relationshipNames();
    }
    return names.toArray(new String[0]);
  }

  private String[] columnNamesForTable(String tableName) {
    SourceTable table = model.findTable(tableName);
    if (table == null) {
      return new String[0];
    }
    List<String> names = new ArrayList<>();
    for (SourceColumn column : table.getColumns()) {
      if (column != null && !Utils.isEmpty(column.getName())) {
        names.add(column.getName());
      }
    }
    return names.toArray(new String[0]);
  }

  private String[] leftColumnChoices(TableItem item) {
    Set<String> inScope =
        SourceQueryRelationSupport.inScopeFromDrivingAndJoins(
            wDrivingTable.getText(), readJoinsWithoutKeys());
    inScope.remove(item.getText(JOIN_COL_TABLE));
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (String tableName : inScope) {
      for (String column : columnNamesForTable(tableName)) {
        names.add(column);
      }
    }
    return names.toArray(new String[0]);
  }

  private String[] rightColumnChoices(TableItem item) {
    return columnNamesForTable(item.getText(JOIN_COL_TABLE));
  }

  private static String[] keyPositionChoices() {
    String[] choices = new String[9];
    choices[0] = "";
    for (int i = 1; i <= 8; i++) {
      choices[i] = Integer.toString(i);
    }
    return choices;
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wPublishedCatalogName.setText(Const.NVL(input.getPublishedCatalogName(), ""));
    wDrivingTable.setText(Const.NVL(input.getDrivingTableName(), ""));
    EnumDialogSupport.selectCombo(wGenerationMode, input.resolveGenerationMode());
    wWhere.setText(Const.NVL(input.getWhereClause(), ""));

    wJoins.clearAll(false);
    for (SourceQueryJoin join : input.getJoins()) {
      if (join == null) {
        continue;
      }
      TableItem item = new TableItem(wJoins.table, SWT.NONE);
      item.setText(JOIN_COL_TABLE, Const.NVL(join.getTableName(), ""));
      item.setText(JOIN_COL_TYPE, join.resolveJoinType().getDescription());
      item.setText(JOIN_COL_KEYS, "");
      item.setText(JOIN_COL_RELATIONSHIP, Const.NVL(join.getRelationshipName(), ""));
      item.setText(JOIN_COL_LEFT, String.join(",", join.getLeftColumns()));
      item.setText(JOIN_COL_RIGHT, String.join(",", join.getRightColumns()));
    }
    wJoins.optimizeTableView();

    wColumns.clearAll(false);
    for (SourceQueryColumn column : input.getColumns()) {
      if (column == null) {
        continue;
      }
      TableItem item = new TableItem(wColumns.table, SWT.NONE);
      item.setText(PROJ_COL_TABLE, Const.NVL(column.getTableName(), ""));
      item.setText(PROJ_COL_COLUMN, Const.NVL(column.getColumnName(), ""));
      item.setText(PROJ_COL_ALIAS, Const.NVL(column.getAlias(), ""));
      item.setText(
          PROJ_COL_KEY,
          column.isPrimaryKey() ? Integer.toString(column.getPrimaryKeyPosition()) : "");
    }
    wColumns.optimizeTableView();
  }

  private void getColumnsFromParticipants(boolean primaryKeysOnly) {
    wColumns.clearAll(false);
    SourceQuery working = workingQueryFromDialog();
    int nextKey = 1;
    for (String tableName : SourceQueryGenerationSupport.participantTableNames(working)) {
      SourceTable table = model.findTable(tableName);
      if (table == null) {
        continue;
      }
      for (SourceColumn column : table.getColumns()) {
        if (column == null || Utils.isEmpty(column.getName())) {
          continue;
        }
        if (primaryKeysOnly && !column.isPrimaryKey()) {
          continue;
        }
        TableItem item = new TableItem(wColumns.table, SWT.NONE);
        item.setText(PROJ_COL_TABLE, tableName);
        item.setText(PROJ_COL_COLUMN, column.getName());
        item.setText(PROJ_COL_ALIAS, "");
        if (primaryKeysOnly || column.isPrimaryKey()) {
          item.setText(PROJ_COL_KEY, Integer.toString(nextKey++));
        } else {
          item.setText(PROJ_COL_KEY, "");
        }
      }
    }
    wColumns.optimizeTableView();
    refreshProjectionTableComboValues();
  }

  private void addRelatedTables() {
    Set<String> inScope =
        SourceQueryRelationSupport.inScopeFromDrivingAndJoins(wDrivingTable.getText(), readJoins());
    if (inScope.isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(
          BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.AddRelated.NeedDriving.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.AddRelated.NeedDriving.Message"));
      box.open();
      return;
    }
    List<String> related = SourceQueryRelationSupport.relatedTableNames(model, inScope);
    if (related.isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.AddRelated.None.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.AddRelated.None.Message"));
      box.open();
      return;
    }
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            shell,
            related.toArray(new String[0]),
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.AddRelated.Title"),
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.AddRelated.Message"));
    dialog.setMulti(true);
    if (dialog.open() == null) {
      return;
    }
    int[] indices = dialog.getSelectionIndeces();
    List<String> picked = new ArrayList<>();
    if (indices != null) {
      for (int index : indices) {
        if (index >= 0 && index < related.size()) {
          picked.add(related.get(index));
        }
      }
    }
    for (String tableName : picked) {
      if (inScope.contains(tableName)) {
        continue;
      }
      TableItem item = new TableItem(wJoins.table, SWT.NONE);
      item.setText(JOIN_COL_TABLE, tableName);
      item.setText(JOIN_COL_TYPE, SourceJoinType.LEFT.getDescription());
      item.setText(JOIN_COL_KEYS, "");
      item.setText(JOIN_COL_RELATIONSHIP, "");
      item.setText(JOIN_COL_LEFT, "");
      item.setText(JOIN_COL_RIGHT, "");
      inScope.add(tableName);
    }
    wJoins.optimizeTableView();
    refreshResolvedKeyLabels();
    refreshJoinTableComboValues();
    refreshProjectionTableComboValues();
  }

  private void refreshResolvedKeyLabels() {
    if (wJoins == null) {
      return;
    }
    Set<String> inScope = new LinkedHashSet<>();
    if (!Utils.isEmpty(wDrivingTable.getText())) {
      inScope.add(wDrivingTable.getText().trim());
    }
    int rows = wJoins.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wJoins.getNonEmpty(i);
      SourceQueryJoin join = joinFromItem(item);
      item.setText(
          JOIN_COL_KEYS, SourceQueryRelationSupport.formatResolvedKeys(model, join, inScope));
      if (!Utils.isEmpty(join.getTableName())) {
        inScope.add(join.getTableName().trim());
      }
    }
  }

  private void refreshJoinTableComboValues() {
    if (colJoinTable != null) {
      colJoinTable.setComboValues(preferredJoinTableNames());
    }
    if (colJoinRelationship != null) {
      colJoinRelationship.setComboValues(relationshipNames());
    }
  }

  private void refreshProjectionTableComboValues() {
    if (colProjTable != null) {
      colProjTable.setComboValues(participantTableNames());
    }
  }

  private SourceQuery workingQueryFromDialog() {
    SourceQuery q = new SourceQuery();
    q.setName(wName.getText());
    q.setDescription(wDescription.getText());
    q.setDrivingTableName(wDrivingTable.getText());
    q.setGenerationMode(SourceQueryGenerationMode.lookupDescription(wGenerationMode.getText()));
    q.setWhereClause(wWhere.getText());
    q.setJoins(readJoins());
    q.setColumns(readColumns());
    return q;
  }

  private List<SourceQueryJoin> readJoins() {
    List<SourceQueryJoin> joins = new ArrayList<>();
    if (wJoins == null) {
      return joins;
    }
    int rows = wJoins.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wJoins.getNonEmpty(i);
      SourceQueryJoin join = joinFromItem(item);
      if (!Utils.isEmpty(join.getTableName())) {
        joins.add(join);
      }
    }
    return joins;
  }

  /** Joins without depending on key-label column (avoids recursion while building combos). */
  private List<SourceQueryJoin> readJoinsWithoutKeys() {
    return readJoins();
  }

  private SourceQueryJoin joinFromItem(TableItem item) {
    SourceQueryJoin join = new SourceQueryJoin();
    join.setTableName(item.getText(JOIN_COL_TABLE).trim());
    join.setJoinType(SourceJoinType.lookupDescription(item.getText(JOIN_COL_TYPE)));
    join.setRelationshipName(item.getText(JOIN_COL_RELATIONSHIP).trim());
    join.setLeftColumns(splitCsv(item.getText(JOIN_COL_LEFT)));
    join.setRightColumns(splitCsv(item.getText(JOIN_COL_RIGHT)));
    return join;
  }

  private List<SourceQueryColumn> readColumns() {
    List<SourceQueryColumn> columns = new ArrayList<>();
    if (wColumns == null) {
      return columns;
    }
    int rows = wColumns.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wColumns.getNonEmpty(i);
      if (Utils.isEmpty(item.getText(PROJ_COL_TABLE))
          || Utils.isEmpty(item.getText(PROJ_COL_COLUMN))) {
        continue;
      }
      SourceQueryColumn column = new SourceQueryColumn();
      column.setTableName(item.getText(PROJ_COL_TABLE).trim());
      column.setColumnName(item.getText(PROJ_COL_COLUMN).trim());
      column.setAlias(item.getText(PROJ_COL_ALIAS).trim());
      column.setPrimaryKeyPosition(Const.toInt(item.getText(PROJ_COL_KEY).trim(), 0));
      columns.add(column);
    }
    return columns;
  }

  private static List<String> splitCsv(String value) {
    List<String> parts = new ArrayList<>();
    if (Utils.isEmpty(value)) {
      return parts;
    }
    for (String part : value.split(",")) {
      if (!Utils.isEmpty(part.trim())) {
        parts.add(part.trim());
      }
    }
    return parts;
  }

  private void refreshSqlPreview() {
    try {
      refreshResolvedKeyLabels();
      SourceQuery working = workingQueryFromDialog();
      if (!SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, working)) {
        wSqlPreview.setText(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Sql.NotSingleConnection"));
        return;
      }
      String connectionName =
          SourceQueryGenerationSupport.resolveSharedDatabaseName(model, working);
      DatabaseMeta databaseMeta =
          metadataProvider
              .getSerializer(DatabaseMeta.class)
              .load(variables.resolve(connectionName));
      String sql = SourceQuerySqlGenerator.generate(model, working, databaseMeta, variables);
      wSqlPreview.setText(DvSqlSupport.formatForDisplay(sql));
    } catch (Exception e) {
      wSqlPreview.setText(e.getMessage() != null ? e.getMessage() : e.toString());
    }
  }

  private void previewData() {
    try {
      SourceQuery working = workingQueryFromDialog();
      List<RowMetaAndData> rows =
          SourceQueryPreviewSupport.preview(
              model,
              working,
              variables,
              metadataProvider,
              SourceQueryPreviewSupport.DEFAULT_ROW_LIMIT);
      if (rows.isEmpty()) {
        MessageBox emptyBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        emptyBox.setText(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Empty.Title"));
        emptyBox.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Empty.Message"));
        emptyBox.open();
        return;
      }
      ShowRowsDialog dialog =
          new ShowRowsDialog(
              shell,
              variables,
              BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Title"),
              BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Message"),
              rows.get(0).getRowMeta(),
              toObjectList(rows));
      dialog.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Error.Message"),
          e);
    }
  }

  private void validateDefinition() {
    try {
      refreshResolvedKeyLabels();
      SourceQuery working = workingQueryFromDialog();
      List<ICheckResult> remarks =
          SourceQueryValidationSupport.check(model, working, variables, metadataProvider);
      if (remarks.isEmpty()) {
        remarks =
            List.of(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_OK,
                    BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Validate.Ok.Message"),
                    null));
      }
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception ex) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Validate.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiSourceQueryDialog.Validate.Error.Message", ex.getMessage()),
          ex);
    }
  }

  private static List<Object[]> toObjectList(List<RowMetaAndData> rows) {
    List<Object[]> list = new ArrayList<>();
    for (RowMetaAndData row : rows) {
      list.add(row.getData());
    }
    return list;
  }

  private void ok() {
    String name = wName.getText().trim();
    if (Utils.isEmpty(name) || Utils.isEmpty(wDrivingTable.getText())) {
      return;
    }
    SourceQuery existing = model.findQuery(name);
    if (existing != null && existing != input) {
      return;
    }
    refreshResolvedKeyLabels();
    String oldName = input.getName();
    input.setName(name);
    input.setDescription(wDescription.getText());
    // Empty catalog feed name → publish uses the query name (SourceQueryCatalogPublisher).
    String catalogFeed = wPublishedCatalogName.getText().trim();
    input.setPublishedCatalogName(Utils.isEmpty(catalogFeed) ? null : catalogFeed);
    input.setDrivingTableName(wDrivingTable.getText());
    input.setGenerationMode(SourceQueryGenerationMode.lookupDescription(wGenerationMode.getText()));
    input.setWhereClause(wWhere.getText());
    input.setJoins(readJoins());
    input.setColumns(readColumns());
    if (model != null) {
      SourceRelationshipLifecycleSupport.dropRelationshipsOnRename(
          model, SourceEndpointKind.QUERY, oldName, name);
    }
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
