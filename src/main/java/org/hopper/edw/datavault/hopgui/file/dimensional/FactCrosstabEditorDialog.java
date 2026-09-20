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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.perspective.database.DatabaseWorkbenchDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.hopper.core.AggregationMethod;
import org.hopper.edw.datavault.hopgui.PresentationGuiPlugin;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;
import org.hopper.edw.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabField;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabPresentationBuilder;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabQuery;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSourceColumn;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSourceModel;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSourceTable;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSpec;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSpec.Zone;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSqlBuilder;
import org.hopper.edw.semantic.SemanticLayerSupport;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticModelPersistence;
import org.hopper.edw.semantic.model.SemanticSelection;
import org.hopper.edw.semantic.query.SemanticSelectionAdapter;
import org.hopper.presentation.simple.HGeneratedCatalog;

/** Crosstab editor: source columns into groups, axes, and facts. */
public class FactCrosstabEditorDialog {

  private static final Class<?> PKG = HopGuiDimensionalModelGraph.class;

  static final String TREE_KIND = "kind";
  static final String TREE_TABLE = "table";
  static final String TREE_COLUMN = "column";
  static final String KIND_ROOT = "root";
  static final String KIND_TABLE = "table";
  static final String KIND_COLUMN = "column";
  static final String DND_SEP = "\t";

  private static final String[] AGGREGATIONS =
      new String[] {
        AggregationMethod.SUM.name(),
        AggregationMethod.COUNT.name(),
        AggregationMethod.AVERAGE.name()
      };

  private final Shell parent;
  private final DimensionalModel model;
  private final IDmFactLikeTable fact;
  private final FactCrosstabSourceModel sources;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final FactCrosstabSpec spec;
  private final SemanticModel semanticModel;

  private Shell shell;
  private Tree wTree;
  private ZoneTable groups;
  private ZoneTable horizontal;
  private ZoneTable vertical;
  private ZoneTable facts;
  private Button wHorizontalTotals;
  private Button wVerticalTotals;

  @Getter private boolean confirmed;
  @Getter private HGeneratedCatalog catalog;

  public FactCrosstabEditorDialog(
      Shell parent,
      DimensionalModel model,
      IDmFactLikeTable fact,
      FactCrosstabSpec spec,
      FactCrosstabSourceModel sources,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this(parent, model, fact, spec, sources, variables, metadataProvider, null);
  }

  public FactCrosstabEditorDialog(
      Shell parent,
      DimensionalModel model,
      IDmFactLikeTable fact,
      FactCrosstabSpec spec,
      FactCrosstabSourceModel sources,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SemanticModel semanticModel) {
    this.parent = parent;
    this.model = model;
    this.fact = fact;
    this.spec = spec != null ? spec : new FactCrosstabSpec();
    this.sources = sources;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.semanticModel = semanticModel;
  }

