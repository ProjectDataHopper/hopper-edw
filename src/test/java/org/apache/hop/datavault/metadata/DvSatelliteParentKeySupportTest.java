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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class DvSatelliteParentKeySupportTest {

  @Test
  void emptySourceFieldListUsesHubBusinessKeyNames() throws HopException {
    DvHub hub = hubWithKeys("customer_id");
    DvSatellite satellite = new DvSatellite("sat_customer");
    satellite.setHubName("hub_customer");

    List<DvSatelliteParentKeySupport.ParentKeyField> fields =
        DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, new Variables());

    assertEquals(1, fields.size());
    assertEquals("customer_id", fields.get(0).getBusinessKeyName());
    assertEquals("customer_id", fields.get(0).getSourceFieldName());
    assertFalse(fields.get(0).requiresRename());
  }

  @Test
  void orderedSourceFieldsZipToHubBusinessKeysByPosition() throws HopException {
    DvHub hub = hubWithKeys("customer_id");
    DvSatellite satellite = new DvSatellite("sat_customer");
    satellite.setParentKeySourceFields(List.of("cust_no"));

    List<DvSatelliteParentKeySupport.ParentKeyField> fields =
        DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, new Variables());

    assertEquals(1, fields.size());
    assertEquals("customer_id", fields.get(0).getBusinessKeyName());
    assertEquals("cust_no", fields.get(0).getSourceFieldName());
    assertTrue(fields.get(0).requiresRename());
  }

  @Test
  void compositeKeysPreserveHubOrder() throws HopException {
    DvHub hub = hubWithKeys("order_id", "line_no");
    DvSatellite satellite = new DvSatellite("sat_line");
    satellite.setParentKeySourceFields(List.of("src_order", "src_line"));

    List<DvSatelliteParentKeySupport.ParentKeyField> fields =
        DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, new Variables());

    assertEquals(2, fields.size());
    assertEquals("order_id", fields.get(0).getBusinessKeyName());
    assertEquals("src_order", fields.get(0).getSourceFieldName());
    assertEquals("line_no", fields.get(1).getBusinessKeyName());
    assertEquals("src_line", fields.get(1).getSourceFieldName());
  }

  @Test
  void sourceFieldCountMismatchThrows() {
    DvHub hub = hubWithKeys("order_id", "line_no");
    DvSatellite satellite = new DvSatellite("sat_line");
    satellite.setParentKeySourceFields(List.of("only_one"));

    assertThrows(
        HopException.class,
        () -> DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, new Variables()));
  }

  @Test
  void ignoresHubSourceScopedBusinessKeyRows() throws HopException {
    DvHub hub = new DvHub("hub_customer");
    BusinessKey hubFeed = new BusinessKey("customer_id");
    hubFeed.setSourceFieldName("customer_id");
    hubFeed.setRecordSourceName("E2E-customer-hub");
    BusinessKey stale = new BusinessKey("customer_id");
    stale.setSourceFieldName("stale_from_hub");
    stale.setRecordSourceName("all-customer-info");
    hub.setBusinessKeys(List.of(hubFeed, stale));

    DvSatellite satellite = new DvSatellite("sat_customer");
    satellite.setRecordSourceName("all-customer-info");

    List<DvSatelliteParentKeySupport.ParentKeyField> fields =
        DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, new Variables());

    assertEquals(1, fields.size());
    assertEquals("customer_id", fields.get(0).getSourceFieldName());
  }

  @Test
  void defaultSourceFieldsFromHubUsesBusinessKeyNames() {
    DvHub hub = hubWithKeys("customer_id");
    assertEquals(
        List.of("customer_id"),
        DvSatelliteParentKeySupport.defaultSourceFieldsFromHub(hub, new Variables()));
  }

  @Test
  void compositeHubExpandsParentKeysToSourceParts() throws HopException {
    DvHub hub = compositeBurgerHub();
    DvSatellite satellite = new DvSatellite("sat_burger");
    satellite.setHubName("hub_burger");
    satellite.setRecordSourceName("ext_burger");
    satellite.setParentKeySourceFields(List.of("num_seq_bkcc_bk", "num_seq_bk"));

    List<DvSatelliteParentKeySupport.ParentKeyField> fields =
        DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, new Variables());

    assertEquals(2, fields.size());
    assertEquals("num_seq_bkcc_bk", fields.get(0).getSourceFieldName());
    assertEquals("num_seq_bkcc_bk", fields.get(0).getBusinessKeyName());
    assertEquals("burger_bk", fields.get(0).getVaultBusinessKeyName());
    assertTrue(fields.get(0).isCompositePart());
    assertEquals("num_seq_bk", fields.get(1).getSourceFieldName());
    assertFalse(fields.get(0).requiresRename());
  }

  @Test
  void compositeHubDefaultsParentKeysFromHubSourceParts() throws HopException {
    DvHub hub = compositeBurgerHub();
    DvSatellite satellite = new DvSatellite("sat_burger");
    satellite.setHubName("hub_burger");
    satellite.setRecordSourceName("ext_burger");

    List<DvSatelliteParentKeySupport.ParentKeyField> fields =
        DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, new Variables());

    assertEquals(2, fields.size());
    assertEquals("num_seq_bkcc_bk", fields.get(0).getSourceFieldName());
    assertEquals("num_seq_bk", fields.get(1).getSourceFieldName());
  }

  @Test
  void compositeHubParentKeyCountMismatchThrows() {
    DvHub hub = compositeBurgerHub();
    DvSatellite satellite = new DvSatellite("sat_burger");
    satellite.setParentKeySourceFields(List.of("only_one"));

    assertThrows(
        HopException.class,
        () -> DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, new Variables()));
  }

  @Test
  void defaultSourceFieldsFromHubExpandsCompositeParts() {
    DvHub hub = compositeBurgerHub();
    assertEquals(
        List.of("num_seq_bkcc_bk", "num_seq_bk"),
        DvSatelliteParentKeySupport.defaultSourceFieldsFromHub(hub, "ext_burger", new Variables()));
    assertEquals(2, DvSatelliteParentKeySupport.expectedParentKeySourceFieldCount(hub));
  }

  private static DvHub compositeBurgerHub() {
    DvHub hub = new DvHub("hub_burger");
    BusinessKey bk = new BusinessKey("burger_bk");
    bk.setComposite(true);
    bk.setSourceFieldNames(List.of("num_seq_bkcc_bk", "num_seq_bk"));
    bk.setRecordSourceName("ext_burger");
    bk.setDataType("String");
    hub.setBusinessKeys(List.of(bk));
    return hub;
  }

  private static DvHub hubWithKeys(String... names) {
    DvHub hub = new DvHub("hub_test");
    List<BusinessKey> keys = new java.util.ArrayList<>();
    for (String name : names) {
      BusinessKey bk = new BusinessKey(name);
      bk.setSourceFieldName(name);
      keys.add(bk);
    }
    hub.setBusinessKeys(keys);
    return hub;
  }
}
