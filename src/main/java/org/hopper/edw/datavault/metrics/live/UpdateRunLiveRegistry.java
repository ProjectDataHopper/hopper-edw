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

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;

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
    for (String ref : generateWorkflowReferences(snapshot.getWorkflowFilename(), null)) {
      indexWorkflowAction(ref, snapshot.getActionName(), snapshot.getMetricsRunId());
    }
    for (String ref : generateWorkflowReferences(snapshot.getWorkflowName(), null)) {
      indexWorkflowAction(ref, snapshot.getActionName(), snapshot.getMetricsRunId());
    }
    if (!Utils.isEmpty(snapshot.getWaveId())) {
      updateWave(
          snapshot.getWaveId(), wave -> UpdateRunWaveSnapshotSupport.mergeChild(wave, snapshot));
    }
  }

  public static void publishWave(UpdateRunWaveSnapshot snapshot) {
    if (snapshot == null || Utils.isEmpty(snapshot.getWaveId())) {
      return;
    }
    for (String ref : generateWorkflowReferences(snapshot.getWorkflowFilename(), null)) {
      replaceWaveIndex(ref, snapshot.getActionName(), snapshot.getWaveId());
    }
    for (String ref : generateWorkflowReferences(snapshot.getWorkflowName(), null)) {
      replaceWaveIndex(ref, snapshot.getActionName(), snapshot.getWaveId());
    }
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
    if (Utils.isEmpty(actionName)) {
      return Optional.empty();
    }
    String key = workflowActionKey(workflowFilename, actionName);
    if (!Utils.isEmpty(key)) {
      String runId = RUN_ID_BY_WORKFLOW_ACTION.get(key);
      if (!Utils.isEmpty(runId)) {
        Optional<UpdateRunLiveSnapshot> snapshot = findByRunId(runId);
        if (snapshot.isPresent()) {
          return snapshot;
        }
      }
    }
    return findSnapshot(workflowFilename, null, actionName, null);
  }

  public static Optional<UpdateRunLiveSnapshot> findSnapshot(
      String workflowFilename, String workflowName, String actionName, IVariables variables) {
    if (Utils.isEmpty(actionName)) {
      return Optional.empty();
    }
    for (String ref : generateWorkflowReferences(workflowFilename, variables)) {
      String key = workflowActionKey(ref, actionName);
      if (key != null) {
        String runId = RUN_ID_BY_WORKFLOW_ACTION.get(key);
        if (!Utils.isEmpty(runId)) {
          Optional<UpdateRunLiveSnapshot> snapshot = findByRunId(runId);
          if (snapshot.isPresent()) {
            return snapshot;
          }
        }
      }
    }
    if (!Utils.isEmpty(workflowName)) {
      for (String ref : generateWorkflowReferences(workflowName, variables)) {
        String key = workflowActionKey(ref, actionName);
        if (key != null) {
          String runId = RUN_ID_BY_WORKFLOW_ACTION.get(key);
          if (!Utils.isEmpty(runId)) {
            Optional<UpdateRunLiveSnapshot> snapshot = findByRunId(runId);
            if (snapshot.isPresent()) {
              return snapshot;
            }
          }
        }
      }
    }
    return findActiveSnapshotByAction(actionName);
  }

  public static Optional<UpdateRunLiveSnapshot> findActiveSnapshotByAction(String actionName) {
    if (Utils.isEmpty(actionName)) {
      return Optional.empty();
    }
    return BY_RUN_ID.values().stream()
        .filter(
            s ->
                s != null
                    && (s.getOverallState() == null
                        || s.getOverallState() == UpdateRunLiveState.RUNNING
                        || s.getOverallState() == UpdateRunLiveState.STALLED))
        .filter(s -> actionName.equalsIgnoreCase(s.getActionName()))
        .max(
            Comparator.comparing(
                UpdateRunLiveSnapshot::getStartedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
  }

  public static Optional<UpdateRunWaveSnapshot> findWaveById(String waveId) {
    if (Utils.isEmpty(waveId)) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_WAVE_ID.get(waveId));
  }

  public static Optional<UpdateRunWaveSnapshot> findWaveByWorkflowAction(
      String workflowFilename, String actionName) {
    if (Utils.isEmpty(actionName)) {
      return Optional.empty();
    }
    String key = workflowActionKey(workflowFilename, actionName);
    if (!Utils.isEmpty(key)) {
      String waveId = WAVE_ID_BY_WORKFLOW_ACTION.get(key);
      if (!Utils.isEmpty(waveId)) {
        Optional<UpdateRunWaveSnapshot> wave = findWaveById(waveId);
        if (wave.isPresent()) {
          return wave;
        }
      }
    }
    return findWave(workflowFilename, null, actionName, null);
  }

  public static Optional<UpdateRunWaveSnapshot> findWave(
      String workflowFilename, String workflowName, String actionName, IVariables variables) {
    if (Utils.isEmpty(actionName)) {
      return Optional.empty();
    }

    // 1. Candidate references from workflowFilename
    for (String ref : generateWorkflowReferences(workflowFilename, variables)) {
      String key = workflowActionKey(ref, actionName);
      if (key != null) {
        String waveId = WAVE_ID_BY_WORKFLOW_ACTION.get(key);
        if (!Utils.isEmpty(waveId)) {
          Optional<UpdateRunWaveSnapshot> wave = findWaveById(waveId);
          if (wave.isPresent()) {
            return wave;
          }
        }
      }
    }

    // 2. Candidate references from workflowName
    if (!Utils.isEmpty(workflowName)) {
      for (String ref : generateWorkflowReferences(workflowName, variables)) {
        String key = workflowActionKey(ref, actionName);
        if (key != null) {
          String waveId = WAVE_ID_BY_WORKFLOW_ACTION.get(key);
          if (!Utils.isEmpty(waveId)) {
            Optional<UpdateRunWaveSnapshot> wave = findWaveById(waveId);
            if (wave.isPresent()) {
              return wave;
            }
          }
        }
      }
    }

    // 3. Fallback: in-flight wave matching actionName
    Optional<UpdateRunWaveSnapshot> activeWave = findActiveWaveByAction(actionName);
    if (activeWave.isPresent()) {
      return activeWave;
    }

    // 4. Fallback: most recent wave matching actionName
    return findLatestWaveByAction(actionName);
  }

  public static Optional<UpdateRunWaveSnapshot> findActiveWaveByAction(String actionName) {
    if (Utils.isEmpty(actionName)) {
      return Optional.empty();
    }
    return BY_WAVE_ID.values().stream()
        .filter(w -> w != null && w.getPhase() != UpdateRunWavePhase.FINISHED)
        .filter(w -> actionName.equalsIgnoreCase(w.getActionName()))
        .max(
            Comparator.comparing(
                UpdateRunWaveSnapshot::getStartedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
  }

  public static Optional<UpdateRunWaveSnapshot> findLatestWaveByAction(String actionName) {
    if (Utils.isEmpty(actionName)) {
      return Optional.empty();
    }
    return BY_WAVE_ID.values().stream()
        .filter(w -> w != null && actionName.equalsIgnoreCase(w.getActionName()))
        .max(
            Comparator.comparing(
                UpdateRunWaveSnapshot::getStartedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
  }

  public static void remove(String metricsRunId) {
    if (Utils.isEmpty(metricsRunId)) {
      return;
    }
    UpdateRunLiveSnapshot removed = BY_RUN_ID.remove(metricsRunId);
    if (removed != null) {
      for (String ref : generateWorkflowReferences(removed.getWorkflowFilename(), null)) {
        removeWorkflowActionIndex(ref, removed.getActionName());
      }
      for (String ref : generateWorkflowReferences(removed.getWorkflowName(), null)) {
        removeWorkflowActionIndex(ref, removed.getActionName());
      }
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
      for (String ref : generateWorkflowReferences(removed.getWorkflowFilename(), null)) {
        removeWaveIndex(ref, removed.getActionName());
      }
      for (String ref : generateWorkflowReferences(removed.getWorkflowName(), null)) {
        removeWaveIndex(ref, removed.getActionName());
      }
    }
  }

  public static void clear() {
    BY_RUN_ID.clear();
    RUN_ID_BY_WORKFLOW_ACTION.clear();
    BY_WAVE_ID.clear();
    WAVE_ID_BY_WORKFLOW_ACTION.clear();
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
    WAVE_ID_BY_WORKFLOW_ACTION.put(workflowActionKey, waveId);
  }

  private static void removeWaveIndex(String workflowReference, String actionName) {
    String workflowActionKey = workflowActionKey(workflowReference, actionName);
    if (!Utils.isEmpty(workflowActionKey)) {
      WAVE_ID_BY_WORKFLOW_ACTION.remove(workflowActionKey);
    }
  }

  static Set<String> generateWorkflowReferences(String reference, IVariables variables) {
    Set<String> references = new LinkedHashSet<>();
    if (Utils.isEmpty(reference)) {
      return references;
    }
    references.add(reference);

    String resolved = reference;
    if (variables != null) {
      try {
        resolved = variables.resolve(reference);
        if (!Utils.isEmpty(resolved)) {
          references.add(resolved);
        }
      } catch (Exception ignored) {
      }
    }

    addPathVariants(references, reference);
    if (!reference.equals(resolved)) {
      addPathVariants(references, resolved);
    }

    return references;
  }

  private static void addPathVariants(Set<String> references, String path) {
    if (Utils.isEmpty(path)) {
      return;
    }

    if (path.startsWith("file://")) {
      references.add(path.substring(7));
    } else if (path.startsWith("file:/")) {
      references.add(path.substring(5));
    } else if (path.startsWith("/")) {
      references.add("file://" + path);
      references.add("file:///" + path.substring(1));
    }

    try {
      String normalized = HopVfs.normalize(path);
      if (!Utils.isEmpty(normalized)) {
        references.add(normalized);
        if (normalized.startsWith("file://")) {
          references.add(normalized.substring(7));
        }
      }
    } catch (Exception ignored) {
    }

    int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    if (lastSlash >= 0 && lastSlash < path.length() - 1) {
      String baseName = path.substring(lastSlash + 1);
      references.add(baseName);
      if (baseName.toLowerCase().endsWith(".hwf")) {
        references.add(baseName.substring(0, baseName.length() - 4));
      }
    } else if (path.toLowerCase().endsWith(".hwf")) {
      references.add(path.substring(0, path.length() - 4));
    }
  }

  static String workflowActionKey(String workflowFilename, String actionName) {
    if (Utils.isEmpty(workflowFilename) || Utils.isEmpty(actionName)) {
      return null;
    }
    return workflowFilename + "|" + actionName;
  }
}
