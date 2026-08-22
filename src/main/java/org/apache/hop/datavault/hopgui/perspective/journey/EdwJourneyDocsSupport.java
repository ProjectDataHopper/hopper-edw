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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;

/**
 * Maps an EDW Journey tree node to plugin HTML documentation pages under {@code docs/}.
 *
 * <p>Pages are opened with {@link org.apache.hop.datavault.hopgui.EdwDocsGuiPlugin#openHtml}.
 */
public final class EdwJourneyDocsSupport {

  public static final String PAGE_EDW_JOURNEY = "edw-journey.html";
  public static final String PAGE_GETTING_STARTED = "getting-started-edw.html";
  public static final String PAGE_RESOURCE_DEFINITION_GROUP = "resource-definition-group.html";
  public static final String PAGE_SOURCE_MODELER = "source-modeler-overview.html";
  public static final String PAGE_GENERATE_DATA_VAULT =
      "generating-data-vault-from-source-model.html";
  public static final String PAGE_DATA_CATALOG = "data-catalog.html";
  public static final String PAGE_DATAVAULT_SOURCE = "datavault-source.html";
  public static final String PAGE_METADATA_HARVESTING = "metadata-harvesting.html";
  public static final String PAGE_SCHEMA_GATE = "resource-definition-validation.html";
  public static final String PAGE_DATA_QUALITY = "data-quality.html";
  public static final String PAGE_DATA_VAULT = "datavault-plugin.html";
  public static final String PAGE_BUSINESS_VAULT = "business-vault-overview.html";
  public static final String PAGE_DIMENSIONAL = "dimensional-modeler-overview.html";
  public static final String PAGE_OPERATIONS = "operations.html";
  public static final String PAGE_UPDATE_RESOURCE_GROUP =
      "update-resource-definition-group-action.html";
  public static final String PAGE_DATA_VAULT_UPDATE = "datavault-update-action.html";
  public static final String PAGE_BUSINESS_VAULT_UPDATE = "business-vault-update-action.html";
  public static final String PAGE_DIMENSIONAL_UPDATE = "dimensional-update-action.html";
  public static final String PAGE_EXECUTION_MAPS = "execution-maps.html";
  public static final String PAGE_LINEAGE_VIEW = "hop-lineage-view.html";
  public static final String PAGE_SOURCE_TO_TARGET_LINEAGE = "source-to-target-lineage.html";
  public static final String PAGE_OPENLINEAGE = "openlineage-export.html";
  public static final String PAGE_ARCHITECTURE_EXPORT = "architecture-export.html";
  public static final String PAGE_DBT_IMPORT = "dbt-import.html";
  public static final String PAGE_DATAVAULT_CONFIGURATION = "datavault-configuration.html";

