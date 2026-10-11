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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpsAnalyticsVaultBuilderTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void pipelineLinkKeepsSatelliteSourceMapping() throws Exception {
    DataVaultModel vault =
        OpsAnalyticsVaultBuilder.apply(
            OpsAnalyticsSourceModelBuilder.build(), new Variables(), new MemoryMetadataProvider());
    DvLink link = vault.findLink("lnk_load_pipeline_metric");
    assertNotNull(link);
    assertFalse(link.getLinkSatelliteSources().isEmpty());
    assertEquals("load_pipeline_metric", link.getLinkSatelliteSources().get(0).getSource());
    assertFalse(link.getLinkSatelliteSources().get(0).getSatelliteSourceKeyFields().isEmpty());
  }

  @Test
  void compositeTransformLinkGeneratesUpdatePipeline(@TempDir Path home) throws Exception {
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", home.toString());
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(home.resolve("catalog").toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    provider.getSerializer(DataCatalogMeta.class).save(catalog);
    RecordDefinitionRegistry.getInstance().invalidate();
    DatabaseMeta ops = new DatabaseMeta();
    ops.setName("OPS");
    ops.setDatabaseType("POSTGRESQL");
    ops.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    ops.setHostname("localhost");
    ops.setDBName("test_ops");
    ops.setUsername("test");
    ops.setPassword("test");
    provider.getSerializer(DatabaseMeta.class).save(ops);

    SourceModel source = OpsAnalyticsSourceModelBuilder.build();
    String sourceFile = home.resolve("ops-analytics.hsm").toString();
    source.setFilename(sourceFile);
    ModelXmlWriteSupport.writeModelXml(SourceModel.XML_TAG, source, sourceFile, variables);
    OpsAnalyticsSourcePublisher.publish(source, "local-catalog", variables, provider);

    DataVaultModel vault = OpsAnalyticsVaultBuilder.apply(source, variables, provider);
    vault.getConfigurationOrDefault().setDataCatalogConnection("local-catalog");
    DvLink link = vault.findLink("lnk_transform");
    assertNotNull(link);
    PipelineMeta pipeline =
        link.generateUpdatePipelines(provider, variables, vault, new Date(), null).get(0);
    String xml = pipeline.getXml(variables);
    assertTrue(xml.contains("pipeline_name"), xml);
    assertTrue(xml.contains("transform_name"), xml);
  }

  @Test
  void splitWritesPrefixedHubAndLinkFiles(@TempDir Path folder) throws Exception {
    Variables variables = new Variables();
    DataVaultModel vault =
        OpsAnalyticsVaultBuilder.apply(
            OpsAnalyticsSourceModelBuilder.build(), variables, new MemoryMetadataProvider());
    OpsAnalyticsVaultBuilder.writeSplit(
        vault, folder.toString(), variables, new MemoryMetadataProvider());
    assertTrue(Files.exists(folder.resolve("ops_hub_load_run.hdv")));
    assertTrue(Files.exists(folder.resolve("ops_lnk_load_pipeline_metric.hdv")));
    assertTrue(Files.exists(folder.resolve("ops_hub_pipeline.hdv")));
  }
}
