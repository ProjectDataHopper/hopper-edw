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
package org.apache.hop.datavault.hopgui.file.businessvault;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.metadata.DvModelLoadSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvBvTableReference;
import org.apache.hop.datavault.metadata.businessvault.BvSqlModelPathSupport;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;

/** Opens another Business Vault model and navigates to a referenced table. */
public final class BusinessVaultBvNavigationSupport {

  private static final Class<?> PKG = BusinessVaultBvNavigationSupport.class;

  private BusinessVaultBvNavigationSupport() {}

  public static void navigateToBvTable(
      HopGui hopGui,
      BusinessVaultModel currentModel,
      BvBvTableReference reference,
      IVariables variables)
      throws HopException {
    if (reference == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultBvNavigationSupport.Error.MissingTableName"));
    }
    navigateToBvTable(
        hopGui,
        currentModel,
        reference.getBvTableName(),
        reference.getReferencedModelFilename(),
        variables);
  }

  public static void navigateToBvTable(
      HopGui hopGui,
      BusinessVaultModel currentModel,
      String bvTableName,
      String referencedModelFilename,
      IVariables variables)
      throws HopException {
    if (hopGui == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultBvNavigationSupport.Error.MissingHopGui"));
    }
    if (Utils.isEmpty(bvTableName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultBvNavigationSupport.Error.MissingTableName"));
    }
    if (Utils.isEmpty(referencedModelFilename)) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultBvNavigationSupport.Error.MissingModelPath"));
    }

    String referring = currentModel != null ? currentModel.getFilename() : null;
    String resolvedPath =
        DvModelLoadSupport.resolveModelPath(referencedModelFilename, referring, variables);
    try {
      resolvedPath =
          HopVfs.normalize(variables != null ? variables.resolve(resolvedPath) : resolvedPath);
    } catch (Exception e) {
      resolvedPath =
          variables != null ? variables.resolve(referencedModelFilename) : referencedModelFilename;
    }

    BusinessVaultModel external =
        BvSqlModelPathSupport.loadBusinessVaultModelUncached(
            resolvedPath, hopGui.getMetadataProvider());
    IBvTable table = external.findTable(bvTableName);
    if (table == null) {
      // try physical name match
      for (IBvTable t : external.getTables()) {
        if (t != null
            && (bvTableName.equalsIgnoreCase(t.getName())
                || bvTableName.equalsIgnoreCase(t.getTableName()))) {
          table = t;
          break;
        }
      }
    }
    if (table == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BusinessVaultBvNavigationSupport.Error.TableNotFound",
              bvTableName,
              referencedModelFilename));
    }

    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    hopGui.setActivePerspective(explorer);
    explorer.activate();

    HopBusinessVaultFileType fileType = new HopBusinessVaultFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, resolvedPath, variables);
    if (!(handler instanceof HopGuiBusinessVaultGraph bvGraph)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BusinessVaultBvNavigationSupport.Error.UnexpectedFileHandler"));
    }
    bvGraph.navigateToTable(table.getName());
  }
}
