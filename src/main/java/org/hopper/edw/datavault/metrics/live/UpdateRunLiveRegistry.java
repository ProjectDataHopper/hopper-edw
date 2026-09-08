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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import org.apache.hop.core.util.Utils;

/** Thread-safe registry of in-flight model update snapshots and resource-group waves. */
public final class UpdateRunLiveRegistry {

  private static final ConcurrentMap<String, UpdateRunLiveSnapshot> BY_RUN_ID =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, String> RUN_ID_BY_WORKFLOW_ACTION =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, UpdateRunWaveSnapshot> BY_WAVE_ID =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, String> WAVE_ID_BY_WORKFLOW_ACTION =
      new ConcurrentHashMap<>();

  private UpdateRunLiveRegistry() {}

  public static void publish(UpdateRunLiveSnapshot snapshot) {
    if (snapshot == null || Utils.isEmpty(snapshot.getMetricsRunId())) {
      return;
    }
    BY_RUN_ID.put(snapshot.getMetricsRunId(), snapshot);
    indexWorkflowAction(
        snapshot.getWorkflowFilename(), snapshot.getActionName(), snapshot.getMetricsRunId());
    indexWorkflowAction(
        snapshot.getWorkflowName(), snapshot.getActionName(), snapshot.getMetricsRunId());
    if (!Utils.isEmpty(snapshot.getWaveId())) {
      updateWave(
          snapshot.getWaveId(), wave -> UpdateRunWaveSnapshotSupport.mergeChild(wave, snapshot));
    }
  }

  public static void publishWave(UpdateRunWaveSnapshot snapshot) {
    if (snapshot == null || Utils.isEmpty(snapshot.getWaveId())) {
      return;
    }
    replaceWaveIndex(
        snapshot.getWorkflowFilename(), snapshot.getActionName(), snapshot.getWaveId());
    replaceWaveIndex(snapshot.getWorkflowName(), snapshot.getActionName(), snapshot.getWaveId());
    BY_WAVE_ID.put(snapshot.getWaveId(), snapshot);
  }

  public static void updateWave(String waveId, UnaryOperator<UpdateRunWaveSnapshot> updater) {
    if (Utils.isEmpty(waveId) || updater == null) {
      return;
    }
    BY_WAVE_ID.computeIfPresent(waveId, (id, wave) -> updater.apply(wave));
  }

  private static void indexWorkflowAction(
      String workflowReference, String actionName, String metricsRunId) {
    String workflowActionKey = workflowActionKey(workflowReference, actionName);
    if (!Utils.isEmpty(workflowActionKey)) {
      RUN_ID_BY_WORKFLOW_ACTION.put(workflowActionKey, metricsRunId);
    }
  }

  public static Optional<UpdateRunLiveSnapshot> findByRunId(String metricsRunId) {
    if (Utils.isEmpty(metricsRunId)) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_RUN_ID.get(metricsRunId));
  }

  public static Optional<UpdateRunLiveSnapshot> findByWorkflowAction(
      String workflowFilename, String actionName) {
    String key = workflowActionKey(workflowFilename, actionName);
    if (Utils.isEmpty(key)) {
      return Optional.empty();
    }
    String runId = RUN_ID_BY_WORKFLOW_ACTION.get(key);
    return findByRunId(runId);
  }

  public static Optional<UpdateRunWaveSnapshot> findWaveById(String waveId) {
    if (Utils.isEmpty(waveId)) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_WAVE_ID.get(waveId));
  }

  public static Optional<UpdateRunWaveSnapshot> findWaveByWorkflowAction(
      String workflowFilename, String actionName) {
    String key = workflowActionKey(workflowFilename, actionName);
    if (Utils.isEmpty(key)) {
      return Optional.empty();
    }
    return findWaveById(WAVE_ID_BY_WORKFLOW_ACTION.get(key));
  }

  public static void remove(String metricsRunId) {
    if (Utils.isEmpty(metricsRunId)) {
      return;
    }
    UpdateRunLiveSnapshot removed = BY_RUN_ID.remove(metricsRunId);
    if (removed != null) {
      removeWorkflowActionIndex(removed.getWorkflowFilename(), removed.getActionName());
      removeWorkflowActionIndex(removed.getWorkflowName(), removed.getActionName());
      if (!Utils.isEmpty(removed.getWaveId())) {
        updateWave(
            removed.getWaveId(),
            wave -> UpdateRunWaveSnapshotSupport.clearCurrentLive(wave, metricsRunId));
      }
    }
  }

  public static void removeWave(String waveId) {
    if (Utils.isEmpty(waveId)) {
      return;
    }
    UpdateRunWaveSnapshot removed = BY_WAVE_ID.remove(waveId);
    if (removed != null) {
      removeWaveIndex(removed.getWorkflowFilename(), removed.getActionName());
      removeWaveIndex(removed.getWorkflowName(), removed.getActionName());
    }
  }

  private static void removeWorkflowActionIndex(String workflowReference, String actionName) {
    String workflowActionKey = workflowActionKey(workflowReference, actionName);
    if (!Utils.isEmpty(workflowActionKey)) {
      RUN_ID_BY_WORKFLOW_ACTION.remove(workflowActionKey);
    }
  }

  private static void replaceWaveIndex(String workflowReference, String actionName, String waveId) {
    String workflowActionKey = workflowActionKey(workflowReference, actionName);
    if (Utils.isEmpty(workflowActionKey) || Utils.isEmpty(waveId)) {
      return;
    }
    String previous = WAVE_ID_BY_WORKFLOW_ACTION.put(workflowActionKey, waveId);
    if (!Utils.isEmpty(previous) && !previous.equals(waveId)) {
      BY_WAVE_ID.remove(previous);
    }
  }

  private static void removeWaveIndex(String workflowReference, String actionName) {
    String workflowActionKey = workflowActionKey(workflowReference, actionName);
    if (!Utils.isEmpty(workflowActionKey)) {
      WAVE_ID_BY_WORKFLOW_ACTION.remove(workflowActionKey);
    }
  }

  static String workflowActionKey(String workflowFilename, String actionName) {
    if (Utils.isEmpty(workflowFilename) || Utils.isEmpty(actionName)) {
      return null;
    }
    return workflowFilename + "|" + actionName;
  }
}
