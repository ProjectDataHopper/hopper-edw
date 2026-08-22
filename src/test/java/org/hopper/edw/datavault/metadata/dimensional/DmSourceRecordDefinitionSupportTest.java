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
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.catalog.RetailExampleCatalogFixtures;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DmSourceRecordDefinitionSupportTest {

  private static final String CATALOG_CONNECTION = "local-catalog";
  private static final String NAMESPACE = "hop/retail-example/sources";
  private static final String RECORD_NAME = "E2E-order-header";

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;
  private DimensionalConfiguration configuration;

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
    DatabaseMeta crm = new DatabaseMeta();
    crm.setName("CRM");
    metadataProvider.getSerializer(DatabaseMeta.class).save(crm);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(buildCatalogMeta());

    RecordDefinitionRegistry.getInstance().invalidate();

    configuration = new DimensionalConfiguration();
    configuration.setDataCatalogConnection(CATALOG_CONNECTION);
  }

  @Test
  void resolvesOrderHeaderFieldsFromRetailCatalog() throws Exception {
    DmSourceConfiguration source = recordSourceConfiguration();

    var rowMeta =
        DmSourceRecordDefinitionSupport.resolveSourceRowMeta(
            source, configuration, variables, metadataProvider);

    assertNotNull(rowMeta);
    assertTrue(rowMeta.size() >= 3);
    assertNotNull(rowMeta.searchValueMeta("order_id"));
    assertEquals(IValueMeta.TYPE_STRING, rowMeta.searchValueMeta("order_id").getType());
    assertNotNull(rowMeta.searchValueMeta("customer_id"));
  }

  @Test
  void appendsTableInputForDatabaseRecordDefinition() throws Exception {
    DmSourceConfiguration source = recordSourceConfiguration();
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setMetadataProvider(metadataProvider);

    TransformMeta sourceTransform =
        DmSourceRecordDefinitionSupport.appendSourceInput(
            source,
            configuration,
            pipelineMeta,
            new org.apache.hop.core.gui.Point(100, 100),
            "source_orders",
            variables,
            metadataProvider);

    assertNotNull(sourceTransform);
    assertTrue(sourceTransform.getTransform() instanceof TableInputMeta);
    TableInputMeta tableInputMeta = (TableInputMeta) sourceTransform.getTransform();
    assertEquals("CRM", tableInputMeta.getConnection());
    assertFalse(pipelineMeta.getTransforms().isEmpty());
  }

  private static DmSourceConfiguration recordSourceConfiguration() {
    DmSourceConfiguration source = new DmSourceConfiguration();
    source.setSourceType(DmSourceType.RECORD_DEFINITION);
    source.setSourceCatalogConnection(CATALOG_CONNECTION);
    source.setSourceRecordNamespace(NAMESPACE);
    source.setSourceRecordName(RECORD_NAME);
    return source;
  }

  private static DataCatalogMeta buildCatalogMeta() {
    DataCatalogMeta meta = new DataCatalogMeta();
    meta.setName(CATALOG_CONNECTION);
    meta.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(RetailExampleCatalogFixtures.catalogStorageRootPath());
    meta.setCatalog(fileCatalog);
    return meta;
  }
}
