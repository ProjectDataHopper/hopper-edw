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
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.repeatfields.RepeatFieldsMeta;
import org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport.Scd2BuildContext;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2MultiSatelliteFixtureTest {

  private static final Path DV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hdv")
          .toAbsolutePath()
          .normalize();
  private static final Path BV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hbv")
          .toAbsolutePath()
          .normalize();
  private static final Path EXTERNAL_DV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360-external.hdv")
          .toAbsolutePath()
          .normalize();
  private static final Path EXTERNAL_BV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360-external.hbv")
          .toAbsolutePath()
          .normalize();
  private static final Path PROJECT_HOME = Path.of("project").toAbsolutePath().normalize();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void externalCustomer360ModelsValidateAndGeneratePipeline() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", PROJECT_HOME.toString());

    BusinessVaultModel bvModel = loadBusinessVaultModel(EXTERNAL_BV_PATH);
    DataVaultModel dvModel =
        BusinessVaultDvModelResolver.loadReferencedModel(
            bvModel.getDataVaultModelPath(), variables, testMetadataProvider());

    assertEquals("Vault", dvModel.getConfigurationOrDefault().getTargetDatabase());
    assertEquals("Vault", bvModel.getConfigurationOrDefault().getTargetDatabase());

    BvScd2Table scd2Table = findCustomer360Table(bvModel);
    List<ICheckResult> remarks = new java.util.ArrayList<>();
    scd2Table.check(remarks, testMetadataProvider(), variables, bvModel, dvModel);
    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> remarks.stream().map(ICheckResult::getText).toList().toString());

    List<PipelineMeta> pipelines =
        scd2Table.generateBuildPipelines(testMetadataProvider(), variables, bvModel, dvModel);
    assertEquals(1, pipelines.size());
    assertEquals(19, pipelines.get(0).getTransforms().size());
  }

  @Test
  void customer360ModelsValidateAndGenerateFourSatellitePipeline() throws Exception {
    DataVaultModel dvModel = loadDataVaultModel(DV_PATH);
    BusinessVaultModel bvModel = loadBusinessVaultModel(BV_PATH);
    BvScd2Table scd2Table = findCustomer360Table(bvModel);

    List<ICheckResult> remarks = new java.util.ArrayList<>();
    scd2Table.check(remarks, null, new Variables(), bvModel, dvModel);
    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> remarks.stream().map(ICheckResult::getText).toList().toString());

    assertEquals(4, scd2Table.getDerivatives().size());
    assertEquals(11, scd2Table.getFieldMappings().size());
    assertEquals(4, scd2Table.getSatelliteConfigs().size());

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            scd2Table, bvModel.getConfigurationOrDefault(), dvModel, new Variables());
    assertEquals(16, layout.size());
    assertEquals("customer_hk", layout.getValueMeta(0).getName());
    assertEquals("cust_segment", layout.getValueMeta(1).getName());
    assertEquals("cust_language", layout.getValueMeta(11).getName());
    assertEquals("x_record_source", layout.getValueMeta(12).getName());
    assertEquals("x_load_ts", layout.getValueMeta(13).getName());
    assertEquals("x_from_ts", layout.getValueMeta(14).getName());
    assertEquals("x_to_ts", layout.getValueMeta(15).getName());

    IHopMetadataProvider metadataProvider = testMetadataProvider();
    List<PipelineMeta> pipelines =
        scd2Table.generateBuildPipelines(metadataProvider, new Variables(), bvModel, dvModel);
    assertEquals(1, pipelines.size());

    PipelineMeta pipelineMeta = pipelines.get(0);
    List<TransformMeta> transforms = pipelineMeta.getTransforms();
    assertEquals(19, transforms.size());
    assertEquals(
        4, transforms.stream().filter(t -> t.getTransform() instanceof TableInputMeta).count());
    for (TransformMeta transform :
        transforms.stream().filter(t -> t.getTransform() instanceof TableInputMeta).toList()) {
      assertFalse(
          ((TableInputMeta) transform.getTransform()).getSql().contains("x_record_source"),
          transform.getName());
    }
    assertEquals(
        4, transforms.stream().filter(t -> t.getTransform() instanceof ConstantMeta).count());
    assertEquals(
        5, transforms.stream().filter(t -> t.getTransform() instanceof SelectValuesMeta).count());

    TransformMeta mergeTransform =
        transforms.stream()
            .filter(t -> t.getTransform() instanceof SortedSchemaMergeMeta)
            .findFirst()
            .orElseThrow();
    SortedSchemaMergeMeta mergeMeta = (SortedSchemaMergeMeta) mergeTransform.getTransform();
    assertEquals(4, mergeMeta.getInputs().size());
    assertEquals("customer_hk", mergeMeta.getSortKeys().get(0).getFieldName());
    assertEquals("x_load_ts", mergeMeta.getSortKeys().get(1).getFieldName());

    TransformMeta repeatTransform =
        transforms.stream()
            .filter(t -> t.getTransform() instanceof RepeatFieldsMeta)
            .findFirst()
            .orElseThrow();
    RepeatFieldsMeta repeatMeta = (RepeatFieldsMeta) repeatTransform.getTransform();
    assertEquals(11, repeatMeta.getRepeats().size());

    long mergeIncomingHops =
        pipelineMeta.getPipelineHops().stream()
            .filter(hop -> hop.getToTransform().equals(mergeTransform))
            .count();
    assertEquals(4, mergeIncomingHops);
  }

  @Test
  void incrementalCustomer360PipelineAddsOpenTargetBaselineLeg() throws Exception {
    DataVaultModel dvModel = loadDataVaultModel(DV_PATH);
    BusinessVaultModel bvModel = loadBusinessVaultModel(BV_PATH);
    BvScd2Table scd2Table = findCustomer360Table(bvModel);
    scd2Table.setBuildMode(BvScd2BuildMode.INCREMENTAL);

    IHopMetadataProvider metadataProvider = testMetadataProvider();
    List<PipelineMeta> pipelines =
        scd2Table.generateBuildPipelines(metadataProvider, new Variables(), bvModel, dvModel);
    assertEquals(1, pipelines.size());

    PipelineMeta pipelineMeta = pipelines.get(0);
    List<TransformMeta> transforms = pipelineMeta.getTransforms();
    // +2 param Generate Rows (watermark, open-row filter)
    assertEquals(30, transforms.size());
    assertEquals(
        6, transforms.stream().filter(t -> t.getTransform() instanceof TableInputMeta).count());
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
    assertTrue(transforms.stream().anyMatch(t -> "read_open_customer_360_bv".equals(t.getName())));
    assertTrue(
        transforms.stream()
            .anyMatch(
                t ->
                    (BvScd2PipelineSupport.CLOSE_LOOKUP_READ_PREFIX + "customer_360_bv")
                        .equals(t.getName())));

    Scd2BuildContext buildContext =
        BvScd2PipelineSupport.createContext(
            metadataProvider, new Variables(), bvModel, dvModel, scd2Table);
    String openReadSql = BvScd2PipelineSupport.buildOpenTargetTableInputSql(buildContext);
    assertTrue(openReadSql.contains("FROM customer_360_bv"));
    assertTrue(openReadSql.contains("x_to_ts = ?"));
    assertTrue(openReadSql.contains("customer_hk IN ("));
    assertTrue(openReadSql.contains("FROM sat_customer_demo WHERE"));
    assertTrue(openReadSql.contains("UNION"));
    assertTrue(openReadSql.contains("FROM sat_customer_prefs WHERE"));
    // 1 open-end ? + 1 watermark ? per satellite leg (JDBC does not reuse binds)
    int openReadPlaceholders = openReadSql.length() - openReadSql.replace("?", "").length();
    assertEquals(1 + buildContext.legs.size(), openReadPlaceholders);
    assertEquals(
        buildContext.legs.size(),
        BvScd2PipelineSupport.countDeltaHashKeyWatermarkPlaceholders(buildContext));

    RowGeneratorMeta openRowFilter =
        (RowGeneratorMeta)
            transforms.stream()
                .filter(
                    t -> BvScd2PipelineSupport.PARAM_OPEN_ROW_FILTER_TRANSFORM.equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(
        openReadPlaceholders,
        openRowFilter.getFields().size(),
        "param_open_row_filter field count must match open-target ? placeholders");

    String closeLookupSql = BvScd2PipelineSupport.buildOpenTargetCloseLookupSql(buildContext);
    assertTrue(
        closeLookupSql.contains("SELECT customer_hk, x_from_ts AS _close_lookup_valid_from"));
    assertTrue(closeLookupSql.contains("FROM customer_360_bv"));
    assertTrue(closeLookupSql.contains(" = ?"));
    assertFalse(closeLookupSql.contains("cust_email"));
    int closePlaceholders = closeLookupSql.length() - closeLookupSql.replace("?", "").length();
    assertEquals(openReadPlaceholders, closePlaceholders);
    TransformMeta openReadTransform =
        transforms.stream()
            .filter(t -> "read_open_customer_360_bv".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    assertEquals(
        GeneratedPipelineMetadataConstants.ROLE_TARGET_READ,
        GeneratedPipelineMetadataSupport.getTransformAttribute(
            openReadTransform, GeneratedPipelineMetadataConstants.LOGICAL_ROLE));

    TransformMeta mergeTransform =
        transforms.stream()
            .filter(t -> t.getTransform() instanceof SortedSchemaMergeMeta)
            .findFirst()
            .orElseThrow();
    SortedSchemaMergeMeta mergeMeta = (SortedSchemaMergeMeta) mergeTransform.getTransform();
    assertEquals(5, mergeMeta.getInputs().size());
    assertTrue(
        mergeMeta.getInputs().stream()
            .anyMatch(input -> "select_baseline".equals(input.getTransformName())));
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "select_baseline".equals(hop.getFromTransform().getName())
                        && hop.getToTransform().equals(mergeTransform)));

    TransformMeta joinCloseLookupTransform =
        transforms.stream()
            .filter(t -> BvScd2PipelineSupport.JOIN_CLOSE_LOOKUP_VALID_FROM.equals(t.getName()))
            .findFirst()
            .orElseThrow();
    long baselineReadOutgoingHops =
        pipelineMeta.getPipelineHops().stream()
            .filter(hop -> "read_open_customer_360_bv".equals(hop.getFromTransform().getName()))
            .count();
    assertEquals(1, baselineReadOutgoingHops);
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "read_open_customer_360_bv".equals(hop.getFromTransform().getName())
                        && "source_baseline".equals(hop.getToTransform().getName())));
    assertFalse(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "read_open_customer_360_bv".equals(hop.getFromTransform().getName())
                        && hop.getToTransform().equals(joinCloseLookupTransform)));
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    "filter_new_open_rows".equals(hop.getFromTransform().getName())
                        && hop.getToTransform().equals(joinCloseLookupTransform)));
    assertTrue(
        pipelineMeta.getPipelineHops().stream()
            .anyMatch(
                hop ->
                    (BvScd2PipelineSupport.CLOSE_LOOKUP_READ_PREFIX + "customer_360_bv")
                            .equals(hop.getFromTransform().getName())
                        && hop.getToTransform().equals(joinCloseLookupTransform)));

    TransformMeta repeatTransform =
        transforms.stream()
            .filter(t -> t.getTransform() instanceof RepeatFieldsMeta)
            .findFirst()
            .orElseThrow();
    RepeatFieldsMeta repeatMeta = (RepeatFieldsMeta) repeatTransform.getTransform();
    assertEquals(22, repeatMeta.getRepeats().size());
    assertTrue(
        repeatMeta.getRepeats().stream()
            .anyMatch(
                repeat ->
                    BvScd2PipelineSupport.BASELINE_SOURCE_INDICATOR.equals(
                            repeat.getIndicatorValue())
                        && "cust_email".equals(repeat.getSourceField())));
  }

  private static BvScd2Table findCustomer360Table(BusinessVaultModel bvModel) {
    return bvModel.getTables().stream()
        .filter(BvScd2Table.class::isInstance)
        .map(BvScd2Table.class::cast)
        .filter(table -> "customer_360_bv".equals(table.getName()))
        .findFirst()
        .orElseThrow();
  }

  private static DataVaultModel loadDataVaultModel(Path path) throws Exception {
    Document document = XmlHandler.loadXmlFile(path.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    IHopMetadataProvider provider = testMetadataProvider();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    return model;
  }

  private static IHopMetadataProvider testMetadataProvider() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return ModelConfigurationTestSupport.prepare(
        metadataProvider, ModelConfigurationTestSupport.INTEGRATION_TESTS);
  }

  private static BusinessVaultModel loadBusinessVaultModel(Path path) throws Exception {
    Document document = XmlHandler.loadXmlFile(path.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    IHopMetadataProvider provider = testMetadataProvider();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    model.setDataVaultModelPath(DV_PATH.toString());
    return model;
  }
}
