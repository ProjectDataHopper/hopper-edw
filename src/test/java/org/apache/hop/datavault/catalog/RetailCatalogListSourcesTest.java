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
package org.apache.hop.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ensures retail catalog listing survives operations record definitions alongside DV sources. */
class RetailCatalogListSourcesTest {

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
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    metadataProvider = new MemoryMetadataProvider();
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(RetailExampleCatalogFixtures.catalogStorageRootPath());
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void listSourcesIncludesRetailE2eSourcesDespiteOperationsDefinitions() {
    List<DataVaultSource> sources =
        assertDoesNotThrow(
            () -> DvSourceCatalogService.listSources("local-catalog", variables, metadataProvider),
            "Listing retail catalog sources should not fail when operations JSON is present");

    assertFalse(sources.isEmpty(), "Expected retail E2E DV sources in catalog");
  }
}
