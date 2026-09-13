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
package org.hopper.edw.datavault.diagram.exporter;

import org.apache.hop.core.gui.Point;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBridge;
import org.hopper.edw.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;

final class DimensionalExportTestModels {

  private DimensionalExportTestModels() {}

  static DimensionalModel starSchema() {
    DimensionalModel model = new DimensionalModel();
    model.setName("sales");

    DmDimension geo = new DmDimension();
    geo.setName("dim_geo");
    geo.setLocation(new Point(40, 40));
    geo.getNaturalKeys().add(new DmNaturalKeyField("geo_id"));
    geo.getAttributes().add(new DmDimensionAttribute("city"));
    model.getTables().add(geo);

    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.setLocation(new Point(280, 40));
    customer.setSurrogateKeyField("customer_key");
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    customer.getAttributes().add(new DmDimensionAttribute("customer_name"));
    customer.getOutriggers().add(new DmDimensionOutriggerRef("dim_geo", "geo_key"));
    model.getTables().add(customer);

    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("dim_order_flags");
    junk.setLocation(new Point(520, 40));
    junk.setSurrogateKeyField("order_flags_key");
    junk.getKeyFields().add(new DmNaturalKeyField("is_gift"));
    model.getTables().add(junk);

    DmFact fact = new DmFact();
    fact.setName("fact_orders");
    fact.setLocation(new Point(280, 220));
    fact.setGrain("one row per order line");
    fact.getDimensionRoles()
        .add(new DmFactDimensionRole("dim_customer", "customer", "customer_key"));
    fact.getJunkDimensionRoles()
        .add(new DmFactJunkDimensionRole("dim_order_flags", "order_flags_key"));
    fact.getDegenerateDimensions().add(new DmFactDegenerateDimension("order_id"));
    fact.getMeasures().add(new DmFactMeasure("quantity"));
    fact.getMeasures().add(new DmFactMeasure("total_amount"));
    model.getTables().add(fact);

    DmBridge bridge = new DmBridge();
    bridge.setName("br_customer_account");
    bridge.setLocation(new Point(40, 220));
    bridge.getDimensionRefs().add(new DmBridgeDimensionRef("dim_customer", "customer_key"));
    model.getTables().add(bridge);

    return model;
  }
}
