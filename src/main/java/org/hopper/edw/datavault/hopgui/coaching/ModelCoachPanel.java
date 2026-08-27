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
package org.hopper.edw.datavault.hopgui.coaching;

import java.util.List;
import java.util.function.Supplier;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.hopper.edw.datavault.hopgui.GuiBusySupport;
import org.hopper.edw.datavault.hopgui.ModelCoachPanelAuditSupport;
import org.hopper.edw.datavault.metadata.coaching.CoachingInsight;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceNode;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceRef;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceResolver;
import org.hopper.edw.datavault.metadata.coaching.CoachingTargetUsage;
import org.hopper.edw.datavault.metadata.coaching.ICoachingModelAdapter;

/** Left-hand coach panel for DV/BV/DM model graphs. */
public class ModelCoachPanel extends Composite {

  public static final String TREE_DATA_KIND = "coachTreeKind";
  public static final String TREE_DATA_SOURCE = "coachSourceRef";
  public static final String TREE_DATA_TARGET = "coachTargetName";
  public static final String TREE_DATA_INSIGHT = "coachInsight";

  public static final String KIND_SOURCE = "source";
  public static final String KIND_TARGET = "target";
  public static final String KIND_INSIGHT = "insight";

  private static final Class<?> PKG = ModelCoachPanel.class;

  private final HopGui hopGui;
  private final IVariables variables;
  private final Supplier<String> modelFilenameSupplier;
  private final Supplier<ICoachingModelAdapter> adapterSupplier;
  private final Runnable modelChangeCallback;
  private final ICoachableModelGraph coachableGraph;

  private final Label titleLabel;
  private final Button addButton;
  private final Button removeButton;
  private final Button refreshButton;
  private final Button refreshOnOpenButton;
  private final Button mapButton;
  private final Button openTableButton;
  private final Button importButton;
  private final Button generateButton;
  private final Tree tree;

