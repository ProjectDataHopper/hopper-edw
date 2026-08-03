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

package org.apache.hop.catalog.impl.file;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDataCatalogTest {

  @TempDir Path tempDir;

  @Test
  void connect_rejectsUnresolvedProjectHome_withoutCreatingDirectories() throws Exception {
    // Hop leaves ${PROJECT_HOME} intact; Path.of(...).toAbsolutePath() would be under CWD.
    Path literalRoot =
        Path.of("${PROJECT_HOME}/work/edw-catalog").toAbsolutePath().normalize();
    boolean existedBefore = Files.exists(literalRoot);

    FileDataCatalog catalog = new FileDataCatalog();
    catalog.setStorageDirectory("${PROJECT_HOME}/work/edw-catalog");
    DataCatalogMeta meta = new DataCatalogMeta("local-catalog");
    meta.setCatalog(catalog);

    Variables variables = new Variables();
    // PROJECT_HOME intentionally unset — Hop leaves ${PROJECT_HOME} in the path.

    HopException error =
        assertThrows(
            HopException.class,
            () -> catalog.connect(meta, variables, new MemoryMetadataProvider()));
    assertTrue(
        error.getMessage() != null && error.getMessage().contains("PROJECT_HOME"),
        "Expected unresolved PROJECT_HOME in error, got: " + error.getMessage());
    if (!existedBefore) {
      assertFalse(
          Files.exists(literalRoot),
          "Must not create a literal ${PROJECT_HOME}/... folder under process CWD");
    }
  }

  @Test
  void connect_rejectsNullVariablesWhenStorageHasPlaceholders() {
    FileDataCatalog catalog = new FileDataCatalog();
    catalog.setStorageDirectory("${PROJECT_HOME}/work/edw-catalog");
    DataCatalogMeta meta = new DataCatalogMeta("local-catalog");
    meta.setCatalog(catalog);

    assertThrows(
        HopException.class, () -> catalog.connect(meta, null, new MemoryMetadataProvider()));
  }

  @Test
  void connect_createsDirectoryWhenProjectHomeResolves() throws Exception {
    Path storage = tempDir.resolve("work").resolve("edw-catalog");
    FileDataCatalog catalog = new FileDataCatalog();
    catalog.setStorageDirectory("${PROJECT_HOME}/work/edw-catalog");
    DataCatalogMeta meta = new DataCatalogMeta("local-catalog");
    meta.setCatalog(catalog);

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toAbsolutePath().normalize().toString());

    catalog.connect(meta, variables, new MemoryMetadataProvider());
    try {
      assertNotNull(catalog.getResolvedRoot());
      assertTrue(Files.isDirectory(storage));
      assertTrue(
          catalog.getResolvedRoot().endsWith(Path.of("work", "edw-catalog"))
              || catalog.getResolvedRoot().equals(storage.toAbsolutePath().normalize()));
    } finally {
      catalog.disconnect();
    }
  }

  @Test
  void connect_acceptsLiteralAbsolutePathWithoutVariables() throws Exception {
    Path storage = tempDir.resolve("catalog-data");
    FileDataCatalog catalog = new FileDataCatalog();
    catalog.setStorageDirectory(storage.toString().replace('\\', '/'));
    DataCatalogMeta meta = new DataCatalogMeta("local");
    meta.setCatalog(catalog);

    catalog.connect(meta, new Variables(), new MemoryMetadataProvider());
    try {
      assertTrue(Files.isDirectory(storage));
    } finally {
      catalog.disconnect();
    }
  }

}
