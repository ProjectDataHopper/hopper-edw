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

import java.util.List;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport.ChangeKind;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport.SchemaDiff;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestStatus;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.junit.jupiter.api.Test;

class HarvestBackedValidationSupportTest {

  @Test
  void toSchemaDiffMapsChangeKinds() {
    SchemaDiff diff =
        HarvestBackedValidationSupport.toSchemaDiff(
            List.of(
                HarvestChange.builder()
                    .changeKind("REMOVED")
                    .fieldName("old_col")
                    .severity("BLOCKING")
                    .build(),
                HarvestChange.builder()
                    .changeKind("CHANGED")
                    .fieldName("address")
                    .actualDetail("length 50 -> 75")
                    .severity("WARNING")
                    .build(),
                HarvestChange.builder()
                    .changeKind("PRIMARY_KEY_CHANGED")
                    .actualDetail("pk composition")
                    .severity("BLOCKING")
                    .build()));
    assertEquals(3, diff.changes().size());
    assertEquals(ChangeKind.REMOVED, diff.changes().get(0).kind());
    assertEquals(ChangeKind.CHANGED, diff.changes().get(1).kind());
    assertEquals(ChangeKind.PRIMARY_KEY_CHANGED, diff.changes().get(2).kind());
  }

  @Test
  void severityMapping() {
    assertEquals(
        IssueSeverity.BLOCKING,
        HarvestBackedValidationSupport.severityForChangeKind(ChangeKind.REMOVED));
    assertEquals(
        IssueSeverity.BLOCKING,
        HarvestBackedValidationSupport.severityForChangeKind(ChangeKind.PRIMARY_KEY_CHANGED));
    assertEquals(
        IssueSeverity.WARNING,
        HarvestBackedValidationSupport.severityForChangeKind(ChangeKind.ADDED));
    assertEquals(
        IssueSeverity.WARNING,
        HarvestBackedValidationSupport.severityForChangeKind(ChangeKind.CHANGED));
  }

  @Test
  void toValidationReportBuildsIssuesFromHarvest() {
    HarvestSubjectResult subject =
        HarvestSubjectResult.builder()
            .subjectKey("hop/retail-example/sources/E2E-customer")
            .sourceType("DATABASE")
            .discoveryStatus(DiscoveryStatus.OK)
            .inSync(false)
            .changes(
                List.of(
                    HarvestChange.builder()
                        .changeKind("REMOVED")
                        .fieldName("legacy_id")
                        .severity("BLOCKING")
                        .build(),
                    HarvestChange.builder()
                        .changeKind("ADDED")
                        .fieldName("new_col")
                        .severity("WARNING")
                        .build()))
            .build();

    HarvestResult harvest =
        HarvestResult.builder()
            .harvestRunId("run-abc")
            .resourceGroupName("retail-sources")
            .status(HarvestStatus.SUCCESS)
            .subjects(List.of(subject))
            .build();

    ValidationReport report =
        HarvestBackedValidationSupport.toValidationReport(harvest, null, new Variables());

    assertEquals(1, report.getRecordValidations().size());
    RecordDefinitionValidation validation = report.getRecordValidations().get(0);
    assertFalse(validation.inSync());
    assertEquals(2, validation.issues().size());
    assertTrue(validation.issues().stream().anyMatch(i -> i.kind() == IssueKind.FIELD_REMOVED));
    assertTrue(validation.issues().stream().anyMatch(i -> i.kind() == IssueKind.FIELD_ADDED));
    assertTrue(
        validation.issues().stream()
            .anyMatch(i -> i.message() != null && i.message().contains("run-abc")));
  }

  @Test
  void unavailableSubjectIsBlocking() {
    HarvestSubjectResult subject =
        HarvestSubjectResult.builder()
            .subjectKey("hop/ns/missing-table")
            .discoveryStatus(DiscoveryStatus.UNAVAILABLE)
            .inSync(false)
            .message("table not found")
            .build();
    HarvestResult harvest =
        HarvestResult.builder().harvestRunId("run-1").subjects(List.of(subject)).build();

    ValidationReport report =
        HarvestBackedValidationSupport.toValidationReport(harvest, null, new Variables());
    assertEquals(1, report.getRecordValidations().get(0).issues().size());
    assertEquals(
        IssueKind.SOURCE_UNAVAILABLE, report.getRecordValidations().get(0).issues().get(0).kind());
    assertEquals(
        IssueSeverity.BLOCKING, report.getRecordValidations().get(0).issues().get(0).severity());
  }

  @Test
  void parseChangeKindAliases() {
    assertEquals(ChangeKind.ADDED, HarvestBackedValidationSupport.parseChangeKind("FIELD_ADDED"));
    assertEquals(
        ChangeKind.PRIMARY_KEY_CHANGED,
        HarvestBackedValidationSupport.parseChangeKind("PK_CHANGED"));
  }
}
