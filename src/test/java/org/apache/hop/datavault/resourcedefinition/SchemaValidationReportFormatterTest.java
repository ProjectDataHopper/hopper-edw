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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.datavault.impact.ImpactGraph;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class SchemaValidationReportFormatterTest {

  @Test
  void formatMarkdown_includesStatusTableAndImpact() {
    SchemaImpactSimulationResult result = sampleResult();
    String md = SchemaValidationReportFormatter.formatMarkdown(result);

    assertTrue(
        md.contains("# Data Vault Resource Definition Validation Report")
            || md.contains("# Data Vault DDL Validation Report"),
        md);
    assertTrue(md.contains("ERP_FINANCE"), md);
    assertTrue(md.contains("v2.4.0"), md);
    assertTrue(md.contains("Baseline"), md);
    assertTrue(md.contains("CRITICAL BLOCKED") || md.contains("❌"), md);
    assertTrue(md.contains("customer_id"), md);
    assertTrue(md.contains("hub_customer"), md);
    assertTrue(md.contains("Downstream Impact Summary"), md);
    assertTrue(md.contains("Required Action"), md);
  }

  @Test
  void formatHtml_includesTableAndStatusClass() {
    String html = SchemaValidationReportFormatter.formatHtml(sampleResult());
    assertTrue(html.contains("<!DOCTYPE html>"), html);
    assertTrue(html.contains("customer_id"), html);
    assertTrue(html.contains("critical") || html.contains("CRITICAL"), html);
  }

  @Test
  void formatLog_includesValidationFormatterOutput() {
    String log = SchemaValidationReportFormatter.formatLog(sampleResult());
    assertTrue(log.contains("Schema validation status"), log);
    assertTrue(log.contains("ERP_FINANCE"), log);
  }

  private static SchemaImpactSimulationResult sampleResult() {
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
        Instant.parse("2026-07-14T06:00:00Z"),
        SimulationStatus.CRITICAL_BLOCKED);
  }
}
