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

import java.util.ArrayList;
import java.util.List;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionValidationAcknowledgement;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class ValidationIssueSupportTest {

  @Test
  void buildIssueIdIncludesChangeSignatureForChangedFields() {
    String issueId =
        ValidationIssueSupport.buildIssueId(
            IssueKind.FIELD_TYPE_CHANGED,
            new RecordDefinitionSchemaDiffSupport.FieldChange(
                RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED,
                "last_name",
                "length 50 -> 75"));
    assertEquals("FIELD_TYPE_CHANGED|last_name|length 50 -> 75", issueId);
  }

  @Test
  void pruneStaleAcknowledgementsRemovesResolvedFieldChanges() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "customer"));
    RecordDefinitionValidationAcknowledgement acknowledgement =
        new RecordDefinitionValidationAcknowledgement();
    acknowledgement.setIssueId(
        ValidationIssueSupport.buildIssueId(
            IssueKind.FIELD_ADDED,
            new RecordDefinitionSchemaDiffSupport.FieldChange(
                RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED, "loyalty_tier", null)));
    definition.setValidationAcknowledgements(new ArrayList<>(List.of(acknowledgement)));

    RecordDefinitionSchemaDiffSupport.SchemaDiff emptyDiff =
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of());
    ValidationIssueSupport.pruneStaleAcknowledgements(definition, emptyDiff, null);

    assertTrue(definition.getValidationAcknowledgements().isEmpty());
  }

  @Test
  void filterAcknowledgedRemovesSuppressedIssues() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "customer"));
    String issueId =
        ValidationIssueSupport.buildIssueId(IssueKind.FIELD_ADDED, "loyalty_tier", null);
    RecordDefinitionValidationAcknowledgement acknowledgement =
        new RecordDefinitionValidationAcknowledgement();
    acknowledgement.setIssueId(issueId);
    acknowledgement.setComment("Accepted for now");
    definition.setValidationAcknowledgements(new ArrayList<>(List.of(acknowledgement)));

    List<ValidationIssue> issues =
        List.of(
            new ValidationIssue(
                issueId,
                IssueKind.FIELD_ADDED,
                ValidationReport.IssueSeverity.WARNING,
                "loyalty_tier",
                "warning",
                List.of()));

    List<ValidationIssue> visible = ValidationIssueSupport.filterAcknowledged(definition, issues);
    assertTrue(visible.isEmpty());
    assertEquals(1, ValidationIssueSupport.countAcknowledged(definition, issues));
  }

  @Test
  void keepsSourceUnavailableAcknowledgementWhileSourceStillUnavailable() {
    RecordDefinition definition = new RecordDefinition();
    String issueId = ValidationIssueSupport.buildIssueId(IssueKind.SOURCE_UNAVAILABLE, null, null);
    RecordDefinitionValidationAcknowledgement acknowledgement =
        new RecordDefinitionValidationAcknowledgement();
    acknowledgement.setIssueId(issueId);
    definition.setValidationAcknowledgements(new ArrayList<>(List.of(acknowledgement)));

    ValidationIssueSupport.pruneStaleAcknowledgements(
        definition,
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
        "still unavailable");

    assertFalse(definition.getValidationAcknowledgements().isEmpty());
  }
}
