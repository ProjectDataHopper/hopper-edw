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
package org.hopper.edw.datavault.lineageview.backend.marquez;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageGraphLayer;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;
import org.junit.jupiter.api.Test;

class MarquezLineageGraphParserTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SEED = "dataset:retail-dataset:f_orders";

  @Test
  void parsesCheckedInMarquezFixture() throws Exception {
    LineageGraph graph = parseFixture();
    assertEquals(SEED, graph.getSeedNodeId());
    assertEquals(5, graph.getNodesOrEmpty().size());
    assertTrue(graph.getEdgesOrEmpty().size() >= 4);

    LineageNode fact = graph.findNode(SEED);
    assertNotNull(fact);
    assertEquals(LineageNodeKind.DATASET, fact.getKind());
    assertEquals(LineageGraphLayer.SOURCE, fact.getLayer());
    assertTrue(fact.getSchemaFieldNames().contains("order_amount"));

    LineageNode job = graph.findNode("job:retail-job:dm/retail-pos/f_orders");
    assertNotNull(job);
    assertEquals(LineageGraphLayer.DM, job.getLayer());
    assertEquals("11111111-1111-1111-1111-111111111111", job.getLatestRunId());
    assertEquals("2026-08-14T10:00:01Z", job.getLastExportedAt());
    assertNull(job.getHopOps(), "latestRun.durationMs must not become hopOps");

    LineageNode sat = graph.findNode("dataset:retail-dataset:sat_customer_demo");
    assertNotNull(sat);
    assertEquals(LineageGraphLayer.SOURCE, sat.getLayer());
    assertEquals("hop/retail/sources/E2E-customer-demo", sat.getHopLocation().getCatalogKey());
  }

  @Test
  void durationMsOnLatestRunIsDiscarded() throws Exception {
    JsonNode root = fixture("lineage-f_orders.json");
    JsonNode duration = root.path("graph").get(1).path("data").path("latestRun").path("durationMs");
    assertEquals(999999, duration.asInt());
    LineageGraph graph = MarquezLineageGraphParser.parse(root, SEED);
    for (LineageNode node : graph.getNodesOrEmpty()) {
      assertNull(node.getHopOps());
    }
  }

  static LineageGraph parseFixture() throws Exception {
    return MarquezLineageGraphParser.parse(fixture("lineage-f_orders.json"), SEED);
  }

  static JsonNode fixture(String name) throws Exception {
    try (InputStream in = MarquezLineageGraphParserTest.class.getResourceAsStream(name)) {
      assertNotNull(in, name);
      return MAPPER.readTree(in);
    }
  }
}
