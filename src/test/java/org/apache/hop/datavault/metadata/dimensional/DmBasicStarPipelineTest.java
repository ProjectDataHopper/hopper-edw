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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.datavault.metadata.ModelConfigurationTestSupport;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmUpdateExecutionSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateValue;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmBasicStarPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void scd1DimensionPipelineUsesTableInputAndInsertUpdate() throws Exception {
    DimensionalModel model = loadBasicStarModel();
    DmDimension dimension = (DmDimension) model.findTable("dim_customer");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        dimension.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    assertEquals(1, pipelines.size());
    PipelineMeta pipelineMeta = pipelines.get(0);
    assertEquals("dm-dim-d_customer", pipelineMeta.getName());
    assertEquals(2, pipelineMeta.getTransforms().size());

    TransformMeta tableInput =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof TableInputMeta)
            .findFirst()
            .orElseThrow();
    TableInputMeta tableInputMeta = (TableInputMeta) tableInput.getTransform();
    assertEquals("Vault", tableInputMeta.getConnection());
    assertTrue(tableInputMeta.getSql().contains("stg_customer"));
    assertTrue(tableInputMeta.getSql().contains("customer_id"));

    TransformMeta insertUpdate =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof InsertUpdateMeta)
            .findFirst()
            .orElseThrow();
    InsertUpdateMeta insertUpdateMeta = (InsertUpdateMeta) insertUpdate.getTransform();
    assertEquals("Vault", insertUpdateMeta.getConnection());
    assertEquals("d_customer", insertUpdateMeta.getInsertUpdateLookupField().getTableName());
    assertEquals(1, insertUpdateMeta.getInsertUpdateLookupField().getLookupKeys().size());
    assertEquals(
        "customer_id",
        insertUpdateMeta.getInsertUpdateLookupField().getLookupKeys().get(0).getKeyStream());

    List<InsertUpdateValue> valueFields =
        insertUpdateMeta.getInsertUpdateLookupField().getValueFields();
    assertEquals(4, valueFields.size());
    assertEquals("customer_id", valueFields.get(0).getUpdateStream());
    assertFalse(valueFields.get(0).isUpdate());
    assertEquals("customer_name", valueFields.get(1).getUpdateStream());
    assertTrue(valueFields.get(1).isUpdate());
    assertEquals("city", valueFields.get(2).getUpdateStream());
    assertTrue(valueFields.get(2).isUpdate());
    assertEquals("load_dt", valueFields.get(3).getUpdateStream());
    assertTrue(valueFields.get(3).isUpdate());
  }

  @Test
  void factPipelineUsesDimensionLookupsAndTableOutput() throws Exception {
    DimensionalModel model = loadBasicStarModel();
    DmFact fact = (DmFact) model.findTable("fact_sales");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    assertEquals(1, pipelines.size());
    PipelineMeta pipelineMeta = pipelines.get(0);
    assertEquals("dm-fact-f_sales", pipelineMeta.getName());
    assertEquals(4, pipelineMeta.getTransforms().size());

    TransformMeta tableInput =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof TableInputMeta)
            .findFirst()
            .orElseThrow();
    TableInputMeta tableInputMeta = (TableInputMeta) tableInput.getTransform();
    assertTrue(tableInputMeta.getSql().contains("stg_sales"));
    assertTrue(tableInputMeta.getSql().contains("quantity"));

    long lookupCount =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof DimensionLookupMeta)
            .count();
    assertEquals(2, lookupCount);

    TransformMeta customerLookup =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "lookup_Customer".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta customerLookupMeta = (DimensionLookupMeta) customerLookup.getTransform();
    assertFalse(customerLookupMeta.isUpdate());
    assertEquals("d_customer", customerLookupMeta.getTableName());
    assertEquals("customer_key", customerLookupMeta.getFields().getReturns().getKeyRename());
    assertFalse(customerLookupMeta.isPreloadingCache());
    assertTrue(Utils.isEmpty(customerLookupMeta.getFields().getDate().getName()));

    TransformMeta tableOutput =
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof TableOutputMeta)
            .findFirst()
            .orElseThrow();
    TableOutputMeta tableOutputMeta = (TableOutputMeta) tableOutput.getTransform();
    assertEquals("f_sales", tableOutputMeta.getTableName());
    assertFalse(tableOutputMeta.isTruncateTable());
  }

  @Test
  void factDimensionLookupPreloadingCacheEnabledWhenConfigured() throws Exception {
    DimensionalModel model = loadBasicStarModel();
    DmFact fact = (DmFact) model.findTable("fact_sales");
    fact.getDimensionRoles().get(0).setPreloadLookupCache(true);
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    PipelineMeta pipelineMeta =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date()).get(0);

    DimensionLookupMeta customerLookupMeta =
        (DimensionLookupMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "lookup_Customer".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    DimensionLookupMeta productLookupMeta =
        (DimensionLookupMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "lookup_Product".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();

    assertTrue(customerLookupMeta.isPreloadingCache());
    assertFalse(productLookupMeta.isPreloadingCache());
  }

  @Test
  void factDimensionLookupDateConfiguredForEffectivityDimensionsOnly() throws Exception {
    DimensionalModel model = loadExtendedCatalogModel();
    DmPeriodicSnapshotFact fact = (DmPeriodicSnapshotFact) model.findTable("fact_daily_balance");
    fact.setDimensionLookupDateField("snapshot_date");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    PipelineMeta pipelineMeta =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date()).get(0);

    DimensionLookupMeta customerLookupMeta =
        (DimensionLookupMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "lookup_Customer".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    DimensionLookupMeta productLookupMeta =
        (DimensionLookupMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "lookup_Product".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();

    assertFalse(Utils.isEmpty(customerLookupMeta.getFields().getDate().getName()));
    assertEquals("snapshot_date", customerLookupMeta.getFields().getDate().getName());
    assertEquals("date_from", customerLookupMeta.getFields().getDate().getFrom());
    assertEquals("date_to", customerLookupMeta.getFields().getDate().getTo());
    assertTrue(Utils.isEmpty(productLookupMeta.getFields().getDate().getName()));
  }

  @Test
  void executionOrderRunsDimensionsBeforeFacts() throws Exception {
    DimensionalModel model = loadBasicStarModel();
    List<IDmTable> ordered =
        DmUpdateExecutionSupport.orderTablesForPipelineExecution(model.getTables());

    assertEquals(3, ordered.size());
    assertEquals(DmTableType.DIMENSION, ordered.get(0).getTableType());
    assertEquals(DmTableType.DIMENSION, ordered.get(1).getTableType());
    assertEquals(DmTableType.FACT, ordered.get(2).getTableType());
    assertEquals("fact_sales", ordered.get(2).getName());
  }

  private static IHopMetadataProvider testMetadataProvider() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return ModelConfigurationTestSupport.prepare(
        metadataProvider, ModelConfigurationTestSupport.INTEGRATION_TESTS);
  }

  private static DimensionalModel loadBasicStarModel() throws Exception {
    return loadDimensionalModel("integration-tests/tests/basic/basic-star.hdm");
  }

  private static DimensionalModel loadExtendedCatalogModel() throws Exception {
    return loadDimensionalModel("integration-tests/tests/basic/extended-catalog.hdm");
  }

  private static DimensionalModel loadDimensionalModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    IHopMetadataProvider provider = testMetadataProvider();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    return model;
  }
}
