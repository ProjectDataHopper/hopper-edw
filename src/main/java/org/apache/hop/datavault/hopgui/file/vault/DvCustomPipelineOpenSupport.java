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
package org.apache.hop.datavault.hopgui.file.vault;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.eclipse.swt.widgets.Shell;

/** Opens custom update pipeline files in the Hop Explorer perspective. */
public final class DvCustomPipelineOpenSupport {

  private static final Class<?> PKG = DvCustomPipelineOpenSupport.class;

  private DvCustomPipelineOpenSupport() {}

  public static void openPipelinePaths(
      HopGui hopGui, Shell shell, IVariables variables, List<String> pipelinePaths) {
    if (hopGui == null || pipelinePaths == null || pipelinePaths.isEmpty()) {
      return;
    }
    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    hopGui.setActivePerspective(explorer);
    explorer.activate();
    for (String pipelinePath : pipelinePaths) {
      if (Utils.isEmpty(pipelinePath)) {
        continue;
      }
      openPipelinePath(hopGui, shell, variables, pipelinePath);
    }
  }

  private static void openPipelinePath(
      HopGui hopGui, Shell shell, IVariables variables, String pipelinePath) {
    String resolvedFilename = variables != null ? variables.resolve(pipelinePath) : pipelinePath;
    if (Utils.isEmpty(resolvedFilename)) {
      return;
    }
    try {
      ExplorerPerspective perspective = HopGui.getExplorerPerspective();
      IHopFileTypeHandler fileTypeHandler =
          perspective.findFileTypeHandlerByFilename(resolvedFilename);
      if (fileTypeHandler != null) {
        perspective.setActiveFileTypeHandler(fileTypeHandler);
      } else {
        hopGui.fileDelegate.fileOpen(resolvedFilename);
      }
    } catch (Exception e) {
      if (shell != null && !shell.isDisposed()) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "DvCustomPipelineOpenSupport.Error.OpenTitle"),
            BaseMessages.getString(
                PKG, "DvCustomPipelineOpenSupport.Error.OpenMessage", resolvedFilename),
            e instanceof HopException ? e : new HopException(e));
      }
    }
  }
}
