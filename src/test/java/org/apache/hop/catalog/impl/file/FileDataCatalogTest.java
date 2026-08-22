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
package org.apache.hop.catalog.impl.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDataCatalogTest {

  @TempDir Path tempDir;

  @Test
  void connect_rejectsUnresolvedProjectHome_withoutCreatingDirectories() throws Exception {
    // Hop leaves ${PROJECT_HOME} intact; Path.of(...).toAbsolutePath() would be under CWD.
    Path literalRoot = Path.of("${PROJECT_HOME}/work/edw-catalog").toAbsolutePath().normalize();
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

  @Test
  void list_withNamespacePrefix_scopesToSourcesAndIgnoresModels() throws Exception {
    FileDataCatalog catalog = connectedCatalog();
    try {
      catalog.create(definition("hop/demo/sources", "src-a", RecordDefinitionType.DV_SOURCE));
      catalog.create(definition("hop/demo/sources", "src-b", RecordDefinitionType.DV_SOURCE));
      catalog.create(definition("hop/demo/models/vault", "hub_x", RecordDefinitionType.DV_HUB));

      RecordDefinitionQuery sourcesOnly = new RecordDefinitionQuery();
      sourcesOnly.setNamespacePrefix("hop/demo/sources");
      sourcesOnly.setType(RecordDefinitionType.DV_SOURCE);

      List<RecordDefinitionRef> refs = catalog.list(sourcesOnly);
      Set<String> names = refs.stream().map(r -> r.getKey().getName()).collect(Collectors.toSet());
      assertEquals(Set.of("src-a", "src-b"), names);
      assertTrue(refs.stream().allMatch(r -> r.getType() == RecordDefinitionType.DV_SOURCE));
    } finally {
      catalog.disconnect();
    }
  }

  @Test
  void list_returnsEmptyWhenNamespaceDirectoryMissing() throws Exception {
    FileDataCatalog catalog = connectedCatalog();
    try {
      catalog.create(definition("hop/demo/models/vault", "hub_x", RecordDefinitionType.DV_HUB));

      RecordDefinitionQuery sourcesOnly = new RecordDefinitionQuery();
      sourcesOnly.setNamespacePrefix("hop/demo/sources");
      sourcesOnly.setType(RecordDefinitionType.DV_SOURCE);

      assertTrue(catalog.list(sourcesOnly).isEmpty());
    } finally {
      catalog.disconnect();
    }
  }

  @Test
  void anyMatch_shortCircuitsWithoutListingModels() throws Exception {
    FileDataCatalog catalog = connectedCatalog();
    try {
      catalog.create(definition("hop/demo/sources", "src-a", RecordDefinitionType.DV_SOURCE));
      catalog.create(definition("hop/demo/models/vault", "hub_x", RecordDefinitionType.DV_HUB));

      RecordDefinitionQuery sourcesOnly = new RecordDefinitionQuery();
      sourcesOnly.setNamespacePrefix("hop/demo/sources");
      sourcesOnly.setType(RecordDefinitionType.DV_SOURCE);
      assertTrue(catalog.anyMatch(sourcesOnly));

      RecordDefinitionQuery missing = new RecordDefinitionQuery();
      missing.setNamespacePrefix("hop/demo/missing");
      assertFalse(catalog.anyMatch(missing));
    } finally {
      catalog.disconnect();
    }
  }

  @Test
  void list_readsLightweightHeaderWithoutFailingOnLargeRowMeta() throws Exception {
    FileDataCatalog catalog = connectedCatalog();
    try {
      RecordDefinition source =
          definition("hop/demo/sources", "fat-source", RecordDefinitionType.DV_SOURCE);
      RowMeta fields = new RowMeta();
      for (int i = 0; i < 50; i++) {
        fields.addValueMeta(new ValueMetaString("col_" + i));
      }
      source.setFields(fields);
      source.setDescription("with fat row meta");
      catalog.create(source);

      RecordDefinitionQuery query = new RecordDefinitionQuery();
      query.setNamespacePrefix("hop/demo/sources");
      List<RecordDefinitionRef> refs = catalog.list(query);
      assertEquals(1, refs.size());
      assertEquals("fat-source", refs.get(0).getKey().getName());
      assertEquals("with fat row meta", refs.get(0).getDescription());
      assertEquals(RecordDefinitionType.DV_SOURCE, refs.get(0).getType());
    } finally {
      catalog.disconnect();
    }
  }

  private FileDataCatalog connectedCatalog() throws HopException {
    Path storage = tempDir.resolve("catalog-data");
    FileDataCatalog catalog = new FileDataCatalog();
    catalog.setStorageDirectory(storage.toString().replace('\\', '/'));
    DataCatalogMeta meta = new DataCatalogMeta("local");
    meta.setCatalog(catalog);
    catalog.connect(meta, new Variables(), new MemoryMetadataProvider());
    return catalog;
  }

  private static RecordDefinition definition(
      String namespace, String name, RecordDefinitionType type) {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, name));
    definition.setType(type);
    definition.setDescription(name + " desc");
    definition.setFields(new RowMeta());
    return definition;
  }
}
