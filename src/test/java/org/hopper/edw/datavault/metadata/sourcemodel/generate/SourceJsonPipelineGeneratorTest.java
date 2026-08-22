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
package org.hopper.edw.datavault.metadata.sourcemodel.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonField;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonParentKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.jsoninput.JsonInputMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceJsonPipelineGeneratorTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void generatesTableInputAndJsonInput() throws Exception {
    SourceModel model = sampleModel();
    SourceJson json = model.findJsonSource("order_lines");
    IHopMetadataProvider metadataProvider = memoryProviderWithCrm();

    PipelineMeta pipeline =
        SourceJsonPipelineGenerator.generate(model, json, new Variables(), metadataProvider);

    assertTrue(pipeline.getTransforms().size() >= 2);
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof TableInputMeta));
    assertTrue(
        pipeline.getTransforms().stream().anyMatch(t -> t.getTransform() instanceof JsonInputMeta));

    JsonInputMeta jsonMeta =
        (JsonInputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof JsonInputMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(jsonMeta.isInFields());
    assertEquals("payload", jsonMeta.getFieldValue());
    assertEquals(2, jsonMeta.getInputFields().size());
  }

  @Test
  void rejectsNonDatabaseTableParent() {
    SourceModel model = sampleModel();
    model.findTable("events").setPhysicalType(DvSourceType.CSV);
    SourceJson json = model.findJsonSource("order_lines");
    IHopMetadataProvider metadataProvider = new MemoryMetadataProvider();

    HopException ex =
        assertThrows(
            HopException.class,
            () ->
                SourceJsonPipelineGenerator.generate(
                    model, json, new Variables(), metadataProvider));
    assertTrue(
        ex.getMessage().contains("CSV") || ex.getMessage().toLowerCase().contains("database"));
  }

  private static IHopMetadataProvider memoryProviderWithCrm() throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta crm = new DatabaseMeta();
    crm.setName("CRM");
    crm.setDatabaseType("POSTGRESQL");
    crm.setHostname("localhost");
    crm.setDBName("test");
    crm.setUsername("test");
    crm.setPassword("test");
    provider.getSerializer(DatabaseMeta.class).save(crm);
    return provider;
  }

  private static SourceModel sampleModel() {
    SourceModel model = new SourceModel();
    SourceTable events = new SourceTable("events");
    events.setPhysicalType(DvSourceType.DATABASE);
    events.setDatabaseName("CRM");
    events.setTableName("events");
    SourceColumn eventId = new SourceColumn("event_id");
    eventId.setPrimaryKeyPosition(1);
    eventId.setHopType(5);
    events.getColumns().add(eventId);
    SourceColumn payload = new SourceColumn("payload");
    payload.setHopType(2);
    events.getColumns().add(payload);
    model.getTables().add(events);

    SourceJson json = new SourceJson("order_lines");
    json.setParentSourceKind(SourceJsonParentKind.TABLE);
    json.setParentSourceName("events");
    json.setJsonFieldName("payload");
    SourceJsonField pass = SourceJsonField.passThroughField("event_id");
    pass.setPrimaryKeyPosition(1);
    pass.setHopType(5);
    json.getFields().add(pass);
    SourceJsonField sku = new SourceJsonField("sku", "$.lines.*.sku");
    sku.setHopType(2);
    sku.setPrimaryKeyPosition(2);
    json.getFields().add(sku);
    SourceJsonField qty = new SourceJsonField("qty", "$.lines.*.qty");
    qty.setHopType(5);
    json.getFields().add(qty);
    model.getJsonSources().add(json);
    return model;
  }
}
