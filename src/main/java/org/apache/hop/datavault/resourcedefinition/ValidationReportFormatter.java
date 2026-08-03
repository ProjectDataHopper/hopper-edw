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

import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;

/** Formats validation reports for logs and export. */
public final class ValidationReportFormatter {

  private static final Class<?> PKG = ValidationReportFormatter.class;

  private ValidationReportFormatter() {}

  public static String format(ValidationReport report) {
    if (report == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder
        .append(
            BaseMessages.getString(
                PKG,
                "ValidationReportFormatter.Summary",
                report.getGroupName(),
                report.getTotalDefinitions(),
                report.getInSyncCount(),
                report.getIssueCount()))
        .append(Const.CR);

    for (RecordDefinitionValidation validation : report.getRecordValidations()) {
      builder.append(Const.CR);
      String keyLabel =
          validation.key() != null
              ? validation.key().getNamespace() + "/" + validation.key().getName()
              : "?";
      builder
          .append(
              BaseMessages.getString(
                  PKG,
                  "ValidationReportFormatter.RecordHeader",
                  keyLabel,
                  validation.inSync()
                      ? BaseMessages.getString(PKG, "ValidationReportFormatter.InSync")
                      : BaseMessages.getString(PKG, "ValidationReportFormatter.OutOfSync")))
          .append(Const.CR);

      if (validation.schemaDiff() != null && validation.schemaDiff().hasChanges()) {
        builder
            .append(RecordDefinitionSchemaDiffSupport.formatDiff(validation.schemaDiff()))
            .append(Const.CR);
      }

      for (SourceUsage usage : validation.usages()) {
        builder
            .append(
                BaseMessages.getString(
                    PKG,
                    "ValidationReportFormatter.Usage",
                    usage.modelType(),
                    usage.modelName(),
                    usage.modelElementName()))
            .append(Const.CR);
      }

      for (ValidationIssue issue : validation.issues()) {
        String shortFinding = ValidationFindingFormatter.shortTitle(issue.message());
        builder
            .append("  ")
            .append(issue.severity() != null ? issue.severity().name() : "?")
            .append(" / ")
            .append(issue.kind() != null ? issue.kind().name() : "?");
        if (!Utils.isEmpty(issue.fieldName())) {
          builder.append("  Field: ").append(issue.fieldName());
        }
        builder.append(Const.CR);
        if (!Utils.isEmpty(shortFinding)) {
          builder.append("    Finding: ").append(shortFinding).append(Const.CR);
        }
        // Full structured body (Axis / Compared / Found / Why) when multi-line.
        if (!Utils.isEmpty(issue.message()) && issue.message().contains("\n")) {
          for (String line : issue.message().split("\\R")) {
            builder.append("    ").append(line).append(Const.CR);
          }
        } else if (!Utils.isEmpty(issue.message())
            && (Utils.isEmpty(shortFinding) || !shortFinding.equals(issue.message().trim()))) {
          builder.append("    ").append(issue.message()).append(Const.CR);
        }
        if (!Utils.isEmpty(issue.downstreamImpact())) {
          builder
              .append("    Downstream impact: ")
              .append(issue.downstreamImpact())
              .append(Const.CR);
        }
        for (RemediationProposal proposal : issue.proposals()) {
          builder
              .append(
                  BaseMessages.getString(
                      PKG,
                      "ValidationReportFormatter.Proposal",
                      proposal.type(),
                      proposal.summary()))
              .append(Const.CR);
          if (!Utils.isEmpty(proposal.details())) {
            builder.append("    ").append(proposal.details()).append(Const.CR);
          }
        }
      }
    }

    if (report.hasBlockingIssues()) {
      builder
          .append(Const.CR)
          .append(BaseMessages.getString(PKG, "ValidationReportFormatter.BlockingIssues"))
          .append(Const.CR);
    }
    return builder.toString();
  }
}
