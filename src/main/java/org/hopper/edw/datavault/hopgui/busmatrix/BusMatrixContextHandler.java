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
package org.hopper.edw.datavault.hopgui.busmatrix;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.hopper.edw.catalog.hopgui.perspective.DataCatalogPerspective;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.datavault.busmatrix.BusMatrixColumn;
import org.hopper.edw.datavault.busmatrix.BusMatrixHit;
import org.hopper.edw.datavault.busmatrix.BusMatrixRow;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.hopgui.EdwDocsWebSupport;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.hopper.edw.datavault.hopgui.lineageview.LineageViewLaunchSupport;
import org.hopper.edw.datavault.lineage.LineageLayer;

/** Context actions handler for clicks on facts, dimensions, and cross-section cells. */
public class BusMatrixContextHandler extends BaseGuiContextHandler implements IGuiContextHandler {

  public static final String CONTEXT_ID = "BusMatrixContextHandler";
  private static final Class<?> PKG = BusMatrixLaunchSupport.class;

  private final HopGui hopGui;
  private final Shell shell;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final BusMatrixHit hit;

  public BusMatrixContextHandler(
      HopGui hopGui,
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      BusMatrixHit hit) {
    this.hopGui = hopGui;
    this.shell = shell;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.hit = hit;
  }

  @Override
  public String getContextId() {
    return CONTEXT_ID;
  }

  @Override
  public List<GuiAction> getSupportedActions() {
    List<GuiAction> actions = new ArrayList<>();
    if (hit == null || hit.getType() == BusMatrixHit.HitType.NONE) {
      return actions;
    }

    if (hit.isFact()) {
      addFactActions(actions, hit.getRow(), null, "1");
    } else if (hit.isDimension()) {
      addDimensionActions(actions, hit.getColumn(), null, "1");
    } else if (hit.isCell()) {
      String factCategory =
          BaseMessages.getString(PKG, "BusMatrixContextHandler.Category.Fact", hit.getRow().factName());
      addFactActions(actions, hit.getRow(), factCategory, "1");

      String dimCategory =
          BaseMessages.getString(
              PKG, "BusMatrixContextHandler.Category.Dimension", hit.getColumn().label());
      addDimensionActions(actions, hit.getColumn(), dimCategory, "2");
    }
    return actions;
  }

  private void addFactActions(
      List<GuiAction> actions, BusMatrixRow row, String category, String categoryOrder) {
    String name = row.factName();
    String filename = row.modelFilename();
    String model = row.modelName();

    // 1. Open model and element
    GuiAction openAction =
        new GuiAction(
            "bus-matrix-fact-open-" + name,
            GuiActionType.Modify,
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.OpenElement.Name", name),
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.OpenElement.Tooltip", name),
            "fact.svg",
            (shiftClicked, controlClicked, t) -> openElement(filename, name));
    openAction.setCategory(category);
    openAction.setCategoryOrder(categoryOrder);
    actions.add(openAction);

    // 2. Lineage
    GuiAction lineageAction =
        new GuiAction(
            "bus-matrix-fact-lineage-" + name,
            GuiActionType.Info,
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Lineage.Name", name),
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Lineage.Tooltip", name),
            "lineage-view.svg",
            (shiftClicked, controlClicked, t) -> openLineage(model, name, filename));
    lineageAction.setCategory(category);
    lineageAction.setCategoryOrder(categoryOrder);
    actions.add(lineageAction);

    // 3. Documentation
    GuiAction docAction =
        new GuiAction(
            "bus-matrix-fact-doc-" + name,
            GuiActionType.Info,
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Documentation.Name", name),
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Documentation.Tooltip", name),
            "ui/images/help.svg",
            (shiftClicked, controlClicked, t) -> openDocumentation(name));
    docAction.setCategory(category);
    docAction.setCategoryOrder(categoryOrder);
    actions.add(docAction);

    // 4. Operational report
    GuiAction reportAction =
        new GuiAction(
            "bus-matrix-fact-ops-" + name,
            GuiActionType.Info,
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.OperationalReport.Name", name),
            BaseMessages.getString(
                PKG, "BusMatrixContextHandler.Action.OperationalReport.Tooltip", name),
            "ui/images/run.svg",
            (shiftClicked, controlClicked, t) -> openOperationalReport(name, model, filename));
    reportAction.setCategory(category);
    reportAction.setCategoryOrder(categoryOrder);
    actions.add(reportAction);

    // 5. Data Catalog (when applicable)
    if (row.hasRecordDefinitionSource()) {
      GuiAction catalogAction =
          new GuiAction(
              "bus-matrix-fact-catalog-" + name,
              GuiActionType.Info,
              BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Catalog.Name", name),
              BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Catalog.Tooltip", name),
              "data-catalog.svg",
              (shiftClicked, controlClicked, t) ->
                  openCatalog(row.recordDefinitionNamespace(), row.recordDefinitionName()));
      catalogAction.setCategory(category);
      catalogAction.setCategoryOrder(categoryOrder);
      actions.add(catalogAction);
    }
  }

