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
package org.hopper.edw.quality.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hopper.edw.quality.disposition.DispositionResult;
import org.hopper.edw.quality.disposition.QualityDisposition;
import org.hopper.edw.quality.disposition.QualityDispositionMode;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityReport;
import org.hopper.edw.quality.model.DataQualityRuleType;
import org.hopper.edw.quality.model.QualityLifecycle;
import org.hopper.edw.quality.model.QualitySeverity;
import org.junit.jupiter.api.Test;

class QualityAlertSupportTest {

  @Test
  void parseSinkIdsBlankDefaultsToLog() {
    assertEquals(List.of("log"), QualityAlertSupport.parseSinkIds(null));
    assertEquals(List.of("log"), QualityAlertSupport.parseSinkIds(""));
    assertEquals(List.of("log"), QualityAlertSupport.parseSinkIds("  "));
  }

  @Test
  void parseSinkIdsSplitsAndDedupes() {
    assertEquals(List.of("log", "ops_table"), QualityAlertSupport.parseSinkIds("log,ops_table"));
    assertEquals(
        List.of("log", "ops_table"), QualityAlertSupport.parseSinkIds(" LOG ; ops_table ; log "));
  }

  @Test
  void registryContainsLogAndOpsTableOnly() {
    Map<String, IQualityAlertSink> registry = QualityAlertSupport.registry();
    assertEquals(2, registry.size());
    assertTrue(registry.containsKey(LogQualityAlertSink.ID));
    assertTrue(registry.containsKey(OpsTableQualityAlertSink.ID));
  }

  @Test
  void zeroFindingsIsNoOpForAllModes() {
    for (QualityDispositionMode mode : QualityDispositionMode.values()) {
      List<IQualityAlertSink> sinks =
          QualityAlertSupport.resolveSinks(mode, 0, "log,ops_table", true);
      assertTrue(sinks.isEmpty(), "expected no sinks for " + mode + " with zero findings");
    }
  }

  @Test
  void alertOnlyWithFindingsInvokesAllConfiguredSinks() {
    List<IQualityAlertSink> sinks =
        QualityAlertSupport.resolveSinks(
            QualityDispositionMode.ALERT_ONLY, 2, "log,ops_table", false);
    assertEquals(
        List.of("log", "ops_table"),
        sinks.stream().map(IQualityAlertSink::id).collect(Collectors.toList()));
  }

  @Test
  void alertOnlyWithFindingsUsesDefaultLogWhenBlank() {
    List<IQualityAlertSink> sinks =
        QualityAlertSupport.resolveSinks(QualityDispositionMode.ALERT_ONLY, 1, null, false);
    assertEquals(
        List.of("log"), sinks.stream().map(IQualityAlertSink::id).collect(Collectors.toList()));
  }

  @Test
  void failModesLogWhenListedEvenWithoutAlertOnGateFailure() {
    List<IQualityAlertSink> sinks =
        QualityAlertSupport.resolveSinks(
            QualityDispositionMode.FAIL_ON_BLOCKING, 1, "log,ops_table", false);
    assertEquals(
        List.of("log"), sinks.stream().map(IQualityAlertSink::id).collect(Collectors.toList()));
  }

  @Test
  void failModesOpsTableOnlyWhenFlagAndListed() {
    List<IQualityAlertSink> withoutFlag =
        QualityAlertSupport.resolveSinks(
            QualityDispositionMode.FAIL_ON_WARNINGS, 1, "ops_table", false);
    assertTrue(withoutFlag.isEmpty());

    List<IQualityAlertSink> withFlag =
        QualityAlertSupport.resolveSinks(
            QualityDispositionMode.FAIL_ON_WARNINGS, 1, "log,ops_table", true);
    assertEquals(
        List.of("log", "ops_table"),
        withFlag.stream().map(IQualityAlertSink::id).collect(Collectors.toList()));
  }

  @Test
  void alertOnlyDispositionPassesWithFindings() {
    DataQualityReport report = reportWithFinding();
    DispositionResult result = QualityDisposition.apply(report, QualityDispositionMode.ALERT_ONLY);
    assertTrue(!result.isFailed());
    assertTrue(report.getFindingCount() > 0);
  }

  private static DataQualityReport reportWithFinding() {
    DataQualityReport report = new DataQualityReport(QualityLifecycle.POST_UPDATE);
    report.addSubjectKey("hop/retail-example/sources/CRM-customer");
    report.addFinding(
        DataQualityFinding.builder()
            .subjectKey("hop/retail-example/sources/CRM-customer")
            .ruleId("not-null")
            .ruleName("Not null")
            .type(DataQualityRuleType.NOT_NULL)
            .severity(QualitySeverity.WARNING)
            .fieldName("customer_id")
            .message("nulls")
            .build());
    return report;
  }
}
