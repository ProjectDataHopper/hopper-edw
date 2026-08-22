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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvStagingBulkLoadPipelineSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void postgresBulkLoadPipelineReadsStagedCsvAndUsesPgBulkLoader() throws Exception {
    if (!DvBulkLoadPluginSupport.isTransformPluginAvailable(
        DvBulkLoadPluginSupport.PG_BULK_LOADER_ID)) {
      return;
    }

    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setBulkLoadDelimiter(",");
    config.setBulkLoadEnclosure("\"");
    config.setBulkLoadEncoding("UTF-8");

    PipelineMeta pipelineMeta =
        DvStagingBulkLoadPipelineSupport.buildPostgresBulkLoadPipeline(
            config,
            new Variables(),
            "target-db",
            "f_orders",
            List.of("order_hk", "customer_hk"),
            "/tmp/dv2/bulk/dm-fact-f_orders-0.csv",
            0);

    assertEquals("bulk-load-f_orders-0", pipelineMeta.getName());
    assertEquals(2, pipelineMeta.getTransforms().size());
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(
                transform ->
                    DvStagingBulkLoadPipelineSupport.CSV_INPUT_TRANSFORM_ID.equals(
                            transform.getPluginId())
                        && transform.getTransform() instanceof CsvInputMeta csvInputMeta
                        && "/tmp/dv2/bulk/dm-fact-f_orders-0.csv".equals(csvInputMeta.getFilename())
                        && csvInputMeta.isHeaderPresent()));
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(
                transform ->
                    DvBulkLoadPluginSupport.PG_BULK_LOADER_ID.equals(transform.getPluginId())
                        && "bulk_load_to_f_orders".equals(transform.getName())));
    assertEquals(1, pipelineMeta.getPipelineHops().size());
  }
}
