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
package org.apache.hop.datavault.hopgui.perspective.journey;

import org.apache.hop.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.apache.hop.catalog.hopgui.perspective.DataCatalogPerspective;
import org.apache.hop.catalog.hopgui.perspective.SchemaHarvestHistoryGuiSupport;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.versioning.CatalogVersionGuiSupport;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.hopgui.resourcedefinition.ResourceDefinitionValidationGuiSupport;
import org.apache.hop.datavault.hopgui.search.ModelSearchOpenSupport;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.HopFileTypeRegistry;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.perspective.metadata.MetadataPerspective;

/** Opens existing Hop surfaces from an EDW Journey tree node. */
public final class EdwJourneyNavigationSupport {

  private EdwJourneyNavigationSupport() {}

  public static boolean canOpenPrimary(EdwJourneyTreeNode node, IVariables variables) {
    if (node == null) {
      return false;
    }
    return switch (node.kind()) {
      case GROUP -> !Utils.isEmpty(node.label());
      case SOURCE_MODEL, MODEL, MODEL_TABLE -> canOpenModel(node, variables);
      case CATALOG_FEED -> node.catalogKey() != null && !Utils.isEmpty(node.catalogConnection());
      case WORKFLOW, WORKFLOW_ACTION, OUTPUT_FILE -> !Utils.isEmpty(node.storedPath());
      default -> false;
    };
  }

  public static void openPrimary(
      HopGui hopGui, ResourceDefinitionGroupMeta group, EdwJourneyTreeNode node)
      throws HopException {
    if (hopGui == null || node == null) {
      return;
    }
    IVariables variables = hopGui.getVariables();
    switch (node.kind()) {
      case GROUP -> openGroupMetadata(hopGui, groupName(group, node));
      case SOURCE_MODEL, MODEL, MODEL_TABLE -> openModel(hopGui, node, variables);
      case CATALOG_FEED -> openCatalog(node);
      case WORKFLOW, WORKFLOW_ACTION, OUTPUT_FILE -> openFile(hopGui, node.storedPath(), variables);
      case CONTROL -> openControl(hopGui, group, node);
      case CATALOG_VERSION -> {
        if (group != null) {
          CatalogVersionGuiSupport.listVersionsForGroup(hopGui, group);
        }
      }
      default -> {
        // Stages and folders have no single artifact to open.
      }
    }
  }

  public static void openGroupMetadata(HopGui hopGui, String groupName) throws HopException {
    if (hopGui == null || Utils.isEmpty(groupName)) {
      return;
    }
    MetadataPerspective metadataPerspective = HopGui.getMetadataPerspective();
    if (metadataPerspective != null) {
      metadataPerspective.activate();
    }
    MetadataManager<ResourceDefinitionGroupMeta> manager =
        new MetadataManager<>(
            hopGui.getVariables(),
            hopGui.getMetadataProvider(),
            ResourceDefinitionGroupMeta.class,
            hopGui.getShell());
    manager.editMetadata(groupName);
  }

  public static void openCatalog(EdwJourneyTreeNode node) throws HopException {
    if (node == null || node.catalogKey() == null || Utils.isEmpty(node.catalogConnection())) {
      return;
    }
    DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
    if (perspective == null) {
      throw new HopException("Data Catalog perspective is not available");
    }
    perspective.selectRecordDefinition(node.catalogConnection(), node.catalogKey());
  }

  public static void openHarvestHistory(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    SchemaHarvestHistoryGuiSupport.openForGroup(hopGui, group);
  }

  public static void openValidateSources(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    ResourceDefinitionValidationGuiSupport.validateAndShowResults(hopGui, group);
  }

  public static void openBrowseLineage(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    new org.apache.hop.datavault.hopgui.lineage.ReverseLineageBrowserDialog(
            hopGui.getShell(), hopGui, group, hopGui.getVariables(), hopGui.getMetadataProvider())
        .open();
  }

  public static void openListVersions(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    CatalogVersionGuiSupport.listVersionsForGroup(hopGui, group);
  }

  public static ResourceDefinitionGroupMeta newGroup(HopGui hopGui) {
    if (hopGui == null) {
      return null;
    }
    MetadataManager<ResourceDefinitionGroupMeta> manager =
        new MetadataManager<>(
            hopGui.getVariables(),
            hopGui.getMetadataProvider(),
            ResourceDefinitionGroupMeta.class,
            hopGui.getShell());
    return manager.newMetadata();
  }

  private static void openControl(
      HopGui hopGui, ResourceDefinitionGroupMeta group, EdwJourneyTreeNode node) {
    if (node.control() == null) {
      return;
    }
    switch (node.control()) {
      case HARVEST -> openHarvestHistory(hopGui, group);
      case SCHEMA_GATE -> openValidateSources(hopGui, group);
      case CATALOG_VERSION -> openListVersions(hopGui, group);
      case SOURCE_QUALITY -> {
        // Phase 2: quality history overlay. No dedicated browser from a group today.
      }
    }
  }

  private static void openModel(HopGui hopGui, EdwJourneyTreeNode node, IVariables variables)
      throws HopException {
    RecordOrigin origin = toOrigin(node);
    if (!RecordOriginNavigationSupport.canNavigateToOrigin(origin, variables)) {
      throw new HopException("Model file is not available: " + node.storedPath());
    }
    RecordOriginNavigationSupport.navigateToOrigin(hopGui, origin, variables);
  }

  private static void openFile(HopGui hopGui, String storedPath, IVariables variables)
      throws HopException {
    if (Utils.isEmpty(storedPath)) {
      return;
    }
    String resolved = HopVfs.normalize(variables.resolve(storedPath));
    IHopFileType fileType = HopFileTypeRegistry.getInstance().findHopFileType(resolved);
    if (fileType == null) {
      throw new HopException("No Hop file type for " + resolved);
    }
    ModelSearchOpenSupport.openModelFile(resolved, fileType);
  }

  static RecordOrigin toOrigin(EdwJourneyTreeNode node) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelFilename(node.storedPath());
    origin.setModelElementName(node.tableName());
    origin.setModelType(mapModelType(node.modelType()));
    return origin;
  }

  static String mapModelType(String modelType) {
    if (EdwJourneySnapshot.MODEL_TYPE_SOURCE.equals(modelType)) {
      return RecordOriginNavigationSupport.MODEL_TYPE_SOURCE_MODEL;
    }
    if (EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT.equals(modelType)) {
      return RecordOriginNavigationSupport.MODEL_TYPE_DATA_VAULT;
    }
    if (EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT.equals(modelType)) {
      return RecordOriginNavigationSupport.MODEL_TYPE_BUSINESS_VAULT;
    }
    if (EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL.equals(modelType)) {
      return RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL;
    }
    return modelType;
  }

  private static boolean canOpenModel(EdwJourneyTreeNode node, IVariables variables) {
    return RecordOriginNavigationSupport.canNavigateToOrigin(toOrigin(node), variables);
  }

  private static String groupName(ResourceDefinitionGroupMeta group, EdwJourneyTreeNode node) {
    if (group != null && !Utils.isEmpty(group.getName())) {
      return group.getName();
    }
    if (node != null && !Utils.isEmpty(node.label())) {
      return node.label();
    }
    return null;
  }
}
