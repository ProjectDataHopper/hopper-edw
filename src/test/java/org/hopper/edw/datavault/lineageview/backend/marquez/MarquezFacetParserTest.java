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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.hopper.edw.datavault.lineageview.backend.HopExportFacet;
import org.hopper.edw.datavault.lineageview.backend.HopOpsFacet;
import org.junit.jupiter.api.Test;

class MarquezFacetParserTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void parsesHopExportAndHopOpsFromRunFacets() throws Exception {
    JsonNode root;
    try (InputStream in = MarquezFacetParserTest.class.getResourceAsStream("run-facets.json")) {
      assertNotNull(in);
      root = MAPPER.readTree(in);
    }
    JsonNode facets = root.path("facets");
    HopExportFacet export = MarquezFacetParser.hopExport(facets);
    assertNotNull(export);
    assertEquals("DM", export.getModelLayer());
    assertEquals("retail-pos", export.getModelName());
    assertEquals("f_orders", export.getLogicalName());
    assertEquals("Vault", export.getTargetDatabase());

    HopOpsFacet ops = MarquezFacetParser.hopOps(facets);
    assertNotNull(ops);
    assertEquals(42_000L, ops.getDurationMs());
    assertEquals("load-1", ops.getLoadRunId());
  }

  @Test
  void latestRunObjectIsNotHopOps() throws Exception {
    JsonNode latestRun =
        MAPPER.readTree(
            "{\"id\":\"r1\",\"durationMs\":12345,\"startedAt\":\"2026-01-01T00:00:00Z\"}");
    assertNull(MarquezFacetParser.hopOps(latestRun));
    assertNull(MarquezFacetParser.hopExport(latestRun));
  }
}