  public static final String KEY_EDW_JOURNEY = "EdwJourneyDocsSupport.EdwJourney";
  public static final String KEY_GETTING_STARTED = "EdwJourneyDocsSupport.GettingStarted";
  public static final String KEY_RESOURCE_DEFINITION_GROUP =
      "EdwJourneyDocsSupport.ResourceDefinitionGroup";
  public static final String KEY_SOURCE_MODELER = "EdwJourneyDocsSupport.SourceModeler";
  public static final String KEY_GENERATE_DATA_VAULT = "EdwJourneyDocsSupport.GenerateDataVault";
  public static final String KEY_DATA_CATALOG = "EdwJourneyDocsSupport.DataCatalog";
  public static final String KEY_DATAVAULT_SOURCE = "EdwJourneyDocsSupport.DatavaultSource";
  public static final String KEY_METADATA_HARVESTING = "EdwJourneyDocsSupport.MetadataHarvesting";
  public static final String KEY_SCHEMA_GATE = "EdwJourneyDocsSupport.SchemaGate";
  public static final String KEY_DATA_QUALITY = "EdwJourneyDocsSupport.DataQuality";
  public static final String KEY_DATA_VAULT = "EdwJourneyDocsSupport.DataVault";
  public static final String KEY_BUSINESS_VAULT = "EdwJourneyDocsSupport.BusinessVault";
  public static final String KEY_DIMENSIONAL = "EdwJourneyDocsSupport.Dimensional";
  public static final String KEY_OPERATIONS = "EdwJourneyDocsSupport.Operations";
  public static final String KEY_UPDATE_RESOURCE_GROUP =
      "EdwJourneyDocsSupport.UpdateResourceGroup";
  public static final String KEY_DATA_VAULT_UPDATE = "EdwJourneyDocsSupport.DataVaultUpdate";
  public static final String KEY_BUSINESS_VAULT_UPDATE =
      "EdwJourneyDocsSupport.BusinessVaultUpdate";
  public static final String KEY_DIMENSIONAL_UPDATE = "EdwJourneyDocsSupport.DimensionalUpdate";
  public static final String KEY_EXECUTION_MAPS = "EdwJourneyDocsSupport.ExecutionMaps";
  public static final String KEY_LINEAGE_VIEW = "EdwJourneyDocsSupport.LineageView";
  public static final String KEY_SOURCE_TO_TARGET_LINEAGE =
      "EdwJourneyDocsSupport.SourceToTargetLineage";
  public static final String KEY_OPENLINEAGE = "EdwJourneyDocsSupport.OpenLineage";
  public static final String KEY_ARCHITECTURE_EXPORT = "EdwJourneyDocsSupport.ArchitectureExport";
  public static final String KEY_DBT_IMPORT = "EdwJourneyDocsSupport.DbtImport";
  public static final String KEY_DATAVAULT_CONFIGURATION =
      "EdwJourneyDocsSupport.DatavaultConfiguration";

  public record DocLink(String htmlPage, String labelKey) {}

  private EdwJourneyDocsSupport() {}

  public static List<DocLink> linksForEmptyProject() {
    List<DocLink> links = new ArrayList<>();
    add(links, PAGE_GETTING_STARTED, KEY_GETTING_STARTED);
    add(links, PAGE_RESOURCE_DEFINITION_GROUP, KEY_RESOURCE_DEFINITION_GROUP);
    add(links, PAGE_EDW_JOURNEY, KEY_EDW_JOURNEY);
    add(links, PAGE_DATAVAULT_CONFIGURATION, KEY_DATAVAULT_CONFIGURATION);
    return List.copyOf(links);
  }

  public static List<DocLink> linksFor(EdwJourneyTreeNode node) {
    if (node == null) {
      return linksForEmptyProject();
    }
    List<DocLink> links = new ArrayList<>();
    switch (node.kind()) {
      case GROUP -> {
        add(links, PAGE_RESOURCE_DEFINITION_GROUP, KEY_RESOURCE_DEFINITION_GROUP);
        add(links, PAGE_EDW_JOURNEY, KEY_EDW_JOURNEY);
        add(links, PAGE_GETTING_STARTED, KEY_GETTING_STARTED);
      }
      case STAGE -> addStage(links, node.stage());
      case SOURCE_MODEL -> {
        add(links, PAGE_SOURCE_MODELER, KEY_SOURCE_MODELER);
        add(links, PAGE_GENERATE_DATA_VAULT, KEY_GENERATE_DATA_VAULT);
      }
      case CATALOG_FEEDS, CATALOG_FEED -> {
        add(links, PAGE_DATA_CATALOG, KEY_DATA_CATALOG);
        add(links, PAGE_DATAVAULT_SOURCE, KEY_DATAVAULT_SOURCE);
      }
      case CONTROL -> addControl(links, node.control());
      case CATALOG_VERSION -> add(links, PAGE_DATA_CATALOG, KEY_DATA_CATALOG);
      case MODEL, MODEL_TABLE -> addModel(links, node.modelType());
      case WORKFLOW -> {
        add(links, PAGE_UPDATE_RESOURCE_GROUP, KEY_UPDATE_RESOURCE_GROUP);
        add(links, PAGE_OPERATIONS, KEY_OPERATIONS);
      }
      case WORKFLOW_ACTION -> addAction(links, node.actionType());
      case OUTPUT_GROUP, OUTPUT_FILE -> addOutput(links, node);
      default -> add(links, PAGE_EDW_JOURNEY, KEY_EDW_JOURNEY);
    }
    if (links.isEmpty()) {
      add(links, PAGE_EDW_JOURNEY, KEY_EDW_JOURNEY);
    }
    return List.copyOf(links);
  }

