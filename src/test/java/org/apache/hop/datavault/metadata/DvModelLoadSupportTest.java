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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DvModelLoadSupportTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
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
}
