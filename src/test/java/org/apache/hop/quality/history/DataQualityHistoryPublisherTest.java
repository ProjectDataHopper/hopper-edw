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
package org.apache.hop.quality.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishContext;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishResult;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishStatus;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityReport;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;
import org.apache.hop.quality.model.QualitySeverity;
import org.apache.hop.quality.profile.DataProfileSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataQualityHistoryPublisherTest {

  private static final String CATALOG_CONNECTION = "quality-history-test-catalog";
  private static final String OPS_NAME = "OPS";

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;
  private DatabaseMeta h2Ops;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

    metadataProvider = new MemoryMetadataProvider();
    Path catalogDir = Files.createTempDirectory("quality-history-catalog-test");
    metadataProvider.getSerializer(DataCatalogMeta.class).save(buildCatalogMeta(catalogDir));

    h2Ops = buildH2DatabaseMeta(OPS_NAME, "mem:quality_hist_" + UUID.randomUUID());
    metadataProvider.getSerializer(DatabaseMeta.class).save(h2Ops);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void catalogDefinitionsMatchSection23Columns() throws Exception {
    String namespace = DataQualityHistoryPublisher.operationsNamespace(variables);
    assertEquals("hop/retail-example/operations", namespace);

    RecordDefinition run =
        DataQualityHistoryPublisher.buildQualityRunDefinition(
            namespace, OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, h2Ops);
    assertEquals(16, run.getFields().size());
    assertNotNull(run.getFields().searchValueMeta("quality_run_id"));
    assertNotNull(run.getFields().searchValueMeta("subjects_json"));
    assertTrue(run.getTags().contains("data-quality"));

    RecordDefinition subject =
        DataQualityHistoryPublisher.buildProfileSubjectDefinition(
            namespace, OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, h2Ops);
    assertEquals(7, subject.getFields().size());
    assertNotNull(subject.getFields().searchValueMeta("row_count"));

    RecordDefinition field =
        DataQualityHistoryPublisher.buildProfileFieldDefinition(
            namespace, OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, h2Ops);
    assertEquals(13, field.getFields().size());
    assertNotNull(field.getFields().searchValueMeta("top_values_json"));

    RecordDefinition finding =
        DataQualityHistoryPublisher.buildFindingDefinition(
            namespace, OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, h2Ops);
    assertEquals(12, finding.getFields().size());
    assertNotNull(finding.getFields().searchValueMeta("finding_seq"));

    RecordDefinition alert =
        DataQualityHistoryPublisher.buildAlertDefinition(
            namespace, OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, h2Ops);
    assertEquals(5, alert.getFields().size());
    assertNotNull(alert.getFields().searchValueMeta("disposition_mode"));
  }

  @Test
  void publishInsertsThenSkipsDuplicateRunId() throws Exception {
    DataQualityReport report = sampleReport();
    PublishContext context =
        new PublishContext(
            OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, null, false, true, true);

    PublishResult first =
        DataQualityHistoryPublisher.publish(
            LogChannel.GENERAL,
            report,
            context,
            "load-1",
            "wf-test",
            "exec-1",
            variables,
            metadataProvider);
    assertEquals(PublishStatus.INSERTED, first.status(), first.message());

    long runs = countRows(DataQualityHistoryPublisher.TABLE_QUALITY_RUN, report.getRunId());
    long subjects =
        countRows(DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT, report.getRunId());
    long fields =
        countRows(DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD, report.getRunId());
    long findings = countRows(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, report.getRunId());
    assertEquals(1L, runs);
    assertEquals(1L, subjects);
    assertEquals(1L, fields);
    assertEquals(1L, findings);

    // All five tables exist (including quality_alert with no rows yet).
    assertTrue(tableExists(DataQualityHistoryPublisher.TABLE_QUALITY_ALERT));

    PublishResult second =
        DataQualityHistoryPublisher.publish(
            LogChannel.GENERAL,
            report,
            context,
            "load-1",
            "wf-test",
            "exec-1",
            variables,
            metadataProvider);
    assertEquals(PublishStatus.SKIPPED, second.status(), second.message());
    assertEquals(1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_RUN, report.getRunId()));
  }

  @Test
  void partialFailureAfterQualityRunCleansUpAllRows() throws Exception {
    DataQualityReport report = sampleReport();
    PublishContext context =
        new PublishContext(
            OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, null, false, true, true);

    DataQualityHistoryPublisher.afterQualityRunInsertedForTest =
        () -> {
          throw new RuntimeException("forced mid-publish failure");
        };
    try {
      PublishResult result =
          DataQualityHistoryPublisher.publish(
              LogChannel.GENERAL,
              report,
              context,
              "load-partial",
              "wf",
              "exec",
              variables,
              metadataProvider);
      assertEquals(PublishStatus.FAILED, result.status(), result.message());
      assertTrue(result.message().contains("forced mid-publish failure"));
      assertEquals(0L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_RUN, report.getRunId()));
      assertEquals(
          0L,
          countRows(DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT, report.getRunId()));
      assertEquals(
          0L,
          countRows(DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD, report.getRunId()));
      assertEquals(
          0L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, report.getRunId()));
    } finally {
      DataQualityHistoryPublisher.afterQualityRunInsertedForTest = null;
    }
  }

  @Test
  void publishPersistsEmptyFindingsBaseline() throws Exception {
    DataQualityReport report = new DataQualityReport(QualityLifecycle.POST_UPDATE);
    report.addSubjectKey("hop/retail-example/models/retail-360/hub_customer");
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey("hop/retail-example/models/retail-360/hub_customer");
    profile.setEvaluationMode(QualityEvaluationMode.SQL_PUSHDOWN);
    profile.setLifecycle(QualityLifecycle.POST_UPDATE);
    profile.setRowCount(10);
    profile.setRowCountExact(true);
    report.putProfile("hop/retail-example/models/retail-360/hub_customer", profile);

    PublishContext context =
        new PublishContext(
            OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, null, false, true, true);

    PublishResult result =
        DataQualityHistoryPublisher.publish(
            LogChannel.GENERAL,
            report,
            context,
            "load-empty",
            "wf",
            "exec",
            variables,
            metadataProvider);
    assertEquals(PublishStatus.INSERTED, result.status(), result.message());
    assertEquals(1L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_RUN, report.getRunId()));
    assertEquals(
        0L, countRows(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, report.getRunId()));
  }

  @Test
  void publishUpsertsCatalogDefinitions() throws Exception {
    DataQualityReport report = sampleReport();
    PublishContext context =
        new PublishContext(
            OPS_NAME,
            DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME,
            CATALOG_CONNECTION,
            true,
            false,
            false);

    PublishResult result =
        DataQualityHistoryPublisher.publish(
            LogChannel.GENERAL,
            report,
            context,
            "load-cat",
            "wf",
            "exec",
            variables,
            metadataProvider);
    assertEquals(PublishStatus.INSERTED, result.status(), result.message());

    String namespace = DataQualityHistoryPublisher.operationsNamespace(variables);
    for (String table :
        List.of(
            DataQualityHistoryPublisher.TABLE_QUALITY_RUN,
            DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT,
            DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD,
            DataQualityHistoryPublisher.TABLE_QUALITY_FINDING,
            DataQualityHistoryPublisher.TABLE_QUALITY_ALERT)) {
      RecordDefinition def =
          RecordDefinitionRegistry.getInstance()
              .read(
                  CATALOG_CONNECTION,
                  new RecordDefinitionKey(namespace, table),
                  variables,
                  metadataProvider);
      assertNotNull(def, "missing catalog def for " + table);
      assertEquals(RecordDefinitionType.PHYSICAL_TABLE, def.getType());
      assertEquals(OPS_NAME, def.getPhysicalTable().getDatabaseMetaName());
      assertEquals(
          DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, def.getPhysicalTable().getSchemaName());
      assertTrue(def.getTags().contains("operations"));
      assertTrue(def.getTags().contains("data-quality"));
    }
  }

  @Test
  void helperJsonAndTruncate() {
    assertEquals("[]", DataQualityHistoryPublisher.toJsonArray(List.of()));
    assertTrue(DataQualityHistoryPublisher.toJsonArray(List.of("a", "b")).contains("\"a\""));
    assertEquals("ab", DataQualityHistoryPublisher.truncate("abcdef", 2));
    assertNull(DataQualityHistoryPublisher.truncate(null, 10));
    assertTrue(DataQualityHistoryPublisher.mapToJson(Map.of("x", "2")).contains("\"x\""));
    String top = DataQualityHistoryPublisher.topValuesJson(Map.of("a", 1L, "b", 5L, "c", 3L));
    assertNotNull(top);
    assertTrue(top.contains("\"b\""));
  }

  private DataQualityReport sampleReport() {
    DataQualityReport report = new DataQualityReport(QualityLifecycle.PRE_UPDATE);
    String subjectKey = "hop/retail-example/sources/CRM-customer";
    report.addSubjectKey(subjectKey);

    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey(subjectKey);
    profile.setEvaluationMode(QualityEvaluationMode.SQL_PUSHDOWN);
    profile.setLifecycle(QualityLifecycle.PRE_UPDATE);
    profile.setRowCount(100);
    profile.setRowCountExact(true);
    profile.field("customer_id").observeValue("C1", "C1", 100);
    profile.field("customer_id").addNullCount(2);
    report.putProfile(subjectKey, profile);

    report.addFinding(
        DataQualityFinding.builder()
            .subjectKey(subjectKey)
            .ruleId("not-null-customer")
            .ruleName("Customer id not null")
            .type(DataQualityRuleType.NOT_NULL)
            .severity(QualitySeverity.BLOCKING)
            .fieldName("customer_id")
            .message("nulls found")
            .actualSummary("2 nulls")
            .expectedSummary("0 nulls")
            .metrics(Map.of("nullCount", "2"))
            .build());
    return report;
  }

  private long countRows(String table, String runId) throws HopException {
    Database db = new Database(new LoggingObject(getClass()), variables, h2Ops);
    try {
      db.connect();
      String qualified =
          h2Ops.getQuotedSchemaTableCombination(
              db, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, table);
      RowMetaAndData row =
          db.getOneRow(
              "SELECT COUNT(*) FROM "
                  + qualified
                  + " WHERE quality_run_id = "
                  + DataQualityHistoryPublisher.sqlLiteral(runId));
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

  private boolean tableExists(String table) throws HopException {
    Database db = new Database(new LoggingObject(getClass()), variables, h2Ops);
    try {
      db.connect();
      return db.checkTableExists(DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, table);
    } finally {
      db.disconnect();
    }
  }

  private static DatabaseMeta buildH2DatabaseMeta(String name, String dbName) {
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName(name);
    databaseMeta.setDatabaseType("H2");
    databaseMeta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    // Keep the in-memory database alive after disconnect so publisher + count share state.
    databaseMeta.setDBName(dbName + ";DB_CLOSE_DELAY=-1");
    databaseMeta.setHostname("");
    databaseMeta.setPort("");
    databaseMeta.setUsername("sa");
    databaseMeta.setPassword("");
    return databaseMeta;
  }

  private static DataCatalogMeta buildCatalogMeta(Path catalogDir) {
    DataCatalogMeta meta = new DataCatalogMeta();
    meta.setName(CATALOG_CONNECTION);
    meta.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(catalogDir.toString().replace('\\', '/'));
    meta.setCatalog(fileCatalog);
    return meta;
  }
}
