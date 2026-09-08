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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.hop.core.Result;
import org.apache.hop.core.util.Utils;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/**
 * Publishes a resource-group update wave to {@link UpdateRunLiveRegistry} for the workflow canvas.
 */
public final class UpdateRunWaveMonitor implements AutoCloseable {

  public record PlannedModel(String layer, String modelFile) {}

  private final String waveId;

  private UpdateRunWaveMonitor(String waveId) {
    this.waveId = waveId;
  }

  public String getWaveId() {
    return waveId;
  }

  public static UpdateRunWaveMonitor start(
      ActionBase action, String resourceGroupName, List<PlannedModel> plannedModels) {
    if (action == null) {
      return null;
    }
    String waveId = UUID.randomUUID().toString();
    List<UpdateRunWaveModelProgress> models = toProgress(plannedModels);
    UpdateRunWaveSnapshot snapshot =
        UpdateRunWaveSnapshotSupport.initial(
            waveId,
            resourceGroupName,
            resolveWorkflowFilename(action),
            resolveWorkflowName(action),
            action.getName(),
            null,
            models,
            new Date());
    UpdateRunLiveRegistry.publishWave(snapshot);
    return new UpdateRunWaveMonitor(waveId);
  }

  public void markValidating(int total) {
    UpdateRunLiveRegistry.updateWave(
        waveId, wave -> UpdateRunWaveSnapshotSupport.markValidating(wave, total));
  }

  public void markUpdating() {
    UpdateRunLiveRegistry.updateWave(waveId, UpdateRunWaveSnapshotSupport::markUpdating);
  }

  public void withWorkflowExecutionId(String workflowExecutionId) {
    if (Utils.isEmpty(workflowExecutionId)) {
      return;
    }
    UpdateRunLiveRegistry.updateWave(
        waveId,
        wave -> UpdateRunWaveSnapshotSupport.withWorkflowExecutionId(wave, workflowExecutionId));
  }

  public void markModelStarted(String modelFile) {
    UpdateRunLiveRegistry.updateWave(
        waveId, wave -> UpdateRunWaveSnapshotSupport.markModelStarted(wave, modelFile));
  }

  public void markModelSkipped(String modelFile, String reason) {
    UpdateRunLiveRegistry.updateWave(
        waveId, wave -> UpdateRunWaveSnapshotSupport.markModelSkipped(wave, modelFile, reason));
  }

  public void markModelFinished(String modelFile, Result result) {
    UpdateRunLiveRegistry.updateWave(
        waveId, wave -> UpdateRunWaveSnapshotSupport.markModelFinished(wave, modelFile, result));
  }

  public UpdateRunLiveAttachment attachment() {
    UpdateRunWaveSnapshot snapshot = UpdateRunLiveRegistry.findWaveById(waveId).orElse(null);
    String actionName = snapshot != null ? snapshot.getActionName() : null;
    return new UpdateRunLiveAttachment(waveId, actionName);
  }

  @Override
  public void close() {
    UpdateRunLiveRegistry.updateWave(waveId, UpdateRunWaveSnapshotSupport::finish);
  }

  static List<UpdateRunWaveModelProgress> toProgress(List<PlannedModel> plannedModels) {
    List<UpdateRunWaveModelProgress> models = new ArrayList<>();
    if (plannedModels == null) {
      return models;
    }
    for (PlannedModel planned : plannedModels) {
      if (planned == null || Utils.isEmpty(planned.modelFile())) {
        continue;
      }
      models.add(
          UpdateRunWaveModelProgress.builder()
              .layer(planned.layer())
              .modelFile(planned.modelFile())
              .state(UpdateRunLiveState.PENDING)
              .build());
    }
    return models;
  }

  static String resolveWorkflowFilename(ActionBase action) {
    IWorkflowEngine<WorkflowMeta> parentWorkflow = action.getParentWorkflow();
    if (parentWorkflow != null && parentWorkflow.getWorkflowMeta() != null) {
      WorkflowMeta workflowMeta = parentWorkflow.getWorkflowMeta();
      if (!Utils.isEmpty(workflowMeta.getFilename())) {
        return workflowMeta.getFilename();
      }
      return workflowMeta.getName();
    }
    if (action.getParentWorkflowMeta() != null
        && !Utils.isEmpty(action.getParentWorkflowMeta().getFilename())) {
      return action.getParentWorkflowMeta().getFilename();
    }
    if (action.getParentWorkflowMeta() != null) {
      return action.getParentWorkflowMeta().getName();
    }
    return action.getName();
  }

  static String resolveWorkflowName(ActionBase action) {
    IWorkflowEngine<WorkflowMeta> parentWorkflow = action.getParentWorkflow();
    if (parentWorkflow != null && parentWorkflow.getWorkflowMeta() != null) {
      return parentWorkflow.getWorkflowMeta().getName();
    }
    if (action.getParentWorkflowMeta() != null) {
      return action.getParentWorkflowMeta().getName();
    }
    return action.getName();
  }
}
