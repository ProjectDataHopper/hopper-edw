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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.sql.ExecSqlMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BvSqlViewPipelineSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void viewScriptPostgresUsesCreateOrReplace() throws Exception {
    BvBusinessTable table = viewTable("satb_product_hb", "SELECT 1");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "POSTGRESQL"), new Variables(), "SELECT 1");

    assertTrue(script.singleStatement());
    assertTrue(script.sql().startsWith("CREATE OR REPLACE VIEW satb_product_hb AS"));
    assertTrue(script.sql().contains("SELECT 1"));
  }

  @Test
  void viewScriptIncludesOptionalSchema() throws Exception {
    BvBusinessTable table = viewTable("customers", "SELECT 1");
    table.setSchemaName("marts");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "POSTGRESQL"), new Variables(), "SELECT 1");
    assertTrue(script.sql().contains("marts.customers"), () -> script.sql());
  }

  @Test
  void viewScriptSqlServerUsesCreateOrAlter() throws Exception {
    BvBusinessTable table = viewTable("v1", "SELECT 1");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "MSSQLNATIVE"), new Variables(), "SELECT 1");

    assertTrue(script.singleStatement());
    assertTrue(script.sql().startsWith("CREATE OR ALTER VIEW v1 AS"));
  }

  @Test
  void viewScriptSingleStoreDropsThenCreateView() throws Exception {
    BvBusinessTable table =
        viewTable("sat_customer_hb_v", "SELECT customer_hk FROM sat_customer_hb");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table,
            new TestDatabaseMeta("Vault", "SINGLESTORE"),
            new Variables(),
            "SELECT customer_hk FROM sat_customer_hb");

    assertFalse(script.singleStatement());
    assertTrue(script.sql().contains("DROP VIEW IF EXISTS sat_customer_hb_v;"));
    assertTrue(script.sql().contains("CREATE VIEW sat_customer_hb_v AS\n"));
    assertFalse(script.sql().contains("CREATE OR REPLACE VIEW"));
    assertFalse(script.sql().contains("CREATE OR ALTER VIEW"));
  }

  @Test
  void viewScriptSqlServerRewritesAnsiTimestampLiterals() throws Exception {
    String authoringSql =
        "SELECT COALESCE(LEAD(x_load_ts) OVER (PARTITION BY customer_hk ORDER BY x_load_ts),"
            + " TIMESTAMP '9999-12-31 23:59:59') AS x_to_ts FROM sat_customer";
    BvBusinessTable table = viewTable("satb_customer_hb", authoringSql);
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "MSSQLNATIVE"), new Variables(), authoringSql);

    assertTrue(script.sql().startsWith("CREATE OR ALTER VIEW satb_customer_hb AS"));
    assertTrue(script.sql().contains("CAST('9999-12-31 23:59:59' AS datetime2)"));
    assertFalse(script.sql().contains("TIMESTAMP '9999-12-31 23:59:59'"));
  }

  @Test
  void viewScriptMysqlRewritesAnsiTimestampLiterals() throws Exception {
    String authoringSql = "SELECT TIMESTAMP '1900-01-01 00:00:00' AS open_start";
    BvBusinessTable table = viewTable("v1", authoringSql);
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "MYSQL"), new Variables(), authoringSql);

    assertTrue(script.sql().contains("CAST('1900-01-01 00:00:00' AS DATETIME)"));
    assertFalse(script.sql().contains("TIMESTAMP '1900-01-01 00:00:00'"));
  }

  @Test
  void viewScriptPostgresKeepsAnsiTimestampLiterals() throws Exception {
    String authoringSql = "SELECT TIMESTAMP '9999-12-31 23:59:59' AS open_end";
    BvBusinessTable table = viewTable("v1", authoringSql);
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "POSTGRESQL"), new Variables(), authoringSql);

    assertTrue(script.sql().contains("TIMESTAMP '9999-12-31 23:59:59'"));
  }

  @Test
  void tableScriptPostgresDropsThenCreateTableAs() throws Exception {
    BvBusinessTable table = tableMaterialization("t1", "SELECT 2");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "POSTGRESQL"), new Variables(), "SELECT 2");

    assertFalse(script.singleStatement());
    assertTrue(script.sql().contains("DROP TABLE IF EXISTS t1;"));
    assertTrue(script.sql().contains("CREATE TABLE t1 AS\nSELECT 2"));
    assertFalse(script.sql().contains("CREATE OR REPLACE TABLE"));
  }

  @Test
  void viewScriptSnowflakeUsesCreateOrReplace() throws Exception {
    BvBusinessTable table = viewTable("satb_product_hb", "SELECT 1");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "SNOWFLAKE"), new Variables(), "SELECT 1");

    assertTrue(script.singleStatement());
    assertTrue(script.sql().startsWith("CREATE OR REPLACE VIEW satb_product_hb AS"));
  }

  @Test
  void tableScriptSnowflakeUsesCreateOrReplaceTable() throws Exception {
    BvBusinessTable table = tableMaterialization("t1", "SELECT 2");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "SNOWFLAKE"), new Variables(), "SELECT 2");

    assertTrue(script.singleStatement());
    assertTrue(script.sql().startsWith("CREATE OR REPLACE TABLE t1 AS"));
  }

  @Test
  void tableScriptMysqlUsesCreateOrReplaceTable() throws Exception {
    BvBusinessTable table = tableMaterialization("t1", "SELECT 2");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table, new TestDatabaseMeta("Vault", "MYSQL"), new Variables(), "SELECT 2");

    assertTrue(script.singleStatement());
    assertTrue(script.sql().startsWith("CREATE OR REPLACE TABLE t1 AS"));
  }

  @Test
  void tableScriptSqlServerSelectInto() throws Exception {
    BvBusinessTable table = tableMaterialization("t1", "SELECT product_hk FROM sat_product");
    BvSqlViewPipelineSupport.CreateScript script =
        BvSqlViewPipelineSupport.buildCreateScript(
            table,
            new TestDatabaseMeta("Vault", "MSSQL"),
            new Variables(),
            "SELECT product_hk FROM sat_product");

    assertFalse(script.singleStatement());
    assertTrue(script.sql().contains("DROP TABLE IF EXISTS t1;"));
    assertTrue(script.sql().contains("SELECT * INTO t1 FROM ("));
    assertTrue(script.sql().contains(") AS _bv_sql_src"));
  }

  @Test
  void stripsTrailingSemicolonFromQuery() throws Exception {
    BvBusinessTable table = viewTable("v1", "SELECT 1;");
    String sql =
        BvSqlViewPipelineSupport.buildCreateStatement(table, null, new Variables(), "SELECT 1;");
    assertFalse(sql.trim().endsWith(";"));
    assertTrue(sql.contains("SELECT 1"));
  }

  @Test
  void generateBuildPipelinesProducesExecSqlWithResolvedRef() throws Exception {
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    DataVaultModel dvModel = new DataVaultModel();
    DvSatellite sat = new DvSatellite();
    sat.setName("sat_product");
    sat.setTableName("sat_product");
    sat.setTableType(DvTableType.SATELLITE);
    dvModel.getTables().add(sat);
    dvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setName("retail-sql");
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    BvBusinessTable businessTable = new BvBusinessTable();
    businessTable.setName("satb_product_hb");
    businessTable.setTableName("satb_product_hb");
    businessTable.setMaterialization(BvSqlMaterialization.VIEW);
    businessTable.setSqlQuery(
        "SELECT product_hk, x_load_ts AS x_from_ts FROM {{ ref('sat_product') }}");
    bvModel.getTables().add(businessTable);

    List<PipelineMeta> pipelines =
        BvSqlViewPipelineSupport.generateBuildPipelines(
            metadataProvider, new Variables(), bvModel, dvModel, businessTable);

    assertEquals(1, pipelines.size());
    assertEquals("bv-biz-satb_product_hb", pipelines.get(0).getName());
    assertEquals(1, pipelines.get(0).getTransforms().size());
    assertTrue(pipelines.get(0).getTransforms().get(0).getTransform() instanceof ExecSqlMeta);

    ExecSqlMeta execSql = (ExecSqlMeta) pipelines.get(0).getTransforms().get(0).getTransform();
    assertEquals("Vault", execSql.getConnection());
    assertTrue(execSql.isSingleStatement());
    assertTrue(execSql.getSql().contains("CREATE OR REPLACE VIEW satb_product_hb AS"));
    assertTrue(execSql.getSql().contains("sat_product"));
    assertFalse(execSql.getSql().contains("{{"));
  }

  @Test
  void generateBuildPipelinesRequiresSqlAndTargetDatabase() {
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");
    BvBusinessTable empty = new BvBusinessTable();
    empty.setName("x");
    empty.setTableName("x");

    assertThrows(
        HopException.class,
        () ->
            BvSqlViewPipelineSupport.generateBuildPipelines(
                testMetadataProvider(), new Variables(), bvModel, new DataVaultModel(), empty));

    BvBusinessTable withSql = viewTable("x", "SELECT 1");
    BusinessVaultModel noDb = new BusinessVaultModel();
    assertThrows(
        HopException.class,
        () ->
            BvSqlViewPipelineSupport.generateBuildPipelines(
                testMetadataProvider(), new Variables(), noDb, new DataVaultModel(), withSql));
  }

  @Test
  void businessTableGenerateBuildPipelinesDelegates() throws Exception {
    IHopMetadataProvider metadataProvider = testMetadataProvider();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");
    BvBusinessTable table = viewTable("v1", "SELECT 1 AS n");

    List<PipelineMeta> pipelines =
        table.generateBuildPipelines(
            metadataProvider, new Variables(), bvModel, new DataVaultModel());

    assertEquals(1, pipelines.size());
    assertTrue(pipelines.get(0).getTransforms().get(0).getTransform() instanceof ExecSqlMeta);
  }

  @Test
  void generateBuildDdlIsEmptySoViewsRunAfterDependentLoads() throws Exception {
    BvBusinessTable table = viewTable("v1", "SELECT 1");
    assertTrue(
        table
            .generateBuildDdl(null, new Variables(), new BusinessVaultModel(), new DataVaultModel())
            .isEmpty());
  }

  private static BvBusinessTable viewTable(String name, String sql) {
    BvBusinessTable table = new BvBusinessTable();
    table.setName(name);
    table.setTableName(name);
    table.setMaterialization(BvSqlMaterialization.VIEW);
    table.setSqlQuery(sql);
    return table;
  }

  private static BvBusinessTable tableMaterialization(String name, String sql) {
    BvBusinessTable table = viewTable(name, sql);
    table.setMaterialization(BvSqlMaterialization.TABLE);
    return table;
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta vault = new TestDatabaseMeta("Vault", "POSTGRESQL");
    metadataProvider.getSerializer(DatabaseMeta.class).save(vault);
    return metadataProvider;
  }
}
