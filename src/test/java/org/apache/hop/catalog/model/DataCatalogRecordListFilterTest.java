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
package org.apache.hop.catalog.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.hopgui.perspective.DataCatalogRecordListFilter;
import org.junit.jupiter.api.Test;

class DataCatalogRecordListFilterTest {

  @Test
  void allFilterMatchesEveryDefinition() {
    assertTrue(DataCatalogRecordListFilter.ALL.toQuery().matches(sourceDefinition()));
    assertTrue(DataCatalogRecordListFilter.ALL.toQuery().matches(hubDefinition()));
    assertTrue(DataCatalogRecordListFilter.ALL.toQuery().matches(operationsDefinition()));
  }

  @Test
  void sourcesFilterMatchesDvSourceOnly() {
    assertTrue(DataCatalogRecordListFilter.SOURCES.toQuery().matches(sourceDefinition()));
    assertFalse(DataCatalogRecordListFilter.SOURCES.toQuery().matches(hubDefinition()));
    assertFalse(DataCatalogRecordListFilter.SOURCES.toQuery().matches(bvDefinition()));
    assertFalse(DataCatalogRecordListFilter.SOURCES.toQuery().matches(operationsDefinition()));
  }

  /**
   * Tree filters hide target datasets (e.g. sat_product) while Execution Map preview still works
   * via registry.read. Open-in-catalog must clear filters before selecting in the tree.
   */
  @Test
  void sourcesFilterHidesRetail360Satellite() {
    RecordDefinition satProduct = new RecordDefinition();
    satProduct.setKey(
        new RecordDefinitionKey("hop/retail-example/models/retail-360", "sat_product"));
    satProduct.setType(RecordDefinitionType.DV_SATELLITE);

    assertTrue(DataCatalogRecordListFilter.ALL.toQuery().matches(satProduct));
    assertFalse(DataCatalogRecordListFilter.SOURCES.toQuery().matches(satProduct));
    assertTrue(DataCatalogRecordListFilter.DATA_VAULT_TABLES.toQuery().matches(satProduct));

    RecordDefinitionQuery search = DataCatalogRecordListFilter.ALL.toQuery();
    search.setNameContains("hub_customer");
    assertFalse(search.matches(satProduct));
  }

  @Test
  void dataVaultTablesFilterMatchesHubLinkAndSatellite() {
    assertTrue(DataCatalogRecordListFilter.DATA_VAULT_TABLES.toQuery().matches(hubDefinition()));
    assertTrue(
        DataCatalogRecordListFilter.DATA_VAULT_TABLES
            .toQuery()
            .matches(typedDefinition(RecordDefinitionType.DV_LINK)));
    assertTrue(
        DataCatalogRecordListFilter.DATA_VAULT_TABLES
            .toQuery()
            .matches(typedDefinition(RecordDefinitionType.DV_SATELLITE)));
    assertFalse(
        DataCatalogRecordListFilter.DATA_VAULT_TABLES.toQuery().matches(sourceDefinition()));
    assertFalse(DataCatalogRecordListFilter.DATA_VAULT_TABLES.toQuery().matches(bvDefinition()));
  }

  @Test
  void businessVaultTablesFilterMatchesBvTableOnly() {
    assertTrue(DataCatalogRecordListFilter.BUSINESS_VAULT_TABLES.toQuery().matches(bvDefinition()));
    assertFalse(
        DataCatalogRecordListFilter.BUSINESS_VAULT_TABLES.toQuery().matches(hubDefinition()));
    assertFalse(
        DataCatalogRecordListFilter.BUSINESS_VAULT_TABLES.toQuery().matches(sourceDefinition()));
  }

  @Test
  void dimensionalTablesFilterMatchesDimAndFactTables() {
    assertTrue(
        DataCatalogRecordListFilter.DIMENSIONAL_TABLES
            .toQuery()
            .matches(typedDefinition(RecordDefinitionType.DIM_TABLE)));
    assertTrue(
        DataCatalogRecordListFilter.DIMENSIONAL_TABLES
            .toQuery()
            .matches(typedDefinition(RecordDefinitionType.FACT_TABLE)));
    assertFalse(DataCatalogRecordListFilter.DIMENSIONAL_TABLES.toQuery().matches(hubDefinition()));
    assertFalse(
        DataCatalogRecordListFilter.DIMENSIONAL_TABLES.toQuery().matches(sourceDefinition()));
  }

  @Test
  void loadMetricsFilterMatchesOperationsMetricsTables() {
    RecordDefinition definition = operationsDefinition();

    assertTrue(DataCatalogRecordListFilter.LOAD_METRICS.toQuery().matches(definition));
    assertTrue(DataCatalogRecordListFilter.OPERATIONS.toQuery().matches(definition));
    assertTrue(DataCatalogRecordListFilter.ALL.toQuery().matches(definition));
  }

  @Test
  void operationsFiltersSkipModelSourceDefinitions() {
    RecordDefinition definition = sourceDefinition();

    assertFalse(DataCatalogRecordListFilter.LOAD_METRICS.toQuery().matches(definition));
    assertFalse(DataCatalogRecordListFilter.OPERATIONS.toQuery().matches(definition));
    assertTrue(DataCatalogRecordListFilter.ALL.toQuery().matches(definition));
  }

  @Test
  void queryTypesListUsesOrSemantics() {
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.getTypes().add(RecordDefinitionType.DV_HUB);
    query.getTypes().add(RecordDefinitionType.DV_LINK);

    assertTrue(query.matches(hubDefinition()));
    assertTrue(query.matches(typedDefinition(RecordDefinitionType.DV_LINK)));
    assertFalse(query.matches(typedDefinition(RecordDefinitionType.DV_SATELLITE)));
    assertFalse(query.matches(sourceDefinition()));
  }

  @Test
  void queryTypesListTakesPrecedenceOverSingleType() {
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setType(RecordDefinitionType.DV_SOURCE);
    query.getTypes().add(RecordDefinitionType.DV_HUB);

    assertTrue(query.matches(hubDefinition()));
    assertFalse(query.matches(sourceDefinition()));
  }

  @Test
  void queryCombinesTypesAndTags() {
    RecordDefinition definition = operationsDefinition();
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.getTypes().add(RecordDefinitionType.PHYSICAL_TABLE);
    query.getTags().add("operations");

    assertTrue(query.matches(definition));

    definition.setType(RecordDefinitionType.DV_HUB);
    assertFalse(query.matches(definition));
  }

  private static RecordDefinition sourceDefinition() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "CRM-customer"));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.getTags().add("DV Source");
    return definition;
  }

  private static RecordDefinition hubDefinition() {
    return typedDefinition(RecordDefinitionType.DV_HUB);
  }

  private static RecordDefinition bvDefinition() {
    return typedDefinition(RecordDefinitionType.BV_TABLE);
  }

  private static RecordDefinition operationsDefinition() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/operations", "load_run"));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.getTags().addAll(List.of("operations", "load-metrics"));
    return definition;
  }

  private static RecordDefinition typedDefinition(RecordDefinitionType type) {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/models/retail-360", "table"));
    definition.setType(type);
    return definition;
  }
}
