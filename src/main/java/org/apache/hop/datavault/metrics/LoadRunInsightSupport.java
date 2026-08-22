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
package org.apache.hop.datavault.metrics;

import java.util.ArrayList;
import java.util.List;

/** Shared severity and reportability rules for load-run insights. */
public final class LoadRunInsightSupport {

  public static final String SEVERITY_WARNING = "warning";
  public static final String SEVERITY_INFO = "info";
  public static final String SEVERITY_NOTE = "note";

  private LoadRunInsightSupport() {}

  public static boolean isReportable(String severity) {
    return !SEVERITY_NOTE.equalsIgnoreCase(severity);
  }

  public static boolean isReportable(LoadRunInsight insight) {
    return insight != null && isReportable(insight.getSeverity());
  }

  public static List<LoadRunInsight> reportableInsights(List<LoadRunInsight> insights) {
    if (insights == null || insights.isEmpty()) {
      return List.of();
    }
    List<LoadRunInsight> reportable = new ArrayList<>();
    for (LoadRunInsight insight : insights) {
      if (isReportable(insight)) {
        reportable.add(insight);
      }
    }
    return reportable;
  }

  static String sqlExcludeNoteSeverityClause() {
    return " AND (severity IS NULL OR LOWER(severity) <> '" + SEVERITY_NOTE + "')";
  }
}
