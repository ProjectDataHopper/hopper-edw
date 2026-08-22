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
package org.hopper.edw.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.datavault.resourcedefinition.SchemaValidationReportFileWriter.ReportFormat;
import org.hopper.edw.datavault.resourcedefinition.ValidationOptions.BaselineKind;
import org.junit.jupiter.api.Test;

class ValidationOptionsTest {

  @Test
  void defaultsMapToLiveSourceWithModelAndTargetAxes() {
    ValidationOptions options = ValidationOptions.defaults();
    SchemaImpactSimulationRequest request = options.toSimulationRequest("retail-sources", true);
    assertEquals(SchemaCompareMode.LIVE_SOURCE, request.compareMode());
    assertTrue(request.checkTargetModels());
    assertTrue(request.checkTargetDatabases());
    assertTrue(request.includeImpact());
    assertFalse(request.checkCatalogVsVersion());
    assertFalse(request.expectAutomaticTargetTableCreation());
    assertEquals(ParallelValidationSupport.DEFAULT_PARALLELISM, request.validationParallelism());
  }

  @Test
  void versionBaselineWithLiveSourcesSetsCatalogVersionTag() {
    ValidationOptions options =
        new ValidationOptions(
            BaselineKind.CATALOG_VERSION,
            "v1.0.0",
            null,
            true,
            false,
            true,
            false,
            false,
            true,
            false,
            null,
            null,
            ReportFormat.BOTH,
            4);
    SchemaImpactSimulationRequest request = options.toSimulationRequest("g", true);
    assertEquals(SchemaCompareMode.LIVE_SOURCE, request.compareMode());
    assertEquals("v1.0.0", request.catalogVersionTag());
    assertEquals("v1.0.0", request.baselineVersionTag());
    assertEquals(4, request.validationParallelism());
  }

  @Test
  void catalogVsVersionOnlyMapsToWorkingVsVersion() {
    ValidationOptions options =
        new ValidationOptions(
            BaselineKind.CATALOG_VERSION,
            "v1.0.0",
            null,
            false,
            true,
            false,
            false,
            false,
            true,
            false,
            null,
            null,
            ReportFormat.BOTH,
            8);
    SchemaImpactSimulationRequest request = options.toSimulationRequest("g", true);
    assertEquals(SchemaCompareMode.WORKING_VS_VERSION, request.compareMode());
    assertEquals("v1.0.0", request.baselineVersionTag());
  }

  @Test
  void describeBaselineMentionsVersionImmutability() {
    ValidationOptions options =
        new ValidationOptions(
            BaselineKind.CATALOG_VERSION,
            "v2",
            null,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            ReportFormat.BOTH,
            8);
    assertTrue(options.describeBaseline().contains("v2"));
    assertTrue(options.describeBaseline().toLowerCase().contains("immutable"));
  }

  @Test
  void expectAutomaticTargetTableCreationPassedToRequest() {
    ValidationOptions options =
        new ValidationOptions(
            BaselineKind.WORKING_CATALOG,
            null,
            null,
            true,
            false,
            false,
            true,
            true,
            true,
            false,
            null,
            null,
            ReportFormat.BOTH,
            16);
    SchemaImpactSimulationRequest request = options.toSimulationRequest("g", true);
    assertTrue(request.checkTargetDatabases());
    assertTrue(request.expectAutomaticTargetTableCreation());
    assertEquals(16, request.validationParallelism());
  }
}
