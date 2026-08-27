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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
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
import org.hopper.edw.datavault.hopgui.StandardProjectElementsOfferSupport;
import org.hopper.edw.datavault.hopgui.file.ExplorerPerspectiveTabSupport;
import org.hopper.edw.datavault.hopgui.search.HopGuiSourceModelSearchable;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

@HopFileTypePlugin(
    id = "HopFile-SourceModel-Plugin",
    name = "Source Model",
    description = "The source system model file information for the Hop GUI",
    image = "source-model.svg")
public class HopSourceModelFileType extends HopFileTypeBase {

  public static final Class<?> PKG = HopSourceModelFileType.class;
  public static final String SOURCE_MODEL_FILE_TYPE_DESCRIPTION = "Source Model";
  public static final String SOURCE_MODEL_FILE_EXTENSION = SourceModel.FILE_EXTENSION;
  public static final String XML_TAG = SourceModel.XML_TAG;

  @Override
  public String getName() {
    return SOURCE_MODEL_FILE_TYPE_DESCRIPTION;
  }

  @Override
  public String getDefaultFileExtension() {
    return SOURCE_MODEL_FILE_EXTENSION;
  }

  @Override
  public String[] getFilterExtensions() {
    return new String[] {"*" + SOURCE_MODEL_FILE_EXTENSION};
  }

  @Override
  public String[] getFilterNames() {
    return new String[] {"Source Models"};
  }

  @Override
  public Properties getCapabilities() {
    Properties caps = new Properties();
    caps.setProperty(IHopFileType.CAPABILITY_NEW, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SAVE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SAVE_AS, "true");
    caps.setProperty(IHopFileType.CAPABILITY_CLOSE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SELECT, "true");
    caps.setProperty(IHopFileType.CAPABILITY_COPY, "true");
    caps.setProperty(IHopFileType.CAPABILITY_PASTE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_CUT, "true");
    caps.setProperty(IHopFileType.CAPABILITY_DELETE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SEARCH, "true");
    return caps;
  }

