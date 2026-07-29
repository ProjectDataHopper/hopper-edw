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

package org.apache.hop.datavault.hopgui.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.datavault.resourcedefinition.SourceUsage;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class ValidationIssueGuiActionsTest {

  @Test
  void filterUsagesReturnsAllWhenIssueHasNoField() {
    SourceUsage usage1 = usage("sat_a", "product_name");
    SourceUsage usage2 = usage("sat_b", "other");
    RecordDefinitionValidation validation = validation(List.of(usage1, usage2));
    ValidationIssue issue =
        new ValidationIssue(
            "id1", IssueKind.PRIMARY_KEY_CHANGED, IssueSeverity.BLOCKING, null, "pk", List.of());

    List<SourceUsage> filtered = ValidationIssueGuiActions.filterUsages(validation, issue);

    assertEquals(2, filtered.size());
  }

  @Test
  void filterUsagesKeepsOnlyMappedField() {
    SourceUsage usage1 = usage("sat_a", "product_name");
    SourceUsage usage2 = usage("sat_b", "other");
    RecordDefinitionValidation validation = validation(List.of(usage1, usage2));
    ValidationIssue issue =
        new ValidationIssue(
            "id1",
            IssueKind.FIELD_TYPE_CHANGED,
            IssueSeverity.WARNING,
            "product_name",
            "changed",
            List.of());

    List<SourceUsage> filtered = ValidationIssueGuiActions.filterUsages(validation, issue);

    assertEquals(1, filtered.size());
    assertEquals("sat_a", filtered.getFirst().modelElementName());
  }

  @Test
  void filterUsagesHandlesNullValidation() {
    assertTrue(ValidationIssueGuiActions.filterUsages(null, null).isEmpty());
  }

  private static SourceUsage usage(String element, String field) {
    return SourceUsage.builder()
        .modelType("DATA_VAULT_MODEL")
        .modelName("retail")
        .modelFilename("retail.hdv")
        .modelElementName(element)
        .mappedField(field)
        .build();
  }

  private static RecordDefinitionValidation validation(List<SourceUsage> usages) {
    return new RecordDefinitionValidation(
        new RecordDefinitionKey("ns", "product"),
        "local-catalog",
        "DV_SOURCE",
        false,
        null,
        usages,
        List.of());
  }
}
