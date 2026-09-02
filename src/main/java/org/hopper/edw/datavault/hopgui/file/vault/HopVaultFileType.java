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
package org.hopper.edw.datavault.hopgui.file.vault;

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
import org.hopper.edw.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.hopper.edw.datavault.hopgui.search.HopGuiDataVaultModelSearchable;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

@HopFileTypePlugin(
    id = "HopFile-Vault-Plugin",
    name = "Data Vault Model",
    description = "The Data Vault model file information for the Hop GUI",
    image = "datavault-model.svg")
public class HopVaultFileType extends HopFileTypeBase {

  public static final Class<?> PKG = HopVaultFileType.class; // for i18n
  public static final String VAULT_FILE_TYPE_DESCRIPTION = "Data Vault Model";
  public static final String VAULT_FILE_EXTENSION = ".hdv";
  public static final String XML_TAG = "data-vault-model";

  public HopVaultFileType() {
    // nothing
  }

  @Override
  public String getName() {
    return VAULT_FILE_TYPE_DESCRIPTION;
  }

  @Override
  public String getDefaultFileExtension() {
    return VAULT_FILE_EXTENSION;
  }

  @Override
  public String[] getFilterExtensions() {
    return new String[] {"*" + VAULT_FILE_EXTENSION};
  }

  @Override
  public String[] getFilterNames() {
    return new String[] {"Data Vault Models"};
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
    caps.setProperty(IHopFileType.CAPABILITY_SNAP_TO_GRID, "true");
    caps.setProperty(IHopFileType.CAPABILITY_ALIGN_LEFT, "true");
    caps.setProperty(IHopFileType.CAPABILITY_ALIGN_RIGHT, "true");
    caps.setProperty(IHopFileType.CAPABILITY_ALIGN_TOP, "true");
    caps.setProperty(IHopFileType.CAPABILITY_ALIGN_BOTTOM, "true");
    caps.setProperty(IHopFileType.CAPABILITY_DISTRIBUTE_HORIZONTAL, "true");
    caps.setProperty(IHopFileType.CAPABILITY_DISTRIBUTE_VERTICAL, "true");
    caps.setProperty(IHopFileType.CAPABILITY_EXPORT_TO_SVG, "true");
    caps.setProperty(IHopFileType.CAPABILITY_FILE_HISTORY, "true");
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
    DataVaultModel model =
        ResourceDefinitionGroupResolver.loadDataVaultModel(filename, variables, metadataProvider);
    return new HopGuiDataVaultModelSearchable(locationDescription, model);
  }

