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
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputSql;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmLoadStrategySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void missingStrategySerializesAsInitialReload() throws Exception {
    DimensionalModel model = modelWith(fact("f_plain", "SELECT 1 AS total_amount FROM src", null));
    String xml = XmlMetadataUtil.serializeObjectToXml(model);

    assertFalse(xml.contains("load_strategy"));
    DimensionalModel loaded = deserialize(xml);
    DmFact fact = (DmFact) loaded.findTable("f_plain");
    assertNull(fact.getLoadStrategy());
    assertEquals(
        DmLoadStrategyType.INITIAL_RELOAD, fact.copyLoadStrategyOrDefault().resolveStrategy());
  }

  @Test
  void incrementalStrategyRoundTrips() throws Exception {
    DmLoadStrategy strategy = new DmLoadStrategy();
    strategy.setStrategy(DmLoadStrategyType.INCREMENTAL);
    strategy.setBaselineKind(DmLoadBaselineKind.TARGET_COLUMN);
    strategy.setBaselineColumn("source_load_ts");
    strategy.setSentinelTimestamp("1900-01-01 00:00:00.000");
    strategy.setValueBinding(DmLoadValueBinding.JDBC_PARAMETERS);
    strategy.setLoadFromVariable("CUSTOM_FROM");
    strategy.setJdbcFromParameter("CustomFrom");
    DimensionalModel loaded =
        deserialize(
            XmlMetadataUtil.serializeObjectToXml(
                modelWith(fact("f_orders", "SELECT 1 AS total_amount FROM src", strategy))));

    DmLoadStrategy restored = ((DmFact) loaded.findTable("f_orders")).getLoadStrategy();
    assertEquals(DmLoadStrategyType.INCREMENTAL, restored.resolveStrategy());
    assertEquals("source_load_ts", restored.getBaselineColumn());
    assertEquals("1900-01-01 00:00:00.000", restored.resolveSentinel());
    assertEquals(DmLoadValueBinding.JDBC_PARAMETERS, restored.resolveValueBinding());
    assertEquals("CUSTOM_FROM", restored.resolveLoadFromVariable());
    assertEquals("CustomFrom", restored.resolveJdbcFromParameter());
  }

  @Test
  void baselineSqlQuotesIdentifiersForEachDialect() throws HopException {
    Variables variables = new Variables();
    String postgres =
        DmLoadStrategySupport.baselineSql(
            database("POSTGRESQL"), variables, "public", "f_orders", "order date");
    String mysql =
        DmLoadStrategySupport.baselineSql(
            database("MYSQL"), variables, null, "f_orders", "order date");
    String singleStore =
        DmLoadStrategySupport.baselineSql(
            database("SINGLESTORE"), variables, null, "f_orders", "order date");
    assertTrue(postgres.contains("MAX(") && postgres.contains("\"order date\""), postgres);
    assertTrue(mysql.contains("MAX(`order date`)"), mysql);
    assertTrue(singleStore.contains("MAX(`order date`)"), singleStore);
  }

  @Test
  void variableBindingBakesTheTimestampIntoSql() throws Exception {
    Variables parent = new Variables();
    parent.setVariable("DM_LOAD_FROM", "parent-value");
    Variables child = new Variables();
    child.initializeFrom(parent);
    DmFact fact =
        fact(
            "f_orders",
            "SELECT total_amount FROM src WHERE ts > CAST('${DM_LOAD_FROM}' AS TIMESTAMP)",
            incremental(DmLoadValueBinding.VARIABLES));
    DmLoadStrategySupport.Snapshot snapshot =
        new DmLoadStrategySupport.Snapshot(
            DmLoadStrategySupport.MODE_INCREMENTAL,
            "2024-01-02 03:04:05.006",
            "2024-01-03 00:00:00.000");
    DmLoadStrategySupport.applyVariables(child, fact.getLoadStrategy(), snapshot);

    PipelineMeta pipeline =
        DmLoadStrategySupport.withSnapshot(
            snapshot,
            () ->
                fact.generateUpdatePipelines(metadataProvider(), child, modelWith(fact), new Date())
                    .get(0));

    TableInputMeta tableInput = tableInput(pipeline);
    assertTrue(tableInput.getSql().contains("2024-01-02 03:04:05.006"));
    assertFalse(tableInput.getSql().contains("${DM_LOAD_FROM}"));
    assertEquals("parent-value", parent.getVariable("DM_LOAD_FROM", ""));
    assertTrue(
        pipeline.getTransforms().stream().noneMatch(t -> t.getTransform() instanceof RowGeneratorMeta));
  }

  @Test
  void jdbcBindingKeepsTheNamedParameterAndAddsATimestampRow() throws Exception {
    DmFact fact =
        fact(
            "f_orders",
            "SELECT total_amount FROM src WHERE ts > {DmLoadFrom}",
            incremental(DmLoadValueBinding.JDBC_PARAMETERS));
    DmLoadStrategySupport.Snapshot snapshot =
        new DmLoadStrategySupport.Snapshot(
            DmLoadStrategySupport.MODE_INCREMENTAL,
            "2024-05-06 07:08:09.010",
            "2024-05-07 00:00:00.000");
    Variables parent = new Variables();

    PipelineMeta pipeline =
        DmLoadStrategySupport.withSnapshot(
            snapshot,
            () ->
                fact.generateUpdatePipelines(
                        metadataProvider(), parent, modelWith(fact), new Date())
                    .get(0));

    TableInputMeta tableInput = tableInput(pipeline);
    assertTrue(tableInput.getSql().contains("{DmLoadFrom}"));
    assertTrue(tableInput.isUseNamedParameters());
    assertEquals(DmLoadStrategySupport.PARAMETER_TRANSFORM, tableInput.getLookup());
    TransformMeta generator =
        pipeline.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof RowGeneratorMeta)
            .findFirst()
            .orElseThrow();
    RowGeneratorMeta generatorMeta = (RowGeneratorMeta) generator.getTransform();
    assertEquals("1", generatorMeta.getRowLimit());
    assertEquals("Timestamp", generatorMeta.getFields().get(1).getType());
    assertEquals("2024-05-06 07:08:09.010", generatorMeta.getFields().get(1).getValue());
    assertEquals(1, pipeline.findAllPipelineHopFrom(generator).size());

    RowMeta parameters = new RowMeta();
    parameters.addValueMeta(new ValueMetaTimestamp("DmLoadFrom"));
    Date bound = new Date();
    TableInputSql.Bound prepared =
        TableInputSql.prepare(tableInput.getSql(), parameters, new Object[] {bound});
    assertTrue(prepared.getJdbcSql().contains("WHERE ts > ?"), prepared.getJdbcSql());
    assertEquals(bound, prepared.getParameterData()[0]);
    assertNull(parent.getVariable("DM_LOAD_FROM", null));
  }

  @Test
  void pipelineParametersCarryTheSnapshot() throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    DmLoadStrategy strategy = new DmLoadStrategy();
    DmLoadStrategySupport.addPipelineParameters(
        pipelineMeta,
        strategy,
        new DmLoadStrategySupport.Snapshot("INITIAL", "1900-01-01 00:00:00.000", "2024-01-01 00:00:00.000"));

    assertEquals("INITIAL", pipelineMeta.getParameterDefault("DM_LOAD_MODE"));
    assertEquals("1900-01-01 00:00:00.000", pipelineMeta.getParameterDefault("DM_LOAD_FROM"));
  }

  @Test
  void hopSlashTimestampIsRewrittenWithDashes() {
    assertEquals(
        "2024-01-01 00:00:00.000000000",
        DmLoadStrategySupport.formatValue("2024/01/01 00:00:00.000000000"));
    assertEquals(
        "2024-01-01 00:00:00.000",
        DmLoadStrategySupport.formatValue(java.sql.Timestamp.valueOf("2024-01-01 00:00:00")));
  }

  @Test
  void previewReplacesJdbcTokensOutsideQuotes() {
    DmLoadStrategy strategy = incremental(DmLoadValueBinding.JDBC_PARAMETERS);
    String sql =
        DmLoadStrategySupport.preparePreviewSql(
            "SELECT '{DmLoadFrom}' AS literal FROM src WHERE ts > {DmLoadFrom}",
            strategy,
            new DmLoadStrategySupport.Snapshot(
                "INCREMENTAL", "2024-01-02 03:04:05.006", "2024-01-03 00:00:00.000"),
            new Variables());

    assertTrue(sql.contains("'{DmLoadFrom}'"));
    assertTrue(sql.contains("CAST('2024-01-02 03:04:05.006' AS TIMESTAMP)"));
  }

  @Test
  void validationReportsReservedStrategyMissingColumnAndType2Warning() {
    DmFact reserved = fact("f_reserved", "SELECT 1 AS total_amount FROM src", new DmLoadStrategy());
    reserved.getLoadStrategy().setStrategy(DmLoadStrategyType.INITIAL_LOAD);
    assertTrue(remarks(reserved).stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));

    DmFact incremental = fact("f_orders", "SELECT 1 AS total_amount FROM src", incremental(DmLoadValueBinding.JDBC_PARAMETERS));
    incremental.getLoadStrategy().setBaselineColumn("");
    assertTrue(
        remarks(incremental).stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("baseline")));

    DmFact predicate =
        fact(
            "f_orders",
            "SELECT total_amount FROM src",
            incremental(DmLoadValueBinding.VARIABLES));
    predicate.getLoadStrategy().setBaselineColumn("source_load_ts");
    assertTrue(
        remarks(predicate).stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_WARNING));

    DmDimension dimension = new DmDimension();
    dimension.setName("d_customer");
    dimension.getAttributes().add(new DmDimensionAttribute("name", DmScdUpdatePolicy.TYPE2));
    dimension.getSourceOrDefault().setSourceSql("SELECT name FROM src WHERE ts > {DmLoadFrom}");
    dimension.setLoadStrategy(incremental(DmLoadValueBinding.JDBC_PARAMETERS));
    dimension.getLoadStrategy().setBaselineColumn("source_load_ts");
    List<ICheckResult> dimensionRemarks = new java.util.ArrayList<>();
    dimension.check(dimensionRemarks, null, new Variables(), modelWith(dimension));
    assertTrue(
        dimensionRemarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_WARNING));
  }

  private static List<ICheckResult> remarks(DmFact fact) {
    List<ICheckResult> remarks = new java.util.ArrayList<>();
    fact.check(remarks, null, new Variables(), modelWith(fact));
    return remarks;
  }

  private static TableInputMeta tableInput(PipelineMeta pipeline) {
    return (TableInputMeta)
        pipeline.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof TableInputMeta)
            .findFirst()
            .orElseThrow()
            .getTransform();
  }

  private static DmLoadStrategy incremental(DmLoadValueBinding binding) {
    DmLoadStrategy strategy = new DmLoadStrategy();
    strategy.setStrategy(DmLoadStrategyType.INCREMENTAL);
    strategy.setValueBinding(binding);
    strategy.setBaselineColumn("source_load_ts");
    return strategy;
  }

  private static DmFact fact(String name, String sql, DmLoadStrategy strategy) {
    DmFact fact = new DmFact();
    fact.setName(name);
    fact.setTableName(name);
    fact.getMeasures().add(new DmFactMeasure("total_amount"));
    fact.getSourceOrDefault().setSourceType(DmSourceType.SQL);
    fact.getSourceOrDefault().setSourceConnection("Vault");
    fact.getSourceOrDefault().setSourceSql(sql);
    fact.setLoadStrategy(strategy);
    return fact;
  }

  private static DimensionalModel modelWith(IDmTable table) {
    DimensionalModel model = new DimensionalModel();
    model.setName("orders");
    DimensionalConfiguration configuration = new DimensionalConfiguration();
    configuration.setTargetDatabase("Vault");
    model.setConfiguration(configuration);
    model.getTables().add(table);
    return model;
  }

  private static IHopMetadataProvider metadataProvider() throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return provider;
  }

  private static DatabaseMeta database(String type) throws HopException {
    return new DatabaseMeta("test", type, "Native", "", "localhost", "db", "user", "");
  }

  private static DimensionalModel deserialize(String xml) throws HopException {
    Document document = XmlHandler.loadXmlString("<dimensional-model>" + xml + "</dimensional-model>");
    Node root = document.getDocumentElement();
    DimensionalModel loaded = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(root, DimensionalModel.class, loaded, metadataProvider());
    return loaded;
  }
}
