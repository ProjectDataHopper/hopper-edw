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
package org.hopper.edw.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityReport;
import org.hopper.edw.quality.model.DataQualityRule;
import org.hopper.edw.quality.model.DataQualityRuleType;
import org.hopper.edw.quality.model.QualityEvaluationMode;
import org.hopper.edw.quality.model.QualityLifecycle;
import org.hopper.edw.quality.model.QualitySeverity;
import org.hopper.edw.quality.profile.DataProfileSnapshot;
import org.hopper.edw.quality.profile.DatabaseProfileCollector;
import org.hopper.edw.quality.profile.FieldProfile;
import org.hopper.edw.quality.profile.RowProfileCollector;
import org.hopper.edw.quality.service.DataQualityMeasureService;
import org.junit.jupiter.api.Test;

class DataQualityPhase2RulesTest {

  @Test
  void nullRatioMaxFailsWhenExceeded() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("segment"));
    List<Object[]> rows = new ArrayList<>();
    rows.add(new Object[] {"A"});
    rows.add(new Object[] {null});
    rows.add(new Object[] {null});
    rows.add(new Object[] {"B"});
    DataProfileSnapshot profile = fullScan(meta, rows);

    DataQualityRule rule =
        rule(DataQualityRuleType.NULL_RATIO_MAX, "segment", Map.of("maxRatio", "0.25"));
    DataQualityReport report = measure(profile, rule);
    assertTrue(report.hasBlockingFindings());
    assertTrue(report.getFindings().get(0).getActualSummary().contains("nullCount=2"));
  }

  @Test
  void nullRatioMaxPassesAtBoundaryAndZeroRows() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("segment"));
    List<Object[]> rows =
        List.of(new Object[] {"A"}, new Object[] {null}, new Object[] {"B"}, new Object[] {"C"});
    DataProfileSnapshot profile = fullScan(meta, rows);
    // 1/4 = 0.25 exactly → pass (ratio <= maxRatio)
    DataQualityRule rule =
        rule(DataQualityRuleType.NULL_RATIO_MAX, "segment", Map.of("maxRatio", "0.25"));
    assertFalse(measure(profile, rule).hasBlockingFindings());

    DataProfileSnapshot empty = new DataProfileSnapshot();
    empty.setSubjectKey("s");
    empty.setRowCount(0);
    empty.field("segment");
    assertFalse(
        measure(
                empty,
                rule(DataQualityRuleType.NULL_RATIO_MAX, "segment", Map.of("maxRatio", "0.0")))
            .hasBlockingFindings());
  }

  @Test
  void nullRatioMaxRejectsInvalidMaxRatio() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    profile.setRowCount(10);
    profile.field("x").addNullCount(1);
    DataQualityRule rule = rule(DataQualityRuleType.NULL_RATIO_MAX, "x", Map.of("maxRatio", "1.5"));
    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    assertEquals(QualitySeverity.WARNING, report.getFindings().get(0).getSeverity());
  }

  @Test
  void nullRatioMaxRejectsNaN() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    profile.setRowCount(10);
    profile.field("x").addNullCount(1);
    DataQualityRule rule = rule(DataQualityRuleType.NULL_RATIO_MAX, "x", Map.of("maxRatio", "NaN"));
    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    assertEquals(QualitySeverity.WARNING, report.getFindings().get(0).getSeverity());
    assertFalse(report.hasBlockingFindings());
  }

  @Test
  void minDistinctUsesExactCount() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    profile.setRowCount(100);
    FieldProfile field = profile.field("customer_id");
    field.setExactDistinctCount(3L);
    DataQualityRule rule =
        rule(DataQualityRuleType.MIN_DISTINCT, "customer_id", Map.of("min", "5"));
    assertTrue(measure(profile, rule).hasBlockingFindings());

    field.setExactDistinctCount(5L);
    assertFalse(measure(profile, rule).hasBlockingFindings());
  }

  @Test
  void maxDistinctExactFailsOverMax() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    field.setExactDistinctCount(10L);
    DataQualityRule rule = rule(DataQualityRuleType.MAX_DISTINCT, "code", Map.of("max", "5"));
    assertTrue(measure(profile, rule).hasBlockingFindings());
  }

  @Test
  void maxDistinctTruncatedEmitsInfoNotFail() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    // Simulate truncated sample with size <= max
    for (int i = 0; i < 3; i++) {
      field.observeValue("v" + i, "v" + i, 3);
    }
    // force truncated with size still 3
    field.observeValueCount("extra", "extra", 1L, 3);
    assertTrue(field.isDistinctTruncated());
    assertEquals(3, field.getDistinctValues().size());

    DataQualityRule rule = rule(DataQualityRuleType.MAX_DISTINCT, "code", Map.of("max", "10"));
    rule.setSeverity(QualitySeverity.BLOCKING);
    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    DataQualityFinding finding = report.getFindings().get(0);
    assertEquals(QualitySeverity.INFO, finding.getSeverity());
    assertTrue(finding.getMessage().contains("truncated"));
    assertEquals("true", finding.getMetrics().get("distinctTruncated"));
    assertFalse(report.hasBlockingFindings());
  }

  @Test
  void maxDistinctObservedSizeProvesViolationEvenIfTruncated() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    for (int i = 0; i < 5; i++) {
      field.observeValue("v" + i, "v" + i, 5);
    }
    field.observeValueCount("extra", "extra", 1L, 5);
    assertTrue(field.isDistinctTruncated());

    DataQualityRule rule = rule(DataQualityRuleType.MAX_DISTINCT, "code", Map.of("max", "3"));
    assertTrue(measure(profile, rule).hasBlockingFindings());
  }

  @Test
  void minDistinctObservedSizeProvesViolation() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    field.observeValue("only", "only", 200);
    DataQualityRule rule = rule(DataQualityRuleType.MIN_DISTINCT, "code", Map.of("min", "5"));
    assertTrue(measure(profile, rule).hasBlockingFindings());
  }

  @Test
  void minDistinctTruncatedWithSizeAtLeastMinPasses() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    for (int i = 0; i < 5; i++) {
      field.observeValue("v" + i, "v" + i, 5);
    }
    field.observeValueCount("extra", "extra", 1L, 5); // truncate, size stays 5
    assertTrue(field.isDistinctTruncated());
    assertEquals(5, field.getDistinctValues().size());

    DataQualityRule rule = rule(DataQualityRuleType.MIN_DISTINCT, "code", Map.of("min", "5"));
    assertEquals(0, measure(profile, rule).getFindingCount());
  }

  @Test
  void maxDistinctExactCountAtOrBelowMaxPasses() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    field.setExactDistinctCount(5L);
    DataQualityRule rule = rule(DataQualityRuleType.MAX_DISTINCT, "code", Map.of("max", "5"));
    assertEquals(0, measure(profile, rule).getFindingCount());
  }

  @Test
  void minDistinctUnknownEmitsWarningNotFalseFail() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    field.setDistinctUnknown(true);
    DataQualityRule rule = rule(DataQualityRuleType.MIN_DISTINCT, "code", Map.of("min", "1"));
    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    assertEquals(QualitySeverity.WARNING, report.getFindings().get(0).getSeverity());
    assertEquals("true", report.getFindings().get(0).getMetrics().get("distinctUnknown"));
    assertFalse(report.hasBlockingFindings());
  }

  @Test
  void emptyStringInRowProfileCountsForDistinctAndRegex() {
    // Direct FieldProfile path (Hop ValueMeta may treat "" as null in row meta)
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    profile.setRowCount(3);
    profile.setEvaluationMode(QualityEvaluationMode.FULL_SCAN);
    FieldProfile field = profile.field("code");
    field.observeValue("ABC", "ABC", 200);
    field.observeEmptyString(200);
    field.observeValue("XYZ", "XYZ", 200);
    field.setExactDistinctCount((long) field.getDistinctValues().size());

    assertTrue(field.getDistinctValues().contains(""));
    assertTrue(field.getValueCounts().containsKey(""));
    assertEquals(3L, field.getExactDistinctCount());

    // REGEX .+ should fail empty string
    DataQualityRule regex =
        rule(DataQualityRuleType.REGEX, "code", Map.of("pattern", ".+", "matchMode", "FULL"));
    assertTrue(measure(profile, regex).hasBlockingFindings());

    // MIN_DISTINCT with exact including empty
    DataQualityRule minDistinct =
        rule(DataQualityRuleType.MIN_DISTINCT, "code", Map.of("min", "3"));
    assertEquals(0, measure(profile, minDistinct).getFindingCount());
  }

  @Test
  void missingFieldEmitsFinding() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    profile.setRowCount(1);
    DataQualityRule rule =
        rule(DataQualityRuleType.MIN_DISTINCT, "missing_col", Map.of("min", "1"));
    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    assertTrue(report.getFindings().get(0).getMessage().contains("not found"));
  }

  @Test
  void regexFullMatchFailsInvalidValues() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("code"));
    List<Object[]> rows = List.of(new Object[] {"ABC"}, new Object[] {"12"}, new Object[] {"XYZ"});
    DataProfileSnapshot profile = fullScan(meta, rows);
    DataQualityRule rule =
        rule(DataQualityRuleType.REGEX, "code", Map.of("pattern", "[A-Z]{3}", "matchMode", "FULL"));
    DataQualityReport report = measure(profile, rule);
    assertTrue(report.hasBlockingFindings());
    assertTrue(report.getFindings().get(0).getMetrics().get("path").equals("sample"));
  }

  @Test
  void regexPassesWhenAllMatch() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("code"));
    List<Object[]> rows = List.of(new Object[] {"ABC"}, new Object[] {"XYZ"});
    DataProfileSnapshot profile = fullScan(meta, rows);
    DataQualityRule rule = rule(DataQualityRuleType.REGEX, "code", Map.of("pattern", "[A-Z]{3}"));
    assertFalse(measure(profile, rule).hasBlockingFindings());
  }

  @Test
  void regexNullAllowedFalseFailsOnNulls() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("code"));
    List<Object[]> rows = List.of(new Object[] {"ABC"}, new Object[] {null});
    DataProfileSnapshot profile = fullScan(meta, rows);
    DataQualityRule rule =
        rule(
            DataQualityRuleType.REGEX,
            "code",
            Map.of("pattern", "[A-Z]{3}", "nullAllowed", "false"));
    assertTrue(measure(profile, rule).hasBlockingFindings());
  }

  @Test
  void regexCoverageIncompleteEmitsInfo() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    field.observeValue("ABC", "ABC", 1);
    field.observeValueCount("DEF", "DEF", 1L, 1); // truncates
    assertTrue(field.isDistinctTruncated());

    DataQualityRule rule = rule(DataQualityRuleType.REGEX, "code", Map.of("pattern", "[A-Z]{3}"));
    rule.setSeverity(QualitySeverity.BLOCKING);
    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    assertEquals(QualitySeverity.INFO, report.getFindings().get(0).getSeverity());
    assertEquals("true", report.getFindings().get(0).getMetrics().get("coverageIncomplete"));
  }

  @Test
  void regexPushdownMismatchUsesRuleSeverity() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    DataQualityRule rule = rule(DataQualityRuleType.REGEX, "code", Map.of("pattern", "^[A-Z]+$"));
    rule.setSeverity(QualitySeverity.WARNING);
    FieldProfile.RegexRuleProfile stats = field.regexProfile(rule.getId());
    stats.setPath("pushdown");
    stats.setMismatchCount(7L);

    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    DataQualityFinding finding = report.getFindings().get(0);
    assertEquals(QualitySeverity.WARNING, finding.getSeverity());
    assertEquals("pushdown", finding.getMetrics().get("path"));
    assertEquals("7", finding.getMetrics().get("mismatchCount"));
  }

  @Test
  void regexFindModePartialMatch() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("note"));
    List<Object[]> rows = List.of(new Object[] {"xxABC yy"}, new Object[] {"nope"});
    DataProfileSnapshot profile = fullScan(meta, rows);
    DataQualityRule rule =
        rule(DataQualityRuleType.REGEX, "note", Map.of("pattern", "ABC", "matchMode", "FIND"));
    DataQualityReport report = measure(profile, rule);
    assertTrue(report.hasBlockingFindings());
  }

  @Test
  void minMaxLengthFromRowProfile() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("name"));
    List<Object[]> rows = List.of(new Object[] {"ab"}, new Object[] {"abcdef"});
    DataProfileSnapshot profile = fullScan(meta, rows);
    FieldProfile field = profile.findField("name");
    assertEquals(2, field.getMinStringLength());
    assertEquals(6, field.getMaxStringLength());

    // Empty string length 0 is tracked when observed as empty (collector path).
    field.observeEmptyString();
    assertEquals(0, field.getMinStringLength());

    DataQualityRule minRule = rule(DataQualityRuleType.MIN_LENGTH, "name", Map.of("min", "1"));
    assertTrue(measure(profile, minRule).hasBlockingFindings());

    DataQualityRule maxRule = rule(DataQualityRuleType.MAX_LENGTH, "name", Map.of("max", "5"));
    assertTrue(measure(profile, maxRule).hasBlockingFindings());

    // Reset max only fails for values above max
    DataProfileSnapshot profile2 =
        fullScan(meta, List.of(new Object[] {"ab"}, new Object[] {"abcdef"}));
    DataQualityRule okMax = rule(DataQualityRuleType.MAX_LENGTH, "name", Map.of("max", "6"));
    assertFalse(measure(profile2, okMax).hasBlockingFindings());
  }

  @Test
  void sqlStringLiteralEscaping() {
    assertEquals("'abc'", DatabaseProfileCollector.escapeSqlStringLiteral("abc"));
    assertEquals("'a''b'", DatabaseProfileCollector.escapeSqlStringLiteral("a'b"));
    assertEquals("''''", DatabaseProfileCollector.escapeSqlStringLiteral("'"));
    assertEquals("''", DatabaseProfileCollector.escapeSqlStringLiteral(null));
    assertEquals("'a''''b'", DatabaseProfileCollector.escapeSqlStringLiteral("a''b"));
  }

  @Test
  void distinctSampleSqlIsDialectAware() {
    String pg = DatabaseProfileCollector.distinctSampleSql("POSTGRESQL", "t", "c", 500);
    assertTrue(pg.contains("LIMIT 500"));
    assertFalse(pg.contains("TOP"));

    String mssql = DatabaseProfileCollector.distinctSampleSql("MSSQL", "t", "c", 100);
    assertTrue(mssql.contains("TOP 100"));

    String oracle = DatabaseProfileCollector.distinctSampleSql("ORACLE", "t", "c", 50);
    assertTrue(oracle.contains("FETCH FIRST 50 ROWS ONLY"));
  }

  @Test
  void regexMissingPatternEmitsWarning() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    profile.field("code").observeValue("ABC", "ABC", 10);
    DataQualityRule rule = rule(DataQualityRuleType.REGEX, "code", Map.of());
    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    assertEquals(QualitySeverity.WARNING, report.getFindings().get(0).getSeverity());
    assertEquals("true", report.getFindings().get(0).getMetrics().get("regexSkipped"));
  }

  @Test
  void regexSkippedWhenNoDistribution() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    profile.field("code"); // no values
    DataQualityRule rule = rule(DataQualityRuleType.REGEX, "code", Map.of("pattern", "[A-Z]+"));
    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    assertEquals(QualitySeverity.WARNING, report.getFindings().get(0).getSeverity());
    assertEquals("true", report.getFindings().get(0).getMetrics().get("regexSkipped"));
  }

  @Test
  void regexSkippedWhenCollectorMarksSkipped() {
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("s");
    FieldProfile field = profile.field("code");
    DataQualityRule rule = rule(DataQualityRuleType.REGEX, "code", Map.of("pattern", "[A-Z]+"));
    FieldProfile.RegexRuleProfile stats = field.regexProfile(RegexSupport.ruleKey(rule));
    stats.setSkipped(true);
    stats.setPath(RegexSupport.PATH_NONE);

    DataQualityReport report = measure(profile, rule);
    assertEquals(1, report.getFindingCount());
    assertEquals(QualitySeverity.WARNING, report.getFindings().get(0).getSeverity());
    assertEquals("true", report.getFindings().get(0).getMetrics().get("regexSkipped"));
  }

  @Test
  void phase2EnumsIncludeSqlAssertion() {
    List<String> names = new ArrayList<>();
    for (DataQualityRuleType t : DataQualityRuleType.values()) {
      names.add(t.name());
    }
    assertTrue(names.contains("NULL_RATIO_MAX"));
    assertTrue(names.contains("MIN_DISTINCT"));
    assertTrue(names.contains("MAX_DISTINCT"));
    assertTrue(names.contains("REGEX"));
    assertTrue(names.contains("MIN_LENGTH"));
    assertTrue(names.contains("MAX_LENGTH"));
    assertTrue(names.contains("SQL_ASSERTION"));
  }

  private static DataProfileSnapshot fullScan(IRowMeta meta, List<Object[]> rows) {
    return RowProfileCollector.collect(
        "s", meta, rows, QualityEvaluationMode.FULL_SCAN, QualityLifecycle.AD_HOC);
  }

  private static DataQualityReport measure(DataProfileSnapshot profile, DataQualityRule rule) {
    return DataQualityMeasureService.measureAgainstProfiles(
        "s", profile, List.of(rule), QualityLifecycle.AD_HOC);
  }

  private static DataQualityRule rule(
      DataQualityRuleType type, String field, Map<String, String> params) {
    DataQualityRule rule = new DataQualityRule();
    rule.setType(type);
    rule.setName(type.name());
    rule.setFieldName(field);
    rule.setSeverity(QualitySeverity.BLOCKING);
    rule.setParameters(new LinkedHashMap<>(params));
    rule.ensureId();
    return rule;
  }
}
