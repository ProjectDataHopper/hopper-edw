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
package org.hopper.edw.datavault.openlineage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metrics.LoadRunMetricsCatalogPublisher;
import org.hopper.edw.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpsLineageEnricherTest {

  private MemoryMetadataProvider metadataProvider;

  @BeforeEach
  void setUp() throws Exception {
    metadataProvider = new MemoryMetadataProvider();
    ExecutionMetricsProfileMeta profile =
        new ExecutionMetricsProfileMeta("retail-execution-metrics");
    profile.setEnabled(true);
    profile.setTargetDatabaseConnection("OPS");
    profile.setOperationsSchema("dv_ops");
    metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).save(profile);
  }

  @Test
  void blankActionSchemaInheritsExecutionMetricsProfile() {
    OpenLineageExportOptions options =
        OpenLineageExportOptions.builder().opsDatabase("OPS").opsSchema(null).build();
    assertEquals("dv_ops", OpsLineageEnricher.resolveOpsSchema(options, metadataProvider, null));
  }

  @Test
  void explicitActionSchemaWinsOverProfile() {
    OpenLineageExportOptions options =
        OpenLineageExportOptions.builder().opsDatabase("OPS").opsSchema("custom_ops").build();
    assertEquals(
        "custom_ops", OpsLineageEnricher.resolveOpsSchema(options, metadataProvider, null));
  }

  @Test
  void noProfileFallsBackToConnectionDefault() throws Exception {
    MemoryMetadataProvider empty = new MemoryMetadataProvider();
    OpenLineageExportOptions options =
        OpenLineageExportOptions.builder().opsDatabase("OPS").opsSchema("").build();
    assertEquals(
        LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME,
        OpsLineageEnricher.resolveOpsSchema(options, empty, null));
  }
}
