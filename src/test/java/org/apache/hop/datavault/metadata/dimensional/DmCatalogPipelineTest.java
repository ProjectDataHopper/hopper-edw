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
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmUpdateExecutionSupport;
import org.apache.hop.datavault.transform.junkdimension.JunkDimensionMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmCatalogPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void extendedCatalogModelChecksOk() throws Exception {
    DimensionalModel model = loadExtendedCatalogModel();
    var remarks = model.check(testMetadataProvider(), new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK));
    assertFalse(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
    assertEquals("dim_customer", model.findConformedDimension("Customer").getName());
  }

  @Test
  void scd3DimensionUsesDimensionLookupUpdate() throws Exception {
    DimensionalModel model = loadExtendedCatalogModel();
    DmDimension dimension = (DmDimension) model.findTable("dim_customer");

    PipelineMeta pipeline =
        dimension
            .generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    assertEquals("dm-dim-d_customer", pipeline.getName());
    TransformMeta lookup =
        pipeline.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof DimensionLookupMeta)
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta lookupMeta = (DimensionLookupMeta) lookup.getTransform();
    assertTrue(lookupMeta.isUpdate());
    DimensionLookupMeta.DLField previousField =
        lookupMeta.getFields().getFields().stream()
            .filter(f -> "city_prev".equals(f.getLookup()))
            .findFirst()
            .orElseThrow();
    assertEquals(
        DimensionLookupMeta.DimensionUpdateType.LAST_VERSION, previousField.getUpdateType());
  }

  @Test
  void junkDimensionUsesJunkDimensionTransform() throws Exception {
    DimensionalModel model = loadExtendedCatalogModel();
    DmJunkDimension junk = (DmJunkDimension) model.findTable("dim_order_junk");

    PipelineMeta pipeline =
        junk.generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    assertEquals("dm-junk-d_order_junk", pipeline.getName());
    assertEquals(2, pipeline.getTransforms().size());
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof JunkDimensionMeta));
    JunkDimensionMeta junkMeta =
        (JunkDimensionMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof JunkDimensionMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(3, junkMeta.getFields().getKeyFields().size());
    assertEquals("promo_flag", junkMeta.getFields().getKeyFields().get(0).getName());
  }

  @Test
  void bridgeUsesInsertUpdateOnForeignKeys() throws Exception {
    DimensionalModel model = loadExtendedCatalogModel();
    DmBridge bridge = (DmBridge) model.findTable("bridge_customer_product");

    PipelineMeta pipeline =
        bridge
            .generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    assertEquals("dm-bridge-bridge_customer_product", pipeline.getName());
    InsertUpdateMeta insertUpdate =
        (InsertUpdateMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof InsertUpdateMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(2, insertUpdate.getInsertUpdateLookupField().getLookupKeys().size());
    assertEquals(1, insertUpdate.getInsertUpdateLookupField().getValueFields().size());
  }

  @Test
  void factlessFactUsesLookupsWithoutTableOutputMeasures() throws Exception {
    DimensionalModel model = loadExtendedCatalogModel();
    DmFactlessFact factless = (DmFactlessFact) model.findTable("factless_coverage");

    PipelineMeta pipeline =
        factless
            .generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    assertEquals(4, pipeline.getTransforms().size());
    assertEquals(
        2,
        pipeline.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof DimensionLookupMeta)
            .count());
    TableOutputMeta tableOutput =
        (TableOutputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof TableOutputMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(2, tableOutput.getFields().size());
  }

  @Test
  void accumulatingSnapshotEndsWithInsertUpdate() throws Exception {
    DimensionalModel model = loadExtendedCatalogModel();
    DmAccumulatingSnapshotFact fact =
        (DmAccumulatingSnapshotFact) model.findTable("fact_order_lifecycle");

    PipelineMeta pipeline =
        fact.generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    List<TransformMeta> transforms = pipeline.getTransforms();
    assertTrue(transforms.get(transforms.size() - 1).getTransform() instanceof InsertUpdateMeta);
    TableInputMeta source = (TableInputMeta) transforms.get(0).getTransform();
    assertTrue(source.getSql().contains("order_id"));
  }

  @Test
  void executionOrderRunsDimensionsJunkBridgeThenFacts() throws Exception {
    DimensionalModel model = loadExtendedCatalogModel();
    List<IDmTable> ordered =
        DmUpdateExecutionSupport.orderTablesForPipelineExecution(model.getTables());

    assertEquals(10, ordered.size());
    assertEquals(DmTableType.DIMENSION, ordered.get(0).getTableType());
    assertEquals(DmTableType.DIMENSION, ordered.get(1).getTableType());
    assertEquals(DmTableType.DIMENSION, ordered.get(2).getTableType());
    assertEquals("dim_patient", ordered.get(2).getName());
    assertEquals(DmTableType.JUNK_DIMENSION, ordered.get(3).getTableType());
    assertEquals(DmTableType.BRIDGE, ordered.get(4).getTableType());
    assertEquals(DmTableType.FACTLESS_FACT, ordered.get(5).getTableType());
    assertEquals(DmTableType.PERIODIC_SNAPSHOT_FACT, ordered.get(6).getTableType());
    assertEquals(DmTableType.ACCUMULATING_SNAPSHOT_FACT, ordered.get(7).getTableType());
    assertEquals(DmTableType.FACT, ordered.get(8).getTableType());
    assertEquals("fact_sales", ordered.get(8).getName());
    assertEquals(DmTableType.AGGREGATE_FACT, ordered.get(9).getTableType());
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return metadataProvider;
  }

  private static DimensionalModel loadExtendedCatalogModel() throws Exception {
    Path fixture =
        Path.of("integration-tests/tests/basic/extended-catalog.hdm").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    model.setFilename(fixture.toString());
    return model;
  }
}
