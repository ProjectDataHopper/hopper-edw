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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.apache.hop.testing.DataSet;
import org.apache.hop.testing.PipelineUnitTest;
import org.apache.hop.testing.PipelineUnitTestSetLocation;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport.Scd2BuildContext;
import org.hopper.edw.datavault.transform.sqlexpression.SqlExpressionMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2CalculationUnitTestSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void namesAreStable() {
    var names = BvScd2CalculationUnitTestSupport.namesFor("customer_360_bv");
    assertEquals("bv-scd2-customer_360_bv-collapse", names.collapseDataSetName());
    assertEquals("bv-scd2-customer_360_bv-calculated", names.calculatedDataSetName());
    assertEquals("bv-scd2-customer_360_bv-calculations", names.unitTestName());
    assertEquals("test-scd2-calc-customer_360_bv", names.unitTestPipelineName());
    assertEquals("capture-scd2-calc-customer_360_bv", names.capturePipelineName());
  }

  @Test
  void unitTestPipelineLinksSqlExpressionToBusinessVaultTable() {
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename("/project/models/retail-360.hbv");
    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("customer_360_bv");
    scd2Table.getCalculations().add(new BvScd2Calculation("online_indicator", "UPPER(seg)"));

    PipelineMeta pipelineMeta =
        BvScd2CalculationUnitTestSupport.buildUnitTestPipeline(bvModel, scd2Table, new Variables());
    assertEquals(3, pipelineMeta.getTransforms().size());
    assertNotNull(
        pipelineMeta.findTransform(BvScd2CalculationUnitTestSupport.TRANSFORM_COLLAPSE_SAMPLE));
    assertNotNull(
        pipelineMeta.findTransform(BvScd2CalculationUnitTestSupport.TRANSFORM_CALCULATED_OUT));
    TransformMeta calculate =
        pipelineMeta.findTransform(BvScd2CalculationUnitTestSupport.TRANSFORM_CALCULATE);
    assertNotNull(calculate);
    assertEquals("SqlExpression", calculate.getTransformPluginId());
    SqlExpressionMeta sqlMeta = (SqlExpressionMeta) calculate.getTransform();
    assertEquals("customer_360_bv", sqlMeta.getScd2TableName());
    assertTrue(sqlMeta.getFields().isEmpty());
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    BvScd2CalculationUnitTestSupport.TRANSFORM_COLLAPSE_SAMPLE.equals(
                            hop.getFromTransform().getName())
                        && BvScd2CalculationUnitTestSupport.TRANSFORM_CALCULATE.equals(
                            hop.getToTransform().getName())));
  }

  @Test
  void identityMappingsCoverEveryField() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("a"));
    rowMeta.addValueMeta(new ValueMetaString("b"));
    var mappings = BvScd2CalculationUnitTestSupport.identityMappings(rowMeta);
    assertEquals(2, mappings.size());
    assertEquals("a", mappings.get(0).getTransformFieldName());
    assertEquals("a", mappings.get(0).getDataSetFieldName());
  }

  @Test
  void unitTestPipelineFilenameOmitsProjectHomePrefix() {
    assertEquals(
        "test/test-scd2-calc-t.hpl",
        BvScd2CalculationUnitTestSupport.pipelineFilenameRelativeToProject(
            "${PROJECT_HOME}/test/test-scd2-calc-t.hpl"));
    assertEquals(
        "test/test-scd2-calc-t.hpl",
        BvScd2CalculationUnitTestSupport.pipelineFilenameRelativeToProject(
            "test/test-scd2-calc-t.hpl"));
    RowMeta collapse = new RowMeta();
    collapse.addValueMeta(new ValueMetaString("name"));
    var names = BvScd2CalculationUnitTestSupport.namesFor("t");
    PipelineUnitTest unitTest =
        BvScd2CalculationUnitTestSupport.createUnitTest(
            names, "${PROJECT_HOME}/test/test-scd2-calc-t.hpl", collapse, collapse);
    assertEquals("test/test-scd2-calc-t.hpl", unitTest.getPipelineFilename());
  }

  @Test
  void createsMissingDataSetFolderAndCsv(@TempDir Path tempDir) throws Exception {
    Variables variables = new Variables();
    variables.setVariable(
        DataSet.VARIABLE_HOP_DATASETS_FOLDER, tempDir.resolve("datasets").toString());
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("name"));
    DataSet dataSet =
        BvScd2CalculationUnitTestSupport.createDataSet("sample-set", "desc", rowMeta, variables);

    BvScd2CalculationUnitTestSupport.ensureDataSetStorage(dataSet, rowMeta, variables);

    FileObject csv = HopVfs.getFileObject(dataSet.getActualDataSetFilename(variables), variables);
    assertTrue(csv.exists());
    assertTrue(csv.getParent().exists());
    String original = new String(csv.getContent().getByteArray(), StandardCharsets.UTF_8);

    BvScd2CalculationUnitTestSupport.ensureDataSetStorage(dataSet, rowMeta, variables);
    String again = new String(csv.getContent().getByteArray(), StandardCharsets.UTF_8);
    assertEquals(original, again);
  }

  @Test
  void unitTestLocationsUseIdentityMappings() {
    RowMeta collapse = new RowMeta();
    collapse.addValueMeta(new ValueMetaString("name"));
    RowMeta calculated = new RowMeta();
    calculated.addValueMeta(new ValueMetaString("name"));
    calculated.addValueMeta(new ValueMetaString("online_indicator"));
    var names = BvScd2CalculationUnitTestSupport.namesFor("t");
    PipelineUnitTest unitTest =
        BvScd2CalculationUnitTestSupport.createUnitTest(
            names, "${PROJECT_HOME}/test/test-scd2-calc-t.hpl", collapse, calculated);
    assertEquals("test/test-scd2-calc-t.hpl", unitTest.getPipelineFilename());
    PipelineUnitTestSetLocation input = unitTest.getInputDataSets().get(0);
    assertEquals(
        BvScd2CalculationUnitTestSupport.TRANSFORM_COLLAPSE_SAMPLE, input.getTransformName());
    assertEquals(1, input.getFieldMappings().size());
    PipelineUnitTestSetLocation golden = unitTest.getGoldenDataSets().get(0);
    assertEquals(
        BvScd2CalculationUnitTestSupport.TRANSFORM_CALCULATED_OUT, golden.getTransformName());
    assertEquals(2, golden.getFieldMappings().size());
  }

  @Test
  void capturePipelineSamplesAfterCollapseAndDropsTableOutput() throws Exception {
    Scd2BuildContext ctx = singleSatelliteContext();
    ctx.scd2Table
        .getCalculations()
        .add(new BvScd2Calculation("name_or_default", "COALESCE(name, 'x')"));
    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    BvScd2CalculationUnitTestSupport.spliceCaptureTail(
        pipelineMeta, "${PROJECT_HOME}/models/vault1.hbv", ctx.scd2Table.getName());

    assertTrue(
        pipelineMeta.getTransforms().stream()
            .noneMatch(t -> t.getTransform() instanceof TableOutputMeta));
    TransformMeta sample =
        pipelineMeta.findTransform(BvScd2CalculationUnitTestSupport.TRANSFORM_SAMPLE);
    assertNotNull(sample);
    assertEquals("ReservoirSampling", sample.getTransformPluginId());
    TransformMeta calculate =
        pipelineMeta.findTransform(BvScd2CalculationUnitTestSupport.TRANSFORM_CALCULATE);
    assertNotNull(calculate);
    SqlExpressionMeta sqlMeta = (SqlExpressionMeta) calculate.getTransform();
    assertEquals(ctx.scd2Table.getName(), sqlMeta.getScd2TableName());
    assertTrue(sqlMeta.getFields().isEmpty());
    assertTrue(hopsFromTo(pipelineMeta, GroupByMeta.class, "ReservoirSampling"));
    assertTrue(
        hopsNamed(
            pipelineMeta,
            BvScd2CalculationUnitTestSupport.TRANSFORM_SAMPLE,
            BvScd2CalculationUnitTestSupport.TRANSFORM_COLLAPSE_SAMPLE));
    assertTrue(
        hopsNamed(
            pipelineMeta,
            BvScd2CalculationUnitTestSupport.TRANSFORM_COLLAPSE_SAMPLE,
            BvScd2CalculationUnitTestSupport.TRANSFORM_CALCULATE));
    assertTrue(
        hopsNamed(
            pipelineMeta,
            BvScd2CalculationUnitTestSupport.TRANSFORM_CALCULATE,
            BvScd2CalculationUnitTestSupport.TRANSFORM_CALCULATED_OUT));
  }

  private static boolean hopsFromTo(
      PipelineMeta pipelineMeta, Class<?> fromMeta, String toPluginId) {
    for (PipelineHopMeta hop : pipelineMeta.getPipelineHops()) {
      if (fromMeta.isInstance(hop.getFromTransform().getTransform())
          && toPluginId.equals(hop.getToTransform().getTransformPluginId())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hopsNamed(PipelineMeta pipelineMeta, String from, String to) {
    return pipelineMeta.getPipelineHops().stream()
        .anyMatch(
            hop ->
                from.equals(hop.getFromTransform().getName())
                    && to.equals(hop.getToTransform().getName()));
  }

  private static Scd2BuildContext singleSatelliteContext() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");
    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");
    DatabaseMeta db = new TestDatabaseMeta("Vault");
    return new Scd2BuildContext(
        scd2Table,
        satellite,
        bvModel,
        dvModel,
        bvModel.getConfigurationOrDefault(),
        dvModel.getConfigurationOrDefault(),
        null,
        new Variables(),
        db,
        "Vault",
        db,
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
