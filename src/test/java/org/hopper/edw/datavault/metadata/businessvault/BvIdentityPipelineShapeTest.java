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
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateValue;
import org.apache.hop.pipeline.transforms.mergejoin.MergeJoinMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvConstraintDdlSupport;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.transform.hashkeypartition.HashKeyPartitionMeta;
import org.hopper.edw.datavault.transform.identitylookup.IdentityLookupMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvIdentityPipelineShapeTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void upsertDoesNotRewriteTheDurableKey() {
    BusinessVaultConfiguration config = new BusinessVaultConfiguration();
    TransformMeta upsert =
        BvIdentityMapPipelineSupport.addInsertUpdate(
            config, new Variables(), new TestDatabaseMeta("Vault"), "map_person", null);
    InsertUpdateMeta meta = (InsertUpdateMeta) upsert.getTransform();
    assertEquals(BvIdentityKeys.HK_RAW, meta.getInsertUpdateLookupField().getLookupKeys().get(0).getKeyLookup());
    InsertUpdateValue durable =
        meta.getInsertUpdateLookupField().getValueFields().stream()
            .filter(value -> BvIdentityKeys.HK_DURABLE.equals(value.getUpdateLookup()))
            .findFirst()
            .orElseThrow();
    assertFalse(durable.isUpdate());
    InsertUpdateValue preferred =
        meta.getInsertUpdateLookupField().getValueFields().stream()
            .filter(value -> BvIdentityKeys.PREFERRED_BK.equals(value.getUpdateLookup()))
            .findFirst()
            .orElseThrow();
    assertTrue(preferred.isUpdate());
  }

  @Test
  void sameAsSqlUsesTheLinkColumns() throws Exception {
    DataVaultModel dvModel = loadVault();
    DvLink link = new DvLink();
    link.setName("lnk_person_same_as");
    link.setTableName("lnk_person_same_as");
    dvModel.getTables().add(link);
    BvIdentityMap identityMap = new BvIdentityMap();
    identityMap.setSameAsLinkName("lnk_person_same_as");
    identityMap.setMasterHashField("person_hk_master");
    identityMap.setDuplicateHashField("person_hk_duplicate");
    identityMap.setPreferredBusinessKeyField("preferred_id");

    String sql =
        BvIdentityMapPipelineSupport.buildSameAsSql(
            identityMap, dvModel, new Variables(), new TestDatabaseMeta("Vault"));

    assertTrue(sql.contains("l.person_hk_master AS hk_master"));
    assertTrue(sql.contains("l.person_hk_duplicate AS hk_duplicate"));
    assertTrue(sql.contains("l.preferred_id AS preferred_bk"));
    assertTrue(sql.contains("FROM lnk_person_same_as l"));
  }

  @Test
  void scd2WithIdentityMapLooksUpBeforeMergeAndDoesNotPartitionOnTheRawKey() throws Exception {
    DataVaultModel dvModel = loadVault();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    BvIdentityMap identityMap = new BvIdentityMap();
    identityMap.setName("map_person");
    identityMap.setTableName("map_person");
    identityMap.setParentHubName("hub_customer");
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getTables().add(identityMap);

    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("bv_customer_scd2");
    scd2.setTableName("bv_customer_scd2");
    scd2.setBuildMode(BvScd2BuildMode.INCREMENTAL);
    scd2.setHashKeyPartitionCount(BvScd2HashPartitionCount.FOUR);
    scd2.setIdentityMapName("map_person");
    scd2.setParentHubName("hub_customer");
    scd2.setFunctionalTimestampField("x_load_ts");
    scd2.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BvScd2PipelineSupport.Scd2BuildContext ctx =
        new BvScd2PipelineSupport.Scd2BuildContext(
            scd2,
            satellite,
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            new TestDatabaseMeta("Vault"),
            "Vault",
            new TestDatabaseMeta("Vault"),
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2",
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

    PipelineMeta pipeline = BvScd2PipelineSupport.generatePipeline(ctx);
    assertTrue(
        pipeline.getTransforms().stream().anyMatch(t -> t.getTransform() instanceof IdentityLookupMeta));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof HashKeyPartitionMeta));
    assertFalse(
        pipeline.getTransforms().stream()
            .anyMatch(t -> BvScd2PipelineSupport.PARAM_WATERMARK_TRANSFORM.equals(t.getName())));
    TableInputMeta satelliteInput =
        (TableInputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> "read_sat_customer".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertFalse(satelliteInput.getSql().contains("PARTITION_NUMBER"));
    assertTrue(
        pipeline.getTransforms().stream().anyMatch(t -> "read_identity_map".equals(t.getName())));
  }

  @Test
  void multiSatelliteSelectKeepsRawAndPreferredKeys() throws Exception {
    DataVaultModel dvModel = new DataVaultModel();
    DvSatellite satA = new DvSatellite("sat_a");
    DvSatellite satB = new DvSatellite("sat_b");
    dvModel.getTables().add(satA);
    dvModel.getTables().add(satB);
    BvIdentityMap identityMap = generatedMap();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getTables().add(identityMap);
    BvScd2Table scd2 = scd2Using(identityMap);

    BvScd2PipelineSupport.Scd2BuildContext ctx =
        new BvScd2PipelineSupport.Scd2BuildContext(
            scd2,
            List.of(
                new BvScd2PipelineSupport.SatelliteLeg(
                    satA, "sat_a", "sat_a", "x_load_ts", List.of()),
                new BvScd2PipelineSupport.SatelliteLeg(
                    satB, "sat_b", "sat_b", "x_load_ts", List.of())),
            true,
            List.of(),
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            new TestDatabaseMeta("Vault"),
            "Vault",
            new TestDatabaseMeta("Vault"),
            "Vault",
            "sat_a",
            "bv_customer_scd2",
            "bv-scd2",
            "customer_hk",
            null,
            List.of(),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    PipelineMeta pipeline = BvScd2PipelineSupport.generatePipeline(ctx);
    SelectValuesMeta select =
        (SelectValuesMeta) pipeline.findTransform("select_repeated").getTransform();
    List<String> selected =
        select.getSelectOption().getSelectFields().stream().map(field -> field.getName()).toList();
    assertTrue(selected.contains(BvIdentityKeys.HK_RAW));
    assertTrue(selected.contains(BvIdentityKeys.PREFERRED_BK));
  }

  @Test
  void hubBusinessKeyJoinSortsTheRawKey() throws Exception {
    DataVaultModel dvModel = loadVault();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    BvIdentityMap identityMap = generatedMap();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getTables().add(identityMap);
    BvScd2Table scd2 = scd2Using(identityMap);
    DvHub hub = new DvHub("hub_customer");
    hub.setTableName("hub_customer");

    BvScd2PipelineSupport.Scd2BuildContext ctx =
        new BvScd2PipelineSupport.Scd2BuildContext(
            scd2,
            satellite,
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            new TestDatabaseMeta("Vault"),
            "Vault",
            new TestDatabaseMeta("Vault"),
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2",
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

    PipelineMeta pipeline = BvScd2PipelineSupport.generatePipeline(ctx);
    SortRowsMeta sort =
        (SortRowsMeta) pipeline.findTransform("sort_raw_hub_bv_customer_scd2").getTransform();
    assertEquals(BvIdentityKeys.HK_RAW, sort.getSortFields().get(0).getFieldName());
    MergeJoinMeta join = (MergeJoinMeta) pipeline.findTransform("join_hub_bk").getTransform();
    assertEquals(BvIdentityKeys.HK_RAW, join.getKeyFields1().get(0));
    assertEquals("customer_hk", join.getKeyFields2().get(0));
    assertTrue(
        pipeline.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "sort_raw_hub_bv_customer_scd2".equals(hop.getFromTransform().getName())
                        && "join_hub_bk".equals(hop.getToTransform().getName())));
  }

  @Test
  void effectivitySatelliteRequiresBothColumnsAndIsJoined() {
    DataVaultModel dvModel = new DataVaultModel();
    DvLink link = new DvLink();
    link.setName("lnk_person_same_as");
    link.setTableName("lnk_person_same_as");
    dvModel.getTables().add(link);
    DvSatellite effectivity = new DvSatellite("sat_same_as_eff");
    effectivity.setTableName("sat_same_as_eff");
    dvModel.getTables().add(effectivity);

    BvIdentityMap identityMap = generatedMap();
    identityMap.setEffectivitySatelliteName("sat_same_as_eff");
    List<ICheckResult> remarks = new ArrayList<>();
    identityMap.check(remarks, null, new Variables(), new BusinessVaultModel(), dvModel);
    assertTrue(hasError(remarks, "from and to"));

    identityMap.setEffectivityFromField("start_dt");
    identityMap.setEffectivityToField("end_dt");
    remarks.clear();
    identityMap.check(remarks, null, new Variables(), new BusinessVaultModel(), dvModel);
    assertFalse(hasError(remarks, "effectivity"));

    String sql =
        BvIdentityMapPipelineSupport.buildSameAsSql(
            identityMap, dvModel, new Variables(), new TestDatabaseMeta("Vault"));
    assertTrue(sql.contains("e.start_dt AS valid_from"));
    assertTrue(sql.contains("e.end_dt AS valid_to"));
    assertTrue(sql.contains("LEFT JOIN sat_same_as_eff e"));
  }

  @Test
  void unknownEffectivitySatelliteIsAnError() {
    BvIdentityMap identityMap = generatedMap();
    identityMap.setEffectivitySatelliteName("sat_missing");
    identityMap.setEffectivityFromField("start_dt");
    identityMap.setEffectivityToField("end_dt");
    List<ICheckResult> remarks = new ArrayList<>();
    identityMap.check(remarks, null, new Variables(), new BusinessVaultModel(), new DataVaultModel());
    assertTrue(hasError(remarks, "sat_missing"));
  }

  @Test
  void primaryKeyIsTheRawHashAndStringsHaveLengths() throws Exception {
    BusinessVaultConfiguration config = new BusinessVaultConfiguration();
    config.setGeneratePrimaryKeys(true);
    BvIdentityMap identityMap = generatedMap();
    IRowMeta layout =
        BvIdentityMapPipelineSupport.buildTargetLayout(
            identityMap, null, null, new Variables());
    assertEquals(256, layout.searchValueMeta(BvIdentityKeys.PREFERRED_BK).getLength());
    assertEquals(100, layout.searchValueMeta(BvIdentityKeys.RECORD_SOURCE).getLength());
    assertEquals(
        List.of(BvIdentityKeys.HK_RAW),
        DvConstraintDdlSupport.resolveBvPrimaryKeyColumns(
            identityMap, new BusinessVaultModel(), config, null, new Variables(), layout));
  }

  private static BvIdentityMap generatedMap() {
    BvIdentityMap identityMap = new BvIdentityMap();
    identityMap.setName("map_person");
    identityMap.setTableName("map_person");
    identityMap.setParentHubName("hub_customer");
    identityMap.setSameAsLinkName("lnk_person_same_as");
    identityMap.setMasterHashField("person_hk_master");
    identityMap.setDuplicateHashField("person_hk_duplicate");
    return identityMap;
  }

  private static BvScd2Table scd2Using(BvIdentityMap identityMap) {
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("bv_customer_scd2");
    scd2.setTableName("bv_customer_scd2");
    scd2.setIdentityMapName(identityMap.getName());
    scd2.setParentHubName(identityMap.getParentHubName());
    scd2.setFunctionalTimestampField("x_load_ts");
    return scd2;
  }

  private static boolean hasError(List<ICheckResult> remarks, String text) {
    return remarks.stream()
        .anyMatch(
            remark ->
                remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                    && remark.getText() != null
                    && remark.getText().contains(text));
  }

  private static DataVaultModel loadVault() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(dvPath.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
