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

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.repeatfields.RepeatFieldsMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeMeta;
import org.hopper.edw.datavault.transform.survivorshipmerge.SurvivorshipMergeMeta;
import org.hopper.edw.datavault.transform.survivorshipmerge.SurvivorshipMergeRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BvSurvivorshipPipelineShapeTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void ranksReplaceRepeatWithSurvivorshipMerge() throws Exception {
    PipelineMeta pipeline = pipeline(true);
    SurvivorshipMergeMeta merge =
        (SurvivorshipMergeMeta) pipeline.findTransform("survivorship").getTransform();
    assertEquals("_bv_source", merge.getSourceField());
    assertEquals("_bv_op", merge.getOpField());
    assertEquals(2, merge.getRules().size());
    SurvivorshipMergeRule kafka = rule(merge, "KAFKA");
    SurvivorshipMergeRule oracle = rule(merge, "ORACLE");
    assertEquals("1", kafka.getRank());
    assertEquals("inherit", kafka.getNullPolicy());
    assertEquals("2", oracle.getRank());
    assertEquals("apply", oracle.getNullPolicy());
    assertFalse(has(pipeline, RepeatFieldsMeta.class));
    assertFalse(has(pipeline, SortedSchemaMergeMeta.class));
    GroupByMeta group =
        (GroupByMeta) pipeline.findTransform("collapse_bv_customer_scd2").getTransform();
    assertTrue(
        group.getGroupingFields().stream().anyMatch(field -> "x_load_ts".equals(field.getName())));
    assertFalse(
        group.getGroupingFields().stream().anyMatch(field -> "email".equals(field.getName())));
    assertTrue(
        group.getAggregations().stream()
            .anyMatch(
                aggregation ->
                    "email".equals(aggregation.getSubject())
                        && "FIRST".equals(aggregation.getTypeLabel())));
  }

  @Test
  void presentFlagAndRowOperationAreReadFromTheLeg() throws Exception {
    PipelineMeta pipeline = pipeline(true, true);
    SurvivorshipMergeMeta merge =
        (SurvivorshipMergeMeta) pipeline.findTransform("survivorship").getTransform();
    assertEquals("email_present", rule(merge, "KAFKA").getPresentFlagField());
    TableInputMeta input =
        (TableInputMeta) pipeline.findTransform("read_sat_kafka").getTransform();
    assertTrue(input.getSql().contains("email_present"));
    assertTrue(input.getSql().contains("row_op"));
    SelectValuesMeta selected =
        (SelectValuesMeta) pipeline.findTransform("select_sat_kafka").getTransform();
    assertTrue(
        selected.getSelectOption().getSelectFields().stream()
            .anyMatch(field -> "email_present".equals(field.getName())));
    SelectValuesMeta op =
        (SelectValuesMeta) pipeline.findTransform("op_sat_kafka").getTransform();
    assertEquals("_bv_op", op.getSelectOption().getSelectFields().get(0).getRename());
  }

  @Test
  void blankRanksKeepSortedSchemaMerge() throws Exception {
    PipelineMeta pipeline = pipeline(false);
    assertTrue(has(pipeline, SortedSchemaMergeMeta.class));
    assertTrue(has(pipeline, RepeatFieldsMeta.class));
    assertFalse(has(pipeline, SurvivorshipMergeMeta.class));
  }

  private static PipelineMeta pipeline(boolean ranked) throws Exception {
    return pipeline(ranked, false);
  }

  private static PipelineMeta pipeline(boolean ranked, boolean deltaColumns) throws Exception {
    DvSatellite kafka = new DvSatellite("sat_kafka");
    DvSatellite oracle = new DvSatellite("sat_oracle");
    BvScd2FieldMapping kafkaEmail = new BvScd2FieldMapping("sat_kafka", "email", "email");
    BvScd2FieldMapping oracleEmail = new BvScd2FieldMapping("sat_oracle", "email_txt", "email");
    if (ranked) {
      kafkaEmail.setRank("1");
      kafkaEmail.setNullPolicy(BvNullPolicy.INHERIT);
      oracleEmail.setRank("2");
      oracleEmail.setNullPolicy(BvNullPolicy.APPLY);
    }
    if (deltaColumns) {
      kafkaEmail.setPresentFlagField("email_present");
    }
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("bv_customer_scd2");
    scd2.setTableName("bv_customer_scd2");
    scd2.setIncludeHashKey(true);
    scd2.getFieldMappings().add(kafkaEmail);
    scd2.getFieldMappings().add(oracleEmail);
    BvScd2SatelliteConfig kafkaConfig = new BvScd2SatelliteConfig("sat_kafka");
    kafkaConfig.setNullPolicyDefault(BvNullPolicy.INHERIT);
    kafkaConfig.setOp(BvLegOperation.UPSERT);
    if (deltaColumns) {
      kafkaConfig.setOpField("row_op");
    }
    BvScd2SatelliteConfig oracleConfig = new BvScd2SatelliteConfig("sat_oracle");
    oracleConfig.setOp(BvLegOperation.UPSERT);
    scd2.getSatelliteConfigs().add(kafkaConfig);
    scd2.getSatelliteConfigs().add(oracleConfig);

    BvScd2PipelineSupport.Scd2BuildContext ctx =
        new BvScd2PipelineSupport.Scd2BuildContext(
            scd2,
            List.of(
                new BvScd2PipelineSupport.SatelliteLeg(
                    kafka, "sat_kafka", "KAFKA", "x_load_ts", List.of(kafkaEmail)),
                new BvScd2PipelineSupport.SatelliteLeg(
                    oracle, "sat_oracle", "ORACLE", "x_load_ts", List.of(oracleEmail))),
            true,
            List.of("email"),
            new BusinessVaultModel(),
            new DataVaultModel(),
            new BusinessVaultConfiguration(),
            new org.hopper.edw.datavault.metadata.DataVaultConfiguration(),
            null,
            new Variables(),
            new TestDatabaseMeta("Vault"),
            "Vault",
            new TestDatabaseMeta("Vault"),
            "Vault",
            "sat_kafka",
            "bv_customer_scd2",
            "bv-scd2",
            "customer_hk",
            null,
            List.of("email"),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);
    return BvScd2PipelineSupport.generatePipeline(ctx);
  }

  private static SurvivorshipMergeRule rule(SurvivorshipMergeMeta merge, String source) {
    return merge.getRules().stream()
        .filter(rule -> source.equals(rule.getSourceId()))
        .findFirst()
        .orElseThrow();
  }

  private static boolean has(PipelineMeta pipeline, Class<?> type) {
    return pipeline.getTransforms().stream().anyMatch(transform -> type.isInstance(transform.getTransform()));
  }
}
