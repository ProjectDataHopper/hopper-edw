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
package org.apache.hop.datavault.openlineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalFileRef;
import org.apache.hop.catalog.model.PhysicalIcebergTableRef;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineage.TableLineage;
import org.junit.jupiter.api.Test;

class OpenLineageDatasetFacetSupportTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void attachesDataSourceAndHopLocationForDatabase() {
    DatasetLocation location =
        DatasetLocation.builder()
            .kind(DatasetLocationKind.DATABASE)
            .connectionName("Vault")
            .schemaName("public")
            .tableName("hub_customer")
            .dataSourceName("Vault")
            .uri("jdbc:postgresql://localhost:54320/vault")
            .build();
    ObjectNode dataset = MAPPER.createObjectNode();
    dataset.put("namespace", "retail-dataset");
    dataset.put("name", "hub_customer");
    OpenLineageDatasetFacetSupport.attachLocationFacets(dataset, location);

    assertEquals("Vault", dataset.path("facets").path("dataSource").path("name").asText());
    assertTrue(
        dataset.path("facets").path("dataSource").path("uri").asText().contains("postgresql"));
    assertEquals("DATABASE", dataset.path("facets").path("hop_location").path("kind").asText());
    assertEquals("public", dataset.path("facets").path("hop_location").path("schemaName").asText());
    assertEquals(
        "hub_customer", dataset.path("facets").path("hop_location").path("tableName").asText());
    assertTrue(dataset.path("facets").has("symlinks"));
  }

  @Test
  void hopLocationIncludesCatalogIdentity() {
    DatasetLocation location =
        DatasetLocation.builder()
            .kind(DatasetLocationKind.DATABASE)
            .connectionName("CRM")
            .tableName("customer_prefs")
            .catalogKey("hop/retail/sources/E2E-customer-prefs")
            .catalogConnection("edw-catalog")
            .uri("jdbc:postgresql://localhost:54320/crm")
            .build();
    ObjectNode dataset = MAPPER.createObjectNode();
    dataset.put("name", "E2E-customer-prefs");
    OpenLineageDatasetFacetSupport.attachLocationFacets(dataset, location);
    JsonNode hop = dataset.path("facets").path("hop_location");
    assertEquals("hop/retail/sources/E2E-customer-prefs", hop.path("catalogKey").asText());
    assertEquals("edw-catalog", hop.path("catalogConnection").asText());
  }

  @Test
  void stripCredentialsRemovesUserInfo() {
    assertEquals(
        "jdbc:postgresql://localhost/db",
        OpenLineageDatasetFacetSupport.stripCredentials(
            "jdbc:postgresql://user:secret@localhost/db"));
    assertFalse(
        OpenLineageDatasetFacetSupport.stripCredentials(
                "jdbc:postgresql://localhost/db?user=u&password=p")
            .toLowerCase()
            .contains("password=p"));
  }

  @Test
  void stagingDataSourceNeverUsesBareStagingNameAndEncodesUri() {
    DatasetLocation location =
        DatasetLocation.builder()
            .kind(DatasetLocationKind.STAGING)
            .dataSourceName("STAGING")
            .tableName("SQL staging")
            .uri("hop://staging/SQL staging")
            .build();
    ObjectNode dataset = MAPPER.createObjectNode();
    dataset.put("namespace", "retail-dataset");
    dataset.put("name", "SQL staging");
    OpenLineageDatasetFacetSupport.attachLocationFacets(dataset, location);
    assertEquals(
        "staging:SQL staging", dataset.path("facets").path("dataSource").path("name").asText());
    assertFalse(
        dataset.path("facets").path("dataSource").path("uri").asText().contains(" "),
        "Marquez rejects dataSource.uri with spaces (connection_url NOT NULL / parse)");
    assertTrue(
        dataset.path("facets").path("dataSource").path("uri").asText().contains("%20")
            || dataset.path("facets").path("dataSource").path("uri").asText().contains("_"));
  }

  @Test
  void unresolvedHopVariablesAreEncodedInDataSourceUri() {
    DatasetLocation location =
        DatasetLocation.builder()
            .kind(DatasetLocationKind.STAGING)
            .tableName("${PROJECT_HOME}/pipelines/generate-date-dimension-data.hpl")
            .uri("hop://staging/${PROJECT_HOME}/pipelines/generate-date-dimension-data.hpl")
            .build();
    ObjectNode dataset = MAPPER.createObjectNode();
    dataset.put("name", "pipeline-src");
    OpenLineageDatasetFacetSupport.attachLocationFacets(dataset, location);
    String uri = dataset.path("facets").path("dataSource").path("uri").asText();
    assertFalse(uri.contains("$"), "Marquez nulls connection_url when uri contains $");
    assertTrue(uri.contains("%24") || uri.contains("%7B"));
  }

  @Test
  void fromRecordDefinitionMapsCsvAndIceberg() {
    RecordDefinition csv = new RecordDefinition();
    csv.setKey(new RecordDefinitionKey("ns", "E2E-customer"));
    PhysicalFileRef file = new PhysicalFileRef();
    file.setFolder("/data/customer");
    file.setIncludeFileMask("*.csv");
    csv.setPhysicalFile(file);
    DvSourceRecord dv = new DvSourceRecord();
    dv.setSourceType("CSV");
    csv.setDvSource(dv);

    DatasetLocation csvLoc = OpenLineageDatasetLocationResolver.fromRecordDefinition(csv, null);
    assertEquals(DatasetLocationKind.CSV, csvLoc.getKind());
    assertTrue(csvLoc.getUri().contains("data/customer"));
    assertEquals("ns/E2E-customer", csvLoc.getCatalogKey());

    RecordDefinition iceberg = new RecordDefinition();
    iceberg.setKey(new RecordDefinitionKey("ns", "ice-cust"));
    PhysicalIcebergTableRef ice = new PhysicalIcebergTableRef();
    ice.setCatalogUri("http://catalog:8181");
    ice.setNamespace("retail");
    ice.setTableName("customers");
    iceberg.setPhysicalIcebergTable(ice);
    DatasetLocation iceLoc = OpenLineageDatasetLocationResolver.fromRecordDefinition(iceberg, null);
    assertEquals(DatasetLocationKind.ICEBERG, iceLoc.getKind());
    assertTrue(iceLoc.getUri().contains("customers"));
  }

  @Test
  void catalogSourceWithoutRegistryStillKeepsCatalogIdentity() {
    OpenLineageLocationContext context = new OpenLineageLocationContext(null, null, "edw-catalog");
    DatasetLocation loc =
        OpenLineageDatasetLocationResolver.forCatalogSource(
            "E2E-customer-prefs", "hop/retail/sources/E2E-customer-prefs", context);
    assertEquals("hop/retail/sources/E2E-customer-prefs", loc.getCatalogKey());
    assertEquals("edw-catalog", loc.getCatalogConnection());
    assertEquals("E2E-customer-prefs", loc.getTableName());
  }

  @Test
  void targetTableLocationFromLineage() {
    TableLineage table = new TableLineage();
    table.setLayer(LineageLayer.DV);
    table.setPhysicalTableName("hub_customer");
    table.setSchemaName("public");
    table.setTargetDatabaseMetaName("Vault");
    DatasetLocation loc = OpenLineageDatasetLocationResolver.forTargetTable(table, null);
    assertEquals(DatasetLocationKind.DATABASE, loc.getKind());
    assertEquals("Vault", loc.getConnectionName());
    assertEquals("hub_customer", loc.getTableName());
  }

  @Test
  void fromRecordDefinitionMapsDatabaseTable() {
    RecordDefinition def = new RecordDefinition();
    def.setKey(new RecordDefinitionKey("ns", "CRM-customer"));
    PhysicalTableRef table = new PhysicalTableRef();
    table.setDatabaseMetaName("CRM");
    table.setSchemaName("sales");
    table.setTableName("customer");
    def.setPhysicalTable(table);
    DatasetLocation loc = OpenLineageDatasetLocationResolver.fromRecordDefinition(def, null);
    assertEquals(DatasetLocationKind.DATABASE, loc.getKind());
    assertEquals("CRM", loc.getConnectionName());
    assertEquals("sales", loc.getSchemaName());
    assertEquals("customer", loc.getTableName());
  }
}
