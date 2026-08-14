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
package org.apache.hop.catalog.hopgui.navigation;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.catalog.DvSourceCatalogMapper;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.apache.hop.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.apache.hop.datavault.hopgui.file.sourcemodel.HopSourceModelFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;

/**
 * Opens the originating source model, DV, business vault, or dimensional model for a catalog record
 * definition.
 */
public final class RecordOriginNavigationSupport {

  public static final String MODEL_TYPE_DATA_VAULT = "DATA_VAULT_MODEL";
  public static final String MODEL_TYPE_DATA_VAULT_SOURCE = "DATA_VAULT_SOURCE";
  public static final String MODEL_TYPE_BUSINESS_VAULT = "BUSINESS_VAULT_MODEL";
  public static final String MODEL_TYPE_DIMENSIONAL = "DIMENSIONAL_MODEL";

  /** Origin model type for feeds published from a {@code .hsm} source model. */
  public static final String MODEL_TYPE_SOURCE_MODEL =
      DvSourceCatalogMapper.ORIGIN_MODEL_TYPE_SOURCE_MODEL;

  private static final Class<?> PKG = RecordOriginNavigationSupport.class;

  private RecordOriginNavigationSupport() {}

  public static boolean canNavigateToOrigin(RecordOrigin origin, IVariables variables) {
    if (origin == null || Utils.isEmpty(origin.getModelFilename())) {
      return false;
    }
    if (!isSupportedModelType(origin.getModelType())) {
      return false;
    }
    try {
      return Files.isRegularFile(Path.of(resolveModelPath(variables, origin.getModelFilename())));
    } catch (HopException e) {
      return false;
    }
  }

  public static void navigateToOrigin(HopGui hopGui, RecordOrigin origin, IVariables variables)
      throws HopException {
    openOrigin(hopGui, origin, variables, true);
  }

  /**
   * Opens the origin model and returns its Explorer handler. When {@code selectElement} is false
   * the file is opened without navigating to a table (used before generating a pipeline so a
   * progress dialog is not cancelled by a tab-focus change).
   */
  public static IHopFileTypeHandler openOrigin(
      HopGui hopGui, RecordOrigin origin, IVariables variables, boolean selectElement)
      throws HopException {
    if (hopGui == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordOriginNavigationSupport.Error.MissingHopGui"));
    }
    if (origin == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordOriginNavigationSupport.Error.MissingOrigin"));
    }
    if (!canNavigateToOrigin(origin, variables)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordOriginNavigationSupport.Error.UnavailableOrigin"));
    }

    String modelPath = resolveModelPath(variables, origin.getModelFilename());
    String elementName = resolve(variables, origin.getModelElementName());
    String modelType = origin.getModelType();

    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    hopGui.setActivePerspective(explorer);
    explorer.activate();

    return switch (modelType) {
      case MODEL_TYPE_SOURCE_MODEL -> {
        HopGuiSourceModelGraph graph = openSourceModelGraph(hopGui, modelPath, variables);
        if (selectElement && !Utils.isEmpty(elementName)) {
          graph.navigateToElement(elementName);
        }
        yield graph;
      }
      case MODEL_TYPE_DATA_VAULT, MODEL_TYPE_DATA_VAULT_SOURCE -> {
        HopGuiVaultGraph graph = openVaultGraph(hopGui, modelPath, variables);
        if (selectElement
            && MODEL_TYPE_DATA_VAULT.equals(modelType)
            && !Utils.isEmpty(elementName)) {
          graph.navigateToTable(elementName);
        }
        yield graph;
      }
      case MODEL_TYPE_BUSINESS_VAULT -> {
        HopGuiBusinessVaultGraph graph = openBusinessVaultGraph(hopGui, modelPath, variables);
        if (selectElement && !Utils.isEmpty(elementName)) {
          graph.navigateToTable(elementName);
        }
        yield graph;
      }
      case MODEL_TYPE_DIMENSIONAL -> {
        HopGuiDimensionalModelGraph graph = openDimensionalGraph(hopGui, modelPath, variables);
        if (selectElement && !Utils.isEmpty(elementName)) {
          graph.navigateToTable(elementName);
        }
        yield graph;
      }
      default ->
          throw new HopException(
              BaseMessages.getString(
                  PKG, "RecordOriginNavigationSupport.Error.UnsupportedModelType", modelType));
    };
  }

  private static boolean isSupportedModelType(String modelType) {
    return MODEL_TYPE_DATA_VAULT.equals(modelType)
        || MODEL_TYPE_DATA_VAULT_SOURCE.equals(modelType)
        || MODEL_TYPE_BUSINESS_VAULT.equals(modelType)
        || MODEL_TYPE_DIMENSIONAL.equals(modelType)
        || MODEL_TYPE_SOURCE_MODEL.equals(modelType);
  }

  private static HopGuiSourceModelGraph openSourceModelGraph(
      HopGui hopGui, String modelPath, IVariables variables) throws HopException {
    HopSourceModelFileType fileType = new HopSourceModelFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, modelPath, variables);
    if (!(handler instanceof HopGuiSourceModelGraph graph)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordOriginNavigationSupport.Error.UnexpectedFileHandler"));
    }
    return graph;
  }

  private static HopGuiVaultGraph openVaultGraph(
      HopGui hopGui, String modelPath, IVariables variables) throws HopException {
    HopVaultFileType fileType = new HopVaultFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, modelPath, variables);
    if (!(handler instanceof HopGuiVaultGraph graph)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordOriginNavigationSupport.Error.UnexpectedFileHandler"));
    }
    return graph;
  }

  private static HopGuiBusinessVaultGraph openBusinessVaultGraph(
      HopGui hopGui, String modelPath, IVariables variables) throws HopException {
    HopBusinessVaultFileType fileType = new HopBusinessVaultFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, modelPath, variables);
    if (!(handler instanceof HopGuiBusinessVaultGraph graph)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordOriginNavigationSupport.Error.UnexpectedFileHandler"));
    }
    return graph;
  }

  private static HopGuiDimensionalModelGraph openDimensionalGraph(
      HopGui hopGui, String modelPath, IVariables variables) throws HopException {
    HopDimensionalFileType fileType = new HopDimensionalFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, modelPath, variables);
    if (!(handler instanceof HopGuiDimensionalModelGraph graph)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordOriginNavigationSupport.Error.UnexpectedFileHandler"));
    }
    return graph;
  }

  private static String resolveModelPath(IVariables variables, String path) throws HopException {
    if (Utils.isEmpty(path)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordOriginNavigationSupport.Error.MissingModelPath"));
    }
    return HopVfs.normalize(resolve(variables, path));
  }

  private static String resolve(IVariables variables, String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
