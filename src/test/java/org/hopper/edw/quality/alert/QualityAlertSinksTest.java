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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.IMetrics;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.quality.disposition.DispositionResult;
import org.hopper.edw.quality.disposition.QualityDisposition;
import org.hopper.edw.quality.disposition.QualityDispositionMode;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher.PublishContext;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher.PublishResult;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher.PublishStatus;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityReport;
import org.hopper.edw.quality.model.DataQualityRuleType;
import org.hopper.edw.quality.model.QualityEvaluationMode;
import org.hopper.edw.quality.model.QualityLifecycle;
import org.hopper.edw.quality.model.QualitySeverity;
import org.hopper.edw.quality.profile.DataProfileSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QualityAlertSinksTest {

  private static final String OPS_NAME = "OPS";

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;
  private DatabaseMeta h2Ops;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");
    metadataProvider = new MemoryMetadataProvider();
    h2Ops = buildH2DatabaseMeta(OPS_NAME, "mem:quality_alert_" + UUID.randomUUID());
    metadataProvider.getSerializer(DatabaseMeta.class).save(h2Ops);
  }

  @Test
  void zeroFindingsPublishIsNoOp() throws Exception {
    DataQualityReport report = new DataQualityReport(QualityLifecycle.POST_UPDATE);
    report.addSubjectKey("hop/x/y");
    DispositionResult disposition =
        QualityDisposition.apply(report, QualityDispositionMode.ALERT_ONLY);

    RecordingLog log = new RecordingLog();
    QualityAlertContext context =
        QualityAlertContext.builder()
            .report(report)
            .disposition(disposition)
            .mode(QualityDispositionMode.ALERT_ONLY)
            .log(log)
            .variables(variables)
            .metadataProvider(metadataProvider)
            .publishContext(
                new PublishContext(
                    OPS_NAME,
                    DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME,
                    null,
                    false,
                    true,
                    true))
            .build();

    QualityAlertSupport.publish(context, "log,ops_table", true);

    assertTrue(
        log.messages.stream().noneMatch(m -> m.startsWith("QUALITY_ALERT")),
        "no QUALITY_ALERT lines when findings==0: " + log.messages);
    assertEquals(0L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_ALERT, report.getRunId()));
    assertEquals(0L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_RUN, report.getRunId()));
  }

  @Test
  void alertOnlyWithFindingsPublishesLogAndOpsTable() throws Exception {
    DataQualityReport report = sampleReportWithFinding();
    DispositionResult disposition =
        QualityDisposition.apply(report, QualityDispositionMode.ALERT_ONLY);
    assertTrue(!disposition.isFailed());

    PublishContext publishContext =
        new PublishContext(
            OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, null, false, true, true);

    RecordingLog log = new RecordingLog();
    QualityAlertContext context =
        QualityAlertContext.builder()
            .report(report)
            .disposition(disposition)
            .mode(QualityDispositionMode.ALERT_ONLY)
            .log(log)
            .variables(variables)
            .metadataProvider(metadataProvider)
            .publishContext(publishContext)
            .loadId("load-1")
            .workflowName("wf-alert")
            .workflowExecutionId("exec-1")
            .build();

    QualityAlertSupport.publish(context, "log,ops_table", false);

    assertTrue(
        log.messages.stream().anyMatch(m -> m.startsWith("QUALITY_ALERT runId=")),
        "expected QUALITY_ALERT header in log: " + log.messages);
    assertTrue(
        log.messages.stream().anyMatch(m -> m.contains("QUALITY_ALERT finding")),
        "expected finding lines: " + log.messages);

    assertEquals(1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_RUN, report.getRunId()));
    assertEquals(
        1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, report.getRunId()));
    assertEquals(1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_ALERT, report.getRunId()));
  }

  @Test
  void opsTableDoesNotDuplicateFindingsWhenRunAlreadyPersisted() throws Exception {
    DataQualityReport report = sampleReportWithFinding();
    PublishContext publishContext =
        new PublishContext(
            OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, null, false, true, true);

    PublishResult first =
        DataQualityHistoryPublisher.publish(
            LogChannel.GENERAL,
            report,
            publishContext,
            "load-1",
            "wf",
            "exec",
            variables,
            metadataProvider);
    assertEquals(PublishStatus.INSERTED, first.status(), first.message());
    assertEquals(
        1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, report.getRunId()));

    DispositionResult disposition =
        QualityDisposition.apply(report, QualityDispositionMode.ALERT_ONLY);
    QualityAlertContext context =
        QualityAlertContext.builder()
            .report(report)
            .disposition(disposition)
            .mode(QualityDispositionMode.ALERT_ONLY)
            .log(LogChannel.GENERAL)
            .variables(variables)
            .metadataProvider(metadataProvider)
            .publishContext(publishContext)
            .loadId("load-1")
            .workflowName("wf")
            .workflowExecutionId("exec")
            .build();

    new OpsTableQualityAlertSink().publish(context);

    assertEquals(1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_RUN, report.getRunId()));
    assertEquals(
        1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, report.getRunId()));
    assertEquals(1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_ALERT, report.getRunId()));

    // Second ops_table publish skips alert (immutable) and still does not re-insert findings.
    new OpsTableQualityAlertSink().publish(context);
    assertEquals(
        1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, report.getRunId()));
    assertEquals(1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_ALERT, report.getRunId()));
  }

  private DataQualityReport sampleReportWithFinding() {
    DataQualityReport report = new DataQualityReport(QualityLifecycle.POST_UPDATE);
    String subjectKey = "hop/retail-example/sources/CRM-customer";
    report.addSubjectKey(subjectKey);

    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey(subjectKey);
    profile.setEvaluationMode(QualityEvaluationMode.SQL_PUSHDOWN);
    profile.setLifecycle(QualityLifecycle.POST_UPDATE);
    profile.setRowCount(10);
    profile.setRowCountExact(true);
    profile.field("customer_id").observeValue("C1", "C1", 10);
    report.putProfile(subjectKey, profile);

    report.addFinding(
        DataQualityFinding.builder()
            .subjectKey(subjectKey)
            .ruleId("not-null-customer")
            .ruleName("Customer id not null")
            .type(DataQualityRuleType.NOT_NULL)
            .severity(QualitySeverity.WARNING)
            .fieldName("customer_id")
            .message("nulls found")
            .actualSummary("1 nulls")
            .expectedSummary("0 nulls")
            .metrics(Map.of("nullCount", "1"))
            .build());
    return report;
  }

  private long countRows(String table, String runId) throws HopException {
    Database db = new Database(new LoggingObject(getClass()), variables, h2Ops);
    try {
      db.connect();
      if (!db.checkTableExists(DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, table)) {
        return 0L;
      }
      String qualified =
          h2Ops.getQuotedSchemaTableCombination(
              db, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, table);
      RowMetaAndData row =
          db.getOneRow(
              "SELECT COUNT(*) FROM "
                  + qualified
                  + " WHERE quality_run_id = '"
                  + runId.replace("'", "''")
                  + "'");
      if (row == null
          || row.getData() == null
          || row.getData().length == 0
          || row.getData()[0] == null) {
        return 0L;
      }
      Object value = row.getData()[0];
      if (value instanceof Number n) {
        return n.longValue();
      }
      return Long.parseLong(String.valueOf(value));
    } finally {
      db.disconnect();
    }
  }

  private static DatabaseMeta buildH2DatabaseMeta(String name, String dbName) {
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName(name);
    databaseMeta.setDatabaseType("H2");
    databaseMeta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    databaseMeta.setDBName(dbName + ";DB_CLOSE_DELAY=-1");
    databaseMeta.setHostname("");
    databaseMeta.setPort("");
    databaseMeta.setUsername("sa");
    databaseMeta.setPassword("");
    return databaseMeta;
  }

  /** Minimal log channel that records basic messages for assertions. */
  private static final class RecordingLog implements ILogChannel {
    private final List<String> messages = new ArrayList<>();
    private final ILogChannel delegate = LogChannel.GENERAL;

    @Override
    public void logBasic(String s) {
      messages.add(s);
      delegate.logBasic(s);
    }

    @Override
    public void logBasic(String s, Object... objects) {
      messages.add(s);
      delegate.logBasic(s, objects);
    }

    @Override
    public String getLogChannelId() {
      return delegate.getLogChannelId();
    }

    @Override
    public void logMinimal(String s) {
      delegate.logMinimal(s);
    }

    @Override
    public void logMinimal(String s, Object... objects) {
      delegate.logMinimal(s, objects);
    }

    @Override
    public void logDebug(String s) {
      delegate.logDebug(s);
    }

    @Override
    public void logDebug(String s, Object... objects) {
      delegate.logDebug(s, objects);
    }

    @Override
    public void logRowlevel(String s) {
      delegate.logRowlevel(s);
    }

    @Override
    public void logRowlevel(String s, Object... objects) {
      delegate.logRowlevel(s, objects);
    }

    @Override
    public void logDetailed(String s) {
      delegate.logDetailed(s);
    }

    @Override
    public void logDetailed(String s, Object... objects) {
      delegate.logDetailed(s, objects);
    }

    @Override
    public void logError(String s) {
      delegate.logError(s);
    }

    @Override
    public void logError(String s, Throwable e) {
      delegate.logError(s, e);
    }

    @Override
    public void logError(String s, Object... objects) {
      delegate.logError(s, objects);
    }

    @Override
    public boolean isBasic() {
      return true;
    }

    @Override
    public boolean isDetailed() {
      return delegate.isDetailed();
    }

    @Override
    public boolean isDebug() {
      return delegate.isDebug();
    }

    @Override
    public boolean isRowLevel() {
      return delegate.isRowLevel();
    }

    @Override
    public boolean isError() {
      return delegate.isError();
    }

    @Override
    public void setFilter(String filter) {
      delegate.setFilter(filter);
    }

    @Override
    public String getFilter() {
      return delegate.getFilter();
    }

    @Override
    public boolean isGatheringMetrics() {
      return delegate.isGatheringMetrics();
    }

    @Override
    public void setGatheringMetrics(boolean gatheringMetrics) {
      delegate.setGatheringMetrics(gatheringMetrics);
    }

    @Override
    public void setForcingSeparateLogging(boolean forcingSeparateLogging) {
      delegate.setForcingSeparateLogging(forcingSeparateLogging);
    }

    @Override
    public boolean isForcingSeparateLogging() {
      return delegate.isForcingSeparateLogging();
    }

    @Override
    public String getContainerObjectId() {
      return delegate.getContainerObjectId();
    }

    @Override
    public void setContainerObjectId(String containerObjectId) {
      delegate.setContainerObjectId(containerObjectId);
    }

    @Override
    public LogLevel getLogLevel() {
      return delegate.getLogLevel();
    }

    @Override
    public void setLogLevel(LogLevel logLevel) {
      delegate.setLogLevel(logLevel);
    }

    @Override
    public void snap(IMetrics metric, long... value) {
      delegate.snap(metric, value);
    }

    @Override
    public void snap(IMetrics metric, String subject, long... value) {
      delegate.snap(metric, subject, value);
    }
  }
}
