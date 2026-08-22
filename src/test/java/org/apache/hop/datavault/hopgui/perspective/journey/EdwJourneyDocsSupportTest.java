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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyDocsSupport.DocLink;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyTreeNode.Kind;
import org.junit.jupiter.api.Test;

class EdwJourneyDocsSupportTest {

  @Test
  void emptyProjectLinksGettingStartedAndGroup() {
    assertPages(
        EdwJourneyDocsSupport.linksForEmptyProject(),
        EdwJourneyDocsSupport.PAGE_GETTING_STARTED,
        EdwJourneyDocsSupport.PAGE_RESOURCE_DEFINITION_GROUP,
        EdwJourneyDocsSupport.PAGE_EDW_JOURNEY,
        EdwJourneyDocsSupport.PAGE_DATAVAULT_CONFIGURATION);
    assertPages(
        EdwJourneyDocsSupport.linksFor(null),
        EdwJourneyDocsSupport.PAGE_GETTING_STARTED,
        EdwJourneyDocsSupport.PAGE_RESOURCE_DEFINITION_GROUP,
        EdwJourneyDocsSupport.PAGE_EDW_JOURNEY,
        EdwJourneyDocsSupport.PAGE_DATAVAULT_CONFIGURATION);
  }

  @Test
  void groupAndCanonicalStages() {
    assertPages(
        node(Kind.GROUP, null, null, null),
        EdwJourneyDocsSupport.PAGE_RESOURCE_DEFINITION_GROUP,
        EdwJourneyDocsSupport.PAGE_EDW_JOURNEY,
        EdwJourneyDocsSupport.PAGE_GETTING_STARTED);
    assertPages(
        stage(EdwJourneyStage.SOURCES),
        EdwJourneyDocsSupport.PAGE_SOURCE_MODELER,
        EdwJourneyDocsSupport.PAGE_DATA_CATALOG);
    assertPages(
        stage(EdwJourneyStage.CONTROLS),
        EdwJourneyDocsSupport.PAGE_METADATA_HARVESTING,
        EdwJourneyDocsSupport.PAGE_SCHEMA_GATE,
        EdwJourneyDocsSupport.PAGE_DATA_QUALITY);
    assertPages(stage(EdwJourneyStage.DATA_VAULT), EdwJourneyDocsSupport.PAGE_DATA_VAULT);
    assertPages(stage(EdwJourneyStage.BUSINESS_VAULT), EdwJourneyDocsSupport.PAGE_BUSINESS_VAULT);
    assertPages(stage(EdwJourneyStage.DIMENSIONAL), EdwJourneyDocsSupport.PAGE_DIMENSIONAL);
    assertPages(stage(EdwJourneyStage.TARGET_QUALITY), EdwJourneyDocsSupport.PAGE_DATA_QUALITY);
    assertPages(
        stage(EdwJourneyStage.ORCHESTRATION),
        EdwJourneyDocsSupport.PAGE_UPDATE_RESOURCE_GROUP,
        EdwJourneyDocsSupport.PAGE_OPERATIONS);
    assertPages(
        stage(EdwJourneyStage.OUTPUTS),
        EdwJourneyDocsSupport.PAGE_OPERATIONS,
        EdwJourneyDocsSupport.PAGE_EXECUTION_MAPS,
        EdwJourneyDocsSupport.PAGE_LINEAGE_VIEW);
  }

  @Test
  void sourceCatalogAndControls() {
    assertPages(
        node(Kind.SOURCE_MODEL, null, null, EdwJourneySnapshot.MODEL_TYPE_SOURCE),
        EdwJourneyDocsSupport.PAGE_SOURCE_MODELER,
        EdwJourneyDocsSupport.PAGE_GENERATE_DATA_VAULT);
    assertPages(
        node(Kind.CATALOG_FEED, null, null, null),
        EdwJourneyDocsSupport.PAGE_DATA_CATALOG,
        EdwJourneyDocsSupport.PAGE_DATAVAULT_SOURCE);
    assertPages(control(EdwJourneyControl.HARVEST), EdwJourneyDocsSupport.PAGE_METADATA_HARVESTING);
    assertPages(control(EdwJourneyControl.SCHEMA_GATE), EdwJourneyDocsSupport.PAGE_SCHEMA_GATE);
    assertPages(control(EdwJourneyControl.SOURCE_QUALITY), EdwJourneyDocsSupport.PAGE_DATA_QUALITY);
    assertPages(
        control(EdwJourneyControl.CATALOG_VERSION), EdwJourneyDocsSupport.PAGE_DATA_CATALOG);
  }

  @Test
  void modelsFollowLayer() {
    assertPages(
        node(Kind.MODEL, null, null, EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT),
        EdwJourneyDocsSupport.PAGE_DATA_VAULT);
    assertPages(
        node(Kind.MODEL_TABLE, null, null, EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT),
        EdwJourneyDocsSupport.PAGE_BUSINESS_VAULT);
    assertPages(
        node(Kind.MODEL, null, null, EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL),
        EdwJourneyDocsSupport.PAGE_DIMENSIONAL);
  }

