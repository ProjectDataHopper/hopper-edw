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
import java.util.concurrent.TimeUnit;
import org.apache.hop.core.Result;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Pure updates for {@link UpdateRunWaveSnapshot}. */
public final class UpdateRunWaveSnapshotSupport {

  private static final Class<?> PKG = UpdateRunWaveSnapshotSupport.class;

  private UpdateRunWaveSnapshotSupport() {}

  public static UpdateRunWaveSnapshot initial(
      String waveId,
      String resourceGroupName,
      String workflowFilename,
      String workflowName,
      String actionName,
      String workflowExecutionId,
      List<UpdateRunWaveModelProgress> plannedModels,
      Date startedAt) {
    Date now = startedAt != null ? startedAt : new Date();
    List<UpdateRunWaveModelProgress> models =
        plannedModels != null ? List.copyOf(plannedModels) : List.of();
    UpdateRunWaveSnapshot snapshot =
        UpdateRunWaveSnapshot.builder()
            .waveId(waveId)
            .resourceGroupName(resourceGroupName)
            .workflowFilename(workflowFilename)
            .workflowName(workflowName)
            .actionName(actionName)
            .workflowExecutionId(workflowExecutionId)
            .startedAt(now)
            .updatedAt(now)
            .phase(UpdateRunWavePhase.UPDATING)
            .overallState(UpdateRunLiveState.RUNNING)
            .modelsTotal(models.size())
            .modelsCompleted(0)
            .modelsFailed(0)
            .validatingTotal(0)
            .models(models)
            .build();
    return withDerived(snapshot);
  }

  public static UpdateRunWaveSnapshot markValidating(UpdateRunWaveSnapshot wave, int total) {
    if (wave == null) {
      return null;
    }
    return withDerived(
        wave.toBuilder()
            .phase(UpdateRunWavePhase.VALIDATING)
            .validatingTotal(Math.max(0, total))
            .updatedAt(new Date())
            .build());
  }

  public static UpdateRunWaveSnapshot markUpdating(UpdateRunWaveSnapshot wave) {
    if (wave == null) {
      return null;
    }
    return withDerived(
        wave.toBuilder().phase(UpdateRunWavePhase.UPDATING).updatedAt(new Date()).build());
  }

  public static UpdateRunWaveSnapshot withWorkflowExecutionId(
      UpdateRunWaveSnapshot wave, String workflowExecutionId) {
    if (wave == null) {
      return null;
    }
    return wave.toBuilder().workflowExecutionId(workflowExecutionId).updatedAt(new Date()).build();
  }

  public static UpdateRunWaveSnapshot markModelStarted(
      UpdateRunWaveSnapshot wave, String modelFile) {
    if (wave == null || Utils.isEmpty(modelFile)) {
      return wave;
    }
    Date now = new Date();
    List<UpdateRunWaveModelProgress> models = new ArrayList<>(copyModels(wave));
    int index = indexOfModel(models, modelFile, null);
    if (index < 0) {
      return withDerived(wave.toBuilder().updatedAt(now).build());
    }
    UpdateRunWaveModelProgress current = models.get(index);
    models.set(
        index,
        current.toBuilder()
            .state(UpdateRunLiveState.RUNNING)
            .startedAt(now)
            .finishedAt(null)
            .build());
    return withDerived(
        wave.toBuilder()
            .phase(UpdateRunWavePhase.UPDATING)
            .models(List.copyOf(models))
            .updatedAt(now)
            .build());
  }

  public static UpdateRunWaveSnapshot markModelSkipped(
      UpdateRunWaveSnapshot wave, String modelFile, String reason) {
    if (wave == null || Utils.isEmpty(modelFile)) {
      return wave;
    }
    Date now = new Date();
    List<UpdateRunWaveModelProgress> models = new ArrayList<>(copyModels(wave));
    int index = indexOfModel(models, modelFile, null);
    if (index < 0) {
      return withDerived(wave.toBuilder().updatedAt(now).build());
    }
    UpdateRunWaveModelProgress current = models.get(index);
    Date started = current.getStartedAt() != null ? current.getStartedAt() : now;
    models.set(
        index,
        current.toBuilder()
            .state(UpdateRunLiveState.COMPLETED)
            .skipped(true)
            .skipReason(reason)
            .startedAt(started)
            .finishedAt(now)
            .durationMs(Math.max(0L, now.getTime() - started.getTime()))
            .build());
    return withDerived(
        wave.toBuilder()
            .models(List.copyOf(models))
            .currentLiveSnapshot(null)
            .updatedAt(now)
            .build());
  }

