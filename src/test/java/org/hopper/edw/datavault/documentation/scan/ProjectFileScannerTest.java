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
package org.hopper.edw.datavault.documentation.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.vfs.HopVfs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFileScannerTest {

  @TempDir Path temp;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void findsNestedProjectFilesAndSkipsGitAndTargetOutput() throws Exception {
    Path source = temp.resolve("project");
    Files.createDirectories(source.resolve("pipelines/load"));
    Files.createDirectories(source.resolve(".git"));
    Files.createDirectories(source.resolve("work/documentation"));
    Files.writeString(source.resolve("root.hpl"), "<pipeline/>");
    Files.writeString(source.resolve("pipelines/load/child.hpl"), "<pipeline/>");
    Files.writeString(source.resolve("model.hdv"), "<dv-model/>");
    Files.writeString(source.resolve(".git/ignored.hpl"), "<pipeline/>");
    Files.writeString(source.resolve("work/documentation/generated.hpl"), "<pipeline/>");
    Files.writeString(source.resolve("readme.md"), "not a hop file");

    FileObject sourceRoot = HopVfs.getFileObject(source.toString());
    FileObject targetRoot = HopVfs.getFileObject(source.resolve("work/documentation").toString());
    List<ScannedFile> files = ProjectFileScanner.scan(sourceRoot, targetRoot);

    Set<String> relative =
        files.stream().map(ScannedFile::relativePath).collect(Collectors.toSet());
    assertEquals(Set.of("root.hpl", "pipelines/load/child.hpl", "model.hdv"), relative);
    for (String path : relative) {
      assertFalse(path.contains("\\"), path);
    }
  }

  @Test
  void underRelativeFolderHandlesWindowsSeparatorsAndCase() {
    assertTrue(
        ProjectFileScanner.isUnderRelativeFolder("work/documentation", "work/documentation"));
    assertTrue(
        ProjectFileScanner.isUnderRelativeFolder(
            "work\\documentation\\pipelines", "work/documentation"));
    assertTrue(
        ProjectFileScanner.isUnderRelativeFolder("Work/Documentation/out", "work/documentation"));
    assertFalse(ProjectFileScanner.isUnderRelativeFolder("work", "work/documentation"));
    assertFalse(ProjectFileScanner.isUnderRelativeFolder(".", "work/documentation"));
    assertFalse(ProjectFileScanner.isUnderRelativeFolder("pipelines", "work/documentation"));
  }
}