  @Override
  public IHopFileTypeHandler openFile(HopGui hopGui, String filename, IVariables variables)
      throws HopException {
    try {
      filename = HopVfs.normalize(variables.resolve(filename));

      // Check if already open
      IHopFileTypeHandler existing =
          HopGui.getExplorerPerspective().findFileTypeHandlerByFilename(filename);
      if (existing != null) {
        HopGui.getExplorerPerspective().setActiveFileTypeHandler(existing);
        return existing;
      }

      // Load the model using XML metadata util
      Document document = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
      if (rootNode == null) {
        // try direct
        rootNode = document.getDocumentElement();
      }

      DataVaultModel model = new DataVaultModel();
      IHopMetadataProvider provider = hopGui.getMetadataProvider();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, provider);
      ModelConfigurationResolver.attach(model, provider);
      model.clearChanged(); // freshly loaded is not changed

      // Set the (current) filename on the model so that getName() can derive from basename if
      // synchronized,
      // using same logic as PipelineMeta / AbstractMeta. (overriding any stale value from xml)
      model.setFilename(filename);

      // Create graph and add to UI using reflection hack to avoid core changes
      return addVaultToExplorer(hopGui, model, filename, this);

    } catch (Exception e) {
      throw new HopException("Error opening Data Vault model file '" + filename + "'", e);
    }
  }

  @Override
  public IHopFileTypeHandler newFile(HopGui hopGui, IVariables variables) throws HopException {
    try {
      DataVaultModel model = new DataVaultModel();
      model.setName(
          BaseMessages.getString(PKG, "HopVaultFileType.New.Text", "New Data Vault Model"));
      ModelConfigurationResolver.attach(model, hopGui.getMetadataProvider());
      ModelConfigurationResolver.applyDefaultNameIfPresent(model, hopGui.getMetadataProvider());

      return addVaultToExplorer(hopGui, model, null, this);

    } catch (Exception e) {
      throw new HopException("Error creating new Data Vault model", e);
    }
  }

  /**
   * Helper to create the graph and hook it into the explorer perspective tabs using reflection (to
   * integrate without modifying core Hop classes for this plugin).
   */
  public IHopFileTypeHandler addVaultToExplorer(
      HopGui hopGui, DataVaultModel model, String filename, HopVaultFileType fileType)
      throws Exception {

    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    CTabFolder targetFolder = ExplorerPerspectiveTabSupport.requireTabFolder(explorer);

    HopGuiVaultGraph vaultGraph =
        new HopGuiVaultGraph(targetFolder, hopGui, explorer, model, fileType);
    vaultGraph.setFilename(filename);

    CTabItem tabItem = new CTabItem(targetFolder, SWT.CLOSE);
    tabItem.setFont(GuiResource.getInstance().getFontDefault());
    tabItem.setText(Const.NVL(vaultGraph.getName(), "<>"));
    tabItem.setToolTipText(filename != null ? filename : "unsaved");
    tabItem.setImage(explorer.getFileTypeImage(fileType));
    vaultGraph.setData("KEY_TAB_FOLDER", targetFolder);
    // Under Hop Web, setControl can fire focus/selection → ExplorerPerspective.updateGui()
    // before the tab is fully wired. setData + register first so getActiveFileTypeHandler()
    // never sees a null tab payload.
    tabItem.setData(vaultGraph);
    ExplorerPerspectiveTabSupport.registerTabItem(explorer, tabItem, vaultGraph);
    tabItem.setControl(vaultGraph);

    targetFolder.setSelection(tabItem);

    explorer.activate();

    targetFolder
        .getDisplay()
        .asyncExec(() -> StandardProjectElementsOfferSupport.maybeOffer(hopGui, model));

    return vaultGraph;
  }

  @Override
  public boolean isHandledBy(String filename, boolean checkContent) throws HopException {
    if (checkContent) {
      try {
        Document doc = XmlHandler.loadXmlFile(filename);
        Node node = XmlHandler.getSubNode(doc, "data-vault-model");
        return node != null;
      } catch (Exception e) {
        return false;
      }
    } else {
      return super.isHandledBy(filename, checkContent);
    }
  }

  @Override
  public boolean supportsFile(IHasFilename metaObject) {
    return metaObject instanceof DataVaultModel;
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    HopGui hopGui = HopGui.getInstance();
    List<IGuiContextHandler> handlers = new ArrayList<>();

    GuiAction newAction =
        new GuiAction(
            "NewDataVaultModel",
            GuiActionType.Create,
            BaseMessages.getString(PKG, "HopVaultFileType.GuiAction.New.Name"),
            BaseMessages.getString(PKG, "HopVaultFileType.GuiAction.New.Tooltip"),
            "datavault-model.svg",
            (shift, ctrl, params) -> {
              try {
                this.newFile(hopGui, hopGui.getVariables());
              } catch (Exception e) {
                new ErrorDialog(
                    hopGui.getShell(),
                    BaseMessages.getString(PKG, "HopVaultFileType.ErrorDialog.Header"),
                    BaseMessages.getString(PKG, "HopVaultFileType.ErrorDialog.Message"),
                    e);
              }
            });
    newAction.setCategory("File");
    newAction.setCategoryOrder("991");

    handlers.add(new GuiContextHandler("NewDataVaultModel", List.of(newAction)));
    return handlers;
  }

  @Override
  public String getFileTypeImage() {
    return "datavault-model.svg";
  }

  // Helper for save called from the graph handler
  public void saveFile(HopGui hopGui, HopGuiVaultGraph graph) throws HopException {
    String filename = graph.getFilename();
    if (filename == null) {
      saveFileAs(hopGui, graph, null);
      return;
    }
    saveModelToFile(
        graph.getModel(), filename, hopGui.getVariables(), hopGui.getMetadataProvider());
    graph.clearChanged();
  }

  public void saveFileAs(HopGui hopGui, HopGuiVaultGraph graph, String filename)
      throws HopException {
    if (filename == null) {
      try {
        DataVaultModel model = graph.getModel();
        IVariables variables = hopGui.getVariables();
        String proposedName =
            Const.NVL(model != null ? model.getName() : null, "data-vault-model")
                + VAULT_FILE_EXTENSION;
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
    saveModelToFile(
        graph.getModel(), filename, hopGui.getVariables(), hopGui.getMetadataProvider());
    graph.setFilename(filename);
    graph.clearChanged();
  }

  private void saveModelToFile(
      DataVaultModel model,
      String filename,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      ModelXmlWriteSupport.writeModelXml(XML_TAG, model, filename, variables);
      String resolved = HopVfs.normalize(variables.resolve(filename));
      DvModelLoadSupport.invalidateCachedModelByResolvedPath(resolved);
      HopGuiBusinessVaultGraph.reloadOpenGraphsReferencingDvModel(resolved, variables);
    } catch (Exception e) {
      throw new HopException("Error saving Data Vault model to '" + filename + "'", e);
    }
  }
}
