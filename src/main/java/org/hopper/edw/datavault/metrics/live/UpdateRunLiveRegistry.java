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
import org.apache.hop.core.util.Utils;

/** Thread-safe registry of in-flight model update snapshots. */
public final class UpdateRunLiveRegistry {

  private static final ConcurrentMap<String, UpdateRunLiveSnapshot> BY_RUN_ID =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, String> RUN_ID_BY_WORKFLOW_ACTION =
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

  public static void remove(String metricsRunId) {
    if (Utils.isEmpty(metricsRunId)) {
      return;
    }
    UpdateRunLiveSnapshot removed = BY_RUN_ID.remove(metricsRunId);
    if (removed != null) {
      removeWorkflowActionIndex(removed.getWorkflowFilename(), removed.getActionName());
      removeWorkflowActionIndex(removed.getWorkflowName(), removed.getActionName());
    }
  }

  private static void removeWorkflowActionIndex(String workflowReference, String actionName) {
    String workflowActionKey = workflowActionKey(workflowReference, actionName);
    if (!Utils.isEmpty(workflowActionKey)) {
      RUN_ID_BY_WORKFLOW_ACTION.remove(workflowActionKey);
    }
  }

  static String workflowActionKey(String workflowFilename, String actionName) {
    if (Utils.isEmpty(workflowFilename) || Utils.isEmpty(actionName)) {
      return null;
    }
    return workflowFilename + "|" + actionName;
  }
}
