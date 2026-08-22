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
package org.hopper.edw.catalog.impl.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hopper.edw.catalog.model.PhysicalIcebergTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.junit.jupiter.api.Test;

class RecordDefinitionDocumentIcebergTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void projectCatalogJson_deserializesPhysicalIcebergTable() throws Exception {
    Path catalogJson =
        Path.of(
            "integration-tests/catalog-data/hop/integration-tests/sources/CRM-customer-iceberg.json");
    RecordDefinitionDocument doc =
        MAPPER.readValue(Files.readString(catalogJson), RecordDefinitionDocument.class);

    assertEquals("hop/integration-tests/sources", doc.getNamespace());
    assertEquals("CRM-customer-iceberg", doc.getName());
    assertEquals("ICEBERG", doc.getDvSource().getSourceType());
    assertNotNull(doc.getPhysicalIcebergTable());
    assertEquals("${ICEBERG_CATALOG_URI}", doc.getPhysicalIcebergTable().getCatalogUri());
    assertEquals("${ICEBERG_NAMESPACE}", doc.getPhysicalIcebergTable().getNamespace());
    assertEquals("${ICEBERG_TABLE}", doc.getPhysicalIcebergTable().getTableName());
  }

  @Test
  void jsonRoundTrip_preservesPhysicalIcebergTable() throws Exception {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/project/sources", "CRM-customer-iceberg"));
    definition.setType(RecordDefinitionType.DV_SOURCE);

    PhysicalIcebergTableRef physicalIcebergTable = new PhysicalIcebergTableRef();
    physicalIcebergTable.setCatalogUri("${ICEBERG_CATALOG_URI}");
    physicalIcebergTable.setWarehouse("${ICEBERG_WAREHOUSE}");
    physicalIcebergTable.setNamespace("${ICEBERG_NAMESPACE}");
    physicalIcebergTable.setTableName("${ICEBERG_TABLE}");
    physicalIcebergTable.setS3Endpoint("${S3_ENDPOINT}");
    physicalIcebergTable.setS3AccessKey("${S3_ACCESS_KEY}");
    physicalIcebergTable.setS3SecretKey("${S3_SECRET_KEY}");
    definition.setPhysicalIcebergTable(physicalIcebergTable);

    RecordDefinitionDocument doc = RecordDefinitionDocument.from(definition);
    String serialized = MAPPER.writeValueAsString(doc);
    assertTrue(serialized.contains("physicalIcebergTable"), serialized);
    assertTrue(serialized.contains("${ICEBERG_CATALOG_URI}"), serialized);

    RecordDefinitionDocument restored =
        MAPPER.readValue(serialized, RecordDefinitionDocument.class);
    assertEquals(
        physicalIcebergTable.getCatalogUri(), restored.getPhysicalIcebergTable().getCatalogUri());
    assertEquals(
        physicalIcebergTable.getS3SecretKey(), restored.getPhysicalIcebergTable().getS3SecretKey());
  }
}
