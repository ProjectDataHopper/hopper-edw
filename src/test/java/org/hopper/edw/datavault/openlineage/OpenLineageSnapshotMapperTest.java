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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.lineage.DvModelLineageCollector;
import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class OpenLineageSnapshotMapperTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Variables variables;
  private DataVaultModel model;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    model = loadModel("retail-example/models/retail-360.hdv");
  }

  @Test
  void retailHubCustomerProducesColumnLineageEvent() throws Exception {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    List<ObjectNode> events =
        OpenLineageSnapshotMapper.toRunEvents(snapshot, "hop-data-vault", true, "run-test-1");

    assertFalse(events.isEmpty(), "expected OpenLineage events for retail-360 tables");

    ObjectNode hubEvent =
        events.stream()
            .filter(e -> e.path("job").path("name").asText("").contains("hub_customer"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("hub_customer event missing"));

    assertEquals("COMPLETE", hubEvent.path("eventType").asText());
    // Each job gets its own runId; export correlation id is on hop_export facet.
    assertFalse(hubEvent.path("run").path("runId").asText().isBlank());
    assertEquals(
        "run-test-1",
        hubEvent.path("run").path("facets").path("hop_export").path("exportRunId").asText());
    assertTrue(hubEvent.path("job").path("namespace").asText().contains("hop-data-vault"));
    assertTrue(hubEvent.path("job").path("name").asText().startsWith("dv/"));
    assertTrue(hubEvent.path("inputs").isArray());
    assertTrue(hubEvent.path("inputs").size() >= 1);
    assertEquals(1, hubEvent.path("outputs").size());

    JsonNode output = hubEvent.path("outputs").get(0);
    assertTrue(output.path("facets").path("schema").path("fields").isArray());
    assertTrue(output.path("facets").path("schema").path("fields").size() >= 1);
    JsonNode columnLineage = output.path("facets").path("columnLineage");
    assertTrue(
        columnLineage.path("fields").has("customer_id")
            || columnLineage.path("fields").fieldNames().hasNext(),
        "expected columnLineage fields");
    if (columnLineage.path("fields").has("customer_id")) {
      assertTrue(columnLineage.path("fields").path("customer_id").path("inputFields").size() >= 1);
    }

    // Source feeds appear as input datasets with inferred schema fields.
    boolean sourceHasSchema = false;
    for (JsonNode input : hubEvent.path("inputs")) {
      if (input.path("facets").path("schema").path("fields").isArray()
          && input.path("facets").path("schema").path("fields").size() > 0) {
        sourceHasSchema = true;
        break;
      }
    }
    assertTrue(sourceHasSchema, "at least one source input should carry a schema facet");

    String json = OpenLineageSnapshotMapper.toPrettyJson(hubEvent);
    assertTrue(json.contains("columnLineage"));
    assertTrue(json.contains("COMPLETE"));
  }

  @Test
  void withoutColumnLineageStillEmitsOutputSchema() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    List<ObjectNode> events =
        OpenLineageSnapshotMapper.toRunEvents(snapshot, "hop-data-vault", false, "run-2");
    ObjectNode hubEvent =
        events.stream()
            .filter(e -> e.path("job").path("name").asText("").contains("hub_customer"))
            .findFirst()
            .orElse(events.get(0));
    assertTrue(hubEvent.path("outputs").get(0).path("facets").path("schema").has("fields"));
    assertFalse(hubEvent.path("outputs").get(0).path("facets").has("columnLineage"));
  }

  @Test
  void eachEventHasUniqueRunId() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    List<ObjectNode> events =
        OpenLineageSnapshotMapper.toRunEvents(snapshot, "hop-data-vault", true, "export-corr");
    Set<String> runIds = new HashSet<>();
    for (ObjectNode event : events) {
      String runId = event.path("run").path("runId").asText();
      assertTrue(runIds.add(runId), "duplicate runId: " + runId);
    }
    assertTrue(runIds.size() >= 2);
  }

  @Test
  void datasetNamespaceOverrideAppliesToInputsAndOutputs() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    List<ObjectNode> events =
        OpenLineageSnapshotMapper.toRunEvents(
            snapshot, "hop-data-vault", "retail-example", true, "export-ds-ns");
    ObjectNode hubEvent =
        events.stream()
            .filter(e -> e.path("job").path("name").asText("").contains("hub_customer"))
            .findFirst()
            .orElseThrow();
    assertEquals("retail-example", hubEvent.path("outputs").get(0).path("namespace").asText());
    assertTrue(hubEvent.path("inputs").size() >= 1);
    for (JsonNode input : hubEvent.path("inputs")) {
      assertEquals("retail-example", input.path("namespace").asText());
    }
  }

  @Test
  void locationContextAttachesHopLocationOnOutputs() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    // Ensure target connection is set so location facets can be built without DatabaseMeta.
    snapshot
        .getTables()
        .forEach(
            t -> {
              if (t.getTargetDatabaseMetaName() == null
                  || t.getTargetDatabaseMetaName().isBlank()) {
                t.setTargetDatabaseMetaName("Vault");
              }
            });
    OpenLineageLocationContext ctx = new OpenLineageLocationContext(variables, null, null);
    List<ObjectNode> events =
        OpenLineageSnapshotMapper.toRunEvents(
            snapshot, "hop-data-vault", null, true, "export-loc", ctx);
    ObjectNode hubEvent =
        events.stream()
            .filter(e -> e.path("job").path("name").asText("").contains("hub_customer"))
            .findFirst()
            .orElseThrow();
    JsonNode out = hubEvent.path("outputs").get(0);
    assertTrue(out.path("facets").has("dataSource"), "output should have dataSource facet");
    assertTrue(out.path("facets").has("hop_location"), "output should have hop_location facet");
    assertEquals("DATABASE", out.path("facets").path("hop_location").path("kind").asText());
    assertEquals(
        "hub_customer", out.path("facets").path("hop_location").path("tableName").asText());
  }

  @Test
  void hopExportIncludesHopIdentityFields() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    snapshot.setProjectKey("retail");
    snapshot.setResourceGroup("retail-sources");
    snapshot.setCatalogConnection("edw-catalog");
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
        OpenLineageSnapshotMapper.toRunEvents(snapshot, "hop-data-vault", true, "export-id");
    ObjectNode hubEvent =
        events.stream()
            .filter(e -> e.path("job").path("name").asText("").contains("hub_customer"))
            .findFirst()
            .orElseThrow();
    JsonNode hopExport = hubEvent.path("run").path("facets").path("hop_export");
    assertEquals("retail", hopExport.path("projectKey").asText());
    assertEquals("retail-sources", hopExport.path("resourceGroup").asText());
    assertEquals("edw-catalog", hopExport.path("catalogConnection").asText());
    assertEquals("hub_customer", hopExport.path("physicalTableName").asText());
    assertEquals("Vault", hopExport.path("targetDatabase").asText());
    assertEquals("HUB", hopExport.path("tableType").asText());
    assertFalse(hopExport.path("logicalName").asText().isBlank());
  }

  @Test
  void fileNameForEventUsesJobName() {
    ObjectNode event = MAPPER.createObjectNode();
    ObjectNode job = MAPPER.createObjectNode();
    job.put("name", "dv/retail-360/hub_customer");
    event.set("job", job);
    assertEquals(
        "dv_retail-360_hub_customer.json", OpenLineageSnapshotMapper.fileNameForEvent(event));
  }

  private static DataVaultModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    model.setFilename(fixture.toString().replace('\\', '/'));
    return model;
  }
}
