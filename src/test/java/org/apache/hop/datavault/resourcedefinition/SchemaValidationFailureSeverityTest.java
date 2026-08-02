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
 *
 */

package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class SchemaValidationFailureSeverityTest {

  @Test
  void failOnWarningsIgnoresInfoOnlyFindings() {
    ValidationReport report = reportWith(IssueSeverity.INFO);
    assertFalse(SchemaValidationFailureSeverity.FAIL_ON_WARNINGS.shouldFail(report));
    assertEquals(SimulationStatus.PASS, SchemaImpactSimulationResult.statusOf(report));
  }

  @Test
  void failOnWarningsFailsOnWarningFindings() {
    ValidationReport report = reportWith(IssueSeverity.WARNING);
    assertTrue(SchemaValidationFailureSeverity.FAIL_ON_WARNINGS.shouldFail(report));
    assertEquals(SimulationStatus.WARNING, SchemaImpactSimulationResult.statusOf(report));
  }

  @Test
  void failOnBlockingIgnoresWarningsAndInfo() {
    assertFalse(
        SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.shouldFail(
            reportWith(IssueSeverity.WARNING)));
    assertFalse(
        SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.shouldFail(
            reportWith(IssueSeverity.INFO)));
  }

  private static ValidationReport reportWith(IssueSeverity severity) {
    ValidationReport report = new ValidationReport("g");
    report.addRecordValidation(
        new ValidationReport.RecordDefinitionValidation(
            new RecordDefinitionKey("ns", "n"),
            "c",
            "DATABASE",
            false,
            new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
            List.of(),
            List.of(
                new ValidationIssue(
                    "1",
                    IssueKind.TARGET_DDL_REQUIRED,
                    severity,
                    null,
                    "finding",
                    List.of()))));
    return report;
  }
}
