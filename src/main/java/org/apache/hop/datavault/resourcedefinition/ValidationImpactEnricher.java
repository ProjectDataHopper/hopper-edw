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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.impact.ImpactGraph;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;

/**
 * Attaches downstream blast-radius labels to validation issues without coupling schema diff
 * construction to the impact graph.
 */
public final class ValidationImpactEnricher {

  private ValidationImpactEnricher() {}

  public static ValidationReport enrich(ValidationReport report, ImpactGraph graph) {
    if (report == null) {
      return null;
    }
    if (graph == null) {
      return report;
    }
    ValidationReport enriched = new ValidationReport(report.getGroupName());
    for (RecordDefinitionValidation validation : report.getRecordValidations()) {
      enriched.addRecordValidation(enrichValidation(validation, graph));
    }
    return enriched;
  }

  private static RecordDefinitionValidation enrichValidation(
      RecordDefinitionValidation validation, ImpactGraph graph) {
    if (validation == null) {
      return null;
    }
    List<ValidationIssue> allIssues = enrichIssues(validation.key(), validation.allIssues(), graph);
    List<ValidationIssue> visibleIssues =
        enrichIssues(validation.key(), validation.issues(), graph);
    return new RecordDefinitionValidation(
        validation.key(),
        validation.catalogConnection(),
        validation.sourceType(),
        validation.inSync(),
        validation.schemaDiff(),
        validation.usages(),
        allIssues,
        visibleIssues,
        validation.acknowledgedIssueCount());
  }

  private static List<ValidationIssue> enrichIssues(
      org.apache.hop.catalog.model.RecordDefinitionKey key,
      List<ValidationIssue> issues,
      ImpactGraph graph) {
    if (issues == null || issues.isEmpty()) {
      return issues == null ? List.of() : issues;
    }
    List<ValidationIssue> enriched = new ArrayList<>(issues.size());
    for (ValidationIssue issue : issues) {
      if (issue == null) {
        continue;
      }
      String impact = graph.formatBlastRadiusLabels(key, issue.fieldName());
      if (Utils.isEmpty(impact)) {
        enriched.add(issue);
      } else {
        enriched.add(issue.withDownstreamImpact(impact));
      }
    }
    return List.copyOf(enriched);
  }
}
