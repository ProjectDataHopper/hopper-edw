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
package org.hopper.edw.datavault.hopgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionScdType;
import org.hopper.edw.datavault.metadata.dimensional.DmLayoutSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelTableLayoutPreviewSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void buildMetadataPreviewRowsIncludesFieldMetadata() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension
        .getAttributes()
        .add(new DmDimensionAttribute("customer_name", DmScdUpdatePolicy.TYPE1));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout =
        DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    ModelTableLayoutPreviewSupport.MetadataPreview preview =
        ModelTableLayoutPreviewSupport.buildMetadataPreviewRows(layout);

    assertEquals(5, preview.previewMeta().size());
    assertEquals(
        ModelTableLayoutPreviewSupport.COL_INDEX, preview.previewMeta().getValueMeta(0).getName());
    assertEquals(
        ModelTableLayoutPreviewSupport.COL_FIELD, preview.previewMeta().getValueMeta(1).getName());
    assertEquals(
        ModelTableLayoutPreviewSupport.COL_TYPE, preview.previewMeta().getValueMeta(2).getName());
    assertEquals(
        ModelTableLayoutPreviewSupport.COL_LENGTH, preview.previewMeta().getValueMeta(3).getName());
    assertEquals(
        ModelTableLayoutPreviewSupport.COL_PRECISION,
        preview.previewMeta().getValueMeta(4).getName());

    assertEquals(3, preview.previewRows().size());
    assertEquals(1L, preview.previewRows().get(0)[0]);
    assertEquals("customer_id", preview.previewRows().get(0)[1]);
    assertEquals("customer_name", preview.previewRows().get(1)[1]);
    assertEquals("load_dt", preview.previewRows().get(2)[1]);
    assertEquals(layout.searchValueMeta("load_dt").getTypeDesc(), preview.previewRows().get(2)[2]);
  }

  @Test
  void resolveDmTableLayoutPropagatesDuplicateColumnError() {
    DimensionalModel model = new DimensionalModel();
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_bad");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension.getAttributes().add(new DmDimensionAttribute("customer_id", DmScdUpdatePolicy.TYPE1));
    model.getTables().add(dimension);

    HopException exception =
        assertThrows(
            HopException.class,
            () ->
                ModelTableLayoutPreviewSupport.resolveDmTableLayout(
                    new MemoryMetadataProvider(), new Variables(), model, dimension));

    assertTrue(exception.getMessage().contains("Duplicate target column 'customer_id'"));
  }

  @Test
  void resolveDvTableLayoutReturnsProjectedColumns() throws HopException {
    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.setTableName("hub_customer");
    BusinessKey customerId = new BusinessKey("customer_id");
    customerId.setDataType("String");
    customerId.setLength("50");
    hub.getBusinessKeys().add(customerId);

    var layout =
        ModelTableLayoutPreviewSupport.resolveDvTableLayout(
            new MemoryMetadataProvider(),
            new Variables(),
            new org.hopper.edw.datavault.metadata.DataVaultModel(),
            hub);

    assertTrue(layout.indexOfValue("customer_id_hk") >= 0);
    assertTrue(layout.indexOfValue("customer_id") >= 0);
    assertTrue(layout.indexOfValue("LOAD_DATE") >= 0);
  }

  @Test
  void resolveBvTableLayoutRequiresReferencedDvModelPath() {
    BusinessVaultModel bvModel = new BusinessVaultModel();
    BvPitTable table = new BvPitTable();
    table.setName("pit_customer");

    HopException exception =
        assertThrows(
            HopException.class,
            () ->
                ModelTableLayoutPreviewSupport.resolveBvTableLayout(
                    new MemoryMetadataProvider(), new Variables(), bvModel, table));

    assertTrue(
        exception
            .getMessage()
            .contains("Business Vault model has no referenced Data Vault model path"));
  }

  @Test
  void createMetadataPreviewRowMetaHasExpectedColumns() {
    RowMeta rowMeta = (RowMeta) ModelTableLayoutPreviewSupport.createMetadataPreviewRowMeta();

    assertEquals(5, rowMeta.size());
    assertEquals(
        IValueMeta.TYPE_INTEGER,
        rowMeta.searchValueMeta(ModelTableLayoutPreviewSupport.COL_INDEX).getType());
    assertEquals(
        IValueMeta.TYPE_STRING,
        rowMeta.searchValueMeta(ModelTableLayoutPreviewSupport.COL_FIELD).getType());
    assertEquals(
        IValueMeta.TYPE_STRING,
        rowMeta.searchValueMeta(ModelTableLayoutPreviewSupport.COL_TYPE).getType());
    assertEquals(
        IValueMeta.TYPE_INTEGER,
        rowMeta.searchValueMeta(ModelTableLayoutPreviewSupport.COL_LENGTH).getType());
    assertEquals(
        IValueMeta.TYPE_INTEGER,
        rowMeta.searchValueMeta(ModelTableLayoutPreviewSupport.COL_PRECISION).getType());
  }

  @Test
  void buildMetadataPreviewRowsHandlesEmptyLayout() {
    ModelTableLayoutPreviewSupport.MetadataPreview preview =
        ModelTableLayoutPreviewSupport.buildMetadataPreviewRows(new RowMeta());

    assertEquals(5, preview.previewMeta().size());
    assertTrue(preview.previewRows().isEmpty());
  }

  @Test
  void buildMetadataPreviewRowsPreservesLengthAndPrecision() {
    RowMeta layout = new RowMeta();
    ValueMetaString valueMeta = new ValueMetaString("code");
    valueMeta.setLength(12);
    layout.addValueMeta(valueMeta);
    layout.addValueMeta(new ValueMetaTimestamp("load_dt"));

    ModelTableLayoutPreviewSupport.MetadataPreview preview =
        ModelTableLayoutPreviewSupport.buildMetadataPreviewRows(layout);

    assertEquals(2, preview.previewRows().size());
    assertEquals(12L, preview.previewRows().get(0)[3]);
    assertEquals((long) valueMeta.getPrecision(), preview.previewRows().get(0)[4]);
  }
}
