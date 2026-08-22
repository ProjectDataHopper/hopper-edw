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
package org.apache.hop.datavault.metrics.live;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tracks per-transform progress samples and flags stalls after a quiet period. */
public final class UpdateRunLiveStallDetector {

  public static final long DEFAULT_STALL_THRESHOLD_MS = 60_000L;

  private final long stallThresholdMs;
  private final Map<UpdateRunLiveProgressKey, UpdateRunLiveProgressSample> lastSamples =
      new HashMap<>();

  public UpdateRunLiveStallDetector() {
    this(DEFAULT_STALL_THRESHOLD_MS);
  }

  UpdateRunLiveStallDetector(long stallThresholdMs) {
    this.stallThresholdMs = Math.max(1L, stallThresholdMs);
  }

  public List<TransformLiveMetrics> annotateTransforms(
      String pipelineName, List<TransformLiveMetrics> transforms, long nowMillis) {
    if (transforms == null || transforms.isEmpty()) {
      return List.of();
    }
    return transforms.stream()
        .map(transform -> annotateTransform(pipelineName, transform, nowMillis))
        .toList();
  }

  private TransformLiveMetrics annotateTransform(
      String pipelineName, TransformLiveMetrics transform, long nowMillis) {
    if (transform == null) {
      return null;
    }
    UpdateRunLiveProgressKey key =
        new UpdateRunLiveProgressKey(pipelineName, transform.getTransformName());
    UpdateRunLiveProgressSample current =
        new UpdateRunLiveProgressSample(
            transform.getRowsRead(),
            transform.getRowsWritten(),
            transform.getBufferIn(),
            transform.getBufferOut(),
            transform.isRunning(),
            nowMillis);
    UpdateRunLiveProgressSample previous = lastSamples.get(key);
    long secondsSinceProgress = 0L;
    if (transform.isRunning()) {
      if (previous == null || !previous.hasSameCounters(current)) {
        lastSamples.put(key, current);
      } else {
        secondsSinceProgress = Math.max(0L, (nowMillis - previous.observedAtMillis()) / 1000L);
      }
    } else {
      lastSamples.remove(key);
    }
    return TransformLiveMetrics.builder()
        .transformName(transform.getTransformName())
        .pluginId(transform.getPluginId())
        .logicalRole(transform.getLogicalRole())
        .rowsRead(transform.getRowsRead())
        .rowsWritten(transform.getRowsWritten())
        .bufferIn(transform.getBufferIn())
        .bufferOut(transform.getBufferOut())
        .running(transform.isRunning())
        .status(transform.getStatus())
        .secondsSinceLastProgress(secondsSinceProgress)
        .build();
  }

  public boolean isStalled(TransformLiveMetrics transform) {
    if (transform == null || !transform.isRunning()) {
      return false;
    }
    return transform.getSecondsSinceLastProgress() * 1000L >= stallThresholdMs;
  }

  /**
   * A pipeline is stalled only when every running transform has been quiet for the threshold
   * period. Branching pipelines often leave side paths idle while the main path (for example a
   * staging Text File Output) continues to make progress.
   */
  public boolean isPipelineStalled(List<TransformLiveMetrics> transforms) {
    if (transforms == null || transforms.isEmpty()) {
      return false;
    }
    boolean hasRunningTransform = false;
    for (TransformLiveMetrics transform : transforms) {
      if (transform == null || !transform.isRunning()) {
        continue;
      }
      hasRunningTransform = true;
      if (!isStalled(transform)) {
        return false;
      }
    }
    return hasRunningTransform;
  }
}
