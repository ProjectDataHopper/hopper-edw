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
package org.apache.hop.datavault.hopgui.file.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.junit.jupiter.api.Test;

class DvLinkDialogSatelliteComboTest {

  @Test
  void resolveLinkSatelliteComboNames_filtersToThisLink() {
    DataVaultModel model = new DataVaultModel();
    model.getTables().add(hub("H_Customer"));
    model.getTables().add(link("L_Order"));
    model.getTables().add(link("L_Other"));
    model.getTables().add(linkSat("LS_Order_Det", "L_Order"));
    model.getTables().add(linkSat("LS_Other_Det", "L_Other"));
    model.getTables().add(hubSat("HS_Customer", "H_Customer"));

    DvLink order = (DvLink) model.findTable("L_Order");
    List<String> names = DvLinkDialog.resolveLinkSatelliteComboNames(model, order);
    assertEquals(List.of("LS_Order_Det"), names);
  }

  @Test
  void resolveLinkSatelliteComboNames_keepsExistingSelectionFromOtherLink() {
    DataVaultModel model = new DataVaultModel();
    model.getTables().add(link("L_Order"));
    model.getTables().add(linkSat("LS_Order_Det", "L_Order"));
    model.getTables().add(linkSat("LS_Legacy", "L_Other"));

    DvLink order = (DvLink) model.findTable("L_Order");
    order.getLinkSatelliteNames().add("LS_Legacy");

    List<String> names = DvLinkDialog.resolveLinkSatelliteComboNames(model, order);
    assertEquals(2, names.size());
    assertTrue(names.contains("LS_Order_Det"));
    assertTrue(names.contains("LS_Legacy"));
  }

  private static DvHub hub(String name) {
    DvHub hub = new DvHub();
    hub.setName(name);
    return hub;
  }

  private static DvLink link(String name) {
    DvLink link = new DvLink();
    link.setName(name);
    return link;
  }

  private static DvSatellite linkSat(String name, String linkName) {
    DvSatellite sat = new DvSatellite();
    sat.setName(name);
    sat.setLinkName(linkName);
    return sat;
  }

  private static DvSatellite hubSat(String name, String hubName) {
    DvSatellite sat = new DvSatellite();
    sat.setName(name);
    sat.setHubName(hubName);
    return sat;
  }
}