  public static UpdateRunWaveSnapshot markModelFinished(
      UpdateRunWaveSnapshot wave, String modelFile, Result result) {
    if (wave == null || Utils.isEmpty(modelFile)) {
      return wave;
    }
    Date now = new Date();
    List<UpdateRunWaveModelProgress> models = new ArrayList<>(copyModels(wave));
    int index = indexOfModel(models, modelFile, null);
    if (index < 0) {
      return withDerived(wave.toBuilder().updatedAt(now).build());
    }
    UpdateRunWaveModelProgress current = models.get(index);
    Date started = current.getStartedAt() != null ? current.getStartedAt() : now;
    boolean failed = result == null || result.getNrErrors() > 0 || !result.getResult();
    long errors = result != null ? result.getNrErrors() : 1L;
    long sourceRows = result != null ? result.getNrLinesRead() + result.getNrLinesInput() : 0L;
    long targetRows = result != null ? result.getNrLinesOutput() + result.getNrLinesWritten() : 0L;
    models.set(
        index,
        current.toBuilder()
            .state(failed ? UpdateRunLiveState.FAILED : UpdateRunLiveState.COMPLETED)
            .finishedAt(now)
            .durationMs(Math.max(0L, now.getTime() - started.getTime()))
            .errors(errors)
            .sourceRowsRead(sourceRows)
            .targetRowsInserted(targetRows)
            .build());
    return withDerived(
        wave.toBuilder()
            .models(List.copyOf(models))
            .currentLiveSnapshot(null)
            .updatedAt(now)
            .build());
  }

  public static UpdateRunWaveSnapshot mergeChild(
      UpdateRunWaveSnapshot wave, UpdateRunLiveSnapshot child) {
    if (wave == null || child == null) {
      return wave;
    }
    Date now = child.getUpdatedAt() != null ? child.getUpdatedAt() : new Date();
    List<UpdateRunWaveModelProgress> models = new ArrayList<>(copyModels(wave));
    int index = indexOfModel(models, child.getModelFilename(), child.getModelName());
    if (index >= 0) {
      UpdateRunWaveModelProgress current = models.get(index);
      UpdateRunLiveState modelState = current.getState();
      if (modelState == null
          || modelState == UpdateRunLiveState.PENDING
          || modelState == UpdateRunLiveState.RUNNING
          || modelState == UpdateRunLiveState.STALLED) {
        if (child.getOverallState() == UpdateRunLiveState.STALLED
            || child.getOverallState() == UpdateRunLiveState.FAILED
            || child.getOverallState() == UpdateRunLiveState.RUNNING) {
          modelState = child.getOverallState();
        }
      }
      String bottleneck =
          child.getPrimaryBottleneck() != null ? child.getPrimaryBottleneck().getMessage() : null;
      models.set(
          index,
          current.toBuilder()
              .modelName(
                  !Utils.isEmpty(child.getModelName())
                      ? child.getModelName()
                      : current.getModelName())
              .state(modelState)
              .currentElementName(
                  !Utils.isEmpty(child.getCurrentElementName())
                      ? child.getCurrentElementName()
                      : current.getCurrentElementName())
              .bottleneckMessage(
                  !Utils.isEmpty(bottleneck) ? bottleneck : current.getBottleneckMessage())
              .build());
    }
    return withDerived(
        wave.toBuilder()
            .models(List.copyOf(models))
            .currentLiveSnapshot(child)
            .updatedAt(now)
            .build());
  }

