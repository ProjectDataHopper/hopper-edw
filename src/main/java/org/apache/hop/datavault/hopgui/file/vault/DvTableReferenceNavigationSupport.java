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
package org.apache.hop.datavault.hopgui.file.vault;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvModelLoadSupport;
import org.apache.hop.datavault.metadata.DvTableReference;
import org.apache.hop.datavault.metadata.DvTableResolutionSupport;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;

/** Navigates from a cross-model table reference to its source table in another or the same .hdv. */
public final class DvTableReferenceNavigationSupport {

  private static final Class<?> PKG = DvTableReferenceNavigationSupport.class;

  private DvTableReferenceNavigationSupport() {}

  public static boolean canNavigateToSourceTable(
      DataVaultModel model,
      DvTableReference reference,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (model == null || reference == null || Utils.isEmpty(reference.getReferencedTableName())) {
      return false;
    }
    try {
      resolveSourceTable(model, reference, variables, metadataProvider);
      return true;
    } catch (HopException ignored) {
      return false;
    }
  }

  public static void navigateToSourceTable(
      HopGui hopGui,
      DataVaultModel model,
      HopGuiVaultGraph currentGraph,
      DvTableReference reference,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (hopGui == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvTableReferenceNavigationSupport.Error.MissingHopGui"));
    }
    if (model == null || reference == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvTableReferenceNavigationSupport.Error.MissingReference"));
    }

    SourceTableTarget target = resolveSourceTable(model, reference, variables, metadataProvider);

    if (target.sameModel()) {
      HopGuiVaultGraph graph = currentGraph;
      if (graph == null || graph.getModel() != model) {
        graph = openVaultGraph(hopGui, model.getFilename(), variables);
      }
      graph.navigateToTable(target.tableName());
      return;
    }

    HopGuiVaultGraph graph = openVaultGraph(hopGui, target.modelPath(), variables);
    graph.navigateToTable(target.tableName());
  }

  private static SourceTableTarget resolveSourceTable(
      DataVaultModel model,
      DvTableReference reference,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String tableName = resolve(reference.getReferencedTableName(), variables);
    if (Utils.isEmpty(tableName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvTableReferenceNavigationSupport.Error.MissingTableName"));
    }

    if (DvTableResolutionSupport.isExternalTableReference(reference)) {
      String modelPath =
          DvModelLoadSupport.resolveModelPath(
              reference.getReferencedModelFilename(), model.getFilename(), variables);
      DataVaultModel externalModel =
          DvModelLoadSupport.loadDataVaultModel(
              reference.getReferencedModelFilename(),
              model.getFilename(),
              variables,
              metadataProvider);
      IDvTable referenced = externalModel.findTable(tableName);
      if (referenced == null || referenced instanceof DvTableReference) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DvTableReferenceNavigationSupport.Error.TableNotFound",
                tableName,
                modelPath));
      }
      if (reference.getReferencedTableType() != null
          && referenced.getTableType() != reference.getReferencedTableType()) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DvTableReferenceNavigationSupport.Error.TableTypeMismatch",
                tableName,
                modelPath));
      }
      return new SourceTableTarget(modelPath, tableName, false);
    }

    IDvTable referenced = model.findTable(tableName);
    if (referenced == null || referenced instanceof DvTableReference) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvTableReferenceNavigationSupport.Error.TableNotFoundInModel", tableName));
    }
    if (reference.getReferencedTableType() != null
        && referenced.getTableType() != reference.getReferencedTableType()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvTableReferenceNavigationSupport.Error.TableTypeMismatchInModel", tableName));
    }
    return new SourceTableTarget(null, tableName, true);
  }

  private static HopGuiVaultGraph openVaultGraph(
      HopGui hopGui, String modelPath, IVariables variables) throws HopException {
    if (Utils.isEmpty(modelPath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvTableReferenceNavigationSupport.Error.MissingModelPath"));
    }

    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    hopGui.setActivePerspective(explorer);
    explorer.activate();

    HopVaultFileType fileType = new HopVaultFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, modelPath, variables);
    if (!(handler instanceof HopGuiVaultGraph graph)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvTableReferenceNavigationSupport.Error.UnexpectedFileHandler"));
    }
    return graph;
  }

  private static String resolve(String value, IVariables variables) {
    return variables != null ? variables.resolve(value) : value;
  }

  private record SourceTableTarget(String modelPath, String tableName, boolean sameModel) {}
}
