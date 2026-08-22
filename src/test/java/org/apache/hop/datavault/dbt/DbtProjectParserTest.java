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
package org.apache.hop.datavault.dbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.metadata.businessvault.BvSqlMaterialization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DbtProjectParserTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void scansJaffleMiniProject() throws Exception {
    DbtProjectScan scan = DbtProjectParser.scan(fixtureRoot().toString());
    assertEquals("jaffle_mini", scan.getProjectName());
    assertEquals("2020-01-01", scan.getVars().get("start_date"));
    assertEquals(4, scan.getModels().size());
    assertEquals(1, scan.getMacros().size());
    assertEquals("cents_to_dollars", scan.getMacros().get(0).getName());
    assertEquals(1, scan.getSources().size());
    assertEquals("jaffle", scan.getSources().get(0).getSourceName());
    assertTrue(scan.getIssues().stream().anyMatch(issue -> "SNAPSHOTS".equals(issue.code())));

    DbtModelDraft staging = find(scan, "stg_customers");
    assertEquals(BvSqlMaterialization.VIEW, staging.getMaterialization());
    assertEquals("staging", staging.getSchemaName());
    assertEquals("Staged customers", staging.getDescription());
    assertEquals(1, staging.getColumnNotes().size());
    assertEquals("customer_id", staging.getColumnNotes().get(0).getName());
    assertEquals("staging", staging.getFirstLevelFolder());

    DbtModelDraft mart = find(scan, "customers");
    assertEquals(BvSqlMaterialization.TABLE, mart.getMaterialization());
    assertEquals("Customer mart", mart.getDescription());
    assertTrue(mart.getSqlQuery().contains("cents_to_dollars"));

    DbtModelDraft incremental = find(scan, "incremental_orders");
    assertEquals(BvSqlMaterialization.TABLE, incremental.getMaterialization());
    assertTrue(
        incremental.getIssues().stream().anyMatch(issue -> "INCREMENTAL".equals(issue.code())));

    DbtModelDraft ephemeral = find(scan, "ephemeral_helper");
    assertEquals(BvSqlMaterialization.VIEW, ephemeral.getMaterialization());
    assertTrue(ephemeral.getIssues().stream().anyMatch(issue -> "EPHEMERAL".equals(issue.code())));
  }

  @Test
  void findsProjectRootFromModelsFolder() throws Exception {
    Path models = fixtureRoot().resolve("models");
    String root = DbtProjectParser.findProjectRoot(models.toString());
    assertTrue(root.replace('\\', '/').endsWith("jaffle-mini"));
  }

  @Test
  void firstLevelFolderIsUnderModelPath() {
    assertEquals("staging", DbtProjectParser.firstLevelFolder("models/staging/stg.sql", "models"));
    assertEquals("marts", DbtProjectParser.firstLevelFolder("models/marts/fin/x.sql", "models"));
    assertEquals("", DbtProjectParser.firstLevelFolder("models/top.sql", "models"));
  }

  @Test
  void configParserReadsAliasAndMaterialized() {
    DbtSqlConfigParser.SqlConfig config =
        DbtSqlConfigParser.parse(
            "{{ config(materialized='incremental', alias='orders_x', schema='marts') }}\nselect 1");
    assertEquals("incremental", config.getMaterialized());
    assertEquals("orders_x", config.getAlias());
    assertEquals("marts", config.getSchema());
  }

  private static DbtModelDraft find(DbtProjectScan scan, String name) {
    return scan.getModels().stream()
        .filter(model -> name.equals(model.getName()))
        .findFirst()
        .orElseThrow();
  }

  public static Path fixtureRoot() throws URISyntaxException {
    return Path.of(
            Objects.requireNonNull(
                    DbtProjectParserTest.class.getResource(
                        "/org/apache/hop/datavault/dbt/jaffle-mini/dbt_project.yml"))
                .toURI())
        .getParent();
  }
}
