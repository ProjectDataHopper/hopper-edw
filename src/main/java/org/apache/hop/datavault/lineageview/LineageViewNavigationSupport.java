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
package org.apache.hop.datavault.lineageview;

import org.apache.hop.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.apache.hop.catalog.hopgui.perspective.DataCatalogPerspective;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.apache.hop.datavault.hopgui.file.dimensional.DmDimensionAliasNavigationSupport;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.apache.hop.datavault.lineageview.backend.HopExportFacet;
import org.apache.hop.datavault.lineageview.backend.HopLocationFacet;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvPitTable;
import org.apache.hop.datavault.metadata.businessvault.BvScd2Table;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAlias;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.eclipse.swt.widgets.Display;

/** Maps lineage facets to existing Hop openers. */
public final class LineageViewNavigationSupport {

  public static final String LAYER_DV = "DV";
  public static final String LAYER_BV = "BV";
  public static final String LAYER_DM = "DM";

  private LineageViewNavigationSupport() {}

  public static RecordOrigin toRecordOrigin(LineageNode node, IVariables variables) {
    HopExportFacet export = hopExport(node);
    if (export == null) {
      return null;
    }
    String modelType = modelTypeForLayer(export.getModelLayer());
    if (modelType == null || Utils.isEmpty(export.getModelFilename())) {
      return null;
    }
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(modelType);
    origin.setModelName(resolve(variables, export.getModelName()));
    origin.setModelFilename(resolve(variables, export.getModelFilename()));
    origin.setModelElementName(export.getLogicalName());
    return origin;
  }

  public static String modelTypeForLayer(String modelLayer) {
    if (Utils.isEmpty(modelLayer)) {
      return null;
    }
    return switch (modelLayer.trim().toUpperCase()) {
      case LAYER_DV -> RecordOriginNavigationSupport.MODEL_TYPE_DATA_VAULT;
      case LAYER_BV -> RecordOriginNavigationSupport.MODEL_TYPE_BUSINESS_VAULT;
      case LAYER_DM -> RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL;
      default -> null;
    };
  }

  public static boolean canOpenModel(LineageNode node, IVariables variables) {
    RecordOrigin origin = toRecordOrigin(node, variables);
    return origin != null && RecordOriginNavigationSupport.canNavigateToOrigin(origin, variables);
  }

  public static RecordDefinitionKey parseCatalogKey(String catalogKey) {
    if (Utils.isEmpty(catalogKey)) {
      return null;
    }
    int slash = catalogKey.lastIndexOf('/');
    if (slash <= 0 || slash >= catalogKey.length() - 1) {
      return null;
    }
    return new RecordDefinitionKey(catalogKey.substring(0, slash), catalogKey.substring(slash + 1));
  }

  public static String catalogConnection(LineageNode node) {
    HopLocationFacet location = hopLocation(node);
    if (location != null && !Utils.isEmpty(location.getCatalogConnection())) {
      return location.getCatalogConnection();
    }
    HopExportFacet export = hopExport(node);
    if (export != null && !Utils.isEmpty(export.getCatalogConnection())) {
      return export.getCatalogConnection();
    }
    return null;
  }

  public static boolean canOpenCatalog(LineageNode node) {
    return parseCatalogKey(catalogKey(node)) != null && !Utils.isEmpty(catalogConnection(node));
  }

  public static boolean canOpenUpdatePipeline(LineageNode node, IVariables variables) {
    HopExportFacet export = hopExport(node);
    if (export == null || Utils.isEmpty(export.getLogicalName())) {
      return false;
    }
    String layer = export.getModelLayer();
    if (!LAYER_DV.equalsIgnoreCase(layer) && !LAYER_DM.equalsIgnoreCase(layer)) {
      return false;
    }
    return canOpenModel(node, variables);
  }

  public static boolean canOpenBuildPipeline(LineageNode node, IVariables variables) {
    HopExportFacet export = hopExport(node);
    if (export == null || Utils.isEmpty(export.getLogicalName())) {
      return false;
    }
    if (!LAYER_BV.equalsIgnoreCase(export.getModelLayer())) {
      return false;
    }
    if (!isBvPipelineTableType(export.getTableType())) {
      return false;
    }
    return canOpenModel(node, variables);
  }

