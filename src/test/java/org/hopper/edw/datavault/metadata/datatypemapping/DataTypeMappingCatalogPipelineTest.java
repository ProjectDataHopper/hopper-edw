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
import org.hopper.edw.catalog.model.CatalogFieldConversionOptions;
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.hopper.edw.catalog.model.CatalogSourceFieldInputOptions;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.SourceFieldInputOptions;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.junit.jupiter.api.Test;

class DataTypeMappingCatalogPipelineTest {

  @Test
  void sourceFieldsWithLengthNeedSelectValues() {
    SourceField field = new SourceField("name");
    field.setHopType(IValueMeta.TYPE_STRING);
    field.setLength("2000");
    assertTrue(DataTypeMappingPipelineSupport.needsSelectValuesFromSourceFields(List.of(field)));
  }

  @Test
  void plainTypeOnlyDoesNotNeedSelectValues() {
    SourceField field = new SourceField("id");
    field.setHopType(IValueMeta.TYPE_INTEGER);
    assertFalse(DataTypeMappingPipelineSupport.needsSelectValuesFromSourceFields(List.of(field)));
  }

  @Test
  void conversionMaskProducesMetaChange() {
    SourceField field = new SourceField("created_at");
    field.setHopType(IValueMeta.TYPE_TIMESTAMP);
    field.setSourceStreamName("created_at");
    FieldConversionOptions conv = new FieldConversionOptions();
    conv.setConversionMask("yyyy-MM-dd HH:mm:ss");
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setConversion(conv);
    field.setInputOptions(inputOptions);

    SelectValuesMeta select =
        DataTypeMappingPipelineSupport.buildSelectValuesMetaFromSourceFields(List.of(field));
    List<SelectMetadataChange> meta = select.getSelectOption().getMeta();
    assertEquals(1, meta.size());
    assertEquals("yyyy-MM-dd HH:mm:ss", meta.get(0).getConversionMask());
    assertEquals(
        DataTypeMappingPatternSupport.hopTypeName(IValueMeta.TYPE_TIMESTAMP),
        meta.get(0).getType());
  }

  @Test
  void typeOnlyCatalogFieldDoesNotProduceMetaChange() {
    SourceField loadDate = new SourceField("load_date");
    loadDate.setHopType(IValueMeta.TYPE_TIMESTAMP);
    SourceField customerId = new SourceField("customer_id");
    customerId.setHopType(IValueMeta.TYPE_INTEGER);
    customerId.setLength("9");

    SelectValuesMeta select =
        DataTypeMappingPipelineSupport.buildSelectValuesMetaFromSourceFields(
            List.of(customerId, loadDate));
    List<SelectMetadataChange> meta = select.getSelectOption().getMeta();
    assertEquals(1, meta.size());
    assertEquals("customer_id", meta.get(0).getName());
    assertFalse(DataTypeMappingPipelineSupport.needsMetadataChange(loadDate));
    assertTrue(DataTypeMappingPipelineSupport.needsMetadataChange(customerId));
  }

  @Test
  void rewriteSourceIndicatorLooksUpVaultAlias() {
    SourceField recordSource = new SourceField("record_source");
    recordSource.setHopType(IValueMeta.TYPE_STRING);
    recordSource.setLength("30");
    SourceField customerId = new SourceField("customer_id");
    customerId.setHopType(IValueMeta.TYPE_INTEGER);
    customerId.setLength("9");

    List<SourceField> aligned =
        DataTypeMappingPipelineSupport.rewriteSourceIndicatorLookup(
            List.of(customerId, recordSource), "record_source", "x_record_source");
    List<SourceField> onStream =
        DataTypeMappingPipelineSupport.filterToStreamFields(
            aligned, List.of("customer_id", "x_record_source"));

    SelectValuesMeta select =
        DataTypeMappingPipelineSupport.buildSelectValuesMetaFromSourceFields(onStream);
    List<String> metaNames =
        select.getSelectOption().getMeta().stream().map(SelectMetadataChange::getName).toList();
    assertTrue(metaNames.contains("customer_id"));
    assertTrue(metaNames.contains("x_record_source"));
    assertFalse(metaNames.contains("record_source"));
  }

  @Test
  void filterToStreamFieldsDropsColumnsNotOnTheSourceStream() {
    SourceField loadDate = new SourceField("load_date");
    loadDate.setHopType(IValueMeta.TYPE_TIMESTAMP);
    FieldConversionOptions conv = new FieldConversionOptions();
    conv.setConversionMask("yyyy-MM-dd");
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setConversion(conv);
    loadDate.setInputOptions(inputOptions);
    SourceField customerId = new SourceField("customer_id");
    customerId.setHopType(IValueMeta.TYPE_INTEGER);
    customerId.setLength("9");

    List<SourceField> filtered =
        DataTypeMappingPipelineSupport.filterToStreamFields(
            List.of(customerId, loadDate), List.of("customer_id", "record_source"));

    assertEquals(1, filtered.size());
    assertEquals("customer_id", filtered.get(0).getName());
    SelectValuesMeta select =
        DataTypeMappingPipelineSupport.buildSelectValuesMetaFromSourceFields(filtered);
    assertTrue(
        select.getSelectOption().getMeta().stream()
            .noneMatch(change -> "load_date".equalsIgnoreCase(change.getName())));
  }

  @Test
  void renameUsesStreamNameInMeta() {
    SourceField field = new SourceField("customer_id");
    field.setSourceStreamName("CUST_ID");
    field.setHopType(IValueMeta.TYPE_INTEGER);
    field.setLength("9");

    SelectValuesMeta select =
        DataTypeMappingPipelineSupport.buildSelectValuesMetaFromSourceFields(List.of(field));
    assertFalse(select.getSelectOption().getSelectFields().isEmpty());
    assertEquals("CUST_ID", select.getSelectOption().getSelectFields().get(0).getName());
    assertEquals("customer_id", select.getSelectOption().getSelectFields().get(0).getRename());
    assertEquals("CUST_ID", select.getSelectOption().getMeta().get(0).getName());
    assertEquals("customer_id", select.getSelectOption().getMeta().get(0).getRename());
  }

  @Test
  void catalogRoundTripPreservesConversionAndStreamName() throws Exception {
    SourceField field = new SourceField("customer_id");
    field.setSourceStreamName("CUST_ID");
    field.setHopType(IValueMeta.TYPE_INTEGER);
    field.setLength("9");
    FieldConversionOptions conv = new FieldConversionOptions();
    conv.setConversionMask("0");
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setConversion(conv);
    field.setInputOptions(inputOptions);

    List<CatalogSourceField> catalog = DvSourceFieldSupport.toCatalogFields(List.of(field));
    assertEquals(1, catalog.size());
    assertEquals("CUST_ID", catalog.get(0).getSourceStreamName());
    assertNotNullConversion(catalog.get(0));

    List<SourceField> restored = DvSourceFieldSupport.fromCatalogFields(catalog);
    assertEquals("customer_id", restored.get(0).getName());
    assertEquals("CUST_ID", restored.get(0).getSourceStreamName());
    assertEquals("0", restored.get(0).getInputOptions().getConversion().getConversionMask());
  }

  private static void assertNotNullConversion(CatalogSourceField field) {
    CatalogSourceFieldInputOptions options = field.getInputOptions();
    assertTrue(options != null);
    CatalogFieldConversionOptions conversion = options.getConversion();
    assertTrue(conversion != null);
    assertEquals("0", conversion.getConversionMask());
  }
}
