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
package org.apache.hop.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoadRunMetricsCatalogPublisherTest {

  private static final String CATALOG_CONNECTION = "metrics-test-catalog";

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

    metadataProvider = new MemoryMetadataProvider();
    Path catalogDir = Files.createTempDirectory("load-run-metrics-catalog-test");
    metadataProvider.getSerializer(DataCatalogMeta.class).save(buildCatalogMeta(catalogDir));

    DatabaseMeta ops = new DatabaseMeta();
    ops.setName("OPS");
    metadataProvider.getSerializer(DatabaseMeta.class).save(ops);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void resolveDurationMsUsesWallClockWhenBothDatesPresent() {
    Date start = new Date(1_700_000_000_000L);
    Date end = new Date(1_700_000_001_500L);
    assertEquals(1500L, DvUpdateTableMetrics.resolveDurationMs(start, end));
    assertEquals(0L, DvUpdateTableMetrics.resolveDurationMs(null, end));
    assertEquals(0L, DvUpdateTableMetrics.resolveDurationMs(start, null));
    assertEquals(0L, DvUpdateTableMetrics.resolveDurationMs(end, start));
  }

  @Test
  void publishUpsertsOperationsRecordDefinitionsOnOpsDatabase() throws Exception {
    String namespace = LoadRunMetricsCatalogPublisher.operationsNamespace(variables);
    DvUpdateMetricsCollector.LoadRunPublishContext context =
        new DvUpdateMetricsCollector.LoadRunPublishContext(
            CATALOG_CONNECTION,
            "OPS",
            LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME,
            "update-retail-dv-bv-dm",
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
            true,
            false,
            false,
            LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_TARGET_READ_RATIO_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_SORT_ROWS_RISK_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_HIGH_TRANSFORM_DURATION_MS,
            null);

    List<DvUpdateTableMetrics> pipelines =
        List.of(
            DvUpdateTableMetrics.builder()
                .runId("run-1")
                .modelName("retail-360")
                .modelType(GeneratedPipelineMetadataConstants.MODEL_TYPE_DV)
                .pipelineName("hub_customer-CRM-customer")
                .tableType("hub")
                .tableName("hub_customer")
                .sourceName("CRM-customer")
                .sourceRowsRead(100)
                .targetRowsInserted(10)
                .success(true)
                .transform(
                    TransformRunMetrics.builder()
                        .transformName("lookup_d_customer")
                        .logicalRole(GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP)
                        .rowsRead(1000L)
                        .build())
                .build());

    LoadRunMetricsCatalogPublisher.publish(
        LogChannel.GENERAL,
        context,
        "run-1",
        "retail-360",
        GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
        "update-retail-dv-bv-dm",
        "log-channel-1",
        true,
        0L,
        pipelines,
        variables,
        metadataProvider);

    RecordDefinition loadRun =
        RecordDefinitionRegistry.getInstance()
            .read(
                CATALOG_CONNECTION,
                new RecordDefinitionKey(namespace, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN),
                variables,
                metadataProvider);
    assertNotNull(loadRun);
    assertEquals(RecordDefinitionType.PHYSICAL_TABLE, loadRun.getType());
    assertEquals("OPS", loadRun.getPhysicalTable().getDatabaseMetaName());
    assertEquals(
        LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME,
        loadRun.getPhysicalTable().getSchemaName());
    assertFalse(loadRun.getFields().isEmpty());

    RecordDefinition pipelineMetric =
        RecordDefinitionRegistry.getInstance()
            .read(
                CATALOG_CONNECTION,
                new RecordDefinitionKey(
                    namespace, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC),
                variables,
                metadataProvider);
    assertNotNull(pipelineMetric);
    assertNotNull(pipelineMetric.getFields().searchValueMeta("pipeline_name"));
    assertNotNull(pipelineMetric.getFields().searchValueMeta("execution_start_date"));
    assertNotNull(pipelineMetric.getFields().searchValueMeta("execution_end_date"));
    assertNotNull(pipelineMetric.getFields().searchValueMeta("duration_ms"));
    assertTrue(pipelineMetric.getTags().contains("load-metrics"));

    RecordDefinition transformMetric =
        RecordDefinitionRegistry.getInstance()
            .read(
                CATALOG_CONNECTION,
                new RecordDefinitionKey(
                    namespace, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC),
                variables,
                metadataProvider);
    assertNotNull(transformMetric);
    assertNotNull(transformMetric.getFields().searchValueMeta("transform_name"));
    assertTrue(transformMetric.getTags().contains("load-metrics"));

    RecordDefinition loadInsight =
        RecordDefinitionRegistry.getInstance()
            .read(
                CATALOG_CONNECTION,
                new RecordDefinitionKey(
                    namespace, LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT),
                variables,
                metadataProvider);
    assertNotNull(loadInsight);
    assertNotNull(loadInsight.getFields().searchValueMeta("code"));
    assertTrue(loadInsight.getTags().contains("insights"));
  }

  private static DataCatalogMeta buildCatalogMeta(Path catalogDir) {
    DataCatalogMeta meta = new DataCatalogMeta();
    meta.setName(CATALOG_CONNECTION);
    meta.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(catalogDir.toString().replace('\\', '/'));
    meta.setCatalog(fileCatalog);
    return meta;
  }
}