  public static boolean isBvPipelineTableType(String tableType) {
    if (Utils.isEmpty(tableType)) {
      return false;
    }
    String type = tableType.trim();
    return "SCD2".equalsIgnoreCase(type) || "PIT".equalsIgnoreCase(type);
  }

  public static void openModel(HopGui hopGui, IVariables variables, LineageNode node)
      throws HopException {
    openModelHandler(hopGui, variables, node, true);
  }

  static IHopFileTypeHandler openModelHandler(
      HopGui hopGui, IVariables variables, LineageNode node, boolean selectElement)
      throws HopException {
    RecordOrigin origin = toRecordOrigin(node, variables);
    if (origin == null) {
      throw new HopException("Lineage node has no hop_export model location");
    }
    return RecordOriginNavigationSupport.openOrigin(hopGui, origin, variables, selectElement);
  }

  public static void openCatalog(HopGui hopGui, LineageNode node) throws HopException {
    RecordDefinitionKey key = parseCatalogKey(catalogKey(node));
    String connection = catalogConnection(node);
    if (key == null || Utils.isEmpty(connection)) {
      throw new HopException("Lineage node has no catalog key or catalog connection");
    }
    DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
    if (perspective == null) {
      throw new HopException("Data Catalog perspective is not available");
    }
    perspective.selectRecordDefinition(connection, key);
  }

  public static void openUpdatePipeline(HopGui hopGui, IVariables variables, LineageNode node)
      throws HopException {
    IHopFileTypeHandler handler = openModelHandler(hopGui, variables, node, false);
    String logical = logicalName(node);
    String physical = physicalTableName(node);
    if (handler instanceof HopGuiVaultGraph vaultGraph) {
      IDvTable table = findDvTable(vaultGraph.getModel(), logical, physical);
      if (table == null) {
        throw new HopException("Data Vault table not found: " + firstNonEmpty(logical, physical));
      }
      runAfterUiSettles(hopGui, () -> vaultGraph.openUpdatePipeline(table));
      return;
    }
    if (handler instanceof HopGuiDimensionalModelGraph dimensionalGraph) {
      IDmTable table = findDmTable(dimensionalGraph.getModel(), logical, physical);
      if (table instanceof DmDimensionAlias alias) {
        DmUpdateTarget target =
            resolveAliasUpdateTarget(hopGui, dimensionalGraph, alias, variables);
        dimensionalGraph = target.graph();
        table = target.table();
      }
      if (table == null || table instanceof DmDimensionAlias) {
        throw new HopException("Dimensional table not found: " + firstNonEmpty(logical, physical));
      }
      HopGuiDimensionalModelGraph graphToOpen = dimensionalGraph;
      IDmTable tableToOpen = table;
      runAfterUiSettles(hopGui, () -> graphToOpen.openUpdatePipeline(tableToOpen));
      return;
    }
    throw new HopException("Opened file is not a Data Vault or dimensional model");
  }

  public static void openBuildPipeline(HopGui hopGui, IVariables variables, LineageNode node)
      throws HopException {
    IHopFileTypeHandler handler = openModelHandler(hopGui, variables, node, false);
    if (!(handler instanceof HopGuiBusinessVaultGraph bvGraph)) {
      throw new HopException("Opened file is not a Business Vault model");
    }
    String logical = logicalName(node);
    String physical = physicalTableName(node);
    IBvTable table = findBvTable(bvGraph.getModel(), logical, physical);
    if (table == null) {
      throw new HopException("Business Vault table not found: " + firstNonEmpty(logical, physical));
    }
    if (!(table instanceof BvScd2Table) && !(table instanceof BvPitTable)) {
      throw new HopException("Build pipeline is only available for SCD2 and PIT tables");
    }
    runAfterUiSettles(hopGui, () -> bvGraph.openBuildPipeline(table));
  }

