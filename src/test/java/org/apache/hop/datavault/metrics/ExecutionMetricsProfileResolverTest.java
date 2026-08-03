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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.apache.hop.datavault.metrics.xp.RegisterExecutionMetricsProfileMetadataExtensionPoint;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionMetricsProfileResolverTest {

  private MemoryMetadataProvider metadataProvider;
  private Variables variables;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    new RegisterExecutionMetricsProfileMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() {
    variables = new Variables();
    metadataProvider = new MemoryMetadataProvider();
  }

  @Test
  void legacyModeEnabledWhenMetricsFolderSet() throws HopException {
    ResolvedExecutionMetrics resolved =
        ExecutionMetricsProfileResolver.resolve(
            null,
            "/tmp/metrics",
            "local-catalog",
            "Vault",
            "dv",
            null,
            variables,
            metadataProvider);

    assertTrue(resolved.enabled());
    assertEquals("/tmp/metrics", resolved.metricsOutputFolder());
    assertNotNull(resolved.publishContext());
    assertEquals("Vault", resolved.publishContext().targetDatabaseName());
    assertEquals("local-catalog", resolved.publishContext().catalogConnectionName());
    assertEquals(
        LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME,
        resolved.publishContext().operationsSchema());
    assertTrue(resolved.publishContext().publishDatabaseRows());
    assertTrue(resolved.publishContext().publishCatalogDefinitions());
  }

  @Test
  void legacyModeDisabledWhenMetricsFolderEmpty() throws HopException {
    ResolvedExecutionMetrics resolved =
        ExecutionMetricsProfileResolver.resolve(
            null, "", "local-catalog", "Vault", "dv", null, variables, metadataProvider);

    assertFalse(resolved.enabled());
    assertNull(resolved.metricsOutputFolder());
    assertNull(resolved.publishContext());
  }

  @Test
  void profileDisabledReturnsDisabledMetrics() throws HopException {
    ExecutionMetricsProfileMeta profile = new ExecutionMetricsProfileMeta("disabled-profile");
    profile.setEnabled(false);
    profile.setMetricsOutputFolder("/tmp/metrics");
    metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).save(profile);

    ResolvedExecutionMetrics resolved =
        ExecutionMetricsProfileResolver.resolve(
            "disabled-profile",
            "",
            "local-catalog",
            "Vault",
            "dv",
            null,
            variables,
            metadataProvider);

    assertFalse(resolved.enabled());
  }

  @Test
  void profileUsesCustomSchemaAndInheritsModelDatabase() throws HopException {
    ExecutionMetricsProfileMeta profile = new ExecutionMetricsProfileMeta("ops-profile");
    profile.setMetricsOutputFolder("${METRICS_DIR}");
    profile.setOperationsSchema("custom_ops");
    profile.setPublishDatabaseRows(false);
    profile.setDimLookupPreloadRatioThreshold(250L);
    profile.setTargetReadRatioThreshold(25L);
    profile.setSortRowsRiskThreshold(750_000L);
    profile.setHighTransformDurationMs(300_000L);
    metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).save(profile);

    variables.setVariable("METRICS_DIR", "/project/metrics");

    ResolvedExecutionMetrics resolved =
        ExecutionMetricsProfileResolver.resolve(
            "ops-profile", "", "action-catalog", "Vault", "dm", null, variables, metadataProvider);

    assertTrue(resolved.enabled());
    assertEquals("/project/metrics", resolved.metricsOutputFolder());
    assertEquals("custom_ops", resolved.publishContext().operationsSchema());
    assertEquals("Vault", resolved.publishContext().targetDatabaseName());
    assertEquals("action-catalog", resolved.publishContext().catalogConnectionName());
    assertFalse(resolved.publishContext().publishDatabaseRows());
    assertEquals(250L, resolved.publishContext().dimLookupPreloadRatioThreshold());
    assertEquals(25L, resolved.publishContext().targetReadRatioThreshold());
    assertEquals(750_000L, resolved.publishContext().sortRowsRiskThreshold());
    assertEquals(300_000L, resolved.publishContext().highTransformDurationMs());
    assertEquals("dm", resolved.publishContext().modelType());
  }

  @Test
  void legacyModeUsesDefaultInsightThresholds() throws HopException {
    ResolvedExecutionMetrics resolved =
        ExecutionMetricsProfileResolver.resolve(
            null,
            "/tmp/metrics",
            "local-catalog",
            "Vault",
            "dv",
            null,
            variables,
            metadataProvider);

    assertEquals(
        LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD,
        resolved.publishContext().dimLookupPreloadRatioThreshold());
    assertEquals(
        LoadRunInsightEngine.DEFAULT_TARGET_READ_RATIO_THRESHOLD,
        resolved.publishContext().targetReadRatioThreshold());
    assertEquals(
        LoadRunInsightEngine.DEFAULT_SORT_ROWS_RISK_THRESHOLD,
        resolved.publishContext().sortRowsRiskThreshold());
    assertEquals(
        LoadRunInsightEngine.DEFAULT_HIGH_TRANSFORM_DURATION_MS,
        resolved.publishContext().highTransformDurationMs());
  }

  @Test
  void profileTargetDatabaseOverridesModelDefault() throws HopException {
    ExecutionMetricsProfileMeta profile = new ExecutionMetricsProfileMeta("override-db");
    profile.setMetricsOutputFolder("/metrics");
    profile.setTargetDatabaseConnection("MetricsVault");
    metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).save(profile);

    ResolvedExecutionMetrics resolved =
        ExecutionMetricsProfileResolver.resolve(
            "override-db", "", "", "Vault", "bv", null, variables, metadataProvider);

    assertEquals("MetricsVault", resolved.publishContext().targetDatabaseName());
  }
}
