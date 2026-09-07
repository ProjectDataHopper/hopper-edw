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
package org.hopper.edw.datavault.documentation.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.vfs.HopVfs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentationIoTest {

  @TempDir Path temp;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void filenameForLoadIsTheHopNativePathThatCanBeReopened() throws Exception {
    Path file = temp.resolve("pipelines").resolve("load").resolve("tiny.hpl");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "<pipeline/>");

    FileObject fo = HopVfs.getFileObject(file.toAbsolutePath().toString());
    String loadable = DocumentationIo.filenameForLoad(fo);

    assertEquals(HopVfs.getFilename(fo), loadable);
    assertTrue(HopVfs.getFileObject(loadable).exists());
    // FileName.getPath() omits the Windows drive; HopVfs.getFilename keeps it.
    assertTrue(
        loadable.contains("tiny.hpl"),
        "expected native filename to keep the original name: " + loadable);
  }
}
