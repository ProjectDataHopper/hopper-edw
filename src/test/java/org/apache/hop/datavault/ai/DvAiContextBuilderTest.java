/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
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
  }

  @Test
  void serializeModelStructureIncludesHubBusinessKeys() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("HUB_CUSTOMER");
    hub.setRecordSources(List.of("customer"));
    org.apache.hop.datavault.metadata.BusinessKey bk =
        new org.apache.hop.datavault.metadata.BusinessKey("customer_id");
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
