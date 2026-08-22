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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.EdwDocsGuiPlugin;
import org.apache.hop.datavault.hopgui.StandardProjectElementsOfferSupport;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyDocsSupport.DocLink;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.EdwJourneyProblem;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyTreeNode.Kind;
import org.apache.hop.datavault.metrics.LoadRunDurationMetricsLoader;
import org.apache.hop.datavault.metrics.LoadRunDurationRun;
import org.apache.hop.datavault.metrics.LoadRunDurationSnapshot;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.quality.history.DataQualityHistoryReader.QualityRunSummary;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Contextual details and Open actions for the selected EDW Journey node. */
public final class EdwJourneyDetailsPanel {

  private static final Class<?> PKG = EdwJourneyDetailsPanel.class;

  private final Composite parent;
  private final ScrolledComposite scroll;
  private final Composite body;
  private final HopGui hopGui;
  private final Consumer<ResourceDefinitionGroupMeta> groupCreated;

  public EdwJourneyDetailsPanel(
      Composite parent, HopGui hopGui, Consumer<ResourceDefinitionGroupMeta> groupCreated) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.groupCreated = groupCreated;
    if (parent.getLayout() == null) {
      parent.setLayout(new FormLayout());
    }

    scroll = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
    PropsUi.setLook(scroll);
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    FormData fdScroll = new FormData();
    fdScroll.left = new FormAttachment(0, 0);
    fdScroll.top = new FormAttachment(0, 0);
    fdScroll.right = new FormAttachment(100, 0);
    fdScroll.bottom = new FormAttachment(100, 0);
    scroll.setLayoutData(fdScroll);

