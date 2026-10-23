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
package org.hopper.edw.datavault.lineageview;

import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.database.DatabaseWorkbenchDialog;
import org.eclipse.swt.widgets.Display;
import org.hopper.edw.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.hopper.edw.catalog.hopgui.perspective.DataCatalogPerspective;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewRunner;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.hopgui.ModelTargetTablePreviewSupport;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.hopper.edw.datavault.hopgui.file.dimensional.DmDimensionAliasNavigationSupport;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.hopper.edw.datavault.lineageview.backend.HopExportFacet;
import org.hopper.edw.datavault.lineageview.backend.HopLocationFacet;
import org.hopper.edw.datavault.lineageview.backend.LineageEdge;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;

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

  /** Warehouse model from {@code hop_export}, or a source model recorded on the catalog origin. */
  public static boolean canOpenModel(
      LineageNode node, IVariables variables, IHopMetadataProvider metadataProvider) {
    return canOpenModel(node, variables) || sourceOrigin(node, variables, metadataProvider) != null;
  }

  /** Open the table dialog on a Data Vault, Business Vault, or dimensional model. */
  public static boolean canOpenTable(LineageNode node, IVariables variables) {
    return canOpenModel(node, variables);
  }

  /**
   * Open the source card on a source model. Reads the catalog; do not call this while building a
   * menu.
   */
  public static boolean canOpenSource(
      LineageNode node, IVariables variables, IHopMetadataProvider metadataProvider) {
    return sourceOrigin(node, variables, metadataProvider) != null;
  }

  /**
   * Menu check for {@code Open source}. Uses the catalog key already on the node and skips a
   * warehouse table that already opens with {@link #canOpenTable}.
   */
  public static boolean canOfferOpenSource(LineageNode node, IVariables variables) {
    if (canOpenTable(node, variables)) {
      return false;
    }
    return hasCatalogIdentity(node);
  }

  /**
   * Menu check for {@code Preview rows}. A database location or a catalog key already on the node
   * is enough. The catalog record is read when the action runs.
   */
  public static boolean canOfferPreviewRows(LineageNode node) {
    return databaseLocation(node) != null || parseCatalogKey(catalogKey(node)) != null;
  }

  /**
   * Fills a missing catalog key from the job path already on the canvas. {@code
   * dm/retail-f-orders/f_orders} is namespace {@code hop/{project}/dimensional/retail-f-orders}. A
   * linked dataset such as {@code d_order} uses that namespace and its own name. No catalog or
   * Marquez read.
   */
  public static LineageNode withCatalogKey(
      LineageGraph graph, LineageNode node, IVariables variables) {
    if (node == null || !Utils.isEmpty(catalogKey(node))) {
      return node;
    }
    String recordName = tableName(node);
    LineageNode modelJob = modelJob(graph, node);
    HopExportFacet export = modelJob != null ? modelJob.getHopExport() : hopExport(node);
    JobPath path = parseJobPath(modelJob != null ? modelJob.getName() : node.getName());
    String layer = export != null ? export.getModelLayer() : null;
    String modelName = export != null ? export.getModelName() : null;
    if (path != null) {
      if (Utils.isEmpty(layer)) {
        layer = path.layer();
      }
      if (Utils.isEmpty(modelName)) {
        modelName = path.model();
      }
      if (Utils.isEmpty(recordName)) {
        recordName = path.table();
      }
    }
    String segment = layerSegment(layer);
    if (segment == null || Utils.isEmpty(modelName) || Utils.isEmpty(recordName)) {
      return node;
    }
    String project =
        export != null && !Utils.isEmpty(export.getProjectKey())
            ? export.getProjectKey()
            : DvCatalogNamespaces.resolveProjectKey(variables);
    String key =
        "hop/"
            + sanitizeCatalogSegment(project)
            + "/"
            + segment
            + "/"
            + sanitizeCatalogSegment(modelName)
            + "/"
            + recordName;
    String connection = catalogConnection(node);
    if (Utils.isEmpty(connection) && export != null) {
      connection = export.getCatalogConnection();
    }
    HopLocationFacet location = node.getHopLocation();
    HopLocationFacet.HopLocationFacetBuilder builder =
        location != null ? location.toBuilder() : HopLocationFacet.builder();
    builder.catalogKey(key);
    if (!Utils.isEmpty(connection)
        && (location == null || Utils.isEmpty(location.getCatalogConnection()))) {
      builder.catalogConnection(connection);
    }
    return node.toBuilder().hopLocation(builder.build()).build();
  }

  public static boolean canPreviewRows(
      LineageNode node, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (databaseLocation(node) != null) {
      return true;
    }
    RecordDefinition definition = catalogDefinition(node, variables, metadataProvider);
    return definition != null && RecordDefinitionPreviewSupport.supportsPreview(definition);
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
    openModel(hopGui, variables, null, node);
  }

  public static void openModel(
      HopGui hopGui, IVariables variables, IHopMetadataProvider metadataProvider, LineageNode node)
      throws HopException {
    if (toRecordOrigin(node, variables) != null) {
      openModelHandler(hopGui, variables, node, true);
      return;
    }
    RecordOrigin source = sourceOrigin(node, variables, metadataProvider);
    if (source == null) {
      throw new HopException("Lineage node has no model location");
    }
    RecordOriginNavigationSupport.openOrigin(hopGui, source, variables, true);
  }

  public static void openTable(HopGui hopGui, IVariables variables, LineageNode node)
      throws HopException {
    IHopFileTypeHandler handler = openModelHandler(hopGui, variables, node, true);
    String tableName = firstNonEmpty(logicalName(node), physicalTableName(node));
    if (Utils.isEmpty(tableName) && node != null) {
      tableName = node.getName();
    }
    String name = tableName;
    runAfterUiSettles(hopGui, () -> openTableEditor(handler, name));
  }

  public static void openSource(
      HopGui hopGui, IVariables variables, IHopMetadataProvider metadataProvider, LineageNode node)
      throws HopException {
    RecordOrigin source = sourceOrigin(node, variables, metadataProvider);
    if (source == null) {
      throw new HopException("Lineage node has no source model location");
    }
    IHopFileTypeHandler handler =
        RecordOriginNavigationSupport.openOrigin(hopGui, source, variables, true);
    if (handler instanceof HopGuiSourceModelGraph sourceGraph) {
      String element = source.getModelElementName();
      runAfterUiSettles(hopGui, () -> sourceGraph.openSearchComponent(element));
    }
  }

  public static void previewRows(
      HopGui hopGui, IVariables variables, IHopMetadataProvider metadataProvider, LineageNode node)
      throws HopException {
    HopLocationFacet location = databaseLocation(node);
    if (location != null) {
      previewDatabaseLocation(variables, metadataProvider, location);
      return;
    }
    String connection = catalogConnection(node);
    if (Utils.isEmpty(connection)) {
      connection = soleCatalogConnection(metadataProvider);
    }
    RecordDefinition definition = readCatalog(node, variables, metadataProvider, connection);
    if (definition == null || !RecordDefinitionPreviewSupport.supportsPreview(definition)) {
      throw new HopException("Lineage node has no table to preview");
    }
    RecordDefinitionPreviewRunner.run(
        hopGui != null ? hopGui.getShell() : null, definition, variables, metadataProvider);
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

  private static void openTableEditor(IHopFileTypeHandler handler, String tableName) {
    if (Utils.isEmpty(tableName) || handler == null) {
      return;
    }
    if (handler instanceof HopGuiVaultGraph vaultGraph) {
      vaultGraph.editNamedTable(tableName);
    } else if (handler instanceof HopGuiBusinessVaultGraph businessVaultGraph) {
      businessVaultGraph.editNamedTable(tableName);
    } else if (handler instanceof HopGuiDimensionalModelGraph dimensionalGraph) {
      dimensionalGraph.editNamedTable(tableName);
    }
  }

  private static void previewDatabaseLocation(
      IVariables variables, IHopMetadataProvider metadataProvider, HopLocationFacet location)
      throws HopException {
    if (metadataProvider == null) {
      throw new HopException("No metadata provider is available for the preview");
    }
    String connectionName = resolve(variables, location.getConnectionName());
    IHopMetadataSerializer<DatabaseMeta> serializer =
        metadataProvider.getSerializer(DatabaseMeta.class);
    DatabaseMeta databaseMeta = serializer.load(connectionName);
    if (databaseMeta == null) {
      throw new HopException("Database connection not found: " + connectionName);
    }
    String sql =
        ModelTargetTablePreviewSupport.previewSelectSql(
            databaseMeta,
            variables,
            resolve(variables, location.getSchemaName()),
            resolve(variables, location.getTableName()),
            ModelTargetTablePreviewSupport.DEFAULT_PREVIEW_ROW_LIMIT);
    DatabaseWorkbenchDialog.openSql(databaseMeta, sql);
  }

  private static boolean hasCatalogIdentity(LineageNode node) {
    return parseCatalogKey(catalogKey(node)) != null && !Utils.isEmpty(catalogConnection(node));
  }

  private static HopLocationFacet databaseLocation(LineageNode node) {
    HopLocationFacet location = hopLocation(node);
    if (location == null
        || Utils.isEmpty(location.getConnectionName())
        || Utils.isEmpty(location.getTableName())) {
      return null;
    }
    String kind = location.getKind();
    if (!Utils.isEmpty(kind) && !"DATABASE".equalsIgnoreCase(kind)) {
      return null;
    }
    return location;
  }

  private static RecordDefinition catalogDefinition(
      LineageNode node, IVariables variables, IHopMetadataProvider metadataProvider) {
    return readCatalog(node, variables, metadataProvider, catalogConnection(node));
  }

  private static RecordDefinition readCatalog(
      LineageNode node,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String connection) {
    if (metadataProvider == null) {
      return null;
    }
    RecordDefinitionKey key = parseCatalogKey(catalogKey(node));
    if (key == null || Utils.isEmpty(connection)) {
      return null;
    }
    try {
      return RecordDefinitionRegistry.getInstance()
          .read(connection, key, variables, metadataProvider);
    } catch (Exception e) {
      return null;
    }
  }

  private static String soleCatalogConnection(IHopMetadataProvider metadataProvider)
      throws HopException {
    if (metadataProvider == null) {
      return null;
    }
    List<String> names = metadataProvider.getSerializer(DataCatalogMeta.class).listObjectNames();
    if (names == null || names.size() != 1) {
      return null;
    }
    return names.get(0);
  }

  private static String tableName(LineageNode node) {
    if (node == null) {
      return null;
    }
    if (node.getKind() == LineageNodeKind.DATASET && !Utils.isEmpty(node.getName())) {
      return node.getName();
    }
    JobPath path = parseJobPath(node.getName());
    if (path != null) {
      return path.table();
    }
    return firstNonEmpty(logicalName(node), node.getName());
  }

  /** Writer job when its table matches, otherwise any neighboring job. */
  private static LineageNode modelJob(LineageGraph graph, LineageNode node) {
    if (node == null || node.getKind() == LineageNodeKind.JOB) {
      return node;
    }
    if (graph == null || node.getId() == null) {
      return null;
    }
    LineageNode named = LineageViewNodeAppearance.producingJob(graph, node);
    if (named != null) {
      return named;
    }
    LineageNode neighbor = null;
    for (LineageEdge edge : graph.getEdgesOrEmpty()) {
      if (edge == null) {
        continue;
      }
      boolean writtenByJob = node.getId().equals(edge.getToNodeId());
      boolean readByJob = node.getId().equals(edge.getFromNodeId());
      if (!writtenByJob && !readByJob) {
        continue;
      }
      LineageNode other = graph.findNode(writtenByJob ? edge.getFromNodeId() : edge.getToNodeId());
      if (other == null
          || other.getKind() != LineageNodeKind.JOB
          || parseJobPath(other.getName()) == null) {
        continue;
      }
      if (writtenByJob) {
        return other;
      }
      neighbor = other;
    }
    return neighbor;
  }

  private record JobPath(String layer, String model, String table) {}

  private static JobPath parseJobPath(String name) {
    if (Utils.isEmpty(name)) {
      return null;
    }
    String[] parts = name.split("/");
    if (parts.length < 3) {
      return null;
    }
    String layer = parts[0];
    if (layerSegment(layer) == null) {
      return null;
    }
    return new JobPath(layer, parts[1], parts[parts.length - 1]);
  }

  private static String layerSegment(String layer) {
    if (Utils.isEmpty(layer)) {
      return null;
    }
    return switch (layer.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "dm", "dimensional" -> "dimensional";
      case "bv", "business-vault" -> "business-vault";
      case "dv", "models" -> "models";
      default -> null;
    };
  }

  private static String sanitizeCatalogSegment(String value) {
    if (Utils.isEmpty(value)) {
      return "unknown";
    }
    return value.replace('\\', '_').replace('/', '_').replace(' ', '_');
  }

  /** Source-model origin stored on the catalog record, when this node points at one. */
  static RecordOrigin sourceOrigin(
      LineageNode node, IVariables variables, IHopMetadataProvider metadataProvider) {
    RecordDefinition definition = catalogDefinition(node, variables, metadataProvider);
    if (definition == null || definition.getOrigin() == null) {
      return null;
    }
    RecordOrigin origin = definition.getOrigin();
    if (!RecordOriginNavigationSupport.MODEL_TYPE_SOURCE_MODEL.equals(origin.getModelType())) {
      return null;
    }
    if (Utils.isEmpty(origin.getModelFilename())) {
      return null;
    }
    return RecordOriginNavigationSupport.canNavigateToOrigin(origin, variables) ? origin : null;
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
