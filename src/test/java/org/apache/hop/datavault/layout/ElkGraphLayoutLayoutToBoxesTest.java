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
package org.apache.hop.datavault.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElkGraphLayoutLayoutToBoxesTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void layoutToBoxesDoesNotRequireTargetMutationAndReturnsPositions() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setName("boxes");

    DvHub hub = new DvHub("hub_x");
    hub.setLocation(10, 10);
    DvSatellite sat = new DvSatellite("sat_x");
    sat.setHubName("hub_x");
    sat.setLocation(10, 200);
    DvLink link = new DvLink("lnk_x");
    link.getHubNames().add("hub_x");
    model.getTables().add(hub);
    model.getTables().add(sat);
    model.getTables().add(link);

    int hubXBefore = hub.getLocation().x;
    int hubYBefore = hub.getLocation().y;

    ElkGraphLayout layoutGraph = ElkGraphLayout.fromDataVaultModel(model);
    Map<String, ElkLayoutBox> boxes = layoutGraph.layoutToBoxes(ElkLayout.createDefault());

    assertFalse(boxes.isEmpty());
    assertNotNull(boxes.get("hub_x"));
    assertNotNull(boxes.get("sat_x"));
    assertTrue(boxes.get("hub_x").width() > 0);
    assertTrue(boxes.get("hub_x").height() > 0);

    // layoutToBoxes must not write back to model targets
    assertEquals(hubXBefore, hub.getLocation().x);
    assertEquals(hubYBefore, hub.getLocation().y);
  }
}
