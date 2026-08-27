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
package org.hopper.edw.datavault.workflow.actions.importdbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.dbt.DbtImportConflictPolicy;
import org.hopper.edw.datavault.dbt.DbtImportDestination;
import org.hopper.edw.datavault.dbt.DbtProjectParserTest;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlModelPathSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionImportDbtProjectTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaults() {
    ActionImportDbtProject action = new ActionImportDbtProject();
    assertEquals(DbtImportDestination.CURRENT_MODEL.name(), action.getDestination());
    assertEquals(DbtImportConflictPolicy.SKIP.name(), action.getConflictPolicy());
    assertTrue(action.isImportMacros());
  }

  @Test
  void executeWritesHbvFromJaffleMini(@TempDir Path temp) throws Exception {
    Path hbv = temp.resolve("imported.hbv");
    ActionImportDbtProject action = new ActionImportDbtProject("import-dbt");
    action.setMetadataProvider(new MemoryMetadataProvider());
    action.setDbtProjectFolder(DbtProjectParserTest.fixtureRoot().toString());
    action.setTargetHbvFilename(hbv.toString());
    action.setDestination(DbtImportDestination.CURRENT_MODEL.name());
    action.setImportMacros(true);
    action.setLibraryName("jaffle-action-macros");

    Result result = action.execute(new Result(), 0);
    assertEquals(0, result.getNrErrors());
    assertTrue(result.getResult());
    assertTrue(Files.exists(hbv));

    BusinessVaultModel model =
        BvSqlModelPathSupport.loadBusinessVaultModelUncached(hbv.toString(), null);
    assertTrue(model.getTables().size() >= 4);
    assertTrue(model.findTable("stg_customers") != null);
    assertTrue(model.findTable("customers") != null);
  }

  @Test
  void missingProjectFails() throws Exception {
    ActionImportDbtProject action = new ActionImportDbtProject("import-dbt");
    action.setDbtProjectFolder("");
    Result result = action.execute(new Result(), 0);
    assertEquals(1, result.getNrErrors());
    assertTrue(!result.getResult());
  }
}
