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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.junit.jupiter.api.Test;

class ValidationReportFormatterTest {

  @Test
  void formatsSummaryAndBlockingNotice() {
    ValidationReport report = new ValidationReport("retail-sources");
    report.addRecordValidation(
        new RecordDefinitionValidation(
            new RecordDefinitionKey("hop/retail-example/sources", "customer_demo"),
            "local-catalog",
            "DATABASE",
            false,
            new RecordDefinitionSchemaDiffSupport.SchemaDiff(
                List.of(
                    new RecordDefinitionSchemaDiffSupport.FieldChange(
                        RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED, "new_field", null))),
            List.of(
                SourceUsage.builder()
                    .modelType(SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT)
                    .modelName("retail-360")
                    .modelElementName("sat_customer_demo")
                    .build()),
            RemediationProposalSupport.buildIssues(
                new RecordDefinitionSchemaDiffSupport.SchemaDiff(
                    List.of(
                        new RecordDefinitionSchemaDiffSupport.FieldChange(
                            RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED,
                            "new_field",
                            null))),
                List.of(),
                null)));

    String formatted = ValidationReportFormatter.format(report);
    assertTrue(formatted.contains("retail-sources"));
    assertTrue(formatted.contains("customer_demo"));
    assertTrue(formatted.contains("ADDED"));
  }
}