  public static UpdateRunWaveSnapshot clearCurrentLive(
      UpdateRunWaveSnapshot wave, String metricsRunId) {
    if (wave == null) {
      return null;
    }
    UpdateRunLiveSnapshot current = wave.getCurrentLiveSnapshot();
    if (current == null
        || Utils.isEmpty(metricsRunId)
        || !metricsRunId.equals(current.getMetricsRunId())) {
      return wave;
    }
    return withDerived(wave.toBuilder().currentLiveSnapshot(null).updatedAt(new Date()).build());
  }

  public static UpdateRunWaveSnapshot finish(UpdateRunWaveSnapshot wave) {
    if (wave == null) {
      return null;
    }
    return withDerived(
        wave.toBuilder()
            .phase(UpdateRunWavePhase.FINISHED)
            .currentLiveSnapshot(null)
            .updatedAt(new Date())
            .build());
  }

  static UpdateRunWaveSnapshot withDerived(UpdateRunWaveSnapshot wave) {
    if (wave == null) {
      return null;
    }
    List<UpdateRunWaveModelProgress> models = copyModels(wave);
    int completed = 0;
    int failed = 0;
    for (UpdateRunWaveModelProgress model : models) {
      if (model == null || model.getState() == null) {
        continue;
      }
      if (model.getState() == UpdateRunLiveState.COMPLETED
          || model.getState() == UpdateRunLiveState.FAILED) {
        completed++;
      }
      if (model.getState() == UpdateRunLiveState.FAILED) {
        failed++;
      }
    }
    UpdateRunLiveState overall = rollupState(wave.getPhase(), models);
    return wave.toBuilder()
        .modelsTotal(models.size())
        .modelsCompleted(completed)
        .modelsFailed(failed)
        .overallState(overall)
        .tooltipText(buildTooltip(wave, models, overall, completed))
        .models(models)
        .build();
  }

  static UpdateRunLiveState rollupState(
      UpdateRunWavePhase phase, List<UpdateRunWaveModelProgress> models) {
    if (phase == UpdateRunWavePhase.FINISHED) {
      for (UpdateRunWaveModelProgress model : models) {
        if (model != null && model.getState() == UpdateRunLiveState.FAILED) {
          return UpdateRunLiveState.FAILED;
        }
      }
      return UpdateRunLiveState.COMPLETED;
    }
    boolean stalled = false;
    boolean failed = false;
    boolean running = phase == UpdateRunWavePhase.VALIDATING;
    for (UpdateRunWaveModelProgress model : models) {
      if (model == null || model.getState() == null) {
        continue;
      }
      if (model.getState() == UpdateRunLiveState.STALLED) {
        stalled = true;
      } else if (model.getState() == UpdateRunLiveState.FAILED) {
        failed = true;
      } else if (model.getState() == UpdateRunLiveState.RUNNING) {
        running = true;
      }
    }
    if (stalled) {
      return UpdateRunLiveState.STALLED;
    }
    if (failed) {
      return UpdateRunLiveState.FAILED;
    }
    if (running) {
      return UpdateRunLiveState.RUNNING;
    }
    return UpdateRunLiveState.RUNNING;
  }

