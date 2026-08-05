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
package org.apache.hop.datavault.workflow.actions.validatedefinitions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.resourcedefinition.SchemaCompareMode;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationFailureSeverity;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFileWriter;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class ActionValidateResourceDefinitionsTest {

  @Test
  void defaultsAndComboOptions() {
    ActionValidateResourceDefinitions action = new ActionValidateResourceDefinitions();
    assertEquals(SchemaCompareMode.LIVE_SOURCE.name(), action.getCompareMode());
    assertEquals(
        SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.name(), action.getFailureSeverity());
    assertEquals(
        SchemaValidationReportFileWriter.ReportFormat.MARKDOWN.name(), action.getReportFormat());
    assertTrue(action.isIncludeImpact());
    assertEquals("8", action.getValidationParallelism());
    assertTrue(action.isEvaluation());

    // Hop GUI requires (ILogChannel, IHopMetadataProvider) signature.
    List<String> modes = action.getCompareModeOptions(null, null);
    assertEquals(3, modes.size());
    assertTrue(modes.contains(SchemaCompareMode.WORKING_VS_VERSION.name()));
    List<String> severities = action.getFailureSeverityOptions(null, null);
    assertEquals(3, severities.size());
    assertTrue(severities.contains(SchemaValidationFailureSeverity.WARN_ONLY.name()));
    List<String> formats = action.getReportFormatOptions(null, null);
    assertEquals(3, formats.size());

    // No group selected: empty list (ComboVar still allows free typing).
    List<String> tags = action.getCatalogVersionTagOptions(null, null);
    assertTrue(tags.isEmpty());
  }

  @Test
  void clone_copiesNewFields() {
    ActionValidateResourceDefinitions original = new ActionValidateResourceDefinitions();
    original.setResourceDefinitionGroup("retail-sources");
    original.setTargetCatalogVersion("${TARGET_VERSION}");
    original.setCompareMode(SchemaCompareMode.WORKING_VS_VERSION.name());
    original.setBaselineCatalogVersion("v1.0.0");
    original.setReportOutputPath("${PROJECT_HOME}/reports");
    original.setReportFileBaseName("schema-gate");
    original.setReportFormat(SchemaValidationReportFileWriter.ReportFormat.BOTH.name());
    original.setFailureSeverity(SchemaValidationFailureSeverity.WARN_ONLY.name());
    original.setFailOnWarnings(true);
    original.setIncludeImpact(false);
    original.setValidationParallelism("${VALIDATION_PARALLELISM}");

    ActionValidateResourceDefinitions copy = (ActionValidateResourceDefinitions) original.clone();
    assertEquals("retail-sources", copy.getResourceDefinitionGroup());
    assertEquals("${TARGET_VERSION}", copy.getTargetCatalogVersion());
    assertEquals(SchemaCompareMode.WORKING_VS_VERSION.name(), copy.getCompareMode());
    assertEquals("v1.0.0", copy.getBaselineCatalogVersion());
    assertEquals("${PROJECT_HOME}/reports", copy.getReportOutputPath());
    assertEquals("schema-gate", copy.getReportFileBaseName());
    assertEquals(SchemaValidationReportFileWriter.ReportFormat.BOTH.name(), copy.getReportFormat());
    assertEquals(SchemaValidationFailureSeverity.WARN_ONLY.name(), copy.getFailureSeverity());
    assertTrue(copy.isFailOnWarnings());
    assertEquals(false, copy.isIncludeImpact());
    assertEquals("${VALIDATION_PARALLELISM}", copy.getValidationParallelism());
  }

  @Test
  void formatGateFindingLineIncludesSeverityKindAndFinding() {
    ValidationIssue issue =
        new ValidationIssue(
            "id1",
            IssueKind.BASELINE_CONTRACT_MISSING,
            IssueSeverity.BLOCKING,
            null,
            "Axis: Working catalog vs baseline\nFound: Source 'hop/x' is missing from baseline.\nWhy it matters: gap",
            List.of());
    String line =
        ActionValidateResourceDefinitions.formatGateFindingLine(
            "hop/retail-example/sources/all-customer-info", issue);
    assertTrue(line.startsWith("BLOCKING / BASELINE_CONTRACT_MISSING"));
    assertTrue(line.contains("hop/retail-example/sources/all-customer-info"));
    assertTrue(line.contains("Source 'hop/x' is missing from baseline."));
  }

  @Test
  void formatGateFindingLineIncludesFieldWhenPresent() {
    ValidationIssue issue =
        new ValidationIssue(
            "id2",
            IssueKind.FIELD_TYPE_CHANGED,
            IssueSeverity.WARNING,
            "address_line1",
            "Found: type changed VARCHAR(40) -> VARCHAR(80)",
            List.of());
    String line = ActionValidateResourceDefinitions.formatGateFindingLine("hop/ns/source", issue);
    assertTrue(line.contains("WARNING / FIELD_TYPE_CHANGED"));
    assertTrue(line.contains("field=address_line1"));
  }
}
