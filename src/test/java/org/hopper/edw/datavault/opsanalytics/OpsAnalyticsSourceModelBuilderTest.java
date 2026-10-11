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
package org.hopper.edw.datavault.opsanalytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryDdlSupport;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.ProposedObjectKind;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.ProposedVaultObject;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceTableRole;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassification;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassifier;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultProposal;
import org.hopper.edw.datavault.metrics.LoadRunMetricsDdlSupport;
import org.hopper.edw.quality.history.DataQualityHistoryDdlSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpsAnalyticsSourceModelBuilderTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void catalogCoversEveryPublisherTable() {
    Set<String> physical =
        OpsAnalyticsCatalog.tables(false).stream()
            .map(OpsAnalyticsTable::physicalTable)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(new LinkedHashSet<>(OpsAnalyticsCatalog.publisherTableNames()), physical);
  }

  @Test
  void catalogColumnsMatchPublisherDdl() {
    Map<String, Set<String>> ddl = new LinkedHashMap<>();
    ddl.putAll(columnsByTable(LoadRunMetricsDdlSupport.buildPreviewStatements(null, "dv_ops")));
    ddl.putAll(
        columnsByTable(DataQualityHistoryDdlSupport.previewCreateStatements(null, "dv_ops")));
    ddl.putAll(
        columnsByTable(SchemaHarvestHistoryDdlSupport.previewCreateStatements(null, "dv_ops")));

    for (OpsAnalyticsTable table : OpsAnalyticsCatalog.tables(false)) {
      Set<String> expected = columnNames(table.columns());
      assertEquals(expected, ddl.get(table.physicalTable()), table.physicalTable());
    }
  }

  @Test
  void executionColumnsMatchRetailBootstrap() throws Exception {
    String sql = Files.readString(Path.of("retail-example/sql/create-load-metrics-tables.sql"));
    Map<String, Set<String>> ddl = columnsByTable(List.of(sql.split(";")));
    OpsAnalyticsTable execution =
        OpsAnalyticsCatalog.tables(true).stream()
            .filter(OpsAnalyticsTable::executionInfo)
            .findFirst()
            .orElseThrow();
    assertEquals(columnNames(execution.columns()), ddl.get(OpsAnalyticsCatalog.TABLE_EXECUTION));
  }

  @Test
  void querySqlIsQuotedUnion() {
    SourceModel model = OpsAnalyticsSourceModelBuilder.build();
    SourceQuery pipeline = model.findQuery(OpsAnalyticsCatalog.QUERY_PIPELINE);
    assertNotNull(pipeline);
    String sql = pipeline.getFreeSql();
    assertTrue(sql.contains("\"dv_ops\".\"load_pipeline_metric\""), sql);
    assertTrue(sql.contains("\"pipeline_name\" IS NOT NULL"), sql);
    assertTrue(sql.contains("\nUNION\n"), sql);
    assertEquals(1, pipeline.getColumns().size());
    assertTrue(pipeline.getColumns().get(0).isPrimaryKey());
  }

  @Test
  void querySqlUsesTheConnectionDialect() {
    Variables variables = new Variables();
    OpsAnalyticsRequest postgres = OpsAnalyticsRequest.defaults();
    postgres.setVariables(variables);
    postgres.setDatabaseMeta(databaseMeta("POSTGRESQL"));
    String postgresSql =
        OpsAnalyticsSourceModelBuilder.build(postgres)
            .findQuery(OpsAnalyticsCatalog.QUERY_PIPELINE)
            .getFreeSql();
    assertTrue(
        postgresSql.contains(
            postgres
                .getDatabaseMeta()
                .getQuotedSchemaTableCombination(variables, "dv_ops", "load_pipeline_metric")),
        postgresSql);
    assertTrue(
        postgresSql.contains(postgres.getDatabaseMeta().quoteField("pipeline_name") + " IS NOT NULL"),
        postgresSql);
    assertFalse(postgresSql.contains("`"), postgresSql);

    OpsAnalyticsRequest mysql = OpsAnalyticsRequest.defaults();
    mysql.setVariables(variables);
    mysql.setDatabaseMeta(databaseMeta("MYSQL"));
    mysql.getDatabaseMeta().setQuoteAllFields(true);
    String mysqlSql =
        OpsAnalyticsSourceModelBuilder.build(mysql)
            .findQuery(OpsAnalyticsCatalog.QUERY_PIPELINE)
            .getFreeSql();
    assertTrue(
        mysqlSql.contains(
            mysql
                .getDatabaseMeta()
                .getQuotedSchemaTableCombination(variables, "dv_ops", "load_pipeline_metric")),
        mysqlSql);
    assertTrue(mysqlSql.contains("`"), mysqlSql);
  }

  @Test
  void rewriteRetailQuerySqlWhenRequested() throws Exception {
    assumeTrue("1".equals(System.getenv("OPS_ANALYTICS_REWRITE_SQL")));
    Path retail = Path.of("retail-example");
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", retail.toAbsolutePath().toString());
    org.apache.hop.metadata.serializer.json.JsonMetadataProvider provider =
        new org.apache.hop.metadata.serializer.json.JsonMetadataProvider(
            org.apache.hop.core.encryption.Encr.getEncoder(),
            retail.resolve("metadata").toString(),
            variables);
    DatabaseMeta ops = provider.getSerializer(DatabaseMeta.class).load("OPS");
    OpsAnalyticsRequest request = OpsAnalyticsRequest.defaults();
    request.setConnectionName("OPS");
    request.setSchemaName("dv_ops");
    request.setVariables(variables);
    request.setDatabaseMeta(ops);
    SourceModel fresh = OpsAnalyticsSourceModelBuilder.build(request);
    String filename = retail.resolve("models/ops-analytics/ops-analytics.hsm").toString();
    SourceModel existing = SourceModelLoadSupport.load(filename, variables, provider);
    for (SourceQuery query : existing.getQueries()) {
      SourceQuery built = fresh.findQuery(query.getName());
      if (built != null) {
        query.setFreeSql(built.getFreeSql());
      }
    }
    existing.setFilename(filename);
    ModelXmlWriteSupport.writeModelXml(SourceModel.XML_TAG, existing, filename, variables);
  }

  private static DatabaseMeta databaseMeta(String pluginId) {
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName(pluginId);
    databaseMeta.setDatabaseType(pluginId);
    return databaseMeta;
  }

  @Test
  void sourceModelRoundTrips(@TempDir Path folder) throws Exception {
    SourceModel model = OpsAnalyticsSourceModelBuilder.build();
    String filename = folder.resolve("ops-analytics.hsm").toString();
    ModelXmlWriteSupport.writeModelXml(
        SourceModel.XML_TAG, model, filename, new Variables());
    SourceModel loaded = SourceModelLoadSupport.load(filename, new Variables(), null);

    assertEquals(OpsAnalyticsCatalog.tables(false).size(), loaded.getTables().size());
    assertEquals(OpsAnalyticsCatalog.queries().size(), loaded.getQueries().size());
    assertEquals(
        OpsAnalyticsCatalog.relationships(false).size(), loaded.getRelationships().size());
    SourceTable quality = loaded.findTable(OpsAnalyticsCatalog.QUALITY_RUN_CARD);
    assertNotNull(quality);
    assertEquals("quality_run", quality.getTableName());
    assertEquals("dv_ops", quality.getSchemaName());
    assertNotNull(loaded.findQuery(OpsAnalyticsCatalog.QUERY_MODEL));
  }

  @Test
  void defaultClassificationDoesNotTreatMetricChildrenAsLinks() {
    SourceModel model = OpsAnalyticsSourceModelBuilder.build();
    SourceToVaultClassification classification = SourceToVaultClassifier.classify(model);
    assertEquals(
        SourceTableRole.HUB, proposal(classification, "load_pipeline_metric").getRole());
    assertEquals(SourceTableRole.HUB, proposal(classification, "load_insight").getRole());
    assertEquals(SourceTableRole.SATELLITE, proposal(classification, "quality_alert").getRole());
  }

  @Test
  void opsProfileClassifiesHubsLinksAndQueryParents() {
    SourceModel model = OpsAnalyticsSourceModelBuilder.build();
    SourceToVaultOptions options = SourceToVaultOptions.defaults();
    options.setSingleParentCompositeKeyAsLink(true);
    SourceToVaultClassification classification =
        SourceToVaultClassifier.classify(model, null, null, options);

    for (SourceToVaultProposal row : classification.getProposals()) {
      assertNotEqualsSkip(row);
    }

    assertHub(classification, "load_run", "hub_load_run");
    assertHub(classification, OpsAnalyticsCatalog.QUALITY_RUN_CARD, "hub_quality_run");
    assertHub(classification, "workflow_load_overview", "hub_workflow_load_overview");
    assertHub(classification, "schema_harvest_run", "hub_schema_harvest_run");
    assertHub(classification, OpsAnalyticsCatalog.QUERY_PIPELINE, "hub_pipeline");
    assertHub(classification, OpsAnalyticsCatalog.QUERY_TRANSFORM, "hub_transform");
    assertHub(classification, OpsAnalyticsCatalog.QUERY_MODEL, "hub_model");
    assertHub(classification, OpsAnalyticsCatalog.QUERY_SUBJECT, "hub_subject");
    assertHub(classification, OpsAnalyticsCatalog.QUERY_RULE, "hub_rule");
    assertHub(
        classification, OpsAnalyticsCatalog.QUERY_WORKFLOW_EXECUTION, "hub_workflow_execution");

    SourceToVaultProposal alert = proposal(classification, "quality_alert");
    assertEquals(SourceTableRole.SATELLITE, alert.getRole());
    assertEquals("sat_quality_alert", alert.firstOfKind(ProposedObjectKind.SATELLITE).getName());
    assertEquals(
        "hub_quality_run", alert.firstOfKind(ProposedObjectKind.SATELLITE).getParentHubName());

    ProposedVaultObject pipelineLink = link(classification, "load_pipeline_metric");
    assertEquals("lnk_load_pipeline_metric", pipelineLink.getName());
    assertTrue(pipelineLink.getParticipatingHubNames().contains("hub_load_run"));
    assertTrue(pipelineLink.getParticipatingHubNames().contains("hub_pipeline"));
    assertTrue(pipelineLink.getDependentChildKeyColumns().isEmpty());
    assertTrue(
        proposal(classification, "load_pipeline_metric")
            .firstOfKind(ProposedObjectKind.SATELLITE)
            .getSatelliteAttributeColumns()
            .contains("duration_ms"));

    ProposedVaultObject insight = link(classification, "load_insight");
    assertEquals(List.of("insight_seq"), insight.getDependentChildKeyColumns());
    assertTrue(insight.getParticipatingHubNames().contains("hub_load_run"));

    ProposedVaultObject finding = link(classification, "quality_finding");
    assertEquals(List.of("finding_seq"), finding.getDependentChildKeyColumns());
    assertTrue(finding.getParticipatingHubNames().contains("hub_quality_run"));
    assertTrue(finding.getParticipatingHubNames().contains("hub_subject"));
    assertTrue(finding.getParticipatingHubNames().contains("hub_rule"));

    ProposedVaultObject overviewModel = link(classification, "workflow_load_overview_model");
    assertEquals(List.of("sequence_no"), overviewModel.getDependentChildKeyColumns());
    assertTrue(overviewModel.getParticipatingHubNames().contains("hub_model"));
  }

  private static void assertHub(
      SourceToVaultClassification classification, String source, String hubName) {
    SourceToVaultProposal row = proposal(classification, source);
    assertEquals(SourceTableRole.HUB, row.getRole(), source);
    assertEquals(hubName, row.firstOfKind(ProposedObjectKind.HUB).getName(), source);
  }

  private static ProposedVaultObject link(
      SourceToVaultClassification classification, String source) {
    SourceToVaultProposal row = proposal(classification, source);
    assertEquals(SourceTableRole.LINK, row.getRole(), source);
    return row.firstOfKind(ProposedObjectKind.LINK);
  }

  private static SourceToVaultProposal proposal(
      SourceToVaultClassification classification, String source) {
    SourceToVaultProposal row = classification.findProposal(source);
    assertNotNull(row, source);
    return row;
  }

  private static void assertNotEqualsSkip(SourceToVaultProposal row) {
    assertFalse(
        row.getRole() == SourceTableRole.SKIP,
        row.getSourceTableName() + " skipped: " + row.getSkipReason());
  }

  private static Set<String> columnNames(List<OpsAnalyticsColumn> columns) {
    Set<String> names = new LinkedHashSet<>();
    for (OpsAnalyticsColumn column : columns) {
      names.add(column.name());
    }
    return names;
  }

  private static Map<String, Set<String>> columnsByTable(List<String> statements) {
    Map<String, Set<String>> found = new LinkedHashMap<>();
    for (String statement : statements) {
      if (statement == null) {
        continue;
      }
      String trimmed = statement.trim();
      if (!trimmed.toUpperCase(Locale.ROOT).contains("CREATE TABLE")) {
        continue;
      }
      int paren = trimmed.indexOf('(');
      int close = trimmed.lastIndexOf(')');
      if (paren < 0 || close < paren) {
        continue;
      }
      String head = trimmed.substring(0, paren).trim();
      String tableToken = head.substring(head.lastIndexOf(' ') + 1);
      int dot = tableToken.lastIndexOf('.');
      if (dot >= 0) {
        tableToken = tableToken.substring(dot + 1);
      }
      tableToken = tableToken.replace("\"", "").replace("`", "");
      Set<String> columns = new LinkedHashSet<>();
      String body = trimmed.substring(paren + 1, close);
      for (String rawLine : body.split("\n")) {
        String line = rawLine.trim();
        if (line.startsWith(",")) {
          line = line.substring(1).trim();
        }
        if (line.isEmpty()) {
          continue;
        }
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.startsWith("PRIMARY KEY")
            || upper.startsWith("CONSTRAINT ")
            || upper.startsWith("UNIQUE ")
            || upper.startsWith("KEY ")
            || upper.startsWith("INDEX ")) {
          continue;
        }
        String name = line.split("\\s+")[0].replace(",", "");
        if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
          columns.add(name.toLowerCase(Locale.ROOT));
        }
      }
      if (!columns.isEmpty()) {
        found.put(tableToken.toLowerCase(Locale.ROOT), columns);
      }
    }
    return found;
  }

}
