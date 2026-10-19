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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.link.EdwJourneyLinkExecutor.EdwJourneyNavigator;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelId;
import org.junit.jupiter.api.Test;

class EdwJourneyLinkTest {

  @Test
  void parsesOpenActAndPanelAndRejectsOtherSchemes() {
    EdwJourneyLink open =
        EdwJourneyLink.parse(
            EdwJourneyLink.open(
                "dv", "${PROJECT_HOME}/models/retail-360.hdv", "hub_customer", "select"));
    assertEquals(EdwJourneyLink.Kind.OPEN, open.kind());
    assertEquals("dv", open.layer());
    assertEquals("${PROJECT_HOME}/models/retail-360.hdv", open.file());
    assertEquals("hub_customer", open.element());
    assertEquals("select", open.verb());

    EdwJourneyLink act = EdwJourneyLink.parse(EdwJourneyLink.action("validate-sources"));
    assertEquals("validate-sources", act.action());
    assertEquals(
        "GATE_DESK",
        EdwJourneyLink.parse(EdwJourneyLink.panel(EdwJourneyPanelId.GATE_DESK)).panelId());
    assertNull(EdwJourneyLink.parse("https://example.com"));
    assertNull(EdwJourneyLink.parse("hop-edw://nope"));
  }

  @Test
  void selectCallsTheNavigatorOnceAndUnknownActionDoesNotOpen() {
    FakeNavigator navigator = new FakeNavigator();
    EdwJourneySnapshot snapshot =
        new EdwJourneySnapshot(
            "g",
            null,
            List.of(),
            List.of(),
            List.of(
                new ModelRef(
                    "models/vault.hdv",
                    "vault",
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
    EdwJourneyLinkExecutor.execute(
        EdwJourneyLink.parse("hop-edw://open?layer=dv&element=hub_customer&verb=select"),
        snapshot,
        navigator);
    assertEquals(List.of("open dv models/vault.hdv hub_customer select"), navigator.calls);

    navigator.calls.clear();
    EdwJourneyLinkExecutor.execute(
        EdwJourneyLink.parse(EdwJourneyLink.action("validate-sources")), snapshot, navigator);
    assertEquals(List.of("act validate-sources"), navigator.calls);

    navigator.calls.clear();
    EdwJourneyLinkExecutor.execute(
        EdwJourneyLink.parse("hop-edw://act?name=drop-database"), snapshot, navigator);
    assertEquals(List.of("fail Unknown action drop-database"), navigator.calls);
    assertTrue(navigator.calls.stream().noneMatch(call -> call.startsWith("open")));

    navigator.calls.clear();
    EdwJourneyLinkExecutor.execute(null, snapshot, navigator);
    assertEquals(List.of("fail This row has no link."), navigator.calls);
  }

  @Test
  void barClickOpensTheModelForThatLayer() {
    FakeNavigator navigator = new FakeNavigator();
    EdwJourneySnapshot snapshot =
        new EdwJourneySnapshot(
            "retail",
            null,
            List.of(),
            List.of(),
            List.of(
                new ModelRef(
                    "models/retail-360.hdv",
                    "retail-360",
                    EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT,
                    List.of())),
            List.of(
                new ModelRef(
                    "models/retail-360.hbv",
                    "retail-360",
                    EdwJourneySnapshot.MODEL_TYPE_BUSINESS_VAULT,
                    List.of())),
            List.of(
                new ModelRef(
                    "models/retail-f-orders.hdm",
                    "retail-f-orders",
                    EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL,
                    List.of("f_orders"))),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    EdwJourneyLinkExecutor.execute(
        EdwJourneyLink.parse("hop-edw://open?element=retail-f-orders&verb=select"),
        snapshot,
        navigator);
    assertEquals(List.of("open dm models/retail-f-orders.hdm  select"), navigator.calls);

    navigator.calls.clear();
    EdwJourneyLinkExecutor.execute(
        EdwJourneyLink.parse(
            "hop-edw://open?element=retail-360"
                + EdwJourneyModelLookup.LAYER_MARK
                + "bv&verb=select"),
        snapshot,
        navigator);
    assertEquals(List.of("open bv models/retail-360.hbv  select"), navigator.calls);

    navigator.calls.clear();
    EdwJourneyLinkExecutor.execute(
        EdwJourneyLink.parse("hop-edw://open?element=retail-360&verb=select"), snapshot, navigator);
    assertEquals(List.of("fail Several models are named retail-360"), navigator.calls);
  }

  private static final class FakeNavigator implements EdwJourneyNavigator {
    private final List<String> calls = new ArrayList<>();

    @Override
    public void openLayer(String layer, String file, String element, String verb) {
      calls.add("open " + layer + " " + file + " " + element + " " + verb);
    }

    @Override
    public void runAction(String action) {
      calls.add("act " + action);
    }

    @Override
    public void showPanel(String panelId) {
      calls.add("panel " + panelId);
    }

    @Override
    public void fail(String message) {
      calls.add("fail " + message);
    }
  }
}
