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
package org.hopper.edw.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.hopper.edw.catalog.xp.RegisterResourceDefinitionGroupMetadataExtensionPoint;
import org.hopper.edw.datavault.catalog.RetailExampleCatalogFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogVersionServiceTest {

  @TempDir Path tempDir;

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
    new RegisterResourceDefinitionGroupMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    Path projectHome = Path.of("retail-example").toAbsolutePath();
    variables.setVariable("PROJECT_HOME", projectHome.toString().replace('\\', '/'));

    // Copy committed retail seed sources into a temp catalog so we can mutate safely.
    Path catalogDir = tempDir.resolve("catalog-data");
    RetailExampleCatalogFixtures.copySeedSourcesInto(catalogDir);

    metadataProvider = new MemoryMetadataProvider();
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(catalogDir.toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);

    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail-sources-test");
    group.setDataCatalogConnection("local-catalog");
    group.setDataVaultModelFiles(List.of("${PROJECT_HOME}/models/retail-360.hdv"));
    metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).save(group);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void createFromGroup_snapshotsSourcesAndReadsBack() throws Exception {
    ResourceDefinitionGroupMeta group =
        metadataProvider
            .getSerializer(ResourceDefinitionGroupMeta.class)
            .load("retail-sources-test");

    CatalogVersionEntry entry =
        CatalogVersionService.createFromGroup(
            group, "v-test-1", "unit test tag", "tester", variables, metadataProvider);

    assertEquals("v-test-1", entry.getTag());
    assertTrue(entry.getRecordCount() > 0);
    assertNotEmptyHash(entry.getContentHash());

    Path versionsDir =
        tempDir.resolve("catalog-data").resolve(CatalogVersionStore.VERSIONS_DIRECTORY_NAME);
    assertTrue(Files.isDirectory(versionsDir));
    assertFalse(Files.exists(tempDir.resolve("catalog-data").resolve(".catalog-versions")));

    List<CatalogVersionEntry> listed =
        CatalogVersionService.listVersions("local-catalog", variables, metadataProvider);
    assertEquals(1, listed.size());
    assertEquals("v-test-1", listed.getFirst().getTag());

    List<RecordDefinition> all =
        CatalogVersionService.readAllAtVersion(
            "local-catalog", "v-test-1", variables, metadataProvider);
    assertEquals(entry.getRecordCount(), all.size());

    RecordDefinitionKey key =
        new RecordDefinitionKey("hop/retail-example/sources", "E2E-customer-hub");
    RecordDefinition atVersion =
        CatalogVersionService.readDefinition(
                "local-catalog", "v-test-1", key, variables, metadataProvider)
            .orElseThrow();
    assertEquals("E2E-customer-hub", atVersion.getKey().getName());

    // Mutate working tree; version remains unchanged.
    RecordDefinition working =
        RecordDefinitionRegistry.getInstance()
            .read("local-catalog", key, variables, metadataProvider);
    working.setDescription("mutated-after-tag");
    RecordDefinitionRegistry.getInstance()
        .update("local-catalog", working, variables, metadataProvider);

    RecordDefinition stillFrozen =
        CatalogVersionService.readDefinition(
                "local-catalog", "v-test-1", key, variables, metadataProvider)
            .orElseThrow();
    assertFalse("mutated-after-tag".equals(stillFrozen.getDescription()));
  }

  @Test
  void createFromGroup_rejectsDuplicateTag() throws Exception {
    ResourceDefinitionGroupMeta group =
        metadataProvider
            .getSerializer(ResourceDefinitionGroupMeta.class)
            .load("retail-sources-test");
    CatalogVersionService.createFromGroup(
        group, "dup-tag", null, "tester", variables, metadataProvider);
    assertThrows(
        HopException.class,
        () ->
            CatalogVersionService.createFromGroup(
                group, "dup-tag", null, "tester", variables, metadataProvider));
  }

  @Test
  void readDefinition_unknownTagFails() {
    assertThrows(
        HopException.class,
        () ->
            CatalogVersionService.readDefinition(
                "local-catalog",
                "missing-tag",
                new RecordDefinitionKey("hop/retail-example/sources", "E2E-customer-hub"),
                variables,
                metadataProvider));
  }

  private static void assertNotEmptyHash(String hash) {
    assertTrue(hash != null && hash.startsWith("sha256:") && hash.length() > 20);
  }
}