  private void addDimensionActions(
      List<GuiAction> actions, BusMatrixColumn column, String category, String categoryOrder) {
    String name = column.dimensionName();
    String label = column.label();
    String filename = column.modelFilename();

    // 1. Open model and element
    GuiAction openAction =
        new GuiAction(
            "bus-matrix-dim-open-" + name,
            GuiActionType.Modify,
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.OpenElement.Name", label),
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.OpenElement.Tooltip", label),
            "dimension.svg",
            (shiftClicked, controlClicked, t) -> openElement(filename, name));
    openAction.setCategory(category);
    openAction.setCategoryOrder(categoryOrder);
    actions.add(openAction);

    // 2. Lineage
    GuiAction lineageAction =
        new GuiAction(
            "bus-matrix-dim-lineage-" + name,
            GuiActionType.Info,
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Lineage.Name", label),
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Lineage.Tooltip", label),
            "lineage-view.svg",
            (shiftClicked, controlClicked, t) -> openLineage(label, name, filename));
    lineageAction.setCategory(category);
    lineageAction.setCategoryOrder(categoryOrder);
    actions.add(lineageAction);

    // 3. Documentation
    GuiAction docAction =
        new GuiAction(
            "bus-matrix-dim-doc-" + name,
            GuiActionType.Info,
            BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Documentation.Name", label),
            BaseMessages.getString(
                PKG, "BusMatrixContextHandler.Action.Documentation.Tooltip", label),
            "ui/images/help.svg",
            (shiftClicked, controlClicked, t) -> openDocumentation(name));
    docAction.setCategory(category);
    docAction.setCategoryOrder(categoryOrder);
    actions.add(docAction);

    // 4. Operational report
    GuiAction reportAction =
        new GuiAction(
            "bus-matrix-dim-ops-" + name,
            GuiActionType.Info,
            BaseMessages.getString(
                PKG, "BusMatrixContextHandler.Action.OperationalReport.Name", label),
            BaseMessages.getString(
                PKG, "BusMatrixContextHandler.Action.OperationalReport.Tooltip", label),
            "ui/images/run.svg",
            (shiftClicked, controlClicked, t) -> openOperationalReport(name, label, filename));
    reportAction.setCategory(category);
    reportAction.setCategoryOrder(categoryOrder);
    actions.add(reportAction);

    // 5. Data Catalog (when applicable)
    if (column.hasRecordDefinitionSource()) {
      GuiAction catalogAction =
          new GuiAction(
              "bus-matrix-dim-catalog-" + name,
              GuiActionType.Info,
              BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Catalog.Name", label),
              BaseMessages.getString(PKG, "BusMatrixContextHandler.Action.Catalog.Tooltip", label),
              "data-catalog.svg",
              (shiftClicked, controlClicked, t) ->
                  openCatalog(
                      column.recordDefinitionNamespace(), column.recordDefinitionName()));
      catalogAction.setCategory(category);
      catalogAction.setCategoryOrder(categoryOrder);
      actions.add(catalogAction);
    }
  }

  private void openElement(String filename, String element) {
    if (Utils.isEmpty(filename)) {
      return;
    }
    try {
      RecordOrigin origin = new RecordOrigin();
      origin.setModelType(RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL);
      origin.setModelFilename(filename);
      origin.setModelElementName(element);
      IHopFileTypeHandler handler =
          RecordOriginNavigationSupport.openOrigin(hopGui, origin, variables, true);
      if (handler instanceof HopGuiDimensionalModelGraph graph) {
        graph.openSearchComponent(element);
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Open"),
          e);
    }
  }

  private void openLineage(String modelName, String tableName, String modelFilename) {
    try {
      LineageViewLaunchSupport.openFromTable(
          hopGui, variables, LineageLayer.DM, modelName, tableName, modelFilename, null);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixContextHandler.Error.Lineage"),
          e);
    }
  }

  private void openDocumentation(String tableName) {
    try {
      String docRelPath = DocPaths.tableHref("dm", tableName);
      String targetFolder =
          variables != null
              ? variables.resolve("${PROJECT_HOME}/work/documentation")
              : "work/documentation";
      String fullPath = targetFolder + "/" + docRelPath;
      FileObject docFile = HopVfs.getFileObject(fullPath);
      if (!docFile.exists()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
        box.setText(BaseMessages.getString(PKG, "BusMatrixContextHandler.DocNotFound.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "BusMatrixContextHandler.DocNotFound.Message", tableName, fullPath));
        box.open();
        return;
      }
      String filename = HopVfs.getFilename(docFile);
      if (EnvironmentUtils.getInstance().isWeb()) {
        EdwDocsWebSupport.openInBrowser(filename);
      } else {
        EnvironmentUtils.getInstance().openUrl(java.nio.file.Path.of(filename).toUri().toString());
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixContextHandler.Error.Documentation"),
          e);
    }
  }

  private void openOperationalReport(String tableName, String modelName, String modelFilename) {
    try {
      BusMatrixOperationalReportDialog dialog =
          new BusMatrixOperationalReportDialog(
              shell, tableName, modelName, modelFilename, variables, metadataProvider);
      dialog.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixContextHandler.Error.OperationalReport"),
          e);
    }
  }

  private void openCatalog(String namespace, String name) {
    try {
      String connectionName =
          DvSourceCatalogService.resolvePreferredCatalogConnection(null, variables, metadataProvider);
      DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
      if (perspective == null) {
        throw new HopException("Data Catalog perspective is not available");
      }
      perspective.selectRecordDefinition(connectionName, new RecordDefinitionKey(namespace, name));
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixContextHandler.Error.Catalog"),
          e);
    }
  }
}
