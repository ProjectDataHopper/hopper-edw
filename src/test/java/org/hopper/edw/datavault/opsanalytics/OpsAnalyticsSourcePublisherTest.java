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
package org.hopper.edw.datavault.opsanalytics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** OPS feeds the vault reads must exist in the project sources catalog. */
class OpsAnalyticsSourcePublisherTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @Test
  void publishesTablesAndQueries(@TempDir Path home) throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", home.toString());
    MemoryMetadataProvider provider = catalogProvider(home.resolve("catalog"));
    saveDatabase(provider);
    SourceModel model = OpsAnalyticsSourceModelBuilder.build(OpsAnalyticsRequest.defaults());
    model.setFilename(home.resolve("ops-analytics.hsm").toString());

    int published = OpsAnalyticsSourcePublisher.publish(model, "local-catalog", variables, provider);

    assertTrue(published >= 16, "tables plus conformed queries, got " + published);
    assertNotNull(
        DvSourceCatalogService.resolveSource("load_run", "local-catalog", variables, provider));
    assertNotNull(
        DvSourceCatalogService.resolveSource("transform", "local-catalog", variables, provider));
    assertNotNull(
        DvSourceCatalogService.resolveSource(
            "workflow_load_overview", "local-catalog", variables, provider));
  }

  /**
   * Writes the retail working catalog when {@code OPS_ANALYTICS_PUBLISH_RETAIL=1}. The catalog
   * lives under {@code work/} and is replaced by bootstrap, so the analytics workflow publishes
   * again on each run.
   */
  @Test
  void publishRetailWorkCatalogWhenRequested() throws Exception {
    assumeTrue("1".equals(System.getenv("OPS_ANALYTICS_PUBLISH_RETAIL")));
    Path retail = Path.of("retail-example");
    Path catalog = retail.resolve("work/edw-catalog");
    assumeTrue(Files.isDirectory(catalog));
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", retail.toAbsolutePath().toString());
    JsonMetadataProvider provider =
        new JsonMetadataProvider(
            org.apache.hop.core.encryption.Encr.getEncoder(),
            retail.resolve("metadata").toString(),
            variables);
    RecordDefinitionRegistry.getInstance().invalidate();
    String source = retail.resolve("models/ops-analytics/ops-analytics.hsm").toString();
    int published = OpsAnalyticsSourcePublisher.publish(source, "local-catalog", variables, provider);
    assertTrue(published >= 16);
    assertNotNull(
        DvSourceCatalogService.resolveSource("load_run", "local-catalog", variables, provider));
  }

  private static MemoryMetadataProvider catalogProvider(Path catalogDir) throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(catalogDir.toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    provider.getSerializer(DataCatalogMeta.class).save(catalog);
    RecordDefinitionRegistry.getInstance().invalidate();
    return provider;
  }

  private static void saveDatabase(IHopMetadataProvider provider) throws HopException {
    DatabaseMeta database = new DatabaseMeta();
    database.setName("OPS");
    database.setDatabaseType("POSTGRESQL");
    database.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    database.setHostname("localhost");
    database.setDBName("test_ops");
    database.setPort("54320");
    database.setUsername("test");
    database.setPassword("test");
    provider.getSerializer(DatabaseMeta.class).save(database);
  }
}
