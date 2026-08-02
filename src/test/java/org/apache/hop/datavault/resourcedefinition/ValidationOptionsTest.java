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

package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFileWriter.ReportFormat;
import org.apache.hop.datavault.resourcedefinition.ValidationOptions.BaselineKind;
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
            ReportFormat.BOTH);
    SchemaImpactSimulationRequest request = options.toSimulationRequest("g", true);
    assertEquals(SchemaCompareMode.LIVE_SOURCE, request.compareMode());
    assertEquals("v1.0.0", request.catalogVersionTag());
    assertEquals("v1.0.0", request.baselineVersionTag());
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
            ReportFormat.BOTH);
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
            ReportFormat.BOTH);
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
            ReportFormat.BOTH);
    SchemaImpactSimulationRequest request = options.toSimulationRequest("g", true);
    assertTrue(request.checkTargetDatabases());
    assertTrue(request.expectAutomaticTargetTableCreation());
  }
}