    body = new Composite(scroll, SWT.NONE);
    PropsUi.setLook(body);
    GridLayout grid = new GridLayout(1, false);
    grid.marginWidth = PropsUi.getMargin();
    grid.marginHeight = PropsUi.getMargin();
    grid.verticalSpacing = PropsUi.getMargin();
    body.setLayout(grid);
    scroll.setContent(body);
  }

  public void clear() {
    disposeChildren();
    relayout();
  }

  public void showNoGroup(boolean projectHasGroups) {
    disposeChildren();
    addText(
        BaseMessages.getString(
            PKG,
            projectHasGroups
                ? "EdwJourneyPerspective.Empty.NoGroup"
                : "EdwJourneyPerspective.Empty.NoGroupsInProject"));
    addDocumentationSection(EdwJourneyDocsSupport.linksForEmptyProject());
    Composite buttons = addButtonRow();
    addButton(
        buttons,
        BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.ConfigureEdw"),
        () -> StandardProjectElementsOfferSupport.openFromMenu(hopGui));
    addButton(
        buttons,
        BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.CreateGroup"),
        this::createGroup);
    relayout();
  }

  public void setNode(
      EdwJourneySnapshot snapshot,
      EdwJourneyOpsOverlay overlay,
      ResourceDefinitionGroupMeta group,
      EdwJourneyTreeNode node) {
    disposeChildren();
    if (node == null) {
      relayout();
      return;
    }
    EdwJourneyOpsOverlay ops = overlay != null ? overlay : EdwJourneyOpsOverlay.empty();

    addText(kindLabel(node.kind()));
    addTitle(Const.NVL(node.label(), ""));
    if (!Utils.isEmpty(node.description()) && !node.description().equals(node.label())) {
      addText(node.description());
    }
    addFact("EdwJourneyDetailsPanel.Path.Label", node.storedPath());
    addFact("EdwJourneyDetailsPanel.Catalog.Label", node.catalogConnection());
    if (node.catalogKey() != null) {
      addFact("EdwJourneyDetailsPanel.CatalogKey.Label", node.catalogKey().toString());
    }
    addFact("EdwJourneyDetailsPanel.ModelType.Label", node.modelType());
    addFact("EdwJourneyDetailsPanel.ActionType.Label", node.actionType());
    if (node.kind() == Kind.MODEL || node.kind() == Kind.SOURCE_MODEL) {
      addFact("EdwJourneyDetailsPanel.Tables.Label", String.valueOf(node.children().size()));
    }
    if (node.kind() == Kind.MODEL_TABLE && !Utils.isEmpty(node.tableName())) {
      addFact("EdwJourneyDetailsPanel.Table.Label", node.tableName());
    }

    addDocumentationSection(EdwJourneyDocsSupport.linksFor(node));

    addLastRunSection(node, ops);
    addDurationSection(node);
    addProblemsSection(node, ops);

    if (node.kind() == Kind.GROUP && snapshot != null && !snapshot.warnings().isEmpty()) {
      addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Warnings.Label"));
      Text warnings =
          new Text(body, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
      PropsUi.setLook(warnings);
      warnings.setText(String.join(Const.CR, snapshot.warnings()));
      GridData fd = new GridData(SWT.FILL, SWT.FILL, true, false);
      fd.heightHint = 80;
      warnings.setLayoutData(fd);
    }

    Composite buttons = addButtonRow();
    addOpenButtons(buttons, group, node);
    addGrowButtons(buttons, snapshot, group, node);
    relayout();
  }

  private void addDocumentationSection(List<DocLink> links) {
    if (links == null || links.isEmpty()) {
      return;
    }
    addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Docs.Title"));
    Composite row = addButtonRow();
    for (DocLink link : links) {
      addHelpButton(
          row,
          BaseMessages.getString(PKG, link.labelKey()),
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Docs.Tooltip", link.htmlPage()),
          link.htmlPage());
    }
  }

  private void addOpenButtons(
      Composite buttons, ResourceDefinitionGroupMeta group, EdwJourneyTreeNode node) {
    if (hasPrimaryAction(node)) {
      addButton(buttons, primaryButtonLabel(node), () -> openSafely(group, node));
    }
    if (group != null) {
      if (node.kind() == Kind.GROUP || node.kind() == Kind.STAGE) {
        addButton(
            buttons,
            BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.Group"),
            () -> openSafely(group, groupNode(group)));
        addButton(
            buttons,
            BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.HarvestHistory"),
            () -> EdwJourneyNavigationSupport.openHarvestHistory(hopGui, group));
        addButton(
            buttons,
            BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.ValidateSources"),
            () -> EdwJourneyNavigationSupport.openValidateSources(hopGui, group));
        addButton(
            buttons,
            BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.BrowseLineage"),
            () -> EdwJourneyNavigationSupport.openBrowseLineage(hopGui, group));
      }
      if (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.HARVEST) {
        addButton(
            buttons,
            BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.HarvestHistory"),
            () -> EdwJourneyNavigationSupport.openHarvestHistory(hopGui, group));
      }
      if (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.SCHEMA_GATE) {
        addButton(
            buttons,
            BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.ValidateSources"),
            () -> EdwJourneyNavigationSupport.openValidateSources(hopGui, group));
      }
      if (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.CATALOG_VERSION
          || node.kind() == Kind.CATALOG_VERSION) {
        addButton(
            buttons,
            BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.ListVersions"),
            () -> EdwJourneyNavigationSupport.openListVersions(hopGui, group));
      }
    }
  }

  private void addGrowButtons(
      Composite buttons,
      EdwJourneySnapshot snapshot,
      ResourceDefinitionGroupMeta group,
      EdwJourneyTreeNode node) {
    Runnable onChanged =
        () -> {
          if (groupCreated != null) {
            groupCreated.accept(group);
          }
        };
    if (node.kind() == Kind.GROUP) {
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.ConfigureEdw"),
          () -> EdwJourneyCreateSupport.configureEdw(hopGui));
    }
    if (node.kind() == Kind.STAGE && node.stage() == EdwJourneyStage.SOURCES) {
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.NewSourceModel"),
          () -> EdwJourneyCreateSupport.newSourceModel(hopGui));
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.ImportCatalog"),
          () -> EdwJourneyCreateSupport.importCatalogFeeds(hopGui, group, onChanged));
    }
    if (node.kind() == Kind.CATALOG_FEEDS) {
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.ImportCatalog"),
          () -> EdwJourneyCreateSupport.importCatalogFeeds(hopGui, group, onChanged));
    }
    if (node.kind() == Kind.STAGE && node.stage() == EdwJourneyStage.DATA_VAULT && group != null) {
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.NewDataVault"),
          () ->
              EdwJourneyCreateSupport.newWarehouseModel(
                  hopGui, group, EdwJourneyCreateSupport.LAYER_DATA_VAULT, onChanged));
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.AddDataVault"),
          () ->
              EdwJourneyCreateSupport.addExistingModels(
                  hopGui, group, EdwJourneyCreateSupport.LAYER_DATA_VAULT, onChanged));
      if (snapshot != null && !snapshot.sourceModels().isEmpty()) {
        addButton(
            buttons,
            BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.GenerateDv"),
            () -> EdwJourneyCreateSupport.generateDataVault(hopGui, group, snapshot, onChanged));
      }
    }
    if (node.kind() == Kind.STAGE
        && node.stage() == EdwJourneyStage.BUSINESS_VAULT
        && group != null) {
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.NewBusinessVault"),
          () ->
              EdwJourneyCreateSupport.newWarehouseModel(
                  hopGui, group, EdwJourneyCreateSupport.LAYER_BUSINESS_VAULT, onChanged));
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.AddBusinessVault"),
          () ->
              EdwJourneyCreateSupport.addExistingModels(
                  hopGui, group, EdwJourneyCreateSupport.LAYER_BUSINESS_VAULT, onChanged));
    }
    if (node.kind() == Kind.STAGE && node.stage() == EdwJourneyStage.DIMENSIONAL && group != null) {
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.NewDimensional"),
          () ->
              EdwJourneyCreateSupport.newWarehouseModel(
                  hopGui, group, EdwJourneyCreateSupport.LAYER_DIMENSIONAL, onChanged));
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.AddDimensional"),
          () ->
              EdwJourneyCreateSupport.addExistingModels(
                  hopGui, group, EdwJourneyCreateSupport.LAYER_DIMENSIONAL, onChanged));
    }
    if (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.SOURCE_QUALITY) {
      addButton(
          buttons,
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Grow.NewQualityRuleSet"),
          () -> EdwJourneyCreateSupport.newQualityRuleSet(hopGui));
    }
  }

  private static boolean hasPrimaryAction(EdwJourneyTreeNode node) {
    return switch (node.kind()) {
      case SOURCE_MODEL,
              MODEL,
              MODEL_TABLE,
              CATALOG_FEED,
              GROUP,
              WORKFLOW,
              WORKFLOW_ACTION,
              OUTPUT_FILE,
              CATALOG_VERSION ->
          true;
      case CONTROL -> node.control() != null && node.control() != EdwJourneyControl.SOURCE_QUALITY;
      default -> false;
    };
  }

  private String primaryButtonLabel(EdwJourneyTreeNode node) {
    return switch (node.kind()) {
      case SOURCE_MODEL, MODEL, MODEL_TABLE ->
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.Model");
      case CATALOG_FEED -> BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.Catalog");
      case GROUP -> BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.Group");
      case WORKFLOW, WORKFLOW_ACTION, OUTPUT_FILE ->
          BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.File");
      case CONTROL ->
          switch (node.control() != null ? node.control() : EdwJourneyControl.HARVEST) {
            case HARVEST ->
                BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.HarvestHistory");
            case SCHEMA_GATE ->
                BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.ValidateSources");
            case CATALOG_VERSION ->
                BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.ListVersions");
            case SOURCE_QUALITY -> BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.File");
          };
      default -> BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Open.File");
    };
  }

  private void openSafely(ResourceDefinitionGroupMeta group, EdwJourneyTreeNode node) {
    try {
      EdwJourneyNavigationSupport.openPrimary(hopGui, group, node);
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Open.Title"),
          BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Open.Message"),
          e);
    }
  }

  private void addLastRunSection(EdwJourneyTreeNode node, EdwJourneyOpsOverlay overlay) {
    if (!Utils.isEmpty(overlay.unavailableReason())
        && (node.kind() == Kind.GROUP
            || node.kind() == Kind.CONTROL
            || node.stage() == EdwJourneyStage.TARGET_QUALITY
            || node.stage() == EdwJourneyStage.CONTROLS)) {
      addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.LastRun.Unavailable"));
      addText(overlay.unavailableReason());
      return;
    }
    if (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.HARVEST) {
      addHarvestFacts(overlay.harvest());
      return;
    }
    if (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.SOURCE_QUALITY) {
      addQualityFacts(overlay.sourceQuality(), "EdwJourneyDetailsPanel.LastRun.SourceQuality");
      return;
    }
    if (node.stage() == EdwJourneyStage.TARGET_QUALITY && node.kind() == Kind.STAGE) {
      addQualityFacts(overlay.targetQuality(), "EdwJourneyDetailsPanel.LastRun.TargetQuality");
      return;
    }
    if (node.kind() == Kind.GROUP
        || node.stage() == EdwJourneyStage.ORCHESTRATION
        || node.kind() == Kind.WORKFLOW) {
      addLoadFacts(overlay.load());
    }
    if (node.kind() == Kind.MODEL) {
      String name =
          Utils.isEmpty(node.label())
              ? EdwJourneyDisplayNames.basenameWithoutExtension(node.storedPath())
              : stripDecoration(node.label());
      addModelLoadFacts(
          EdwJourneyOpsOverlayLoader.modelLoad(
              overlay, name, EdwJourneyOpsOverlayLoader.opsModelType(node.modelType())));
    }
  }

  private void addHarvestFacts(HarvestRunSummary harvest) {
    addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.LastRun.Harvest"));
    if (harvest == null) {
      addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.LastRun.Never"));
      return;
    }
    addFact(
        "EdwJourneyDetailsPanel.LastRun.When",
        EdwJourneyOpsDecorations.formatWhen(harvest.finishedAt()));
    addFact("EdwJourneyDetailsPanel.LastRun.Status", harvest.status());
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Changes",
        harvest.changeCount() != null ? String.valueOf(harvest.changeCount()) : null);
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Subjects",
        harvest.subjectCount() != null ? String.valueOf(harvest.subjectCount()) : null);
    addFact("EdwJourneyDetailsPanel.LastRun.RunId", harvest.harvestRunId());
  }

  private void addQualityFacts(QualityRunSummary quality, String titleKey) {
    addText(BaseMessages.getString(PKG, titleKey));
    if (quality == null) {
      addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.LastRun.Never"));
      return;
    }
    addFact(
        "EdwJourneyDetailsPanel.LastRun.When",
        EdwJourneyOpsDecorations.formatWhen(quality.measuredAt()));
    addFact("EdwJourneyDetailsPanel.LastRun.Status", EdwJourneyOpsDecorations.quality(quality));
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Findings",
        quality.findingCount() != null ? String.valueOf(quality.findingCount()) : null);
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Blocking",
        quality.blockingCount() != null ? String.valueOf(quality.blockingCount()) : null);
    addFact("EdwJourneyDetailsPanel.LastRun.RunId", quality.qualityRunId());
  }

  private void addLoadFacts(LoadOverviewSummary load) {
    addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.LastRun.Load"));
    if (load == null) {
      addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.LastRun.Never"));
      return;
    }
    addFact(
        "EdwJourneyDetailsPanel.LastRun.When",
        EdwJourneyOpsDecorations.formatWhen(load.finishedAt()));
    addFact("EdwJourneyDetailsPanel.LastRun.Workflow", load.rootWorkflowName());
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Duration",
        EdwJourneyOpsDecorations.formatDuration(load.durationMs()));
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Models",
        load.modelCount() != null ? String.valueOf(load.modelCount()) : null);
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Errors",
        load.errors() != null ? String.valueOf(load.errors()) : null);
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Status",
        Boolean.FALSE.equals(load.success()) ? "failed" : "ok");
  }

  private void addModelLoadFacts(ModelLoadSummary summary) {
    addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.LastRun.Model"));
    if (summary == null) {
      addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.LastRun.Never"));
      return;
    }
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Duration",
        EdwJourneyOpsDecorations.formatDuration(summary.durationMs()));
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Errors",
        summary.errors() != null ? String.valueOf(summary.errors()) : null);
    addFact(
        "EdwJourneyDetailsPanel.LastRun.Status",
        Boolean.FALSE.equals(summary.success()) ? "failed" : "ok");
  }

  private void addDurationSection(EdwJourneyTreeNode node) {
    if (node.kind() != Kind.MODEL || hopGui == null) {
      return;
    }
    String modelName = stripDecoration(node.label());
    if (Utils.isEmpty(modelName)) {
      modelName = EdwJourneyDisplayNames.basenameWithoutExtension(node.storedPath());
    }
    String opsType = EdwJourneyOpsOverlayLoader.opsModelType(node.modelType());
    if (Utils.isEmpty(opsType)) {
      return;
    }
    List<String> tableNames = new ArrayList<>();
    for (EdwJourneyTreeNode child : node.children()) {
      if (child != null && !Utils.isEmpty(child.tableName())) {
        tableNames.add(child.tableName());
      }
    }
    LoadRunDurationSnapshot duration =
        LoadRunDurationMetricsLoader.load(
            modelName, opsType, tableNames, hopGui.getMetadataProvider(), hopGui.getVariables(), 8);
    if (duration == null || duration.getStatus() != LoadRunDurationSnapshot.Status.LOADED) {
      return;
    }
    addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Duration.Title"));
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Duration.Column.When"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Duration.Column.Status"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Duration.Column.Duration"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    TableView view =
        new TableView(
            hopGui.getVariables(),
            body,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE,
            columns,
            Math.max(1, duration.getRuns().size()),
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    GridData grid = new GridData(SWT.FILL, SWT.FILL, true, false);
    grid.heightHint = tableViewHeightHint(120);
    view.setLayoutData(grid);
    view.clearAll(false);
    int row = 0;
    for (int i = 0; i < duration.getRuns().size(); i++) {
      LoadRunDurationRun run = duration.getRuns().get(i);
      org.eclipse.swt.widgets.TableItem item;
      if (row == 0 && view.nrNonEmpty() > 0) {
        item = view.table.getItem(0);
      } else {
        item = new org.eclipse.swt.widgets.TableItem(view.table, SWT.NONE);
      }
      long max = 0L;
      for (String table : duration.getTableNames()) {
        max = Math.max(max, duration.durationMs(table, i));
      }
      item.setText(1, Const.NVL(EdwJourneyOpsDecorations.formatWhen(run.getFinishedAt()), ""));
      item.setText(2, run.isSuccess() ? "ok" : "failed");
      item.setText(3, Const.NVL(EdwJourneyOpsDecorations.formatDuration(max), ""));
      row++;
    }
    view.setRowNums();
    view.optWidth(true);
    view.optimizeTableView();
  }

  private void addProblemsSection(EdwJourneyTreeNode node, EdwJourneyOpsOverlay overlay) {
    List<EdwJourneyProblem> problems = problemsFor(node, overlay);
    if (problems.isEmpty()) {
      return;
    }
    addText(BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Problems.Title"));
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Problems.Column.Severity"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Problems.Column.Source"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Problems.Column.Subject"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Problems.Column.Message"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    TableView view =
        new TableView(
            hopGui.getVariables(),
            body,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(1, problems.size()),
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    GridData grid = new GridData(SWT.FILL, SWT.FILL, true, false);
    grid.heightHint = tableViewHeightHint(Math.min(220, 28 + problems.size() * 22));
    view.setLayoutData(grid);
    view.clearAll(false);
    for (int i = 0; i < problems.size(); i++) {
      EdwJourneyProblem problem = problems.get(i);
      org.eclipse.swt.widgets.TableItem item;
      if (i == 0 && view.table.getItemCount() > 0) {
        item = view.table.getItem(0);
      } else {
        item = new org.eclipse.swt.widgets.TableItem(view.table, SWT.NONE);
      }
      item.setText(1, Const.NVL(problem.severity(), ""));
      item.setText(2, Const.NVL(problem.source(), ""));
      item.setText(3, Const.NVL(problem.subject(), ""));
      item.setText(4, Const.NVL(problem.message(), ""));
    }
    view.setRowNums();
    view.optWidth(true);
  }

  private static List<EdwJourneyProblem> problemsFor(
      EdwJourneyTreeNode node, EdwJourneyOpsOverlay overlay) {
    if (overlay == null || overlay.problems().isEmpty()) {
      return List.of();
    }
    if (node.kind() == Kind.GROUP
        || node.stage() == EdwJourneyStage.CONTROLS
        || (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.HARVEST)
        || (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.SOURCE_QUALITY)
        || node.stage() == EdwJourneyStage.TARGET_QUALITY
        || node.kind() == Kind.WORKFLOW
        || node.stage() == EdwJourneyStage.ORCHESTRATION) {
      String sourceFilter = null;
      if (node.kind() == Kind.CONTROL && node.control() == EdwJourneyControl.HARVEST) {
        sourceFilter = "harvest";
      } else if (node.kind() == Kind.CONTROL
          && node.control() == EdwJourneyControl.SOURCE_QUALITY) {
        sourceFilter = "quality";
      } else if (node.stage() == EdwJourneyStage.TARGET_QUALITY) {
        sourceFilter = "quality";
      } else if (node.kind() == Kind.WORKFLOW || node.stage() == EdwJourneyStage.ORCHESTRATION) {
        sourceFilter = "load";
      }
      if (sourceFilter == null) {
        return overlay.problems();
      }
      List<EdwJourneyProblem> filtered = new ArrayList<>();
      for (EdwJourneyProblem problem : overlay.problems()) {
        if (sourceFilter.equals(problem.source())) {
          filtered.add(problem);
        }
      }
      return filtered;
    }
    return List.of();
  }

  private static String stripDecoration(String label) {
    if (Utils.isEmpty(label)) {
      return label;
    }
    int sep = label.indexOf("  ·  ");
    return sep > 0 ? label.substring(0, sep) : label;
  }

  private void createGroup() {
    ResourceDefinitionGroupMeta created = EdwJourneyNavigationSupport.newGroup(hopGui);
    if (created != null && groupCreated != null) {
      groupCreated.accept(created);
    }
  }

  private Label addTitle(String text) {
    Label label = new Label(body, SWT.WRAP);
    PropsUi.setLook(label);
    label.setText(Const.NVL(text, ""));
    GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
    label.setLayoutData(data);
    return label;
  }

  private Label addText(String text) {
    Label label = new Label(body, SWT.WRAP);
    PropsUi.setLook(label);
    label.setText(Const.NVL(text, ""));
    label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return label;
  }

  private void addFact(String labelKey, String value) {
    if (Utils.isEmpty(value)) {
      return;
    }
    addText(BaseMessages.getString(PKG, labelKey) + ": " + value);
  }

  private Composite addButtonRow() {
    Composite row = new Composite(body, SWT.NONE);
    PropsUi.setLook(row);
    RowLayout layout = new RowLayout(SWT.HORIZONTAL);
    layout.wrap = true;
    layout.spacing = PropsUi.getMargin();
    row.setLayout(layout);
    row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return row;
  }

  private static int tableViewHeightHint(int baseHeight) {
    return (int) (baseHeight * 4 * PropsUi.getNativeZoomFactor());
  }

  private void relayout() {
    if (body == null || body.isDisposed()) {
      return;
    }
    body.layout(true, true);
    Point size = body.computeSize(SWT.DEFAULT, SWT.DEFAULT);
    scroll.setMinSize(size);
    if (parent != null && !parent.isDisposed()) {
      parent.layout(true, true);
    }
  }

  private void addButton(Composite parent, String text, Runnable action) {
    Button button = new Button(parent, SWT.PUSH);
    PropsUi.setLook(button);
    button.setText(text);
    button.addListener(SWT.Selection, e -> action.run());
  }

  private void addHelpButton(Composite parent, String text, String tooltip, String htmlPage) {
    Button button = new Button(parent, SWT.PUSH);
    PropsUi.setLook(button);
    button.setImage(GuiResource.getInstance().getImageHelp());
    button.setText(text);
    if (!Utils.isEmpty(tooltip)) {
      button.setToolTipText(tooltip);
    }
    button.addListener(SWT.Selection, e -> EdwDocsGuiPlugin.openHtml(hopGui, htmlPage));
  }

  private void disposeChildren() {
    for (Control child : body.getChildren()) {
      if (child != null && !child.isDisposed()) {
        child.dispose();
      }
    }
  }

  private static String kindLabel(Kind kind) {
    if (kind == null) {
      return "";
    }
    return BaseMessages.getString(PKG, "EdwJourneyDetailsPanel.Kind." + kind.name());
  }

  private static EdwJourneyTreeNode groupNode(ResourceDefinitionGroupMeta group) {
    return EdwJourneyTreeNode.builder(
            Kind.GROUP, EdwJourneyIds.group(group.getName()), group.getName())
        .catalogConnection(group.getDataCatalogConnection())
        .build();
  }
}
