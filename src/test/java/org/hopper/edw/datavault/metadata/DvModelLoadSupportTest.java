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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DvModelLoadSupportTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void clearCache() {
    DvModelLoadSupport.clearCache();
  }

  @AfterEach
  void tearDown() {
    DvModelLoadSupport.clearCache();
  }

  @Test
  void toStoredModelPathRelativizesUnderProjectHome() throws Exception {
    Path modelsDir = tempDir.resolve("models");
    Files.createDirectories(modelsDir);
    Path model = modelsDir.resolve("vault1.hdv");
    Files.writeString(model, "<data-vault-model/>");

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());

    assertEquals(
        "${PROJECT_HOME}/models/vault1.hdv",
        DvModelLoadSupport.toStoredModelPath(model.toString(), null, variables));
  }

  @Test
  void resolveModelPathRemapsForeignAbsolutePathUnderProjectHome() throws Exception {
    Path modelsDir = tempDir.resolve("models");
    Files.createDirectories(modelsDir);
    Path realModel = modelsDir.resolve("retail-360.hdv");
    Files.writeString(realModel, "<data-vault-model/>");

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toAbsolutePath().normalize().toString());

    String foreign = "/home/otheruser/git/hop-data-vault/retail-example/models/retail-360.hdv";
    String resolved = DvModelLoadSupport.resolveModelPath(foreign, null, variables);

    assertTrue(
        Files.isRegularFile(Path.of(resolved)), "expected remapped path to exist: " + resolved);
    assertEquals(realModel.toAbsolutePath().normalize(), Path.of(resolved).normalize());
  }

  @Test
  void remapMissingAbsolutePathReturnsNullWhenNoProjectMatch() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());

    assertEquals(
        null,
        DvModelLoadSupport.remapMissingAbsolutePath("/nowhere/else/missing-model.hdv", variables));
  }

  @Test
  void loadDataVaultModelUsesCacheUntilInvalidated() throws Exception {
    Path tempModel = copyVault1Fixture();
    Variables variables = new Variables();

    DataVaultModel first =
        DvModelLoadSupport.loadDataVaultModel(tempModel.toString(), null, variables, null);
    DataVaultModel cached =
        DvModelLoadSupport.loadDataVaultModel(tempModel.toString(), null, variables, null);
    assertSame(first, cached);
    assertTrue(satelliteNames(cached).contains("sat_customer"));

    String xml = Files.readString(tempModel);
    Files.writeString(
        tempModel, xml.replace("<name>sat_customer</name>", "<name>sat_customer_new</name>"));

    DataVaultModel stillCached =
        DvModelLoadSupport.loadDataVaultModel(tempModel.toString(), null, variables, null);
    assertSame(first, stillCached);
    assertTrue(satelliteNames(stillCached).contains("sat_customer"));
    assertFalse(satelliteNames(stillCached).contains("sat_customer_new"));

    DvModelLoadSupport.invalidateCachedModel(tempModel.toString(), null, variables);
    DataVaultModel reloaded =
        DvModelLoadSupport.loadDataVaultModel(tempModel.toString(), null, variables, null);
    assertNotSame(first, reloaded);
    assertFalse(satelliteNames(reloaded).contains("sat_customer"));
    assertTrue(satelliteNames(reloaded).contains("sat_customer_new"));
  }

  @Test
  void loadDataVaultModelFreshRereadsSatellitesAfterFileChange() throws Exception {
    Path tempModel = copyVault1Fixture();
    Variables variables = new Variables();

    DataVaultModel first =
        DvModelLoadSupport.loadDataVaultModel(tempModel.toString(), null, variables, null);
    assertTrue(satelliteNames(first).contains("sat_customer"));
    assertFalse(satelliteNames(first).contains("sat_customer_new"));

    String xml = Files.readString(tempModel);
    Files.writeString(
        tempModel, xml.replace("<name>sat_customer</name>", "<name>sat_customer_new</name>"));

    DataVaultModel fresh =
        DvModelLoadSupport.loadDataVaultModelFresh(tempModel.toString(), null, variables, null);
    assertNotSame(first, fresh);
    assertFalse(satelliteNames(fresh).contains("sat_customer"));
    assertTrue(satelliteNames(fresh).contains("sat_customer_new"));
  }

  @Test
  void invalidateCachedModelByResolvedPathRemovesEntry() throws Exception {
    Path fixture = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Variables variables = new Variables();
    String resolvedPath = DvModelLoadSupport.resolveModelPath(fixture.toString(), null, variables);

    DataVaultModel first =
        DvModelLoadSupport.loadDataVaultModel(fixture.toString(), null, variables, null);
    assertSame(
        first, DvModelLoadSupport.loadDataVaultModel(fixture.toString(), null, variables, null));

    DvModelLoadSupport.invalidateCachedModelByResolvedPath(resolvedPath);
    assertNotSame(
        first, DvModelLoadSupport.loadDataVaultModel(fixture.toString(), null, variables, null));
  }

  private Path copyVault1Fixture() throws Exception {
    Path fixture = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Path tempModel = tempDir.resolve("vault1.hdv");
    Files.copy(fixture, tempModel);
    return tempModel;
  }

  private static List<String> satelliteNames(DataVaultModel model) {
    return DvModelLoadSupport.listTableNames(model, DvTableType.SATELLITE);
  }
}
