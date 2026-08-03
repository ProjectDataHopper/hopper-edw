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
package org.apache.hop.datavault.metrics.live;

import java.util.Comparator;
import java.util.List;
import org.apache.hop.core.util.Utils;

/** Applies simple heuristics to explain likely bottlenecks in a running update pipeline. */
public final class UpdateRunLiveClassifier {

  private static final long STALL_SECONDS_FOR_CLASSIFICATION = 60L;

  private UpdateRunLiveClassifier() {}

  public static UpdateRunLiveBottleneck classify(PipelineLiveMetrics pipeline) {
    if (pipeline == null || Utils.isEmpty(pipeline.getTransforms())) {
      return null;
    }
    TransformLiveMetrics candidate =
        pipeline.getTransforms().stream()
            .filter(UpdateRunLiveClassifier::isInterestingTransform)
            .max(Comparator.comparingLong(TransformLiveMetrics::getSecondsSinceLastProgress))
            .orElse(null);
    if (candidate == null) {
      return null;
    }
    String message = classifyMessage(candidate);
    return UpdateRunLiveBottleneck.builder()
        .pipelineName(pipeline.getPipelineName())
        .elementName(pipeline.getElementName())
        .transformName(candidate.getTransformName())
        .pluginId(candidate.getPluginId())
        .message(message)
        .build();
  }

  public static UpdateRunLiveBottleneck classifyRun(List<PipelineLiveMetrics> pipelines) {
    if (pipelines == null) {
      return null;
    }
    UpdateRunLiveBottleneck best = null;
    long bestStallSeconds = -1L;
    for (PipelineLiveMetrics pipeline : pipelines) {
      if (pipeline == null || pipeline.getState() != UpdateRunLiveState.STALLED) {
        continue;
      }
      UpdateRunLiveBottleneck bottleneck = classify(pipeline);
      if (bottleneck == null) {
        continue;
      }
      long stallSeconds = maxStallSeconds(pipeline.getTransforms());
      if (stallSeconds > bestStallSeconds) {
        bestStallSeconds = stallSeconds;
        best = bottleneck;
      }
    }
    if (best != null) {
      return best;
    }
    for (PipelineLiveMetrics pipeline : pipelines) {
      if (pipeline == null || pipeline.getState() != UpdateRunLiveState.RUNNING) {
        continue;
      }
      UpdateRunLiveBottleneck bottleneck = classify(pipeline);
      if (bottleneck != null) {
        return bottleneck;
      }
    }
    return null;
  }

  private static boolean isInterestingTransform(TransformLiveMetrics transform) {
    return transform != null
        && transform.isRunning()
        && transform.getSecondsSinceLastProgress() >= STALL_SECONDS_FOR_CLASSIFICATION;
  }

  private static long maxStallSeconds(List<TransformLiveMetrics> transforms) {
    long max = 0L;
    if (transforms == null) {
      return max;
    }
    for (TransformLiveMetrics transform : transforms) {
      if (transform != null) {
        max = Math.max(max, transform.getSecondsSinceLastProgress());
      }
    }
    return max;
  }

  static String classifyMessage(TransformLiveMetrics transform) {
    if (transform == null) {
      return "";
    }
    String pluginId = safe(transform.getPluginId());
    long stallSeconds = transform.getSecondsSinceLastProgress();
    if ("TableInput".equalsIgnoreCase(pluginId)
        && transform.getRowsRead() == 0L
        && transform.getRowsWritten() == 0L
        && transform.getBufferIn() == 0L
        && transform.getBufferOut() == 0L) {
      return "Slow source or target database query (Table Input is still executing).";
    }
    if (transform.getBufferOut() > 0L
        && ("SortRows".equalsIgnoreCase(pluginId) || pluginId.toLowerCase().contains("sort"))) {
      return "Sort Rows is active with buffered output; sort may still be running or spilling to disk.";
    }
    if ("MergeRows".equalsIgnoreCase(pluginId)
        || "MergeRowsPlus".equalsIgnoreCase(pluginId)
        || pluginId.toLowerCase().contains("merge")) {
      if (transform.getBufferIn() > 0L && transform.getRowsWritten() == 0L) {
        return "Merge transform is waiting for a slower input stream.";
      }
    }
    if (transform.getBufferOut() > 0L && transform.getRowsWritten() == 0L) {
      return "Downstream backpressure detected; inspect merge, sort, or target write transforms.";
    }
    return "No measurable progress for "
        + stallSeconds
        + " seconds; inspect database, network, or target write performance.";
  }

  private static String safe(String value) {
    return value != null ? value : "";
  }
}
