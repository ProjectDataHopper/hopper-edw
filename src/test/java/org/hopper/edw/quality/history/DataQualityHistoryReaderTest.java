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
package org.hopper.edw.quality.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher.PublishContext;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher.PublishResult;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher.PublishStatus;
import org.hopper.edw.quality.history.DataQualityHistoryReader.FindingEntry;
import org.hopper.edw.quality.history.DataQualityHistoryReader.HistoryConnection;
import org.hopper.edw.quality.history.DataQualityHistoryReader.SubjectHistoryEntry;
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

class DataQualityHistoryReaderTest {

  private static final String CATALOG_CONNECTION = "quality-history-reader-catalog";
  private static final String OPS_NAME = "OPS";
  private static final String SUBJECT_KEY = "hop/retail-example/sources/CRM-customer";

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
    Path catalogDir = Files.createTempDirectory("quality-history-reader-catalog");
    metadataProvider.getSerializer(DataCatalogMeta.class).save(buildCatalogMeta(catalogDir));

    h2Ops = buildH2DatabaseMeta(OPS_NAME, "mem:quality_hist_reader_" + UUID.randomUUID());
    metadataProvider.getSerializer(DatabaseMeta.class).save(h2Ops);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void resolveProjectKeyFromProjectHomeBasename() {
    assertEquals("retail-example", DataQualityHistoryReader.resolveProjectKey(variables));
    assertEquals(
        "hop/retail-example/operations", DataQualityHistoryReader.operationsNamespace(variables));

    Variables empty = new Variables();
    assertEquals("project", DataQualityHistoryReader.resolveProjectKey(empty));
    assertEquals("hop/project/operations", DataQualityHistoryReader.operationsNamespace(empty));
  }

