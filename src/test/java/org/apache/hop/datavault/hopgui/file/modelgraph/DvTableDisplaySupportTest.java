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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvTableDisplaySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void satelliteHashKeyResolvesFromParentHub() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    hub.setHashKeyFieldName("customer_hk");
    model.getTables().add(hub);

    DvSatellite satellite = new DvSatellite("sat_customer");
    satellite.setHubName("hub_customer");
    model.getTables().add(satellite);

    assertEquals(
        "customer_hk",
        DvTableDisplaySupport.getHashKeyFieldNameForDisplay(satellite, model, new Variables()));
  }

  @Test
  void hubHashKeyFallsBackToBusinessKeySuffix() {
    DvHub hub = new DvHub("hub_order");
    BusinessKey orderId = new BusinessKey("order_id");
    orderId.setDataType("String");
    orderId.setLength("50");
    hub.getBusinessKeys().add(orderId);

    assertEquals(
        "order_id_HK",
        DvTableDisplaySupport.getHashKeyFieldNameForDisplay(
            hub, new DataVaultModel(), new Variables()));
  }

  @Test
  void imagePathMapsTableTypes() {
    assertEquals("datavault-hub.svg", DvTableDisplaySupport.getImagePath(DvTableType.HUB));
    assertEquals(
        "datavault-satellite.svg", DvTableDisplaySupport.getImagePath(DvTableType.SATELLITE));
    assertTrue(DvTableDisplaySupport.getImagePath(null).endsWith(".svg"));
  }
}
