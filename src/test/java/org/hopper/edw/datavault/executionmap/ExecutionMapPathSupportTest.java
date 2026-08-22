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
package org.hopper.edw.datavault.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapArtifactSnapshot;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionMapPathSupportTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void logicalSchemesAreUnchanged() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());

    assertEquals(
        "dataset://hop/retail/sources::hub",
        ExecutionMapPathSupport.toStoredPath("dataset://hop/retail/sources::hub", variables));
    assertEquals(
        "generated://model/pipe",
        ExecutionMapPathSupport.toStoredPath("generated://model/pipe", variables));
    assertEquals(
        "synthetic://orch", ExecutionMapPathSupport.toStoredPath("synthetic://orch", variables));
    assertTrue(ExecutionMapPathSupport.isLogicalScheme("dataset://x"));
    assertFalse(ExecutionMapPathSupport.isLogicalScheme(tempDir.resolve("a.hwf").toString()));
  }

  @Test
  void toStoredPathRelativizesUnderProjectHome() throws Exception {
    Path models = tempDir.resolve("models");
    Files.createDirectories(models);
    Path model = models.resolve("retail-360.hdv");
    Files.writeString(model, "<x/>");

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toAbsolutePath().normalize().toString());

    assertEquals(
        "${PROJECT_HOME}/models/retail-360.hdv",
        ExecutionMapPathSupport.toStoredPath(model.toString(), variables));
  }

  @Test
  void portableizeDocumentRewritesFilesystemPathsOnly() throws Exception {
    Path wf = tempDir.resolve("workflows");
    Files.createDirectories(wf);
    Path hwf = wf.resolve("run.hwf");
    Files.writeString(hwf, "<workflow/>");

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toAbsolutePath().normalize().toString());

    ExecutionMapDocument document = new ExecutionMapDocument();
    document.setRootArtifactPath(hwf.toString());

    ExecutionMapNode fileNode = new ExecutionMapNode();
    fileNode.setPath(hwf.toString());
    document.getNodesOrEmpty().add(fileNode);

    ExecutionMapNode datasetNode = new ExecutionMapNode();
    datasetNode.setPath("dataset://hop/retail/sources::x");
    document.getNodesOrEmpty().add(datasetNode);

    ExecutionMapArtifactSnapshot snapshot = new ExecutionMapArtifactSnapshot();
    snapshot.setSourcePath(hwf.toString());
    document.getSnapshotsOrEmpty().add(snapshot);

    ExecutionMapPathSupport.portableizeDocument(document, variables);

    assertEquals("${PROJECT_HOME}/workflows/run.hwf", document.getRootArtifactPath());
    assertEquals("${PROJECT_HOME}/workflows/run.hwf", fileNode.getPath());
    assertEquals("dataset://hop/retail/sources::x", datasetNode.getPath());
    assertEquals("${PROJECT_HOME}/workflows/run.hwf", snapshot.getSourcePath());
  }

  @Test
  void toResolvedPathExpandsProjectHome() throws Exception {
    Path models = tempDir.resolve("models");
    Files.createDirectories(models);
    Path model = models.resolve("x.hdv");
    Files.writeString(model, "<x/>");

    Variables variables = new Variables();
    String home = tempDir.toAbsolutePath().normalize().toString();
    variables.setVariable("PROJECT_HOME", home);

    String resolved =
        ExecutionMapPathSupport.toResolvedPath("${PROJECT_HOME}/models/x.hdv", variables);
    assertEquals(
        model.toAbsolutePath().normalize().toString(), Path.of(resolved).normalize().toString());
  }
}