  public FactCrosstabSpec getSpec() {
    return spec;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Shell.Title"));
    shell.setLayout(new FormLayout());
    int margin = PropsUi.getMargin();

    DialogHelpSupport.createHelpButton(shell, HelpTopics.FACT_CROSSTAB_EDITOR);

    Button wShow = new Button(shell, SWT.PUSH);
    wShow.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Show.Label"));
    wShow.addListener(SWT.Selection, e -> show());
    Button wSql = new Button(shell, SWT.PUSH);
    wSql.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Sql.Label"));
    wSql.setToolTipText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Sql.Tooltip"));
    wSql.addListener(SWT.Selection, e -> previewSql());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    Button wSaveSel = new Button(shell, SWT.PUSH);
    wSaveSel.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.SaveSelection.Label"));
    wSaveSel.addListener(SWT.Selection, e -> saveSelection());
    Button wOpenSel = new Button(shell, SWT.PUSH);
    wOpenSel.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.OpenSelection.Label"));
    wOpenSel.addListener(SWT.Selection, e -> openSelection());
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wShow, wSql, wSaveSel, wOpenSel, wCancel}, margin, null);

    SashForm sash = new SashForm(shell, SWT.HORIZONTAL);
    PropsUi.setLook(sash);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, 0);
    fdSash.top = new FormAttachment(0, margin);
    fdSash.right = new FormAttachment(100, 0);
    fdSash.bottom = new FormAttachment(wShow, -margin);
    sash.setLayoutData(fdSash);

    Composite left = new Composite(sash, SWT.NONE);
    left.setLayout(new FormLayout());
    PropsUi.setLook(left);

    Label wlSources = new Label(left, SWT.LEFT);
    PropsUi.setLook(wlSources);
    wlSources.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Sources.Label"));
    FormData fdlSources = new FormData();
    fdlSources.left = new FormAttachment(0, 0);
    fdlSources.top = new FormAttachment(0, 0);
    wlSources.setLayoutData(fdlSources);

    ToolBar toolBar = new ToolBar(left, SWT.FLAT | SWT.RIGHT);
    PropsUi.setLook(toolBar, Props.WIDGET_STYLE_TOOLBAR);
    addZoneTool(toolBar, Zone.GROUPS, "FactCrosstabEditorDialog.AddGroups.Tooltip");
    addZoneTool(toolBar, Zone.HORIZONTAL, "FactCrosstabEditorDialog.AddHorizontal.Tooltip");
    addZoneTool(toolBar, Zone.VERTICAL, "FactCrosstabEditorDialog.AddVertical.Tooltip");
    addZoneTool(toolBar, Zone.FACTS, "FactCrosstabEditorDialog.AddFacts.Tooltip");
    FormData fdTool = new FormData();
    fdTool.left = new FormAttachment(0, 0);
    fdTool.top = new FormAttachment(wlSources, margin);
    fdTool.right = new FormAttachment(100, 0);
    toolBar.setLayoutData(fdTool);

    wTree = new Tree(left, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(wTree);
    FormData fdTree = new FormData();
    fdTree.left = new FormAttachment(0, 0);
    fdTree.top = new FormAttachment(toolBar, margin);
    fdTree.right = new FormAttachment(100, 0);
    fdTree.bottom = new FormAttachment(100, 0);
    wTree.setLayoutData(fdTree);

    Composite right = new Composite(sash, SWT.NONE);
    right.setLayout(new FormLayout());
    PropsUi.setLook(right);

    groups = createZone(right, Zone.GROUPS, "FactCrosstabEditorDialog.Groups.Label", false, 0, 22);
    horizontal =
        createZone(
            right, Zone.HORIZONTAL, "FactCrosstabEditorDialog.Horizontal.Label", false, 22, 44);
    vertical =
        createZone(right, Zone.VERTICAL, "FactCrosstabEditorDialog.Vertical.Label", false, 44, 66);
    facts = createZone(right, Zone.FACTS, "FactCrosstabEditorDialog.Facts.Label", true, 66, 88);

    wHorizontalTotals = new Button(right, SWT.CHECK);
    PropsUi.setLook(wHorizontalTotals);
    wHorizontalTotals.setText(
        BaseMessages.getString(PKG, "FactCrosstabEditorDialog.HorizontalTotals.Label"));
    FormData fdH = new FormData();
    fdH.left = new FormAttachment(0, 0);
    fdH.top = new FormAttachment(88, margin);
    wHorizontalTotals.setLayoutData(fdH);

    wVerticalTotals = new Button(right, SWT.CHECK);
    PropsUi.setLook(wVerticalTotals);
    wVerticalTotals.setText(
        BaseMessages.getString(PKG, "FactCrosstabEditorDialog.VerticalTotals.Label"));
    FormData fdV = new FormData();
    fdV.left = new FormAttachment(wHorizontalTotals, margin * 2);
    fdV.top = new FormAttachment(88, margin);
    wVerticalTotals.setLayoutData(fdV);

    sash.setWeights(new int[] {35, 65});

    populateTree();
    populateZonesFromSpec();
    wHorizontalTotals.setSelection(spec.isShowingHorizontalTotals());
    wVerticalTotals.setSelection(spec.isShowingVerticalTotals());
    if (!EnvironmentUtils.getInstance().isWeb()) {
      installDragSource();
    }

    BaseDialog.defaultShellHandling(shell, e -> show(), e -> cancel());
    return confirmed;
  }

  private void addZoneTool(ToolBar toolBar, Zone zone, String tooltipKey) {
    ToolItem item = new ToolItem(toolBar, SWT.PUSH);
    item.setText(zoneToolLabel(zone));
    item.setToolTipText(BaseMessages.getString(PKG, tooltipKey));
    item.addListener(SWT.Selection, e -> addSelectionTo(zone));
  }

  private String zoneToolLabel(Zone zone) {
    return switch (zone) {
      case GROUPS -> BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Tool.Groups");
      case HORIZONTAL -> BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Tool.Horizontal");
      case VERTICAL -> BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Tool.Vertical");
      case FACTS -> BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Tool.Facts");
    };
  }

  private ZoneTable createZone(
      Composite parent,
      Zone zone,
      String labelKey,
      boolean includeAggregation,
      int topPercent,
      int bottomPercent) {
    Label label = new Label(parent, SWT.LEFT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, labelKey));
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.top = new FormAttachment(topPercent, 0);
    fdl.right = new FormAttachment(100, 0);
    label.setLayoutData(fdl);

    List<ColumnInfo> columns = new ArrayList<>();
    columns.add(
        new ColumnInfo(
            BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Column.Header"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false,
            false));
    columns.add(
        new ColumnInfo(
            BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Column.Table"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false,
            true));
    columns.add(
        new ColumnInfo(
            BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Column.Column"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false,
            true));
    if (includeAggregation) {
      columns.add(
          new ColumnInfo(
              BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Column.Aggregation"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              AGGREGATIONS,
              false));
    }
    TableView table =
        new TableView(
            variables,
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns.toArray(ColumnInfo[]::new),
            1,
            false,
            null,
            PropsUi.getInstance());
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(label, 2);
    fd.right = new FormAttachment(100, 0);
    fd.bottom = new FormAttachment(bottomPercent, 0);
    table.setLayoutData(fd);
    if (!EnvironmentUtils.getInstance().isWeb()) {
      installDropTarget(table, zone);
    }
    return new ZoneTable(zone, table, includeAggregation);
  }

  private void populateTree() {
    wTree.removeAll();
    TreeItem root = new TreeItem(wTree, SWT.NONE);
    root.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Tree.Sources"));
    root.setData(TREE_KIND, KIND_ROOT);
    root.setImage(image("fact.svg"));
    for (FactCrosstabSourceTable table : sources.getTables()) {
      TreeItem tableItem = new TreeItem(root, SWT.NONE);
      tableItem.setText(table.getLogicalName());
      tableItem.setData(TREE_KIND, KIND_TABLE);
      tableItem.setData(TREE_TABLE, table.getLogicalName());
      tableItem.setImage(tableImage(table.getTableType()));
      for (FactCrosstabSourceColumn column : table.getColumns()) {
        TreeItem columnItem = new TreeItem(tableItem, SWT.NONE);
        columnItem.setText(column.getFieldName());
        columnItem.setData(TREE_KIND, KIND_COLUMN);
        columnItem.setData(TREE_TABLE, table.getLogicalName());
        columnItem.setData(TREE_COLUMN, column.getFieldName());
        columnItem.setImage(valueImage(column.getValueType()));
      }
      tableItem.setExpanded(true);
    }
    root.setExpanded(true);
  }

  private void populateZonesFromSpec() {
    groups.setFields(spec.getGroups());
    horizontal.setFields(spec.getHorizontalDimensions());
    vertical.setFields(spec.getVerticalDimensions());
    facts.setFields(spec.getFacts());
  }

  private void addSelectionTo(Zone zone) {
    TreeItem[] selection = wTree.getSelection();
    if (selection == null || selection.length == 0) {
      return;
    }
    TreeItem item = selection[0];
    String kind = string(item.getData(TREE_KIND));
    if (KIND_COLUMN.equals(kind)) {
      addColumn(zone, string(item.getData(TREE_TABLE)), string(item.getData(TREE_COLUMN)));
    } else if (KIND_TABLE.equals(kind)) {
      FactCrosstabSourceTable table = sources.findTable(string(item.getData(TREE_TABLE)));
      if (table != null) {
        for (FactCrosstabSourceColumn column : table.getColumns()) {
          addColumn(zone, table.getLogicalName(), column.getFieldName());
        }
      }
    }
  }

  private void addColumn(Zone zone, String tableName, String columnName) {
    if (Utils.isEmpty(tableName) || Utils.isEmpty(columnName)) {
      return;
    }
    ZoneTable zoneTable = zoneTable(zone);
    if (zoneTable.contains(tableName, columnName)) {
      return;
    }
    FactCrosstabSourceTable table = sources.findTable(tableName);
    FactCrosstabSourceColumn column = table != null ? table.findColumn(columnName) : null;
    String header = column != null ? column.getHeader() : columnName;
    AggregationMethod aggregation = null;
    if (zone == Zone.FACTS) {
      aggregation = defaultAggregation(tableName, column);
    }
    zoneTable.addField(new FactCrosstabField(tableName, columnName, header, aggregation));
  }

  private AggregationMethod defaultAggregation(String tableName, FactCrosstabSourceColumn column) {
    if (semanticModel != null && !Utils.isEmpty(tableName) && column != null) {
      SemanticEntity entity = semanticModel.findEntity(tableName);
      if (entity != null) {
        SemanticMeasure measure = entity.findMeasure(column.getFieldName());
        if (measure != null) {
          return measure.resolveAggregation();
        }
      }
    }
    if (column != null
        && (column.isAdditiveMeasure()
            || column.getKind() == FactCrosstabSourceColumn.Kind.MEASURE
            || column.isNumeric())) {
      return AggregationMethod.SUM;
    }
    return AggregationMethod.COUNT;
  }

  private ZoneTable zoneTable(Zone zone) {
    return switch (zone) {
      case GROUPS -> groups;
      case HORIZONTAL -> horizontal;
      case VERTICAL -> vertical;
      case FACTS -> facts;
    };
  }

  private void collectSpec() {
    spec.setFactTableName(fact.getName());
    spec.setGroups(groups.readFields(false));
    spec.setHorizontalDimensions(horizontal.readFields(false));
    spec.setVerticalDimensions(vertical.readFields(false));
    spec.setFacts(facts.readFields(true));
    spec.setShowingHorizontalTotals(wHorizontalTotals.getSelection());
    spec.setShowingVerticalTotals(wVerticalTotals.getSelection());
  }

  private boolean requireColumnsSelected() {
    if (!spec.isEmpty()) {
      return true;
    }
    MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
    box.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Empty.Title"));
    box.setMessage(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Empty.Message"));
    box.open();
    return false;
  }

  private void previewSql() {
    collectSpec();
    if (!requireColumnsSelected()) {
      return;
    }
    try {
      DatabaseMeta target =
          DmTargetDatabaseSupport.loadTargetDatabase(
              metadataProvider, model.getConfigurationOrDefault());
      if (target == null) {
        throw new HopException(
            "Set a target database on the dimensional configuration before previewing SQL");
      }
      FactCrosstabQuery query = FactCrosstabSqlBuilder.build(spec, sources, target, variables);
      String sql =
          FactCrosstabSqlBuilder.applyRowLimit(
              query.getSql(), target, FactCrosstabSqlBuilder.DEBUG_ROW_LIMIT);
      DatabaseWorkbenchDialog.openSql(target, sql);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Title"),
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.PreviewSql"),
          e);
    }
  }

  private void show() {
    collectSpec();
    if (!requireColumnsSelected()) {
      return;
    }
    try {
      PresentationGuiPlugin.ensureEnvironment();
      catalog =
          FactCrosstabPresentationBuilder.build(
              model, fact, spec, sources, variables, metadataProvider, semanticModel);
      confirmed = true;
      dispose();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Title"),
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Generate"),
          e);
    }
  }

  private void saveSelection() {
    if (semanticModel == null) {
      return;
    }
    collectSpec();
    if (!requireColumnsSelected()) {
      return;
    }
    EnterStringDialog nameDialog =
        new EnterStringDialog(
            shell,
            Const.NVL(fact.getName(), "selection"),
            BaseMessages.getString(PKG, "FactCrosstabEditorDialog.SaveSelection.Title"),
            BaseMessages.getString(PKG, "FactCrosstabEditorDialog.SaveSelection.Message"));
    String name = nameDialog.open();
    if (Utils.isEmpty(name)) {
      return;
    }
    SemanticSelection selection =
        SemanticSelectionAdapter.fromSpec(semanticModel, fact.getName(), spec, name);
    semanticModel.putSelection(selection);
    try {
      String filename = semanticModel.getFilename();
      if (Utils.isEmpty(filename)) {
        filename = promptSemanticFilename();
      }
      if (Utils.isEmpty(filename)) {
        return;
      }
      SemanticModelPersistence.save(semanticModel, filename, variables);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Title"),
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.SaveSelection"),
          e);
    }
  }

  private String promptSemanticFilename() throws Exception {
    String proposed =
        SemanticLayerSupport.defaultSemanticFilename(model != null ? model.getFilename() : null);
    if (Utils.isEmpty(proposed)) {
      String name =
          Const.NVL(semanticModel.getName(), "semantic-layer") + SemanticModel.FILE_EXTENSION;
      String home = Const.NVL(variables.getVariable("PROJECT_HOME"), ".");
      proposed = home + "/" + name;
    }
    return BaseDialog.presentFileDialog(
        true,
        shell,
        null,
        variables,
        HopVfs.getFileObject(proposed),
        new String[] {"*" + SemanticModel.FILE_EXTENSION},
        new String[] {
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.SemanticLayers.Filter")
        },
        true);
  }

  private void openSelection() {
    if (semanticModel == null
        || semanticModel.getSelections() == null
        || semanticModel.getSelections().isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.NoSelections.Title"));
      box.setMessage(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.NoSelections.Message"));
      box.open();
      return;
    }
    List<String> names = new ArrayList<>();
    for (SemanticSelection selection : semanticModel.getSelections()) {
      if (selection != null && !Utils.isEmpty(selection.getName())) {
        names.add(selection.getName());
      }
    }
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            shell,
            names.toArray(String[]::new),
            BaseMessages.getString(PKG, "FactCrosstabEditorDialog.OpenSelection.Title"),
            BaseMessages.getString(PKG, "FactCrosstabEditorDialog.OpenSelection.Message"));
    String name = dialog.open();
    if (Utils.isEmpty(name)) {
      return;
    }
    SemanticSelection selection = semanticModel.findSelection(name);
    if (selection == null) {
      return;
    }
    FactCrosstabSpec loaded = SemanticSelectionAdapter.toSpec(semanticModel, selection);
    spec.setGroups(loaded.getGroups());
    spec.setHorizontalDimensions(loaded.getHorizontalDimensions());
    spec.setVerticalDimensions(loaded.getVerticalDimensions());
    spec.setFacts(loaded.getFacts());
    spec.setShowingHorizontalTotals(loaded.isShowingHorizontalTotals());
    spec.setShowingVerticalTotals(loaded.isShowingVerticalTotals());
    populateZonesFromSpec();
    wHorizontalTotals.setSelection(spec.isShowingHorizontalTotals());
    wVerticalTotals.setSelection(spec.isShowingVerticalTotals());
  }

  private void cancel() {
    confirmed = false;
    dispose();
  }

  private void dispose() {
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
    shell.dispose();
  }

  private void installDragSource() {
    DragSource dragSource = new DragSource(wTree, DND.DROP_COPY);
    dragSource.setTransfer(new Transfer[] {TextTransfer.getInstance()});
    dragSource.addDragListener(
        new DragSourceAdapter() {
          @Override
          public void dragSetData(DragSourceEvent event) {
            String payload = selectionPayload();
            if (payload == null) {
              event.doit = false;
              return;
            }
            event.data = payload;
          }
        });
  }

  private void installDropTarget(TableView table, Zone zone) {
    DropTarget dropTarget = new DropTarget(table.getTable(), DND.DROP_COPY);
    dropTarget.setTransfer(new Transfer[] {TextTransfer.getInstance()});
    dropTarget.addDropListener(
        new DropTargetAdapter() {
          @Override
          public void drop(DropTargetEvent event) {
            if (!(event.data instanceof String payload)) {
              return;
            }
            applyPayload(zone, payload);
          }
        });
  }

  private String selectionPayload() {
    TreeItem[] selection = wTree.getSelection();
    if (selection == null || selection.length == 0) {
      return null;
    }
    TreeItem item = selection[0];
    String kind = string(item.getData(TREE_KIND));
    if (KIND_COLUMN.equals(kind)) {
      return string(item.getData(TREE_TABLE)) + DND_SEP + string(item.getData(TREE_COLUMN));
    }
    if (KIND_TABLE.equals(kind)) {
      return string(item.getData(TREE_TABLE)) + DND_SEP;
    }
    return null;
  }

  private void applyPayload(Zone zone, String payload) {
    int sep = payload.indexOf(DND_SEP);
    if (sep < 0) {
      return;
    }
    String tableName = payload.substring(0, sep);
    String columnName = payload.substring(sep + DND_SEP.length());
    if (Utils.isEmpty(columnName)) {
      FactCrosstabSourceTable table = sources.findTable(tableName);
      if (table != null) {
        for (FactCrosstabSourceColumn column : table.getColumns()) {
          addColumn(zone, tableName, column.getFieldName());
        }
      }
      return;
    }
    addColumn(zone, tableName, columnName);
  }

  private Image tableImage(DmTableType type) {
    if (type == null) {
      return image("dimension.svg");
    }
    return switch (type) {
      case FACT,
              FACTLESS_FACT,
              PERIODIC_SNAPSHOT_FACT,
              ACCUMULATING_SNAPSHOT_FACT,
              AGGREGATE_FACT ->
          image("fact.svg");
      case DIMENSION_ALIAS -> image("dimension-alias.svg");
      case JUNK_DIMENSION -> image("dimension-junk.svg");
      default -> image("dimension.svg");
    };
  }

  private Image image(String location) {
    return GuiResource.getInstance()
        .getImage(
            location,
            FactCrosstabEditorDialog.class.getClassLoader(),
            ConstUi.SMALL_ICON_SIZE,
            ConstUi.SMALL_ICON_SIZE);
  }

  private Image valueImage(int valueType) {
    if (valueType == IValueMeta.TYPE_NONE) {
      return GuiResource.getInstance().getImageLabel();
    }
    try {
      return GuiResource.getInstance().getImage(ValueMetaFactory.createValueMeta("c", valueType));
    } catch (Exception ignored) {
      return GuiResource.getInstance().getImageLabel();
    }
  }

  private static String string(Object value) {
    return value == null ? "" : value.toString();
  }

  private static final class ZoneTable {
    private final TableView table;
    private final boolean aggregations;

    private ZoneTable(Zone zone, TableView table, boolean aggregations) {
      this.table = table;
      this.aggregations = aggregations;
    }

    boolean contains(String tableName, String columnName) {
      for (FactCrosstabField field : readFields(aggregations)) {
        if (field.sameSource(tableName, columnName)) {
          return true;
        }
      }
      return false;
    }

    void addField(FactCrosstabField field) {
      TableItem item = new TableItem(table.getTable(), SWT.NONE);
      writeItem(item, field);
      table.removeEmptyRows();
      table.setRowNums();
      table.optWidth(true);
    }

    void setFields(List<FactCrosstabField> fields) {
      table.clearAll(false);
      if (fields != null) {
        for (FactCrosstabField field : fields) {
          if (field != null) {
            TableItem item = new TableItem(table.getTable(), SWT.NONE);
            writeItem(item, field);
          }
        }
      }
      table.removeEmptyRows();
      table.setRowNums();
      table.optWidth(true);
    }

    List<FactCrosstabField> readFields(boolean includeAggregation) {
      List<FactCrosstabField> fields = new ArrayList<>();
      for (int i = 0; i < table.nrNonEmpty(); i++) {
        TableItem item = table.getNonEmpty(i);
        String header = Const.NVL(item.getText(1), "");
        String tableName = Const.NVL(item.getText(2), "");
        String columnName = Const.NVL(item.getText(3), "");
        if (Utils.isEmpty(tableName) || Utils.isEmpty(columnName)) {
          continue;
        }
        AggregationMethod aggregation = null;
        if (includeAggregation) {
          aggregation = parseAggregation(item.getText(4));
        }
        fields.add(new FactCrosstabField(tableName, columnName, header, aggregation));
      }
      return fields;
    }

    private void writeItem(TableItem item, FactCrosstabField field) {
      item.setText(1, Const.NVL(field.getHeader(), field.getColumnName()));
      item.setText(2, Const.NVL(field.getTableName(), ""));
      item.setText(3, Const.NVL(field.getColumnName(), ""));
      if (aggregations) {
        AggregationMethod aggregation =
            field.getAggregation() != null ? field.getAggregation() : AggregationMethod.SUM;
        item.setText(4, aggregation.name());
      }
    }

    private static AggregationMethod parseAggregation(String raw) {
      if (Utils.isEmpty(raw)) {
        return AggregationMethod.SUM;
      }
      try {
        return AggregationMethod.valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
        return AggregationMethod.SUM;
      }
    }
  }
}
