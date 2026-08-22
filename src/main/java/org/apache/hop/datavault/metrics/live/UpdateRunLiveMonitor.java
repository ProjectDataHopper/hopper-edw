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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;

/** Polls a running orchestrator engine and publishes live update snapshots. */
public final class UpdateRunLiveMonitor implements AutoCloseable {

  private static final Class<?> PKG = UpdateRunLiveMonitor.class;

  public static final long STALL_LOG_INTERVAL_MS = 60_000L;

  public static long pollIntervalMs() {
    return DataVaultConfigSingleton.getConfig().resolveLiveUpdatePollIntervalMs();
  }

  private final UpdateRunLiveRunContext context;
  private final AtomicReference<IPipelineEngine<PipelineMeta>> engineRef;
  private final UpdateRunLiveStallDetector stallDetector;
  private final ScheduledExecutorService executor;
  private final ScheduledFuture<?> pollTask;
  private volatile long lastStallLogAtMillis;

  private UpdateRunLiveMonitor(
      UpdateRunLiveRunContext context,
      IPipelineEngine<PipelineMeta> engine,
      UpdateRunLiveStallDetector stallDetector,
      ScheduledExecutorService executor,
      ScheduledFuture<?> pollTask) {
    this.context = context;
    this.engineRef = new AtomicReference<>(engine);
    this.stallDetector = stallDetector;
    this.executor = executor;
    this.pollTask = pollTask;
  }

