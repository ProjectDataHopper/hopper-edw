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
package org.hopper.edw.catalog.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionRef;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordDefinitionRegistryListCacheTest {

  @TempDir Path tempDir;

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws HopException {
    variables = new Variables();
    metadataProvider = new MemoryMetadataProvider();

    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(tempDir.resolve("catalog-data").toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void list_isCachedUntilMutation() throws Exception {
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    RecordDefinitionQuery query = sourcesQuery();

    registry.create("local-catalog", source("alpha"), variables, metadataProvider);

    List<RecordDefinitionRef> first =
        registry.list("local-catalog", query, variables, metadataProvider);
    List<RecordDefinitionRef> second =
        registry.list("local-catalog", query, variables, metadataProvider);
    assertEquals(1, first.size());
    assertEquals(1, second.size());
    assertEquals("alpha", first.get(0).getKey().getName());
    assertNotSame(first, second);
    assertNotSame(first.get(0), second.get(0));

    registry.create("local-catalog", source("beta"), variables, metadataProvider);

    List<RecordDefinitionRef> afterCreate =
        registry.list("local-catalog", query, variables, metadataProvider);
    assertEquals(2, afterCreate.size());
  }

  @Test
  void hasAny_isCachedAndInvalidatesOnMutation() throws Exception {
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    RecordDefinitionQuery query = sourcesQuery();

    assertEquals(false, registry.hasAny("local-catalog", query, variables, metadataProvider));
    assertEquals(false, registry.hasAny("local-catalog", query, variables, metadataProvider));

    registry.create("local-catalog", source("alpha"), variables, metadataProvider);
    assertEquals(true, registry.hasAny("local-catalog", query, variables, metadataProvider));

    List<String> names = registry.listNames("local-catalog", query, variables, metadataProvider);
    assertEquals(List.of("alpha"), names);
    assertEquals(
        List.of("alpha"), registry.listNames("local-catalog", query, variables, metadataProvider));
  }

  @Test
  void reconnectsWhenResolvedStorageDirectoryChanges() throws Exception {
    Path first = tempDir.resolve("first");
    Path second = tempDir.resolve("second");
    Files.createDirectories(first);
    Files.createDirectories(second);

    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("relocating-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory("${PROJECT_HOME}/catalog");
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);

    Variables firstVars = new Variables();
    firstVars.setVariable("PROJECT_HOME", first.toAbsolutePath().normalize().toString());
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    registry.create("relocating-catalog", source("alpha"), firstVars, metadataProvider);
    assertEquals(
        1, registry.list("relocating-catalog", sourcesQuery(), firstVars, metadataProvider).size());

    Variables secondVars = new Variables();
    secondVars.setVariable("PROJECT_HOME", second.toAbsolutePath().normalize().toString());
    assertEquals(
        0,
        registry.list("relocating-catalog", sourcesQuery(), secondVars, metadataProvider).size());
  }

  @Test
  void listCacheKey_distinguishesQueryFields() {
    RecordDefinitionQuery a = new RecordDefinitionQuery();
    a.setNamespacePrefix("hop/demo/sources");
    a.setType(RecordDefinitionType.DV_SOURCE);

    RecordDefinitionQuery b = new RecordDefinitionQuery();
    b.setNamespacePrefix("hop/demo/sources");
    b.setType(RecordDefinitionType.DV_HUB);

    assertEquals(
        RecordDefinitionRegistry.listCacheKey("c1", a),
        RecordDefinitionRegistry.listCacheKey("c1", a));
    assertEquals(
        false,
        RecordDefinitionRegistry.listCacheKey("c1", a)
            .equals(RecordDefinitionRegistry.listCacheKey("c1", b)));
  }

  private static RecordDefinitionQuery sourcesQuery() {
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNamespacePrefix("hop/demo/sources");
    query.setType(RecordDefinitionType.DV_SOURCE);
    return query;
  }

  private static RecordDefinition source(String name) {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/demo/sources", name));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setFields(new RowMeta());
    return definition;
  }
}