  static String buildTooltip(
      UpdateRunWaveSnapshot wave,
      List<UpdateRunWaveModelProgress> models,
      UpdateRunLiveState overall,
      int completed) {
    int total = models.size();
    if (wave.getPhase() == UpdateRunWavePhase.VALIDATING) {
      return BaseMessages.getString(
          PKG,
          "UpdateRunWave.Tooltip.Validating",
          Integer.toString(Math.max(0, wave.getValidatingTotal())));
    }
    UpdateRunWaveModelProgress current = currentModel(models);
    String modelName = current != null ? displayName(current) : "";
    String table = current != null ? ConstNvl(current.getCurrentElementName()) : "";
    String progress = Integer.toString(Math.min(completed + 1, total)) + "/" + total;
    if (wave.getPhase() == UpdateRunWavePhase.FINISHED) {
      return BaseMessages.getString(
          PKG,
          "UpdateRunWave.Tooltip.Finished",
          Integer.toString(completed),
          Integer.toString(total),
          formatDuration(wave));
    }
    if (overall == UpdateRunLiveState.STALLED) {
      if (!Utils.isEmpty(table) && !Utils.isEmpty(modelName)) {
        return BaseMessages.getString(
            PKG, "UpdateRunWave.Tooltip.StalledTable", table, modelName, progress);
      }
      if (!Utils.isEmpty(modelName)) {
        return BaseMessages.getString(
            PKG, "UpdateRunWave.Tooltip.StalledModel", modelName, progress);
      }
      return BaseMessages.getString(PKG, "UpdateRunWave.Tooltip.Stalled", progress);
    }
    if (overall == UpdateRunLiveState.FAILED) {
      if (!Utils.isEmpty(modelName)) {
        return BaseMessages.getString(
            PKG, "UpdateRunWave.Tooltip.FailedModel", modelName, progress);
      }
      return BaseMessages.getString(PKG, "UpdateRunWave.Tooltip.Failed", progress);
    }
    if (!Utils.isEmpty(table) && !Utils.isEmpty(modelName)) {
      return BaseMessages.getString(
          PKG, "UpdateRunWave.Tooltip.UpdatingTable", table, modelName, progress);
    }
    if (!Utils.isEmpty(modelName)) {
      return BaseMessages.getString(
          PKG, "UpdateRunWave.Tooltip.UpdatingModel", modelName, progress);
    }
    return BaseMessages.getString(PKG, "UpdateRunWave.Tooltip.Updating", progress);
  }

  public static UpdateRunWaveModelProgress currentModel(List<UpdateRunWaveModelProgress> models) {
    if (models == null || models.isEmpty()) {
      return null;
    }
    UpdateRunWaveModelProgress failed = null;
    for (UpdateRunWaveModelProgress model : models) {
      if (model == null || model.getState() == null) {
        continue;
      }
      if (model.getState() == UpdateRunLiveState.RUNNING
          || model.getState() == UpdateRunLiveState.STALLED) {
        return model;
      }
      if (failed == null && model.getState() == UpdateRunLiveState.FAILED) {
        failed = model;
      }
    }
    return failed;
  }

  public static String displayName(UpdateRunWaveModelProgress model) {
    if (model == null) {
      return "";
    }
    if (!Utils.isEmpty(model.getModelName())) {
      return model.getModelName();
    }
    return ConstNvl(model.getModelFile());
  }

  public static String formatDuration(UpdateRunWaveSnapshot wave) {
    if (wave == null || wave.getStartedAt() == null) {
      return "0m 0s";
    }
    long endMillis =
        wave.getUpdatedAt() != null ? wave.getUpdatedAt().getTime() : System.currentTimeMillis();
    long seconds = Math.max(0L, (endMillis - wave.getStartedAt().getTime()) / 1000L);
    long minutes = TimeUnit.SECONDS.toMinutes(seconds);
    long remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(minutes);
    return minutes + "m " + remainingSeconds + "s";
  }

  static int indexOfModel(
      List<UpdateRunWaveModelProgress> models, String modelFile, String modelName) {
    if (models == null || models.isEmpty()) {
      return -1;
    }
    if (!Utils.isEmpty(modelFile)) {
      for (int i = 0; i < models.size(); i++) {
        UpdateRunWaveModelProgress model = models.get(i);
        if (model != null && modelFile.equals(model.getModelFile())) {
          return i;
        }
      }
    }
    if (!Utils.isEmpty(modelName)) {
      for (int i = 0; i < models.size(); i++) {
        UpdateRunWaveModelProgress model = models.get(i);
        if (model != null && modelName.equals(model.getModelName())) {
          return i;
        }
      }
    }
    return -1;
  }

  private static List<UpdateRunWaveModelProgress> copyModels(UpdateRunWaveSnapshot wave) {
    if (wave.getModels() == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(wave.getModels());
  }

  private static String ConstNvl(String value) {
    return value != null ? value : "";
  }
}
