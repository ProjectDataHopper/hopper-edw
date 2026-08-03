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
package org.apache.hop.quality.service;

import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityReport;

/** Formats a quality report for workflow logs. */
public final class DataQualityReportFormatter {

  private DataQualityReportFormatter() {}

  public static String format(DataQualityReport report) {
    if (report == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Data quality report ")
        .append(report.getRunId())
        .append(" at ")
        .append(report.getMeasuredAt())
        .append(" lifecycle=")
        .append(report.getLifecycle())
        .append('\n');
    sb.append("Subjects: ")
        .append(
            report.getSubjectKeys().isEmpty()
                ? "(none)"
                : String.join(", ", report.getSubjectKeys()))
        .append('\n');
    sb.append("Findings: ")
        .append(report.getFindingCount())
        .append(" (blocking=")
        .append(report.countBySeverity(org.apache.hop.quality.model.QualitySeverity.BLOCKING))
        .append(", warning=")
        .append(report.countBySeverity(org.apache.hop.quality.model.QualitySeverity.WARNING))
        .append(", info=")
        .append(report.countBySeverity(org.apache.hop.quality.model.QualitySeverity.INFO))
        .append(")\n");
    if (report.hasInfraErrors()) {
      sb.append("Infrastructure errors:\n");
      for (String error : report.getInfraErrors()) {
        sb.append("  - ").append(error).append('\n');
      }
    }
    for (DataQualityFinding finding : report.getFindings()) {
      sb.append("  [")
          .append(finding.getSeverity())
          .append("] ")
          .append(finding.getSubjectKey() != null ? finding.getSubjectKey() : "?")
          .append(" | ")
          .append(finding.getType())
          .append(finding.getFieldName() != null ? " field=" + finding.getFieldName() : "")
          .append(" | ")
          .append(finding.getMessage());
      if (finding.getActualSummary() != null) {
        sb.append(" (actual=").append(finding.getActualSummary()).append(')');
      }
      sb.append('\n');
    }
    return sb.toString();
  }
}
