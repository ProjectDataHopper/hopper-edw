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
package org.hopper.edw.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.engine.EngineComponent;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.junit.jupiter.api.Test;

class EngineMetricsSupportTest {

  @Test
  void componentsNamedReturnsEveryCopyNotJustCopyZero() {
    EngineMetrics metrics = new EngineMetrics();
    EngineComponent copy0 = new EngineComponent("stage_to_f_orders", 0);
    EngineComponent copy1 = new EngineComponent("stage_to_f_orders", 1);
    EngineComponent other = new EngineComponent("source_f_orders", 0);
    metrics.addComponent(copy0);
    metrics.addComponent(copy1);
    metrics.addComponent(other);

    List<IEngineComponent> copies =
        EngineMetricsSupport.componentsNamed(metrics, "stage_to_f_orders");

    assertEquals(2, copies.size());
    assertEquals(0, copies.get(0).getCopyNr());
    assertEquals(1, copies.get(1).getCopyNr());
  }

  @Test
  void sumMetricAddsEveryCopy() {
    EngineMetrics metrics = new EngineMetrics();
    EngineComponent copy0 = new EngineComponent("stage_to_f_orders", 0);
    EngineComponent copy1 = new EngineComponent("stage_to_f_orders", 1);
    metrics.addComponent(copy0);
    metrics.addComponent(copy1);
    metrics.setComponentMetric(copy0, Pipeline.METRIC_OUTPUT, 100L);
    metrics.setComponentMetric(copy1, Pipeline.METRIC_OUTPUT, 250L);

    assertEquals(
        350L,
        EngineMetricsSupport.sumMetric(metrics, List.of(copy0, copy1), Pipeline.METRIC_OUTPUT));
  }

  @Test
  void anyRunningIsTrueWhenALaterCopyIsStillRunning() {
    EngineMetrics metrics = new EngineMetrics();
    EngineComponent copy0 = new EngineComponent("stage_to_f_orders", 0);
    EngineComponent copy1 = new EngineComponent("stage_to_f_orders", 1);
    metrics.setComponentRunning(copy0, false);
    metrics.setComponentRunning(copy1, true);

    assertTrue(EngineMetricsSupport.anyRunning(metrics, List.of(copy0, copy1)));
    assertFalse(EngineMetricsSupport.anyRunning(metrics, List.of(copy0)));
  }
}
