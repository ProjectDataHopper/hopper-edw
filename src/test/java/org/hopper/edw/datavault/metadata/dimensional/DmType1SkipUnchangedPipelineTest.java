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

import java.util.Date;
import java.util.List;
import org.apache.hop.core.Condition;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateValue;
import org.apache.hop.pipeline.transforms.mergerows.MergeRowsMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.transform.datedimensiongenerator.DateDimensionGeneratorMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class DmType1SkipUnchangedPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void missingModeDeserializesAsOverwrite() throws Exception {
    Document document =
        XmlHandler.loadXmlString("<dimension><name>dim_customer</name></dimension>");
    DmDimension loaded = new DmDimension();
    XmlMetadataUtil.deSerializeFromXml(
        XmlHandler.getSubNode(document, "dimension"), DmDimension.class, loaded, null);

    assertEquals(DmType1UpdateMode.OVERWRITE, loaded.getType1UpdateModeOrDefault());
  }

  @Test
  void skipUnchangedRoundTripsAsCode() throws Exception {
    DmDimension original = new DmDimension();
    original.setName("dim_date");
    original.setType1UpdateMode(DmType1UpdateMode.SKIP_UNCHANGED);

    String xml = XmlMetadataUtil.serializeObjectToXml(original);
    assertTrue(xml.contains("SKIP_UNCHANGED"), xml);

    Document document = XmlHandler.loadXmlString("<dimension>" + xml + "</dimension>");
    DmDimension loaded = new DmDimension();
    XmlMetadataUtil.deSerializeFromXml(
        XmlHandler.getSubNode(document, "dimension"), DmDimension.class, loaded, null);

    assertEquals(DmType1UpdateMode.SKIP_UNCHANGED, loaded.getType1UpdateModeOrDefault());
  }

  @Test
  void dateDimensionSkipsUnchangedRows() throws Exception {
    DmDimension dimension = DmDateDimensionTemplate.createDateDimension(null);
    PipelineMeta pipeline = generate(dimension);

    assertEquals(1, count(pipeline, DateDimensionGeneratorMeta.class));
    assertEquals(2, count(pipeline, SortRowsMeta.class));
    assertEquals(1, count(pipeline, TableInputMeta.class));
    assertEquals(2, count(pipeline, SelectValuesMeta.class));
    assertEquals(1, count(pipeline, MergeRowsMeta.class));
    assertEquals(1, count(pipeline, FilterRowsMeta.class));
    assertEquals(1, count(pipeline, InsertUpdateMeta.class));

    TableInputMeta targetInput =
        (TableInputMeta) find(pipeline, "target_d_date").getTransform();
    String sql = targetInput.getSql();
    assertTrue(sql.contains("date_key"));
    assertTrue(sql.contains("full_date"));
    assertTrue(sql.contains("FROM d_date"));
    assertFalse(sql.contains("load_dt"));
    assertFalse(sql.toUpperCase().contains("ORDER BY"));

    SelectValuesMeta compare =
        (SelectValuesMeta) find(pipeline, "select_merge_compare").getTransform();
    SelectValuesMeta reference =
        (SelectValuesMeta) find(pipeline, "select_merge_reference").getTransform();
    assertTrue(selectedNames(compare).contains("load_dt"));
    assertFalse(selectedNames(reference).contains("load_dt"));

    MergeRowsMeta merge = (MergeRowsMeta) find(pipeline, "merge_diff_d_date").getTransform();
    assertEquals(List.of("date_key"), merge.getKeyFields());
    assertTrue(merge.getValueFields().contains("full_date"));
    assertTrue(merge.getValueFields().contains("is_weekend"));
    assertFalse(merge.getValueFields().contains("load_dt"));
    assertFalse(merge.getValueFields().contains("date_key"));
    assertEquals("flag", merge.getFlagField());
    assertTrue(merge.isAlignInputLayouts());
    assertEquals("sort_target_d_date", merge.getReferenceTransform());
    assertEquals("sort_source_d_date", merge.getCompareTransform());

    FilterRowsMeta filter = (FilterRowsMeta) find(pipeline, "new or changed").getTransform();
    Condition condition = filter.getCondition();
    assertEquals(2, condition.nrConditions());
    assertEquals("flag", condition.getCondition(0).getLeftValueName());
    assertEquals("new", condition.getCondition(0).getRightValueString());
    assertEquals("flag", condition.getCondition(1).getLeftValueName());
    assertEquals("changed", condition.getCondition(1).getRightValueString());
    assertEquals(Condition.Operator.OR, condition.getCondition(1).getOperator());

    InsertUpdateMeta insertUpdate = (InsertUpdateMeta) findInsertUpdate(pipeline).getTransform();
    assertEquals("d_date", insertUpdate.getInsertUpdateLookupField().getTableName());
    assertEquals(
        "date_key", insertUpdate.getInsertUpdateLookupField().getLookupKeys().get(0).getKeyStream());
    assertTrue(updates(insertUpdate, "full_date"));
    assertTrue(updates(insertUpdate, "load_dt"));
  }

  @Test
  void overwriteModeStaysOnInsertUpdate() throws Exception {
    DmDimension dimension = DmDateDimensionTemplate.createDateDimension(null);
    dimension.setType1UpdateMode(DmType1UpdateMode.OVERWRITE);

    PipelineMeta pipeline = generate(dimension);

    assertEquals(2, pipeline.getTransforms().size());
    assertEquals(1, count(pipeline, DateDimensionGeneratorMeta.class));
    assertEquals(1, count(pipeline, InsertUpdateMeta.class));
    assertEquals(0, count(pipeline, MergeRowsMeta.class));
  }

  private static PipelineMeta generate(DmDimension dimension) throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.setName("dates");
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    model.getTables().add(dimension);
    IHopMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return dimension
        .generateUpdatePipelines(metadataProvider, new Variables(), model, new Date())
        .get(0);
  }

  private static TransformMeta find(PipelineMeta pipeline, String name) {
    TransformMeta transform = pipeline.findTransform(name);
    assertTrue(transform != null, name);
    return transform;
  }

  private static TransformMeta findInsertUpdate(PipelineMeta pipeline) {
    return pipeline.getTransforms().stream()
        .filter(transform -> transform.getTransform() instanceof InsertUpdateMeta)
        .findFirst()
        .orElseThrow();
  }

  private static int count(PipelineMeta pipeline, Class<?> metaClass) {
    return (int)
        pipeline.getTransforms().stream()
            .filter(transform -> metaClass.isInstance(transform.getTransform()))
            .count();
  }

  private static List<String> selectedNames(SelectValuesMeta select) {
    return select.getSelectOption().getSelectFields().stream().map(SelectField::getName).toList();
  }

  private static boolean updates(InsertUpdateMeta insertUpdate, String fieldName) {
    for (InsertUpdateValue value : insertUpdate.getInsertUpdateLookupField().getValueFields()) {
      if (fieldName.equals(value.getUpdateStream())) {
        return value.isUpdate();
      }
    }
    return false;
  }
}
