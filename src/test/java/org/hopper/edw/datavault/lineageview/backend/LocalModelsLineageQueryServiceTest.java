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
package org.hopper.edw.datavault.lineageview.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.lineage.DvModelLineageCollector;
import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.openlineage.OpenLineageSnapshotMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class LocalModelsLineageQueryServiceTest {

  private Variables variables;
  private DataVaultModel model;
  private LineageSnapshot snapshot;

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
    snapshot = DvModelLineageCollector.collect(model, variables);
  }

  @Test
  void extraSnapshotsOnlyBuildsGraphWithSeed() throws Exception {
    LocalModelsLineageQueryService service =
        new LocalModelsLineageQueryService(variables, null, null, "retail-job", "retail-dataset");
    ObjectNode hub = hubEvent(snapshot);
    LineageQuery query =
        LineageQuery.builder()
            .extraSnapshots(List.of(snapshot))
            .job(
                OpenLineageRef.builder()
                    .namespace(hub.path("job").path("namespace").asText())
                    .name(hub.path("job").path("name").asText())
                    .build())
            .build();
    LineageGraph graph = service.fetchGraph(query);
    assertNotNull(graph.getSeedNodeId());
    assertTrue(graph.getSeedNodeId().startsWith("job:"));
    assertTrue(graph.getSeedNodeId().contains("hub_customer"));
    assertFalse(graph.getNodesOrEmpty().isEmpty());
    assertTrue(service.facetsInlineOnGraph());
    assertEquals(LineageBackendKind.LOCAL_MODELS, service.kind());
  }

  @Test
  void jobIdsMatchMapperWithSameNamespaces() throws Exception {
    List<ObjectNode> mapped =
        OpenLineageSnapshotMapper.toRunEvents(
            snapshot, "retail-job", "retail-dataset", false, "corr");
    ObjectNode hub =
        mapped.stream()
            .filter(e -> e.path("job").path("name").asText("").contains("hub_customer"))
            .findFirst()
            .orElseThrow();
    String expectedNs = hub.path("job").path("namespace").asText();
    String expectedName = hub.path("job").path("name").asText();

    LocalModelsLineageQueryService service =
        new LocalModelsLineageQueryService(variables, null, null, "retail-job", "retail-dataset");
    LineageGraph graph =
        service.fetchGraph(
            LineageQuery.builder()
                .extraSnapshots(List.of(snapshot))
                .job(OpenLineageRef.builder().namespace(expectedNs).name(expectedName).build())
                .build());
    assertNotNull(graph.findNode("job:" + expectedNs + ":" + expectedName));
  }

  @Test
  void extraSnapshotsOverrideSameFilename() {
    LineageSnapshot group = new LineageSnapshot();
    group.setModelFilename("${PROJECT_HOME}/models/retail-360.hdv");
    group.setModelName("group");
    LineageSnapshot extra = new LineageSnapshot();
    extra.setModelFilename("${PROJECT_HOME}/models/retail-360.hdv");
    extra.setModelName("editor");
    List<LineageSnapshot> merged =
        LocalModelsLineageQueryService.mergeExtras(List.of(group), List.of(extra));
    assertEquals(1, merged.size());
    assertSame(extra, merged.get(0));
  }

  @Test
  void missingGroupAndExtrasThrows() {
    LocalModelsLineageQueryService service =
        new LocalModelsLineageQueryService(variables, null, null, "retail-job", "retail-dataset");
    HopException error =
        assertThrows(
            HopException.class,
            () ->
                service.fetchGraph(
                    LineageQuery.builder()
                        .dataset(
                            OpenLineageRef.builder()
                                .namespace("retail-dataset")
                                .name("hub_customer")
                                .build())
                        .build()));
    assertTrue(error.getMessage().toLowerCase().contains("resource definition group"));
  }

  @Test
  void unknownSeedThrowsSeedNotFound() {
    LocalModelsLineageQueryService service =
        new LocalModelsLineageQueryService(variables, null, null, "retail-job", "retail-dataset");
    HopException error =
        assertThrows(
            HopException.class,
            () ->
                service.fetchGraph(
                    LineageQuery.builder()
                        .extraSnapshots(List.of(snapshot))
                        .dataset(OpenLineageRef.builder().namespace("nope").name("missing").build())
                        .build()));
    assertTrue(error.getMessage().contains(ILineageQueryService.SEED_NOT_FOUND));
  }

  @Test
  void queryResourceGroupWinsOverSettings() {
    LocalModelsLineageQueryService service =
        new LocalModelsLineageQueryService(variables, null, "settings-group", null, null);
    assertEquals(
        "query-group",
        service.resolveGroupName(LineageQuery.builder().resourceGroup("query-group").build()));
    assertEquals("settings-group", service.resolveGroupName(LineageQuery.builder().build()));
  }

  private static ObjectNode hubEvent(LineageSnapshot snapshot) {
    return OpenLineageSnapshotMapper.toRunEvents(
            snapshot, "retail-job", "retail-dataset", false, "corr")
        .stream()
        .filter(e -> e.path("job").path("name").asText("").contains("hub_customer"))
        .findFirst()
        .orElseThrow();
  }

  private static DataVaultModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel loaded = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, loaded, null);
    loaded.setFilename(fixture.toString().replace('\\', '/'));
    return loaded;
  }
}
