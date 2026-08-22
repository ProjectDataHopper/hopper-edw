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
package org.hopper.edw.datavault.metadata.datatypemapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.junit.jupiter.api.Test;

class DataTypeMappingPipelineSupportTest {

  @Test
  void needsSelectValuesWhenMapped() {
    EffectiveSourceField field = new EffectiveSourceField();
    field.setSourceFieldName("name");
    field.setEffectiveFieldName("name");
    field.setHopType(IValueMeta.TYPE_STRING);
    field.setLength("2000");
    field.setLengthChanged(true);

    assertTrue(DataTypeMappingPipelineSupport.needsSelectValues(List.of(field)));
  }

  @Test
  void doesNotNeedSelectValuesWhenUnmapped() {
    EffectiveSourceField field = new EffectiveSourceField();
    field.setSourceFieldName("id");
    field.setEffectiveFieldName("id");
    field.setHopType(IValueMeta.TYPE_INTEGER);

    assertFalse(DataTypeMappingPipelineSupport.needsSelectValues(List.of(field)));
  }

  @Test
  void buildSelectValuesIncludesMetaChange() {
    EffectiveSourceField field = new EffectiveSourceField();
    field.setSourceFieldName("created_at");
    field.setEffectiveFieldName("created_at");
    field.setHopType(IValueMeta.TYPE_TIMESTAMP);
    field.setTypeChanged(true);
    field.setConversionChanged(true);
    field.getConversion().setConversionMask("yyyy-MM-dd HH:mm:ss");

    SelectValuesMeta select = DataTypeMappingPipelineSupport.buildSelectValuesMeta(List.of(field));
    List<SelectMetadataChange> meta = select.getSelectOption().getMeta();
    assertEquals(1, meta.size());
    assertEquals("created_at", meta.get(0).getName());
    assertEquals(
        DataTypeMappingPatternSupport.hopTypeName(IValueMeta.TYPE_TIMESTAMP),
        meta.get(0).getType());
    assertEquals("yyyy-MM-dd HH:mm:ss", meta.get(0).getConversionMask());
  }

  @Test
  void renameProducesSelectField() {
    EffectiveSourceField field = new EffectiveSourceField();
    field.setSourceFieldName("CUST_ID");
    field.setEffectiveFieldName("customer_id");
    field.setHopType(IValueMeta.TYPE_INTEGER);
    field.setLength("9");
    field.setRenamed(true);
    field.setTypeChanged(false);
    field.setLengthChanged(true);

    SelectValuesMeta select = DataTypeMappingPipelineSupport.buildSelectValuesMeta(List.of(field));
    assertFalse(select.getSelectOption().getSelectFields().isEmpty());
    assertEquals("CUST_ID", select.getSelectOption().getSelectFields().get(0).getName());
    assertEquals("customer_id", select.getSelectOption().getSelectFields().get(0).getRename());
  }
}
