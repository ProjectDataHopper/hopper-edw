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
package org.apache.hop.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.datavault.metadata.CsvFieldOptions;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldInputOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvSourceFieldSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void roundTripsCsvInputOptionsBetweenCatalogAndSourceFields() {
    SourceField sourceField = new SourceField("amount");
    sourceField.setHopType(2);
    CsvFieldOptions csv = new CsvFieldOptions();
    csv.setFormat("#,##0.00");
    csv.setDecimalSymbol(",");
    csv.setGroupingSymbol(".");
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setCsv(csv);
    sourceField.setInputOptions(inputOptions);

    List<CatalogSourceField> catalogFields =
        DvSourceFieldSupport.toCatalogFields(List.of(sourceField));
    assertEquals(1, catalogFields.size());
    CatalogSourceField catalogField = catalogFields.getFirst();
    assertNotNull(catalogField.getInputOptions());
    assertNotNull(catalogField.getInputOptions().getCsv());
    assertEquals("#,##0.00", catalogField.getInputOptions().getCsv().getFormat());
    assertEquals(",", catalogField.getInputOptions().getCsv().getDecimalSymbol());
    assertEquals(".", catalogField.getInputOptions().getCsv().getGroupingSymbol());

    List<SourceField> restored = DvSourceFieldSupport.fromCatalogFields(catalogFields);
    assertEquals(1, restored.size());
    SourceField restoredField = restored.getFirst();
    assertNotNull(restoredField.getInputOptions());
    assertNotNull(restoredField.getInputOptions().getCsv());
    assertEquals("#,##0.00", restoredField.getInputOptions().getCsv().getFormat());
    assertEquals(",", restoredField.getInputOptions().getCsv().getDecimalSymbol());
    assertEquals(".", restoredField.getInputOptions().getCsv().getGroupingSymbol());
  }

  @Test
  void roundTripsPrimaryKeyPositionBetweenCatalogAndSourceFields() {
    SourceField sourceField = new SourceField("customer_id");
    sourceField.setPrimaryKeyPosition(2);

    List<CatalogSourceField> catalogFields =
        DvSourceFieldSupport.toCatalogFields(List.of(sourceField));
    assertEquals(2, catalogFields.getFirst().getPrimaryKeyPosition());

    List<SourceField> restored = DvSourceFieldSupport.fromCatalogFields(catalogFields);
    assertEquals(2, restored.getFirst().getPrimaryKeyPosition());
  }

  @Test
  void preservesNullInputOptions() {
    SourceField sourceField = new SourceField("name");
    List<CatalogSourceField> catalogFields =
        DvSourceFieldSupport.toCatalogFields(List.of(sourceField));
    assertNull(catalogFields.getFirst().getInputOptions());

    List<SourceField> restored = DvSourceFieldSupport.fromCatalogFields(catalogFields);
    assertNull(restored.getFirst().getInputOptions());
  }

  @Test
  void toCatalogFieldsNormalizesUnsetHopTypeToString() {
    SourceField sourceField = new SourceField("message_id");
    sourceField.setHopType(0);
    sourceField.setLength("36");

    List<CatalogSourceField> catalogFields =
        DvSourceFieldSupport.toCatalogFields(List.of(sourceField));
    assertEquals(IValueMeta.TYPE_STRING, catalogFields.getFirst().getHopType());
  }

  @Test
  void applyLayoutToDefinitionWritesStructuredFieldsAndDerivedRowMeta() throws Exception {
    SourceField partition = new SourceField("partition");
    partition.setHopType(IValueMeta.TYPE_INTEGER);
    partition.setLength("9");
    partition.setSourceDataType("Integer");

    SourceField ts = new SourceField("kafka_timestamp");
    ts.setHopType(IValueMeta.TYPE_TIMESTAMP);

    RecordDefinition definition = new RecordDefinition();
    definition.setType(RecordDefinitionType.DV_SOURCE);

    DvSourceFieldSupport.applyLayoutToDefinition(definition, List.of(partition, ts), null);

    assertNotNull(definition.getDvSource());
    assertEquals(2, definition.getDvSource().getFields().size());
    assertEquals(
        IValueMeta.TYPE_INTEGER, definition.getDvSource().getFields().getFirst().getHopType());
    assertEquals(
        IValueMeta.TYPE_TIMESTAMP, definition.getDvSource().getFields().get(1).getHopType());
    assertNotNull(definition.getFields());
    assertEquals(2, definition.getFields().size());
    assertEquals(IValueMeta.TYPE_INTEGER, definition.getFields().getValueMeta(0).getType());
    assertEquals(IValueMeta.TYPE_TIMESTAMP, definition.getFields().getValueMeta(1).getType());
  }

  @Test
  void applyLayoutToDefinitionWritesPhysicalTableFieldsForHub() throws Exception {
    SourceField customerId = new SourceField("customer_id");
    customerId.setHopType(IValueMeta.TYPE_INTEGER);
    customerId.setLength("9");

    RecordDefinition definition = new RecordDefinition();
    definition.setType(RecordDefinitionType.DV_HUB);
    org.apache.hop.catalog.model.PhysicalTableRef physical =
        new org.apache.hop.catalog.model.PhysicalTableRef();
    physical.setDatabaseMetaName("Vault");
    physical.setTableName("hub_customer");
    definition.setPhysicalTable(physical);

    DvSourceFieldSupport.applyLayoutToDefinition(definition, List.of(customerId), null);

    assertNotNull(definition.getPhysicalTable().getFields());
    assertEquals(1, definition.getPhysicalTable().getFields().size());
    assertEquals(
        IValueMeta.TYPE_INTEGER, definition.getPhysicalTable().getFields().getFirst().getHopType());
    assertEquals(IValueMeta.TYPE_INTEGER, definition.getFields().getValueMeta(0).getType());
    // Source home must stay empty for vault targets.
    assertTrue(
        definition.getDvSource() == null
            || definition.getDvSource().getFields() == null
            || definition.getDvSource().getFields().isEmpty());
  }

  @Test
  void applyLayoutToDefinitionPreservesForeignKeyMetadata() throws Exception {
    CatalogSourceField previous = new CatalogSourceField();
    previous.setName("order_id");
    previous.setHopType(IValueMeta.TYPE_STRING);
    previous.setFkConstraintName("fk_order");
    previous.setFkPosition(1);
    previous.setFkReferencedTable("order_header");
    previous.setFkReferencedColumn("order_id");

    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setFields(List.of(previous));

    RecordDefinition definition = new RecordDefinition();
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setDvSource(dvSource);

    SourceField updated = new SourceField("order_id");
    updated.setHopType(IValueMeta.TYPE_STRING);
    updated.setLength("7");

    DvSourceFieldSupport.applyLayoutToDefinition(definition, List.of(updated), null);

    CatalogSourceField after = definition.getDvSource().getFields().getFirst();
    assertEquals("fk_order", after.getFkConstraintName());
    assertEquals(1, after.getFkPosition());
    assertEquals("order_header", after.getFkReferencedTable());
    assertEquals("order_id", after.getFkReferencedColumn());
    assertEquals("7", after.getLength());
  }

  @Test
  void sourceFieldsFromDefinitionRepairsStringHopTypeFromSourceDataTypeLabel() throws Exception {
    CatalogSourceField demoScore = new CatalogSourceField();
    demoScore.setName("demo_score");
    demoScore.setSourceDataType("Integer");
    // Stale default: hopType was normalized to String while the real type stayed in sourceDataType.
    demoScore.setHopType(IValueMeta.TYPE_STRING);
    demoScore.setLength("4");

    CatalogSourceField loadDts = new CatalogSourceField();
    loadDts.setName("load_dts");
    loadDts.setSourceDataType("DATETIME(6)");
    loadDts.setHopType(IValueMeta.TYPE_STRING);

    CatalogSourceField segment = new CatalogSourceField();
    segment.setName("segment");
    segment.setSourceDataType("String");
    segment.setHopType(IValueMeta.TYPE_STRING);
    segment.setLength("20");

    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setFields(List.of(demoScore, loadDts, segment));

    RecordDefinition definition = new RecordDefinition();
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setDvSource(dvSource);

    List<SourceField> fields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    assertEquals(3, fields.size());
    assertEquals(IValueMeta.TYPE_INTEGER, fields.get(0).getHopType());
    assertEquals(IValueMeta.TYPE_TIMESTAMP, fields.get(1).getHopType());
    assertEquals(IValueMeta.TYPE_STRING, fields.get(2).getHopType());
  }

  @Test
  void sourceFieldsFromDefinitionPrefersStructuredTypesAndFillsGapsFromRowMeta() throws Exception {
    CatalogSourceField nestedPartition = new CatalogSourceField();
    nestedPartition.setName("partition");
    nestedPartition.setHopType(0);
    nestedPartition.setLength("9");

    CatalogSourceField nestedTs = new CatalogSourceField();
    nestedTs.setName("kafka_timestamp");
    nestedTs.setHopType(IValueMeta.TYPE_TIMESTAMP);

    CatalogSourceField nestedKey = new CatalogSourceField();
    nestedKey.setName("message_id");
    nestedKey.setHopType(IValueMeta.TYPE_STRING);
    nestedKey.setLength("36");
    nestedKey.setPrimaryKeyPosition(1);

    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setFields(List.of(nestedKey, nestedTs, nestedPartition));

    RowMeta rowMeta = new RowMeta();
    ValueMetaString messageId = new ValueMetaString("message_id");
    messageId.setLength(36);
    rowMeta.addValueMeta(messageId);
    rowMeta.addValueMeta(new ValueMetaTimestamp("kafka_timestamp"));
    ValueMetaInteger partition = new ValueMetaInteger("partition");
    partition.setLength(9);
    rowMeta.addValueMeta(partition);

    RecordDefinition definition = new RecordDefinition();
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setDvSource(dvSource);
    definition.setFields(rowMeta);

    List<SourceField> fields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    assertEquals(3, fields.size());
    assertEquals(IValueMeta.TYPE_STRING, fields.get(0).getHopType());
    assertEquals(1, fields.get(0).getPrimaryKeyPosition());
    assertEquals(IValueMeta.TYPE_TIMESTAMP, fields.get(1).getHopType());
    // Nested hopType was 0; row meta fills Integer.
    assertEquals(IValueMeta.TYPE_INTEGER, fields.get(2).getHopType());
  }

  @Test
  void synchronizeLayoutAfterLoadRepairsNestedHopTypesAndAlignsRowMeta() throws Exception {
    CatalogSourceField nested = new CatalogSourceField();
    nested.setName("partition");
    nested.setHopType(0);
    nested.setPrimaryKeyPosition(0);

    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setFields(List.of(nested));

    RowMeta rowMeta = new RowMeta();
    ValueMetaInteger partition = new ValueMetaInteger("partition");
    partition.setLength(9);
    rowMeta.addValueMeta(partition);

    RecordDefinition definition = new RecordDefinition();
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setDvSource(dvSource);
    definition.setFields(rowMeta);

    DvSourceFieldSupport.synchronizeLayoutAfterLoad(definition);

    assertEquals(
        IValueMeta.TYPE_INTEGER, definition.getDvSource().getFields().getFirst().getHopType());
    assertEquals(IValueMeta.TYPE_INTEGER, definition.getFields().getValueMeta(0).getType());
    assertTrue(definition.getFields().getValueMeta(0).getLength() >= 9
        || "9".equals(definition.getDvSource().getFields().getFirst().getLength()));
  }

  @Test
  void prepareForPersistenceDerivesRowMetaFromStructuredFields() throws Exception {
    CatalogSourceField nested = new CatalogSourceField();
    nested.setName("customer_id");
    nested.setHopType(IValueMeta.TYPE_INTEGER);
    nested.setLength("9");

    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setFields(List.of(nested));

    // Stale row meta deliberately wrong.
    RowMeta stale = new RowMeta();
    stale.addValueMeta(new ValueMetaString("customer_id"));

    RecordDefinition definition = new RecordDefinition();
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setDvSource(dvSource);
    definition.setFields(stale);

    DvSourceFieldSupport.prepareForPersistence(definition);

    assertEquals(IValueMeta.TYPE_INTEGER, definition.getFields().getValueMeta(0).getType());
  }

  @Test
  void sourceFieldsFromDefinitionFallsBackToNestedWhenRowMetaEmpty() throws Exception {
    CatalogSourceField nested = new CatalogSourceField();
    nested.setName("customer_id");
    nested.setHopType(IValueMeta.TYPE_INTEGER);
    nested.setPrimaryKeyPosition(1);

    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setFields(List.of(nested));

    RecordDefinition definition = new RecordDefinition();
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setDvSource(dvSource);
    definition.setFields(new RowMeta());

    List<SourceField> fields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    assertEquals(1, fields.size());
    assertEquals(IValueMeta.TYPE_INTEGER, fields.getFirst().getHopType());
    assertEquals(1, fields.getFirst().getPrimaryKeyPosition());
  }
}