  static IDvTable findDvTable(DataVaultModel model, String logical, String physical) {
    if (model == null) {
      return null;
    }
    IDvTable table = !Utils.isEmpty(logical) ? model.findTable(logical) : null;
    if (table == null && !Utils.isEmpty(physical) && !physical.equalsIgnoreCase(logical)) {
      table = model.findTable(physical);
    }
    if (table == null && !Utils.isEmpty(physical)) {
      for (IDvTable candidate : model.getTables()) {
        if (candidate != null && physical.equalsIgnoreCase(candidate.getTableName())) {
          return candidate;
        }
      }
    }
    return table;
  }

  static DmUpdateTarget resolveAliasUpdateTarget(
      HopGui hopGui,
      HopGuiDimensionalModelGraph currentGraph,
      DmDimensionAlias alias,
      IVariables variables)
      throws HopException {
    DimensionalModel model = currentGraph != null ? currentGraph.getModel() : null;
    DmDimensionAliasNavigationSupport.DimensionPipelineSource source =
        DmDimensionAliasNavigationSupport.resolvePipelineSource(
            model, alias, variables, hopGui != null ? hopGui.getMetadataProvider() : null);
    if (source.sameModel()) {
      IDmTable target = findDmTable(model, source.dimensionName(), null);
      return new DmUpdateTarget(currentGraph, target);
    }
    HopDimensionalFileType fileType = new HopDimensionalFileType();
    IHopFileTypeHandler opened = fileType.openFile(hopGui, source.modelPath(), variables);
    if (!(opened instanceof HopGuiDimensionalModelGraph targetGraph)) {
      throw new HopException("Opened file is not a dimensional model: " + source.modelPath());
    }
    IDmTable target = findDmTable(targetGraph.getModel(), source.dimensionName(), null);
    return new DmUpdateTarget(targetGraph, target);
  }

  record DmUpdateTarget(HopGuiDimensionalModelGraph graph, IDmTable table) {}

  static IDmTable findDmTable(DimensionalModel model, String logical, String physical) {
    if (model == null) {
      return null;
    }
    IDmTable table = !Utils.isEmpty(logical) ? model.findTable(logical) : null;
    if (table == null && !Utils.isEmpty(physical) && !physical.equals(logical)) {
      table = model.findTable(physical);
    }
    return table;
  }

  static IBvTable findBvTable(BusinessVaultModel model, String logical, String physical) {
    if (model == null) {
      return null;
    }
    IBvTable table = !Utils.isEmpty(logical) ? model.findTable(logical) : null;
    if (table == null && !Utils.isEmpty(physical) && !physical.equals(logical)) {
      table = model.findTable(physical);
    }
    return table;
  }

  static String catalogKey(LineageNode node) {
    HopLocationFacet location = hopLocation(node);
    return location != null ? location.getCatalogKey() : null;
  }

  static String logicalName(LineageNode node) {
    HopExportFacet export = hopExport(node);
    return export != null ? export.getLogicalName() : null;
  }

  static String physicalTableName(LineageNode node) {
    HopExportFacet export = hopExport(node);
    return export != null ? export.getPhysicalTableName() : null;
  }

  static String firstNonEmpty(String first, String second) {
    if (!Utils.isEmpty(first)) {
      return first;
    }
    return second;
  }

  /**
   * Pipeline generation shows a progress dialog. The context-menu action already runs from {@code
   * asyncExec}, and opening the model queues more UI work. Running generation on the next display
   * turn avoids that progress dialog being cancelled by the tab switch.
   */
  static void runAfterUiSettles(HopGui hopGui, Runnable work) {
    Display display = hopGui != null ? hopGui.getDisplay() : Display.getCurrent();
    if (display == null || display.isDisposed()) {
      work.run();
      return;
    }
    display.asyncExec(
        () -> {
          try {
            work.run();
          } catch (Exception e) {
            if (hopGui != null && hopGui.getShell() != null && !hopGui.getShell().isDisposed()) {
              new ErrorDialog(
                  hopGui.getShell(),
                  "Lineage view",
                  "Unable to open the generated pipeline",
                  e instanceof HopException ? e : new HopException(e));
            }
          }
        });
  }

  static HopExportFacet hopExport(LineageNode node) {
    return node != null ? node.getHopExport() : null;
  }

  static HopLocationFacet hopLocation(LineageNode node) {
    return node != null ? node.getHopLocation() : null;
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
