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
package org.hopper.edw.datavault.hopgui.file.projectdoc;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.HopFileTypePlugin;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.file.empty.EmptyHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerFile;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.file.capabilities.FileTypeCapabilities;
import org.apache.hop.ui.hopgui.perspective.explorer.file.types.base.BaseExplorerFileType;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.hopper.edw.datavault.hopgui.EdwDocsWebSupport;

/**
 * Explorer handler for generated project-documentation HTML on Hop Web. Desktop keeps the system
 * browser; this type is installed on tree items that sit in a hop-doc site.
 */
@HopFileTypePlugin(
    id = "HopProjectDocumentationHtmlFileType",
    name = "Project documentation",
    description = "Generated Hop project documentation HTML",
    image = "edw-logo.svg")
public class HopProjectDocFileType
    extends BaseExplorerFileType<HopProjectDocExplorerFileTypeHandler> {

  public static final String FILE_TYPE_NAME = "Project documentation";

  private static final HopProjectDocFileType INSTANCE = new HopProjectDocFileType();

  public HopProjectDocFileType() {
    super(
        FILE_TYPE_NAME,
        ".html",
        new String[] {"*.hop-project-doc.html"},
        new String[] {"Project documentation"},
        FileTypeCapabilities.getCapabilities(
            IHopFileType.CAPABILITY_CLOSE, IHopFileType.CAPABILITY_FILE_HISTORY));
  }

  public static HopProjectDocFileType getInstance() {
    return INSTANCE;
  }

  @Override
  public HopProjectDocExplorerFileTypeHandler createFileTypeHandler(
      HopGui hopGui, ExplorerPerspective perspective, ExplorerFile file) {
    return new HopProjectDocExplorerFileTypeHandler(hopGui, perspective, file);
  }

  @Override
  public IHopFileTypeHandler newFile(HopGui hopGui, IVariables parentVariableSpace)
      throws HopException {
    return new EmptyHopFileTypeHandler();
  }

  @Override
  public HopProjectDocExplorerFileTypeHandler openFile(
      HopGui hopGui, String filename, IVariables variables) throws HopException {
    String resolved = variables == null ? filename : variables.resolve(filename);
    if (Utils.isEmpty(resolved)) {
      throw new HopException("Documentation file name is required");
    }
    if (isHttp(resolved)) {
      return openExplorerFile(hopGui, resolved, tabName(resolved));
    }
    if (EnvironmentUtils.getInstance().isWeb()) {
      String vfsName = HopVfs.getFilename(HopVfs.getFileObject(resolved, variables));
      EdwDocsWebSupport.openInBrowser(vfsName);
      return null;
    }
    return openExplorerFile(hopGui, resolved, tabName(resolved));
  }

  private HopProjectDocExplorerFileTypeHandler openExplorerFile(
      HopGui hopGui, String filename, String name) {
    ExplorerFile explorerFile = new ExplorerFile();
    explorerFile.setName(name);
    explorerFile.setFilename(filename);
    explorerFile.setFileType(this);
    ExplorerPerspective perspective = ExplorerPerspective.getInstance();
    HopProjectDocExplorerFileTypeHandler handler =
        createFileTypeHandler(hopGui, perspective, explorerFile);
    perspective.addFile(handler);
    return handler;
  }

  static boolean isHttp(String filename) {
    if (Utils.isEmpty(filename)) {
      return false;
    }
    String lower = filename.toLowerCase();
    return lower.startsWith("http://") || lower.startsWith("https://");
  }

  private static String tabName(String filename) {
    if (Utils.isEmpty(filename)) {
      return "documentation";
    }
    int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
    return slash >= 0 && slash < filename.length() - 1 ? filename.substring(slash + 1) : filename;
  }
}
