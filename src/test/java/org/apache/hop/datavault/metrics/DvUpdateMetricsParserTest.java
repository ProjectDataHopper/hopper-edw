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

import org.junit.jupiter.api.Test;

class DvUpdateMetricsParserTest {

  @Test
  void parsesDimensionalDimensionPipelineNames() {
    DvUpdateMetricsParser.ParsedPipeline parsed =
        DvUpdateMetricsParser.parse("dm-dim-d_customer").orElseThrow();
    assertEquals("dimension", parsed.tableType());
    assertEquals("d_customer", parsed.tableName());
    assertEquals("", parsed.sourceName());
  }

  @Test
  void parsesDataVaultSatellitePipelineNames() {
    DvUpdateMetricsParser.ParsedPipeline parsed =
        DvUpdateMetricsParser.parse("sat-customer-crm").orElseThrow();
    assertEquals("sat", parsed.tableType());
    assertEquals("customer", parsed.tableName());
    assertEquals("crm", parsed.sourceName());
  }

  @Test
  void ignoresOrchestratorPipelineNames() {
    assertTrue(
        DvUpdateMetricsParser.parse("DV Update Orchestrator - retail-conformed-dims").isEmpty());
  }

  @Test
  void parsesBusinessVaultScd2PipelineNames() {
    DvUpdateMetricsParser.ParsedPipeline parsed =
        DvUpdateMetricsParser.parse("bv-scd2-customer_360").orElseThrow();
    assertEquals("scd2", parsed.tableType());
    assertEquals("customer_360", parsed.tableName());
    assertEquals("", parsed.sourceName());
  }

  @Test
  void parsesBulkAndStagingWriteTransformsViaExtractorConstants() {
    assertEquals(
        "bulk_load_to_hub_customer",
        DvUpdateMetricsConstants.BULK_WRITE_TRANSFORM_PREFIX + "hub_customer");
    assertEquals(
        "stage_to_hub_customer",
        DvUpdateMetricsConstants.STAGING_WRITE_TRANSFORM_PREFIX + "hub_customer");
  }
}
