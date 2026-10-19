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
package org.hopper.edw.datavault.hopgui.perspective.journey.link;

import java.util.function.Consumer;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.HopFileTypeRegistry;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.hopper.edw.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.hopper.edw.catalog.hopgui.perspective.DataCatalogPerspective;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.datavault.hopgui.EdwDocsGuiPlugin;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.hopper.edw.datavault.hopgui.opsanalytics.OpsAnalyticsDialog;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyCreateSupport;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyNavigationSupport;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyPerspective;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.datavault.hopgui.perspective.journey.link.EdwJourneyLinkExecutor.EdwJourneyNavigator;
import org.hopper.edw.datavault.hopgui.search.ModelSearchOpenSupport;

/** Hop GUI implementation of presentation links from the EDW Journey. */
public final class EdwJourneyGuiNavigator implements EdwJourneyNavigator {

  private static final Class<?> PKG = EdwJourneyPerspective.class;

  private final HopGui hopGui;
  private final ResourceDefinitionGroupMeta group;
  private final EdwJourneySnapshot snapshot;
  private final Consumer<String> showPanel;

  public EdwJourneyGuiNavigator(
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      EdwJourneySnapshot snapshot,
      Consumer<String> showPanel) {
    this.hopGui = hopGui;
    this.group = group;
    this.snapshot = snapshot;
    this.showPanel = showPanel;
  }

  @Override
  public void openLayer(String layer, String file, String element, String verb) {
    try {
      IVariables variables = hopGui.getVariables();
      String resolved = Utils.isEmpty(file) ? "" : HopVfs.normalize(variables.resolve(file));
      switch (layer == null ? "" : layer) {
        case "catalog" -> openCatalog(file, element);
        case "workflow", "hsl", "hlv" -> openFile(resolved);
        case "hsm", "dv", "bv", "dm" -> openModel(layer, file, element, verb, variables);
        default -> fail("Unknown layer " + layer);
      }
    } catch (Exception e) {
      fail(e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  @Override
  public void runAction(String action) {
    try {
      switch (action) {
        case "harvest-history" -> EdwJourneyNavigationSupport.openHarvestHistory(hopGui, group);
        case "validate-sources" -> EdwJourneyNavigationSupport.openValidateSources(hopGui, group);
        case "list-versions" -> EdwJourneyNavigationSupport.openListVersions(hopGui, group);
        case "browse-lineage" -> EdwJourneyNavigationSupport.openBrowseLineage(hopGui, group);
        case "edit-group" ->
            EdwJourneyNavigationSupport.openGroupMetadata(
                hopGui, group == null ? null : group.getName());
        case "create-group" -> EdwJourneyNavigationSupport.newGroup(hopGui);
        case "create-source-model" -> EdwJourneyCreateSupport.newSourceModel(hopGui);
        case "generate-dv" ->
            EdwJourneyCreateSupport.generateDataVault(hopGui, group, snapshot, null);
        case "create-ops-analytics" -> OpsAnalyticsDialog.open(hopGui);
        case "open-docs" -> EdwDocsGuiPlugin.openHtml(hopGui, "index.html");
        default -> fail("Unknown action " + action);
      }
    } catch (Exception e) {
      fail(e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  @Override
  public void showPanel(String panelId) {
    if (showPanel != null) {
      showPanel.accept(panelId);
    }
  }

  @Override
  public void fail(String message) {
    if (hopGui == null || hopGui.getShell() == null || hopGui.getShell().isDisposed()) {
      return;
    }
    new ErrorDialog(
        hopGui.getShell(),
        BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Open.Title"),
        BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Open.Message"),
        new HopException(message == null ? "" : message));
  }

  private void openCatalog(String connection, String element) throws HopException {
    int split = element == null ? -1 : element.indexOf("::");
    if (split < 1 || Utils.isEmpty(connection)) {
      throw new HopException("Catalog link needs a connection and namespace::name");
    }
    DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
    if (perspective == null) {
      throw new HopException("Data Catalog perspective is not available");
    }
    perspective.selectRecordDefinition(
        connection,
        new RecordDefinitionKey(element.substring(0, split), element.substring(split + 2)));
  }

  private void openFile(String resolved) throws HopException {
    if (Utils.isEmpty(resolved)) {
      throw new HopException("No file to open");
    }
    IHopFileType fileType = HopFileTypeRegistry.getInstance().findHopFileType(resolved);
    if (fileType == null) {
      throw new HopException("No Hop file type for " + resolved);
    }
    ModelSearchOpenSupport.openModelFile(resolved, fileType);
  }

  private void openModel(
      String layer, String file, String element, String verb, IVariables variables)
      throws HopException {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelFilename(file);
    origin.setModelElementName(element);
    origin.setModelType(modelType(layer));
    boolean select = "select".equals(verb) && !Utils.isEmpty(element);
    IHopFileTypeHandler handler =
        RecordOriginNavigationSupport.openOrigin(hopGui, origin, variables, select);
    if (Utils.isEmpty(element) || "select".equals(verb)) {
      return;
    }
    switch (verb) {
      case "edit" -> edit(handler, element);
      case "browse" -> {
        if (handler instanceof HopGuiSourceModelGraph graph) {
          graph.browseNamedElement(element);
        }
      }
      case "pipeline" -> {
        if (handler instanceof HopGuiDimensionalModelGraph graph) {
          graph.openNamedUpdatePipeline(element);
        }
      }
      case "presentation" -> openFile(HopVfs.normalize(variables.resolve(file)));
      default -> fail("Unknown verb " + verb);
    }
  }

  private static void edit(IHopFileTypeHandler handler, String element) {
    if (handler instanceof HopGuiSourceModelGraph graph) {
      graph.openSearchComponent(element);
    } else if (handler instanceof HopGuiVaultGraph graph) {
      graph.editNamedTable(element);
    } else if (handler instanceof HopGuiBusinessVaultGraph graph) {
      graph.editNamedTable(element);
    } else if (handler instanceof HopGuiDimensionalModelGraph graph) {
      graph.editNamedTable(element);
    }
  }

  private static String modelType(String layer) {
    return switch (layer) {
      case "hsm" -> RecordOriginNavigationSupport.MODEL_TYPE_SOURCE_MODEL;
      case "bv" -> RecordOriginNavigationSupport.MODEL_TYPE_BUSINESS_VAULT;
      case "dm" -> RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL;
      default -> RecordOriginNavigationSupport.MODEL_TYPE_DATA_VAULT;
    };
  }
}
