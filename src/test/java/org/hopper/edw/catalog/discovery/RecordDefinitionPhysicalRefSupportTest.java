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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.PhysicalIcebergTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.datavault.metadata.DvSourceType;
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

  @Test
  void supportsRefreshForPipelineDvSourceWithSourceModelPointer() throws Exception {
    RecordDefinition definition = pipelineDefinition();

    assertTrue(RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition));
    assertEquals(
        DvSourceType.PIPELINE, RecordDefinitionPhysicalRefSupport.resolveSourceType(definition));
    assertEquals(
        "models/source-tables-crm.hsm",
        RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition)
            .getPipelineSourceModelFilename());
    assertEquals(
        "asn-package-lines",
        RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition).getPipelineSourceName());
    assertEquals(
        "${PROJECT_HOME}/pipelines/parse-asn-xml.hpl",
        RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition).getPipelineFilename());
  }

  @Test
  void doesNotSupportRefreshForPipelineWithoutPointers() {
    RecordDefinition definition = pipelineDefinition();
    definition.getDvSource().setPipelineSourceModelFilename(null);
    definition.getDvSource().setPipelineSourceName(null);
    definition.getDvSource().setPipelineFilename(null);
    definition.getDvSource().setPipelineTransformName(null);

    assertFalse(RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition));
  }

  private static RecordDefinition pipelineDefinition() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "asn-package-lines"));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("PIPELINE");
    dvSource.setPipelineSourceModelFilename("models/source-tables-crm.hsm");
    dvSource.setPipelineSourceName("asn-package-lines");
    dvSource.setPipelineFilename("${PROJECT_HOME}/pipelines/parse-asn-xml.hpl");
    dvSource.setPipelineTransformName("ASN lines");
    definition.setDvSource(dvSource);
    return definition;
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
