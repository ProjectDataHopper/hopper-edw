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
package org.apache.hop.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalIcebergTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.junit.jupiter.api.Test;

class RecordDefinitionPhysicalRefSupportTest {

  @Test
  void supportsRefreshForIcebergDvSourceWithPhysicalPointer() throws Exception {
    RecordDefinition definition = icebergDefinition();

    assertTrue(RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition));
    assertEquals(
        DvSourceType.ICEBERG, RecordDefinitionPhysicalRefSupport.resolveSourceType(definition));
    assertEquals(
        "${ICEBERG_NAMESPACE}",
        RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition).getIcebergNamespace());
  }

  @Test
  void doesNotSupportRefreshWithoutPhysicalPointer() {
    RecordDefinition definition = icebergDefinition();
    definition.setPhysicalIcebergTable(null);

    assertFalse(RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition));
  }

  @Test
  void supportsRefreshForCompositeDvSourceWithModelQueryPointer() throws Exception {
    RecordDefinition definition = compositeDefinition();

    assertTrue(RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition));
    assertEquals(
        DvSourceType.COMPOSITE, RecordDefinitionPhysicalRefSupport.resolveSourceType(definition));
    assertEquals(
        "models/source-tables-crm.hsm",
        RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition)
            .getCompositeSourceModelFilename());
    assertEquals(
        "all-customer-info",
        RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition)
            .getCompositeSourceQueryName());
  }

  @Test
  void doesNotSupportRefreshForCompositeWithoutModelQueryPointer() {
    RecordDefinition definition = compositeDefinition();
    definition.getDvSource().setCompositeSourceModelFilename(null);
    definition.getDvSource().setCompositeSourceQueryName(null);

    assertFalse(RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition));
  }

  private static RecordDefinition compositeDefinition() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "all-customer-info"));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("COMPOSITE");
    dvSource.setCompositeSourceModelFilename("models/source-tables-crm.hsm");
    dvSource.setCompositeSourceQueryName("all-customer-info");
    definition.setDvSource(dvSource);
    return definition;
  }

  private static RecordDefinition icebergDefinition() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(
        new RecordDefinitionKey("hop/integration-tests/sources", "CRM-customer-iceberg"));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("ICEBERG");
    definition.setDvSource(dvSource);
    PhysicalIcebergTableRef physicalIcebergTable = new PhysicalIcebergTableRef();
    physicalIcebergTable.setCatalogUri("${ICEBERG_CATALOG_URI}");
    physicalIcebergTable.setNamespace("${ICEBERG_NAMESPACE}");
    physicalIcebergTable.setTableName("${ICEBERG_TABLE}");
    definition.setPhysicalIcebergTable(physicalIcebergTable);
    return definition;
  }
}
