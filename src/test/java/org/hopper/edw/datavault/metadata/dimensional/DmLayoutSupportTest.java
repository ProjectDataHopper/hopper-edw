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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmLayoutSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void type1DimensionLayoutIncludesNaturalKeysAttributesAndLoadDate() throws HopException {
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

    assertEquals(3, layout.size());
    assertTrue(layout.indexOfValue("customer_id") >= 0);
    assertTrue(layout.indexOfValue("customer_name") >= 0);
    assertTrue(layout.indexOfValue("load_dt") >= 0);
  }

  @Test
  void type2DimensionLayoutIncludesScdControlColumns() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension
        .getAttributes()
        .add(new DmDimensionAttribute("customer_name", DmScdUpdatePolicy.TYPE2));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout =
        DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    assertTrue(layout.indexOfValue("dim_key") >= 0);
    assertTrue(layout.indexOfValue("customer_id") >= 0);
    assertTrue(layout.indexOfValue("version") >= 0);
    assertEquals(
        DmLayoutSupport.DEFAULT_INTEGER_FIELD_LENGTH,
        layout.searchValueMeta("version").getLength());
    assertEquals(IValueMeta.TYPE_INTEGER, layout.searchValueMeta("version").getType());
    assertTrue(layout.indexOfValue("date_from") >= 0);
    assertTrue(layout.indexOfValue("date_to") >= 0);
    assertTrue(layout.indexOfValue("is_current") >= 0);
    assertTrue(layout.indexOfValue("customer_name") >= 0);
    assertTrue(layout.indexOfValue("load_dt") >= 0);
  }

  @Test
  void type1DateDimensionLayoutUsesSourceIntegerTypeForNaturalKey() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_date");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("date_key"));
    dimension.getAttributes().add(new DmDimensionAttribute("full_date", DmScdUpdatePolicy.TYPE1));

    IRowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaInteger("date_key"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildDimensionTargetTableLayout(
            dimension, config, new Variables(), sourceRowMeta);

    assertEquals(IValueMeta.TYPE_INTEGER, layout.searchValueMeta("date_key").getType());
  }

  @Test
  void type1DateDimensionUsesNaturalKeyForLookup() {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_date");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("date_key"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    assertEquals(
        "date_key",
        DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, new Variables()));
  }

  @Test
  void type2DimensionUsesConfiguredSurrogateKeyForLookup() {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    assertEquals(
        "dim_key",
        DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, new Variables()));
  }

  @Test
  void type2DimensionWithCustomSurrogateFieldUsesConfiguredColumn() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.setSurrogateKeyField("customer_key");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout =
        DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    assertTrue(layout.indexOfValue("customer_key") >= 0);
    assertEquals(
        "customer_key",
        DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, new Variables()));
  }

  @Test
  void type1UseSourceFieldSurrogateUsesStringColumnInLayout() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("d_warehouse");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    dimension.setSurrogateKeyField("warehouse_hk");
    dimension.setSurrogateKeySourceField("warehouse_hk");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("warehouse_id"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout =
        DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    assertTrue(layout.indexOfValue("warehouse_hk") >= 0);
    assertTrue(layout.indexOfValue("warehouse_id") >= 0);
  }

  @Test
  void useSourceFieldSurrogateUsesStringColumnInLayout() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    dimension.setSurrogateKeyField("customer_hk");
    dimension.setSurrogateKeySourceField("customer_hk");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout =
        DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    assertTrue(layout.indexOfValue("customer_hk") >= 0);
    assertEquals(
        org.apache.hop.core.row.IValueMeta.TYPE_STRING,
        layout.searchValueMeta("customer_hk").getType());
  }

  @Test
  void factLayoutIncludesRangeDimensionTargetFields() throws HopException {
    DimensionalModel model = new DimensionalModel();
    DmRangeDimension amountBand = new DmRangeDimension();
    amountBand.setName("dim_amount_band");
    amountBand.getBands().add(new DmRangeBand("0", "101", "small"));
    model.getTables().add(amountBand);

    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.getMeasures().add(new DmFactMeasure("amount", true));
    fact.getRangeDimensionRoles()
        .add(new DmFactRangeDimensionRole("dim_amount_band", "amount", "amount_band"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildFactTargetTableLayout(fact, model, config, new Variables());

    assertTrue(layout.indexOfValue("amount_band") >= 0);
    assertEquals(
        org.apache.hop.core.row.IValueMeta.TYPE_STRING,
        layout.searchValueMeta("amount_band").getType());
    assertTrue(layout.indexOfValue("amount") >= 0);
  }

  @Test
  void factLayoutIncludesDegenerateDimensionFields() throws HopException {
    DimensionalModel model = new DimensionalModel();
    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.setScdType(DmDimensionScdType.TYPE1);
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    model.getTables().add(customer);

    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.getDimensionRoles().add(new DmFactDimensionRole("dim_customer", "customer_key"));
    fact.getMeasures().add(new DmFactMeasure("amount", true));
    fact.getDegenerateDimensions().add(new DmFactDegenerateDimension("order_number"));
    fact.getDegenerateDimensions().add(new DmFactDegenerateDimension("invoice_number"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildFactTargetTableLayout(fact, model, config, new Variables());

    assertTrue(layout.indexOfValue("order_number") >= 0);
    assertTrue(layout.indexOfValue("invoice_number") >= 0);
    assertTrue(layout.indexOfValue("amount") >= 0);
    assertTrue(layout.indexOfValue("customer_key") >= 0);
  }

  @Test
  void factForeignKeyUsesStringTypeForUseSourceFieldDimension() throws HopException {
    DimensionalModel model = new DimensionalModel();
    DmDimension order = new DmDimension();
    order.setName("d_order");
    order.setScdType(DmDimensionScdType.TYPE2);
    order.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    order.setSurrogateKeyField("order_hk");
    order.setSurrogateKeySourceField("order_hk");
    order.getNaturalKeys().add(new DmNaturalKeyField("order_id"));
    model.getTables().add(order);

    DmFact fact = new DmFact();
    fact.setName("f_orders");
    fact.getDimensionRoles().add(new DmFactDimensionRole("d_order", "order_hk"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildFactTargetTableLayout(fact, model, config, new Variables());

    assertEquals(IValueMeta.TYPE_STRING, layout.searchValueMeta("order_hk").getType());
  }

  @Test
  void factForeignKeyUsesIntegerTypeForAutoIncrementDimension() throws HopException {
    DimensionalModel model = new DimensionalModel();
    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.setScdType(DmDimensionScdType.TYPE2);
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    model.getTables().add(customer);

    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.getDimensionRoles().add(new DmFactDimensionRole("dim_customer", "customer_key"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildFactTargetTableLayout(fact, model, config, new Variables());

    assertEquals(IValueMeta.TYPE_INTEGER, layout.searchValueMeta("customer_key").getType());
  }

  @Test
  void factForeignKeyUsesIntegerTypeForDateRolePlaying() throws HopException {
    DimensionalModel model = new DimensionalModel();
    DmDimension date = new DmDimension();
    date.setName("d_date");
    date.setScdType(DmDimensionScdType.TYPE1);
    date.getNaturalKeys().add(new DmNaturalKeyField("date_key"));
    model.getTables().add(date);

    DmFact fact = new DmFact();
    fact.setName("f_orders");
    DmFactDimensionRole role = new DmFactDimensionRole("d_order_date", "order_date_key");
    role.setTruncateToDateKey(true);
    role.setSourceFieldName("order_date");
    fact.getDimensionRoles().add(role);

    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName("d_order_date");
    alias.setReferencedDimensionName("d_date");
    model.getTables().add(alias);

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildFactTargetTableLayout(fact, model, config, new Variables());

    assertEquals(IValueMeta.TYPE_INTEGER, layout.searchValueMeta("order_date_key").getType());
    assertEquals(
        DmLayoutSupport.DATE_KEY_FIELD_LENGTH,
        layout.searchValueMeta("order_date_key").getLength());
  }

  @Test
  void dimensionLayoutDoesNotDuplicateConfiguredLoadDateAttribute() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_date");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("date_key"));
    dimension.getAttributes().add(new DmDimensionAttribute("full_date", DmScdUpdatePolicy.TYPE1));
    dimension.getAttributes().add(new DmDimensionAttribute("load_dt", DmScdUpdatePolicy.TYPE1));

    IRowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaTimestamp("load_dt"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildDimensionTargetTableLayout(
            dimension, config, new Variables(), sourceRowMeta);

    assertEquals(3, layout.size());
    assertTrue(layout.indexOfValue("load_dt") >= 0);
    assertEquals(IValueMeta.TYPE_TIMESTAMP, layout.searchValueMeta("load_dt").getType());
  }

  @Test
  void factLayoutIncludesForeignKeysAndMeasures() throws HopException {
    DimensionalModel model = new DimensionalModel();
    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    model.getTables().add(customer);

    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.getDimensionRoles()
        .add(new DmFactDimensionRole("dim_customer", "Customer", "customer_key"));
    fact.getMeasures().add(new DmFactMeasure("quantity", true));
    fact.getMeasures().add(new DmFactMeasure("amount", true));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout = DmLayoutSupport.buildFactTargetTableLayout(fact, model, config, new Variables());

    assertEquals(3, layout.size());
    assertTrue(layout.indexOfValue("customer_key") >= 0);
    assertTrue(layout.indexOfValue("quantity") >= 0);
    assertTrue(layout.indexOfValue("amount") >= 0);
  }

  @Test
  void junkDimensionSharedHashColumnProducesSingleSurrogateColumn() throws HopException {
    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("d_orders_junk");
    junk.setSurrogateKeyField("orders_junk_hk");
    junk.setSurrogateKeyStrategy(DmJunkSurrogateKeyStrategy.COMPUTE_HASH_KEY);
    junk.setHashCodeStrategy(DmJunkHashCodeStrategy.MD5);
    junk.setUseSurrogateKeyAsHashCodeField(true);
    junk.getKeyFields().add(new DmNaturalKeyField("j1"));
    junk.getKeyFields().add(new DmNaturalKeyField("j2"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildJunkDimensionTargetTableLayout(junk, config, new Variables());

    assertEquals(4, layout.size());
    assertTrue(layout.indexOfValue("orders_junk_hk") >= 0);
    assertEquals(-1, layout.indexOfValue("hashcode"));
    assertTrue(layout.indexOfValue("j1") >= 0);
    assertTrue(layout.indexOfValue("j2") >= 0);
    assertTrue(layout.indexOfValue("load_dt") >= 0);
  }

  @Test
  void junkDimensionSeparateHashColumnWhenCheckboxUnset() throws HopException {
    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("junk_flags");
    junk.setSurrogateKeyField("junk_key");
    junk.setHashCodeStrategy(DmJunkHashCodeStrategy.MD5);
    junk.getKeyFields().add(new DmNaturalKeyField("flag_a"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildJunkDimensionTargetTableLayout(junk, config, new Variables());

    assertTrue(layout.indexOfValue("junk_key") >= 0);
    assertTrue(layout.indexOfValue("hashcode") >= 0);
    assertTrue(layout.indexOfValue("flag_a") >= 0);
  }
}
