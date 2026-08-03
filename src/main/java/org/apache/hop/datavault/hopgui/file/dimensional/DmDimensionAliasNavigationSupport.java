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
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAlias;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmModelLoadSupport;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;

/** Navigates from a dimension alias to its referenced source dimension table. */
public final class DmDimensionAliasNavigationSupport {

  private static final Class<?> PKG = DmDimensionAliasNavigationSupport.class;

  private DmDimensionAliasNavigationSupport() {}

  public static boolean canNavigateToSourceDimension(
      DimensionalModel model,
      DmDimensionAlias alias,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (model == null || alias == null || Utils.isEmpty(alias.getReferencedDimensionName())) {
      return false;
    }
    try {
      resolveSourceDimension(model, alias, variables, metadataProvider);
      return true;
    } catch (HopException ignored) {
      return false;
    }
  }

  public static void navigateToSourceDimension(
      HopGui hopGui,
      DimensionalModel model,
      HopGuiDimensionalModelGraph currentGraph,
      DmDimensionAlias alias,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (hopGui == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmDimensionAliasNavigationSupport.Error.MissingHopGui"));
    }
    if (model == null || alias == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmDimensionAliasNavigationSupport.Error.MissingAlias"));
    }

    SourceDimensionTarget target =
        resolveSourceDimension(model, alias, variables, metadataProvider);

    if (target.sameModel()) {
      HopGuiDimensionalModelGraph graph = currentGraph;
      if (graph == null || graph.getModel() != model) {
        graph = openDimensionalGraph(hopGui, model.getFilename(), variables);
      }
      graph.navigateToTable(target.dimensionName());
      return;
    }

    HopGuiDimensionalModelGraph graph = openDimensionalGraph(hopGui, target.modelPath(), variables);
    graph.navigateToTable(target.dimensionName());
  }

  private static SourceDimensionTarget resolveSourceDimension(
      DimensionalModel model,
      DmDimensionAlias alias,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String dimensionName = resolve(alias.getReferencedDimensionName(), variables);
    if (Utils.isEmpty(dimensionName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmDimensionAliasNavigationSupport.Error.MissingDimensionName"));
    }

    if (DmDimensionResolutionSupport.isExternalDimensionAlias(alias)) {
      String modelPath =
          DmModelLoadSupport.resolveModelPath(
              alias.getReferencedModelFilename(), model.getFilename(), variables);
      DimensionalModel externalModel =
          DmModelLoadSupport.loadDimensionalModel(
              alias.getReferencedModelFilename(), model.getFilename(), variables, metadataProvider);
      if (!isDimensionTable(externalModel, dimensionName)) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DmDimensionAliasNavigationSupport.Error.DimensionNotFound",
                dimensionName,
                modelPath));
      }
      return new SourceDimensionTarget(modelPath, dimensionName, false);
    }

    if (!isDimensionTable(model, dimensionName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DmDimensionAliasNavigationSupport.Error.DimensionNotFoundInModel",
              dimensionName));
    }
    return new SourceDimensionTarget(null, dimensionName, true);
  }

  private static boolean isDimensionTable(DimensionalModel model, String dimensionName) {
    if (model == null || Utils.isEmpty(dimensionName)) {
      return false;
    }
    IDmTable table = model.findTable(dimensionName);
    return table instanceof DmDimension;
  }

  private static HopGuiDimensionalModelGraph openDimensionalGraph(
      HopGui hopGui, String modelPath, IVariables variables) throws HopException {
    if (Utils.isEmpty(modelPath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmDimensionAliasNavigationSupport.Error.MissingModelPath"));
    }

    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    hopGui.setActivePerspective(explorer);
    explorer.activate();

    HopDimensionalFileType fileType = new HopDimensionalFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, modelPath, variables);
    if (!(handler instanceof HopGuiDimensionalModelGraph graph)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmDimensionAliasNavigationSupport.Error.UnexpectedFileHandler"));
    }
    return graph;
  }

  private static String resolve(String value, IVariables variables) {
    return variables != null ? variables.resolve(value) : value;
  }

  private record SourceDimensionTarget(String modelPath, String dimensionName, boolean sameModel) {}
}
