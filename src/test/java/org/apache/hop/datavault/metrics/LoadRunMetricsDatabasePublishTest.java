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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.UUID;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoadRunMetricsDatabasePublishTest {

  private static final String OPS_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
  private static final String OPS_PORT = System.getenv().getOrDefault("DB_PORT", "54320");
  private static final String OPS_USER = System.getenv().getOrDefault("DB_USER", "test");
  private static final String OPS_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "test");
  private static final String OPS_DATABASE =
      System.getenv().getOrDefault("DB_OPS_NAME", "test_ops");

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;
  private DatabaseMeta opsDatabase;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() throws HopException {
    variables = new Variables();
    variables.setVariable("DB_HOST", OPS_HOST);
    variables.setVariable("DB_PORT", OPS_PORT);
    variables.setVariable("DB_USER", OPS_USER);
    variables.setVariable("DB_PASSWORD", OPS_PASSWORD);
    variables.setVariable("DB_OPS_NAME", OPS_DATABASE);

    metadataProvider = new MemoryMetadataProvider();
    opsDatabase = buildPostgresDatabaseMeta("OPS");
    metadataProvider.getSerializer(DatabaseMeta.class).save(opsDatabase);

    assumeTrue(
        LoadRunMetricsDatabaseAssertionSupport.canConnect(opsDatabase, variables),
        "OPS PostgreSQL is not reachable on " + OPS_HOST + ":" + OPS_PORT + "/" + OPS_DATABASE);
  }

  @Test
  void publishPersistsLoadRunTransformAndInsightRows() throws Exception {
    String runId = "test-run-" + UUID.randomUUID();
    List<DvUpdateTableMetrics> pipelines =
        List.of(
            DvUpdateTableMetrics.builder()
                .runId(runId)
                .pipelineName("hub_customer-CRM-customer")
                .tableType("hub")
                .tableName("hub_customer")
                .sourceName("CRM-customer")
                .sourceRowsRead(100L)
                .targetRowsRead(5000L)
                .targetRowsInserted(10L)
                .transform(
                    TransformRunMetrics.builder()
                        .transformName("sort_changes")
                        .logicalRole(GeneratedPipelineMetadataConstants.ROLE_SORT)
                        .rowsRead(600_000L)
                        .build())
                .build());

    DvUpdateMetricsCollector.LoadRunPublishContext context =
        new DvUpdateMetricsCollector.LoadRunPublishContext(
            null,
            "OPS",
            LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME,
            "verify-load-metrics",
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
            false,
            true,
            true,
            LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_TARGET_READ_RATIO_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_SORT_ROWS_RISK_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_HIGH_TRANSFORM_DURATION_MS,
            null);

    LoadRunMetricsCatalogPublisher.publish(
        LogChannel.GENERAL,
        context,
        runId,
        "retail-360",
        GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
        "verify-load-metrics",
        "log-channel-test",
        true,
        0L,
        null,
        pipelines,
        LoadRunInsightEngine.evaluate(pipelines),
        variables,
        metadataProvider);

    LoadRunMetricsDatabaseAssertionSupport.LoadRunDatabaseCounts counts =
        LoadRunMetricsDatabaseAssertionSupport.countRowsForRun(
            opsDatabase, LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME, runId, variables);

    assertEquals(1L, counts.loadRunRows());
    assertTrue(counts.transformMetricRows() >= 1L);
    assertTrue(counts.insightRows() >= 1L);
  }

  private static DatabaseMeta buildPostgresDatabaseMeta(String name) {
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName(name);
    databaseMeta.setDatabaseType("POSTGRESQL");
    databaseMeta.setHostname(OPS_HOST);
    databaseMeta.setPort(OPS_PORT);
    databaseMeta.setUsername(OPS_USER);
    databaseMeta.setPassword(OPS_PASSWORD);
    databaseMeta.setDBName(OPS_DATABASE);
    return databaseMeta;
  }
}