  public static UpdateRunLiveMonitor start(
      UpdateRunLiveRunContext context, IPipelineEngine<PipelineMeta> engine) {
    if (context == null || engine == null || Utils.isEmpty(context.getMetricsRunId())) {
      return null;
    }
    logStagingFolder(context);
    ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "update-run-live-monitor");
              thread.setDaemon(true);
              return thread;
            });
    UpdateRunLiveStallDetector stallDetector = new UpdateRunLiveStallDetector();
    AtomicReference<IPipelineEngine<PipelineMeta>> engineRef = new AtomicReference<>(engine);
    UpdateRunLiveMonitor[] holder = new UpdateRunLiveMonitor[1];
    ScheduledFuture<?> pollTask =
        executor.scheduleAtFixedRate(
            () -> {
              if (holder[0] != null) {
                holder[0].poll(engineRef.get());
              }
            },
            0L,
            pollIntervalMs(),
            TimeUnit.MILLISECONDS);
    holder[0] = new UpdateRunLiveMonitor(context, engine, stallDetector, executor, pollTask);
    return holder[0];
  }

  private void poll(IPipelineEngine<PipelineMeta> engine) {
    if (engine == null) {
      return;
    }
    try {
      UpdateRunLiveSnapshot snapshot = buildSnapshot(engine);
      UpdateRunLiveRegistry.publish(snapshot);
      maybeLogStall(snapshot);
    } catch (Exception ignored) {
      // Monitoring must never interrupt the orchestrator run.
    }
  }

  UpdateRunLiveSnapshot buildSnapshot(IPipelineEngine<PipelineMeta> engine) {
    List<PipelineLiveMetrics> rawPipelines =
        UpdateRunLiveMetricsExtractor.extractActivePipelines(engine);
    return UpdateRunLiveSnapshotBuilder.build(
        context, stallDetector, rawPipelines, engine.isFinished(), engine.getErrors(), null);
  }

  private void maybeLogStall(UpdateRunLiveSnapshot snapshot) {
    if (snapshot == null
        || snapshot.getOverallState() != UpdateRunLiveState.STALLED
        || context.getLog() == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastStallLogAtMillis < STALL_LOG_INTERVAL_MS) {
      return;
    }
    lastStallLogAtMillis = now;
    UpdateRunLiveBottleneck bottleneck = snapshot.getPrimaryBottleneck();
    String pipelineName = bottleneck != null ? bottleneck.getPipelineName() : "";
    String elementName =
        bottleneck != null ? bottleneck.getElementName() : snapshot.getCurrentElementName();
    String transformName = bottleneck != null ? bottleneck.getTransformName() : "";
    String pluginId = bottleneck != null ? bottleneck.getPluginId() : "";
    String message = bottleneck != null ? bottleneck.getMessage() : "";
    context
        .getLog()
        .logBasic(
            BaseMessages.getString(
                PKG,
                "UpdateRunLiveMonitor.Log.StallDetected",
                safe(context.getModelName()),
                safe(elementName),
                safe(pipelineName),
                safe(transformName),
                safe(pluginId),
                safe(message),
                safe(snapshot.getStagingFolder())));
    if (isDetailedLogging()) {
      context.getLog().logDetailed(formatDetailedSnapshot(snapshot));
    }
  }

  private boolean isDetailedLogging() {
    LogLevel level = context.getLogLevel();
    return level != null && level.isDetailed();
  }

  public static String buildTooltipText(
      String elementName, String modelName, UpdateRunLiveState overallState) {
    if (overallState == UpdateRunLiveState.STALLED) {
      if (!Utils.isEmpty(elementName) && !Utils.isEmpty(modelName)) {
        return BaseMessages.getString(
            PKG, "UpdateRunLiveMonitor.Tooltip.StalledTable", elementName, modelName);
      }
      if (!Utils.isEmpty(modelName)) {
        return BaseMessages.getString(PKG, "UpdateRunLiveMonitor.Tooltip.StalledModel", modelName);
      }
      return BaseMessages.getString(PKG, "UpdateRunLiveMonitor.Tooltip.Stalled");
    }
    if (!Utils.isEmpty(elementName) && !Utils.isEmpty(modelName)) {
      return BaseMessages.getString(
          PKG, "UpdateRunLiveMonitor.Tooltip.UpdatingTable", elementName, modelName);
    }
    if (!Utils.isEmpty(modelName)) {
      return BaseMessages.getString(PKG, "UpdateRunLiveMonitor.Tooltip.UpdatingModel", modelName);
    }
    return BaseMessages.getString(PKG, "UpdateRunLiveMonitor.Tooltip.Updating");
  }

  private static void logStagingFolder(UpdateRunLiveRunContext context) {
    if (context.getLog() == null || Utils.isEmpty(context.getStagingFolder())) {
      return;
    }
    context
        .getLog()
        .logBasic(
            BaseMessages.getString(
                PKG,
                "UpdateRunLiveMonitor.Log.StagingFolder",
                safe(context.getModelName()),
                context.getStagingFolder()));
  }

  static String formatDetailedSnapshot(UpdateRunLiveSnapshot snapshot) {
    StringBuilder builder = new StringBuilder();
    builder.append("Live update diagnostics for model ").append(safe(snapshot.getModelName()));
    builder.append(System.lineSeparator());
    for (PipelineLiveMetrics pipeline : snapshot.getPipelines()) {
      builder
          .append("Pipeline ")
          .append(safe(pipeline.getPipelineName()))
          .append(" [")
          .append(pipeline.getState())
          .append("]")
          .append(System.lineSeparator());
      if (pipeline.getTransforms() == null) {
        continue;
      }
      for (TransformLiveMetrics transform : pipeline.getTransforms()) {
        builder
            .append("  ")
            .append(safe(transform.getTransformName()))
            .append(" (")
            .append(safe(transform.getPluginId()))
            .append(") running=")
            .append(transform.isRunning())
            .append(" in=")
            .append(transform.getRowsRead())
            .append(" out=")
            .append(transform.getRowsWritten())
            .append(" bufIn=")
            .append(transform.getBufferIn())
            .append(" bufOut=")
            .append(transform.getBufferOut())
            .append(" stallSec=")
            .append(transform.getSecondsSinceLastProgress())
            .append(System.lineSeparator());
      }
    }
    return builder.toString();
  }

  private static String safe(String value) {
    return value != null ? value : "";
  }

  @Override
  public void close() {
    if (pollTask != null) {
      pollTask.cancel(false);
    }
    if (executor != null) {
      executor.shutdown();
    }
    if (context != null) {
      UpdateRunLiveRegistry.remove(context.getMetricsRunId());
    }
  }
}
