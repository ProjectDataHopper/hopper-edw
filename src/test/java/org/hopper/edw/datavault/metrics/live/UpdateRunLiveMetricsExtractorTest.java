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
package org.hopper.edw.datavault.metrics.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.EngineComponent;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UpdateRunLiveMetricsExtractorTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void sumsLiveMetricsAcrossParallelStagingCopies() {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("dm-fact-f_orders");
    pipelineMeta.addTransform(
        new TransformMeta("TextFileOutput", "stage_to_f_orders", new TextFileOutputMeta()));

    EngineMetrics metrics = new EngineMetrics();
    EngineComponent copy0 = addCopy(metrics, "stage_to_f_orders", 0, 400L, 400L, 10L, false);
    EngineComponent copy1 = addCopy(metrics, "stage_to_f_orders", 1, 500L, 500L, 20L, true);
    metrics.setComponentStatus(copy0, "Finished");
    metrics.setComponentStatus(copy1, "Running");

    List<TransformLiveMetrics> transforms =
        UpdateRunLiveMetricsExtractor.extractTransforms(pipelineMeta, metrics);

    assertEquals(1, transforms.size());
    TransformLiveMetrics write = transforms.get(0);
    assertEquals("stage_to_f_orders", write.getTransformName());
    assertEquals(900L, write.getRowsRead());
    assertEquals(900L, write.getRowsWritten());
    assertEquals(30L, write.getBufferOut());
    assertTrue(write.isRunning());
    assertEquals("Running", write.getStatus());
  }

  private static EngineComponent addCopy(
      EngineMetrics metrics,
      String transformName,
      int copyNr,
      long rowsRead,
      long rowsWritten,
      long bufferOut,
      boolean running) {
    EngineComponent component = new EngineComponent(transformName, copyNr);
    metrics.addComponent(component);
    metrics.setComponentMetric(component, Pipeline.METRIC_INPUT, rowsRead);
    metrics.setComponentMetric(component, Pipeline.METRIC_OUTPUT, rowsWritten);
    metrics.setComponentMetric(component, Pipeline.METRIC_BUFFER_OUT, bufferOut);
    metrics.setComponentRunning(component, running);
    return component;
  }
}
