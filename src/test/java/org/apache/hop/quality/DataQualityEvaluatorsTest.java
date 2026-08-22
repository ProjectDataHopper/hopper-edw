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
package org.apache.hop.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.quality.disposition.DispositionResult;
import org.apache.hop.quality.disposition.QualityDisposition;
import org.apache.hop.quality.disposition.QualityDispositionMode;
import org.apache.hop.quality.model.DataQualityReport;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;
import org.apache.hop.quality.model.QualitySeverity;
import org.apache.hop.quality.profile.DataProfileSnapshot;
import org.apache.hop.quality.profile.RowProfileCollector;
import org.apache.hop.quality.service.DataQualityMeasureService;
import org.junit.jupiter.api.Test;

class DataQualityEvaluatorsTest {

  @Test
  void minRowCountBlocksEmpty() {
    DataProfileSnapshot profile = emptyProfile();
    DataQualityRule rule = rule(DataQualityRuleType.MIN_ROW_COUNT, null, Map.of("min", "1"));
    DataQualityReport report =
        DataQualityMeasureService.measureAgainstProfiles(
            "test/subject", profile, List.of(rule), QualityLifecycle.PRE_UPDATE);
    assertTrue(report.hasBlockingFindings());
    assertEquals(1, report.getFindingCount());
  }

  @Test
  void notNullFindsNulls() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("name"));
    List<Object[]> rows = new ArrayList<>();
    rows.add(new Object[] {"Ada"});
    rows.add(new Object[] {null});
    DataProfileSnapshot profile =
        RowProfileCollector.collect(
            "s", meta, rows, QualityEvaluationMode.FULL_SCAN, QualityLifecycle.AD_HOC);
    DataQualityRule rule = rule(DataQualityRuleType.NOT_NULL, "name", Map.of());
    DataQualityReport report =
        DataQualityMeasureService.measureAgainstProfiles(
            "s", profile, List.of(rule), QualityLifecycle.AD_HOC);
    assertTrue(report.hasBlockingFindings());
  }

  @Test
  void allowedValuesDetectsInvalid() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("gender"));
    List<Object[]> rows = List.of(new Object[] {"M"}, new Object[] {"X"}, new Object[] {null});
    DataProfileSnapshot profile =
        RowProfileCollector.collect(
            "s", meta, rows, QualityEvaluationMode.FULL_SCAN, QualityLifecycle.AD_HOC);
    DataQualityRule rule =
        rule(
            DataQualityRuleType.ALLOWED_VALUES,
            "gender",
            Map.of("values", "M,F,U", "nullAllowed", "true"));
    DataQualityReport report =
        DataQualityMeasureService.measureAgainstProfiles(
            "s", profile, List.of(rule), QualityLifecycle.AD_HOC);
    assertTrue(report.hasBlockingFindings());
    assertTrue(report.getFindings().get(0).getMessage().contains("outside the allowed set"));
  }

  @Test
  void rangeDetectsOutOfBand() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaInteger("items"));
    List<Object[]> rows = List.of(new Object[] {5L}, new Object[] {1500L});
    DataProfileSnapshot profile =
        RowProfileCollector.collect(
            "s", meta, rows, QualityEvaluationMode.FULL_SCAN, QualityLifecycle.AD_HOC);
    DataQualityRule rule =
        rule(DataQualityRuleType.RANGE, "items", Map.of("min", "0", "max", "999"));
    DataQualityReport report =
        DataQualityMeasureService.measureAgainstProfiles(
            "s", profile, List.of(rule), QualityLifecycle.AD_HOC);
    assertTrue(report.hasBlockingFindings());
  }

  @Test
  void dispositionGateFailsBlockingOnly() {
    DataProfileSnapshot profile = emptyProfile();
    DataQualityRule rule = rule(DataQualityRuleType.MIN_ROW_COUNT, null, Map.of("min", "1"));
    rule.setSeverity(QualitySeverity.BLOCKING);
    DataQualityReport report =
        DataQualityMeasureService.measureAgainstProfiles(
            "s", profile, List.of(rule), QualityLifecycle.PRE_UPDATE);

    DispositionResult gate =
        QualityDisposition.apply(report, QualityDispositionMode.FAIL_ON_BLOCKING);
    assertTrue(gate.isFailed());

    DispositionResult alert = QualityDisposition.apply(report, QualityDispositionMode.ALERT_ONLY);
    assertFalse(alert.isFailed());
  }

  @Test
  void measureApiDoesNotThrowOnFindings() {
    DataProfileSnapshot profile = emptyProfile();
    DataQualityRule rule = rule(DataQualityRuleType.MIN_ROW_COUNT, null, Map.of("min", "10"));
    DataQualityReport report =
        DataQualityMeasureService.measureAgainstProfiles(
            "s", profile, List.of(rule), QualityLifecycle.PRE_UPDATE);
    assertTrue(report.getFindingCount() > 0);
    assertFalse(report.hasInfraErrors());
  }

  private static DataProfileSnapshot emptyProfile() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    profile.setRowCount(0);
    profile.setRowCountExact(true);
    profile.setEvaluationMode(QualityEvaluationMode.SQL_PUSHDOWN);
    return profile;
  }

  private static DataQualityRule rule(
      DataQualityRuleType type, String field, Map<String, String> params) {
    DataQualityRule rule = new DataQualityRule();
    rule.setType(type);
    rule.setName(type.name());
    rule.setFieldName(field);
    rule.setSeverity(QualitySeverity.BLOCKING);
    rule.setParameters(new java.util.LinkedHashMap<>(params));
    rule.ensureId();
    return rule;
  }
}
