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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvLinkHubSourceKeyFieldSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveBusinessKeySourcesUsesHubBusinessKeyNamesWhenMappingEmpty() {
    DvHub hub = new DvHub("hub_customer");
    hub.setBusinessKeys(List.of(businessKey("customer_id"), businessKey("region_code")));

    DvLink.HubSourceKeyField hubSourceKeyField = new DvLink.HubSourceKeyField();
    hubSourceKeyField.setHubName("hub_customer");

    List<DvLinkHubSourceKeyFieldSupport.ResolvedBusinessKeySource> resolved =
        DvLinkHubSourceKeyFieldSupport.resolveBusinessKeySources(
            hub, hubSourceKeyField, new Variables());

    assertEquals(2, resolved.size());
    assertEquals("customer_id", resolved.get(0).getSourceFieldName());
    assertEquals("region_code", resolved.get(1).getSourceFieldName());
  }

  @Test
  void resolveBusinessKeySourcesDeduplicatesRepeatedBusinessKeyNamesPerRecordSource() {
    DvHub hub = new DvHub("hub_customer");
    hub.setBusinessKeys(
        List.of(
            businessKeyForSource("customer_id", "E2E-customer-hub"),
            businessKeyForSource("customer_id", "E2E-customer-address"),
            businessKeyForSource("customer_id", "E2E-customer-contact")));

    DvLink.HubSourceKeyField hubSourceKeyField = new DvLink.HubSourceKeyField();
    hubSourceKeyField.setHubName("hub_customer");
    hubSourceKeyField
        .getSourceBusinessKeyFields()
        .add(new BusinessKeySource("customer_id", "customer_id"));

    List<String> sourceFieldNames =
        DvLinkHubSourceKeyFieldSupport.resolveSourceFieldNames(
            hub, hubSourceKeyField, new Variables());

    assertEquals(List.of("customer_id"), sourceFieldNames);
  }

  @Test
  void resolveBusinessKeySourcesUsesExplicitSourceFieldNamesWhenProvided() {
    DvHub hub = new DvHub("hub_customer");
    hub.setBusinessKeys(List.of(businessKey("customer_id")));

    DvLink.HubSourceKeyField hubSourceKeyField = new DvLink.HubSourceKeyField();
    hubSourceKeyField.setHubName("hub_customer");
    hubSourceKeyField
        .getSourceBusinessKeyFields()
        .add(new BusinessKeySource("customer_id", "cust_fk"));

    List<String> sourceFieldNames =
        DvLinkHubSourceKeyFieldSupport.resolveSourceFieldNames(
            hub, hubSourceKeyField, new Variables());

    assertEquals(List.of("cust_fk"), sourceFieldNames);
  }

  @Test
  void resolveSourceFieldNamesThrowsWhenHubMappingRowMissing() {
    DvLink.DvLinkHubSource linkHubSource = new DvLink.DvLinkHubSource();
    linkHubSource.setSourceName("order-header");

    HopException ex =
        assertThrows(
            HopException.class,
            () ->
                DvLinkHubSourceKeyFieldSupport.resolveSourceFieldNames(
                    linkHubSource, "hub_customer", new DvHub("hub_customer"), new Variables()));

    assertTrue(ex.getMessage().contains("hub_customer"));
    assertTrue(ex.getMessage().contains("order-header"));
  }

  private static BusinessKey businessKey(String name) {
    BusinessKey key = new BusinessKey(name);
    key.setDataType("String");
    key.setLength("20");
    return key;
  }

  private static BusinessKey businessKeyForSource(String name, String recordSourceName) {
    BusinessKey key = businessKey(name);
    key.setRecordSourceName(recordSourceName);
    return key;
  }
}
