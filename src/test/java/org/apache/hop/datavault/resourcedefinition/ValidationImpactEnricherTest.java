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
package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.datavault.impact.ImpactEdge;
import org.apache.hop.datavault.impact.ImpactEdgeType;
import org.apache.hop.datavault.impact.ImpactGraph;
import org.apache.hop.datavault.impact.ImpactNode;
import org.apache.hop.datavault.impact.ImpactNodeKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class ValidationImpactEnricherTest {

  @Test
  void enrich_attachesDownstreamImpactToFieldIssues() {
    RecordDefinitionKey key = new RecordDefinitionKey("hop/demo/sources", "customers");
    ImpactGraph graph = tinyGraph(key);

    ValidationIssue issue =
        new ValidationIssue(
            "id1",
            IssueKind.FIELD_TYPE_CHANGED,
            IssueSeverity.BLOCKING,
            "email",
            "type changed",
            List.of());
    ValidationReport report = new ValidationReport("g1");
    report.addRecordValidation(
        new RecordDefinitionValidation(
            key,
            "local",
            "CSV",
            false,
            new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
            List.of(),
            List.of(issue)));

    ValidationReport enriched = ValidationImpactEnricher.enrich(report, graph);
    ValidationIssue out = enriched.getRecordValidations().getFirst().issues().getFirst();
    assertTrue(out.downstreamImpact().contains("sat_customer"), out.downstreamImpact());
  }

  @Test
  void enrich_nullGraph_returnsOriginal() {
    ValidationReport report = new ValidationReport("g1");
    assertEquals(report, ValidationImpactEnricher.enrich(report, null));
  }

  @Test
  void enrich_nullReport_returnsNull() {
    assertNull(ValidationImpactEnricher.enrich(null, ImpactGraph.empty()));
  }

  private static ImpactGraph tinyGraph(RecordDefinitionKey key) {
    Map<String, ImpactNode> nodes = new LinkedHashMap<>();
    Map<String, List<ImpactEdge>> outgoing = new LinkedHashMap<>();
    ImpactNode sourceField =
        new ImpactNode(
            ImpactNodeKind.SOURCE_FIELD,
            null,
            null,
            null,
            null,
            "email",
            key.getNamespace(),
            key.getName());
    ImpactNode sat =
        new ImpactNode(
            ImpactNodeKind.DV_TABLE,
            SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
            "m",
            "m.hdv",
            "sat_customer",
            null,
            null,
            null);
    nodes.put(sourceField.id(), sourceField);
    nodes.put(sat.id(), sat);
    outgoing
        .computeIfAbsent(sourceField.id(), ignored -> new ArrayList<>())
        .add(new ImpactEdge(ImpactEdgeType.SOURCE_TO_DV, sourceField.id(), sat.id()));
    return new ImpactGraph(nodes, outgoing);
  }
}
