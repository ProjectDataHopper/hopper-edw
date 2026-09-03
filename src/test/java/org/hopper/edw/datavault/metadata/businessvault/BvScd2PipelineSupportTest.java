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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.analyticquery.AnalyticQueryMeta;
import org.apache.hop.pipeline.transforms.analyticquery.QueryField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.ifnull.IfNullMeta;
import org.apache.hop.pipeline.transforms.mergejoin.MergeJoinMeta;
import org.apache.hop.pipeline.transforms.repeatfields.Repeat;
import org.apache.hop.pipeline.transforms.repeatfields.RepeatFieldsMeta;
import org.apache.hop.pipeline.transforms.repeatfields.RepeatFieldsMeta.RepeatType;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileOutputMeta;
import org.apache.hop.pipeline.transforms.update.UpdateMeta;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.DvTargetLoadMode;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport.SatelliteLeg;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport.Scd2BuildContext;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeMeta;
import org.hopper.edw.datavault.transform.sqlexpression.SqlExpressionMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2PipelineSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveFunctionalTimestampPrefersExplicitField() {
    BvScd2Table table = new BvScd2Table();
    table.setFunctionalTimestampField("effective_date");
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    String resolved =
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            table, bvConfig, dvConfig, new Variables());

    assertEquals("effective_date", resolved);
  }

  @Test
  void resolveFunctionalTimestampUsesModelDefaultBeforeLoadDateFallback() {
    BvScd2Table table = new BvScd2Table();
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    bvConfig.setFunctionalTimestampField("effective_date");
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    String resolved =
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            table, bvConfig, dvConfig, new Variables());

    assertEquals("effective_date", resolved);
  }

  @Test
  void resolveFunctionalTimestampFallsBackToDvLoadDate() {
    BvScd2Table table = new BvScd2Table();
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    String resolved =
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            table, bvConfig, dvConfig, new Variables());

    assertEquals("x_load_ts", resolved);
  }

  @Test
  void resolveValidityFieldsUseModelDefaultsWhenTableOverridesEmpty() {
    BvScd2Table table = new BvScd2Table();
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    bvConfig.setValidFromField("from_ts");
    bvConfig.setValidToField("to_ts");
    Variables variables = new Variables();

    assertEquals(
        "from_ts", BvScd2PipelineSupport.resolveValidFromField(table, bvConfig, variables));
    assertEquals("to_ts", BvScd2PipelineSupport.resolveValidToField(table, bvConfig, variables));

    table.setValidFromField("custom_from");
    table.setValidToField("custom_to");
    assertEquals(
        "custom_from", BvScd2PipelineSupport.resolveValidFromField(table, bvConfig, variables));
    assertEquals(
        "custom_to", BvScd2PipelineSupport.resolveValidToField(table, bvConfig, variables));
  }

  @Test
  void buildSatelliteTableInputSqlOrdersByGrainAndFunctionalTimestamp() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");

    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            satellite,
            new BusinessVaultModel(),
            dvModel,
            new BusinessVaultConfiguration(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2-bv_customer_scd2-sat_customer",
            "customer_hk",
            null,
            BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(ctx);

    assertTrue(sql.startsWith("SELECT "));
    assertTrue(sql.contains("customer_hk"));
    assertTrue(sql.contains("x_record_source"));
    assertTrue(sql.contains("x_load_ts"));
    assertTrue(sql.contains("FROM "));
    assertTrue(sql.contains("sat_customer"));
    assertTrue(sql.contains("ORDER BY "));
    assertTrue(sql.indexOf("ORDER BY") < sql.lastIndexOf("x_load_ts"));
    assertFalse(sql.contains(" WHERE "));
  }

  @Test
  void satelliteTableInputSqlOmitsRecordSourceWhenSatelliteDoesNotStoreIt() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.FULL_REBUILD, null);
    ctx.getSatellite().setStoreRecordSource(false);

    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(ctx);

    assertTrue(sql.contains("FROM sat_customer"));
    assertTrue(sql.contains("customer_hk"));
    assertTrue(sql.contains("x_load_ts"));
    assertFalse(sql.contains("x_record_source"));
    assertFalse(BvScd2PipelineSupport.shouldSelectPhysicalRecordSource(ctx, ctx.legs.get(0)));
  }

  @Test
  void singleSatellitePipelineInjectsRecordSourceConstantWhenNotStored() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.FULL_REBUILD, null);
    ctx.getSatellite().setStoreRecordSource(false);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);

    TableInputMeta satInput =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "read_sat_customer".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertFalse(satInput.getSql().contains("x_record_source"));

    TransformMeta constantTransform =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "record_source_sat_customer".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    ConstantMeta constantMeta = (ConstantMeta) constantTransform.getTransform();
    assertTrue(
        constantMeta.getFields().stream()
            .anyMatch(
                field ->
                    "x_record_source".equals(field.getFieldName())
                        && "sat_customer".equals(field.getValue())));

    GroupByMeta groupByMeta =
        (GroupByMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof GroupByMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals("x_record_source", groupByMeta.getAggregations().get(2).getField());

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.getSatellite(), new Variables());
    assertTrue(
        layout.getValueMetaList().stream().anyMatch(vm -> "x_record_source".equals(vm.getName())));
  }

  @Test
  void incrementalSatelliteSqlOmitsRecordSourceWhenNotStored() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null);
    ctx.getSatellite().setStoreRecordSource(false);

    String satSql = BvScd2PipelineSupport.buildSatelliteTableInputSql(ctx);
    assertTrue(satSql.contains("x_load_ts > ?"));
    assertFalse(satSql.contains("x_record_source"));

    String openSql = BvScd2PipelineSupport.buildOpenTargetTableInputSql(ctx);
    assertTrue(openSql.contains("FROM bv_customer_scd2"));
    assertTrue(openSql.contains("x_record_source"));
  }

  @Test
  void satelliteXmlRoundTripPreservesStoreRecordSourceOff() throws Exception {
    DvSatellite original = new DvSatellite("sat_customer");
    original.setStoreRecordSource(false);

    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    assertTrue(xml.contains("<storeRecordSource>N</storeRecordSource>"));

    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");
    DvSatellite restored = new DvSatellite();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DvSatellite.class, restored, null);

    assertFalse(restored.isStoreRecordSource());
  }

  @Test
  void satelliteXmlMissingStoreRecordSourceDefaultsToTrue() throws Exception {
    String xml =
        XmlHandler.aroundTag("table", "<name>sat_customer</name><tableType>SATELLITE</tableType>");
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");
    DvSatellite restored = new DvSatellite();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DvSatellite.class, restored, null);

    assertTrue(restored.isStoreRecordSource());
  }

  @Test
  void buildIncrementalSatelliteFilterSqlUsesPositionalParameter() {
    assertEquals(
        "x_load_ts > ?", BvScd2PipelineSupport.buildIncrementalSatelliteFilterSql("x_load_ts"));
  }

  @Test
  void buildLegTableInputSqlIncludesIncrementalFilterForSingleSatellite() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null);

    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(ctx);

    assertTrue(sql.contains(" WHERE "));
    // Positional ? — never embeds BV tables or dialect timestamp literals in DV SQL.
    assertTrue(sql.contains("x_load_ts > ?"));
    assertFalse(sql.contains("TIMESTAMP '"));
    assertFalse(sql.contains("CAST("));
    assertFalse(sql.contains("FROM bv_customer_scd2"));
    assertTrue(sql.indexOf(" WHERE ") < sql.indexOf(" ORDER BY "));
  }

  @Test
  void sourceQueryLegUsesOwnConnectionAndWrapsSql() throws Exception {
    Scd2BuildContext ctx =
        singleSatelliteContext(BvScd2BuildMode.FULL_REBUILD, null, "Vault", "Vault");
    BvSourceQuery source = new BvSourceQuery();
    source.setName("sat_customer_corrected");
    source.setSourceKind(BvSourceQueryKind.SQL);
    source.setSqlQuery(
        "WITH x AS (SELECT customer_hk, name, x_load_ts FROM sat_customer) SELECT * FROM x");
    source.setHashKeyField("customer_hk");
    source.setFunctionalTimestampField("x_load_ts");
    BvSourceQueryColumn nameCol = new BvSourceQueryColumn("name");
    source.getColumns().add(nameCol);
    source.getColumns().add(new BvSourceQueryColumn("customer_hk"));
    source.getColumns().add(new BvSourceQueryColumn("x_load_ts"));
    DatabaseMeta crm = new TestDatabaseMeta("CRM");
    SatelliteLeg leg =
        new SatelliteLeg(
            null,
            source,
            "sat_customer_corrected",
            "sat_customer_corrected",
            "x_load_ts",
            List.of(),
            crm,
            "CRM",
            "customer_hk",
            BvSourceQuerySqlSupport.fromClause(crm, ctx.variables, source));
    Scd2BuildContext sourceCtx =
        new Scd2BuildContext(
            ctx.scd2Table,
            List.of(leg),
            false,
            List.of(),
            ctx.bvModel,
            ctx.dvModel,
            ctx.bvConfig,
            ctx.dvConfig,
            ctx.metadataProvider,
            ctx.variables,
            ctx.sourceDatabaseMeta,
            ctx.sourceDbName,
            ctx.targetDatabaseMeta,
            ctx.targetDbName,
            "sat_customer_corrected",
            ctx.bvTargetTableName,
            ctx.pipelineName,
            ctx.hashKeyFieldName,
            ctx.drivingKeyFieldName,
            List.of("name"),
            ctx.functionalTimestampField,
            ctx.validFromField,
            ctx.validToField,
            ctx.recordSourceField,
            ctx.openStartSentinel,
            ctx.openEndSentinel,
            true);

    String sql = BvScd2PipelineSupport.buildLegTableInputSql(sourceCtx, leg);
    assertTrue(sql.contains("(WITH x AS"));
    assertTrue(sql.contains(") src"));
    assertTrue(sql.contains(" ORDER BY "));
    assertTrue(sql.indexOf("FROM (") < sql.indexOf(" ORDER BY "));
    assertFalse(sql.contains(";"));

    assertEquals("CRM", leg.connectionName(sourceCtx));
    assertFalse(BvScd2PipelineSupport.legSharesTargetConnection(sourceCtx, leg));
  }

  @Test
  void sourceQueryTableInputKeepsPhysicalHashKeyColumn() throws Exception {
    Scd2BuildContext ctx =
        singleSatelliteContext(BvScd2BuildMode.FULL_REBUILD, null, "Vault", "Vault");
    BvSourceQuery source = new BvSourceQuery();
    source.setName("sat_burger_view");
    source.setSourceKind(BvSourceQueryKind.TABLE);
    source.setTableName("vw_burger");
    source.setHashKeyField("burger_hkey");
    source.setHubHashKeyField("hub_burger_hkey");
    source.setFunctionalTimestampField("x_load_ts");
    DatabaseMeta crm = new TestDatabaseMeta("CRM");
    SatelliteLeg leg =
        new SatelliteLeg(
            null,
            source,
            "vw_burger",
            "sat_burger_view",
            "x_load_ts",
            List.of(),
            crm,
            "CRM",
            "burger_hkey",
            BvSourceQuerySqlSupport.fromClause(crm, ctx.variables, source));
    Scd2BuildContext sourceCtx =
        new Scd2BuildContext(
            ctx.scd2Table,
            List.of(leg),
            false,
            List.of(),
            ctx.bvModel,
            ctx.dvModel,
            ctx.bvConfig,
            ctx.dvConfig,
            ctx.metadataProvider,
            ctx.variables,
            ctx.sourceDatabaseMeta,
            ctx.sourceDbName,
            ctx.targetDatabaseMeta,
            ctx.targetDbName,
            "vw_burger",
            ctx.bvTargetTableName,
            ctx.pipelineName,
            "hub_burger_hkey",
            ctx.drivingKeyFieldName,
            List.of("name"),
            ctx.functionalTimestampField,
            ctx.validFromField,
            ctx.validToField,
            ctx.recordSourceField,
            ctx.openStartSentinel,
            ctx.openEndSentinel,
            true);

    String sql = BvScd2PipelineSupport.buildLegTableInputSql(sourceCtx, leg);
    assertTrue(sql.contains("burger_hkey"));
    assertFalse(sql.contains("burger_hkey AS hub_burger_hkey"));
    assertTrue(sql.contains(" ORDER BY "));
  }

  @Test
  void repeatFieldsIncludesSourceQueryMappedAttributes() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite customerSatellite = (DvSatellite) dvModel.findTable("sat_customer");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvSourceQuery source = new BvSourceQuery();
    source.setName("sq_customer_view");
    source.setSourceKind(BvSourceQueryKind.TABLE);
    source.setTableName("vw_customer");
    source.setHashKeyField("customer_hk");
    source.setFunctionalTimestampField("x_load_ts");
    source.getColumns().add(new BvSourceQueryColumn("customer_hk"));
    source.getColumns().add(new BvSourceQueryColumn("x_load_ts"));
    source.getColumns().add(new BvSourceQueryColumn("loyalty_tier"));

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("customer_bv");
    scd2Table.setTableName("customer_bv");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    scd2Table.getSourceQueryRefs().add(new BvSourceQueryRef("sq_customer_view"));
    scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sq_customer_view", "loyalty_tier", "loyalty_tier"));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");
    bvModel.getTables().add(source);

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            List.of(
                new SatelliteLeg(
                    customerSatellite,
                    "sat_customer",
                    "sat_customer",
                    "x_load_ts",
                    List.of(scd2Table.getFieldMappings().get(0))),
                new SatelliteLeg(
                    null,
                    source,
                    "vw_customer",
                    "sq_customer_view",
                    "x_load_ts",
                    List.of(scd2Table.getFieldMappings().get(1)),
                    databaseMeta,
                    "Vault",
                    "customer_hk",
                    BvSourceQuerySqlSupport.fromClause(databaseMeta, new Variables(), source))),
            true,
            List.of("customer_name", "loyalty_tier"),
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "customer_bv",
            "bv-scd2-customer_bv-customer_bv",
            "customer_hk",
            null,
            List.of("customer_name", "loyalty_tier"),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    RepeatFieldsMeta repeatMeta =
        (RepeatFieldsMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof RepeatFieldsMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();

    Repeat satelliteRepeat =
        repeatMeta.getRepeats().stream()
            .filter(repeat -> "customer_name".equals(repeat.getSourceField()))
            .findFirst()
            .orElseThrow();
    assertEquals("sat_customer", satelliteRepeat.getIndicatorValue());
    assertEquals("_r_customer_name", satelliteRepeat.getTargetField());

    Repeat sourceQueryRepeat =
        repeatMeta.getRepeats().stream()
            .filter(repeat -> "loyalty_tier".equals(repeat.getSourceField()))
            .findFirst()
            .orElseThrow();
    assertEquals("sq_customer_view", sourceQueryRepeat.getIndicatorValue());
    assertEquals("_r_loyalty_tier", sourceQueryRepeat.getTargetField());
    assertEquals(
        BvScd2PipelineSupport.SOURCE_INDICATOR_FIELD, sourceQueryRepeat.getIndicatorFieldName());
  }

  @Test
  void scd2ParentHubIsTheMergeGrainForSourceQueryHashKey() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setParentHubName("hub_customer");
    BvSourceQuery source = new BvSourceQuery();
    source.setHashKeyField("view_hk");
    source.setHubHashKeyField("customer_hk");

    assertEquals(
        "customer_hk",
        BvScd2PipelineSupport.resolveSharedHashKeyFieldName(
            scd2, List.of(), List.of(source), dvModel, new Variables()));
  }

  @Test
  void buildLegTableInputSqlCrossDbDoesNotReferenceBvTable() throws Exception {
    Scd2BuildContext ctx =
        singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null, "Vault", "BusinessVault");

    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(ctx);

    assertTrue(sql.contains("FROM sat_customer"));
    assertTrue(sql.contains("x_load_ts > ?"));
    assertFalse(sql.contains("bv_customer_scd2"));
    assertFalse(sql.contains("TIMESTAMP '"));
  }

  @Test
  void buildIncrementalWatermarkSqlUsesCustomWatermarkField() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, "event_ts");

    String sql = BvScd2PipelineSupport.buildIncrementalWatermarkSql(ctx);

    assertTrue(sql.contains("SELECT MAX(event_ts)"));
    assertTrue(sql.contains("FROM bv_customer_scd2"));
    assertFalse(sql.contains("TIMESTAMP '"));
  }

  @Test
  void buildOpenTargetTableInputSqlReadsOpenRowsForDeltaHashKeys() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null);

    String sql = BvScd2PipelineSupport.buildOpenTargetTableInputSql(ctx);

    assertTrue(sql.contains("FROM bv_customer_scd2"));
    assertTrue(sql.contains("valid_to = ?"));
    assertTrue(sql.contains("customer_hk IN ("));
    assertTrue(sql.contains("FROM sat_customer WHERE"));
    assertTrue(sql.contains("x_load_ts > ?"));
    assertFalse(sql.contains("TIMESTAMP '"));
    assertTrue(sql.contains("ORDER BY customer_hk COLLATE \"C\", x_load_ts"));
  }

  @Test
  void buildOpenTargetTableInputSqlCrossDbOmitsDeltaHashKeySubquery() throws Exception {
    Scd2BuildContext ctx =
        singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null, "Vault", "BusinessVault");

    String sql = BvScd2PipelineSupport.buildOpenTargetTableInputSql(ctx);

    assertTrue(sql.contains("FROM bv_customer_scd2"));
    assertTrue(sql.contains("valid_to = ?"));
    assertFalse(sql.contains("customer_hk IN ("));
    assertFalse(sql.contains("sat_customer"));
    assertTrue(sql.contains("ORDER BY customer_hk COLLATE \"C\", x_load_ts"));
  }

  @Test
  void buildOpenTargetCloseLookupSqlSelectsOnlyJoinKeyAndValidFrom() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null);

    String sql = BvScd2PipelineSupport.buildOpenTargetCloseLookupSql(ctx);

    assertTrue(sql.contains("SELECT customer_hk, valid_from AS _close_lookup_valid_from"));
    assertTrue(sql.contains("FROM bv_customer_scd2"));
    assertTrue(sql.contains("valid_to = ?"));
    assertTrue(sql.contains("customer_hk IN ("));
    assertTrue(sql.contains("ORDER BY customer_hk"));
    assertFalse(sql.contains("name"));
    assertFalse(sql.contains("x_record_source"));
  }

  @Test
  void buildOpenTargetCloseLookupSqlCrossDbOmitsDeltaHashKeySubquery() throws Exception {
    Scd2BuildContext ctx =
        singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null, "Vault", "BusinessVault");

    String sql = BvScd2PipelineSupport.buildOpenTargetCloseLookupSql(ctx);

    assertTrue(sql.contains("FROM bv_customer_scd2"));
    assertTrue(sql.contains("valid_to = ?"));
    assertFalse(sql.contains("customer_hk IN ("));
    assertFalse(sql.contains("sat_customer"));
  }

  @Test
  void buildIncrementalWatermarkSqlUsesMaxFromTarget() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null);

    String sql = BvScd2PipelineSupport.buildIncrementalWatermarkSql(ctx);

    assertTrue(sql.contains("SELECT MAX(x_load_ts)"));
    assertTrue(sql.contains("FROM bv_customer_scd2"));
    assertTrue(sql.contains(BvScd2PipelineSupport.INCREMENTAL_WATERMARK_FIELD));
    assertFalse(sql.contains("TIMESTAMP '"));
  }

  @Test
  void incrementalPipelineWiresWatermarkParamIntoSatelliteTableInput() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);

    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> BvScd2PipelineSupport.PARAM_WATERMARK_TRANSFORM.equals(t.getName())));
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(
                t -> BvScd2PipelineSupport.PARAM_OPEN_ROW_FILTER_TRANSFORM.equals(t.getName())));

    TableInputMeta satInput =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "read_sat_customer".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(BvScd2PipelineSupport.PARAM_WATERMARK_TRANSFORM, satInput.getLookup());
    assertTrue(satInput.getSql().contains("x_load_ts > ?"));

    TableInputMeta openInput =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "read_open_bv_customer_scd2".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(BvScd2PipelineSupport.PARAM_OPEN_ROW_FILTER_TRANSFORM, openInput.getLookup());
    assertTrue(openInput.getSql().contains("valid_to = ?"));
  }

  @Test
  void incrementalPipelineCloseUpdateUsesHashKeyAndOpenValidTo() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    UpdateMeta updateMeta =
        (UpdateMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof UpdateMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();

    assertEquals("bv_customer_scd2", updateMeta.getLookupField().getTableName());
    assertEquals(2, updateMeta.getLookupField().getLookupKeys().size());
    assertEquals("customer_hk", updateMeta.getLookupField().getLookupKeys().get(0).getKeyLookup());
    assertEquals("valid_from", updateMeta.getLookupField().getLookupKeys().get(1).getKeyLookup());
    assertEquals(
        BvScd2PipelineSupport.CLOSE_LOOKUP_VALID_FROM_FIELD,
        updateMeta.getLookupField().getLookupKeys().get(1).getKeyStream());
    assertEquals(1, updateMeta.getLookupField().getUpdateFields().size());
    assertEquals(
        "valid_to", updateMeta.getLookupField().getUpdateFields().get(0).getUpdateLookup());
    assertEquals(
        "valid_from", updateMeta.getLookupField().getUpdateFields().get(0).getUpdateStream());
    assertTrue(updateMeta.isErrorIgnored());
  }

  @Test
  void incrementalSingleSatellitePipelineAddsBaselineMergeLeg() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.INCREMENTAL, null);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    List<TransformMeta> transforms = pipelineMeta.getTransforms();

    // +2 param Generate Rows (watermark, open-row filter) vs full-rebuild chain
    assertEquals(15, transforms.size());
    assertEquals(
        3, transforms.stream().filter(t -> t.getTransform() instanceof TableInputMeta).count());
    assertTrue(transforms.stream().anyMatch(t -> "read_open_bv_customer_scd2".equals(t.getName())));
    assertTrue(
        transforms.stream()
            .anyMatch(
                t ->
                    (BvScd2PipelineSupport.CLOSE_LOOKUP_READ_PREFIX + "bv_customer_scd2")
                        .equals(t.getName())));
    assertTrue(
        transforms.stream()
            .anyMatch(
                t ->
                    ("set_" + BvScd2PipelineSupport.INCREMENTAL_WATERMARK_FIELD)
                        .equals(t.getName())));
    assertTrue(
        transforms.stream()
            .anyMatch(t -> BvScd2PipelineSupport.PARAM_WATERMARK_TRANSFORM.equals(t.getName())));
    assertTrue(
        transforms.stream()
            .anyMatch(
                t -> BvScd2PipelineSupport.PARAM_OPEN_ROW_FILTER_TRANSFORM.equals(t.getName())));
    // Parameter sources must be RowGenerator (start transform), not Constant alone
    assertTrue(
        transforms.stream()
            .filter(t -> BvScd2PipelineSupport.PARAM_WATERMARK_TRANSFORM.equals(t.getName()))
            .allMatch(
                t ->
                    t.getTransform()
                        instanceof
                        org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta));
    assertEquals(
        1,
        transforms.stream().filter(t -> t.getTransform() instanceof SortedSchemaMergeMeta).count());
    assertEquals(
        1, transforms.stream().filter(t -> t.getTransform() instanceof MergeJoinMeta).count());
    assertEquals(
        0, transforms.stream().filter(t -> t.getTransform() instanceof SortRowsMeta).count());
    assertEquals(
        2, transforms.stream().filter(t -> t.getTransform() instanceof FilterRowsMeta).count());
    assertEquals(
        1, transforms.stream().filter(t -> t.getTransform() instanceof UpdateMeta).count());

    TableOutputMeta tableOutputMeta =
        (TableOutputMeta)
            transforms.stream()
                .filter(t -> t.getTransform() instanceof TableOutputMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertFalse(tableOutputMeta.isTruncateTable());

    TransformMeta mergeTransform =
        transforms.stream()
            .filter(t -> t.getTransform() instanceof SortedSchemaMergeMeta)
            .findFirst()
            .orElseThrow();
    SortedSchemaMergeMeta mergeMeta = (SortedSchemaMergeMeta) mergeTransform.getTransform();
    assertEquals(2, mergeMeta.getInputs().size());

    TransformMeta joinCloseLookupTransform =
        transforms.stream()
            .filter(t -> BvScd2PipelineSupport.JOIN_CLOSE_LOOKUP_VALID_FROM.equals(t.getName()))
            .findFirst()
            .orElseThrow();
    MergeJoinMeta mergeJoinMeta = (MergeJoinMeta) joinCloseLookupTransform.getTransform();
    assertEquals("INNER", mergeJoinMeta.getJoinType());
    assertEquals(List.of("customer_hk"), mergeJoinMeta.getKeyFields1());
    assertEquals(List.of("customer_hk"), mergeJoinMeta.getKeyFields2());
    assertEquals("filter_new_open_rows", mergeJoinMeta.getLeftTransformName());
    assertEquals(
        BvScd2PipelineSupport.CLOSE_LOOKUP_READ_PREFIX + "bv_customer_scd2",
        mergeJoinMeta.getRightTransformName());
    long baselineReadOutgoingHops =
        pipelineMeta.getPipelineHops().stream()
            .filter(hop -> "read_open_bv_customer_scd2".equals(hop.getFromTransform().getName()))
            .count();
    assertEquals(1, baselineReadOutgoingHops);
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "read_open_bv_customer_scd2".equals(hop.getFromTransform().getName())
                        && hop.getToTransform().equals(mergeTransform)));
    assertFalse(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "read_open_bv_customer_scd2".equals(hop.getFromTransform().getName())
                        && hop.getToTransform().equals(joinCloseLookupTransform)));
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "filter_new_open_rows".equals(hop.getFromTransform().getName())
                        && hop.getToTransform().equals(joinCloseLookupTransform)));
    long closeLookupReadOutgoingHops =
        pipelineMeta.getPipelineHops().stream()
            .filter(
                hop ->
                    (BvScd2PipelineSupport.CLOSE_LOOKUP_READ_PREFIX + "bv_customer_scd2")
                        .equals(hop.getFromTransform().getName()))
            .count();
    assertEquals(1, closeLookupReadOutgoingHops);
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    (BvScd2PipelineSupport.CLOSE_LOOKUP_READ_PREFIX + "bv_customer_scd2")
                            .equals(hop.getFromTransform().getName())
                        && hop.getToTransform().equals(joinCloseLookupTransform)));
  }

  @Test
  void buildLegTableInputSqlUsesPerLegTimestampForMultiSatelliteIncremental() throws Exception {
    DataVaultModel dvModel = loadVault1ModelWithDemoSatellite();
    DvSatellite customerSatellite = (DvSatellite) dvModel.findTable("sat_customer");
    DvSatellite demoSatellite = (DvSatellite) dvModel.findTable("sat_customer_demo");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("customer_bv");
    scd2Table.setTableName("customer_bv");
    scd2Table.setBuildMode(BvScd2BuildMode.INCREMENTAL);
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer_demo", DvTableType.SATELLITE));
    scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_demo", "demo_score", "demo_score"));
    scd2Table.getSatelliteConfigs().add(new BvScd2SatelliteConfig("sat_customer_demo"));
    scd2Table.getSatelliteConfigs().get(0).setFunctionalTimestampField("effective_ts");
    scd2Table.getSatelliteConfigs().get(0).setSourceIndicatorValue("DEMO");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            List.of(
                new SatelliteLeg(
                    customerSatellite,
                    "sat_customer",
                    "sat_customer",
                    "x_load_ts",
                    List.of(scd2Table.getFieldMappings().get(0))),
                new SatelliteLeg(
                    demoSatellite,
                    "sat_customer_demo",
                    "DEMO",
                    "effective_ts",
                    List.of(scd2Table.getFieldMappings().get(1)))),
            true,
            List.of("customer_name", "demo_score"),
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "customer_bv",
            "bv-scd2-customer_bv-customer_bv",
            "customer_hk",
            null,
            List.of("customer_name", "demo_score"),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    String customerSql = BvScd2PipelineSupport.buildLegTableInputSql(ctx, ctx.legs.get(0));
    String demoSql = BvScd2PipelineSupport.buildLegTableInputSql(ctx, ctx.legs.get(1));

    assertTrue(customerSql.contains("x_load_ts > ?"));
    assertTrue(demoSql.contains("effective_ts > ?"));
    assertFalse(customerSql.contains("FROM customer_bv"));
    assertFalse(demoSql.contains("FROM customer_bv"));
    assertFalse(customerSql.contains("TIMESTAMP '"));
  }

  @Test
  void generatedPipelineContainsExpectedTransformChain() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");

    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            satellite,
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2-bv_customer_scd2-sat_customer",
            "customer_hk",
            null,
            BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    List<TransformMeta> transforms = pipelineMeta.getTransforms();

    assertEquals(5, transforms.size());
    assertTrue(transforms.get(0).getTransform() instanceof TableInputMeta);
    assertTrue(transforms.get(1).getTransform() instanceof AnalyticQueryMeta);

    AnalyticQueryMeta analyticQueryMeta = (AnalyticQueryMeta) transforms.get(1).getTransform();
    assertEquals(1, analyticQueryMeta.getGroupFields().size());
    assertEquals("customer_hk", analyticQueryMeta.getGroupFields().get(0).getFieldName());
    assertEquals(2, analyticQueryMeta.getQueryFields().size());
    assertEquals(
        QueryField.AggregateType.LAG, analyticQueryMeta.getQueryFields().get(0).getAggregateType());
    assertEquals("valid_from", analyticQueryMeta.getQueryFields().get(0).getAggregateField());
    assertEquals("x_load_ts", analyticQueryMeta.getQueryFields().get(0).getSubjectField());
    assertEquals(1, analyticQueryMeta.getQueryFields().get(0).getValueField());
    assertEquals(
        QueryField.AggregateType.LEAD,
        analyticQueryMeta.getQueryFields().get(1).getAggregateType());
    assertEquals("valid_to", analyticQueryMeta.getQueryFields().get(1).getAggregateField());
    assertEquals("x_load_ts", analyticQueryMeta.getQueryFields().get(1).getSubjectField());

    assertTrue(transforms.get(2).getTransform() instanceof IfNullMeta);
    IfNullMeta ifNullMeta = (IfNullMeta) transforms.get(2).getTransform();
    assertEquals(2, ifNullMeta.getFields().size());

    assertTrue(transforms.get(3).getTransform() instanceof GroupByMeta);
    GroupByMeta groupByMeta = (GroupByMeta) transforms.get(3).getTransform();
    assertEquals(4, groupByMeta.getAggregations().size());
    assertEquals("MIN", groupByMeta.getAggregations().get(0).getTypeLabel());
    assertEquals("MAX", groupByMeta.getAggregations().get(1).getTypeLabel());
    assertEquals("CONCAT_DISTINCT", groupByMeta.getAggregations().get(2).getTypeLabel());
    assertEquals(
        BvScd2PipelineSupport.RECORD_SOURCE_CONCAT_SEPARATOR,
        groupByMeta.getAggregations().get(2).getValue());
    assertEquals("MAX", groupByMeta.getAggregations().get(3).getTypeLabel());
    assertEquals("valid_from", groupByMeta.getAggregations().get(0).getSubject());
    assertEquals("valid_to", groupByMeta.getAggregations().get(1).getSubject());
    assertEquals("x_record_source", groupByMeta.getAggregations().get(2).getField());
    assertEquals("x_load_ts", groupByMeta.getAggregations().get(3).getField());

    assertTrue(transforms.get(4).getTransform() instanceof TableOutputMeta);
    TableOutputMeta tableOutputMeta = (TableOutputMeta) transforms.get(4).getTransform();
    assertTrue(tableOutputMeta.isTruncateTable());
    assertEquals("bv_customer_scd2", tableOutputMeta.getTableName());
  }

  @Test
  void partitionedSatelliteSqlFiltersOnHashKeyModAndDoesNotTruncate() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    dvModel.getConfigurationOrDefault().setHashKeyDataType(HashKeyDataType.HEX.name());
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault", "POSTGRESQL");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.setHashKeyPartitionCount(BvScd2HashPartitionCount.FOUR);
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            satellite,
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2-bv_customer_scd2-sat_customer",
            "customer_hk",
            null,
            BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(ctx);
    assertTrue(sql.contains(" WHERE "));
    assertTrue(sql.contains("substr(customer_hk, 1, 2)"));
    assertTrue(sql.contains("${PARTITION_COUNT}"));
    assertTrue(sql.contains("${PARTITION_NUMBER}"));
    assertTrue(sql.indexOf(" WHERE ") < sql.indexOf(" ORDER BY "));
    assertTrue(sql.contains("customer_hk COLLATE \"C\""), sql);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    TableInputMeta tableInputMeta =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .map(TransformMeta::getTransform)
                .filter(t -> t instanceof TableInputMeta)
                .findFirst()
                .orElseThrow();
    assertTrue(tableInputMeta.isVariableReplacementActive());

    TableOutputMeta tableOutputMeta =
        (TableOutputMeta)
            pipelineMeta.getTransforms().stream()
                .map(TransformMeta::getTransform)
                .filter(t -> t instanceof TableOutputMeta)
                .findFirst()
                .orElseThrow();
    assertFalse(tableOutputMeta.isTruncateTable());

    List<String> parameters = List.of(pipelineMeta.listParameters());
    assertTrue(parameters.contains(BvScd2HashPartitionSqlSupport.PARTITION_COUNT_VARIABLE));
    assertTrue(parameters.contains(BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_VARIABLE));
    assertEquals(
        "4",
        pipelineMeta.getParameterDefault(BvScd2HashPartitionSqlSupport.PARTITION_COUNT_VARIABLE));
  }

  @Test
  void binaryHashKeyOrderByDoesNotInjectStringCollation() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    dvModel.getConfigurationOrDefault().setHashKeyDataType(HashKeyDataType.BINARY.name());
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault", "POSTGRESQL");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            satellite,
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2-bv_customer_scd2-sat_customer",
            "customer_hk",
            null,
            BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(ctx);
    assertTrue(sql.contains(" ORDER BY customer_hk, x_load_ts"), sql);
    assertFalse(sql.contains("COLLATE"), sql);
  }

  @Test
  void partitionedStagingFileNameIncludesPartitionNumber() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault", "MYSQL");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.setHashKeyPartitionCount(BvScd2HashPartitionCount.FOUR);
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");
    bvModel.getConfigurationOrDefault().setTargetLoadMode(DvTargetLoadMode.STAGING_FILE.getCode());
    bvModel.getConfigurationOrDefault().setBulkLoadStagingFolder("/tmp/dv2/bulk/");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            satellite,
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2-bv_customer_scd2-sat_customer",
            "customer_hk",
            null,
            BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    TextFileOutputMeta textFileOutputMeta =
        (TextFileOutputMeta)
            pipelineMeta.getTransforms().stream()
                .map(TransformMeta::getTransform)
                .filter(t -> t instanceof TextFileOutputMeta)
                .findFirst()
                .orElseThrow();
    String fileName = textFileOutputMeta.getFileSettings().getFileName();
    assertTrue(fileName.contains("${PARTITION_NUMBER}"), fileName);
    assertTrue(fileName.contains("${Internal.Transform.CopyNr}"), fileName);
    assertEquals(
        "/tmp/dv2/bulk/bv-scd2-bv_customer_scd2-sat_customer-0-${Internal.Transform.CopyNr}",
        BvScd2PartitionWorkflowSupport.resolvePartitionedStagingFileBase(fileName, 0));
  }

  @Test
  void partitionedNativeBulkUsesBulkLoaderWhenPluginInstalled() throws Exception {
    if (!DvBulkLoadPluginSupport.isTransformPluginAvailable(
        DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID)) {
      return;
    }
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    DatabaseMeta databaseMeta =
        new DatabaseMeta("Vault", "MySQL", "Native", "", "localhost", "test", "root", "");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.setHashKeyPartitionCount(BvScd2HashPartitionCount.FOUR);
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");
    bvModel.getConfigurationOrDefault().setTargetLoadMode(DvTargetLoadMode.NATIVE_BULK.getCode());

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            satellite,
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2-bv_customer_scd2-sat_customer",
            "customer_hk",
            null,
            BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(tm -> DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID.equals(tm.getPluginId())));
    assertFalse(
        pipelineMeta.getTransforms().stream()
            .anyMatch(tm -> tm.getTransform() instanceof TableOutputMeta));
  }

  @Test
  void targetTableLayoutIncludesHashKeyAttributesAndValidityColumns() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setIncludeHashKey(true);

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            scd2Table, new BusinessVaultConfiguration(), dvModel, satellite, new Variables());

    assertEquals("customer_hk", layout.getValueMeta(0).getName());
    assertEquals("name", layout.getValueMeta(1).getName());
    assertEquals("x_record_source", layout.getValueMeta(layout.size() - 4).getName());
    assertEquals("x_load_ts", layout.getValueMeta(layout.size() - 3).getName());
    assertEquals("valid_from", layout.getValueMeta(layout.size() - 2).getName());
    assertEquals("valid_to", layout.getValueMeta(layout.size() - 1).getName());
    assertEquals(IValueMeta.TYPE_TIMESTAMP, layout.getValueMeta(layout.size() - 1).getType());
  }

  @Test
  void multiSatellitePipelineContainsMergeRepeatAndMappedCollapse() throws Exception {
    DataVaultModel dvModel = loadVault1ModelWithDemoSatellite();
    DvSatellite customerSatellite = (DvSatellite) dvModel.findTable("sat_customer");
    DvSatellite demoSatellite = (DvSatellite) dvModel.findTable("sat_customer_demo");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("customer_bv");
    scd2Table.setTableName("customer_bv");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer_demo", DvTableType.SATELLITE));
    scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_demo", "demo_score", "demo_score"));
    scd2Table.getSatelliteConfigs().add(new BvScd2SatelliteConfig("sat_customer_demo"));
    scd2Table.getSatelliteConfigs().get(0).setSourceIndicatorValue("DEMO");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            List.of(
                new SatelliteLeg(
                    customerSatellite,
                    "sat_customer",
                    "sat_customer",
                    "x_load_ts",
                    List.of(scd2Table.getFieldMappings().get(0))),
                new SatelliteLeg(
                    demoSatellite,
                    "sat_customer_demo",
                    "DEMO",
                    "x_load_ts",
                    List.of(scd2Table.getFieldMappings().get(1)))),
            true,
            List.of("customer_name", "demo_score"),
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "customer_bv",
            "bv-scd2-customer_bv-customer_bv",
            "customer_hk",
            null,
            List.of("customer_name", "demo_score"),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    String customerSql = BvScd2PipelineSupport.buildLegTableInputSql(ctx, ctx.legs.get(0));
    assertTrue(customerSql.contains("name"));
    assertFalse(customerSql.contains("demo_score"));
    assertFalse(customerSql.contains("x_record_source"));
    assertTrue(customerSql.contains("ORDER BY"));
    assertTrue(customerSql.indexOf("ORDER BY") < customerSql.lastIndexOf("x_load_ts"));

    String demoSql = BvScd2PipelineSupport.buildLegTableInputSql(ctx, ctx.legs.get(1));
    assertFalse(demoSql.contains("x_record_source"));

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    List<TransformMeta> transforms = pipelineMeta.getTransforms();

    assertEquals(13, transforms.size());
    assertEquals(
        2, transforms.stream().filter(t -> t.getTransform() instanceof TableInputMeta).count());
    for (TransformMeta transform :
        transforms.stream().filter(t -> t.getTransform() instanceof TableInputMeta).toList()) {
      assertEquals(
          GeneratedPipelineMetadataConstants.ROLE_SOURCE_READ,
          GeneratedPipelineMetadataSupport.getTransformAttribute(
              transform, GeneratedPipelineMetadataConstants.LOGICAL_ROLE));
      assertFalse(((TableInputMeta) transform.getTransform()).getSql().contains("x_record_source"));
    }
    assertEquals(
        2, transforms.stream().filter(t -> t.getTransform() instanceof ConstantMeta).count());
    assertEquals(
        3, transforms.stream().filter(t -> t.getTransform() instanceof SelectValuesMeta).count());

    TransformMeta mergeTransform =
        transforms.stream()
            .filter(t -> t.getTransform() instanceof SortedSchemaMergeMeta)
            .findFirst()
            .orElseThrow();
    SortedSchemaMergeMeta mergeMeta = (SortedSchemaMergeMeta) mergeTransform.getTransform();
    assertEquals(2, mergeMeta.getInputs().size());
    assertEquals(2, mergeMeta.getSortKeys().size());
    assertEquals("customer_hk", mergeMeta.getSortKeys().get(0).getFieldName());
    assertEquals("x_load_ts", mergeMeta.getSortKeys().get(1).getFieldName());

    TransformMeta repeatTransform =
        transforms.stream()
            .filter(t -> t.getTransform() instanceof RepeatFieldsMeta)
            .findFirst()
            .orElseThrow();
    RepeatFieldsMeta repeatMeta = (RepeatFieldsMeta) repeatTransform.getTransform();
    assertEquals(1, repeatMeta.getGroupFields().size());
    assertEquals("customer_hk", repeatMeta.getGroupFields().get(0));

    Repeat customerRepeat =
        repeatMeta.getRepeats().stream()
            .filter(
                repeat ->
                    RepeatType.CurrentWhenIndicated == repeat.getType()
                        && "customer_name".equals(repeat.getSourceField()))
            .findFirst()
            .orElseThrow();
    assertEquals("_r_customer_name", customerRepeat.getTargetField());
    assertEquals("sat_customer", customerRepeat.getIndicatorValue());

    Repeat demoRepeat =
        repeatMeta.getRepeats().stream()
            .filter(
                repeat ->
                    RepeatType.CurrentWhenIndicated == repeat.getType()
                        && "demo_score".equals(repeat.getSourceField()))
            .findFirst()
            .orElseThrow();
    assertEquals("_r_demo_score", demoRepeat.getTargetField());
    assertEquals("DEMO", demoRepeat.getIndicatorValue());
    assertEquals(BvScd2PipelineSupport.SOURCE_INDICATOR_FIELD, demoRepeat.getIndicatorFieldName());

    assertFalse(
        repeatMeta.getRepeats().stream()
            .anyMatch(repeat -> RepeatType.PreviousWhenNull == repeat.getType()));

    TransformMeta postRepeatSelectTransform =
        transforms.stream()
            .filter(t -> "select_repeated".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    SelectValuesMeta postRepeatSelectMeta =
        (SelectValuesMeta) postRepeatSelectTransform.getTransform();
    List<SelectField> postRepeatFields = postRepeatSelectMeta.getSelectOption().getSelectFields();
    assertEquals("customer_hk", postRepeatFields.get(0).getName());
    assertEquals("x_load_ts", postRepeatFields.get(1).getName());
    assertEquals(BvScd2PipelineSupport.SOURCE_INDICATOR_FIELD, postRepeatFields.get(2).getName());
    assertEquals("x_record_source", postRepeatFields.get(2).getRename());
    SelectValuesMeta customerSelectMeta =
        (SelectValuesMeta)
            transforms.stream()
                .filter(t -> "select_sat_customer".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(
        customerSelectMeta.getSelectOption().getSelectFields().stream()
            .noneMatch(field -> "x_record_source".equals(field.getName())));
    assertEquals("_r_customer_name", postRepeatFields.get(3).getName());
    assertEquals("customer_name", postRepeatFields.get(3).getRename());
    assertEquals("_r_demo_score", postRepeatFields.get(4).getName());
    assertEquals("demo_score", postRepeatFields.get(4).getRename());

    TransformMeta groupByTransform =
        transforms.stream()
            .filter(t -> t.getTransform() instanceof GroupByMeta)
            .findFirst()
            .orElseThrow();
    GroupByMeta groupByMeta = (GroupByMeta) groupByTransform.getTransform();
    assertEquals(3, groupByMeta.getGroupingFields().size());
    assertEquals("customer_hk", groupByMeta.getGroupingFields().get(0).getName());
    assertEquals("customer_name", groupByMeta.getGroupingFields().get(1).getName());
    assertEquals("demo_score", groupByMeta.getGroupingFields().get(2).getName());

    long mergeIncomingHops =
        pipelineMeta.getPipelineHops().stream()
            .filter(hop -> hop.getToTransform().equals(mergeTransform))
            .count();
    assertEquals(2, mergeIncomingHops);
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    hop.getFromTransform().getName().startsWith("select_")
                        && hop.getToTransform().equals(mergeTransform)));
  }

  @Test
  void multiSatelliteTableInputOmitsRecordSourceEvenWhenOneSatelliteStoresIt() throws Exception {
    DataVaultModel dvModel = loadVault1ModelWithDemoSatellite();
    DvSatellite customerSatellite = (DvSatellite) dvModel.findTable("sat_customer");
    DvSatellite demoSatellite = (DvSatellite) dvModel.findTable("sat_customer_demo");
    customerSatellite.setStoreRecordSource(true);
    demoSatellite.setStoreRecordSource(false);
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("customer_bv");
    scd2Table.setTableName("customer_bv");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer_demo", DvTableType.SATELLITE));
    scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_demo", "demo_score", "demo_score"));

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            List.of(
                new SatelliteLeg(
                    customerSatellite,
                    "sat_customer",
                    "sat_customer",
                    "x_load_ts",
                    List.of(scd2Table.getFieldMappings().get(0))),
                new SatelliteLeg(
                    demoSatellite,
                    "sat_customer_demo",
                    "DEMO",
                    "x_load_ts",
                    List.of(scd2Table.getFieldMappings().get(1)))),
            true,
            List.of("customer_name", "demo_score"),
            new BusinessVaultModel(),
            dvModel,
            new BusinessVaultConfiguration(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "customer_bv",
            "bv-scd2-customer_bv-customer_bv",
            "customer_hk",
            null,
            List.of("customer_name", "demo_score"),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    assertFalse(
        BvScd2PipelineSupport.buildLegTableInputSql(ctx, ctx.legs.get(0))
            .contains("x_record_source"));
    assertFalse(
        BvScd2PipelineSupport.buildLegTableInputSql(ctx, ctx.legs.get(1))
            .contains("x_record_source"));
  }

  @Test
  void calculationsInsertSqlExpressionAfterGroupBy() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.FULL_REBUILD, null);
    ctx.scd2Table
        .getCalculations()
        .add(
            new BvScd2Calculation(
                "name_or_default", "COALESCE(name, 'Default value' :> VARCHAR(720))"));

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    TransformMeta calc =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "SqlExpression".equals(t.getTransformPluginId()))
            .findFirst()
            .orElseThrow();
    assertEquals("calculate_bv_customer_scd2", calc.getName());
    SqlExpressionMeta sqlMeta = (SqlExpressionMeta) calc.getTransform();
    assertTrue(
        sqlMeta.getFields().stream().anyMatch(f -> "name_or_default".equals(f.getFieldName())));
    assertTrue(
        sqlMeta.getBusinessVaultModelFilename() == null
            || sqlMeta.getBusinessVaultModelFilename().isEmpty());
    assertTrue(sqlMeta.getScd2TableName() == null || sqlMeta.getScd2TableName().isEmpty());

    boolean hopFromGroupBy =
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    hop.getFromTransform().getTransform() instanceof GroupByMeta
                        && hop.getToTransform() == calc);
    assertTrue(hopFromGroupBy);
    assertEquals(1, calc.getCopies(new Variables()));

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.getSatellite(), new Variables());
    assertTrue(
        layout.getValueMetaList().stream().anyMatch(vm -> "name_or_default".equals(vm.getName())));
  }

  @Test
  void sqlExpressionCopiesAreSetOnGeneratedTransform() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.FULL_REBUILD, null);
    ctx.scd2Table.setSqlExpressionCopyCount(BvScd2SqlExpressionCopyCount.FOUR);
    ctx.scd2Table
        .getCalculations()
        .add(
            new BvScd2Calculation(
                "name_or_default", "COALESCE(name, 'Default value' :> VARCHAR(720))"));

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    TransformMeta calc =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "SqlExpression".equals(t.getTransformPluginId()))
            .findFirst()
            .orElseThrow();
    assertEquals(4, calc.getCopies(new Variables()));
    TransformMeta from =
        pipelineMeta.getPipelineHops().stream()
            .filter(hop -> hop.getToTransform() == calc)
            .map(hop -> hop.getFromTransform())
            .findFirst()
            .orElseThrow();
    assertTrue(from.isDistributes());
  }

  @Test
  void emptyCalculationsDoNotAddSqlExpression() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.FULL_REBUILD, null);
    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .noneMatch(t -> "SqlExpression".equals(t.getTransformPluginId())));
  }

  @Test
  void targetTableLayoutCanOmitHashKey() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setIncludeHashKey(false);

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            scd2Table, new BusinessVaultConfiguration(), dvModel, satellite, new Variables());

    assertFalse(
        layout.getValueMetaList().stream().anyMatch(vm -> "customer_hk".equals(vm.getName())));
    assertEquals("name", layout.getValueMeta(0).getName());
  }

  private static Scd2BuildContext singleSatelliteContext(
      BvScd2BuildMode buildMode, String incrementalWatermarkField) throws Exception {
    return singleSatelliteContext(buildMode, incrementalWatermarkField, "Vault", "Vault");
  }

  @Test
  void hubBusinessKeySqlSelectsHashAndBkAndOrdersByHash() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContextWithHubBusinessKeys();
    String sql = BvScd2PipelineSupport.buildHubTableInputSql(ctx);
    assertTrue(sql.contains("customer_hk"));
    assertTrue(sql.contains("customer_id"));
    assertTrue(sql.contains("FROM hub_customer"));
    assertTrue(sql.contains("ORDER BY"));
    assertFalse(sql.contains("x_load_ts"));
  }

  @Test
  void singleSatellitePipelineJoinsHubBusinessKeysAfterCollapse() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContextWithHubBusinessKeys();
    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);

    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> "read_hub_customer".equals(t.getName())));
    assertFalse(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof SortedSchemaMergeMeta));
    assertFalse(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof RepeatFieldsMeta));
    assertFalse(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> "filter_hub_bk_rows".equals(t.getName())));

    TransformMeta join =
        pipelineMeta.getTransforms().stream()
            .filter(t -> BvScd2PipelineSupport.JOIN_HUB_BK_TRANSFORM.equals(t.getName()))
            .findFirst()
            .orElseThrow();
    MergeJoinMeta joinMeta = (MergeJoinMeta) join.getTransform();
    assertEquals("LEFT OUTER", joinMeta.getJoinType());
    assertEquals("customer_hk", joinMeta.getKeyFields1().get(0));
    assertEquals("customer_hk", joinMeta.getKeyFields2().get(0));
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "read_hub_customer".equals(hop.getFromTransform().getName())
                        && BvScd2PipelineSupport.JOIN_HUB_BK_TRANSFORM.equals(
                            hop.getToTransform().getName())));
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    hop.getFromTransform().getTransform() instanceof GroupByMeta
                        && BvScd2PipelineSupport.JOIN_HUB_BK_TRANSFORM.equals(
                            hop.getToTransform().getName())));

    GroupByMeta groupByMeta =
        (GroupByMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof GroupByMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(
        groupByMeta.getAggregations().stream()
            .noneMatch(agg -> "customer_id".equals(agg.getField())));
    assertTrue(
        groupByMeta.getGroupingFields().stream()
            .noneMatch(field -> "customer_id".equals(field.getName())));

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    assertTrue(
        layout.getValueMetaList().stream().anyMatch(vm -> "customer_id".equals(vm.getName())));
  }

  @Test
  void targetLayoutOmitsCalculationOnlyMappings() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext(BvScd2BuildMode.FULL_REBUILD, null);
    ctx.scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    ctx.scd2Table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer", "email", "email", false));

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    assertTrue(
        layout.getValueMetaList().stream().anyMatch(vm -> "customer_name".equals(vm.getName())));
    assertTrue(layout.getValueMetaList().stream().noneMatch(vm -> "email".equals(vm.getName())));

    var collapse =
        BvScd2PipelineSupport.buildCollapseRowLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    assertTrue(collapse.getValueMetaList().stream().anyMatch(vm -> "email".equals(vm.getName())));
  }

  @Test
  void targetLayoutOmitsHubBusinessKeysWhenNotLoaded() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContextWithHubBusinessKeys();
    ctx.scd2Table.setLoadHubBusinessKeys(false);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> BvScd2PipelineSupport.JOIN_HUB_BK_TRANSFORM.equals(t.getName())));

    var collapse =
        BvScd2PipelineSupport.buildCollapseRowLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    assertTrue(
        collapse.getValueMetaList().stream().anyMatch(vm -> "customer_id".equals(vm.getName())));

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    assertTrue(
        layout.getValueMetaList().stream().noneMatch(vm -> "customer_id".equals(vm.getName())));
  }

  @Test
  void hubBusinessKeyIsAvailableToCalculations() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContextWithHubBusinessKeys();
    ctx.scd2Table
        .getCalculations()
        .add(
            new BvScd2Calculation(
                "id_label", "CONCAT(CAST(customer_id AS VARCHAR(20)), '-', COALESCE(name, ''))"));
    var collapse =
        BvScd2PipelineSupport.buildCollapseRowLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    assertTrue(
        collapse.getValueMetaList().stream().anyMatch(vm -> "customer_id".equals(vm.getName())));
    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    assertTrue(layout.getValueMetaList().stream().anyMatch(vm -> "id_label".equals(vm.getName())));
  }

  private static Scd2BuildContext singleSatelliteContextWithHubBusinessKeys() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    DvHub hub = dvModel.findHub("hub_customer");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.setIncludeHubBusinessKeys(true);
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    return new Scd2BuildContext(
        scd2Table,
        satellite,
        bvModel,
        dvModel,
        bvModel.getConfigurationOrDefault(),
        dvModel.getConfigurationOrDefault(),
        null,
        new Variables(),
        databaseMeta,
        "Vault",
        databaseMeta,
        "Vault",
        "sat_customer",
        "bv_customer_scd2",
        "bv-scd2-bv_customer_scd2-sat_customer",
        "customer_hk",
        null,
        BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
        "x_load_ts",
        "valid_from",
        "valid_to",
        "x_record_source",
        BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
        BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
        true,
        hub,
        "hub_customer",
        List.of("customer_id"));
  }

  private static Scd2BuildContext singleSatelliteContext(
      BvScd2BuildMode buildMode,
      String incrementalWatermarkField,
      String sourceDbName,
      String targetDbName)
      throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    DatabaseMeta sourceDatabaseMeta = new TestDatabaseMeta(sourceDbName);
    DatabaseMeta targetDatabaseMeta = new TestDatabaseMeta(targetDbName);

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setBuildMode(buildMode);
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.setIncrementalWatermarkField(incrementalWatermarkField);
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase(targetDbName);

    return new Scd2BuildContext(
        scd2Table,
        satellite,
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
        "sat_customer",
        "bv_customer_scd2",
        "bv-scd2-bv_customer_scd2-sat_customer",
        "customer_hk",
        null,
        BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
        "x_load_ts",
        "valid_from",
        "valid_to",
        "x_record_source",
        BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
        BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
        true);
  }

  private static DataVaultModel loadVault1Model() throws Exception {
    return loadVault1ModelWithDemoSatellite();
  }

  private static DataVaultModel loadVault1ModelWithDemoSatellite() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(dvPath.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);

    DvSatellite demoSatellite = new DvSatellite();
    demoSatellite.setName("sat_customer_demo");
    demoSatellite.setTableName("sat_customer_demo");
    demoSatellite.setHubName("hub_customer");
    SatelliteAttribute demoScore = new SatelliteAttribute();
    demoScore.setName("demo_score");
    demoScore.setDataType("Integer");
    demoScore.setLength("9");
    demoSatellite.getAttributes().add(demoScore);
    model.getTables().add(demoSatellite);
    return model;
  }
}
