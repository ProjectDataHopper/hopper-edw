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
package org.hopper.edw.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceRef;
import org.junit.jupiter.api.Test;

class DvAiContextBuilderTest {

  @Test
  void collectSourceNamesFromModelReadsHubRecordSources() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setName("HUB_CUSTOMER");
    hub.setRecordSources(List.of("customer", "orders"));
    model.setTables(List.of(hub));

    assertEquals(
        List.of("customer", "orders"),
        DvAiContextBuilder.collectSourceNamesFromModel(model).stream().toList());
  }

  @Test
  void jsonStringEscapesQuotesAndNewlines() {
    assertEquals("\"line\\nbreak\"", DvAiContextBuilder.jsonString("line\nbreak"));
    assertEquals("\"say \\\"hi\\\"\"", DvAiContextBuilder.jsonString("say \"hi\""));
  }

  @Test
  void resolveSelectedCatalogSourceNamesPrefersExplicitSelection() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setRecordSources(List.of("customer"));
    model.setTables(List.of(hub));

    DvAiRequest request =
        DvAiRequest.builder().catalogSourceName("orders").catalogSourceName("product").build();

    assertEquals(
        List.of("orders", "product"),
        DvAiContextBuilder.resolveSelectedCatalogSourceNames(model, request));
  }

  @Test
  void resolveSelectedCatalogSourceNamesFallsBackToModelSources() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setRecordSources(List.of("customer"));
    model.setTables(List.of(hub));

    assertEquals(
        List.of("customer"),
        DvAiContextBuilder.resolveSelectedCatalogSourceNames(
            model, DvAiRequest.builder().userPrompt("test").build()));
    assertEquals(
        List.of("customer"),
        DvAiContextBuilder.resolveSelectedCatalogSourceNames(
            model, DvAiRequest.builder().userPrompt("test").build(), List.of()));
  }

  @Test
  void resolveSelectedCatalogSourceNamesPrefersExplicitSelectionOverCoach() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setRecordSources(List.of("customer"));
    model.setTables(List.of(hub));

    DvAiRequest request =
        DvAiRequest.builder().catalogSourceName("orders").catalogSourceName("product").build();

    assertEquals(
        List.of("orders", "product"),
        DvAiContextBuilder.resolveSelectedCatalogSourceNames(
            model, request, List.of("E2E-customer-address")));
  }

  @Test
  void resolveSelectedCatalogSourceNamesUsesCoachBeforeTableSources() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setRecordSources(List.of("customer"));
    model.setTables(List.of(hub));

    assertEquals(
        List.of("E2E-customer-address"),
        DvAiContextBuilder.resolveSelectedCatalogSourceNames(
            model,
            DvAiRequest.builder().userPrompt("test").build(),
            List.of("E2E-customer-address")));
  }

  @Test
  void buildIncludesSelectedCatalogSourcesInEnvelope() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setName("demo");
    model.getConfigurationOrDefault().setDataCatalogConnection("local-catalog");

    DvAiContextBundle bundle =
        DvAiContextBuilder.build(
            model,
            null,
            null,
            DvAiRequest.builder()
                .userPrompt("Build a model from catalog sources")
                .includeCatalogSources(true)
                .catalogSourceName("customer")
                .catalogSourceName("orders")
                .build());

    String catalogJson = bundle.getRecordDefinitionsJson();
    assertTrue(catalogJson.contains("\"catalogConnection\":\"local-catalog\""), catalogJson);
    assertTrue(catalogJson.contains("\"namespace\":"), catalogJson);
    assertTrue(catalogJson.contains("\"selected\":2"), catalogJson);
    assertTrue(catalogJson.contains("\"name\":\"customer\""), catalogJson);
    assertTrue(catalogJson.contains("\"name\":\"orders\""), catalogJson);
    assertFalse(catalogJson.contains("E2E-customer-address"), catalogJson);
  }

  @Test
  void buildUsesCoachSourcesWhenNothingIsSelected() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setName("demo");
    model.getConfigurationOrDefault().setDataCatalogConnection("local-catalog");
    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/retail-example/sources", "E2E-customer-address"));
    DvHub hub = new DvHub();
    hub.setRecordSources(List.of("customer"));
    model.setTables(List.of(hub));

    DvAiContextBundle bundle =
        DvAiContextBuilder.build(
            model,
            null,
            null,
            DvAiRequest.builder()
                .userPrompt("Which hub should this address feed load?")
                .includeCatalogSources(true)
                .build());

    String catalogJson = bundle.getRecordDefinitionsJson();
    assertTrue(catalogJson.contains("\"selected\":1"), catalogJson);
    assertTrue(catalogJson.contains("\"name\":\"E2E-customer-address\""), catalogJson);
    assertFalse(catalogJson.contains("\"name\":\"customer\""), catalogJson);
  }

  @Test
  void serializeRecordDefinitionIncludesKeysAndDescriptiveFields() {
    CatalogSourceField customerId = new CatalogSourceField();
    customerId.setName("customer_id");
    customerId.setHopType(5);
    customerId.setLength("9");
    customerId.setPrimaryKeyPosition(1);
    customerId.setSourceDataType("Integer");
    CatalogSourceField addressLine = new CatalogSourceField();
    addressLine.setName("address_line1");
    addressLine.setHopType(2);
    addressLine.setLength("50");
    addressLine.setSourceDataType("String");
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setFields(List.of(customerId, addressLine));
    RecordDefinition definition = new RecordDefinition();
    definition.setDescription("Retail customer address satellite database source");
    definition.setDvSource(dvSource);

    String json = DvAiContextBuilder.serializeRecordDefinitionEntry("E2E-customer-address", definition);

    assertTrue(json.contains("\"name\":\"E2E-customer-address\""), json);
    assertTrue(json.contains("\"name\":\"customer_id\""), json);
    assertTrue(json.contains("\"keyPosition\":1"), json);
    assertTrue(json.contains("\"length\":\"9\""), json);
    assertTrue(json.contains("\"name\":\"address_line1\""), json);
    assertTrue(json.contains("\"length\":\"50\""), json);
    assertTrue(json.contains("Retail customer address satellite database source"), json);
  }

  @Test
  void serializeModelStructureIncludesHubBusinessKeys() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("HUB_CUSTOMER");
    hub.setRecordSources(List.of("customer"));
    org.hopper.edw.datavault.metadata.BusinessKey bk =
        new org.hopper.edw.datavault.metadata.BusinessKey("customer_id");
    bk.setRecordSourceName("customer");
    bk.setSourceFieldName("id");
    hub.setBusinessKeys(List.of(bk));
    model.setTables(List.of(hub));

    String structure = DvAiContextBuilder.serializeModelStructure(model);

    assertTrue(structure.contains("\"name\":\"HUB_CUSTOMER\""), structure);
    assertTrue(structure.contains("\"businessKeys\""), structure);
    assertTrue(structure.contains("\"customer_id\""), structure);
    assertTrue(structure.contains("\"recordSources\""), structure);
  }
}
