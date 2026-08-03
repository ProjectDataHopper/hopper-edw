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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DmModelLoadSupportTest {

  private Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() throws Exception {
    DmModelLoadSupport.clearCache();
    tempDir = Files.createTempDirectory("dm-model-cache-test");
  }

  @AfterEach
  void tearDown() throws Exception {
    DmModelLoadSupport.clearCache();
    if (tempDir != null) {
      Files.walk(tempDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // best-effort cleanup
                }
              });
    }
  }

  @Test
  void loadDimensionalModelUsesCacheUntilInvalidated() throws Exception {
    Path fixture =
        Path.of("integration-tests/tests/basic/shared-conformed.hdm").toAbsolutePath().normalize();
    Path tempModel = tempDir.resolve("shared-conformed.hdm");
    Files.copy(fixture, tempModel);

    String referringModel =
        Path.of("integration-tests/tests/basic/cross-model-alias.hdm")
            .toAbsolutePath()
            .normalize()
            .toString();
    Variables variables = new Variables();

    DimensionalModel first =
        DmModelLoadSupport.loadDimensionalModel(
            tempModel.toString(), referringModel, variables, null);
    DimensionalModel cached =
        DmModelLoadSupport.loadDimensionalModel(
            tempModel.toString(), referringModel, variables, null);
    assertSame(first, cached);
    assertTrue(
        Arrays.asList(DmModelLoadSupport.listBaseDimensionNames(cached)).contains("dim_customer"));

    String xml = Files.readString(tempModel);
    Files.writeString(
        tempModel, xml.replace("<name>dim_customer</name>", "<name>dim_customer_renamed</name>"));

    DimensionalModel stillCached =
        DmModelLoadSupport.loadDimensionalModel(
            tempModel.toString(), referringModel, variables, null);
    assertSame(first, stillCached);
    assertTrue(
        Arrays.asList(DmModelLoadSupport.listBaseDimensionNames(stillCached))
            .contains("dim_customer"));

    DmModelLoadSupport.invalidateCachedModel(tempModel.toString(), referringModel, variables);
    DimensionalModel reloaded =
        DmModelLoadSupport.loadDimensionalModel(
            tempModel.toString(), referringModel, variables, null);
    assertNotSame(first, reloaded);
    assertFalse(
        Arrays.asList(DmModelLoadSupport.listBaseDimensionNames(reloaded))
            .contains("dim_customer"));
    assertTrue(
        Arrays.asList(DmModelLoadSupport.listBaseDimensionNames(reloaded))
            .contains("dim_customer_renamed"));
  }

  @Test
  void invalidateCachedModelByResolvedPathRemovesEntry() throws Exception {
    Path fixture =
        Path.of("integration-tests/tests/basic/shared-conformed.hdm").toAbsolutePath().normalize();
    Variables variables = new Variables();
    String resolvedPath = DmModelLoadSupport.resolveModelPath(fixture.toString(), null, variables);

    DimensionalModel first =
        DmModelLoadSupport.loadDimensionalModel(fixture.toString(), null, variables, null);
    assertSame(
        first, DmModelLoadSupport.loadDimensionalModel(fixture.toString(), null, variables, null));

    DmModelLoadSupport.invalidateCachedModelByResolvedPath(resolvedPath);
    assertNotSame(
        first, DmModelLoadSupport.loadDimensionalModel(fixture.toString(), null, variables, null));
  }

  @Test
  void toStoredModelPathPreservesVariableBasedPath() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());

    assertEquals(
        "${PROJECT_HOME}/models/shared.hdm",
        DmModelLoadSupport.toStoredModelPath("${PROJECT_HOME}/models/shared.hdm", null, variables));
  }

  @Test
  void toStoredModelPathRelativizesUnderProjectHome() throws Exception {
    Path modelsDir = tempDir.resolve("models");
    Files.createDirectories(modelsDir);
    Path sharedModel = modelsDir.resolve("shared-conformed.hdm");
    Files.copy(
        Path.of("integration-tests/tests/basic/shared-conformed.hdm").toAbsolutePath().normalize(),
        sharedModel);

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());

    assertEquals(
        "${PROJECT_HOME}/models/shared-conformed.hdm",
        DmModelLoadSupport.toStoredModelPath(sharedModel.toString(), null, variables));
  }

  @Test
  void toStoredModelPathRelativizesToReferringModelDirectory() throws Exception {
    Path modelsDir = tempDir.resolve("models");
    Files.createDirectories(modelsDir);
    Path sharedModel = modelsDir.resolve("shared-conformed.hdm");
    Path subjectModel = modelsDir.resolve("subject.hdm");
    Files.copy(
        Path.of("integration-tests/tests/basic/shared-conformed.hdm").toAbsolutePath().normalize(),
        sharedModel);
    Files.writeString(subjectModel, "<dimensional-model><tables/></dimensional-model>");

    Variables variables = new Variables();

    assertEquals(
        "shared-conformed.hdm",
        DmModelLoadSupport.toStoredModelPath(
            sharedModel.toString(), subjectModel.toString(), variables));
  }

  @Test
  void toStoredModelPathKeepsAbsolutePathOutsideProjectAndReferringModel() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());
    Path outside = Path.of("/tmp/outside-model.hdm");

    assertEquals(
        outside.normalize().toString(),
        DmModelLoadSupport.toStoredModelPath(outside.toString(), null, variables));
  }

  @Test
  void listBaseDimensionNamesReturnsSortedDimensionTablesOnly() throws Exception {
    Path fixture =
        Path.of("integration-tests/tests/basic/shared-conformed.hdm").toAbsolutePath().normalize();
    DimensionalModel model =
        DmModelLoadSupport.loadDimensionalModel(fixture.toString(), null, new Variables(), null);

    assertArrayEquals(
        new String[] {"dim_customer", "dim_date", "dim_product"},
        DmModelLoadSupport.listBaseDimensionNames(model));
  }
}
