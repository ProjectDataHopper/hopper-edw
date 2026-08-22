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
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmFactDimensionJoinBuilder;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmUpdateExecutionSupport;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmDateRolePlayingPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void factPipelineUsesBatchedSelectValuesForAllDateRoles() throws Exception {
    DimensionalModel model = loadDateRolePlayingModel();
    DmFact fact = (DmFact) model.findTable("fact_orders");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    assertEquals(1, pipelines.size());
    PipelineMeta pipelineMeta = pipelines.get(0);
    assertEquals(4, pipelineMeta.getTransforms().size());

    assertEquals(
        1,
        pipelineMeta.getTransforms().stream()
            .filter(t -> "date_keys_format".equals(t.getName()))
            .count());
    assertEquals(
        1,
        pipelineMeta.getTransforms().stream()
            .filter(t -> "date_keys_int".equals(t.getName()))
            .count());
    assertFalse(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof CalculatorMeta));
    assertFalse(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof DimensionLookupMeta));

    SelectValuesMeta formatMeta =
        (SelectValuesMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "date_keys_format".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(formatMeta.getSelectOption().isSelectingAndSortingUnspecifiedFields());
    assertEquals(2, formatMeta.getSelectOption().getSelectFields().size());
    assertEquals("order_date", formatMeta.getSelectOption().getSelectFields().get(0).getName());
    assertEquals(
        "order_date_key", formatMeta.getSelectOption().getSelectFields().get(0).getRename());
    assertEquals("shipping_date", formatMeta.getSelectOption().getSelectFields().get(1).getName());
    assertEquals(
        "shipment_date_key", formatMeta.getSelectOption().getSelectFields().get(1).getRename());

    List<SelectMetadataChange> formatChanges = formatMeta.getSelectOption().getMeta();
    assertEquals(2, formatChanges.size());
    assertStringDateKeyMeta(formatChanges.get(0), "order_date_key");
    assertStringDateKeyMeta(formatChanges.get(1), "shipment_date_key");

    SelectValuesMeta intMeta =
        (SelectValuesMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "date_keys_int".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(intMeta.getSelectOption().isSelectingAndSortingUnspecifiedFields());
    List<SelectMetadataChange> intChanges = intMeta.getSelectOption().getMeta();
    assertEquals(2, intChanges.size());
    assertIntegerDateKeyMeta(intChanges.get(0), "order_date_key");
    assertIntegerDateKeyMeta(intChanges.get(1), "shipment_date_key");
  }

  @Test
  void dateKeysFormatPassesThroughDimensionLookupDateWhenConfigured() throws Exception {
    DimensionalModel model = loadDateRolePlayingModel();
    DmFact fact = (DmFact) model.findTable("fact_orders");
    fact.setDimensionLookupDateField("order_date");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    PipelineMeta pipelineMeta =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date()).get(0);

    SelectValuesMeta formatMeta =
        (SelectValuesMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "date_keys_format".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(3, formatMeta.getSelectOption().getSelectFields().size());
    assertEquals("order_date", formatMeta.getSelectOption().getSelectFields().get(0).getName());
    assertEquals(
        "order_date_key", formatMeta.getSelectOption().getSelectFields().get(0).getRename());
    assertEquals("shipping_date", formatMeta.getSelectOption().getSelectFields().get(1).getName());
    assertEquals(
        "shipment_date_key", formatMeta.getSelectOption().getSelectFields().get(1).getRename());
    assertEquals("order_date", formatMeta.getSelectOption().getSelectFields().get(2).getName());
    assertTrue(
        org.apache.hop.core.util.Utils.isEmpty(
            formatMeta.getSelectOption().getSelectFields().get(2).getRename()));
  }

  @Test
  void legacyRoleNameOnDateRoleDoesNotCreateLookupTransform() throws Exception {
    DimensionalModel model = loadDateRolePlayingModel();
    DmFact fact = (DmFact) model.findTable("fact_orders");
    fact.getDimensionRoles().get(0).setRoleName("OrderDate");

    List<PipelineMeta> pipelines =
        fact.generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date());

    assertFalse(
        pipelines.get(0).getTransforms().stream()
            .anyMatch(t -> "lookup_OrderDate".equals(t.getName())));
    assertTrue(
        pipelines.get(0).getTransforms().stream()
            .anyMatch(t -> "date_keys_format".equals(t.getName())));
  }

  @Test
  void executionOrderSkipsDimensionAliases() throws Exception {
    DimensionalModel model = loadDateRolePlayingModel();
    List<IDmTable> ordered =
        DmUpdateExecutionSupport.orderTablesForPipelineExecution(model.getTables());

    assertEquals(2, ordered.size());
    assertEquals("dim_date", ordered.get(0).getName());
    assertEquals("fact_orders", ordered.get(1).getName());
  }

  private static void assertStringDateKeyMeta(SelectMetadataChange change, String fieldName) {
    assertEquals(fieldName, change.getName());
    assertEquals(ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_STRING), change.getType());
    assertEquals(DateDimensionGeneratorLogic.MASK_DATE_KEY, change.getConversionMask());
  }

  private static void assertIntegerDateKeyMeta(SelectMetadataChange change, String fieldName) {
    assertEquals(fieldName, change.getName());
    assertEquals(ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_INTEGER), change.getType());
    assertEquals(DmFactDimensionJoinBuilder.DATE_KEY_INTEGER_LENGTH, change.getLength());
    assertEquals(0, change.getPrecision());
    assertEquals("0", change.getConversionMask());
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return metadataProvider;
  }

  private static DimensionalModel loadDateRolePlayingModel() throws Exception {
    Path fixture =
        Path.of("integration-tests/tests/basic/date-role-playing.hdm").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    return model;
  }
}
