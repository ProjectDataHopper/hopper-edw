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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecordDefinitionDocumentRowMetaTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void migratesLegacyRowMetaXmlIntoPhysicalTableFields() throws Exception {
    RecordDefinitionDocument document = new RecordDefinitionDocument();
    document.setNamespace("hop/test/operations");
    document.setName("load_run");
    document.setType("PHYSICAL_TABLE");
    PhysicalTableRef physical = new PhysicalTableRef();
    physical.setDatabaseMetaName("Vault");
    physical.setTableName("load_run");
    document.setPhysicalTable(physical);
    document.setRowMetaXml(
        "<row-meta><value-meta><type>Integer</type><name>error_count</name></value-meta></row-meta>");

    RecordDefinition definition = document.toRecordDefinition();
    assertNotNull(definition.getPhysicalTable());
    assertNotNull(definition.getPhysicalTable().getFields());
    assertEquals(1, definition.getPhysicalTable().getFields().size());
    assertEquals("error_count", definition.getPhysicalTable().getFields().getFirst().getName());
    assertEquals(
        IValueMeta.TYPE_INTEGER, definition.getPhysicalTable().getFields().getFirst().getHopType());
    // Transient row meta is derived from structured fields.
    assertNotNull(definition.getFields());
    assertEquals(1, definition.getFields().size());
  }

  @Test
  void writeOmitsRowMetaXmlAndKeepsPhysicalTableFields() throws Exception {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/test/models", "hub_customer"));
    definition.setType(RecordDefinitionType.DV_HUB);
    definition.setDescription("Customer hub");
    PhysicalTableRef physical = new PhysicalTableRef();
    physical.setDatabaseMetaName("Vault");
    physical.setTableName("hub_customer");
    definition.setPhysicalTable(physical);

    RowMeta layout = new RowMeta();
    layout.addValueMeta(new ValueMetaInteger("customer_id"));
    DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, layout, null);

    RecordDefinitionDocument document = RecordDefinitionDocument.from(definition);
    assertNull(document.getRowMetaXml());
    assertNotNull(document.getPhysicalTable());
    assertNotNull(document.getPhysicalTable().getFields());
    assertEquals(1, document.getPhysicalTable().getFields().size());
    assertEquals("customer_id", document.getPhysicalTable().getFields().getFirst().getName());
  }

  @Test
  void migratesLegacyRowMetaXmlIntoDvSourceFields() throws Exception {
    RecordDefinitionDocument document = new RecordDefinitionDocument();
    document.setNamespace("hop/test/sources");
    document.setName("CRM-customer");
    document.setType("DV_SOURCE");
    document.setDvSource(new org.hopper.edw.catalog.model.DvSourceRecord());
    document.setRowMetaXml(
        "<row-meta><value-meta><type>Integer</type><name>customer_id</name></value-meta>"
            + "<value-meta><type>String</type><name>name</name></value-meta></row-meta>");

    RecordDefinition definition = document.toRecordDefinition();
    assertNotNull(definition.getDvSource());
    assertEquals(2, definition.getDvSource().getFields().size());
    assertEquals("customer_id", definition.getDvSource().getFields().get(0).getName());
    assertEquals(IValueMeta.TYPE_INTEGER, definition.getDvSource().getFields().get(0).getHopType());
    assertEquals(IValueMeta.TYPE_STRING, definition.getDvSource().getFields().get(1).getHopType());

    RecordDefinitionDocument rewritten = RecordDefinitionDocument.from(definition);
    assertNull(rewritten.getRowMetaXml());
    assertTrue(rewritten.getDvSource().getFields().size() >= 2);
  }
}
