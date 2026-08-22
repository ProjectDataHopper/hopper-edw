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
package org.apache.hop.datavault.hopgui.search;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;

/**
 * Shared open/focus helpers for model search result callbacks (open tab or load from disk, then
 * activate the explorer perspective).
 */
public final class ModelSearchOpenSupport {

  private ModelSearchOpenSupport() {}

  /**
   * Opens or focuses the model file, activates the explorer perspective, and returns the active
   * file type handler for optional component navigation.
   */
  public static IHopFileTypeHandler openModelFile(String filename, IHopFileType fileType)
      throws HopException {
    if (Utils.isEmpty(filename) || fileType == null) {
      return null;
    }
    HopGui hopGui = HopGui.getInstance();
    IVariables variables = hopGui.getVariables();
    String resolved = HopVfs.normalize(variables.resolve(filename));

    ExplorerPerspective perspective = HopGui.getExplorerPerspective();
    IHopFileTypeHandler handler = perspective.findFileTypeHandlerByFilename(resolved);
    if (handler == null) {
      handler = fileType.openFile(hopGui, resolved, variables);
    } else {
      perspective.setActiveFileTypeHandler(handler);
    }
    perspective.activate();
    return handler;
  }
}
