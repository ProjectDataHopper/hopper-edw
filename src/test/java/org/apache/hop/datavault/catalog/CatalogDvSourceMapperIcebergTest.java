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
package org.apache.hop.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalIcebergTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.iceberg.DvIcebergSource;
import org.junit.jupiter.api.Test;

class CatalogDvSourceMapperIcebergTest {

  @Test
  void mapsIcebergCatalogDefinitionToDataVaultSource() throws Exception {
    RecordDefinition definition = icebergRecordDefinition();

    DataVaultSource source = CatalogDvSourceMapper.toDataVaultSource(definition, new Variables());

    assertEquals("CRM-customer-iceberg", source.getName());
    assertEquals(DvSourceDeliveryType.FULL_SNAPSHOT, source.getDeliveryTypeOrDefault());
    assertEquals(DvSourceType.ICEBERG, source.getSourceType());

    IDvSource dvSource = source.getDvSourceOrDefault();
    DvIcebergSource icebergSource = assertInstanceOf(DvIcebergSource.class, dvSource);
    assertEquals("http://iceberg-rest:8181", icebergSource.getCatalogUri());
    assertEquals("s3://warehouse/", icebergSource.getWarehouse());
    assertEquals("crm", icebergSource.getNamespace());
    assertEquals("customer", icebergSource.getTableName());
    assertEquals("http://minio:9000", icebergSource.getS3Endpoint());
    assertEquals(2, icebergSource.getFields().size());
    assertEquals("customer_id", icebergSource.getFields().get(0).getName());
  }

  @Test
  void mapsIcebergLocationBetweenCatalogAndDvSource() {
    DvIcebergSource icebergSource = new DvIcebergSource();
    icebergSource.setCatalogUri("http://iceberg-rest:8181");
    icebergSource.setWarehouse("s3://warehouse/");
    icebergSource.setNamespace("crm");
    icebergSource.setTableName("customer");
    icebergSource.setS3Endpoint("http://minio:9000");
    icebergSource.setS3AccessKey("admin");
    icebergSource.setS3SecretKey("password");

    PhysicalIcebergTableRef physicalIcebergTable =
        org.apache.hop.datavault.metadata.iceberg.DvIcebergLocationSupport
            .toPhysicalIcebergTableRef(icebergSource);
    assertEquals("http://iceberg-rest:8181", physicalIcebergTable.getCatalogUri());
    assertEquals("crm", physicalIcebergTable.getNamespace());
    assertEquals("customer", physicalIcebergTable.getTableName());
    assertEquals("admin", physicalIcebergTable.getS3AccessKey());

    DvIcebergSource restored = new DvIcebergSource();
    org.apache.hop.datavault.metadata.iceberg.DvIcebergLocationSupport.applyPhysicalIcebergTable(
        restored, physicalIcebergTable);
    assertEquals(icebergSource.getCatalogUri(), restored.getCatalogUri());
    assertEquals(icebergSource.getS3SecretKey(), restored.getS3SecretKey());
    assertEquals(DvSourceType.ICEBERG, restored.getSourceType());
  }

  private static RecordDefinition icebergRecordDefinition() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(
        new RecordDefinitionKey("hop/integration-tests/sources", "CRM-customer-iceberg"));
    definition.setType(RecordDefinitionType.DV_SOURCE);

    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("ICEBERG");
    dvSource.setSourceIndicatorField("record_source");
    dvSource.setDeliveryType("FULL_SNAPSHOT");
    dvSource.setFields(List.of(catalogField("customer_id", 5), catalogField("record_source", 2)));
    definition.setDvSource(dvSource);

    PhysicalIcebergTableRef physicalIcebergTable = new PhysicalIcebergTableRef();
    physicalIcebergTable.setCatalogUri("http://iceberg-rest:8181");
    physicalIcebergTable.setWarehouse("s3://warehouse/");
    physicalIcebergTable.setNamespace("crm");
    physicalIcebergTable.setTableName("customer");
    physicalIcebergTable.setS3Endpoint("http://minio:9000");
    definition.setPhysicalIcebergTable(physicalIcebergTable);
    return definition;
  }

  private static CatalogSourceField catalogField(String name, int hopType) {
    CatalogSourceField field = new CatalogSourceField();
    field.setName(name);
    field.setHopType(hopType);
    return field;
  }
}
