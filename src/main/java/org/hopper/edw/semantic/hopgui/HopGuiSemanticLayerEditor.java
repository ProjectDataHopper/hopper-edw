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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.hopper.core.AggregationMethod;
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
  private Label wlDimensionalModel;
  private Text wHeader;
  private Text wFormat;
  private Text wAggregation;
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

    tree = new Tree(sash, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    PropsUi.setLook(tree);
    tree.addListener(SWT.Selection, e -> showSelection());

    Composite right = new Composite(sash, SWT.NONE);
    right.setLayout(new FormLayout());
    PropsUi.setLook(right);
    wHeader =
        labeledText(right, BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Header"), null);
    wFormat =
        labeledText(
            right, BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.FormatMask"), wHeader);
    wAggregation =
        labeledText(
            right, BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Aggregation"), wFormat);
    wDescription =
        labeledText(
            right,
            BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Description"),
            wAggregation);

    Listener applyAsTyped =
        e -> {
          if (!loadingProperties) {
            applyProperties();
          }
        };
    wHeader.addListener(SWT.Modify, applyAsTyped);
    wFormat.addListener(SWT.Modify, applyAsTyped);
    wAggregation.addListener(SWT.Modify, applyAsTyped);
    wDescription.addListener(SWT.Modify, applyAsTyped);

    sash.setWeights(new int[] {40, 60});
    refreshTree();
  }

  private Text labeledText(Composite parent, String label, Text above) {
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
    Text text = new Text(parent, SWT.BORDER | SWT.SINGLE);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, PropsUi.getMargin());
    fd.top = new FormAttachment(wl, 2);
    fd.right = new FormAttachment(100, -PropsUi.getMargin());
    text.setLayoutData(fd);
    return text;
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
  }

  private void showSelection() {
    selected = null;
    loadingProperties = true;
    try {
      showSelectionFields();
    } finally {
      loadingProperties = false;
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
      wAggregation.setText("");
      wDescription.setText(Const.NVL(attribute != null ? attribute.getDescription() : null, ""));
    } else if (KIND_MEASURE.equals(kind)) {
      SemanticEntity entity = model.findEntity(entityName);
      SemanticMeasure measure = entity != null ? entity.findMeasure(name) : null;
      selected = measure;
      wHeader.setText(Const.NVL(measure != null ? measure.getHeader() : null, ""));
      wFormat.setText(Const.NVL(measure != null ? measure.getFormatMask() : null, ""));
      wAggregation.setText(
          measure != null ? measure.resolveAggregation().name() : AggregationMethod.SUM.name());
      wDescription.setText(Const.NVL(measure != null ? measure.getDescription() : null, ""));
    } else {
      wHeader.setText("");
      wFormat.setText("");
      wAggregation.setText("");
      wDescription.setText("");
    }
  }

  private void applyProperties() {
    if (selected instanceof SemanticAttribute attribute) {
      attribute.setHeader(wHeader.getText());
      attribute.setFormatMask(wFormat.getText());
      attribute.setDescription(wDescription.getText());
      setChanged();
    } else if (selected instanceof SemanticMeasure measure) {
      measure.setHeader(wHeader.getText());
      measure.setFormatMask(wFormat.getText());
      measure.setDescription(wDescription.getText());
      measure.setAggregationMethod(parseAggregation(wAggregation.getText()));
      setChanged();
    }
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
      try {
        save();
      } catch (Exception e) {
        new ErrorDialog(
            hopGui.getShell(),
            BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.Title"),
            BaseMessages.getString(PKG, "HopGuiSemanticLayerEditor.Error.Save"),
            e);
        return false;
      }
    }
    return true;
  }

  @Override
  public void close() {}

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
