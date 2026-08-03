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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAlias;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionScdType;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField;
import org.apache.hop.datavault.metadata.dimensional.DmRangeBand;
import org.apache.hop.datavault.metadata.dimensional.DmRangeDimension;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmTableDisplaySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveTableIconPathUsesDimensionIcons() {
    assertEquals(
        DmTableDisplaySupport.DIMENSION_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.DIMENSION));
    assertEquals(
        DmTableDisplaySupport.DIMENSION_ALIAS_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.DIMENSION_ALIAS));
    assertEquals(
        DmTableDisplaySupport.JUNK_DIMENSION_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.JUNK_DIMENSION));
    assertEquals(
        DmTableDisplaySupport.RANGE_DIMENSION_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.RANGE_DIMENSION));
    assertEquals(
        DmTableDisplaySupport.BRIDGE_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.BRIDGE));
    assertEquals(
        DmTableDisplaySupport.DIMENSION_ICON, DmTableDisplaySupport.resolveTableIconPath(null));
  }

  @Test
  void resolveTableIconPathUsesFactIconForFacts() {
    assertEquals(
        DmTableDisplaySupport.FACT_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.FACT));
    assertEquals(
        DmTableDisplaySupport.FACT_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.FACTLESS_FACT));
    assertEquals(
        DmTableDisplaySupport.FACT_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.PERIODIC_SNAPSHOT_FACT));
    assertEquals(
        DmTableDisplaySupport.FACT_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.ACCUMULATING_SNAPSHOT_FACT));
    assertEquals(
        DmTableDisplaySupport.FACT_ICON,
        DmTableDisplaySupport.resolveTableIconPath(DmTableType.AGGREGATE_FACT));
  }

  @Test
  void resolveSecondaryFieldNameForType2Dimension() {
    DimensionalModel model = new DimensionalModel();
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));

    assertEquals(
        "dim_key",
        DmTableDisplaySupport.resolveSecondaryFieldName(
            dimension, model, new Variables(), new MemoryMetadataProvider()));
  }

  @Test
  void resolveSecondaryFieldNameForType1DimensionIsNull() {
    DimensionalModel model = new DimensionalModel();
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_date");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("date_key"));

    assertNull(
        DmTableDisplaySupport.resolveSecondaryFieldName(
            dimension, model, new Variables(), new MemoryMetadataProvider()));
  }

  @Test
  void resolveSecondaryFieldNameForAliasUsesTargetDimension() {
    DimensionalModel model = new DimensionalModel();
    DmDimension target = new DmDimension();
    target.setName("dim_customer");
    target.setScdType(DmDimensionScdType.TYPE2);
    target.setSurrogateKeyField("customer_key");
    target.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    model.getTables().add(target);

    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName("dim_customer_alias");
    alias.setReferencedDimensionName("dim_customer");
    model.getTables().add(alias);

    assertEquals(
        "customer_key",
        DmTableDisplaySupport.resolveSecondaryFieldName(
            alias, model, new Variables(), new MemoryMetadataProvider()));
  }

  @Test
  void resolveSecondaryFieldNameForJunkDimension() {
    DimensionalModel model = new DimensionalModel();
    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("dim_flags");
    junk.setSurrogateKeyField("junk_key");

    assertEquals(
        "junk_key",
        DmTableDisplaySupport.resolveSecondaryFieldName(
            junk, model, new Variables(), new MemoryMetadataProvider()));
  }

  @Test
  void resolveSecondaryFieldNameForRangeDimensionShowsBandCount() {
    DimensionalModel model = new DimensionalModel();
    DmRangeDimension range = new DmRangeDimension();
    range.setName("dim_amount_band");
    range.getBands().add(new DmRangeBand("0", "100", "small"));
    range.getBands().add(new DmRangeBand("100", "500", "medium"));

    assertEquals(
        "2 bands",
        DmTableDisplaySupport.resolveSecondaryFieldName(
            range, model, new Variables(), new MemoryMetadataProvider()));
  }

  @Test
  void resolveSecondaryFieldNameForFactIsNull() {
    DimensionalModel model = new DimensionalModel();
    DmFact fact = new DmFact();
    fact.setName("fact_sales");

    assertNull(
        DmTableDisplaySupport.resolveSecondaryFieldName(
            fact, model, new Variables(), new MemoryMetadataProvider()));
  }
}
