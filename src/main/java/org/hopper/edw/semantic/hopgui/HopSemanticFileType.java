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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.GuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.HopFileTypeBase;
import org.apache.hop.ui.hopgui.file.HopFileTypePlugin;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.hopper.edw.datavault.hopgui.file.ExplorerPerspectiveTabSupport;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticModelPersistence;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

@HopFileTypePlugin(
    id = "HopFile-SemanticLayer-Plugin",
    name = "Semantic Layer",
    description = "Hopper semantic layer for presentation selections and warehouse queries",
    image = "semantic-layer.svg")
public class HopSemanticFileType extends HopFileTypeBase {

  public static final Class<?> PKG = HopSemanticFileType.class;
  public static final String FILE_TYPE_DESCRIPTION = "Semantic Layer";
  public static final String FILE_EXTENSION = SemanticModel.FILE_EXTENSION;
  public static final String XML_TAG = SemanticModel.XML_TAG;

  @Override
  public String getName() {
    return FILE_TYPE_DESCRIPTION;
  }

  @Override
  public String getDefaultFileExtension() {
    return FILE_EXTENSION;
  }

  @Override
  public String[] getFilterExtensions() {
    return new String[] {"*" + FILE_EXTENSION};
  }

  @Override
  public String[] getFilterNames() {
    return new String[] {"Semantic Layers"};
  }

  @Override
  public Properties getCapabilities() {
    Properties caps = new Properties();
    caps.setProperty(IHopFileType.CAPABILITY_NEW, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SAVE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SAVE_AS, "true");
    caps.setProperty(IHopFileType.CAPABILITY_CLOSE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_FILE_HISTORY, "true");
    return caps;
  }

  @Override
  public IHopFileTypeHandler openFile(HopGui hopGui, String filename, IVariables variables)
      throws HopException {
    try {
      filename = HopVfs.normalize(variables.resolve(filename));
      IHopFileTypeHandler existing =
          HopGui.getExplorerPerspective().findFileTypeHandlerByFilename(filename);
      if (existing != null) {
        HopGui.getExplorerPerspective().setActiveFileTypeHandler(existing);
        return existing;
      }
      SemanticModel model =
          SemanticModelPersistence.load(filename, hopGui.getMetadataProvider(), variables);
      return addToExplorer(hopGui, model, filename);
    } catch (Exception e) {
      throw new HopException("Error opening semantic layer '" + filename + "'", e);
    }
  }

  @Override
  public IHopFileTypeHandler newFile(HopGui hopGui, IVariables variables) throws HopException {
    try {
      SemanticModel model = new SemanticModel();
      model.setName("New semantic layer");
      return addToExplorer(hopGui, model, null);
    } catch (Exception e) {
      throw new HopException("Error creating semantic layer", e);
    }
  }

  private IHopFileTypeHandler addToExplorer(HopGui hopGui, SemanticModel model, String filename)
      throws Exception {
    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    CTabFolder targetFolder = ExplorerPerspectiveTabSupport.requireTabFolder(explorer);
    HopGuiSemanticLayerEditor editor =
        new HopGuiSemanticLayerEditor(targetFolder, hopGui, explorer, model, this);
    editor.setFilename(filename);
    CTabItem tabItem = new CTabItem(targetFolder, SWT.CLOSE);
    tabItem.setFont(GuiResource.getInstance().getFontDefault());
    tabItem.setText(Const.NVL(editor.getName(), "<>"));
    tabItem.setToolTipText(filename != null ? filename : "unsaved");
    tabItem.setImage(explorer.getFileTypeImage(this));
    tabItem.setData(editor);
    ExplorerPerspectiveTabSupport.registerTabItem(explorer, tabItem, editor);
    tabItem.setControl(editor);
    targetFolder.setSelection(tabItem);
    explorer.activate();
    return editor;
  }

  @Override
  public boolean isHandledBy(String filename, boolean checkContent) throws HopException {
    if (filename != null && filename.toLowerCase().endsWith(FILE_EXTENSION)) {
      return true;
    }
    if (checkContent) {
      try {
        Document document = XmlHandler.loadXmlFile(filename);
        Node node = XmlHandler.getSubNode(document, XML_TAG);
        return node != null;
      } catch (Exception e) {
        return false;
      }
    }
    return super.isHandledBy(filename, checkContent);
  }

  @Override
  public boolean supportsFile(IHasFilename metaObject) {
    return metaObject instanceof SemanticModel;
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    HopGui hopGui = HopGui.getInstance();
    GuiAction newAction =
        new GuiAction(
            "NewSemanticLayer",
            GuiActionType.Create,
            "Semantic layer",
            "Create a new Hopper semantic layer",
            "semantic-layer.svg",
            (shift, ctrl, params) -> {
              try {
                this.newFile(hopGui, hopGui.getVariables());
              } catch (Exception e) {
                new ErrorDialog(hopGui.getShell(), "Error", "Unable to create semantic layer", e);
              }
            });
    newAction.setCategory("File");
    newAction.setCategoryOrder("994");
    List<IGuiContextHandler> handlers = new ArrayList<>();
    handlers.add(new GuiContextHandler("NewSemanticLayer", List.of(newAction)));
    return handlers;
  }

  @Override
  public String getFileTypeImage() {
    return "semantic-layer.svg";
  }

  public void saveFile(HopGui hopGui, HopGuiSemanticLayerEditor editor) throws HopException {
    String filename = editor.getFilename();
    if (filename == null) {
      saveFileAs(hopGui, editor, null);
      return;
    }
    SemanticModelPersistence.save(editor.getModel(), filename, hopGui.getVariables());
    editor.clearChanged();
  }

  public void saveFileAs(HopGui hopGui, HopGuiSemanticLayerEditor editor, String filename)
      throws HopException {
    if (filename == null) {
      IVariables variables = hopGui.getVariables();
      String proposedName =
          Const.NVL(
                  editor.getModel() != null ? editor.getModel().getName() : null, "semantic-layer")
              + FILE_EXTENSION;
      String proposedFilename = variables.getVariable("user.home") + File.separator + proposedName;
      filename =
          BaseDialog.presentFileDialog(
              true,
              hopGui.getActiveShell(),
              null,
              variables,
              HopVfs.getFileObject(proposedFilename),
              getFilterExtensions(),
              getFilterNames(),
              true);
      if (filename == null) {
        return;
      }
    }
    filename = hopGui.getVariables().resolve(filename);
    SemanticModelPersistence.save(editor.getModel(), filename, hopGui.getVariables());
    editor.setFilename(filename);
    editor.clearChanged();
  }
}
