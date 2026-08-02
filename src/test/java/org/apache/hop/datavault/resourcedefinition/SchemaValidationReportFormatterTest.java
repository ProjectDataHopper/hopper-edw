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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.impact.ImpactGraph;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SchemaValidationReportFormatterTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void formatMarkdown_includesStatusTableAndImpact() {
    SchemaImpactSimulationResult result = sampleFieldResult();
    String md = SchemaValidationReportFormatter.formatMarkdown(result);

    assertTrue(md.contains("# Data Vault Resource Definition Validation Report"), md);
    assertTrue(md.contains("ERP_FINANCE"), md);
    assertTrue(md.contains("What we compared"), md);
    assertTrue(md.contains("Expected (baseline)"), md);
    assertTrue(md.contains("Actual:"), md);
    assertTrue(md.contains("CRITICAL BLOCKED") || md.contains("❌"), md);
    assertTrue(md.contains("customer_id"), md);
    assertTrue(md.contains("hub_customer"), md);
    assertTrue(md.contains("Downstream impact"), md);
    assertTrue(md.contains("Required action"), md);
  }

  @Test
  void formatHtml_includesTableAndStatusClass() {
    String html = SchemaValidationReportFormatter.formatHtml(sampleFieldResult());
    assertTrue(html.contains("<!DOCTYPE html>"), html);
    assertTrue(html.contains("customer_id"), html);
    assertTrue(html.contains("critical") || html.contains("CRITICAL"), html);
    assertTrue(html.contains("What we compared"), html);
    assertTrue(html.contains("Expected"), html);
  }

  @Test
  void formatHtml_baselineGapIsCardNotActualTypeColumn() {
    String html = SchemaValidationReportFormatter.formatHtml(sampleBaselineMissingResult());
    assertTrue(html.contains("What we compared"), html);
    assertTrue(html.contains("v1.0.0"), html);
    assertTrue(html.contains("What is wrong"), html);
    assertTrue(html.contains("What to do"), html);
    assertTrue(
        html.contains("all-customer-info") || html.contains("missing from baseline"), html);
    // Must not stuff Axis jargon into a type table cell.
    assertFalse(html.contains("<th>Actual Type</th>"), html);
    assertFalse(html.contains("Axis:"), html);
    assertTrue(html.contains("finding"), html);
    assertTrue(html.toLowerCase().contains("tag a new catalog version"), html);
  }

  @Test
  void formatMarkdown_baselineGapUsesProseNotTypeTable() {
    String md = SchemaValidationReportFormatter.formatMarkdown(sampleBaselineMissingResult());
    assertTrue(md.contains("What is wrong"), md);
    assertTrue(md.contains("What to do"), md);
    assertFalse(md.contains("| Catalog Type | Actual Type |"), md);
    assertFalse(md.contains("Axis:"), md);
    assertTrue(md.contains("Working catalog"), md);
  }

  @Test
  void formatHtml_fieldDiffKeepsExpectedActualColumns() {
    String html = SchemaValidationReportFormatter.formatHtml(sampleFieldResult());
    assertTrue(html.contains("<th>Expected</th>"), html);
    assertTrue(html.contains("<th>Actual</th>"), html);
    assertTrue(html.contains("VARCHAR") || html.contains("INT"), html);
  }

  @Test
  void formatLog_includesValidationFormatterOutput() {
    String log = SchemaValidationReportFormatter.formatLog(sampleFieldResult());
    assertTrue(log.contains("Schema validation status"), log);
    assertTrue(log.contains("ERP_FINANCE"), log);
    assertTrue(log.contains("What we compared"), log);
  }

  @Test
  void requiredActionMentionsBaselineWhenThatIsTheProblem() {
    List<SchemaValidationReportFormatter.IssueRow> rows =
        SchemaValidationReportFormatter.collectRows(
            sampleBaselineMissingResult().validationReport());
    String action =
        SchemaValidationReportFormatter.buildRequiredAction(
            SimulationStatus.CRITICAL_BLOCKED, rows);
    assertTrue(action.toLowerCase().contains("catalog version"), action);
    assertFalse(action.toLowerCase().contains("ddl migration"), action);
  }

  private static SchemaImpactSimulationResult sampleFieldResult() {
    ValidationIssue issue =
        new ValidationIssue(
                "i1",
                IssueKind.FIELD_TYPE_CHANGED,
                IssueSeverity.BLOCKING,
                "customer_id",
                "Type changed",
                List.of())
            .withDownstreamImpact("hub_customer; sat_orders");
    ValidationReport report = new ValidationReport("ERP_FINANCE");
    report.addRecordValidation(
        new RecordDefinitionValidation(
            new RecordDefinitionKey("hop/demo/sources", "src_orders"),
            "local-catalog",
            "DATABASE",
            false,
            new RecordDefinitionSchemaDiffSupport.SchemaDiff(
                List.of(
                    new RecordDefinitionSchemaDiffSupport.FieldChange(
                        RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED,
                        "customer_id",
                        "VARCHAR(50) -> INT"))),
            List.of(),
            List.of(issue)));

    return new SchemaImpactSimulationResult(
        report,
        ImpactGraph.empty(),
        "v2.4.0",
        "v2.4.0",
        SchemaCompareMode.LIVE_SOURCE,
        Instant.parse("2026-07-01T12:00:00Z"),
        SimulationStatus.CRITICAL_BLOCKED);
  }

  private static SchemaImpactSimulationResult sampleBaselineMissingResult() {
    String message =
        ValidationFindingFormatter.baselineContractMissing(
            "hop/retail-example/sources/all-customer-info", "v1.0.0", true);
    ValidationIssue issue =
        new ValidationIssue(
                "b1",
                IssueKind.BASELINE_CONTRACT_MISSING,
                IssueSeverity.BLOCKING,
                null,
                message,
                List.of())
            .withDownstreamImpact("hub_customer; sat_customer; d_customer");
    ValidationReport report = new ValidationReport("retail-sources");
    report.addRecordValidation(
        new RecordDefinitionValidation(
            new RecordDefinitionKey("hop/retail-example/sources", "all-customer-info"),
            "local-catalog",
            "DATABASE",
            false,
            new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
            List.of(),
            List.of(issue)));

    return new SchemaImpactSimulationResult(
        report,
        ImpactGraph.empty(),
        null,
        "v1.0.0",
        SchemaCompareMode.WORKING_VS_VERSION,
        Instant.parse("2026-08-02T14:00:00Z"),
        SimulationStatus.CRITICAL_BLOCKED);
  }
}
