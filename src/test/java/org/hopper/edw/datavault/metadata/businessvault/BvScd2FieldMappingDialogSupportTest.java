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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvLinkedTable;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2FieldMappingDialogSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void listsSatelliteDerivativesAndAttributes() throws Exception {
    BvScd2Table table = customer360Table();
    DataVaultModel dvModel = loadCustomer360DvModel();

    assertEquals(
        List.of(
            "sat_customer_demo",
            "sat_customer_contact",
            "sat_customer_address",
            "sat_customer_prefs"),
        BvScd2FieldMappingDialogSupport.satelliteDerivativeNames(table, dvModel));
    assertEquals(
        List.of("segment", "loyalty_tier", "demo_score"),
        BvScd2FieldMappingDialogSupport.satelliteAttributeNames("sat_customer_demo", dvModel));
  }

  @Test
  void suggestMappingsUsesPrefixedTargetsOnCollision() throws Exception {
    BvScd2Table table = customer360Table();
    DataVaultModel dvModel = loadCustomer360DvModel();

    List<BvScd2FieldMapping> suggestions =
        BvScd2FieldMappingDialogSupport.suggestMappings(table, dvModel);

    assertFalse(suggestions.isEmpty());
    Set<String> targets = new HashSet<>();
    for (BvScd2FieldMapping mapping : suggestions) {
      assertTrue(targets.add(mapping.getTargetFieldName()));
    }
    assertTrue(
        suggestions.stream()
            .anyMatch(
                mapping ->
                    "sat_customer_demo".equals(mapping.getSatelliteName())
                        && "segment".equals(mapping.getSourceFieldName())));
  }

  @Test
  void analyzeWithoutDvModelReportsMissingModel() {
    BvScd2Table table = customer360Table();
    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, null, new Variables(), null);

    assertFalse(suggestion.dvModelPresent());
    assertEquals(0, suggestion.dvTableCount());
    assertTrue(suggestion.suggestedMappings().isEmpty());
    assertEquals(4, suggestion.missingNames().size());
  }

  @Test
  void analyzeReportsMissingSatellites() {
    BvScd2Table table = customer360Table();
    DataVaultModel dvModel = new DataVaultModel();

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, dvModel, new Variables(), null);

    assertTrue(suggestion.dvModelPresent());
    assertEquals(0, suggestion.dvTableCount());
    assertTrue(suggestion.suggestedMappings().isEmpty());
    assertTrue(suggestion.missingNames().contains("sat_customer_demo"));
  }

  @Test
  void analyzeReportsEmptyAttributes() {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_scd2");
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    DataVaultModel dvModel = new DataVaultModel();
    dvModel.getTables().add(new DvSatellite("sat_customer"));

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, dvModel, new Variables(), null);

    assertEquals(List.of("sat_customer"), suggestion.emptyAttributeNames());
    assertTrue(suggestion.suggestedMappings().isEmpty());
  }

  @Test
  void analyzeFollowsLinkedTableToSatelliteAttributes() throws Exception {
    DataVaultModel dvModel = loadCustomer360DvModel();
    DvLinkedTable alias = new DvLinkedTable();
    alias.setName("sat_customer_demo_link");
    alias.setReferencedTableName("sat_customer_demo");
    alias.setReferencedTableType(DvTableType.SATELLITE);
    dvModel.getTables().add(alias);

    BvScd2Table table = new BvScd2Table();
    table.setName("customer_scd2");
    table
        .getDerivatives()
        .add(new BvDerivativeRef("sat_customer_demo_link", DvTableType.SATELLITE));

    assertEquals(
        List.of("segment", "loyalty_tier", "demo_score"),
        BvScd2FieldMappingDialogSupport.satelliteAttributeNames(
            "sat_customer_demo_link", dvModel, new Variables(), null));

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, dvModel, new Variables(), null);
    assertTrue(suggestion.resolvedNames().contains("sat_customer_demo_link"));
    assertTrue(
        suggestion.suggestedMappings().stream()
            .anyMatch(
                mapping ->
                    "sat_customer_demo_link".equals(mapping.getSatelliteName())
                        && "segment".equals(mapping.getSourceFieldName())));
  }

  @Test
  void analyzeSuggestsSourceQueryColumnsWithoutDvModel() {
    BvSourceQuery sourceQuery = burgerSourceQuery();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getTables().add(sourceQuery);

    BvScd2Table table = new BvScd2Table();
    table.setName("burger_scd2");
    table.getSourceQueryRefs().add(new BvSourceQueryRef("sq_burger_view"));

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, null, bvModel, new Variables(), null);

    assertFalse(suggestion.dvModelPresent());
    assertEquals(List.of("sq_burger_view"), suggestion.resolvedNames());
    assertEquals(
        List.of("sauce", "patty"),
        BvScd2FieldMappingDialogSupport.satelliteAttributeNames(
            "sq_burger_view", null, bvModel, new Variables(), null));
    assertEquals(2, suggestion.suggestedMappings().size());
    assertTrue(
        suggestion.suggestedMappings().stream()
            .anyMatch(
                mapping ->
                    "sq_burger_view".equals(mapping.getSatelliteName())
                        && "sauce".equals(mapping.getSourceFieldName())
                        && "sauce".equals(mapping.getTargetFieldName())));
    assertTrue(
        suggestion.suggestedMappings().stream()
            .noneMatch(mapping -> "burger_hkey".equals(mapping.getSourceFieldName())));
    assertTrue(
        suggestion.suggestedMappings().stream()
            .noneMatch(mapping -> "effective_ts".equals(mapping.getSourceFieldName())));
    assertTrue(
        suggestion.suggestedMappings().stream()
            .noneMatch(mapping -> "x_load_ts".equals(mapping.getSourceFieldName())));
  }

  @Test
  void analyzeReportsMissingSourceQueryWhenBvModelLacksIt() {
    BvScd2Table table = new BvScd2Table();
    table.setName("burger_scd2");
    table.getSourceQueryRefs().add(new BvSourceQueryRef("sq_burger_view"));

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(
            table, null, new BusinessVaultModel(), new Variables(), null);

    assertEquals(List.of("sq_burger_view"), suggestion.missingNames());
    assertTrue(suggestion.suggestedMappings().isEmpty());
  }

  @Test
  void analyzeReportsEmptySourceQueryColumns() {
    BvSourceQuery sourceQuery = new BvSourceQuery();
    sourceQuery.setName("sq_empty");
    sourceQuery.setHashKeyField("hk");
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getTables().add(sourceQuery);

    BvScd2Table table = new BvScd2Table();
    table.setName("empty_scd2");
    table.getSourceQueryRefs().add(new BvSourceQueryRef("sq_empty"));

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, null, bvModel, new Variables(), null);

    assertEquals(List.of("sq_empty"), suggestion.emptyAttributeNames());
    assertTrue(suggestion.suggestedMappings().isEmpty());
  }

  @Test
  void analyzeMixesSatelliteAndSourceQueryMappings() throws Exception {
    BvScd2Table table = customer360Table();
    table.getSourceQueryRefs().add(new BvSourceQueryRef("sq_burger_view"));
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getTables().add(burgerSourceQuery());
    DataVaultModel dvModel = loadCustomer360DvModel();

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, dvModel, bvModel, new Variables(), null);

    assertTrue(suggestion.resolvedNames().contains("sat_customer_demo"));
    assertTrue(suggestion.resolvedNames().contains("sq_burger_view"));
    assertTrue(
        suggestion.suggestedMappings().stream()
            .anyMatch(
                mapping ->
                    "sat_customer_demo".equals(mapping.getSatelliteName())
                        && "segment".equals(mapping.getSourceFieldName())));
    assertTrue(
        suggestion.suggestedMappings().stream()
            .anyMatch(
                mapping ->
                    "sq_burger_view".equals(mapping.getSatelliteName())
                        && "patty".equals(mapping.getSourceFieldName())));
  }

  @Test
  void analyzeKeepsExistingSourceQueryMappingsAndAddsOnlyNewColumns() {
    BvSourceQuery sourceQuery = burgerSourceQuery();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getTables().add(sourceQuery);

    BvScd2Table table = new BvScd2Table();
    table.setName("burger_scd2");
    table.getSourceQueryRefs().add(new BvSourceQueryRef("sq_burger_view"));
    table.getFieldMappings().add(new BvScd2FieldMapping("sq_burger_view", "sauce", "burger_sauce"));

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, null, bvModel, new Variables(), null);

    assertEquals(1, suggestion.alreadyMappedCount());
    assertTrue(
        suggestion.suggestedMappings().stream()
            .noneMatch(
                mapping ->
                    "sq_burger_view".equals(mapping.getSatelliteName())
                        && "sauce".equals(mapping.getSourceFieldName())));
    assertTrue(
        suggestion.suggestedMappings().stream()
            .anyMatch(
                mapping ->
                    "sq_burger_view".equals(mapping.getSatelliteName())
                        && "patty".equals(mapping.getSourceFieldName())));
  }

  @Test
  void resolveSatelliteDerivativesIgnoresSourceQueries() {
    BvScd2Table table = new BvScd2Table();
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    table.getSourceQueryRefs().add(new BvSourceQueryRef("sq_burger_view"));

    DataVaultModel dvModel = new DataVaultModel();
    DvSatellite satellite = new DvSatellite("sat_customer");
    satellite.getAttributes().add(new SatelliteAttribute("segment"));
    dvModel.getTables().add(satellite);

    List<DvSatellite> satellites =
        BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(
            table, dvModel, new Variables(), null);
    assertEquals(1, satellites.size());
    assertEquals("sat_customer", satellites.get(0).getName());
  }

  @Test
  void analyzeKeepsExistingMappingsAndAddsOnlyNewSources() throws Exception {
    BvScd2Table table = customer360Table();
    table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_demo", "segment", "cust_segment"));
    DataVaultModel dvModel = loadCustomer360DvModel();

    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        BvScd2FieldMappingDialogSupport.analyze(table, dvModel, new Variables(), null);

    assertEquals(1, suggestion.alreadyMappedCount());
    assertFalse(
        suggestion.suggestedMappings().stream()
            .anyMatch(
                mapping ->
                    "sat_customer_demo".equals(mapping.getSatelliteName())
                        && "segment".equals(mapping.getSourceFieldName())));
    assertTrue(
        suggestion.suggestedMappings().stream()
            .anyMatch(
                mapping ->
                    "sat_customer_demo".equals(mapping.getSatelliteName())
                        && "loyalty_tier".equals(mapping.getSourceFieldName())));
  }

  @Test
  void pruneMappingsAndConfigsRemovesOrphans() {
    BvScd2Table table = new BvScd2Table();
    table.getFieldMappings().add(new BvScd2FieldMapping("sat_a", "f1", "t1"));
    table.getFieldMappings().add(new BvScd2FieldMapping("sat_b", "f2", "t2"));
    table.getSatelliteConfigs().add(new BvScd2SatelliteConfig("sat_b"));
    table.getSatelliteConfigs().get(0).setSourceIndicatorValue("B");

    BvScd2FieldMappingDialogSupport.pruneMappingsAndConfigs(table, Set.of("sat_a"));

    assertEquals(1, table.getFieldMappings().size());
    assertEquals("sat_a", table.getFieldMappings().get(0).getSatelliteName());
    assertTrue(table.getSatelliteConfigs().isEmpty());
  }

  @Test
  void syncSatelliteConfigsPreservesExistingValues() {
    BvScd2Table table = new BvScd2Table();
    BvScd2SatelliteConfig existing = new BvScd2SatelliteConfig("sat_customer_demo");
    existing.setSourceIndicatorValue("DEMO");
    table.getSatelliteConfigs().add(existing);

    List<BvScd2SatelliteConfig> synced =
        BvScd2FieldMappingDialogSupport.syncSatelliteConfigs(
            table, List.of("sat_customer_demo", "sat_customer_contact"));

    assertEquals(2, synced.size());
    assertEquals("DEMO", synced.get(0).getSourceIndicatorValue());
    assertEquals("sat_customer_contact", synced.get(1).getSatelliteName());
    assertTrue(
        synced.get(1).getSourceIndicatorValue() == null
            || synced.get(1).getSourceIndicatorValue().isEmpty());
  }

  @Test
  void validateForDialogReportsMultiSatelliteMappingErrors() throws Exception {
    BvScd2Table table = customer360Table();
    DataVaultModel dvModel = loadCustomer360DvModel();

    List<org.apache.hop.core.ICheckResult> remarks =
        BvScd2FieldMappingDialogSupport.validateForDialog(
            table, new BusinessVaultModel(), dvModel, new Variables());

    assertTrue(BvScd2FieldMappingDialogSupport.hasValidationErrors(remarks));
    assertTrue(
        BvScd2FieldMappingDialogSupport.formatValidationErrors(remarks).contains("field mappings"));
  }

  @Test
  void validateForDialogReportsModelConfigurationErrors() {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_scd2");
    table.setTableName("customer_scd2");
    table.setBuildMode(BvScd2BuildMode.INCREMENTAL);
    table.setFunctionalTimestampField("LOAD_DATE");
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    bvConfig.setOpenEndSentinel("");
    bvConfig.setTargetDatabase("");
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setConfiguration(bvConfig);

    DataVaultModel dvModel = new DataVaultModel();
    DvSatellite satellite = new DvSatellite("sat_customer");
    satellite.setHubName("hub_customer");
    satellite.getAttributes().add(new SatelliteAttribute("segment"));
    dvModel.getTables().add(satellite);

    List<org.apache.hop.core.ICheckResult> remarks =
        BvScd2FieldMappingDialogSupport.validateForDialog(table, bvModel, dvModel, new Variables());

    assertTrue(BvScd2FieldMappingDialogSupport.hasValidationErrors(remarks));
    String formatted = BvScd2FieldMappingDialogSupport.formatValidationErrors(remarks);
    assertTrue(formatted.contains("open-end sentinel"));
    assertTrue(formatted.contains("target database"));
  }

  private static BvSourceQuery burgerSourceQuery() {
    BvSourceQuery sourceQuery = new BvSourceQuery();
    sourceQuery.setName("sq_burger_view");
    sourceQuery.setHashKeyField("burger_hkey");
    sourceQuery.setHubHashKeyField("hub_burger_hkey");
    sourceQuery.setFunctionalTimestampField("effective_ts");
    sourceQuery.setLoadDateField("x_load_ts");
    sourceQuery.getColumns().add(new BvSourceQueryColumn("burger_hkey"));
    sourceQuery.getColumns().add(new BvSourceQueryColumn("effective_ts"));
    sourceQuery.getColumns().add(new BvSourceQueryColumn("x_load_ts"));
    sourceQuery.getColumns().add(new BvSourceQueryColumn("sauce"));
    sourceQuery.getColumns().add(new BvSourceQueryColumn("patty"));
    return sourceQuery;
  }

  private static BvScd2Table customer360Table() {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_360_bv");
    table.setTableName("customer_360_bv");
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_demo", DvTableType.SATELLITE));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_contact", DvTableType.SATELLITE));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_address", DvTableType.SATELLITE));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_prefs", DvTableType.SATELLITE));
    return table;
  }

  private static DataVaultModel loadCustomer360DvModel() throws Exception {
    Path dvPath =
        Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hdv")
            .toAbsolutePath()
            .normalize();
    Document document = XmlHandler.loadXmlFile(dvPath.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
