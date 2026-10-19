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
package org.hopper.edw.datavault.hopgui.perspective.journey.poster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.EdwJourneyProblem;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.poster.EdwJourneyPosterStatus.Mark;
import org.junit.jupiter.api.Test;

class EdwJourneyPosterStatusTest {

  private static final Date NOW = new Date(1_800_000_000_000L);

  @Test
  void errorBeatsWarningAndInformationalBlocksStayUnmarked() {
    HarvestRunSummary harvest =
        new HarvestRunSummary("h", NOW, "g", "FAIL", 2L, 4L, 1L, 1L, null, null);
    EdwJourneyOpsOverlay overlay =
        new EdwJourneyOpsOverlay(
            null,
            harvest,
            null,
            null,
            new LoadOverviewSummary("e", "run", NOW, 10L, 1L, 2L, false),
            List.of(new ModelLoadSummary("dv", "retail", 5L, 1L, false, NOW)),
            List.of(new EdwJourneyProblem("WARNING", "harvest", "customer", "widened")));
    EdwJourneySnapshot snapshot =
        new EdwJourneySnapshot(
            "g",
            "local",
            List.of(new ModelRef("a.hsm", "a", EdwJourneySnapshot.MODEL_TYPE_SOURCE, List.of())),
            List.of(),
            List.of(
                new ModelRef(
                    "v.hdv", "v", EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT, List.of("hub"))),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    var marks = EdwJourneyPosterStatus.marks(snapshot, overlay, NOW);
    assertEquals(Mark.ERROR, marks.get("harvester").mark());
    assertEquals(Mark.ERROR, marks.get("source-models").mark());
    assertEquals(Mark.ERROR, marks.get("hop-engine").mark());
    assertEquals(Mark.ERROR, marks.get("dv-models").mark());
    assertEquals(Mark.NONE, marks.get("bv-models").mark());
    assertEquals(Mark.NONE, marks.get("coach").mark());
    assertEquals(Mark.NONE, marks.get("documentation").mark());
    assertEquals(Mark.NONE, marks.get("presentations").mark());
    assertEquals(Mark.ERROR, marks.get("ops").mark());
  }

  @Test
  void unavailableOpsDoesNotMarkOpsAsError() {
    EdwJourneyOpsOverlay overlay = EdwJourneyOpsOverlay.unavailable("No OPS connection");
    var marks = EdwJourneyPosterStatus.marks(EdwJourneySnapshot.empty(), overlay, NOW);
    assertEquals(Mark.NONE, marks.get("ops").mark());
    assertEquals(Mark.NONE, marks.get("gates").mark());
  }

  @Test
  void cleanModelIsOkAndStaleLoadIsWarning() {
    Date old = new Date(NOW.getTime() - EdwJourneyPosterStatus.STALE_MS - 1);
    EdwJourneySnapshot snapshot =
        new EdwJourneySnapshot(
            "g",
            null,
            List.of(),
            List.of(),
            List.of(
                new ModelRef("v.hdv", "v", EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT, List.of())),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    var present = EdwJourneyPosterStatus.marks(snapshot, EdwJourneyOpsOverlay.empty(), NOW);
    assertEquals(Mark.OK, present.get("dv-db").mark());
    EdwJourneyOpsOverlay stale =
        new EdwJourneyOpsOverlay(
            null,
            null,
            null,
            null,
            new LoadOverviewSummary("e", "run", old, 10L, 1L, 0L, true),
            List.of(),
            List.of());
    var marks = EdwJourneyPosterStatus.marks(snapshot, stale, NOW);
    assertEquals(Mark.WARNING, marks.get("publish").mark());
  }
}
