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
package org.apache.hop.datavault.lineageview.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenLineageEventGraphBuilderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void buildsDatasetJobDatasetFromRunEvent() {
    ObjectNode event =
        runEvent("retail-job", "dm/retail-pos/f_orders", "retail-dataset", "bv_in", "f_orders");
    LineageGraph graph =
        OpenLineageEventGraphBuilder.build(List.of(event), "dataset:retail-dataset:f_orders");
    assertEquals("dataset:retail-dataset:f_orders", graph.getSeedNodeId());
    assertNotNull(graph.findNode("job:retail-job:dm/retail-pos/f_orders"));
    assertEquals(
        LineageGraphLayer.DM, graph.findNode("job:retail-job:dm/retail-pos/f_orders").getLayer());
    assertNotNull(graph.findNode("dataset:retail-dataset:bv_in"));
    assertTrue(
        graph.getEdgesOrEmpty().stream()
            .anyMatch(
                e ->
                    "dataset:retail-dataset:bv_in".equals(e.getFromNodeId())
                        && "job:retail-job:dm/retail-pos/f_orders".equals(e.getToNodeId())));
    assertTrue(
        graph.getEdgesOrEmpty().stream()
            .anyMatch(
                e ->
                    "job:retail-job:dm/retail-pos/f_orders".equals(e.getFromNodeId())
                        && "dataset:retail-dataset:f_orders".equals(e.getToNodeId())));
  }

  static ObjectNode runEvent(
      String jobNs, String jobName, String dsNs, String input, String output) {
    ObjectNode event = MAPPER.createObjectNode();
    event.put("eventType", "COMPLETE");
    event.put("eventTime", "2026-08-14T10:00:00Z");
    ObjectNode job = MAPPER.createObjectNode();
    job.put("namespace", jobNs);
    job.put("name", jobName);
    event.set("job", job);
    ObjectNode run = MAPPER.createObjectNode();
    ObjectNode facets = MAPPER.createObjectNode();
    ObjectNode hopExport = MAPPER.createObjectNode();
    hopExport.put("modelLayer", "DM");
    hopExport.put("logicalName", output);
    facets.set("hop_export", hopExport);
    run.set("facets", facets);
    event.set("run", run);
    ArrayNode inputs = MAPPER.createArrayNode();
    ObjectNode in = MAPPER.createObjectNode();
    in.put("namespace", dsNs);
    in.put("name", input);
    inputs.add(in);
    event.set("inputs", inputs);
    ArrayNode outputs = MAPPER.createArrayNode();
    ObjectNode out = MAPPER.createObjectNode();
    out.put("namespace", dsNs);
    out.put("name", output);
    outputs.add(out);
    event.set("outputs", outputs);
    return event;
  }
}
