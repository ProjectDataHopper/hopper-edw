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
package org.hopper.edw.datavault.openlineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.lineage.DmModelLineageCollector;
import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Diagnostic probe for DM OpenLineage events (dimensions/facts). Not a full integration test
 * against Marquez.
 */
class OpenLineageDmExportProbeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private Variables variables;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() {
    variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
  }

  @Test
  void retailDmModelsProduceDmJobsAndValidJson() throws Exception {
    String[] models = {
      "retail-example/models/retail-conformed-dims.hdm",
      "retail-example/models/retail-f-orders.hdm",
      "retail-example/models/retail-f-order-lines.hdm",
      "retail-example/models/retail-f-inventory.hdm",
      "retail-example/models/retail-warehouse.hdm"
    };
    List<ObjectNode> all = new ArrayList<>();
    OpenLineageLocationContext ctx = new OpenLineageLocationContext(variables, null, null);
    for (String path : models) {
      DimensionalModel model = loadModel(path);
      LineageSnapshot snapshot = DmModelLineageCollector.collect(model, variables, null);
      assertFalse(snapshot.getTables().isEmpty(), path + " should have tables");
      snapshot
          .getTables()
          .forEach(
              t -> {
                if (t.getTargetDatabaseMetaName() == null
                    || t.getTargetDatabaseMetaName().isBlank()) {
                  t.setTargetDatabaseMetaName("Vault");
                }
              });
      List<ObjectNode> events =
          OpenLineageSnapshotMapper.toRunEvents(
              snapshot, "retail-job", "retail-dataset", true, "probe", ctx);
      System.out.println(
          path
              + " tables="
              + snapshot.getTables().size()
              + " events="
              + events.size()
              + " jobs="
              + events.stream().map(e -> e.path("job").path("name").asText()).toList());
      all.addAll(events);
    }
    assertFalse(all.isEmpty());
    long dmJobs =
        all.stream().filter(e -> e.path("job").path("name").asText().startsWith("dm/")).count();
    assertTrue(dmJobs > 0, "expected dm/* jobs");
    // Role-playing aliases must keep unique job names (logical), not collapse to physical d_date.
    assertTrue(
        all.stream().anyMatch(e -> e.path("job").path("name").asText().contains("d_order_date")),
        "expected unique job for d_order_date alias");

    // Alias datasets use logical name and symlink to the shared physical dimension (d_date).
    ObjectNode shipping =
        all.stream()
            .filter(e -> e.path("job").path("name").asText().contains("d_shipping_date"))
            .findFirst()
            .orElse(null);
    assertTrue(shipping != null, "d_shipping_date alias event expected");
    assertEquals(
        "d_shipping_date",
        shipping.path("outputs").get(0).path("name").asText(),
        "alias dataset identity should be the logical role name");
    assertTrue(
        shipping.path("outputs").get(0).path("facets").path("schema").path("fields").size() > 0
            || shipping.path("outputs").get(0).path("facets").has("symlinks"),
        "alias should expose projected fields and/or a symlink to the physical dimension");
    JsonNode identifiers =
        shipping.path("outputs").get(0).path("facets").path("symlinks").path("identifiers");
    assertTrue(identifiers.isArray() && identifiers.size() >= 1, "expected symlink identifiers");
    boolean linksToDate = false;
    for (JsonNode id : identifiers) {
      if ("d_date".equalsIgnoreCase(id.path("name").asText())) {
        linksToDate = true;
        break;
      }
    }
    assertTrue(linksToDate, "d_shipping_date should symlink to physical d_date");

    // Every event must serialize and have non-blank job + at least one output name.
    for (ObjectNode event : all) {
      String json = OpenLineageSnapshotMapper.toCompactJson(event);
      assertFalse(json.isBlank());
      JsonNode parsed = MAPPER.readTree(json);
      assertFalse(parsed.path("job").path("name").asText().isBlank());
      assertTrue(parsed.path("outputs").isArray() && parsed.path("outputs").size() >= 1);
      assertFalse(parsed.path("outputs").get(0).path("name").asText().isBlank());
      // Facets must remain valid objects if present
      JsonNode facets = parsed.path("outputs").get(0).path("facets");
      if (facets.isObject() && facets.has("dataSource")) {
        assertTrue(facets.path("dataSource").path("name").isTextual());
      }
    }
  }

  private static DimensionalModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel loaded = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, loaded, null);
    loaded.setFilename(fixture.toString().replace('\\', '/'));
    return loaded;
  }
}