  @Test
  void workflowActionsMapToActionGuides() {
    assertPages(
        node(Kind.WORKFLOW, null, null, null),
        EdwJourneyDocsSupport.PAGE_UPDATE_RESOURCE_GROUP,
        EdwJourneyDocsSupport.PAGE_OPERATIONS);
    assertPages(
        action("UPDATE_RESOURCE_DEFINITION_GROUP"),
        EdwJourneyDocsSupport.PAGE_UPDATE_RESOURCE_GROUP);
    assertPages(action("DATA_VAULT_UPDATE"), EdwJourneyDocsSupport.PAGE_DATA_VAULT_UPDATE);
    assertPages(action("BUSINESS_VAULT_UPDATE"), EdwJourneyDocsSupport.PAGE_BUSINESS_VAULT_UPDATE);
    assertPages(action("DIMENSIONAL_PUBLISH"), EdwJourneyDocsSupport.PAGE_DIMENSIONAL_UPDATE);
    assertPages(action("VALIDATE_RESOURCE_DEFINITIONS"), EdwJourneyDocsSupport.PAGE_SCHEMA_GATE);
    assertPages(action("HARVEST_SOURCE_METADATA"), EdwJourneyDocsSupport.PAGE_METADATA_HARVESTING);
    assertPages(action("MEASURE_DATA_QUALITY"), EdwJourneyDocsSupport.PAGE_DATA_QUALITY);
    assertPages(action("GENERATE_EXECUTION_MAP"), EdwJourneyDocsSupport.PAGE_EXECUTION_MAPS);
    assertPages(
        action("EXPORT_DATA_LINEAGE"),
        EdwJourneyDocsSupport.PAGE_OPENLINEAGE,
        EdwJourneyDocsSupport.PAGE_LINEAGE_VIEW);
    assertPages(action("EXPORT_ARCHITECTURE"), EdwJourneyDocsSupport.PAGE_ARCHITECTURE_EXPORT);
    assertPages(action("IMPORT_DBT_PROJECT"), EdwJourneyDocsSupport.PAGE_DBT_IMPORT);
    assertPages(
        action("BEGIN_VAULT_UPDATE"),
        EdwJourneyDocsSupport.PAGE_DATA_VAULT_UPDATE,
        EdwJourneyDocsSupport.PAGE_OPERATIONS);
  }

  @Test
  void outputGroupsUseKind() {
    assertPages(
        output(Kind.OUTPUT_GROUP, EdwJourneySnapshot.OUTPUT_REPORTS),
        EdwJourneyDocsSupport.PAGE_OPERATIONS);
    assertPages(
        output(Kind.OUTPUT_FILE, EdwJourneySnapshot.OUTPUT_EXECUTION_MAPS),
        EdwJourneyDocsSupport.PAGE_EXECUTION_MAPS);
    assertPages(
        output(Kind.OUTPUT_FILE, EdwJourneySnapshot.OUTPUT_LINEAGE_VIEWS),
        EdwJourneyDocsSupport.PAGE_LINEAGE_VIEW,
        EdwJourneyDocsSupport.PAGE_SOURCE_TO_TARGET_LINEAGE);
  }

  @Test
  void outputKindReadsModelTypeOrId() {
    EdwJourneyTreeNode fromId =
        EdwJourneyTreeNode.builder(Kind.OUTPUT_GROUP, "outputs:execution-maps", "maps").build();
    assertEquals(
        EdwJourneySnapshot.OUTPUT_EXECUTION_MAPS, EdwJourneyDocsSupport.outputKind(fromId));
    EdwJourneyTreeNode file =
        EdwJourneyTreeNode.builder(
                Kind.OUTPUT_FILE, "output:reports:${PROJECT_HOME}/work/reports/a.html", "a.html")
            .build();
    assertEquals(EdwJourneySnapshot.OUTPUT_REPORTS, EdwJourneyDocsSupport.outputKind(file));
  }

  @Test
  void everyLinkHasLabelKey() {
    for (DocLink link : EdwJourneyDocsSupport.linksForEmptyProject()) {
      assertTrue(link.labelKey().startsWith("EdwJourneyDocsSupport."));
      assertTrue(link.htmlPage().endsWith(".html"));
    }
  }

  private static EdwJourneyTreeNode stage(EdwJourneyStage stage) {
    return EdwJourneyTreeNode.builder(Kind.STAGE, EdwJourneyIds.stage(stage), stage.id())
        .stage(stage)
        .build();
  }

  private static EdwJourneyTreeNode control(EdwJourneyControl control) {
    return EdwJourneyTreeNode.builder(Kind.CONTROL, EdwJourneyIds.control(control), control.id())
        .control(control)
        .build();
  }

  private static EdwJourneyTreeNode action(String actionType) {
    return node(Kind.WORKFLOW_ACTION, null, actionType, null);
  }

  private static EdwJourneyTreeNode output(Kind kind, String outputKind) {
    return EdwJourneyTreeNode.builder(kind, EdwJourneyIds.outputGroup(outputKind), outputKind)
        .modelType(outputKind)
        .stage(EdwJourneyStage.OUTPUTS)
        .build();
  }

  private static EdwJourneyTreeNode node(
      Kind kind, EdwJourneyStage stage, String actionType, String modelType) {
    return EdwJourneyTreeNode.builder(kind, kind.name(), kind.name())
        .stage(stage)
        .actionType(actionType)
        .modelType(modelType)
        .build();
  }

  private static void assertPages(EdwJourneyTreeNode node, String... pages) {
    assertPages(EdwJourneyDocsSupport.linksFor(node), pages);
  }

  private static void assertPages(List<DocLink> links, String... pages) {
    assertEquals(List.of(pages), links.stream().map(DocLink::htmlPage).toList());
  }
}
