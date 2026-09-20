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
package org.hopper.edw.datavault.presentation.fact;

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactlessFact;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;

public final class FactCrosstabTestModels {

  private FactCrosstabTestModels() {}

  public static DimensionalModel star() {
    DimensionalModel model = new DimensionalModel();
    model.setName("sales-dm");
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.setTableName("d_customer");
    customer.setSurrogateKeyField("dim_key");
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_nk"));
    customer.getAttributes().add(new DmDimensionAttribute("customer_name"));
    customer.getAttributes().add(new DmDimensionAttribute("country"));
    model.getTables().add(customer);

    DmDimension date = new DmDimension();
    date.setName("dim_date");
    date.setTableName("d_date");
    date.setSurrogateKeyField("dim_key");
    date.getNaturalKeys().add(new DmNaturalKeyField("date_nk"));
    date.getAttributes().add(new DmDimensionAttribute("year"));
    date.getAttributes().add(new DmDimensionAttribute("month"));
    model.getTables().add(date);

    DmDimensionAlias orderDate = new DmDimensionAlias();
    orderDate.setName("dim_order_date");
    orderDate.setReferencedDimensionName("dim_date");
    orderDate.setTableName("d_date");
    model.getTables().add(orderDate);

    DmDimensionAlias shipDate = new DmDimensionAlias();
    shipDate.setName("dim_ship_date");
    shipDate.setReferencedDimensionName("dim_date");
    shipDate.setTableName("d_date");
    model.getTables().add(shipDate);

    DmJunkDimension flags = new DmJunkDimension();
    flags.setName("dim_order_flags");
    flags.setTableName("d_order_flags");
    flags.setSurrogateKeyField("dim_key");
    flags.getKeyFields().add(new DmNaturalKeyField("channel"));
    model.getTables().add(flags);

    DmFact fact = new DmFact();
    fact.setName("f_order_lines");
    fact.setTableName("f_order_lines");
    fact.getDimensionRoles().add(new DmFactDimensionRole("dim_customer", "customer_key"));
    fact.getDimensionRoles().add(new DmFactDimensionRole("dim_order_date", "order_date_key"));
    fact.getDimensionRoles().add(new DmFactDimensionRole("dim_ship_date", "ship_date_key"));
    fact.getJunkDimensionRoles().add(new DmFactJunkDimensionRole("dim_order_flags", "flags_key"));
    fact.getRangeDimensionRoles()
        .add(new DmFactRangeDimensionRole("dim_qty_band", "quantity", "qty_band"));
    fact.getMeasures().add(new DmFactMeasure("quantity", true));
    fact.getMeasures().add(new DmFactMeasure("amount", true));
    fact.getDegenerateDimensions().add(new DmFactDegenerateDimension("order_id"));
    model.getTables().add(fact);
    return model;
  }

  static IDmFactLikeTable fact(DimensionalModel model) {
    return (IDmFactLikeTable) model.findTable("f_order_lines");
  }

  static DimensionalModel factlessStar() {
    DimensionalModel model = star();
    model.getTables().removeIf(t -> "f_order_lines".equals(t.getName()));
    DmFactlessFact factless = new DmFactlessFact();
    factless.setName("f_coverage");
    factless.setTableName("f_coverage");
    factless.getDimensionRoles().add(new DmFactDimensionRole("dim_customer", "customer_key"));
    factless.getDegenerateDimensions().add(new DmFactDegenerateDimension("coverage_id"));
    model.getTables().add(factless);
    return model;
  }

  public static DatabaseMeta postgres() throws Exception {
    return new DatabaseMeta(
        "Vault", "POSTGRESQL", "Native", "localhost", "test_edw", "5432", "test", "test");
  }

  static MemoryMetadataProvider providerWithVault() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(DatabaseMeta.class).save(postgres());
    return provider;
  }

  static Variables variables() {
    return new Variables();
  }
}
