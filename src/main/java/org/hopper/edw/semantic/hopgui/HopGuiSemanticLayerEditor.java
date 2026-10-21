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
package org.hopper.edw.semantic.hopgui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
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
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.edw.datavault.hopgui.file.dimensional.FactCrosstabGuiSupport;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmModelLoadSupport;
import org.hopper.edw.semantic.bind.HdmSemanticSeed;
import org.hopper.edw.semantic.model.SemanticAttribute;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticSelection;

/** Tree editor for a Hopper semantic layer (`.hsl`). */
public class HopGuiSemanticLayerEditor extends Composite implements IHopFileTypeHandler {

  private static final Class<?> PKG = HopGuiSemanticLayerEditor.class;

  private static final String KIND = "kind";
  private static final String KIND_ENTITY = "entity";
  private static final String KIND_ATTRIBUTE = "attribute";
  private static final String KIND_MEASURE = "measure";
  private static final String KIND_SELECTION = "selection";
  private static final String DATA_NAME = "name";
  private static final String DATA_ENTITY = "entity";

  private final HopGui hopGui;
  private final ExplorerPerspective perspective;
  private final HopSemanticFileType fileType;
  @Getter private final SemanticModel model;
  @Getter private String filename;

  private Tree tree;
  private ToolItem wOpenSelection;
  private ToolItem wDeleteSelection;
  private ToolItem wRenameSelection;
  private Label wlDimensionalModel;
  private static final String[] AGGREGATIONS =
      new String[] {
        AggregationMethod.SUM.name(),
        AggregationMethod.COUNT.name(),
        AggregationMethod.AVERAGE.name()
      };

  private Text wHeader;
  private Text wFormat;
  private Combo wHorizontalAlignment;
  private Combo wVerticalAlignment;
  private Combo wAggregation;
  private Button wTotals;
  private Text wDescription;
  private Object selected;
  private boolean loadingProperties;

