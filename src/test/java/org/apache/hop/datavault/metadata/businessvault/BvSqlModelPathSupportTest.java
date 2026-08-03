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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BvSqlModelPathSupportTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void looksLikeFilesystemPathDetectsRelativeAndExtensions() {
    assertTrue(BvSqlModelPathSupport.looksLikeFilesystemPath("../dv/retail-360"));
    assertTrue(BvSqlModelPathSupport.looksLikeFilesystemPath("models/core.hdv"));
    assertTrue(BvSqlModelPathSupport.looksLikeFilesystemPath("${PROJECT_HOME}/models/x"));
    assertFalse(BvSqlModelPathSupport.looksLikeFilesystemPath("retail-360"));
  }

  @Test
  void resolvesRelativePathWithoutExtension() throws Exception {
    Path bvDir = tempDir.resolve("bv");
    Path dvDir = tempDir.resolve("dv");
    Files.createDirectories(bvDir);
    Files.createDirectories(dvDir);
    Path hdv = dvDir.resolve("retail-360.hdv");
    Files.writeString(hdv, minimalHdv("retail-360"));
    Path hbv = bvDir.resolve("retail-sql.hbv");
    Files.writeString(hbv, "<!-- empty -->");

    Variables vars = new Variables();
    BvSqlModelPathSupport.ResolvedModelPath resolved =
        BvSqlModelPathSupport.resolveExistingModelPath("../dv/retail-360", hbv.toString(), vars);

    assertEquals(BvSqlModelPathSupport.ModelFileKind.HDV, resolved.kind());
    assertTrue(resolved.loadPath().replace('\\', '/').endsWith("/dv/retail-360.hdv"));
    assertEquals("../dv/retail-360", resolved.authoringArg());
  }

  @Test
  void resolvesBasenameNextToBvWhenHdvPresent() throws Exception {
    Path modelDir = tempDir.resolve("models");
    Files.createDirectories(modelDir);
    Path hdv = modelDir.resolve("vault1.hdv");
    Files.writeString(hdv, minimalHdv("vault1"));
    Path hbv = modelDir.resolve("vault1.hbv");
    Files.writeString(hbv, "<!-- empty -->");

    BvSqlModelPathSupport.ResolvedModelPath resolved =
        BvSqlModelPathSupport.resolveExistingModelPath("vault1", hbv.toString(), new Variables());

    assertEquals(BvSqlModelPathSupport.ModelFileKind.HDV, resolved.kind());
    assertTrue(resolved.loadPath().replace('\\', '/').endsWith("/models/vault1.hdv"));
  }

  @Test
  void missingPathThrows() {
    Path hbv = tempDir.resolve("x.hbv");
    assertThrows(
        HopException.class,
        () ->
            BvSqlModelPathSupport.resolveExistingModelPath(
                "../nope/missing", hbv.toString(), new Variables()));
  }

  @Test
  void candidatesIncludeHdvSuffixAndProjectHome() {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/proj");
    var candidates =
        BvSqlModelPathSupport.buildCandidatePaths("retail-360", "/proj/bv/a.hbv", vars);
    assertTrue(candidates.stream().anyMatch(c -> c.endsWith("retail-360.hdv")));
    assertTrue(candidates.stream().anyMatch(c -> c.contains("${PROJECT_HOME}")));
  }

  private static String minimalHdv(String name) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <data-vault-model>
          <name>%s</name>
          <tables/>
        </data-vault-model>
        """
        .formatted(name);
  }
}
