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
package org.apache.hop.datavault.hopgui.file.lineageview;

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
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.ExplorerPerspectiveTabSupport;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineageview.HopLineageViewDocument;
import org.apache.hop.datavault.lineageview.LineageViewPersistence;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;

@HopFileTypePlugin(
    id = "HopFile-LineageView-Plugin",
    name = "Hop Lineage View",
    description = "View definition for data lineage over an OpenLineage backend",
    image = "lineage-view.svg")
public class HopLineageViewFileType extends HopFileTypeBase {

  public static final Class<?> PKG = HopGuiLineageViewGraph.class;
  public static final String FILE_TYPE_DESCRIPTION = "Hop Lineage View";
  public static final String FILE_EXTENSION = ".hlv";
  public static final String XML_TAG = "hop-lineage-view";

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
    return new String[] {"Hop Lineage Views"};
  }

  @Override
  public Properties getCapabilities() {
    Properties caps = new Properties();
    caps.setProperty(IHopFileType.CAPABILITY_NEW, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SAVE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SAVE_AS, "true");
    caps.setProperty(IHopFileType.CAPABILITY_CLOSE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_EXPORT_TO_SVG, "true");
    caps.setProperty(IHopFileType.CAPABILITY_FILE_HISTORY, "true");
    caps.setProperty(IHopFileType.CAPABILITY_SEARCH, "true");
    return caps;
  }

  @Override
  public ISearchable<?> createSearchable(
      String filename,
      String locationDescription,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    HopLineageViewDocument document =
        LineageViewPersistence.load(filename, metadataProvider, variables);
    return new HopGuiLineageViewSearchable(locationDescription, document);
  }

  @Override
  public IHopFileTypeHandler newFile(HopGui hopGui, IVariables variables) throws HopException {
    try {
      HopLineageViewDocument draft = new HopLineageViewDocument();
      LineageViewSettingsDialog dialog =
          new LineageViewSettingsDialog(hopGui.getShell(), hopGui, draft, true);
      if (!dialog.open()) {
        return null;
      }
      return addToExplorer(hopGui, draft, null);
    } catch (Exception e) {
      throw new HopException("Error creating new lineage view", e);
    }
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
      HopLineageViewDocument document =
          LineageViewPersistence.load(filename, hopGui.getMetadataProvider(), variables);
      return addToExplorer(hopGui, document, filename);
    } catch (Exception e) {
      throw new HopException("Unable to open lineage view file: " + filename, e);
    }
  }

  public IHopFileTypeHandler addToExplorer(
      HopGui hopGui, HopLineageViewDocument document, String filename) throws Exception {
    return addToExplorer(hopGui, document, filename, List.of());
  }

  public IHopFileTypeHandler addToExplorer(
      HopGui hopGui,
      HopLineageViewDocument document,
      String filename,
      List<LineageSnapshot> extraSnapshots)
      throws Exception {
    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    CTabFolder targetFolder = ExplorerPerspectiveTabSupport.requireTabFolder(explorer);
    HopGuiLineageViewGraph graph =
        new HopGuiLineageViewGraph(targetFolder, hopGui, explorer, document, this);
    graph.setFilename(filename);
    graph.setExtraSnapshots(extraSnapshots != null ? extraSnapshots : List.of());

    CTabItem tabItem = new CTabItem(targetFolder, SWT.CLOSE);
    tabItem.setFont(GuiResource.getInstance().getFontDefault());
    tabItem.setText(Const.NVL(graph.getName(), "<>"));
    tabItem.setToolTipText(filename != null ? filename : "unsaved");
    tabItem.setImage(explorer.getFileTypeImage(this));
    // Under Hop Web, setControl can fire focus/selection before the tab is fully wired.
    tabItem.setData(graph);
    ExplorerPerspectiveTabSupport.registerTabItem(explorer, tabItem, graph);
    tabItem.setControl(graph);
    targetFolder.setSelection(tabItem);
    explorer.activate();
    if (filename == null) {
      graph.setChanged();
    }
    graph.updateGui();
    graph.refreshGraph();
    return graph;
  }

  @Override
  public boolean isHandledBy(String filename, boolean checkContent) throws HopException {
    try {
      if (filename != null && filename.toLowerCase().endsWith(FILE_EXTENSION)) {
        return true;
      }
      if (checkContent) {
        Document document = XmlHandler.loadXmlFile(filename);
        Node node = XmlHandler.getSubNode(document, XML_TAG);
        return node != null;
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean supportsFile(IHasFilename meta) {
    return meta instanceof HopLineageViewDocument;
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    HopGui hopGui = HopGui.getInstance();
    List<IGuiContextHandler> handlers = new ArrayList<>();
    GuiAction newAction =
        new GuiAction(
            "NewHopLineageView",
            GuiActionType.Create,
            BaseMessages.getString(PKG, "HopLineageViewFileType.GuiAction.New.Name"),
            BaseMessages.getString(PKG, "HopLineageViewFileType.GuiAction.New.Tooltip"),
            "lineage-view.svg",
            (shift, ctrl, params) -> {
              try {
                this.newFile(hopGui, hopGui.getVariables());
              } catch (Exception e) {
                new ErrorDialog(
                    hopGui.getShell(),
                    BaseMessages.getString(PKG, "HopLineageViewFileType.ErrorDialog.Header"),
                    BaseMessages.getString(PKG, "HopLineageViewFileType.ErrorDialog.Message"),
                    e);
              }
            });
    newAction.setCategory("File");
    newAction.setCategoryOrder("994");
    handlers.add(new GuiContextHandler("NewHopLineageView", List.of(newAction)));
    return handlers;
  }

  public String getFileTypeImage() {
    return "lineage-view.svg";
  }

  public void saveFile(HopGui hopGui, HopGuiLineageViewGraph graph) throws HopException {
    String filename = graph.getFilename();
    if (filename == null) {
      saveFileAs(hopGui, graph, null);
      return;
    }
    LineageViewPersistence.save(graph.getDocument(), filename, hopGui.getVariables());
    graph.clearChanged();
    updateExplorerTab(graph);
  }

  public void saveFileAs(HopGui hopGui, HopGuiLineageViewGraph graph, String filename)
      throws HopException {
    if (filename == null) {
      try {
        IVariables variables = hopGui.getVariables();
        String proposedName = proposedSaveName(graph) + FILE_EXTENSION;
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
    LineageViewPersistence.save(graph.getDocument(), filename, hopGui.getVariables());
    graph.setFilename(filename);
    graph.clearChanged();
    updateExplorerTab(graph);
  }

  private static void updateExplorerTab(HopGuiLineageViewGraph graph) {
    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    if (explorer != null) {
      explorer.updateTabItem(graph);
    }
  }

  static String proposedSaveName(HopGuiLineageViewGraph graph) {
    return proposedSaveName(graph != null ? graph.getDocument() : null);
  }

  static String proposedSaveName(HopLineageViewDocument document) {
    if (document == null) {
      return "lineage-view";
    }
    if (!Utils.isEmpty(document.getLogicalTable())) {
      return document.getLogicalTable();
    }
    if (!Utils.isEmpty(document.getDatasetName())) {
      return document.getDatasetName();
    }
    if (!Utils.isEmpty(document.getJobName())) {
      String job = document.getJobName();
      int slash = job.lastIndexOf('/');
      return slash >= 0 ? job.substring(slash + 1) : job;
    }
    return "lineage-view";
  }
}
