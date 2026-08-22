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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class DmJunkDimensionSupportTest {

  @Test
  void applyFactTableSourceSetsSourceAndLegacyFields() {
    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("junk_flags");

    DmJunkDimensionSupport.applyFactTableSource(junk, "fact_sales");

    assertTrue(junk.getSourceOrDefault().isFactTableSource());
    assertEquals("fact_sales", junk.getSourceOrDefault().getSourceFactTableName());
    assertTrue(junk.isLoadFromFactTable());
    assertEquals("fact_sales", junk.getFactTableName());
    assertTrue(DmJunkDimensionSupport.isFactEmbedded(junk));
    assertFalse(DmJunkDimensionSupport.requiresStandalonePipeline(junk));
  }

  @Test
  void clearFactTableSourceRemovesInlineFlags() {
    DmJunkDimension junk = new DmJunkDimension();
    DmJunkDimensionSupport.applyFactTableSource(junk, "fact_sales");
    junk.getSourceOrDefault().setSourceType(DmSourceType.SQL);

    DmJunkDimensionSupport.clearFactTableSource(junk);

    assertFalse(junk.isLoadFromFactTable());
    assertEquals(null, junk.getFactTableName());
    assertEquals(null, junk.getSourceOrDefault().getSourceFactTableName());
    assertFalse(DmJunkDimensionSupport.isFactEmbedded(junk));
  }

  @Test
  void resolveFactTableNamePrefersSourceConfiguration() {
    DmJunkDimension junk = new DmJunkDimension();
    junk.getSourceOrDefault().setSourceType(DmSourceType.FACT_TABLE);
    junk.getSourceOrDefault().setSourceFactTableName("fact_orders");
    junk.setFactTableName("legacy_fact");

    assertEquals("fact_orders", DmJunkDimensionSupport.resolveFactTableName(junk, new Variables()));
  }

  @Test
  void resolveFactTableNameFallsBackToLegacyField() {
    DmJunkDimension junk = new DmJunkDimension();
    junk.setLoadFromFactTable(true);
    junk.setFactTableName("legacy_fact");

    assertEquals("legacy_fact", DmJunkDimensionSupport.resolveFactTableName(junk, new Variables()));
  }

  @Test
  void listFactTableNamesReturnsFactLikeTables() {
    DimensionalModel model = new DimensionalModel();
    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    model.getTables().add(fact);
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    model.getTables().add(dimension);

    assertArrayEquals(
        new String[] {"fact_sales"}, DmJunkDimensionSupport.listFactTableNames(model));
  }

  @Test
  void defaultForeignKeyForJunkStripsDimPrefix() {
    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("dim_orders_junk");

    assertEquals("orders_junk_key", DmJunkDimensionSupport.defaultForeignKeyForJunk(junk));
  }

  @Test
  void resolveJunkHashCodeFieldUsesSurrogateKeyWhenCheckboxSet() {
    DmJunkDimension junk = new DmJunkDimension();
    junk.setSurrogateKeyField("orders_junk_hk");
    junk.setUseSurrogateKeyAsHashCodeField(true);
    DimensionalConfiguration config = new DimensionalConfiguration();

    assertEquals(
        "orders_junk_hk",
        DmJunkDimensionSupport.resolveJunkHashCodeField(junk, config, new Variables()));
    assertTrue(DmJunkDimensionSupport.sharesHashAndSurrogateColumn(junk, config, new Variables()));
  }

  @Test
  void resolveJunkHashCodeFieldDefaultsToHashcodeWhenUnchecked() {
    DmJunkDimension junk = new DmJunkDimension();
    junk.setHashCodeStrategy(DmJunkHashCodeStrategy.MD5);
    DimensionalConfiguration config = new DimensionalConfiguration();

    assertEquals(
        "hashcode", DmJunkDimensionSupport.resolveJunkHashCodeField(junk, config, new Variables()));
    assertFalse(DmJunkDimensionSupport.sharesHashAndSurrogateColumn(junk, config, new Variables()));
  }
}
