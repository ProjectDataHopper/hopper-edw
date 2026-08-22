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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyTreeNode.Kind;
import org.apache.hop.quality.history.DataQualityHistoryReader.QualityRunSummary;
import org.junit.jupiter.api.Test;

class EdwJourneyOpsDecorationsTest {

  @Test
  void formatsDurationsAndDecoratesLabels() {
    assertEquals("45s", EdwJourneyOpsDecorations.formatDuration(45_000L));
    assertEquals("2m 5s", EdwJourneyOpsDecorations.formatDuration(125_000L));
    assertEquals("Harvest  ·  PASS", EdwJourneyOpsDecorations.decorate("Harvest", "PASS"));
    assertNull(EdwJourneyOpsDecorations.harvest(null));
  }

  @Test
  void harvestAndQualityDecorations() {
    HarvestRunSummary harvest =
        new HarvestRunSummary(
            "h1", new Date(), "retail-sources", "DRIFT", 10L, 3L, 0L, 2L, "v1", null);
    assertTrue(EdwJourneyOpsDecorations.harvest(harvest).contains("3 changes"));
    QualityRunSummary quality =
        new QualityRunSummary("q1", new Date(), "PRE_UPDATE", 4L, 2L, 1L, 0L, false, "wf");
    assertEquals("1 blocking", EdwJourneyOpsDecorations.quality(quality));
  }

  @Test
  void treeBuilderAppendsLastRunOnHarvestAndModels() {
    HarvestRunSummary harvest =
        new HarvestRunSummary("h1", new Date(), "retail-sources", "OK", 4L, 0L, 0L, 0L, null, null);
    EdwJourneyOpsOverlay overlay =
        new EdwJourneyOpsOverlay(
            null,
            harvest,
            null,
            null,
            new LoadOverviewSummary("ex", "run-update", new Date(), 90_000L, 3L, 0L, true),
            List.of(new ModelLoadSummary("dv", "core", 12_000L, 0L, true, null)),
            List.of());
    EdwJourneySnapshot snapshot =
        new EdwJourneySnapshot(
            "retail-sources",
            "local-catalog",
            List.of(),
            List.of(),
            List.of(
                new EdwJourneySnapshot.ModelRef(
                    "${PROJECT_HOME}/models/core.hdv",
                    "core",
                    EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT,
                    List.of("hub_customer"))),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    EdwJourneyTreeNode root = EdwJourneyTreeBuilder.build(snapshot, overlay);
    assertTrue(root.label().contains("1m 30s"));
    EdwJourneyTreeNode harvestNode = root.children().get(1).children().get(0);
    assertEquals(Kind.CONTROL, harvestNode.kind());
    assertTrue(harvestNode.label().contains("OK"));
    EdwJourneyTreeNode model = root.children().get(2).children().get(0);
    assertTrue(model.label().startsWith("core"));
    assertTrue(model.label().contains("12s"));
  }

  @Test
  void opsModelTypeAndLookup() {
    assertEquals(
        "dv", EdwJourneyOpsOverlayLoader.opsModelType(EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT));
    assertEquals(
        "bv",
        EdwJourneyOpsOverlayLoader.opsModelType(EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT));
    assertEquals(
        "dm", EdwJourneyOpsOverlayLoader.opsModelType(EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL));
    EdwJourneyOpsOverlay overlay =
        new EdwJourneyOpsOverlay(
            null,
            null,
            null,
            null,
            null,
            List.of(new ModelLoadSummary("dv", "core", 1000L, 0L, true, null)),
            List.of());
    assertEquals(1000L, EdwJourneyOpsOverlayLoader.modelLoad(overlay, "core", "dv").durationMs());
  }
}