  public ModelCoachPanel(
      Composite parent,
      HopGui hopGui,
      IVariables variables,
      Supplier<String> modelFilenameSupplier,
      Supplier<ICoachingModelAdapter> adapterSupplier,
      Runnable modelChangeCallback,
      ICoachableModelGraph coachableGraph) {
    super(parent, SWT.BORDER);
    this.hopGui = hopGui;
    this.variables = variables;
    this.modelFilenameSupplier = modelFilenameSupplier;
    this.adapterSupplier = adapterSupplier;
    this.modelChangeCallback = modelChangeCallback;
    this.coachableGraph = coachableGraph;

    setLayout(new FormLayout());
    PropsUi.setLook(this);

    titleLabel = new Label(this, SWT.NONE);
    titleLabel.setText(BaseMessages.getString(PKG, "ModelCoachPanel.Title"));
    PropsUi.setLook(titleLabel);
    FormData fdTitle = new FormData();
    fdTitle.left = new FormAttachment(0, PropsUi.getMargin());
    fdTitle.top = new FormAttachment(0, PropsUi.getMargin());
    titleLabel.setLayoutData(fdTitle);

    addButton = createToolbarButton(BaseMessages.getString(PKG, "ModelCoachPanel.AddSources"));
    removeButton = createToolbarButton(BaseMessages.getString(PKG, "ModelCoachPanel.RemoveSource"));
    refreshButton = createToolbarButton(BaseMessages.getString(PKG, "ModelCoachPanel.Refresh"));
    mapButton = createToolbarButton(BaseMessages.getString(PKG, "ModelCoachPanel.MapToTarget"));
    openTableButton = createToolbarButton(BaseMessages.getString(PKG, "ModelCoachPanel.OpenTable"));
    importButton =
        createToolbarButton(BaseMessages.getString(PKG, "ModelCoachPanel.ImportSources"));
    generateButton =
        createToolbarButton(BaseMessages.getString(PKG, "ModelCoachPanel.GenerateFromSource"));
    generateButton.setToolTipText(
        BaseMessages.getString(PKG, "ModelCoachPanel.GenerateFromSource.Tooltip"));
    refreshOnOpenButton = new Button(this, SWT.CHECK);
    refreshOnOpenButton.setText(BaseMessages.getString(PKG, "ModelCoachPanel.RefreshOnOpen"));
    PropsUi.setLook(refreshOnOpenButton);
    refreshOnOpenButton.setSelection(
        ModelCoachPanelAuditSupport.retrieveRefreshOnModelLoad(modelFilenameSupplier.get()));
    refreshOnOpenButton.addListener(
        SWT.Selection,
        e ->
            ModelCoachPanelAuditSupport.storeRefreshOnModelLoad(
                modelFilenameSupplier.get(), refreshOnOpenButton.getSelection()));

    FormData fdAdd = new FormData();
    fdAdd.left = new FormAttachment(0, PropsUi.getMargin());
    fdAdd.top = new FormAttachment(titleLabel, PropsUi.getMargin());
    addButton.setLayoutData(fdAdd);

    FormData fdRemove = new FormData();
    fdRemove.left = new FormAttachment(addButton, PropsUi.getMargin());
    fdRemove.top = new FormAttachment(titleLabel, PropsUi.getMargin());
    removeButton.setLayoutData(fdRemove);

    FormData fdRefresh = new FormData();
    fdRefresh.left = new FormAttachment(removeButton, PropsUi.getMargin());
    fdRefresh.top = new FormAttachment(titleLabel, PropsUi.getMargin());
    refreshButton.setLayoutData(fdRefresh);

    FormData fdMap = new FormData();
    fdMap.left = new FormAttachment(0, PropsUi.getMargin());
    fdMap.top = new FormAttachment(addButton, PropsUi.getMargin());
    mapButton.setLayoutData(fdMap);

    FormData fdOpen = new FormData();
    fdOpen.left = new FormAttachment(mapButton, PropsUi.getMargin());
    fdOpen.top = new FormAttachment(addButton, PropsUi.getMargin());
    openTableButton.setLayoutData(fdOpen);

    FormData fdImport = new FormData();
    fdImport.left = new FormAttachment(openTableButton, PropsUi.getMargin());
    fdImport.top = new FormAttachment(addButton, PropsUi.getMargin());
    importButton.setLayoutData(fdImport);

    FormData fdGenerate = new FormData();
    fdGenerate.left = new FormAttachment(importButton, PropsUi.getMargin());
    fdGenerate.top = new FormAttachment(addButton, PropsUi.getMargin());
    generateButton.setLayoutData(fdGenerate);

    FormData fdRefreshOnOpen = new FormData();
    fdRefreshOnOpen.left = new FormAttachment(0, PropsUi.getMargin());
    fdRefreshOnOpen.top = new FormAttachment(mapButton, PropsUi.getMargin());
    refreshOnOpenButton.setLayoutData(fdRefreshOnOpen);

    tree = new Tree(this, SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(tree);
    FormData fdTree = new FormData();
    fdTree.left = new FormAttachment(0, 0);
    fdTree.top = new FormAttachment(refreshOnOpenButton, PropsUi.getMargin());
    fdTree.right = new FormAttachment(100, 0);
    fdTree.bottom = new FormAttachment(100, 0);
    tree.setLayoutData(fdTree);

    addButton.addListener(SWT.Selection, e -> openAddSourcesDialog());
    removeButton.addListener(SWT.Selection, e -> removeSelectedSource());
    refreshButton.addListener(SWT.Selection, e -> refresh());
    mapButton.addListener(SWT.Selection, e -> mapSelectedSource());
    openTableButton.addListener(SWT.Selection, e -> openSelectedTarget());
    importButton.addListener(SWT.Selection, e -> openImportSources());
    generateButton.addListener(SWT.Selection, e -> generateFromSourceModel());
    tree.addListener(SWT.Selection, e -> updateToolbarState());

    installDragSource();
    updateToolbarState();
    if (ModelCoachPanelAuditSupport.retrieveRefreshOnModelLoad(modelFilenameSupplier.get())) {
      refresh();
    } else {
      refreshSourcesOnly();
    }
  }

  public void refresh() {
    runRefresh(true);
  }

  public void refreshSourcesOnly() {
    runRefresh(false);
  }

  private void runRefresh(boolean includeValidation) {
    GuiBusySupport.showWhile(
        this,
        () -> {
          tree.removeAll();
          ICoachingModelAdapter adapter = adapterSupplier.get();
          if (adapter == null) {
            return;
          }
          try {
            IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
            List<CoachingSourceNode> nodes =
                includeValidation
                    ? CoachingSourceResolver.resolve(adapter, variables, metadataProvider)
                    : CoachingSourceResolver.resolveSourcesOnly(
                        adapter, variables, metadataProvider);
            populateTree(nodes, includeValidation);
          } catch (Exception e) {
            new ErrorDialog(
                hopGui.getShell(),
                BaseMessages.getString(PKG, "ModelCoachPanel.Title"),
                "Error building coach tree",
                e);
          }
          updateToolbarState();
        });
  }

  private void populateTree(List<CoachingSourceNode> nodes, boolean includeValidation) {
    if (nodes.isEmpty()) {
      TreeItem emptyItem = new TreeItem(tree, SWT.NONE);
      emptyItem.setText(BaseMessages.getString(PKG, "ModelCoachPanel.Empty"));
      return;
    }
    if (!includeValidation) {
      TreeItem hintItem = new TreeItem(tree, SWT.NONE);
      hintItem.setText(BaseMessages.getString(PKG, "ModelCoachPanel.PressRefreshToAnalyse"));
    }
    for (CoachingSourceNode node : nodes) {
      TreeItem sourceItem = new TreeItem(tree, SWT.NONE);
      CoachingSourceRef ref = node.getSourceRef();
      sourceItem.setText(node.getDisplayLabel() + " [" + node.getTypeLabel() + "]");
      sourceItem.setData(TREE_DATA_KIND, KIND_SOURCE);
      sourceItem.setData(TREE_DATA_SOURCE, ref);

      for (CoachingTargetUsage target : node.getTargetsOrEmpty()) {
        TreeItem targetItem = new TreeItem(sourceItem, SWT.NONE);
        targetItem.setText(
            BaseMessages.getString(
                PKG,
                "ModelCoachPanel.TargetSummary",
                target.getTableName(),
                target.getTableRole(),
                target.getSummary() == null ? "" : target.getSummary()));
        targetItem.setData(TREE_DATA_KIND, KIND_TARGET);
        targetItem.setData(TREE_DATA_TARGET, target.getTableName());
      }
      for (CoachingInsight insight : node.getInsightsOrEmpty()) {
        TreeItem insightItem = new TreeItem(sourceItem, SWT.NONE);
        insightItem.setText(formatInsight(insight));
        insightItem.setData(TREE_DATA_KIND, KIND_INSIGHT);
        insightItem.setData(TREE_DATA_INSIGHT, insight);
      }
    }
  }

  private String formatInsight(CoachingInsight insight) {
    if (insight.getSeverity() == CoachingInsight.Severity.ERROR) {
      return BaseMessages.getString(PKG, "ModelCoachPanel.Insight.Error", insight.getMessage());
    }
    if (insight.getSeverity() == CoachingInsight.Severity.WARNING) {
      return BaseMessages.getString(PKG, "ModelCoachPanel.Insight.Warning", insight.getMessage());
    }
    return BaseMessages.getString(PKG, "ModelCoachPanel.Insight.Info", insight.getMessage());
  }

  private Button createToolbarButton(String text) {
    Button button = new Button(this, SWT.PUSH);
    button.setText(text);
    PropsUi.setLook(button);
    return button;
  }

  private void updateToolbarState() {
    TreeItem item = getSelectedItem();
    String kind = item == null ? null : (String) item.getData(TREE_DATA_KIND);
    removeButton.setEnabled(KIND_SOURCE.equals(kind) && isRemovableSource(item));
    mapButton.setEnabled(KIND_SOURCE.equals(kind));
    openTableButton.setEnabled(KIND_TARGET.equals(kind) || KIND_INSIGHT.equals(kind));
    importButton.setEnabled(true);
    generateButton.setEnabled(
        coachableGraph != null && coachableGraph.canGenerateFromSourceModel());
  }

  private boolean isRemovableSource(TreeItem item) {
    if (item == null) {
      return false;
    }
    Object data = item.getData(TREE_DATA_SOURCE);
    if (!(data instanceof CoachingSourceRef ref)) {
      return false;
    }
    return !ref.isDerived();
  }

  private TreeItem getSelectedItem() {
    TreeItem[] selection = tree.getSelection();
    return selection == null || selection.length == 0 ? null : selection[0];
  }

  private CoachingSourceRef getSelectedSourceRef() {
    TreeItem item = getSelectedItem();
    if (item == null) {
      return null;
    }
    if (KIND_SOURCE.equals(item.getData(TREE_DATA_KIND))) {
      return (CoachingSourceRef) item.getData(TREE_DATA_SOURCE);
    }
    TreeItem parent = item.getParentItem();
    if (parent != null && KIND_SOURCE.equals(parent.getData(TREE_DATA_KIND))) {
      return (CoachingSourceRef) parent.getData(TREE_DATA_SOURCE);
    }
    return null;
  }

  private void openAddSourcesDialog() {
    ICoachingModelAdapter adapter = adapterSupplier.get();
    if (adapter == null) {
      return;
    }
    AddCoachingSourcesDialog dialog =
        new AddCoachingSourcesDialog(hopGui.getShell(), hopGui, adapter, variables);
    dialog.open(
        () -> {
          notifyModelChanged();
          refresh();
        });
  }

  private void removeSelectedSource() {
    CoachingSourceRef ref = getSelectedSourceRef();
    if (ref == null || ref.isDerived()) {
      return;
    }
    ICoachingModelAdapter adapter = adapterSupplier.get();
    if (adapter == null) {
      return;
    }
    adapter.getCoachingConfiguration().removeCoachingSource(ref);
    notifyModelChanged();
    refresh();
  }

  private void mapSelectedSource() {
    CoachingSourceRef ref = getSelectedSourceRef();
    if (ref == null) {
      return;
    }
    CoachingMappingDialogSupport.openMapDialog(hopGui, adapterSupplier.get(), ref, variables);
    refresh();
  }

  private void openSelectedTarget() {
    TreeItem item = getSelectedItem();
    if (item == null) {
      return;
    }
    String tableName = null;
    if (KIND_TARGET.equals(item.getData(TREE_DATA_KIND))) {
      tableName = (String) item.getData(TREE_DATA_TARGET);
    } else if (KIND_INSIGHT.equals(item.getData(TREE_DATA_KIND))) {
      CoachingInsight insight = (CoachingInsight) item.getData(TREE_DATA_INSIGHT);
      if (insight != null) {
        tableName = insight.getTargetTableName();
      }
    }
    if (tableName == null) {
      return;
    }
    ICoachingModelAdapter adapter = adapterSupplier.get();
    if (adapter != null) {
      adapter.openTableEditor(tableName);
    }
  }

  private void generateFromSourceModel() {
    if (coachableGraph == null || !coachableGraph.canGenerateFromSourceModel()) {
      return;
    }
    coachableGraph.generateFromSourceModel();
    refresh();
  }

  private void openImportSources() {
    ICoachingModelAdapter adapter = adapterSupplier.get();
    if (adapter == null) {
      return;
    }
    try {
      CoachingImportSupport.openImportMenu(hopGui, adapter, variables, this::refresh);
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "ModelCoachPanel.ImportSources"),
          e.getMessage(),
          e);
    }
  }

  private void notifyModelChanged() {
    if (modelChangeCallback != null) {
      modelChangeCallback.run();
    }
  }

  private void installDragSource() {
    DragSource dragSource = new DragSource(tree, DND.DROP_COPY | DND.DROP_MOVE);
    dragSource.setTransfer(new Transfer[] {TextTransfer.getInstance()});
    dragSource.addDragListener(
        new DragSourceAdapter() {
          @Override
          public void dragSetData(DragSourceEvent event) {
            CoachingSourceRef ref = getSelectedSourceRef();
            if (ref == null) {
              event.doit = false;
              return;
            }
            event.data = ref.identityKey();
          }
        });
  }
}