  private static void addStage(List<DocLink> links, EdwJourneyStage stage) {
    if (stage == null) {
      add(links, PAGE_EDW_JOURNEY, KEY_EDW_JOURNEY);
      return;
    }
    switch (stage) {
      case SOURCES -> {
        add(links, PAGE_SOURCE_MODELER, KEY_SOURCE_MODELER);
        add(links, PAGE_DATA_CATALOG, KEY_DATA_CATALOG);
      }
      case CONTROLS -> {
        add(links, PAGE_METADATA_HARVESTING, KEY_METADATA_HARVESTING);
        add(links, PAGE_SCHEMA_GATE, KEY_SCHEMA_GATE);
        add(links, PAGE_DATA_QUALITY, KEY_DATA_QUALITY);
      }
      case DATA_VAULT -> add(links, PAGE_DATA_VAULT, KEY_DATA_VAULT);
      case BUSINESS_VAULT -> add(links, PAGE_BUSINESS_VAULT, KEY_BUSINESS_VAULT);
      case DIMENSIONAL -> add(links, PAGE_DIMENSIONAL, KEY_DIMENSIONAL);
      case TARGET_QUALITY -> add(links, PAGE_DATA_QUALITY, KEY_DATA_QUALITY);
      case ORCHESTRATION -> {
        add(links, PAGE_UPDATE_RESOURCE_GROUP, KEY_UPDATE_RESOURCE_GROUP);
        add(links, PAGE_OPERATIONS, KEY_OPERATIONS);
      }
      case OUTPUTS -> {
        add(links, PAGE_OPERATIONS, KEY_OPERATIONS);
        add(links, PAGE_EXECUTION_MAPS, KEY_EXECUTION_MAPS);
        add(links, PAGE_LINEAGE_VIEW, KEY_LINEAGE_VIEW);
      }
    }
  }

  private static void addControl(List<DocLink> links, EdwJourneyControl control) {
    if (control == null) {
      add(links, PAGE_EDW_JOURNEY, KEY_EDW_JOURNEY);
      return;
    }
    switch (control) {
      case HARVEST -> add(links, PAGE_METADATA_HARVESTING, KEY_METADATA_HARVESTING);
      case SCHEMA_GATE -> add(links, PAGE_SCHEMA_GATE, KEY_SCHEMA_GATE);
      case SOURCE_QUALITY -> add(links, PAGE_DATA_QUALITY, KEY_DATA_QUALITY);
      case CATALOG_VERSION -> add(links, PAGE_DATA_CATALOG, KEY_DATA_CATALOG);
    }
  }

  private static void addModel(List<DocLink> links, String modelType) {
    String type = Const.NVL(modelType, "");
    switch (type) {
      case EdwJourneySnapshot.MODEL_TYPE_SOURCE -> {
        add(links, PAGE_SOURCE_MODELER, KEY_SOURCE_MODELER);
        add(links, PAGE_GENERATE_DATA_VAULT, KEY_GENERATE_DATA_VAULT);
      }
      case EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT -> add(links, PAGE_DATA_VAULT, KEY_DATA_VAULT);
      case EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT ->
          add(links, PAGE_BUSINESS_VAULT, KEY_BUSINESS_VAULT);
      case EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL ->
          add(links, PAGE_DIMENSIONAL, KEY_DIMENSIONAL);
      default -> add(links, PAGE_EDW_JOURNEY, KEY_EDW_JOURNEY);
    }
  }

