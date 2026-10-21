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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.semantic.model.SemanticAttribute;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.composite.HCompositeComponent;
import org.hopper.presentation.component.types.crosstab.HCrosstabComponent;
import org.hopper.presentation.component.types.group.HGroupComponent;
import org.hopper.presentation.component.types.table.HTableComponent;
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
    assertEquals(1, presentation.getPages().get(0).getComponents().size());
    HCrosstabComponent crosstab =
        findCrosstab(presentation.getPages().get(0).getComponents().get(0));
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
  void countFactPivotsAsSumOfTheSqlCount() throws Exception {
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
                      "f_order_lines", "quantity", "Qty", AggregationMethod.COUNT));
            });
    String sql = connectorSql(catalog);
    assertTrue(sql.toUpperCase().contains("NULLIF(COUNT("), sql);
    assertTrue(sql.toUpperCase().contains("GROUP BY"), sql);
    HCrosstabComponent crosstab =
        findCrosstab(catalog.getPresentation().getPages().get(0).getComponents().get(0));
    assertEquals(AggregationMethod.SUM, crosstab.getFacts().get(0).getAggregationMethod());
    assertEquals("0", crosstab.getFacts().get(0).getFormatMask());
    assertNull(crosstab.getFacts().get(0).getWeightColumnName());
  }

  @Test
  void averageFactKeepsAverageAndWeightColumn() throws Exception {
    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.GROUPS,
                  new FactCrosstabField("dim_customer", "country", "Country"));
              spec.addField(
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_order_date", "year", "Year"));
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField(
                      "f_order_lines", "amount", "Amount", AggregationMethod.AVERAGE));
            });
    String sql = connectorSql(catalog);
    String upper = sql.toUpperCase();
    assertTrue(upper.contains("SUM("), sql);
    assertTrue(upper.contains("COUNT("), sql);
    assertTrue(upper.contains("GROUP BY"), sql);
    HComponent root = catalog.getPresentation().getPages().get(0).getComponents().get(0);
    HGroupComponent group = assertInstanceOf(HGroupComponent.class, root.getComponent());
    HCompositeComponent composite =
        assertInstanceOf(HCompositeComponent.class, group.getGroupComponent().getComponent());
    HCrosstabComponent crosstab = findCrosstab(composite.getChildren().get(1));
    assertEquals(AggregationMethod.AVERAGE, crosstab.getFacts().get(0).getAggregationMethod());
    assertEquals("amount_n", crosstab.getFacts().get(0).getWeightColumnName());
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
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_order_date", "year", "Year"));
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
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
    assertEquals(1, catalog.getPresentation().getPages().get(0).getComponents().size());
  }

  @Test
  void missingAxisRendersATableOfDimensionsAndFacts() throws Exception {
    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  field("dim_customer", "customer_name", "Customer", false));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS, field("f_order_lines", "amount", "Amount", true));
            });
    assertEquals(1, catalog.getPresentation().getPages().get(0).getComponents().size());
    HTableComponent table =
        assertInstanceOf(
            HTableComponent.class,
            catalog.getPresentation().getPages().get(0).getComponents().get(0).getComponent());
    assertEquals(2, table.getColumnSelection().size());
    assertEquals("customer_name", table.getColumnSelection().get(0).getColumnName());
    assertEquals(
        HHorizontalAlignment.LEFT, table.getColumnSelection().get(0).getHorizontalAlignment());
    assertEquals(
        HVerticalAlignment.BOTTOM, table.getColumnSelection().get(0).getVerticalAlignment());
    assertEquals(
        HHorizontalAlignment.RIGHT, table.getColumnSelection().get(1).getHorizontalAlignment());
    assertEquals("amount", table.getColumnSelection().get(1).getColumnName());
    String sql = connectorSql(catalog);
    assertTrue(sql.toUpperCase().contains("SUM("), sql);
    assertFalse(sql.toUpperCase().contains("AVG("), sql);
  }

  @Test
  void noAxesAndNoGroupsAreOneFactTable() throws Exception {
    HGeneratedCatalog catalog =
        catalog(
            spec ->
                spec.addField(
                    FactCrosstabSpec.Zone.FACTS,
                    new FactCrosstabField(
                        "f_order_lines", "amount", "Amount", AggregationMethod.AVERAGE)));
    HTableComponent table =
        assertInstanceOf(
            HTableComponent.class,
            catalog.getPresentation().getPages().get(0).getComponents().get(0).getComponent());
    assertEquals(1, table.getColumnSelection().size());
    assertEquals(1, catalog.getPresentation().getPages().get(0).getComponents().size());
    assertTrue(connectorSql(catalog).toUpperCase().contains("AVG("));
    assertFalse(connectorSql(catalog).toUpperCase().contains("GROUP BY"));
  }

  @Test
  void groupsWithoutAxesRepeatATable() throws Exception {
    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.GROUPS,
                  new FactCrosstabField("dim_customer", "country", "Country"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField(
                      "f_order_lines", "quantity", "Qty", AggregationMethod.COUNT));
            });
    HComponent root = catalog.getPresentation().getPages().get(0).getComponents().get(0);
    HGroupComponent group = assertInstanceOf(HGroupComponent.class, root.getComponent());
    HCompositeComponent composite =
        assertInstanceOf(HCompositeComponent.class, group.getGroupComponent().getComponent());
    HTableComponent table =
        assertInstanceOf(HTableComponent.class, composite.getChildren().get(1).getComponent());
    assertEquals(2, table.getColumnSelection().size());
    assertEquals("country", table.getColumnSelection().get(0).getColumnName());
    assertEquals("quantity", table.getColumnSelection().get(1).getColumnName());
    assertEquals(1, catalog.getPresentation().getPages().get(0).getComponents().size());
  }

  @Test
  void alignmentAndTotalsReachTheCrosstab() throws Exception {
    FactCrosstabField amount =
        new FactCrosstabField("f_order_lines", "amount", "Amount", AggregationMethod.SUM);
    amount.setHorizontalAlignment(HHorizontalAlignment.LEFT);
    amount.setVerticalAlignment(HVerticalAlignment.TOP);
    amount.setShowingTotals(false);
    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
              spec.addField(
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_order_date", "year", "Year"));
              spec.addField(FactCrosstabSpec.Zone.FACTS, amount);
            });
    HCrosstabComponent crosstab =
        findCrosstab(catalog.getPresentation().getPages().get(0).getComponents().get(0));
    assertEquals(HHorizontalAlignment.LEFT, crosstab.getFacts().get(0).getHorizontalAlignment());
    assertEquals(HVerticalAlignment.TOP, crosstab.getFacts().get(0).getVerticalAlignment());
    assertFalse(crosstab.getFacts().get(0).isHorizontalAggregation());
    assertFalse(crosstab.isShowingHorizontalTotals());
    assertFalse(crosstab.isShowingVerticalTotals());
    assertEquals(
        HHorizontalAlignment.CENTER,
        crosstab.getVerticalDimensions().get(0).getHorizontalAlignment());
  }

  @Test
  void swapAxesExchangesTheDimensionLists() {
    FactCrosstabSpec spec = new FactCrosstabSpec();
    spec.addField(
        FactCrosstabSpec.Zone.HORIZONTAL, new FactCrosstabField("dim_order_date", "year", "Year"));
    spec.addField(
        FactCrosstabSpec.Zone.VERTICAL,
        new FactCrosstabField("dim_customer", "customer_name", "Customer"));
    spec.swapAxes();
    assertEquals("customer_name", spec.getHorizontalDimensions().get(0).getColumnName());
    assertEquals("year", spec.getVerticalDimensions().get(0).getColumnName());
    assertTrue(spec.usesCrosstab());
  }

  @Test
  void dateDimensionFormatMaskReachesTheCrosstabAxis() throws Exception {
    SemanticModel semantic = new SemanticModel();
    SemanticEntity date = new SemanticEntity();
    date.setName("dim_order_date");
    SemanticAttribute fullDate = new SemanticAttribute("year", "year");
    fullDate.setFormatMask("yyyy-MM-dd");
    date.getAttributes().add(fullDate);
    semantic.getEntities().add(date);

    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_order_date", "year", "Full date"));
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField("f_order_lines", "quantity", "Qty", AggregationMethod.SUM));
            },
            semantic);
    HCrosstabComponent crosstab =
        findCrosstab(catalog.getPresentation().getPages().get(0).getComponents().get(0));
    assertEquals("yyyy-MM-dd", crosstab.getHorizontalDimensions().get(0).getFormatMask());
  }

  @Test
  void groupColumnUsesTheDateFormatMask() throws Exception {
    SemanticModel semantic = new SemanticModel();
    SemanticEntity date = new SemanticEntity();
    date.setName("dim_order_date");
    SemanticAttribute fullDate = new SemanticAttribute("year", "year");
    fullDate.setFormatMask("yyyy-MM-dd");
    date.getAttributes().add(fullDate);
    semantic.getEntities().add(date);

    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.GROUPS,
                  new FactCrosstabField("dim_order_date", "year", "Full date"));
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField("f_order_lines", "quantity", "Qty", AggregationMethod.SUM));
            },
            semantic);
    HComponent root = catalog.getPresentation().getPages().get(0).getComponents().get(0);
    HGroupComponent group = assertInstanceOf(HGroupComponent.class, root.getComponent());
    assertNull(group.getColumnSelection().get(0).getFormatMask());
    assertTrue(connectorSql(catalog).contains("to_char("));
    assertTrue(connectorSql(catalog).contains("YYYY-MM-DD"));
  }

  @Test
  void timestampConversionMaskDropsTheTime() throws Exception {
    ValueMetaTimestamp meta = new ValueMetaTimestamp("full_date");
    meta.setConversionMask("yyyy-MM-dd");
    assertEquals("2026-10-11", meta.getString(Timestamp.valueOf("2026-10-11 00:00:00")));
  }

  @Test
  void portraitPageIsTallerThanItIsWide() throws Exception {
    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.setPortrait(true);
              spec.addField(
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_order_date", "year", "Year"));
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField("f_order_lines", "quantity", "Qty", AggregationMethod.SUM));
            });
    var page = catalog.getPresentation().getPages().get(0);
    assertTrue(page.getHeight() > page.getWidth());
  }

  @Test
  void landscapeIsTheDefaultPage() throws Exception {
    HGeneratedCatalog catalog =
        catalog(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_order_date", "year", "Year"));
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField("f_order_lines", "quantity", "Qty", AggregationMethod.SUM));
            });
    var page = catalog.getPresentation().getPages().get(0);
    assertTrue(page.getWidth() > page.getHeight());
  }

  private static FactCrosstabField field(String table, String column, String header, boolean fact) {
    FactCrosstabField field =
        fact
            ? new FactCrosstabField(table, column, header, AggregationMethod.SUM)
            : new FactCrosstabField(table, column, header);
    if (fact) {
      field.setHorizontalAlignment(HHorizontalAlignment.RIGHT);
      field.setVerticalAlignment(HVerticalAlignment.MIDDLE);
    } else {
      field.setHorizontalAlignment(HHorizontalAlignment.LEFT);
      field.setVerticalAlignment(HVerticalAlignment.BOTTOM);
    }
    return field;
  }

  private static HGeneratedCatalog catalog(SpecWriter writer) throws Exception {
    return catalog(writer, null);
  }

  private static HGeneratedCatalog catalog(SpecWriter writer, SemanticModel semantic)
      throws Exception {
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
        FactCrosstabTestModels.providerWithVault(),
        semantic);
  }

  private static String connectorSql(HGeneratedCatalog catalog) throws Exception {
    HConnector connector =
        catalog
            .getProvider()
            .getSerializer(HConnector.class)
            .load(FactCrosstabPresentationBuilder.CONNECTOR_NAME);
    return assertInstanceOf(HSqlConnector.class, connector.getConnector()).getSql();
  }

  private static HCrosstabComponent findCrosstab(HComponent component) {
    return assertInstanceOf(HCrosstabComponent.class, component.getComponent());
  }

  @FunctionalInterface
  private interface SpecWriter {
    void write(FactCrosstabSpec spec);
  }
}
