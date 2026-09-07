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
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.dimensional.pipeline.DmUpdateExecutionSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmLogicalContractValidationTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  @Test
  void logicalDimensionDoesNotRequireSourceSql() {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = logicalProductDimension();
    model.getTables().add(dimension);

    List<ICheckResult> remarks = new ArrayList<>();
    dimension.check(remarks, null, new Variables(), model);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_WARNING
                        && r.getText().contains("logical")));
    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().toLowerCase().contains("source sql")));
  }

  @Test
  void sqlDimensionWithoutSqlStillErrors() {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = logicalProductDimension();
    dimension.getSourceOrDefault().setSourceType(DmSourceType.SQL);
    model.getTables().add(dimension);

    List<ICheckResult> remarks = new ArrayList<>();
    dimension.check(remarks, null, new Variables(), model);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().toLowerCase().contains("source sql")));
  }

  @Test
  void factWithoutGrainWarns() {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = logicalProductDimension();
    model.getTables().add(dimension);
    DmFact fact = new DmFact();
    fact.setName("f_sales");
    fact.setTableName("f_sales");
    fact.getSourceOrDefault().setSourceType(DmSourceType.NONE);
    fact.getMeasures().add(new DmFactMeasure("amount"));
    fact.getDimensionRoles().add(new DmFactDimensionRole("d_product", "d_product_key"));
    model.getTables().add(fact);

    List<ICheckResult> remarks = new ArrayList<>();
    fact.check(remarks, null, new Variables(), model);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_WARNING
                        && r.getText().toLowerCase().contains("grain")));
  }

  @Test
  void xmlRoundTripPreservesLogicalSourceAndFieldDocumentation() throws Exception {
    DimensionalModel original = new DimensionalModel();
    original.setName("logical-star");
    original.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = logicalProductDimension();
    DmDimensionAttribute color = dimension.getAttributes().get(0);
    DmFieldDocumentation docs = new DmFieldDocumentation();
    docs.setDescription("Retail color label");
    docs.setNotes("**Markdown** notes for analysts.");
    docs.setRequirements("Look up `codes.color_description` on `color_code`.");
    docs.setRequiredFlag(true);
    color.setDocumentation(docs);
    original.getTables().add(dimension);

    String xml =
        XmlHandler.aroundTag(
            HopDimensionalFileType.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    assertTrue(xml.contains("NONE"));
    assertTrue(xml.contains("Look up"));
    assertTrue(xml.contains("Markdown"));

    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel restored = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, restored, null);

    DmDimension restoredDim = (DmDimension) restored.getTables().get(0);
    assertEquals(DmSourceType.NONE, restoredDim.getSourceOrDefault().resolveSourceType());
    assertTrue(restoredDim.isLogicalContract());
    DmFieldDocumentation restoredDocs =
        restoredDim.getAttributesOrEmpty().get(0).getDocumentation();
    assertEquals("Retail color label", restoredDocs.getDescription());
    assertTrue(restoredDocs.getNotes().contains("Markdown"));
    assertTrue(restoredDocs.getRequirements().contains("color_code"));
    assertTrue(restoredDocs.isRequired());
  }

  @Test
  void loadReadyExcludesLogicalTables() throws Exception {
    DmDimension logical = logicalProductDimension();
    DmDimension loadReady = logicalProductDimension();
    loadReady.setName("d_loaded");
    loadReady.getSourceOrDefault().setSourceType(DmSourceType.SQL);
    loadReady.getSourceOrDefault().setSourceSql("select 1");

    assertFalse(DmUpdateExecutionSupport.isLoadReady(logical));
    assertTrue(DmUpdateExecutionSupport.isLoadReady(loadReady));
    assertTrue(logical.generateBuildDdl(null, new Variables(), new DimensionalModel()).isEmpty());
  }

  @Test
  void fieldDocumentationGridSummary() {
    DmFieldDocumentation docs = new DmFieldDocumentation();
    assertEquals("", docs.gridSummary());
    docs.setDescription("Color");
    docs.setNotes("notes");
    docs.setRequirements("req");
    docs.setRequiredFlag(false);
    assertEquals("desc, notes, req, optional", docs.gridSummary());
    assertFalse(docs.isRequired());
    assertTrue(docs.hasContent());
  }

  private static DmDimension logicalProductDimension() {
    DmDimension dimension = new DmDimension();
    dimension.setName("d_product");
    dimension.setTableName("d_product");
    dimension.getSourceOrDefault().setSourceType(DmSourceType.NONE);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("product_id"));
    dimension.getAttributes().add(new DmDimensionAttribute("color_description"));
    return dimension;
  }
}
