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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.Result;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.workflow.IActionListener;
import org.apache.hop.workflow.Workflow;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/** Polls a generated staging-file bulk workflow and publishes live update snapshots. */
public final class UpdateRunLiveStagingWorkflowMonitor implements AutoCloseable {

  private static final Class<?> PKG = UpdateRunLiveStagingWorkflowMonitor.class;

  private final UpdateRunLiveRunContext context;
  private final AtomicReference<IWorkflowEngine<WorkflowMeta>> engineRef;
  private final IActionListener<WorkflowMeta> pipelineTrackingListener;
  private final UpdateRunLiveStallDetector stallDetector;
  private final ScheduledExecutorService executor;
  private final ScheduledFuture<?> pollTask;
  private volatile long lastStallLogAtMillis;

  private UpdateRunLiveStagingWorkflowMonitor(
      UpdateRunLiveRunContext context,
      IWorkflowEngine<WorkflowMeta> engine,
      IActionListener<WorkflowMeta> pipelineTrackingListener,
      UpdateRunLiveStallDetector stallDetector,
      ScheduledExecutorService executor,
      ScheduledFuture<?> pollTask) {
    this.context = context;
    this.engineRef = new AtomicReference<>(engine);
    this.pipelineTrackingListener = pipelineTrackingListener;
    this.stallDetector = stallDetector;
    this.executor = executor;
    this.pollTask = pollTask;
  }

  public static UpdateRunLiveStagingWorkflowMonitor start(
      UpdateRunLiveRunContext context, IWorkflowEngine<WorkflowMeta> engine) {
    if (context == null || engine == null || Utils.isEmpty(context.getMetricsRunId())) {
      return null;
    }
    logStagingFolder(context);
    IActionListener<WorkflowMeta> pipelineTrackingListener =
        UpdateRunLiveStagingPipelineSupport.pipelineTrackingListener();
    engine.addActionListener(pipelineTrackingListener);

    ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "update-run-live-staging-monitor");
              thread.setDaemon(true);
              return thread;
            });
    UpdateRunLiveStallDetector stallDetector = new UpdateRunLiveStallDetector();
    AtomicReference<IWorkflowEngine<WorkflowMeta>> engineRef = new AtomicReference<>(engine);
    UpdateRunLiveStagingWorkflowMonitor[] holder = new UpdateRunLiveStagingWorkflowMonitor[1];
    ScheduledFuture<?> pollTask =
        executor.scheduleAtFixedRate(
            () -> {
              if (holder[0] != null) {
                holder[0].poll(engineRef.get());
              }
            },
            0L,
            UpdateRunLiveMonitor.pollIntervalMs(),
            TimeUnit.MILLISECONDS);
    holder[0] =
        new UpdateRunLiveStagingWorkflowMonitor(
            context, engine, pipelineTrackingListener, stallDetector, executor, pollTask);
    return holder[0];
  }

  private void poll(IWorkflowEngine<WorkflowMeta> engine) {
    if (engine == null) {
      return;
    }
    try {
      UpdateRunLiveSnapshot snapshot = buildSnapshot(engine);
      UpdateRunLiveRegistry.publish(snapshot);
      maybeLogStall(snapshot);
    } catch (Exception ignored) {
      // Monitoring must never interrupt the bulk workflow run.
    }
  }

  UpdateRunLiveSnapshot buildSnapshot(IWorkflowEngine<WorkflowMeta> engine) {
    List<PipelineLiveMetrics> pipelines =
        UpdateRunLiveStagingPipelineSupport.extractActivePipelines(engine);
    String fallbackElementName =
        UpdateRunLiveStagingPipelineSupport.resolveFallbackElementName(engine);
    return UpdateRunLiveSnapshotBuilder.build(
        context,
        stallDetector,
        pipelines,
        engine.isFinished(),
        workflowErrors(engine),
        fallbackElementName);
  }

  private void maybeLogStall(UpdateRunLiveSnapshot snapshot) {
    if (snapshot == null
        || snapshot.getOverallState() != UpdateRunLiveState.STALLED
        || context.getLog() == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastStallLogAtMillis < UpdateRunLiveMonitor.STALL_LOG_INTERVAL_MS) {
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
      context.getLog().logDetailed(UpdateRunLiveMonitor.formatDetailedSnapshot(snapshot));
    }
  }

  private boolean isDetailedLogging() {
    LogLevel level = context.getLogLevel();
    return level != null && level.isDetailed();
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

  private static long workflowErrors(IWorkflowEngine<WorkflowMeta> engine) {
    if (engine == null) {
      return 0L;
    }
    Result result = engine.getResult();
    return result != null ? result.getNrErrors() : 0L;
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
    IWorkflowEngine<WorkflowMeta> engine = engineRef.get();
    if (engine != null) {
      if (engine instanceof Workflow workflow) {
        workflow.removeActionListener(pipelineTrackingListener);
      }
      UpdateRunLiveStagingPipelineSupport.clearWorkflow(engine);
    }
    if (context != null) {
      UpdateRunLiveRegistry.remove(context.getMetricsRunId());
    }
  }
}
