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
package org.apache.hop.datavault.hopgui.file.dimensional;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.eclipse.swt.widgets.Shell;

/** Opens the configured staging pipeline for a dimensional table. */
public final class DmSourcePipelineOpenSupport {

  private static final Class<?> PKG = DmSourcePipelineOpenSupport.class;

  private DmSourcePipelineOpenSupport() {}

  public static boolean canOpenSourcePipeline(IDmTable table, IVariables variables) {
    if (table == null) {
      return false;
    }
    DmSourceConfiguration source = table.getSourceOrDefault();
    return source.isPipelineSource() && !Utils.isEmpty(source.resolveSourcePipelineFile(variables));
  }

  public static boolean canOpenSourcePipeline(
      IVariables variables, String pipelineFile, boolean pipelineSourceSelected) {
    if (!pipelineSourceSelected) {
      return false;
    }
    String resolved = variables != null ? variables.resolve(pipelineFile) : pipelineFile;
    return !Utils.isEmpty(resolved);
  }

  public static void openSourcePipeline(HopGui hopGui, IDmTable table, IVariables variables) {
    if (hopGui == null || table == null) {
      return;
    }
    if (!canOpenSourcePipeline(table, variables)) {
      return;
    }
    openSourcePipelineFile(
        hopGui, hopGui.getShell(), variables, table.getSourceOrDefault().getSourcePipelineFile());
  }

  public static void openSourcePipelineFile(
      HopGui hopGui, Shell shell, IVariables variables, String pipelineFile) {
    if (hopGui == null) {
      return;
    }
    String resolvedFilename = variables != null ? variables.resolve(pipelineFile) : pipelineFile;
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
            BaseMessages.getString(PKG, "DmSourcePipelineOpenSupport.Error.OpenTitle"),
            BaseMessages.getString(
                PKG, "DmSourcePipelineOpenSupport.Error.OpenMessage", resolvedFilename),
            e instanceof HopException ? e : new HopException(e));
      }
    }
  }
}