  public HopGuiSemanticLayerEditor(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      SemanticModel model,
      HopSemanticFileType fileType) {
    super(parent, SWT.NONE);
    this.hopGui = hopGui;
    this.perspective = perspective;
    this.model = model != null ? model : new SemanticModel();
    this.fileType = fileType;
    PropsUi.setLook(this);
    setLayout(new FormLayout());
    int margin = PropsUi.getMargin();

    Button selectModel = new Button(this, SWT.PUSH);
    PropsUi.setLook(selectModel);
    selectModel.setText(
        BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.SelectDimensionalModel.Label"));
    selectModel.addListener(SWT.Selection, e -> selectDimensionalModel());
    FormData fdSelect = new FormData();
    fdSelect.left = new FormAttachment(0, margin);
    fdSelect.top = new FormAttachment(0, margin);
    selectModel.setLayoutData(fdSelect);

    Button create = new Button(this, SWT.PUSH);
    PropsUi.setLook(create);
    create.setText(
        BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.CreatePresentation.Label"));
    create.addListener(SWT.Selection, e -> createPresentation());
    FormData fdCreate = new FormData();
    fdCreate.left = new FormAttachment(selectModel, margin);
    fdCreate.top = new FormAttachment(0, margin);
    create.setLayoutData(fdCreate);

    wlDimensionalModel = new Label(this, SWT.LEFT);
    PropsUi.setLook(wlDimensionalModel);
    FormData fdModel = new FormData();
    fdModel.left = new FormAttachment(0, margin);
    fdModel.top = new FormAttachment(selectModel, margin);
    fdModel.right = new FormAttachment(100, -margin);
    wlDimensionalModel.setLayoutData(fdModel);
    refreshDimensionalModelLabel();

    SashForm sash = new SashForm(this, SWT.HORIZONTAL);
    PropsUi.setLook(sash);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, 0);
    fdSash.top = new FormAttachment(wlDimensionalModel, margin);
    fdSash.right = new FormAttachment(100, 0);
    fdSash.bottom = new FormAttachment(100, 0);
    sash.setLayoutData(fdSash);

    Composite left = new Composite(sash, SWT.NONE);
    left.setLayout(new FormLayout());
    PropsUi.setLook(left);

    ToolBar toolBar = new ToolBar(left, SWT.FLAT | SWT.HORIZONTAL);
    PropsUi.setLook(toolBar, Props.WIDGET_STYLE_TOOLBAR);
    toolItem(
        toolBar,
        "ui/images/new.svg",
        "HopGuiSemanticLayerEditor.Toolbar.New",
        this::createPresentation);
    wOpenSelection =
        toolItem(
            toolBar,
            "ui/images/open.svg",
            "HopGuiSemanticLayerEditor.Toolbar.Open",
            this::openSelectedSelection);
    wDeleteSelection =
        toolItem(
            toolBar,
            "ui/images/delete.svg",
            "HopGuiSemanticLayerEditor.Toolbar.Delete",
            this::deleteSelectedSelection);
    wRenameSelection =
        toolItem(
            toolBar,
            "ui/images/rename.svg",
            "HopGuiSemanticLayerEditor.Toolbar.Rename",
            this::renameSelectedSelection);
    FormData fdTool = new FormData();
    fdTool.left = new FormAttachment(0, 0);
    fdTool.top = new FormAttachment(0, 0);
    fdTool.right = new FormAttachment(100, 0);
    toolBar.setLayoutData(fdTool);

    tree = new Tree(left, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(tree);
    tree.addListener(SWT.Selection, e -> showSelection());
    tree.addListener(SWT.DefaultSelection, e -> openSelectedSelection());
    FormData fdTree = new FormData();
    fdTree.left = new FormAttachment(0, 0);
    fdTree.top = new FormAttachment(toolBar, 0);
    fdTree.right = new FormAttachment(100, 0);
    fdTree.bottom = new FormAttachment(100, 0);
    tree.setLayoutData(fdTree);

    Composite right = new Composite(sash, SWT.NONE);
    right.setLayout(new FormLayout());
    PropsUi.setLook(right);
    wHeader =
        labeledText(right, BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Header"), null);
    wFormat =
        labeledText(
            right, BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.FormatMask"), wHeader);
    wHorizontalAlignment =
        labeledCombo(
            right,
            BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.HorizontalAlignment"),
            wFormat,
            true,
            HHorizontalAlignment.LEFT.name(),
            HHorizontalAlignment.CENTER.name(),
            HHorizontalAlignment.RIGHT.name());
    wVerticalAlignment =
        labeledCombo(
            right,
            BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.VerticalAlignment"),
            wHorizontalAlignment,
            true,
            HVerticalAlignment.TOP.name(),
            HVerticalAlignment.MIDDLE.name(),
            HVerticalAlignment.BOTTOM.name());
    wAggregation =
        labeledCombo(
            right,
            BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Aggregation"),
            wVerticalAlignment,
            false,
            AGGREGATIONS);
    wAggregation.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Aggregation.Tooltip"));
    wTotals = new Button(right, SWT.CHECK);
    PropsUi.setLook(wTotals);
    wTotals.setText(BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Totals"));
    wTotals.setToolTipText(BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Totals.Tooltip"));
    FormData fdTotals = new FormData();
    fdTotals.left = new FormAttachment(0, PropsUi.getMargin());
    fdTotals.top = new FormAttachment(wAggregation, PropsUi.getMargin());
    wTotals.setLayoutData(fdTotals);
    wDescription =
        labeledText(
            right, BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Description"), wTotals);

    Listener applyAsTyped =
        e -> {
          if (!loadingProperties) {
            applyProperties();
          }
        };
    wHeader.addListener(SWT.Modify, applyAsTyped);
    wFormat.addListener(SWT.Modify, applyAsTyped);
    wHorizontalAlignment.addListener(SWT.Selection, applyAsTyped);
    wVerticalAlignment.addListener(SWT.Selection, applyAsTyped);
    wAggregation.addListener(SWT.Modify, applyAsTyped);
    wAggregation.addListener(SWT.Selection, applyAsTyped);
    wTotals.addListener(SWT.Selection, applyAsTyped);
    wDescription.addListener(SWT.Modify, applyAsTyped);

    sash.setWeights(new int[] {40, 60});
    refreshTree();
  }

  private Text labeledText(Composite parent, String label, Control above) {
    Label wl = fieldLabel(parent, label, above);
    Text text = new Text(parent, SWT.BORDER | SWT.SINGLE);
    PropsUi.setLook(text);
    attachField(text, wl);
    return text;
  }

  private Combo labeledCombo(
      Composite parent, String label, Control above, boolean readOnly, String... items) {
    Label wl = fieldLabel(parent, label, above);
    Combo combo = new Combo(parent, SWT.BORDER | (readOnly ? SWT.READ_ONLY : SWT.NONE));
    combo.setItems(items);
    PropsUi.setLook(combo);
    attachField(combo, wl);
    return combo;
  }

  private Label fieldLabel(Composite parent, String label, Control above) {
    Label wl = new Label(parent, SWT.LEFT);
    PropsUi.setLook(wl);
    wl.setText(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, PropsUi.getMargin());
    fdl.top =
        above == null
            ? new FormAttachment(0, PropsUi.getMargin())
            : new FormAttachment(above, PropsUi.getMargin());
    wl.setLayoutData(fdl);
    return wl;
  }

  private static void attachField(Control field, Label label) {
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, PropsUi.getMargin());
    fd.top = new FormAttachment(label, 2);
    fd.right = new FormAttachment(100, -PropsUi.getMargin());
    field.setLayoutData(fd);
  }

  public void setFilename(String filename) {
    this.filename = filename;
    refreshTab();
  }

  public void refreshTab() {
    try {
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
    } catch (Exception ignored) {
      // tab title updates are best-effort
    }
  }

  private void refreshTree() {
    tree.removeAll();
    TreeItem entitiesRoot = new TreeItem(tree, SWT.NONE);
    entitiesRoot.setText(BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Tree.Entities"));
    if (model.getEntities() != null) {
      for (SemanticEntity entity : model.getEntities()) {
        if (entity == null) {
          continue;
        }
        TreeItem entityItem = new TreeItem(entitiesRoot, SWT.NONE);
        entityItem.setText(entity.getName() + " (" + entity.resolveRole().name() + ")");
        entityItem.setData(KIND, KIND_ENTITY);
        entityItem.setData(DATA_NAME, entity.getName());
        for (SemanticAttribute attribute : entity.getAttributes()) {
          if (attribute == null) {
            continue;
          }
          TreeItem item = new TreeItem(entityItem, SWT.NONE);
          item.setText("A  " + attribute.getName());
          item.setData(KIND, KIND_ATTRIBUTE);
          item.setData(DATA_ENTITY, entity.getName());
          item.setData(DATA_NAME, attribute.getName());
        }
        for (SemanticMeasure measure : entity.getMeasures()) {
          if (measure == null) {
            continue;
          }
          TreeItem item = new TreeItem(entityItem, SWT.NONE);
          item.setText("M  " + measure.getName());
          item.setData(KIND, KIND_MEASURE);
          item.setData(DATA_ENTITY, entity.getName());
          item.setData(DATA_NAME, measure.getName());
        }
        entityItem.setExpanded(true);
      }
    }
    entitiesRoot.setExpanded(true);
    TreeItem selectionsRoot = new TreeItem(tree, SWT.NONE);
    selectionsRoot.setText(
        BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Tree.Selections"));
    if (model.getSelections() != null) {
      for (SemanticSelection selection : model.getSelections()) {
        if (selection == null) {
          continue;
        }
        TreeItem item = new TreeItem(selectionsRoot, SWT.NONE);
        item.setText(selection.getName());
        item.setData(KIND, KIND_SELECTION);
        item.setData(DATA_NAME, selection.getName());
      }
    }
    selectionsRoot.setExpanded(true);
    updateSelectionTools();
  }

  private ToolItem toolItem(ToolBar toolBar, String icon, String key, Runnable action) {
    ToolItem item = new ToolItem(toolBar, SWT.PUSH);
    item.setImage(
        GuiResource.getInstance()
            .getImage(icon, ConstUi.SMALL_ICON_SIZE, ConstUi.SMALL_ICON_SIZE));
    item.setToolTipText(BaseMessages.getString(PKG, key));
    item.addListener(SWT.Selection, e -> action.run());
    return item;
  }

  private void showSelection() {
    selected = null;
    loadingProperties = true;
    try {
      showSelectionFields();
    } finally {
      loadingProperties = false;
      updateSelectionTools();
    }
  }

  private void updateSelectionTools() {
    boolean selection = !Utils.isEmpty(selectedSelectionName());
    if (wOpenSelection != null && !wOpenSelection.isDisposed()) {
      wOpenSelection.setEnabled(selection);
    }
    if (wDeleteSelection != null && !wDeleteSelection.isDisposed()) {
      wDeleteSelection.setEnabled(selection);
    }
    if (wRenameSelection != null && !wRenameSelection.isDisposed()) {
      wRenameSelection.setEnabled(selection);
    }
  }

  private String selectedSelectionName() {
    if (tree == null || tree.isDisposed()) {
      return null;
    }
    TreeItem[] items = tree.getSelection();
    if (items == null || items.length == 0) {
      return null;
    }
    TreeItem item = items[0];
    if (!KIND_SELECTION.equals(String.valueOf(item.getData(KIND)))) {
      return null;
    }
    return (String) item.getData(DATA_NAME);
  }

  private void openSelectedSelection() {
    String name = selectedSelectionName();
    if (Utils.isEmpty(name)) {
      return;
    }
    try {
      FactCrosstabGuiSupport.openSelection(hopGui, model, name);
      refreshTree();
      selectTreeSelection(name);
      updateGui();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.OpenSelection"),
          e);
    }
  }

  private void deleteSelectedSelection() {
    String name = selectedSelectionName();
    SemanticSelection selection = model.findSelection(name);
    if (selection == null || model.getSelections() == null) {
      return;
    }
    MessageBox box = new MessageBox(hopGui.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
    box.setText(BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.DeleteSelection.Title"));
    box.setMessage(
        BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.DeleteSelection.Message", name));
    if (box.open() != SWT.YES) {
      return;
    }
    model.getSelections().remove(selection);
    setChanged();
    refreshTree();
  }

  private void renameSelectedSelection() {
    String name = selectedSelectionName();
    SemanticSelection selection = model.findSelection(name);
    if (selection == null) {
      return;
    }
    EnterStringDialog dialog =
        new EnterStringDialog(
            hopGui.getShell(),
            name,
            BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.RenameSelection.Title"),
            BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.RenameSelection.Message"));
    String renamed = dialog.open();
    if (Utils.isEmpty(renamed) || renamed.equals(name)) {
      return;
    }
    SemanticSelection existing = model.findSelection(renamed);
    if (existing != null && existing != selection) {
      MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
      box.setText(
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.RenameSelection.Duplicate.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "HopGuiSemanticLayerEditor.RenameSelection.Duplicate.Message", renamed));
      box.open();
      return;
    }
    selection.setName(renamed);
    setChanged();
    refreshTree();
    selectTreeSelection(renamed);
  }

  private void selectTreeSelection(String name) {
    if (tree == null || tree.isDisposed() || Utils.isEmpty(name)) {
      return;
    }
    for (TreeItem root : tree.getItems()) {
      for (TreeItem child : root.getItems()) {
        if (KIND_SELECTION.equals(String.valueOf(child.getData(KIND)))
            && name.equals(child.getData(DATA_NAME))) {
          tree.setSelection(child);
          showSelection();
          return;
        }
      }
    }
  }

  private void showSelectionFields() {
    TreeItem[] items = tree.getSelection();
    if (items == null || items.length == 0) {
      return;
    }
    TreeItem item = items[0];
    String kind = String.valueOf(item.getData(KIND));
    String entityName = (String) item.getData(DATA_ENTITY);
    String name = (String) item.getData(DATA_NAME);
    if (KIND_ATTRIBUTE.equals(kind)) {
      SemanticEntity entity = model.findEntity(entityName);
      SemanticAttribute attribute = entity != null ? entity.findAttribute(name) : null;
      selected = attribute;
      wHeader.setText(Const.NVL(attribute != null ? attribute.getHeader() : null, ""));
      wFormat.setText(Const.NVL(attribute != null ? attribute.getFormatMask() : null, ""));
      selectCombo(
          wHorizontalAlignment,
          attribute != null ? attribute.getHorizontalAlignment() : null,
          HHorizontalAlignment.CENTER.name());
      selectCombo(
          wVerticalAlignment,
          attribute != null ? attribute.getVerticalAlignment() : null,
          HVerticalAlignment.MIDDLE.name());
      wAggregation.setText("");
      wAggregation.setEnabled(false);
      wTotals.setSelection(false);
      wTotals.setEnabled(false);
      wDescription.setText(Const.NVL(attribute != null ? attribute.getDescription() : null, ""));
    } else if (KIND_MEASURE.equals(kind)) {
      SemanticEntity entity = model.findEntity(entityName);
      SemanticMeasure measure = entity != null ? entity.findMeasure(name) : null;
      selected = measure;
      wHeader.setText(Const.NVL(measure != null ? measure.getHeader() : null, ""));
      wFormat.setText(Const.NVL(measure != null ? measure.getFormatMask() : null, ""));
      selectCombo(
          wHorizontalAlignment,
          measure != null ? measure.getHorizontalAlignment() : null,
          HHorizontalAlignment.RIGHT.name());
      selectCombo(
          wVerticalAlignment,
          measure != null ? measure.getVerticalAlignment() : null,
          HVerticalAlignment.MIDDLE.name());
      selectCombo(
          wAggregation,
          measure != null ? measure.resolveAggregation().name() : AggregationMethod.SUM.name(),
          AggregationMethod.SUM.name());
      wAggregation.setEnabled(true);
      wTotals.setEnabled(true);
      wTotals.setSelection(measure == null || measure.isShowingTotals());
      wDescription.setText(Const.NVL(measure != null ? measure.getDescription() : null, ""));
    } else {
      wHeader.setText("");
      wFormat.setText("");
      wHorizontalAlignment.deselectAll();
      wVerticalAlignment.deselectAll();
      wAggregation.setText("");
      wAggregation.setEnabled(false);
      wTotals.setSelection(false);
      wTotals.setEnabled(false);
      wDescription.setText("");
    }
  }

  private static void selectCombo(Combo combo, String value, String fallback) {
    String wanted = Utils.isEmpty(value) ? fallback : value.trim();
    for (int i = 0; i < combo.getItemCount(); i++) {
      if (combo.getItem(i).equalsIgnoreCase(wanted)) {
        combo.select(i);
        return;
      }
    }
    if ((combo.getStyle() & SWT.READ_ONLY) == 0) {
      combo.setText(wanted);
    } else if (combo.getItemCount() > 0) {
      combo.select(0);
    }
  }

  private void applyProperties() {
    if (selected instanceof SemanticAttribute attribute) {
      attribute.setHeader(wHeader.getText());
      attribute.setFormatMask(wFormat.getText());
      attribute.setDescription(wDescription.getText());
      attribute.setHorizontalAlignment(blankToNull(wHorizontalAlignment.getText()));
      attribute.setVerticalAlignment(blankToNull(wVerticalAlignment.getText()));
      setChanged();
    } else if (selected instanceof SemanticMeasure measure) {
      measure.setHeader(wHeader.getText());
      measure.setFormatMask(wFormat.getText());
      measure.setDescription(wDescription.getText());
      measure.setHorizontalAlignment(blankToNull(wHorizontalAlignment.getText()));
      measure.setVerticalAlignment(blankToNull(wVerticalAlignment.getText()));
      measure.setAggregationMethod(parseAggregation(wAggregation.getText()));
      measure.setShowingTotals(wTotals.getSelection());
      setChanged();
    }
  }

  private static String blankToNull(String raw) {
    String trimmed = Const.NVL(raw, "").trim();
    return trimmed.isEmpty() ? null : trimmed.toUpperCase();
  }

  private static AggregationMethod parseAggregation(String raw) {
    try {
      return AggregationMethod.valueOf(Const.NVL(raw, "SUM").trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return AggregationMethod.SUM;
    }
  }

  private void refreshDimensionalModelLabel() {
    if (wlDimensionalModel == null || wlDimensionalModel.isDisposed()) {
      return;
    }
    String path = model.getDimensionalModelFilename();
    if (Utils.isEmpty(path)) {
      path = BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.DimensionalModel.None");
    }
    wlDimensionalModel.setText(
        BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.DimensionalModel.Label", path));
  }

  private String selectedFactEntityName() {
    TreeItem[] items = tree.getSelection();
    if (items == null || items.length == 0) {
      return null;
    }
    TreeItem item = items[0];
    String kind = String.valueOf(item.getData(KIND));
    if (KIND_ENTITY.equals(kind)) {
      return (String) item.getData(DATA_NAME);
    }
    if (KIND_ATTRIBUTE.equals(kind) || KIND_MEASURE.equals(kind)) {
      return (String) item.getData(DATA_ENTITY);
    }
    return null;
  }

  private void createPresentation() {
    try {
      FactCrosstabGuiSupport.openFromSemanticLayer(hopGui, model, selectedFactEntityName());
      refreshTree();
      updateGui();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.CreatePresentation"),
          e);
    }
  }

  private void selectDimensionalModel() {
    try {
      String filename =
          BaseDialog.presentFileDialog(
              false,
              hopGui.getShell(),
              null,
              hopGui.getVariables(),
              HopVfs.getFileObject(
                  Const.NVL(hopGui.getVariables().getVariable("PROJECT_HOME"), ".")),
              new HopDimensionalFileType().getFilterExtensions(),
              new HopDimensionalFileType().getFilterNames(),
              true);
      if (filename == null) {
        return;
      }
      DimensionalModel hdm =
          DmModelLoadSupport.loadDimensionalModel(
              filename, model.getFilename(), hopGui.getVariables(), hopGui.getMetadataProvider());
      SemanticModel seeded =
          HdmSemanticSeed.seed(hdm, hopGui.getVariables(), hopGui.getMetadataProvider());
      model.setName(seeded.getName());
      model.setDescription(seeded.getDescription());
      model.setDimensionalModelFilename(
          DmModelLoadSupport.toStoredModelPath(
              filename, model.getFilename(), hopGui.getVariables()));
      model.setTargetDatabase(seeded.getTargetDatabase());
      model.setEntities(seeded.getEntities());
      model.setRelationships(seeded.getRelationships());
      setChanged();
      refreshDimensionalModelLabel();
      refreshTree();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.SelectDimensionalModel"),
          e);
    }
  }

  @Override
  public Object getSubject() {
    return model;
  }

  @Override
  public String getName() {
    if (filename != null) {
      int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
      return slash >= 0 ? filename.substring(slash + 1) : filename;
    }
    return Const.NVL(model.getName(), "semantic-layer");
  }

  @Override
  public void setName(String name) {
    model.setName(name);
    setChanged();
  }

  @Override
  public IHopFileType getFileType() {
    return fileType;
  }

  @Override
  public void save() throws HopException {
    applyProperties();
    fileType.saveFile(hopGui, this);
  }

  @Override
  public void saveAs(String filename) throws HopException {
    applyProperties();
    fileType.saveFileAs(hopGui, this, filename);
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void pause() {}

  @Override
  public void resume() {}

  @Override
  public void preview() {}

  @Override
  public void debug() {}

  @Override
  public void redraw() {
    refreshTree();
  }

  public void setChanged() {
    model.setChanged();
    updateGui();
  }

  public void clearChanged() {
    model.clearChanged();
    updateGui();
  }

  @Override
  public void updateGui() {
    hopGui.handleFileCapabilities(fileType, this, hasChanged(), false, false);
    if (perspective != null) {
      perspective.updateTabItem(this);
      perspective.updateTreeItem(this);
    }
  }

  @Override
  public void selectAll() {}

  @Override
  public void unselectAll() {}

  @Override
  public void copySelectedToClipboard() {}

  @Override
  public void cutSelectedToClipboard() {}

  @Override
  public void deleteSelected() {}

  @Override
  public void pasteFromClipboard() {}

  @Override
  public boolean isCloseable() {
    try {
      if (hopGui != null && hopGui.fileDelegate != null && hopGui.fileDelegate.isClosing()) {
        return true;
      }
      if (!model.hasChanged()) {
        return true;
      }
      MessageBox box =
          new MessageBox(hopGui.getShell(), SWT.YES | SWT.NO | SWT.CANCEL | SWT.ICON_QUESTION);
      box.setText(BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.SavePrompt.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.SavePrompt.Message"));
      int answer = box.open();
      if (answer == SWT.CANCEL) {
        return false;
      }
      if (answer == SWT.YES) {
        save();
        if (hasChanged()) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.Save"),
          e);
      return false;
    }
  }

  @Override
  public void close() {
    if (perspective != null) {
      perspective.remove(this);
    }
  }

  @Override
  public boolean hasChanged() {
    return model.hasChanged();
  }

  @Override
  public void undo() {}

  @Override
  public void redo() {}

  @Override
  public Map<String, Object> getStateProperties() {
    return new HashMap<>();
  }

  @Override
  public void applyStateProperties(Map<String, Object> stateProperties) {}

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>();
  }

  @Override
  public IVariables getVariables() {
    return hopGui.getVariables();
  }
}
