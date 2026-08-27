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
package org.hopper.edw.datavault.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.datavault.lineageview.backend.HopExportFacet;
import org.hopper.edw.datavault.lineageview.backend.HopLocationFacet;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.junit.jupiter.api.Test;

class LineageViewNavigationSupportTest {

  @Test
  void mapsHopExportLayerToRecordOrigin() {
    LineageNode node =
        LineageNode.builder()
            .id("job:retail-job:dm/retail-f-order-lines/f_order_lines")
            .kind(LineageNodeKind.JOB)
            .hopExport(
                HopExportFacet.builder()
                    .modelLayer("DM")
                    .modelName("retail-f-order-lines")
                    .logicalName("f_order_lines")
                    .modelFilename("${PROJECT_HOME}/models/retail-f-order-lines.hdm")
                    .build())
            .build();
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    RecordOrigin origin = LineageViewNavigationSupport.toRecordOrigin(node, variables);
    assertNotNull(origin);
    assertEquals(RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL, origin.getModelType());
    assertEquals("f_order_lines", origin.getModelElementName());
    assertTrue(origin.getModelFilename().endsWith("retail-f-order-lines.hdm"));
    assertTrue(LineageViewNavigationSupport.canOpenModel(node, variables));
    assertTrue(LineageViewNavigationSupport.canOpenUpdatePipeline(node, variables));
    assertFalse(LineageViewNavigationSupport.canOpenBuildPipeline(node, variables));
  }

  @Test
  void buildPipelineOnlyForBvScd2AndPit() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    LineageNode scd2 =
        LineageNode.builder()
            .id("job:ns:bv/retail-360/sat_x")
            .hopExport(
                HopExportFacet.builder()
                    .modelLayer("BV")
                    .logicalName("customer_360")
                    .tableType("SCD2")
                    .modelFilename("${PROJECT_HOME}/models/retail-360.hbv")
                    .build())
            .build();
    assertTrue(LineageViewNavigationSupport.canOpenBuildPipeline(scd2, variables));
    assertFalse(LineageViewNavigationSupport.canOpenUpdatePipeline(scd2, variables));

    LineageNode sql =
        scd2.toBuilder()
            .hopExport(scd2.getHopExport().toBuilder().tableType("BUSINESS_TABLE").build())
            .build();
    assertFalse(LineageViewNavigationSupport.canOpenBuildPipeline(sql, variables));
  }

  @Test
  void parsesCatalogKeyOnLastSlash() {
    RecordDefinitionKey key =
        LineageViewNavigationSupport.parseCatalogKey("hop/retail-example/sources/order_line");
    assertNotNull(key);
    assertEquals("hop/retail-example/sources", key.getNamespace());
    assertEquals("order_line", key.getName());
    assertNull(LineageViewNavigationSupport.parseCatalogKey("noslash"));
    assertNull(LineageViewNavigationSupport.parseCatalogKey("/trailing"));
  }

  @Test
  void catalogConnectionPrefersLocationThenExport() {
    LineageNode source =
        LineageNode.builder()
            .id("dataset:CRM:order_line")
            .kind(LineageNodeKind.DATASET)
            .hopLocation(
                HopLocationFacet.builder()
                    .catalogKey("hop/retail/sources/order_line")
                    .catalogConnection("edw-catalog")
                    .build())
            .build();
    assertEquals("edw-catalog", LineageViewNavigationSupport.catalogConnection(source));
    assertTrue(LineageViewNavigationSupport.canOpenCatalog(source));

    LineageNode jobOnly =
        LineageNode.builder()
            .id("job:ns:x")
            .hopExport(HopExportFacet.builder().catalogConnection("from-export").build())
            .build();
    assertEquals("from-export", LineageViewNavigationSupport.catalogConnection(jobOnly));
    assertFalse(LineageViewNavigationSupport.canOpenCatalog(jobOnly));
  }

  @Test
  void findDmTableFindsInventoryProductAlias() {
    DimensionalModel model = new DimensionalModel();
    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName("d_product");
    alias.setTableName("d_product");
    model.getTables().add(alias);
    assertEquals(alias, LineageViewNavigationSupport.findDmTable(model, "d_product", "d_product"));
  }

  @Test
  void findDvTableUsesLogicalThenPhysicalName() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.setTableName("HUB_CUSTOMER");
    model.getTables().add(hub);
    assertEquals(hub, LineageViewNavigationSupport.findDvTable(model, "hub_customer", null));
    assertEquals(hub, LineageViewNavigationSupport.findDvTable(model, "missing", "HUB_CUSTOMER"));
    assertNull(LineageViewNavigationSupport.findDvTable(model, "missing", "other"));
  }

  @Test
  void blankLayerCannotOpenModel() {
    LineageNode node =
        LineageNode.builder()
            .hopExport(
                HopExportFacet.builder()
                    .modelLayer("CROSS")
                    .modelFilename("models/x.hdv")
                    .logicalName("hub_x")
                    .build())
            .build();
    assertNull(LineageViewNavigationSupport.toRecordOrigin(node, new Variables()));
    assertFalse(LineageViewNavigationSupport.canOpenModel(node, new Variables()));
  }
}
