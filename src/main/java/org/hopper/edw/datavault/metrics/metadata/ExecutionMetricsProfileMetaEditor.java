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
package org.hopper.edw.datavault.metrics.metadata;

import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.SqlEditor;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metrics.LoadRunMetricsDdlSupport;

/** Editor for {@link ExecutionMetricsProfileMeta}. */
@GuiPlugin(description = "Editor for Execution Metrics Profile metadata")
public class ExecutionMetricsProfileMetaEditor extends MetadataEditor<ExecutionMetricsProfileMeta> {

  private static final Class<?> PKG = ExecutionMetricsProfileMetaEditor.class;

  private Text wName;
  private Text wDescription;
  private Button wEnabled;
  private Text wMetricsOutputFolder;
  private Combo wCatalogConnection;
  private Combo wTargetDatabase;
  private Text wOperationsSchema;
  private Button wAutoCreateTables;
  private Button wPublishCatalogDefinitions;
  private Button wPublishDatabaseRows;
  private Text wDimLookupPreloadRatioThreshold;
  private Text wTargetReadRatioThreshold;
  private Text wSortRowsRiskThreshold;
  private Text wHighTransformDurationMs;

  public ExecutionMetricsProfileMetaEditor(
      HopGui hopGui,
      MetadataManager<ExecutionMetricsProfileMeta> manager,
      ExecutionMetricsProfileMeta metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  protected Button createHelpButton(Shell shell) {
    return DialogHelpSupport.createHelpButton(shell, HelpTopics.EXECUTION_METRICS_PROFILE);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.Name.Label"));
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

    CTabFolder tabFolder = new CTabFolder(parent, SWT.BORDER);
    FormData fdTabs = new FormData();
    fdTabs.top = new FormAttachment(wName, margin);
    fdTabs.left = new FormAttachment(0, 0);
    fdTabs.right = new FormAttachment(100, 0);
    fdTabs.bottom = new FormAttachment(100, 0);
    tabFolder.setLayoutData(fdTabs);

    createGeneralTab(tabFolder, middle, margin);
    createCollectionTab(tabFolder, middle, margin);
    createPublishingTab(tabFolder, middle, margin);
    createInsightsTab(tabFolder, middle, margin);
    tabFolder.setSelection(0);

    setWidgetsContent();
    resetChanged();

    Listener modifyListener = e -> setChanged();
    wName.addListener(SWT.Modify, modifyListener);
    wDescription.addListener(SWT.Modify, modifyListener);
    wEnabled.addListener(SWT.Selection, modifyListener);
    wMetricsOutputFolder.addListener(SWT.Modify, modifyListener);
    wCatalogConnection.addListener(SWT.Modify, modifyListener);
    wTargetDatabase.addListener(SWT.Modify, modifyListener);
    wOperationsSchema.addListener(SWT.Modify, modifyListener);
    wAutoCreateTables.addListener(SWT.Selection, modifyListener);
    wPublishCatalogDefinitions.addListener(SWT.Selection, modifyListener);
    wPublishDatabaseRows.addListener(SWT.Selection, modifyListener);
    wDimLookupPreloadRatioThreshold.addListener(SWT.Modify, modifyListener);
  }

  private void createGeneralTab(CTabFolder tabFolder, int middle, int margin) {
    Composite comp = new Composite(tabFolder, SWT.NONE);
    comp.setLayout(new FormLayout());
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.General.Tab"));
    tab.setControl(comp);

    wDescription = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    Control last =
        addField(
            comp,
            middle,
            margin,
            null,
            "ExecutionMetricsProfileMetaEditor.Description.Label",
            wDescription);

    Label wlEnabled = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlEnabled);
    wlEnabled.setText(
        BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.Enabled.Label"));
    FormData fdlEnabled = new FormData();
    fdlEnabled.top = new FormAttachment(last, margin);
    fdlEnabled.left = new FormAttachment(0, 0);
    fdlEnabled.right = new FormAttachment(middle, -margin);
    wlEnabled.setLayoutData(fdlEnabled);

    wEnabled = new Button(comp, SWT.CHECK);
    PropsUi.setLook(wEnabled);
    wEnabled.setSelection(true);
    FormData fdEnabled = new FormData();
    fdEnabled.top = new FormAttachment(wlEnabled, 0, SWT.CENTER);
    fdEnabled.left = new FormAttachment(middle, 0);
    wEnabled.setLayoutData(fdEnabled);
  }

  private void createCollectionTab(CTabFolder tabFolder, int middle, int margin) {
    Composite comp = new Composite(tabFolder, SWT.NONE);
    comp.setLayout(new FormLayout());
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.Collection.Tab"));
    tab.setControl(comp);

    wMetricsOutputFolder = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    addField(
        comp,
        middle,
        margin,
        null,
        "ExecutionMetricsProfileMetaEditor.MetricsOutputFolder.Label",
        wMetricsOutputFolder);
  }

  private void createPublishingTab(CTabFolder tabFolder, int middle, int margin) {
    Composite comp = new Composite(tabFolder, SWT.NONE);
    comp.setLayout(new FormLayout());
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.Publishing.Tab"));
    tab.setControl(comp);

    Label wlCatalog = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlCatalog);
    wlCatalog.setText(
        BaseMessages.getString(
            PKG, "ExecutionMetricsProfileMetaEditor.DataCatalogConnection.Label"));
    FormData fdlCatalog = new FormData();
    fdlCatalog.top = new FormAttachment(0, margin);
    fdlCatalog.left = new FormAttachment(0, 0);
    fdlCatalog.right = new FormAttachment(middle, -margin);
    wlCatalog.setLayoutData(fdlCatalog);

    wCatalogConnection = new Combo(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wCatalogConnection);
    populateCatalogConnections();
    FormData fdCatalog = new FormData();
    fdCatalog.top = new FormAttachment(wlCatalog, 0, SWT.CENTER);
    fdCatalog.left = new FormAttachment(middle, 0);
    fdCatalog.right = new FormAttachment(100, 0);
    wCatalogConnection.setLayoutData(fdCatalog);
    Control last = wCatalogConnection;

    Label wlDatabase = new Label(comp, SWT.RIGHT);
    PropsUi.setLook(wlDatabase);
    wlDatabase.setText(
        BaseMessages.getString(
            PKG, "ExecutionMetricsProfileMetaEditor.TargetDatabaseConnection.Label"));
    FormData fdlDatabase = new FormData();
    fdlDatabase.top = new FormAttachment(last, margin);
    fdlDatabase.left = new FormAttachment(0, 0);
    fdlDatabase.right = new FormAttachment(middle, -margin);
    wlDatabase.setLayoutData(fdlDatabase);

    wTargetDatabase = new Combo(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wTargetDatabase);
    populateDatabaseConnections();
    FormData fdDatabase = new FormData();
    fdDatabase.top = new FormAttachment(wlDatabase, 0, SWT.CENTER);
    fdDatabase.left = new FormAttachment(middle, 0);
    fdDatabase.right = new FormAttachment(100, 0);
    wTargetDatabase.setLayoutData(fdDatabase);
    last = wTargetDatabase;

    wOperationsSchema = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    last =
        addField(
            comp,
            middle,
            margin,
            last,
            "ExecutionMetricsProfileMetaEditor.OperationsSchema.Label",
            wOperationsSchema);
    wOperationsSchema.setToolTipText(
        BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.OperationsSchema.ToolTip"));

    last =
        addCheckbox(
            comp,
            middle,
            margin,
            last,
            "ExecutionMetricsProfileMetaEditor.AutoCreateTables.Label",
            wAutoCreateTables = new Button(comp, SWT.CHECK));
    wAutoCreateTables.setSelection(true);
    wAutoCreateTables.setToolTipText(
        BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.AutoCreateTables.ToolTip"));

    last =
        addCheckbox(
            comp,
            middle,
            margin,
            last,
            "ExecutionMetricsProfileMetaEditor.PublishCatalogDefinitions.Label",
            wPublishCatalogDefinitions = new Button(comp, SWT.CHECK));
    wPublishCatalogDefinitions.setSelection(true);

    addCheckbox(
        comp,
        middle,
        margin,
        last,
        "ExecutionMetricsProfileMetaEditor.PublishDatabaseRows.Label",
        wPublishDatabaseRows = new Button(comp, SWT.CHECK));
    wPublishDatabaseRows.setSelection(true);
  }

  private void createInsightsTab(CTabFolder tabFolder, int middle, int margin) {
    Composite comp = new Composite(tabFolder, SWT.NONE);
    comp.setLayout(new FormLayout());
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.Insights.Tab"));
    tab.setControl(comp);

    wDimLookupPreloadRatioThreshold = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    Control previous =
        addField(
            comp,
            middle,
            margin,
            null,
            "ExecutionMetricsProfileMetaEditor.DimLookupPreloadRatioThreshold.Label",
            wDimLookupPreloadRatioThreshold);

    wTargetReadRatioThreshold = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    previous =
        addField(
            comp,
            middle,
            margin,
            previous,
            "ExecutionMetricsProfileMetaEditor.TargetReadRatioThreshold.Label",
            wTargetReadRatioThreshold);

    wSortRowsRiskThreshold = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    previous =
        addField(
            comp,
            middle,
            margin,
            previous,
            "ExecutionMetricsProfileMetaEditor.SortRowsRiskThreshold.Label",
            wSortRowsRiskThreshold);

    wHighTransformDurationMs = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    addField(
        comp,
        middle,
        margin,
        previous,
        "ExecutionMetricsProfileMetaEditor.HighTransformDurationMs.Label",
        wHighTransformDurationMs);
  }

  private Control addField(
      Composite parent, int middle, int margin, Control previous, String labelKey, Text text) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, labelKey));
    FormData fdl = new FormData();
    if (previous != null) {
      fdl.top = new FormAttachment(previous, margin);
    } else {
      fdl.top = new FormAttachment(0, margin);
    }
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    label.setLayoutData(fdl);

    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    text.setLayoutData(fd);
    return text;
  }

  private Control addCheckbox(
      Composite parent, int middle, int margin, Control previous, String labelKey, Button button) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, labelKey));
    FormData fdl = new FormData();
    fdl.top = new FormAttachment(previous, margin);
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    label.setLayoutData(fdl);

    PropsUi.setLook(button);
    FormData fd = new FormData();
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    fd.left = new FormAttachment(middle, 0);
    button.setLayoutData(fd);
    return button;
  }

  private void populateCatalogConnections() {
    wCatalogConnection.removeAll();
    wCatalogConnection.add("");
    try {
      IHopMetadataProvider metadataProvider = HopGui.getInstance().getMetadataProvider();
      for (String name :
          DvSourceCatalogService.listEnabledCatalogConnectionNames(metadataProvider)) {
        wCatalogConnection.add(name);
      }
    } catch (HopException e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.Error.CatalogTitle"),
          e.getMessage(),
          e);
    }
  }

  private void populateDatabaseConnections() {
    wTargetDatabase.removeAll();
    wTargetDatabase.add("");
    try {
      IHopMetadataProvider metadataProvider = HopGui.getInstance().getMetadataProvider();
      for (String name : metadataProvider.getSerializer(DatabaseMeta.class).listObjectNames()) {
        wTargetDatabase.add(name);
      }
    } catch (HopException e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.Error.DatabaseTitle"),
          e.getMessage(),
          e);
    }
  }

  @Override
  public void setWidgetsContent() {
    ExecutionMetricsProfileMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wEnabled.setSelection(meta.isEnabled());
    wMetricsOutputFolder.setText(Const.NVL(meta.getMetricsOutputFolder(), ""));
    wCatalogConnection.setText(Const.NVL(meta.getDataCatalogConnection(), ""));
    wTargetDatabase.setText(Const.NVL(meta.getTargetDatabaseConnection(), ""));
    wOperationsSchema.setText(Const.NVL(meta.getOperationsSchemaOrDefault(), ""));
    wAutoCreateTables.setSelection(meta.isAutoCreateTables());
    wPublishCatalogDefinitions.setSelection(meta.isPublishCatalogDefinitions());
    wPublishDatabaseRows.setSelection(meta.isPublishDatabaseRows());
    wDimLookupPreloadRatioThreshold.setText(
        Long.toString(meta.getDimLookupPreloadRatioThreshold()));
    wTargetReadRatioThreshold.setText(Long.toString(meta.getTargetReadRatioThreshold()));
    wSortRowsRiskThreshold.setText(Long.toString(meta.getSortRowsRiskThreshold()));
    wHighTransformDurationMs.setText(Long.toString(meta.getHighTransformDurationMs()));
  }

  @Override
  public void getWidgetsContent(ExecutionMetricsProfileMeta meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    meta.setEnabled(wEnabled.getSelection());
    meta.setMetricsOutputFolder(wMetricsOutputFolder.getText());
    meta.setDataCatalogConnection(wCatalogConnection.getText());
    meta.setTargetDatabaseConnection(wTargetDatabase.getText());
    meta.setOperationsSchema(wOperationsSchema.getText());
    meta.setAutoCreateTables(wAutoCreateTables.getSelection());
    meta.setPublishCatalogDefinitions(wPublishCatalogDefinitions.getSelection());
    meta.setPublishDatabaseRows(wPublishDatabaseRows.getSelection());
    meta.setDimLookupPreloadRatioThreshold(
        parseThreshold(
            wDimLookupPreloadRatioThreshold,
            org.hopper.edw.datavault.metrics.LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD));
    meta.setTargetReadRatioThreshold(
        parseThreshold(
            wTargetReadRatioThreshold,
            org.hopper.edw.datavault.metrics.LoadRunInsightEngine
                .DEFAULT_TARGET_READ_RATIO_THRESHOLD));
    meta.setSortRowsRiskThreshold(
        parseThreshold(
            wSortRowsRiskThreshold,
            org.hopper.edw.datavault.metrics.LoadRunInsightEngine
                .DEFAULT_SORT_ROWS_RISK_THRESHOLD));
    meta.setHighTransformDurationMs(
        parseThreshold(
            wHighTransformDurationMs,
            org.hopper.edw.datavault.metrics.LoadRunInsightEngine
                .DEFAULT_HIGH_TRANSFORM_DURATION_MS));
  }

  private static long parseThreshold(Text widget, long defaultValue) {
    try {
      return Long.parseLong(widget.getText().trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public boolean setFocus() {
    if (wName == null || wName.isDisposed()) {
      return false;
    }
    return wName.setFocus();
  }

  @Override
  public Button[] createButtonsForButtonBar(Composite parent) {
    Button wGenerateSql = new Button(parent, SWT.PUSH);
    wGenerateSql.setText(
        BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.GenerateSql.Button"));
    wGenerateSql.setToolTipText(
        BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.GenerateSql.ToolTip"));
    wGenerateSql.addListener(SWT.Selection, e -> generateSql());
    return new Button[] {wGenerateSql};
  }

  /**
   * Opens Hop's SQL editor with dialect-specific CREATE statements for {@code load_run}, {@code
   * load_pipeline_metric}, and the other operations tables on the selected OPS connection.
   */
  private void generateSql() {
    ExecutionMetricsProfileMeta preview = new ExecutionMetricsProfileMeta();
    getWidgetsContent(preview);
    String connectionName =
        hopGui.getVariables().resolve(Const.NVL(preview.getTargetDatabaseConnection(), ""));
    if (Utils.isEmpty(connectionName)) {
      MessageBox box = new MessageBox(getShell(), SWT.OK | SWT.ICON_ERROR);
      box.setText(
          BaseMessages.getString(
              PKG, "ExecutionMetricsProfileMetaEditor.GenerateSql.MissingDatabase.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "ExecutionMetricsProfileMetaEditor.GenerateSql.MissingDatabase.Message"));
      box.open();
      return;
    }
    try {
      IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
      if (databaseMeta == null) {
        MessageBox box = new MessageBox(getShell(), SWT.OK | SWT.ICON_ERROR);
        box.setText(
            BaseMessages.getString(
                PKG, "ExecutionMetricsProfileMetaEditor.GenerateSql.MissingDatabase.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG,
                "ExecutionMetricsProfileMetaEditor.GenerateSql.UnknownDatabase.Message",
                connectionName));
        box.open();
        return;
      }
      String schema =
          hopGui.getVariables().resolve(Const.NVL(preview.getOperationsSchemaOrDefault(), ""));
      String sql = LoadRunMetricsDdlSupport.buildPreviewSql(databaseMeta, schema);
      SqlEditor sqlEditor =
          new SqlEditor(
              getShell(),
              SWT.NONE,
              hopGui.getVariables(),
              databaseMeta,
              DbCache.getInstance(),
              sql);
      sqlEditor.open();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "ExecutionMetricsProfileMetaEditor.GenerateSql.Error.Title"),
          BaseMessages.getString(
              PKG, "ExecutionMetricsProfileMetaEditor.GenerateSql.Error.Message"),
          e);
    }
  }
}
