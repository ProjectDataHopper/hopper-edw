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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.Test;

class BusinessVaultDerivativeSupportTest {

  @Test
  void scd2AcceptsSatelliteOnly() {
    assertTrue(
        BusinessVaultDerivativeSupport.isValidDerivativePair(
            BvTableType.SCD2, DvTableType.SATELLITE));
    assertFalse(
        BusinessVaultDerivativeSupport.isValidDerivativePair(BvTableType.SCD2, DvTableType.HUB));
  }

  @Test
  void pitAcceptsHubAndSatellite() {
    assertTrue(
        BusinessVaultDerivativeSupport.isValidDerivativePair(BvTableType.PIT, DvTableType.HUB));
    assertTrue(
        BusinessVaultDerivativeSupport.isValidDerivativePair(
            BvTableType.PIT, DvTableType.SATELLITE));
    assertFalse(
        BusinessVaultDerivativeSupport.isValidDerivativePair(BvTableType.PIT, DvTableType.LINK));
  }

  @Test
  void addDerivativeRejectsDuplicatesAndInvalidPairs() {
    BvScd2Table bvTable = new BvScd2Table();
    DvSatellite satellite = new DvSatellite("sat_customer");
    DvHub hub = new DvHub("hub_customer");

    assertTrue(BusinessVaultDerivativeSupport.addDerivative(bvTable, satellite));
    assertFalse(BusinessVaultDerivativeSupport.addDerivative(bvTable, satellite));
    assertFalse(BusinessVaultDerivativeSupport.addDerivative(bvTable, hub));
    assertEquals(1, bvTable.getDerivatives().size());
  }

  @Test
  void addDerivativeFromCanvasReferenceAllowsMultipleReferences() {
    BvPitTable pitTable = new BvPitTable();
    BvDvTableReference hubRef = new BvDvTableReference("hub_customer", DvTableType.HUB);
    BvDvTableReference satRef = new BvDvTableReference("sat_customer", DvTableType.SATELLITE);

    assertTrue(BusinessVaultDerivativeSupport.addDerivative(pitTable, hubRef));
    assertTrue(BusinessVaultDerivativeSupport.addDerivative(pitTable, satRef));
    assertFalse(BusinessVaultDerivativeSupport.addDerivative(pitTable, hubRef));
    assertEquals(2, pitTable.getDerivatives().size());
  }
}
