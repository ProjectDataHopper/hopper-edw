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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.catalog.RetailExampleCatalogFixtures;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmPipelineBuilderSupport;
import org.apache.hop.datavault.transform.mergerowsplus.MergeRowsPlusMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.apache.hop.pipeline.transforms.update.UpdateMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmScd2DimensionPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @Test
  void recordDefinitionSourceBuildsPhysicalInputInsteadOfSqlTableInput() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    model.getConfigurationOrDefault().setDataCatalogConnection("local-catalog");

    DmDimension dimension = new DmDimension();
    dimension.setName("d_orders");
    dimension.setTableName("d_orders");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("order_id"));
    dimension.getSourceOrDefault().setSourceType(DmSourceType.RECORD_DEFINITION);
    dimension.getSourceOrDefault().setSourceCatalogConnection("local-catalog");
    dimension.getSourceOrDefault().setSourceRecordNamespace("hop/retail-example/sources");
    dimension.getSourceOrDefault().setSourceRecordName("E2E-order-header");
    model.getTables().add(dimension);

    Variables variables = retailExampleVariables();
    IHopMetadataProvider metadataProvider = retailExampleMetadataProvider(variables);

    PipelineMeta pipeline =
        dimension.generateUpdatePipelines(metadataProvider, variables, model, new Date()).get(0);

    TransformMeta sourceTransform =
        pipeline.getTransforms().stream()
            .filter(t -> t.getName().startsWith("source_d_orders"))
            .findFirst()
            .orElseThrow();
    assertTrue(
        sourceTransform.getTransform() instanceof TableInputMeta
            || pipeline.getTransforms().stream()
                .anyMatch(t -> t.getTransform() instanceof TableInputMeta));
  }

  @Test
  void pipelineSourceBuildsMetaInjectInsteadOfTableInput() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    DmDimension dimension = new DmDimension();
    dimension.setName("d_stream");
    dimension.setTableName("d_stream");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("id"));
    dimension.getSourceOrDefault().setSourceType(DmSourceType.PIPELINE);
    dimension
        .getSourceOrDefault()
        .setSourcePipelineFile(
            java.nio.file.Path.of("retail-example", "test", "streaming-source.hpl")
                .toAbsolutePath()
                .toString());
    dimension.getSourceOrDefault().setSourcePipelineTransform("id");
    model.getTables().add(dimension);

    PipelineMeta pipeline =
        dimension
            .generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    TransformMeta sourceTransform =
        pipeline.getTransforms().stream()
            .filter(t -> t.getName().equals("source_d_stream"))
            .findFirst()
            .orElseThrow();
    assertTrue(sourceTransform.getTransform() instanceof MetaInjectMeta);
    MetaInjectMeta metaInjectMeta = (MetaInjectMeta) sourceTransform.getTransform();
    assertEquals("id", metaInjectMeta.getSourceTransformName());
    assertFalse(
        pipeline.getTransforms().stream()
            .anyMatch(
                t ->
                    t.getName().equals("source_d_stream")
                        && t.getTransform() instanceof TableInputMeta));
  }

  @Test
  void type2CustomerDimensionBuildsVersionIncrementAndCloseUpdate() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    DmDimension dimension = new DmDimension();
    dimension.setName("d_customer");
    dimension.setTableName("d_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    dimension.setSurrogateKeyField("customer_hk");
    dimension.setSurrogateKeySourceField("customer_hk");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension
        .getAttributes()
        .add(new DmDimensionAttribute("cust_segment", DmScdUpdatePolicy.TYPE2));
    dimension
        .getSourceOrDefault()
        .setSourceSql(
            "SELECT hc.customer_id, sc.* FROM hub_customer hc JOIN customer_360_bv sc ON hc.customer_hk = sc.customer_hk");
    model.getTables().add(dimension);

    PipelineMeta pipeline =
        dimension
            .generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    assertEquals("dm-dim-d_customer", pipeline.getName());
    CalculatorMeta versionCalculator =
        (CalculatorMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().equals("calc_d_customer_version"))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(
        "version_new",
        versionCalculator
            .getFunctions()
            .get(versionCalculator.getFunctions().size() - 1)
            .getFieldName());
    assertFalse(
        versionCalculator
            .getFunctions()
            .get(versionCalculator.getFunctions().size() - 1)
            .isRemovedFromResult());
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(
                t ->
                    t.getName().equals("collapse_d_customer")
                        && t.getTransform() instanceof GroupByMeta));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("needs_versioned_update")));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("needs_inplace_update")));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("update_d_customer_inplace")));
    assertTrue(
        pipeline.getTransforms().stream().anyMatch(t -> t.getName().equals("close_d_customer")));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("select_merge_compare")));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("select_merge_reference")));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(
                t ->
                    t.getName().equals("merge_diff")
                        && t.getTransform() instanceof MergeRowsPlusMeta));
    assertFalse(
        pipeline.getTransforms().stream()
            .anyMatch(
                t -> t.getName().equals("merge_reference") || t.getName().equals("merge_compare")));

    ConstantMeta effectiveDates =
        (ConstantMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().equals("add_effective_dates"))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(
        effectiveDates.getFields().stream()
            .anyMatch(
                field ->
                    "version".equals(field.getFieldName())
                        && "Integer".equals(field.getFieldType())
                        && "0".equals(field.getValue())
                        && field.getFieldLength() == 9));
    assertTrue(
        effectiveDates.getFields().stream()
            .anyMatch(field -> "date_from".equals(field.getFieldName())));

    List<String> expectedMergeFields =
        List.of("customer_hk", "cust_segment", "customer_id", "version", "date_from", "date_to");
    assertEquals(expectedMergeFields, selectOutputFieldNames(pipeline, "select_merge_compare"));
    assertEquals(expectedMergeFields, selectOutputFieldNames(pipeline, "select_merge_reference"));
    assertEquals(6, selectOutputFieldNames(pipeline, "select_merge_compare").size());

    TableInputMeta targetInput =
        (TableInputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().equals("target_d_customer"))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(targetInput.getSql().contains("version"));
    assertTrue(targetInput.getSql().contains("ORDER BY"));
    assertTrue(targetInput.getSql().contains("customer_id"));

    TableOutputMeta tableOutput =
        (TableOutputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof TableOutputMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(
        tableOutput.getFields().stream()
            .anyMatch(
                field ->
                    "version".equals(field.getFieldDatabase())
                        && "version_new".equals(field.getFieldStream())));
    Set<String> outputFields =
        tableOutput.getFields().stream()
            .map(field -> field.getFieldDatabase())
            .collect(Collectors.toSet());
    assertTrue(outputFields.contains("version"));
    assertTrue(outputFields.contains("is_current"));
    assertFalse(outputFields.contains("flag"));
    assertFalse(outputFields.contains(DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD));
    assertFalse(outputFields.contains(DmPipelineBuilderSupport.PREVIOUS_VERSION_NUM_FIELD));

    TransformMeta closeTransform =
        pipeline.getTransforms().stream()
            .filter(t -> t.getName().equals("close_d_customer"))
            .findFirst()
            .orElseThrow();
    UpdateMeta updateMeta = (UpdateMeta) closeTransform.getTransform();
    assertNotNull(updateMeta.getCommitSizeVar());
    assertFalse(Utils.isEmpty(updateMeta.getCommitSizeVar()));
    assertEquals("Vault", updateMeta.getConnection());
    assertEquals("", updateMeta.getLookupField().getSchemaName());
    assertEquals("d_customer", updateMeta.getLookupField().getTableName());
    assertTrue(
        updateMeta.getLookupField().getLookupKeys().stream()
            .anyMatch(
                key ->
                    DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD.equals(key.getKeyStream())
                        && "date_from".equals(key.getKeyLookup())));
    assertEquals(1, updateMeta.getLookupField().getUpdateFields().size());
    assertEquals("date_to", updateMeta.getLookupField().getUpdateFields().get(0).getUpdateLookup());
    assertEquals(
        "date_from", updateMeta.getLookupField().getUpdateFields().get(0).getUpdateStream());
  }

  @Test
  void type2DimensionUsesConfiguredEffectiveDateSourceField() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    model.getConfigurationOrDefault().setDateFromField("x_from_ts");

    DmDimension dimension = new DmDimension();
    dimension.setName("d_customer");
    dimension.setTableName("d_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.setEffectiveDateSourceField("x_from_ts");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension
        .getAttributes()
        .add(new DmDimensionAttribute("cust_segment", DmScdUpdatePolicy.TYPE2));
    dimension
        .getSourceOrDefault()
        .setSourceSql(
            "SELECT customer_id, cust_segment, x_from_ts FROM customer_360_bv WHERE x_to_ts = '9999-12-31 23:59:59'");
    model.getTables().add(dimension);

    PipelineMeta pipeline =
        dimension
            .generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    ConstantMeta effectiveDates =
        (ConstantMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().equals("add_effective_dates"))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertFalse(
        effectiveDates.getFields().stream()
            .anyMatch(field -> "x_from_ts".equals(field.getFieldName())));
    assertFalse(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("map_d_customer_effective_date")));
  }

  private static List<String> selectOutputFieldNames(PipelineMeta pipeline, String transformName) {
    SelectValuesMeta selectMeta =
        (SelectValuesMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().equals(transformName))
                .findFirst()
                .orElseThrow()
                .getTransform();
    return selectMeta.getSelectOption().getSelectFields().stream()
        .map(field -> Utils.isEmpty(field.getRename()) ? field.getName() : field.getRename())
        .toList();
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return metadataProvider;
  }

  private static Variables retailExampleVariables() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    return variables;
  }

  private static IHopMetadataProvider retailExampleMetadataProvider(Variables variables)
      throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(vault);
    DatabaseMeta crm = new DatabaseMeta();
    crm.setName("CRM");
    metadataProvider.getSerializer(DatabaseMeta.class).save(crm);

    DataCatalogMeta catalogMeta = new DataCatalogMeta();
    catalogMeta.setName("local-catalog");
    catalogMeta.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(RetailExampleCatalogFixtures.catalogStorageRootPath());
    catalogMeta.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalogMeta);

    RecordDefinitionRegistry.getInstance().invalidate();
    return metadataProvider;
  }
}
