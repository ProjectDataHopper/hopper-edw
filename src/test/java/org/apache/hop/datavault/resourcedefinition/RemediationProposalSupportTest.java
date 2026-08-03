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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class RemediationProposalSupportTest {

  @Test
  void mappedLengthGrowthOffersExpandModelsFromCatalogNeverCatalogRewrite() {
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(
            List.of(
                new RecordDefinitionSchemaDiffSupport.FieldChange(
                    RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED,
                    "last_name",
                    "expected length 50 → actual length 75")));

    List<SourceUsage> usages =
        List.of(
            SourceUsage.builder()
                .modelType(SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT)
                .modelName("retail-360")
                .modelElementName("sat_customer_demo")
                .mappedField("last_name")
                .build());

    List<ValidationIssue> issues = RemediationProposalSupport.buildIssues(diff, usages, null);
    assertEquals(1, issues.size());
    ValidationIssue issue = issues.getFirst();
    assertEquals(IssueKind.FIELD_TYPE_CHANGED, issue.kind());
    assertEquals(IssueSeverity.BLOCKING, issue.severity());
    assertEquals(ProposalType.ALIGN_MODELS_TO_BASELINE, issue.proposals().getFirst().type());
    assertTrue(
        issue.proposals().stream()
            .noneMatch(p -> p.type() == ProposalType.UPDATE_TARGET_COLUMN_LENGTH),
        "must not offer catalog-rewriting accept-live");
    assertTrue(
        issue.proposals().stream().noneMatch(p -> p.type() == ProposalType.IGNORE_SOURCE_DRIFT),
        "must not offer catalog rewrite from version");
    assertTrue(
        issue.proposals().stream()
            .noneMatch(p -> p.type() == ProposalType.REFRESH_CATALOG_CONTRACT),
        "must not offer catalog refresh");
    assertTrue(
        issue.proposals().getFirst().summary().toLowerCase().contains("catalog"),
        issue.proposals().getFirst().summary());
    assertTrue(
        issue.proposals().getFirst().details().toLowerCase().contains("catalog is not changed")
            || issue.proposals().getFirst().details().toLowerCase().contains("not changed"),
        issue.proposals().getFirst().details());
  }

  @Test
  void whenActualShorterStillOffersExpandModelsFromCatalogNeverShrinkCatalog() {
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(
            List.of(
                new RecordDefinitionSchemaDiffSupport.FieldChange(
                    RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED,
                    "address_line1",
                    "expected length 75 → actual length 50")));

    List<SourceUsage> usages =
        List.of(
            SourceUsage.builder()
                .modelType(SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT)
                .modelName("retail-360")
                .modelElementName("sat_customer_address")
                .mappedField("address_line1")
                .build());

    List<ValidationIssue> issues = RemediationProposalSupport.buildIssues(diff, usages, null);
    assertEquals(1, issues.size());
    assertEquals(
        ProposalType.ALIGN_MODELS_TO_BASELINE, issues.getFirst().proposals().getFirst().type());
    assertTrue(
        issues.getFirst().proposals().stream()
            .noneMatch(p -> p.type() == ProposalType.UPDATE_TARGET_COLUMN_LENGTH));
    assertTrue(
        issues.getFirst().proposals().stream()
            .noneMatch(p -> p.type() == ProposalType.REFRESH_CATALOG_CONTRACT));
  }

  @Test
  void problemBProducesSatelliteExtensionProposalsForAddedFields() {
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(
            List.of(
                new RecordDefinitionSchemaDiffSupport.FieldChange(
                    RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED, "loyalty_tier", null),
                new RecordDefinitionSchemaDiffSupport.FieldChange(
                    RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED, "loyalty_points", null)));

    List<SourceUsage> usages =
        List.of(
            SourceUsage.builder()
                .modelType(SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT)
                .modelName("retail-360")
                .modelElementName("sat_customer_demo")
                .build());

    List<ValidationIssue> issues = RemediationProposalSupport.buildIssues(diff, usages, null);
    assertEquals(2, issues.size());
    assertTrue(
        issues.stream()
            .flatMap(issue -> issue.proposals().stream())
            .anyMatch(proposal -> proposal.type() == ProposalType.ADD_NEW_SATELLITE));
    assertTrue(
        issues.stream()
            .flatMap(issue -> issue.proposals().stream())
            .anyMatch(proposal -> proposal.type() == ProposalType.EXTEND_EXISTING_SATELLITE));
  }

  @Test
  void isActualLengthLongerDetectsGrowthDirection() {
    assertTrue(RemediationProposalSupport.isActualLengthLonger("length 50 -> 75"));
    assertTrue(
        RemediationProposalSupport.isActualLengthLonger("expected length 50 → actual length 75"));
    assertTrue(!RemediationProposalSupport.isActualLengthLonger("length 75 -> 50"));
    assertTrue(
        !RemediationProposalSupport.isActualLengthLonger("expected length 75 → actual length 50"));
  }
}
