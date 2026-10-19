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
package org.hopper.edw.datavault.hopgui.perspective.journey.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.link.EdwJourneyModelLookup;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyHistory.FindingRef;
import org.junit.jupiter.api.Test;

class EdwJourneyPanelContentTest {

  @Test
  void historyCapsAtFourteenAndChronicStartsAtThree() {
    List<String> many = new java.util.ArrayList<>();
    for (int i = 0; i < 20; i++) {
      many.add("r" + i);
    }
    assertEquals(14, EdwJourneyHistory.cap(many).size());
    assertEquals("r0", EdwJourneyHistory.cap(many).get(0));
    List<List<FindingRef>> runs = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      runs.add(
          new java.util.ArrayList<>(List.of(new FindingRef("customer", "not-null", "WARNING"))));
    }
    runs.get(3).add(new FindingRef("once", "range", "ERROR"));
    var rollup = EdwJourneyHistory.rollup(runs);
    assertEquals("yes", rollup.get(0).chronic() ? "yes" : "no");
    assertEquals(5, rollup.get(0).runs());
    assertEquals("customer", rollup.get(0).subject());
    assertTrue(rollup.stream().anyMatch(row -> "once".equals(row.subject()) && !row.chronic()));
  }

  @Test
  void waveBoardTiltsModelLabelsAndKeepsSameNamesApart() {
    EdwJourneyOpsOverlay overlay =
        new EdwJourneyOpsOverlay(
            null,
            null,
            null,
            null,
            null,
            List.of(
                new ModelLoadSummary("dm", "retail-f-orders", 1_000L, 0L, true, null),
                new ModelLoadSummary("dv", "retail-360", 3_000L, 0L, true, null),
                new ModelLoadSummary("bv", "retail-360", 2_000L, 0L, true, null)),
            List.of());
    var spec =
        EdwJourneyPanelContent.build(
            EdwJourneyPanelId.WAVE_BOARD,
            EdwJourneyPanelContent.Inputs.of(EdwJourneySnapshot.empty(), overlay, null));
    var models = spec.charts().get(1);
    assertEquals(-25, models.labelAngle());
    assertTrue(models.linkTemplate().contains("element={model}"));
    assertFalse(models.linkTemplate().contains("layer=dv"));
    String labels = models.rows().toString();
    assertTrue(labels.contains("retail-f-orders"));
    assertTrue(labels.contains("retail-360" + EdwJourneyModelLookup.LAYER_MARK + "dv"));
    assertTrue(labels.contains("retail-360" + EdwJourneyModelLookup.LAYER_MARK + "bv"));
  }

  @Test
  void emptyGroupOffersCreateAndSourceWithoutVaultOffersGenerate() {
    var empty =
        EdwJourneyPanelContent.build(
            EdwJourneyPanelId.NEXT_MOVE,
            EdwJourneyPanelContent.Inputs.of(EdwJourneySnapshot.empty(), null, null));
    String gaps = empty.tables().get(0).rows().toString();
    assertTrue(gaps.contains("create-group"));

    EdwJourneySnapshot sourced =
        new EdwJourneySnapshot(
            "retail",
            "local",
            List.of(
                new ModelRef(
                    "models/crm.hsm",
                    "crm",
                    EdwJourneySnapshot.MODEL_TYPE_SOURCE,
                    List.of("customer"))),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    var next =
        EdwJourneyPanelContent.build(
            EdwJourneyPanelId.NEXT_MOVE,
            EdwJourneyPanelContent.Inputs.of(sourced, EdwJourneyOpsOverlay.empty(), null));
    String text = next.tables().get(0).rows().toString();
    assertTrue(text.contains("generate-dv"));
    assertTrue(!text.contains("create-group"));
  }

  @Test
  void gateDeskMarksChronicFindings() {
    var history =
        new EdwJourneyHistory(
            null,
            List.of(),
            List.of(),
            List.of(),
            EdwJourneyHistory.rollup(
                List.of(
                    List.of(new FindingRef("city", "length", "WARNING")),
                    List.of(new FindingRef("city", "length", "WARNING")),
                    List.of(new FindingRef("city", "length", "WARNING")))));
    var panel =
        EdwJourneyPanelContent.build(
            EdwJourneyPanelId.GATE_DESK,
            EdwJourneyPanelContent.Inputs.of(group(), EdwJourneyOpsOverlay.empty(), history));
    String findings = panel.tables().get(1).rows().toString();
    assertTrue(findings.contains("chronic=yes") || findings.contains("yes"));
    assertTrue(findings.contains("city"));
  }

  private static EdwJourneySnapshot group() {
    return new EdwJourneySnapshot(
        "retail", "local", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of());
  }
}
