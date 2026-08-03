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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.junit.jupiter.api.Test;

class BusinessVaultDvReferenceSupportTest {

  @Test
  void hasDvReferenceNameOnlyIgnoresPath() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvDvTableReference external = new BvDvTableReference("hub_customer", DvTableType.HUB);
    external.setReferencedModelFilename("${PROJECT_HOME}/models/other.hdv");
    model.getDvReferences().add(external);

    // Path-agnostic: any alias with this table name counts (SQL sync dedupe).
    assertTrue(BusinessVaultDvReferenceSupport.hasDvReference(model, "hub_customer"));
  }

  @Test
  void hasDvReferenceMatchesModelPath() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvDvTableReference linked = new BvDvTableReference("hub_customer", DvTableType.HUB);
    BvDvTableReference external = new BvDvTableReference("hub_customer", DvTableType.HUB);
    external.setReferencedModelFilename("${PROJECT_HOME}/models/other.hdv");
    model.getDvReferences().add(linked);
    model.getDvReferences().add(external);

    assertTrue(BusinessVaultDvReferenceSupport.hasDvReference(model, "hub_customer", null));
    assertTrue(
        BusinessVaultDvReferenceSupport.hasDvReference(
            model, "hub_customer", "${PROJECT_HOME}/models/other.hdv"));
    assertFalse(
        BusinessVaultDvReferenceSupport.hasDvReference(
            model, "hub_customer", "${PROJECT_HOME}/models/third.hdv"));
  }

  @Test
  void ensureDvCanvasAliasesDoesNotDuplicate() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvDvTableReference existing = new BvDvTableReference("sat_product", DvTableType.SATELLITE);
    existing.setReferencedModelFilename("${PROJECT_HOME}/models/retail-360.hdv");
    existing.setLocation(new Point(100, 100));
    model.getDvReferences().add(existing);

    BvSqlRef sqlRef = new BvSqlRef(null, "sat_product");
    sqlRef.setResolvedKind(BvSqlResolvedKind.DV_TABLE);
    sqlRef.setResolvedDvTableType(DvTableType.SATELLITE);
    sqlRef.setResolvedModelFilename("${PROJECT_HOME}/models/retail-360.hdv");

    BvSqlRefResolver.ensureDvCanvasAliases(model, List.of(sqlRef), new DataVaultModel());
    assertEquals(1, model.getDvReferences().size());
    assertEquals(100, model.getDvReferences().get(0).getLocation().x);
  }

  @Test
  void listAvailableExcludesSamePathOnly() {
    DataVaultModel dv = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.setTableType(DvTableType.HUB);
    dv.getTables().add(hub);
    DvSatellite sat = new DvSatellite();
    sat.setName("sat_customer");
    sat.setTableType(DvTableType.SATELLITE);
    dv.getTables().add(sat);

    BusinessVaultModel bv = new BusinessVaultModel();
    BvDvTableReference external = new BvDvTableReference("hub_customer", DvTableType.HUB);
    external.setReferencedModelFilename("other.hdv");
    bv.getDvReferences().add(external);

    // Linked model (null path): hub_customer still available even if external alias exists.
    List<String> hubs =
        BusinessVaultDvReferenceSupport.listAvailableDvTableNames(dv, bv, DvTableType.HUB, null);
    assertTrue(hubs.contains("hub_customer"));

    // Same external path: hub already used.
    List<String> externalHubs =
        BusinessVaultDvReferenceSupport.listAvailableDvTableNames(
            dv, bv, DvTableType.HUB, "other.hdv");
    assertFalse(externalHubs.contains("hub_customer"));
  }

  @Test
  void createReferenceStoresModelFilename() {
    DvHub hub = new DvHub();
    hub.setName("hub_product");
    hub.setTableType(DvTableType.HUB);
    BvDvTableReference ref =
        BusinessVaultDvReferenceSupport.createReference(
            hub, new Point(10, 20), "${PROJECT_HOME}/models/retail.hdv");
    assertEquals("hub_product", ref.getDvTableName());
    assertEquals("${PROJECT_HOME}/models/retail.hdv", ref.getReferencedModelFilename());
    assertEquals(10, ref.getLocation().x);
  }

  @Test
  void listDvModelSourcesIncludesLinkedWhenPresent() {
    BusinessVaultModel bv = new BusinessVaultModel();
    bv.setDataVaultModelPath("${PROJECT_HOME}/models/vault1.hdv");
    DataVaultModel linked = new DataVaultModel();
    linked.setName("vault1");
    linked.setFilename("/proj/models/vault1.hdv");

    List<BusinessVaultDvReferenceSupport.DvModelSource> sources =
        BusinessVaultDvReferenceSupport.listDvModelSources(bv, linked, null, null);
    // Linked model + browse .hdv option
    assertEquals(2, sources.size());
    assertTrue(sources.get(0).linkedModel());
    assertTrue(sources.get(0).label().contains("Linked"));
    assertTrue(sources.get(1).browseFile());
  }

  @Test
  void listDvModelSourcesWithoutLinkedStillOffersBrowse() {
    BusinessVaultModel bv = new BusinessVaultModel();
    List<BusinessVaultDvReferenceSupport.DvModelSource> sources =
        BusinessVaultDvReferenceSupport.listDvModelSources(bv, null, null, null);
    assertEquals(1, sources.size());
    assertTrue(sources.get(0).browseFile());
  }
}
