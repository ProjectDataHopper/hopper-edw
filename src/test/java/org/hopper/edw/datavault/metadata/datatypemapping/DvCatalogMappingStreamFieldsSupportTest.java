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
package org.hopper.edw.datavault.metadata.datatypemapping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.BusinessKeySource;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metadata.SourceField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.junit.jupiter.api.Test;

class DvCatalogMappingStreamFieldsSupportTest {

  @Test
  void satelliteExpectedFieldsIncludeParentKeyAndAttributes() {
    DataVaultModel model = new DataVaultModel();
    model.getTables().add(hub("hub_customer", "customer_id"));
    DvSatellite satellite = new DvSatellite("sat_customer");
    satellite.setHubName("hub_customer");
    satellite.setStoreRecordSource(true);
    SatelliteAttribute name = new SatelliteAttribute("name");
    satellite.getAttributes().add(name);
    model.getTables().add(satellite);
    DataVaultSource source = new DataVaultSource("CRM-customer-csv");

    List<String> expected =
        DvCatalogMappingStreamFieldsSupport.expectedStreamFieldNames(
            satellite, source, model, new Variables(), "x_record_source", null);

    assertTrue(expected.contains("customer_id"), expected::toString);
    assertTrue(expected.contains("name"), expected::toString);
    assertTrue(expected.contains("x_record_source"), expected::toString);
    assertFalse(expected.contains("load_date"), expected::toString);
  }

  @Test
  void linkExpectedFieldsExcludeSatelliteAttributes() {
    DataVaultModel model = warehouseProductModel();
    DvLink link = (DvLink) model.findTable("lnk_warehouse_product");
    DataVaultSource source = warehouseProductSource();

    List<String> expected =
        DvCatalogMappingStreamFieldsSupport.expectedStreamFieldNames(
            link, source, model, new Variables(), "x_record_source", null);

    assertTrue(expected.contains("warehouse_id"), expected::toString);
    assertTrue(expected.contains("product_id"), expected::toString);
    assertTrue(expected.contains("x_record_source"), expected::toString);
    assertFalse(expected.contains("stock_qty"), expected::toString);
    assertFalse(expected.contains("reorder_point"), expected::toString);
    assertFalse(expected.contains("load_date"), expected::toString);
  }

  @Test
  void linkMappingsDoNotLookupSatelliteAttributes() {
    List<SourceField> catalog = List.of(field("warehouse_id", 9), field("stock_qty", 9));
    List<SourceField> aligned =
        DataTypeMappingPipelineSupport.rewriteSourceIndicatorLookup(
            catalog, "record_source", "x_record_source");
    List<SourceField> onStream =
        DataTypeMappingPipelineSupport.filterToStreamFields(
            aligned, List.of("warehouse_id", "product_id", "x_record_source"));

    SelectValuesMeta select =
        DataTypeMappingPipelineSupport.buildSelectValuesMetaFromSourceFields(onStream);
    List<String> metaNames =
        select.getSelectOption().getMeta().stream().map(SelectMetadataChange::getName).toList();
    assertTrue(metaNames.contains("warehouse_id"));
    assertFalse(metaNames.contains("stock_qty"));
  }

  private static DataVaultModel warehouseProductModel() {
    DataVaultModel model = new DataVaultModel();
    model.getTables().add(hub("hub_warehouse", "warehouse_id"));
    model.getTables().add(hub("hub_product", "product_id"));

    DvLink link = new DvLink();
    link.setName("lnk_warehouse_product");
    link.getHubNames().add("hub_warehouse");
    link.getHubNames().add("hub_product");
    DvLink.DvLinkHubSource hubSource = new DvLink.DvLinkHubSource();
    hubSource.setSource("E2E-warehouse-product");
    hubSource.getHubSourceKeyFields().add(hubKey("hub_warehouse", "warehouse_id", "warehouse_id"));
    hubSource.getHubSourceKeyFields().add(hubKey("hub_product", "product_id", "product_id"));
    link.getLinkHubSources().add(hubSource);
    model.getTables().add(link);
    return model;
  }

  private static DvHub hub(String name, String keyName) {
    DvHub hub = new DvHub(name);
    BusinessKey key = new BusinessKey(keyName);
    key.setSourceFieldName(keyName);
    hub.getBusinessKeys().add(key);
    return hub;
  }

  private static DvLink.HubSourceKeyField hubKey(
      String hubName, String businessKey, String sourceField) {
    DvLink.HubSourceKeyField field = new DvLink.HubSourceKeyField();
    field.setHubName(hubName);
    BusinessKeySource mapping = new BusinessKeySource();
    mapping.setBusinessKeyField(businessKey);
    mapping.setSourceFieldName(sourceField);
    field.getSourceBusinessKeyFields().add(mapping);
    return field;
  }

  private static DataVaultSource warehouseProductSource() {
    DataVaultSource source = new DataVaultSource("E2E-warehouse-product");
    source.setSourceIndicatorField("record_source");
    return source;
  }

  private static SourceField field(String name, int length) {
    SourceField field = new SourceField(name);
    field.setHopType(IValueMeta.TYPE_INTEGER);
    field.setLength(Integer.toString(length));
    return field;
  }
}
