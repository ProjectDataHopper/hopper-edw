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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyTreeNode.Kind;
import org.junit.jupiter.api.Test;

class EdwJourneyNavigationSupportTest {

  @Test
  void mapsSnapshotModelTypesToOriginTypes() {
    assertEquals(
        RecordOriginNavigationSupport.MODEL_TYPE_SOURCE_MODEL,
        EdwJourneyNavigationSupport.mapModelType(EdwJourneySnapshot.MODEL_TYPE_SOURCE));
    assertEquals(
        RecordOriginNavigationSupport.MODEL_TYPE_DATA_VAULT,
        EdwJourneyNavigationSupport.mapModelType(EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT));
    assertEquals(
        RecordOriginNavigationSupport.MODEL_TYPE_BUSINESS_VAULT,
        EdwJourneyNavigationSupport.mapModelType(EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT));
    assertEquals(
        RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL,
        EdwJourneyNavigationSupport.mapModelType(EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL));
  }

  @Test
  void primaryOpenRequiresAnArtifact() {
    Variables variables = new Variables();
    EdwJourneyTreeNode stage =
        EdwJourneyTreeNode.builder(
                Kind.STAGE, EdwJourneyIds.stage(EdwJourneyStage.DATA_VAULT), "DV")
            .stage(EdwJourneyStage.DATA_VAULT)
            .build();
    assertFalse(EdwJourneyNavigationSupport.canOpenPrimary(stage, variables));

    EdwJourneyTreeNode feed =
        EdwJourneyTreeNode.builder(
                Kind.CATALOG_FEED,
                EdwJourneyIds.catalogFeed(new RecordDefinitionKey("ns", "customer")),
                "customer")
            .catalogConnection("local-catalog")
            .catalogKey(new RecordDefinitionKey("ns", "customer"))
            .build();
    assertTrue(EdwJourneyNavigationSupport.canOpenPrimary(feed, variables));
  }
}
