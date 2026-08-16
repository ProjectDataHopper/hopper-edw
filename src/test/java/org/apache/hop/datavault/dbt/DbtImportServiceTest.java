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
package org.apache.hop.datavault.dbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvBusinessTable;
import org.apache.hop.datavault.metadata.businessvault.BvSqlMaterialization;
import org.apache.hop.datavault.metadata.jinja.JinjaMacroLibraryMeta;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtImportServiceTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void appliesSelectedModelsIntoCurrentHbvAndLibrary() throws Exception {
    DbtProjectScan scan = DbtProjectParser.scan(DbtProjectParserTest.fixtureRoot().toString());
    BusinessVaultModel model = new BusinessVaultModel();
    model.setName("current");
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    DbtImportOptions options = new DbtImportOptions();
    options.setScan(scan);
    options.setCurrentModel(model);
    options.setMetadataProvider(metadata);
    options.setVariables(new Variables());
    options.setDestination(DbtImportDestination.CURRENT_MODEL);
    options.setImportMacros(true);
    options.getSelectedModels().add(find(scan, "stg_customers"));
    options.getSelectedModels().add(find(scan, "customers"));

    DbtImportResult result = DbtImportService.apply(options);
    assertEquals(2, result.getImportedTables());
    assertEquals(2, model.getTables().size());
    BvBusinessTable staging = (BvBusinessTable) model.findTable("stg_customers");
    assertEquals("staging", staging.getSchemaName());
    assertEquals("models/staging/stg_customers.sql", staging.getOriginDbtPath());
    assertEquals(1, staging.getSources().size());
    assertEquals("jaffle", staging.getSources().get(0).getSourceName());
    assertEquals("raw", staging.getSources().get(0).getSchemaName());
    assertEquals(BvSqlMaterialization.VIEW, staging.getMaterialization());

    BvBusinessTable mart = (BvBusinessTable) model.findTable("customers");
    assertEquals(BvSqlMaterialization.TABLE, mart.getMaterialization());
    assertEquals("Customer mart", mart.getDescription());

    JinjaMacroLibraryMeta library =
        metadata.getSerializer(JinjaMacroLibraryMeta.class).load("jaffle_mini-macros");
    assertEquals(1, library.getMacros().size());
    assertEquals("cents_to_dollars", library.getMacros().get(0).getName());
    assertEquals("2020-01-01", library.getVars().get(0).getValue());
  }

  @Test
  void skipAndReplaceConflicts() throws Exception {
    DbtProjectScan scan = DbtProjectParser.scan(DbtProjectParserTest.fixtureRoot().toString());
    BusinessVaultModel model = new BusinessVaultModel();
    BvBusinessTable existing = new BvBusinessTable();
    existing.setName("stg_customers");
    existing.setTableName("stg_customers");
    existing.setSqlQuery("SELECT 0");
    model.getTables().add(existing);

    DbtImportOptions skip = baseOptions(scan, model);
    skip.setConflictPolicy(DbtImportConflictPolicy.SKIP);
    skip.getSelectedModels().add(find(scan, "stg_customers"));
    DbtImportResult skipped = DbtImportService.apply(skip);
    assertEquals(1, skipped.getSkippedTables());
    assertEquals("SELECT 0", ((BvBusinessTable) model.findTable("stg_customers")).getSqlQuery());

    DbtImportOptions replace = baseOptions(scan, model);
    replace.setConflictPolicy(DbtImportConflictPolicy.REPLACE);
    replace.getSelectedModels().add(find(scan, "stg_customers"));
    DbtImportResult replaced = DbtImportService.apply(replace);
    assertEquals(1, replaced.getReplacedTables());
    assertTrue(
        ((BvBusinessTable) model.findTable("stg_customers"))
            .getSqlQuery()
            .contains("source('jaffle'"));
  }

  @Test
  void splitWritesOneHbvPerFolder(@TempDir Path temp) throws Exception {
    DbtProjectScan scan = DbtProjectParser.scan(DbtProjectParserTest.fixtureRoot().toString());
    DbtImportOptions options = new DbtImportOptions();
    options.setScan(scan);
    options.setDestination(DbtImportDestination.SPLIT_BY_FOLDER);
    options.setOutputFolder(temp.toString());
    options.setImportMacros(false);
    options.setVariables(new Variables());
    options.getSelectedModels().addAll(scan.getModels());

    DbtImportResult result = DbtImportService.apply(options);
    assertEquals(2, result.getWrittenModelFiles().size());
    assertTrue(Files.exists(temp.resolve("staging.hbv")));
    assertTrue(Files.exists(temp.resolve("marts.hbv")));
    assertTrue(result.getImportedTables() >= 4);
  }

  @Test
  void suggestDestinationUsesThreshold() {
    assertEquals(DbtImportDestination.CURRENT_MODEL, DbtImportService.suggestDestination(10));
    assertEquals(DbtImportDestination.SPLIT_BY_FOLDER, DbtImportService.suggestDestination(81));
  }

  private static DbtImportOptions baseOptions(DbtProjectScan scan, BusinessVaultModel model) {
    DbtImportOptions options = new DbtImportOptions();
    options.setScan(scan);
    options.setCurrentModel(model);
    options.setDestination(DbtImportDestination.CURRENT_MODEL);
    options.setImportMacros(false);
    options.setVariables(new Variables());
    return options;
  }

  private static DbtModelDraft find(DbtProjectScan scan, String name) {
    return scan.getModels().stream()
        .filter(model -> name.equals(model.getName()))
        .findFirst()
        .orElseThrow();
  }
}
