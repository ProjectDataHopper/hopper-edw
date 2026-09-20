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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HDatabaseConnection;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.composite.HCompositeComponent;
import org.hopper.presentation.component.types.crosstab.HCrosstabComponent;
import org.hopper.presentation.component.types.group.HGroupComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.sql.HSqlConnector;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FactCrosstabPresentationBuilderTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  @Test
  void noGroupsPlacesCrosstabOnThePage() throws Exception {
    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
              spec.addField(
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_order_date", "year", "Year"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField(
                      "f_order_lines", "amount", "Amount", AggregationMethod.SUM));
            });
    HPresentation presentation = catalog.getPresentation();
    HCrosstabComponent crosstab =
        findCrosstab(presentation.getPages().get(0).getComponents().get(1));
    assertEquals(1, crosstab.getVerticalDimensions().size());
    assertEquals(1, crosstab.getHorizontalDimensions().size());
    assertEquals(1, crosstab.getFacts().size());
    assertTrue(crosstab.isShowingHorizontalTotals());
    assertTrue(crosstab.isShowingVerticalTotals());
    assertTrue(crosstab.getFacts().get(0).isHorizontalAggregation());
    assertTrue(crosstab.getFacts().get(0).isVerticalAggregation());

    HConnector connector =
        catalog
            .getProvider()
            .getSerializer(HConnector.class)
            .load(FactCrosstabPresentationBuilder.CONNECTOR_NAME);
    assertInstanceOf(HSqlConnector.class, connector.getConnector());
    HDatabaseConnection connection =
        catalog
            .getProvider()
            .getSerializer(HDatabaseConnection.class)
            .load(FactCrosstabConnectionSupport.CONNECTION_NAME);
    assertEquals("POSTGRESQL", connection.getDatabaseTypeCode());
  }

  @Test
  void groupsWrapCompositeWithLabelAndCrosstab() throws Exception {
    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.GROUPS,
                  new FactCrosstabField("dim_customer", "country", "Country"));
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_order_date", "year", "Year"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField("f_order_lines", "quantity", "Qty", AggregationMethod.SUM));
              spec.setShowingHorizontalTotals(false);
              spec.setShowingVerticalTotals(true);
            });
    HComponent root = catalog.getPresentation().getPages().get(0).getComponents().get(0);
    HGroupComponent group = assertInstanceOf(HGroupComponent.class, root.getComponent());
    assertTrue(group.isDistinctSelection());
    assertEquals(1, group.getColumnSelection().size());
    HComponent nested = group.getGroupComponent();
    HCompositeComponent composite =
        assertInstanceOf(HCompositeComponent.class, nested.getComponent());
    assertEquals(2, composite.getChildren().size());
    HCrosstabComponent crosstab = findCrosstab(composite.getChildren().get(1));
    assertFalse(crosstab.isShowingHorizontalTotals());
    assertTrue(crosstab.isShowingVerticalTotals());
  }

  private static HGeneratedCatalog catalog(SpecWriter writer) throws Exception {
    DimensionalModel model = FactCrosstabTestModels.star();
    FactCrosstabSourceModel sources =
        FactCrosstabSourceModel.build(
            model, FactCrosstabTestModels.fact(model), FactCrosstabTestModels.variables(), null);
    FactCrosstabSpec spec = new FactCrosstabSpec();
    spec.setFactTableName("f_order_lines");
    writer.write(spec);
    return FactCrosstabPresentationBuilder.build(
        model,
        FactCrosstabTestModels.fact(model),
        spec,
        sources,
        FactCrosstabTestModels.variables(),
        FactCrosstabTestModels.providerWithVault());
  }

  private static HCrosstabComponent findCrosstab(HComponent component) {
    return assertInstanceOf(HCrosstabComponent.class, component.getComponent());
  }

  @FunctionalInterface
  private interface SpecWriter {
    void write(FactCrosstabSpec spec);
  }
}
