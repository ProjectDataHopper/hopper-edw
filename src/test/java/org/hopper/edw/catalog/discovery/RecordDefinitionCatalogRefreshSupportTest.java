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
package org.hopper.edw.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.List;
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.edw.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

class RecordDefinitionCatalogRefreshSupportTest {

  @Test
  void applyDiscoveredFieldsForIcebergPreservesStoredLengthAndPrecision() throws Exception {
    RecordDefinition definition = icebergDefinition();
    DvSourceRecord dvSource = definition.getDvSource();
    dvSource.setFields(
        List.of(catalogField("customer_id", "Integer", IValueMeta.TYPE_INTEGER, "9", "0")));

    List<SourceField> discovered =
        List.of(field("customer_id", "Integer", IValueMeta.TYPE_INTEGER, "15", ""));

    RecordDefinitionCatalogRefreshSupport.applyDiscoveredFields(definition, discovered, new Date());

    assertEquals("9", definition.getDvSource().getFields().get(0).getLength());
    assertEquals("0", definition.getDvSource().getFields().get(0).getPrecision());
  }

  @Test
  void applyDiscoveredFieldsUpdatesContractAndProvenance() throws Exception {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/integration-tests/sources", "CRM-customer"));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("ICEBERG");
    dvSource.setFields(List.of());
    definition.setDvSource(dvSource);

    List<SourceField> discovered =
        List.of(field("customer_id", "Integer", IValueMeta.TYPE_INTEGER, "9", "0"));
    Date discoveredAt = new Date(1_700_000_000_000L);

    RecordDefinitionCatalogRefreshSupport.applyDiscoveredFields(
        definition, discovered, discoveredAt, 42L);

    assertEquals(1, definition.getDvSource().getFields().size());
    assertEquals("customer_id", definition.getDvSource().getFields().get(0).getName());
    assertNotNull(definition.getFields());
    assertEquals(1, definition.getFields().size());
    RecordOrigin origin = definition.getOrigin();
    assertNotNull(origin);
    assertEquals(discoveredAt, origin.getLastDiscoveredAt());
    assertEquals(
        "42",
        definition
            .getCustomProperties()
            .get(RecordDefinitionCatalogRefreshSupport.CUSTOM_PROPERTY_PHYSICAL_SCHEMA_ID)
            .getValue());
  }

  private static RecordDefinition icebergDefinition() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(
        new RecordDefinitionKey("hop/integration-tests/sources", "CRM-customer-iceberg"));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("ICEBERG");
    dvSource.setFields(List.of());
    definition.setDvSource(dvSource);
    return definition;
  }

  private static CatalogSourceField catalogField(
      String name, String sourceDataType, int hopType, String length, String precision) {
    CatalogSourceField field = new CatalogSourceField();
    field.setName(name);
    field.setSourceDataType(sourceDataType);
    field.setHopType(hopType);
    field.setLength(length);
    field.setPrecision(precision);
    return field;
  }

  private static SourceField field(
      String name, String sourceDataType, int hopType, String length, String precision) {
    SourceField field = new SourceField(name);
    field.setSourceDataType(sourceDataType);
    field.setHopType(hopType);
    field.setLength(length);
    field.setPrecision(precision);
    return field;
  }
}