  @Test
  void resolveConnectionPrefersQualityHistoryVariable() throws Exception {
    variables.setVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_DATABASE, "OPS_FROM_VAR");
    variables.setVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_SCHEMA, "custom_ops");

    HistoryConnection connection =
        DataQualityHistoryReader.resolveConnection(CATALOG_CONNECTION, variables, metadataProvider);
    assertNotNull(connection);
    assertEquals("OPS_FROM_VAR", connection.databaseMetaName());
    assertEquals("custom_ops", connection.schemaName());
    assertEquals(DataQualityHistoryReader.RESOLVED_FROM_VARIABLE, connection.resolvedFrom());
  }

  @Test
  void resolveConnectionDefaultsSchemaWhenOnlyDatabaseVariableSet() throws Exception {
    variables.setVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_DATABASE, OPS_NAME);

    HistoryConnection connection =
        DataQualityHistoryReader.resolveConnection(CATALOG_CONNECTION, variables, metadataProvider);
    assertNotNull(connection);
    assertEquals(OPS_NAME, connection.databaseMetaName());
    assertEquals(DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, connection.schemaName());
  }

  @Test
  void resolveConnectionFromQualityRunCatalogDefinition() throws Exception {
    String namespace = DataQualityHistoryPublisher.operationsNamespace(variables);
    upsertOpsDef(namespace, DataQualityHistoryPublisher.TABLE_QUALITY_RUN, OPS_NAME, "dv_ops");

    HistoryConnection connection =
        DataQualityHistoryReader.resolveConnection(CATALOG_CONNECTION, variables, metadataProvider);
    assertNotNull(connection);
    assertEquals(OPS_NAME, connection.databaseMetaName());
    assertEquals("dv_ops", connection.schemaName());
    assertEquals(DataQualityHistoryReader.RESOLVED_FROM_QUALITY_RUN, connection.resolvedFrom());
  }

  @Test
  void resolveConnectionFallsBackToLoadRunCatalogDefinition() throws Exception {
    String namespace = DataQualityHistoryPublisher.operationsNamespace(variables);
    upsertOpsDef(namespace, "load_run", "OPS_LOAD", "ops_schema");

    HistoryConnection connection =
        DataQualityHistoryReader.resolveConnection(CATALOG_CONNECTION, variables, metadataProvider);
    assertNotNull(connection);
    assertEquals("OPS_LOAD", connection.databaseMetaName());
    assertEquals("ops_schema", connection.schemaName());
    assertEquals(DataQualityHistoryReader.RESOLVED_FROM_LOAD_RUN, connection.resolvedFrom());
  }

  @Test
  void resolveConnectionReturnsNullWhenUnconfigured() throws Exception {
    assertNull(
        DataQualityHistoryReader.resolveConnection(
            CATALOG_CONNECTION, variables, metadataProvider));
  }

  @Test
  void listSubjectHistoryAndFindingsAfterPublish() throws Exception {
    DataQualityReport report = sampleReport();
    PublishContext context =
        new PublishContext(
            OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, null, false, true, true);
    PublishResult result =
        DataQualityHistoryPublisher.publish(
            LogChannel.GENERAL,
            report,
            context,
            "load-1",
            "wf-test",
            "exec-1",
            variables,
            metadataProvider);
    assertEquals(PublishStatus.INSERTED, result.status(), result.message());

    List<SubjectHistoryEntry> history =
        DataQualityHistoryReader.listSubjectHistory(
            h2Ops, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, SUBJECT_KEY, variables, 50);
    assertEquals(1, history.size());
    SubjectHistoryEntry entry = history.get(0);
    assertEquals(report.getRunId(), entry.qualityRunId());
    assertEquals(QualityLifecycle.PRE_UPDATE.name(), entry.lifecycle());
    assertEquals(100L, entry.rowCount());
    assertEquals(1L, entry.findingCount());
    assertEquals(1L, entry.blockingCount());
    assertEquals("load-1", entry.loadId());
    // captured_at may be null under H2+Hop TIMESTAMP mapping; non-null on Postgres/MySQL ops.

    List<FindingEntry> findings =
        DataQualityHistoryReader.listFindings(
            h2Ops,
            DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME,
            report.getRunId(),
            SUBJECT_KEY,
            variables);
    assertEquals(1, findings.size());
    FindingEntry finding = findings.get(0);
    assertEquals("customer_id", finding.fieldName());
    assertEquals(QualitySeverity.BLOCKING.name(), finding.severity());
    assertTrue(finding.message().contains("nulls"));
  }

  @Test
  void listSubjectHistoryEmptyForUnknownSubject() throws Exception {
    DataQualityReport report = sampleReport();
    PublishContext context =
        new PublishContext(
            OPS_NAME, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, null, false, true, true);
    assertEquals(
        PublishStatus.INSERTED,
        DataQualityHistoryPublisher.publish(
                LogChannel.GENERAL,
                report,
                context,
                "load-1",
                "wf",
                "exec",
                variables,
                metadataProvider)
            .status());

    List<SubjectHistoryEntry> history =
        DataQualityHistoryReader.listSubjectHistory(
            h2Ops,
            DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME,
            "hop/other/subject",
            variables,
            50);
    assertTrue(history.isEmpty());
  }

  @Test
  void listSubjectHistoryThrowsWhenTablesMissing() {
    assertThrows(
        DataQualityHistoryReader.QualityHistoryTablesMissingException.class,
        () ->
            DataQualityHistoryReader.listSubjectHistory(
                h2Ops,
                DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME,
                SUBJECT_KEY,
                variables,
                50));
  }

  private void upsertOpsDef(String namespace, String name, String databaseName, String schema)
      throws HopException {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, name));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("test ops def " + name);
    PhysicalTableRef table = new PhysicalTableRef();
    table.setDatabaseMetaName(databaseName);
    table.setSchemaName(schema);
    table.setTableName(name);
    definition.setPhysicalTable(table);
    RecordOrigin origin = new RecordOrigin();
    origin.setCreatedAt(new java.util.Date());
    definition.setOrigin(origin);
    definition.validate();
    RecordDefinitionRegistry.getInstance()
        .upsert(CATALOG_CONNECTION, definition, variables, metadataProvider);
  }

  private DataQualityReport sampleReport() {
    DataQualityReport report = new DataQualityReport(QualityLifecycle.PRE_UPDATE);
    report.addSubjectKey(SUBJECT_KEY);

    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey(SUBJECT_KEY);
    profile.setEvaluationMode(QualityEvaluationMode.SQL_PUSHDOWN);
    profile.setLifecycle(QualityLifecycle.PRE_UPDATE);
    profile.setRowCount(100);
    profile.setRowCountExact(true);
    profile.field("customer_id").observeValue("C1", "C1", 100);
    profile.field("customer_id").addNullCount(2);
    report.putProfile(SUBJECT_KEY, profile);

    report.addFinding(
        DataQualityFinding.builder()
            .subjectKey(SUBJECT_KEY)
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
