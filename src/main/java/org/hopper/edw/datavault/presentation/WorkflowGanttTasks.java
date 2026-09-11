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
package org.hopper.edw.datavault.presentation;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Result;
import org.apache.hop.core.gui.WorkflowTracker;
import org.apache.hop.core.util.Utils;
import org.apache.hop.workflow.ActionResult;
import org.hopper.presentation.component.types.chart.GanttTask;

/**
 * Maps a {@link WorkflowTracker} tree to Gantt bars for <em>top-level</em> actions only. Nested
 * sub-workflow trackers are skipped; the parent workflow action that launched them is already a
 * start/end pair at this level.
 */
public final class WorkflowGanttTasks {

  public static final String COLOR_SUCCESS = "success";
  public static final String COLOR_FAILURE = "failure";
  public static final String COLOR_RUNNING = "running";

  private WorkflowGanttTasks() {}

  public static List<GanttTask> from(WorkflowTracker<?> root, long nowMs) {
    List<GanttTask> tasks = new ArrayList<>();
    if (root == null) {
      return tasks;
    }
    collect(root, StringUtils.defaultIfBlank(root.getWorkflowName(), null), nowMs, tasks);
    return tasks;
  }

  public static String fingerprint(List<GanttTask> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return "0";
    }
    long sum = 0;
    for (GanttTask task : tasks) {
      if (task == null) {
        continue;
      }
      sum += task.getStart() + 31L * task.getEnd();
      if (task.getLabel() != null) {
        sum += task.getLabel().hashCode();
      }
    }
    return tasks.size() + ":" + sum;
  }

  private static void collect(
      WorkflowTracker<?> tracker, String group, long nowMs, List<GanttTask> out) {
    List<WorkflowTracker> children = tracker.getWorkflowTrackers();
    if (children == null || children.isEmpty()) {
      return;
    }
    boolean[] used = new boolean[children.size()];
    for (int i = 0; i < children.size(); i++) {
      WorkflowTracker child = children.get(i);
      if (child == null) {
        continue;
      }
      if (child.nrWorkflowTrackers() > 0) {
        // Sub-workflow execution tracker: the parent action start/end pair is the bar.
        continue;
      }
      ActionResult action = child.getActionResult();
      if (action == null || Utils.isEmpty(action.getActionName()) || action.getResult() != null) {
        continue;
      }
      int endIndex = findEnd(children, i + 1, action.getActionName(), used);
      long start = timeOf(action, nowMs);
      long end;
      String color;
      if (endIndex >= 0) {
        used[endIndex] = true;
        ActionResult endAction = children.get(endIndex).getActionResult();
        end = timeOf(endAction, start);
        Result result = endAction.getResult();
        if (result != null && result.getElapsedTimeMillis() > 0) {
          start = end - result.getElapsedTimeMillis();
        }
        color = result != null && result.isResult() ? COLOR_SUCCESS : COLOR_FAILURE;
      } else {
        end = nowMs;
        color = COLOR_RUNNING;
      }
      if (end < start) {
        end = start;
      }
      out.add(new GanttTask(action.getActionName(), start, end, group, color));
    }
    for (int i = 0; i < children.size(); i++) {
      if (used[i]) {
        continue;
      }
      WorkflowTracker child = children.get(i);
      if (child == null || child.nrWorkflowTrackers() > 0) {
        continue;
      }
      ActionResult action = child.getActionResult();
      if (action == null || Utils.isEmpty(action.getActionName()) || action.getResult() == null) {
        continue;
      }
      long end = timeOf(action, nowMs);
      long elapsed = action.getResult().getElapsedTimeMillis();
      long start = elapsed > 0 ? end - elapsed : end;
      if (end < start) {
        end = start;
      }
      String color = action.getResult().isResult() ? COLOR_SUCCESS : COLOR_FAILURE;
      out.add(new GanttTask(action.getActionName(), start, end, group, color));
    }
  }

  private static int findEnd(
      List<WorkflowTracker> children, int from, String actionName, boolean[] used) {
    for (int i = from; i < children.size(); i++) {
      if (used[i]) {
        continue;
      }
      WorkflowTracker child = children.get(i);
      if (child == null || child.nrWorkflowTrackers() > 0) {
        continue;
      }
      ActionResult next = child.getActionResult();
      if (next != null && actionName.equals(next.getActionName()) && next.getResult() != null) {
        return i;
      }
    }
    return -1;
  }

  private static long timeOf(ActionResult action, long fallback) {
    if (action == null || action.getLogDate() == null) {
      return fallback;
    }
    return action.getLogDate().getTime();
  }
}
