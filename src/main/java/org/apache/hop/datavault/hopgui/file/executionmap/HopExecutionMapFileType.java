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
package org.apache.hop.datavault.hopgui.file.executionmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.executionmap.ExecutionMapPersistence;
import org.apache.hop.datavault.hopgui.file.ExplorerPerspectiveTabSupport;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.ui.hopgui.HopGui;
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
    id = "HopFile-ExecutionMap-Plugin",
    name = "Hop Execution Map",
    description = "Read-only graph of connected Hop workflows, pipelines, and DV/BV/DM artifacts",
    image = "execution-map.svg")
public class HopExecutionMapFileType extends HopFileTypeBase {

  public static final Class<?> PKG = HopGuiExecutionMapGraph.class;
  public static final String FILE_TYPE_DESCRIPTION = "Hop Execution Map";
  public static final String FILE_EXTENSION = ".hem";
  public static final String XML_TAG = "hop-execution-map";

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
    return new String[] {"Hop Execution Maps"};
  }

  @Override
  public Properties getCapabilities() {
    Properties caps = new Properties();
    caps.setProperty(IHopFileType.CAPABILITY_CLOSE, "true");
    caps.setProperty(IHopFileType.CAPABILITY_EXPORT_TO_SVG, "true");
    return caps;
  }

  @Override
  public IHopFileTypeHandler newFile(HopGui hopGui, IVariables variables) throws HopException {
    throw new HopException(
        "Hop execution maps are generated from workflows or pipelines and cannot be created empty");
  }

  @Override
  public IHopFileTypeHandler openFile(HopGui hopGui, String filename, IVariables variables)
      throws HopException {
    try {
      ExecutionMapDocument document =
          ExecutionMapPersistence.load(filename, hopGui.getMetadataProvider(), variables);
      return addExecutionMapToExplorer(hopGui, document, filename);
    } catch (Exception e) {
      throw new HopException("Unable to open execution map file: " + filename, e);
    }
  }

  @Override
  public boolean isHandledBy(String filename, boolean checkContent) throws HopException {
    try {
      if (filename.toLowerCase().endsWith(FILE_EXTENSION)) {
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
    return meta instanceof ExecutionMapDocument;
  }

  private IHopFileTypeHandler addExecutionMapToExplorer(
      HopGui hopGui, ExecutionMapDocument document, String filename) throws Exception {
    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    CTabFolder targetFolder = ExplorerPerspectiveTabSupport.requireTabFolder(explorer);
    HopGuiExecutionMapGraph graph =
        new HopGuiExecutionMapGraph(targetFolder, hopGui, explorer, document, this);
    graph.setFilename(filename);

    CTabItem tabItem = new CTabItem(targetFolder, SWT.CLOSE);
    tabItem.setText(document.getName() != null ? document.getName() : filename);
    tabItem.setImage(explorer.getFileTypeImage(this));
    // Under Hop Web, setControl can fire focus/selection → ExplorerPerspective.updateGui()
    // before the tab is fully wired. setData + register first so getActiveFileTypeHandler()
    // never sees a null tab payload.
    tabItem.setData(graph);
    ExplorerPerspectiveTabSupport.registerTabItem(explorer, tabItem, graph);
    tabItem.setControl(graph);
    targetFolder.setSelection(tabItem);
    explorer.activate();
    graph.updateGui();
    return graph;
  }

  public String getFileTypeImage() {
    return "execution-map.svg";
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>();
  }
}
