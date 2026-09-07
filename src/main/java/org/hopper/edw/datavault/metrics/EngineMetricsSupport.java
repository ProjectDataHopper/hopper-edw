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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IEngineMetric;

/**
 * Looks up engine metrics for every parallel copy of a transform. Hop reports each copy as a
 * separate {@link IEngineComponent} with the same name; matching only the first component
 * undercounts rows when {@code targetTableParallelCopies} is greater than 1 (staging-file bulk
 * loads).
 */
public final class EngineMetricsSupport {

  private EngineMetricsSupport() {}

  public static List<IEngineComponent> componentsNamed(
      EngineMetrics metrics, String transformName) {
    if (metrics == null || Utils.isEmpty(transformName) || metrics.getComponents() == null) {
      return List.of();
    }
    List<IEngineComponent> matches = new ArrayList<>();
    for (IEngineComponent component : metrics.getComponents()) {
      if (component != null && transformName.equals(component.getName())) {
        matches.add(component);
      }
    }
    return matches;
  }

  public static long sumMetric(
      EngineMetrics metrics, List<IEngineComponent> components, IEngineMetric metric) {
    if (metrics == null || components == null || metric == null) {
      return 0L;
    }
    long total = 0L;
    for (IEngineComponent component : components) {
      if (component == null) {
        continue;
      }
      Long value = metrics.getComponentMetric(component, metric);
      if (value != null) {
        total += value;
      }
    }
    return total;
  }

  public static long maxExecutionDurationMs(List<IEngineComponent> components) {
    if (components == null) {
      return 0L;
    }
    long max = 0L;
    for (IEngineComponent component : components) {
      if (component != null) {
        max = Math.max(max, Math.max(0L, component.getExecutionDuration()));
      }
    }
    return max;
  }

  public static boolean anyRunning(EngineMetrics metrics, List<IEngineComponent> components) {
    if (metrics == null || components == null || metrics.getComponentRunningMap() == null) {
      return false;
    }
    for (IEngineComponent component : components) {
      Boolean running = metrics.getComponentRunningMap().get(component);
      if (running != null && running) {
        return true;
      }
    }
    return false;
  }

  public static String statusText(EngineMetrics metrics, List<IEngineComponent> components) {
    if (metrics == null || components == null || metrics.getComponentStatusMap() == null) {
      return "";
    }
    String fallback = "";
    for (IEngineComponent component : components) {
      String status = metrics.getComponentStatusMap().get(component);
      if (Utils.isEmpty(status)) {
        continue;
      }
      Boolean running =
          metrics.getComponentRunningMap() != null
              ? metrics.getComponentRunningMap().get(component)
              : null;
      if (running != null && running) {
        return status;
      }
      if (fallback.isEmpty()) {
        fallback = status;
      }
    }
    return fallback;
  }
}
