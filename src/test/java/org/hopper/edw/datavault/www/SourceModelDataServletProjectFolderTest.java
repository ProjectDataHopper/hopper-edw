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
package org.hopper.edw.datavault.www;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceModelDataServletProjectFolderTest {

  @BeforeAll
  static void initEncr() throws Exception {
    Encr.init("Hop");
  }

  @Test
  void projectMetadataFolderFromProjectHome() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/opt/hopper-edw/retail-example");
    assertEquals(
        "/opt/hopper-edw/retail-example/metadata",
        SourceModelDataServlet.projectMetadataFolder(variables));
  }

  @Test
  void projectMetadataFolderFromHopProjectFolder() {
    Variables variables = new Variables();
    variables.setVariable("HOP_PROJECT_FOLDER", "/data/project/");
    assertEquals("/data/project/metadata", SourceModelDataServlet.projectMetadataFolder(variables));
  }

  @Test
  void emptyHopServerConfigProviderIsNotABackend() {
    MultiMetadataProvider empty =
        new MultiMetadataProvider(Encr.getEncoder(), Collections.emptyList(), new Variables());
    assertFalse(SourceModelDataServlet.hasBackends(empty));
    assertFalse(SourceModelDataServlet.hasBackends(null));
    MultiMetadataProvider nested = new MultiMetadataProvider(new Variables());
    MultiMetadataProvider withBackend = new MultiMetadataProvider(new Variables(), nested);
    assertTrue(SourceModelDataServlet.hasBackends(withBackend));
  }

  @Test
  void applyEnvironmentConfigFilesSetsDbHost() throws Exception {
    Path file = Files.createTempFile("hop-env", ".json");
    Files.writeString(
        file,
        """
        { "variables": [ { "name": "DB_HOST", "value": "postgres", "description": "db" } ] }
        """);
    Variables variables = new Variables();
    variables.setVariable("HOP_ENVIRONMENT_CONFIG_FILE_NAME_PATHS", file.toString());
    SourceModelDataServlet.applyEnvironmentConfigFiles(variables);
    assertEquals("postgres", variables.getVariable("DB_HOST"));
  }

  @Test
  void projectMetadataFolderFallsBackToEnvWhenVariablesEmpty() {
    String env = System.getenv("HOP_PROJECT_FOLDER");
    if (env == null || env.isBlank()) {
      assertNull(SourceModelDataServlet.projectMetadataFolder(new Variables()));
    } else {
      String expected = env.endsWith("/") ? env + "metadata" : env + "/metadata";
      assertEquals(expected, SourceModelDataServlet.projectMetadataFolder(new Variables()));
    }
  }
}