  private static void addAction(List<DocLink> links, String actionType) {
    String type = Const.NVL(actionType, "").toUpperCase(Locale.ROOT);
    switch (type) {
      case "UPDATE_RESOURCE_DEFINITION_GROUP" ->
          add(links, PAGE_UPDATE_RESOURCE_GROUP, KEY_UPDATE_RESOURCE_GROUP);
      case "DATA_VAULT_UPDATE" -> add(links, PAGE_DATA_VAULT_UPDATE, KEY_DATA_VAULT_UPDATE);
      case "BUSINESS_VAULT_UPDATE" ->
          add(links, PAGE_BUSINESS_VAULT_UPDATE, KEY_BUSINESS_VAULT_UPDATE);
      case "DIMENSIONAL_UPDATE", "DIMENSIONAL_PUBLISH" ->
          add(links, PAGE_DIMENSIONAL_UPDATE, KEY_DIMENSIONAL_UPDATE);
      case "VALIDATE_RESOURCE_DEFINITIONS" -> add(links, PAGE_SCHEMA_GATE, KEY_SCHEMA_GATE);
      case "HARVEST_SOURCE_METADATA" ->
          add(links, PAGE_METADATA_HARVESTING, KEY_METADATA_HARVESTING);
      case "MEASURE_DATA_QUALITY", "EVALUATE_QUALITY_GATE" ->
          add(links, PAGE_DATA_QUALITY, KEY_DATA_QUALITY);
      case "GENERATE_EXECUTION_MAP" -> add(links, PAGE_EXECUTION_MAPS, KEY_EXECUTION_MAPS);
      case "EXPORT_DATA_LINEAGE" -> {
        add(links, PAGE_OPENLINEAGE, KEY_OPENLINEAGE);
        add(links, PAGE_LINEAGE_VIEW, KEY_LINEAGE_VIEW);
      }
      case "EXPORT_ARCHITECTURE" -> add(links, PAGE_ARCHITECTURE_EXPORT, KEY_ARCHITECTURE_EXPORT);
      case "IMPORT_DBT_PROJECT" -> add(links, PAGE_DBT_IMPORT, KEY_DBT_IMPORT);
      case "BEGIN_VAULT_UPDATE", "END_VAULT_UPDATE" -> {
        add(links, PAGE_DATA_VAULT_UPDATE, KEY_DATA_VAULT_UPDATE);
        add(links, PAGE_OPERATIONS, KEY_OPERATIONS);
      }
      default -> {
        add(links, PAGE_UPDATE_RESOURCE_GROUP, KEY_UPDATE_RESOURCE_GROUP);
        add(links, PAGE_OPERATIONS, KEY_OPERATIONS);
      }
    }
  }

  private static void addOutput(List<DocLink> links, EdwJourneyTreeNode node) {
    String kind = outputKind(node);
    switch (Const.NVL(kind, "")) {
      case EdwJourneySnapshot.OUTPUT_EXECUTION_MAPS ->
          add(links, PAGE_EXECUTION_MAPS, KEY_EXECUTION_MAPS);
      case EdwJourneySnapshot.OUTPUT_LINEAGE_VIEWS -> {
        add(links, PAGE_LINEAGE_VIEW, KEY_LINEAGE_VIEW);
        add(links, PAGE_SOURCE_TO_TARGET_LINEAGE, KEY_SOURCE_TO_TARGET_LINEAGE);
      }
      case EdwJourneySnapshot.OUTPUT_REPORTS -> add(links, PAGE_OPERATIONS, KEY_OPERATIONS);
      default -> {
        add(links, PAGE_OPERATIONS, KEY_OPERATIONS);
        add(links, PAGE_EXECUTION_MAPS, KEY_EXECUTION_MAPS);
        add(links, PAGE_LINEAGE_VIEW, KEY_LINEAGE_VIEW);
      }
    }
  }

  static String outputKind(EdwJourneyTreeNode node) {
    if (node == null) {
      return "";
    }
    if (!Utils.isEmpty(node.modelType())) {
      return node.modelType();
    }
    String id = Const.NVL(node.id(), "");
    if (id.startsWith("outputs:") || id.startsWith("output:")) {
      int first = id.indexOf(':');
      int second = id.indexOf(':', first + 1);
      if (second > first) {
        return id.substring(first + 1, second);
      }
      if (first >= 0) {
        return id.substring(first + 1);
      }
    }
    return "";
  }

  private static void add(List<DocLink> links, String htmlPage, String labelKey) {
    for (DocLink existing : links) {
      if (existing.htmlPage().equals(htmlPage)) {
        return;
      }
    }
    links.add(new DocLink(htmlPage, labelKey));
  }
}