  @Override
  public ISearchable createSearchable(
      String filename,
      String locationDescription,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    SourceModel model = SourceModelLoadSupport.load(filename, variables, metadataProvider);
    return new HopGuiSourceModelSearchable(locationDescription, model);
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

      Document document = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(document, XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }

      SourceModel model = new SourceModel();
      IHopMetadataProvider provider = hopGui.getMetadataProvider();
      XmlMetadataUtil.deSerializeFromXml(rootNode, SourceModel.class, model, provider);
      ModelConfigurationResolver.attach(model, provider);
      model.clearChanged();
      model.setFilename(filename);

      return addSourceModelToExplorer(hopGui, model, filename, this);
    } catch (Exception e) {
      throw new HopException("Error opening source model file '" + filename + "'", e);
    }
  }

  @Override
  public IHopFileTypeHandler newFile(HopGui hopGui, IVariables variables) throws HopException {
    try {
      SourceModel model = new SourceModel();
      model.setName(
          BaseMessages.getString(PKG, "HopSourceModelFileType.New.Text", "New Source Model"));
      ModelConfigurationResolver.attach(model, hopGui.getMetadataProvider());
      ModelConfigurationResolver.applyDefaultNameIfPresent(model, hopGui.getMetadataProvider());
      return addSourceModelToExplorer(hopGui, model, null, this);
    } catch (Exception e) {
      throw new HopException("Error creating new source model", e);
    }
  }

  public IHopFileTypeHandler addSourceModelToExplorer(
      HopGui hopGui, SourceModel model, String filename, HopSourceModelFileType fileType)
      throws Exception {
    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    CTabFolder targetFolder = ExplorerPerspectiveTabSupport.requireTabFolder(explorer);

    HopGuiSourceModelGraph graph =
        new HopGuiSourceModelGraph(targetFolder, hopGui, explorer, model, fileType);
    graph.setFilename(filename);

    CTabItem tabItem = new CTabItem(targetFolder, SWT.CLOSE);
    tabItem.setFont(GuiResource.getInstance().getFontDefault());
    tabItem.setText(Const.NVL(graph.getName(), "<>"));
    tabItem.setToolTipText(filename != null ? filename : "unsaved");
    tabItem.setImage(explorer.getFileTypeImage(fileType));
    graph.setData("KEY_TAB_FOLDER", targetFolder);
    // Under Hop Web, setControl can fire focus/selection → ExplorerPerspective.updateGui()
    // before the tab is fully wired. setData + register first so getActiveFileTypeHandler()
    // never sees a null tab payload.
    tabItem.setData(graph);
    ExplorerPerspectiveTabSupport.registerTabItem(explorer, tabItem, graph);
    tabItem.setControl(graph);

    targetFolder.setSelection(tabItem);
    explorer.activate();
    targetFolder
        .getDisplay()
        .asyncExec(() -> StandardProjectElementsOfferSupport.maybeOffer(hopGui, model));

    return graph;
  }

  @Override
  public boolean isHandledBy(String filename, boolean checkContent) throws HopException {
    if (checkContent) {
      try {
        Document doc = XmlHandler.loadXmlFile(filename);
        Node node = XmlHandler.getSubNode(doc, XML_TAG);
        return node != null;
      } catch (Exception e) {
        return false;
      }
    }
    return super.isHandledBy(filename, checkContent);
  }

  @Override
  public boolean supportsFile(IHasFilename metaObject) {
    return metaObject instanceof SourceModel;
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    HopGui hopGui = HopGui.getInstance();
    List<IGuiContextHandler> handlers = new ArrayList<>();

    GuiAction newAction =
        new GuiAction(
            "NewSourceModel",
            GuiActionType.Create,
            BaseMessages.getString(PKG, "HopSourceModelFileType.GuiAction.New.Name"),
            BaseMessages.getString(PKG, "HopSourceModelFileType.GuiAction.New.Tooltip"),
            "source-model.svg",
            (shift, ctrl, params) -> {
              try {
                this.newFile(hopGui, hopGui.getVariables());
              } catch (Exception e) {
                new ErrorDialog(
                    hopGui.getShell(),
                    BaseMessages.getString(PKG, "HopSourceModelFileType.ErrorDialog.Header"),
                    BaseMessages.getString(PKG, "HopSourceModelFileType.ErrorDialog.Message"),
                    e);
              }
            });
    newAction.setCategory("File");
    newAction.setCategoryOrder("994");

    handlers.add(new GuiContextHandler("NewSourceModel", List.of(newAction)));
    return handlers;
  }

  @Override
  public String getFileTypeImage() {
    return "source-model.svg";
  }

  public void saveFile(HopGui hopGui, HopGuiSourceModelGraph graph) throws HopException {
    String filename = graph.getFilename();
    if (filename == null) {
      saveFileAs(hopGui, graph, null);
      return;
    }
    saveModelToFile(graph.getModel(), filename, hopGui.getVariables());
    graph.clearChanged();
  }

  public void saveFileAs(HopGui hopGui, HopGuiSourceModelGraph graph, String filename)
      throws HopException {
    if (filename == null) {
      try {
        SourceModel model = graph.getModel();
        IVariables variables = hopGui.getVariables();
        String proposedName =
            Const.NVL(model != null ? model.getName() : null, "source-model")
                + SOURCE_MODEL_FILE_EXTENSION;
        String proposedFilename =
            variables.getVariable("user.home") + File.separator + proposedName;

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
      } catch (Exception e) {
        throw new HopException("Error showing save file dialog", e);
      }
    }
    filename = hopGui.getVariables().resolve(filename);
    saveModelToFile(graph.getModel(), filename, hopGui.getVariables());
    graph.setFilename(filename);
    graph.clearChanged();
  }

  private void saveModelToFile(SourceModel model, String filename, IVariables variables)
      throws HopException {
    try {
      ModelXmlWriteSupport.writeModelXml(XML_TAG, model, filename, variables);
    } catch (Exception e) {
      throw new HopException("Error saving source model to '" + filename + "'", e);
    }
  }
}
