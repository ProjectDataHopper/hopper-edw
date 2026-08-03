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
package org.apache.hop.datavault.impact;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.catalog.xp.RegisterResourceDefinitionGroupMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.catalog.RetailExampleCatalogFixtures;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImpactGraphBuilderTest {

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

    metadataProvider = new MemoryMetadataProvider();
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(RetailExampleCatalogFixtures.catalogStorageRootPath());
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);

    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail-impact");
    group.setDataCatalogConnection("local-catalog");
    group.setDataVaultModelFiles(List.of("${PROJECT_HOME}/models/retail-360.hdv"));
    group.setBusinessVaultModelFiles(List.of("${PROJECT_HOME}/models/retail-360.hbv"));
    group.setDimensionalModelFiles(List.of("${PROJECT_HOME}/models/retail-conformed-dims.hdm"));
    metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).save(group);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void build_retailModels_includeHubSatAndScd2() throws Exception {
    ResourceDefinitionGroupMeta group =
        metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).load("retail-impact");
    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
    ImpactGraph graph = ImpactGraphBuilder.build(models, variables);

    assertFalse(graph.nodes().isEmpty());
    assertFalse(graph.edges().isEmpty());

    // Customer hub source is E2E-customer-hub in retail catalog.
    RecordDefinitionKey customerHub =
        new RecordDefinitionKey("hop/retail-example/sources", "E2E-customer-hub");
    String labels = graph.formatBlastRadiusLabels(customerHub, null);
    assertTrue(
        labels.toLowerCase().contains("hub") || labels.toLowerCase().contains("customer"),
        "Expected hub/customer impact, got: " + labels);

    boolean hasScd2Edge =
        graph.edges().stream().anyMatch(e -> e.type() == ImpactEdgeType.DV_TO_BV_SCD2);
    assertTrue(hasScd2Edge, "Expected at least one DV→BV SCD2 edge in retail BV model");
  }
}
