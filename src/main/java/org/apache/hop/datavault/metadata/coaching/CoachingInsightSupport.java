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
package org.apache.hop.datavault.metadata.coaching;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;

/** Builds coaching insights from cached validation results and target usage. */
public final class CoachingInsightSupport {

  private CoachingInsightSupport() {}

  public static List<CoachingInsight> buildInsights(
      ICoachingModelAdapter adapter,
      CoachingSourceRef sourceRef,
      List<CoachingTargetUsage> targets,
      List<ICheckResult> checks,
      IVariables variables) {
    if (adapter instanceof DvCoachingModelAdapter) {
      return buildDvInsights(sourceRef, targets, checks, variables);
    }
    if (adapter instanceof BvCoachingModelAdapter) {
      return buildBvInsights(sourceRef, targets, checks);
    }
    if (adapter instanceof DmCoachingModelAdapter dmAdapter) {
      return buildDmInsights(dmAdapter.getModel(), sourceRef, targets, checks);
    }
    return List.of();
  }

  private static List<CoachingInsight> buildDvInsights(
      CoachingSourceRef sourceRef,
      List<CoachingTargetUsage> targets,
      List<ICheckResult> checks,
      IVariables variables) {
    List<CoachingInsight> insights = new ArrayList<>();
    if (targets.isEmpty()) {
      insights.add(
          CoachingInsight.builder()
              .severity(CoachingInsight.Severity.INFO)
              .message("Not mapped. Use Generate DV or drag to canvas")
              .build());
    }
    appendFilteredChecks(
        insights,
        checks,
        sourceRef == null || Utils.isEmpty(sourceRef.getRecordName())
            ? null
            : variables.resolve(sourceRef.getRecordName()),
        null);
    return insights;
  }

  private static List<CoachingInsight> buildBvInsights(
      CoachingSourceRef sourceRef, List<CoachingTargetUsage> targets, List<ICheckResult> checks) {
    List<CoachingInsight> insights = new ArrayList<>();
    if (targets.isEmpty()) {
      insights.add(
          CoachingInsight.builder()
              .severity(CoachingInsight.Severity.INFO)
              .message("DV derivative not referenced by a BV table yet")
              .build());
    }
    appendFilteredChecks(
        insights, checks, sourceRef == null ? null : sourceRef.getDerivedDvTableName(), null);
    return insights;
  }

  private static List<CoachingInsight> buildDmInsights(
      DimensionalModel model,
      CoachingSourceRef sourceRef,
      List<CoachingTargetUsage> targets,
      List<ICheckResult> checks) {
    List<CoachingInsight> insights = new ArrayList<>();
    if (sourceRef == null) {
      return insights;
    }
    String tableName = sourceRef.getDerivedFromTable();
    if (sourceRef.getSourceType() == CoachingSourceType.SQL
        && Utils.isEmpty(findTableSourceSql(model, tableName))) {
      insights.add(
          CoachingInsight.builder()
              .severity(CoachingInsight.Severity.WARNING)
              .message("SQL source is empty")
              .targetTableName(tableName)
              .build());
    }
    if (sourceRef.getSourceType() == CoachingSourceType.PIPELINE) {
      DmSourceConfiguration source = findTableSource(model, tableName);
      if (source == null
          || Utils.isEmpty(source.getSourcePipelineFile())
          || Utils.isEmpty(source.getSourcePipelineTransform())) {
        insights.add(
            CoachingInsight.builder()
                .severity(CoachingInsight.Severity.WARNING)
                .message("Pipeline source is incomplete")
                .targetTableName(tableName)
                .build());
      }
    }
    appendFilteredChecks(insights, checks, tableName, tableName);
    return insights;
  }

  private static void appendFilteredChecks(
      List<CoachingInsight> insights,
      List<ICheckResult> checks,
      String textFilter,
      String targetTableName) {
    if (checks == null || Utils.isEmpty(textFilter)) {
      return;
    }
    try {
      for (ICheckResult check : checks) {
        if (check == null || Utils.isEmpty(check.getText())) {
          continue;
        }
        if (check.getText().contains(textFilter)) {
          insights.add(
              CoachingInsight.builder()
                  .severity(severityForCheck(check))
                  .message(check.getText())
                  .targetTableName(targetTableName)
                  .build());
        }
      }
    } catch (Exception e) {
      insights.add(
          CoachingInsight.builder()
              .severity(CoachingInsight.Severity.WARNING)
              .message(e.getMessage())
              .targetTableName(targetTableName)
              .build());
    }
  }

  private static DmSourceConfiguration findTableSource(DimensionalModel model, String tableName) {
    if (model == null || model.getTables() == null) {
      return null;
    }
    for (IDmTable table : model.getTables()) {
      if (table instanceof DmTableBase tableBase && tableName.equals(table.getTableName())) {
        return tableBase.getSource();
      }
    }
    return null;
  }

  private static String findTableSourceSql(DimensionalModel model, String tableName) {
    DmSourceConfiguration source = findTableSource(model, tableName);
    return source == null ? null : source.getSourceSql();
  }

  private static CoachingInsight.Severity severityForCheck(ICheckResult check) {
    if (check.getType() == ICheckResult.TYPE_RESULT_ERROR) {
      return CoachingInsight.Severity.ERROR;
    }
    if (check.getType() == ICheckResult.TYPE_RESULT_WARNING) {
      return CoachingInsight.Severity.WARNING;
    }
    return CoachingInsight.Severity.INFO;
  }
}
