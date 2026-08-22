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
package org.hopper.edw.datavault.hopgui.coaching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceRef;
import org.hopper.edw.datavault.metadata.coaching.DvCoachingModelAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CoachingMappingApplierTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void appliesRecordSourceToSatelliteByActualTableName() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvSatellite satellite = new DvSatellite("customer_2");
    model.getTables().add(satellite);

    DvCoachingModelAdapter adapter = new DvCoachingModelAdapter(model, name -> {}, name -> {});
    CoachingSourceRef sourceRef =
        CoachingSourceRef.forRecordDefinition("local-catalog", "hop/test/sources", "CRM-customer");

    CoachingMappingApplier.apply(adapter, sourceRef, "customer_2", new Variables(), null);

    assertEquals("CRM-customer", satellite.getRecordSourceName());

    var targets = adapter.resolveTargetsForSource(sourceRef, new Variables(), null);
    assertEquals(1, targets.size());
    assertEquals("customer_2", targets.getFirst().getTableName());
    assertEquals(DvTableType.SATELLITE.name(), targets.getFirst().getTableRole());
  }

  @Test
  void ignoresSatelliteWhenTableNameDoesNotMatchCreatedName() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvSatellite satellite = new DvSatellite("customer_2");
    model.getTables().add(satellite);

    DvCoachingModelAdapter adapter = new DvCoachingModelAdapter(model, name -> {}, name -> {});
    CoachingSourceRef sourceRef =
        CoachingSourceRef.forRecordDefinition("local-catalog", "hop/test/sources", "CRM-customer");

    CoachingMappingApplier.apply(adapter, sourceRef, "customer", new Variables(), null);

    assertEquals(null, satellite.getRecordSourceName());
  }
}
