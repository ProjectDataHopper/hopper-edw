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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvPitPipelineSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void buildPitTableInputSqlContainsSpineHubKeysAndSatellitePointer() throws Exception {
    BvPitPipelineSupport.PitBuildContext ctx = pitContext();

    String sql = BvPitPipelineSupport.buildPitTableInputSql(ctx);

    assertTrue(sql.contains("WITH earliest_satellite_load AS ("));
    assertTrue(sql.contains("bounds AS (SELECT"));
    assertTrue(sql.contains("snapshot_spine AS ("));
    assertTrue(sql.contains("generate_series(b.start_date, b.end_date, INTERVAL '1 day')"));
    assertTrue(sql.contains("hub_keys AS (SELECT DISTINCT customer_hk"));
    assertTrue(sql.contains("FROM hub_customer"));
    assertTrue(sql.contains("CROSS JOIN snapshot_spine spine"));
    assertTrue(
        sql.contains(
            "(SELECT MAX(sat.x_load_ts) FROM sat_customer sat WHERE sat.customer_hk = hk.customer_hk AND sat.x_load_ts <= spine.snapshot_date)"));
    assertTrue(sql.contains("AS sat_customer_ldts"));
    assertTrue(sql.contains("(CURRENT_DATE - INTERVAL '1 day')::date"));
    // Positional ? — never embeds PIT table or dialect timestamp literals in DV SQL.
    assertTrue(sql.contains("spine.snapshot_date > ?"));
    assertFalse(sql.contains("COALESCE((SELECT MAX(snapshot_date) FROM pit_customer)"));
    assertFalse(sql.contains("TIMESTAMP '9999"));
  }

  @Test
  void buildPitTableInputSqlCrossDbDoesNotReferencePitTable() throws Exception {
    BvPitPipelineSupport.PitBuildContext ctx = pitContext(null, "Vault", "BusinessVault");

    String sql = BvPitPipelineSupport.buildPitTableInputSql(ctx);

    assertTrue(sql.contains("FROM hub_customer"));
    assertTrue(sql.contains("FROM sat_customer"));
    assertTrue(sql.contains("spine.snapshot_date > ?"));
    assertFalse(sql.contains("FROM pit_customer"));
    assertFalse(sql.contains("COALESCE((SELECT MAX("));
  }

  @Test
  void buildPitTableInputSqlForMysqlUsesRecursiveSpineNotPostgres() throws Exception {
    BvPitPipelineSupport.PitBuildContext ctx = pitContext("MYSQL");

    String sql = BvPitPipelineSupport.buildPitTableInputSql(ctx);

    assertTrue(sql.contains("WITH RECURSIVE days AS ("));
    assertTrue(sql.contains("DATE_ADD(d.spine_day, INTERVAL 1 DAY)"));
    assertTrue(sql.contains("DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)"));
    assertTrue(sql.contains("spine.snapshot_date > ?"));
    assertFalse(sql.contains("generate_series"));
    assertFalse(sql.contains("::timestamp"));
    assertFalse(sql.contains("::date"));
    assertFalse(sql.contains("TIMESTAMP '"));
  }

  @Test
  void buildPitTableInputSqlForSingleStoreUsesNonRecursiveNumberSpine() throws Exception {
    BvPitPipelineSupport.PitBuildContext ctx = pitContext("SINGLESTORE");

    String sql = BvPitPipelineSupport.buildPitTableInputSql(ctx);

    assertFalse(sql.contains("WITH RECURSIVE"));
    assertTrue(sql.contains("day_offsets AS ("));
    assertTrue(sql.contains("DATE_ADD(b.start_date, INTERVAL o.n DAY)"));
    assertTrue(sql.contains("DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)"));
    assertTrue(sql.contains("spine.snapshot_date > ?"));
    assertTrue(sql.contains("CROSS JOIN snapshot_spine spine"));
    assertTrue(sql.contains("LEFT JOIN sat_customer sat ON"));
    assertTrue(sql.contains("GROUP BY hk.customer_hk, spine.snapshot_date"));
    assertFalse(
        sql.contains("SELECT MAX(sat.x_load_ts) FROM sat_customer sat WHERE"),
        "SingleStore cannot use correlated scalar subselects over CTE outers");
    assertFalse(sql.contains("generate_series"));
    assertFalse(sql.contains("::date"));
  }

  @Test
  void generatedPipelineUsesTableInputAndAppendTableOutput() throws Exception {
    BvPitPipelineSupport.PitBuildContext ctx = pitContext();

    PipelineMeta pipelineMeta = BvPitPipelineSupport.generatePipeline(ctx);

    assertEquals("bv-pit-pit_customer", pipelineMeta.getName());
    // watermark param Generate Rows + TableInput + TableOutput
    assertEquals(3, pipelineMeta.getTransforms().size());
    TransformMeta watermarkParam =
        pipelineMeta.getTransforms().stream()
            .filter(
                t -> BvPitPipelineSupport.PARAM_SNAPSHOT_WATERMARK_TRANSFORM.equals(t.getName()))
            .findFirst()
            .orElseThrow();
    assertTrue(
        watermarkParam.getTransform()
            instanceof org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta);

    TransformMeta tableInput =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof TableInputMeta)
            .findFirst()
            .orElseThrow();
    TableInputMeta tableInputMeta = (TableInputMeta) tableInput.getTransform();
    assertEquals("Vault", tableInputMeta.getConnection());
    assertEquals(
        BvPitPipelineSupport.PARAM_SNAPSHOT_WATERMARK_TRANSFORM, tableInputMeta.getLookup());
    assertTrue(tableInputMeta.getSql().contains("snapshot_spine"));
    assertTrue(tableInputMeta.getSql().contains("spine.snapshot_date > ?"));
    assertTrue(tableInputMeta.getSql().contains("WITH\n"));
    assertTrue(tableInputMeta.getSql().contains("earliest_satellite_load AS (\n"));
    assertTrue(tableInputMeta.getSql().contains("CROSS JOIN snapshot_spine spine"));

    TransformMeta tableOutput =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof TableOutputMeta)
            .findFirst()
            .orElseThrow();
    TableOutputMeta tableOutputMeta = (TableOutputMeta) tableOutput.getTransform();
    assertEquals("Vault", tableOutputMeta.getConnection());
    assertEquals("pit_customer", tableOutputMeta.getTableName());
    assertFalse(tableOutputMeta.isTruncateTable());
    assertEquals(3, tableOutputMeta.getFields().size());
    assertEquals("customer_hk", tableOutputMeta.getFields().get(0).getFieldDatabase());
    assertEquals("snapshot_date", tableOutputMeta.getFields().get(1).getFieldDatabase());
    assertEquals("sat_customer_ldts", tableOutputMeta.getFields().get(2).getFieldDatabase());
  }

  @Test
  void pitTableGenerateBuildPipelinesDelegatesToSupport() throws Exception {
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    DataVaultModel dvModel = loadVault1Model();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    BvPitTable pitTable = validPitTable();
    List<PipelineMeta> pipelines =
        pitTable.generateBuildPipelines(metadataProvider, new Variables(), bvModel, dvModel);

    assertEquals(1, pipelines.size());
    assertEquals("bv-pit-pit_customer", pipelines.get(0).getName());
  }

  private static BvPitPipelineSupport.PitBuildContext pitContext() throws Exception {
    return pitContext(null);
  }

  private static BvPitPipelineSupport.PitBuildContext pitContext(String pluginId) throws Exception {
    return pitContext(pluginId, "Vault", "Vault");
  }

  private static BvPitPipelineSupport.PitBuildContext pitContext(
      String pluginId, String sourceDbName, String targetDbName) throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase(targetDbName);
    DatabaseMeta sourceDatabaseMeta = new TestDatabaseMeta(sourceDbName, pluginId);
    DatabaseMeta targetDatabaseMeta = new TestDatabaseMeta(targetDbName, pluginId);

    BvPitTable pitTable = validPitTable();
    return new BvPitPipelineSupport.PitBuildContext(
        pitTable,
        BvPitLayoutSupport.resolveHubDerivative(pitTable, dvModel),
        BvPitLayoutSupport.resolveSatelliteDerivatives(pitTable, dvModel).get(0),
        pitTable.getSnapshotScheduleOrDefault(),
        bvModel,
        dvModel,
        bvModel.getConfigurationOrDefault(),
        dvModel.getConfigurationOrDefault(),
        null,
        new Variables(),
        sourceDatabaseMeta,
        sourceDbName,
        targetDatabaseMeta,
        targetDbName,
        "pit_customer",
        "bv-pit-pit_customer",
        "customer_hk",
        "snapshot_date",
        "x_load_ts",
        "sat_customer_ldts",
        "hub_customer",
        "sat_customer");
  }

  private static BvPitTable validPitTable() {
    BvPitTable table = new BvPitTable();
    table.setName("pit_customer");
    table.setTableName("pit_customer");
    table.getDerivatives().add(new BvDerivativeRef("hub_customer", DvTableType.HUB));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    return table;
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return metadataProvider;
  }

  private static DataVaultModel loadVault1Model() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(dvPath.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
