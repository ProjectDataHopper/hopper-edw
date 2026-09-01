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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.DvTargetLoadMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2TableTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultsToFullRebuild() {
    BvScd2Table table = new BvScd2Table();
    assertEquals(BvScd2BuildMode.FULL_REBUILD, table.getBuildModeOrDefault());
    assertFalse(table.isIncrementalBuild());
    assertEquals(BvScd2HashPartitionCount.NONE, table.getHashKeyPartitionCountOrDefault());
    assertFalse(table.isHashKeyPartitioned());
  }

  @Test
  void resolveIncrementalWatermarkPrefersExplicitOverride() {
    BvScd2Table table = new BvScd2Table();
    table.setIncrementalWatermarkField("updated_at");
    table.setFunctionalTimestampField("x_load_ts");

    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    assertEquals(
        "updated_at", table.resolveIncrementalWatermarkField(bvConfig, dvConfig, new Variables()));
  }

  @Test
  void resolveIncrementalWatermarkFallsBackToFunctionalTimestamp() {
    BvScd2Table table = new BvScd2Table();
    table.setFunctionalTimestampField("effective_date");

    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    assertEquals(
        "effective_date",
        table.resolveIncrementalWatermarkField(bvConfig, dvConfig, new Variables()));
  }

  @Test
  void xmlRoundTripPreservesBuildModeAndWatermarkField() throws Exception {
    BvScd2Table original = new BvScd2Table();
    original.setName("customer_bv");
    original.setTableName("customer_bv");
    original.setBuildMode(BvScd2BuildMode.INCREMENTAL);
    original.setFunctionalTimestampField("x_load_ts");
    original.setIncrementalWatermarkField("event_ts");
    original.setHashKeyPartitionCount(BvScd2HashPartitionCount.EIGHT);
    original.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");

    BvScd2Table restored = new BvScd2Table();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvScd2Table.class, restored, null);

    assertEquals(BvScd2BuildMode.INCREMENTAL, restored.getBuildModeOrDefault());
    assertEquals("event_ts", restored.getIncrementalWatermarkField());
    assertEquals(BvScd2HashPartitionCount.EIGHT, restored.getHashKeyPartitionCountOrDefault());
    assertTrue(restored.isIncrementalBuild());
  }

  @Test
  void xmlRoundTripPreservesCalculationsAndTests() throws Exception {
    BvScd2Table original = new BvScd2Table();
    original.setName("customer_bv");
    BvScd2Calculation calculation =
        new BvScd2Calculation(
            "x_date", "CASE WHEN deleted_flag = 'Y' THEN NULL ELSE event_date END");
    original.getCalculations().add(calculation);
    BvScd2CalculationTestCase testCase = new BvScd2CalculationTestCase("deleted-nulls-date");
    testCase.getInputs().add(new BvScd2NamedValue("deleted_flag", "Y"));
    testCase.getExpected().add(new BvScd2NamedValue("x_date", ""));
    original.getCalculationTests().add(testCase);
    original
        .getCollapseTests()
        .add(
            new BvScd2CollapseTestCase(
                "wave1", "${PROJECT_HOME}/in.csv", "${PROJECT_HOME}/out.csv"));

    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");
    BvScd2Table restored = new BvScd2Table();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvScd2Table.class, restored, null);

    assertEquals(1, restored.getCalculations().size());
    assertEquals("x_date", restored.getCalculations().get(0).getTargetFieldName());
    assertEquals(1, restored.getCalculationTests().size());
    assertEquals("deleted-nulls-date", restored.getCalculationTests().get(0).getName());
    assertEquals(1, restored.getCollapseTests().size());
    assertEquals("wave1", restored.getCollapseTests().get(0).getName());
  }

  @Test
  void hashKeyPartitionWithIncrementalIsError() {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_bv");
    table.setTableName("customer_bv");
    table.setBuildMode(BvScd2BuildMode.INCREMENTAL);
    table.setFunctionalTimestampField("x_load_ts");
    table.setHashKeyPartitionCount(BvScd2HashPartitionCount.FOUR);
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    List<ICheckResult> remarks = check(table, new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("hash-key partitions")));
  }

  @Test
  void hashKeyPartitionAllowsStagingFileWhenCatalogIsUnavailable() {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_bv");
    table.setTableName("customer_bv");
    table.setFunctionalTimestampField("x_load_ts");
    table.setHashKeyPartitionCount(BvScd2HashPartitionCount.SIXTEEN);
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    List<ICheckResult> remarks = new ArrayList<>();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetLoadMode(DvTargetLoadMode.STAGING_FILE.getCode());
    table.check(remarks, null, new Variables(), bvModel, new DataVaultModel());

    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getText() != null
                        && r.getText().contains("Staging file")
                        && r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void incrementalMultiSatelliteWithoutSourceIndicatorsProducesWarning() throws Exception {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_360_bv");
    table.setTableName("customer_360_bv");
    table.setBuildMode(BvScd2BuildMode.INCREMENTAL);
    table.setFunctionalTimestampField("x_load_ts");
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_demo", DvTableType.SATELLITE));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_contact", DvTableType.SATELLITE));
    table.getSatelliteConfigs().add(new BvScd2SatelliteConfig("sat_customer_demo"));
    table.getSatelliteConfigs().get(0).setSourceIndicatorValue("DEMO");

    List<ICheckResult> remarks = check(table, new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_WARNING
                        && r.getText() != null
                        && r.getText().contains("source indicator")));
  }

  private static List<ICheckResult> check(BvScd2Table table, DataVaultModel dvModel) {
    List<ICheckResult> remarks = new ArrayList<>();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setOpenEndSentinel("9999-12-31 23:59:59");
    table.check(remarks, null, new Variables(), bvModel, dvModel);
    return remarks;
  }
}
