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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.apache.hop.core.Result;
import org.apache.hop.core.gui.WorkflowTracker;
import org.apache.hop.workflow.ActionResult;
import org.apache.hop.workflow.WorkflowMeta;
import org.hopper.presentation.component.types.chart.GanttTask;
import org.junit.jupiter.api.Test;

class WorkflowGanttTasksTest {

  @Test
  void emptyTracker() {
    assertTrue(WorkflowGanttTasks.from(null, 0).isEmpty());
    WorkflowMeta meta = new WorkflowMeta();
    meta.setName("wf");
    assertTrue(WorkflowGanttTasks.from(new WorkflowTracker<>(meta), 1).isEmpty());
  }

  @Test
  void pairsStartAndEnd() {
    WorkflowMeta meta = new WorkflowMeta();
    meta.setName("wf");
    WorkflowTracker<WorkflowMeta> root = new WorkflowTracker<>(meta);
    root.addWorkflowTracker(new WorkflowTracker<>(meta, start("Load", 1_000L)));
    root.addWorkflowTracker(new WorkflowTracker<>(meta, end("Load", 4_000L, true, 3_000L)));

    List<GanttTask> tasks = WorkflowGanttTasks.from(root, 10_000L);
    assertEquals(1, tasks.size());
    assertEquals("Load", tasks.get(0).getLabel());
    assertEquals(1_000L, tasks.get(0).getStart());
    assertEquals(4_000L, tasks.get(0).getEnd());
    assertEquals(WorkflowGanttTasks.COLOR_SUCCESS, tasks.get(0).getColorKey());
    assertEquals("wf", tasks.get(0).getGroup());
  }

  @Test
  void runningActionExtendsToNow() {
    WorkflowMeta meta = new WorkflowMeta();
    meta.setName("wf");
    WorkflowTracker<WorkflowMeta> root = new WorkflowTracker<>(meta);
    root.addWorkflowTracker(new WorkflowTracker<>(meta, start("Slow", 5_000L)));
    List<GanttTask> tasks = WorkflowGanttTasks.from(root, 9_000L);
    assertEquals(1, tasks.size());
    assertEquals(5_000L, tasks.get(0).getStart());
    assertEquals(9_000L, tasks.get(0).getEnd());
    assertEquals(WorkflowGanttTasks.COLOR_RUNNING, tasks.get(0).getColorKey());
  }

  @Test
  void nestedWorkflowActionsAreNotFlattened() {
    WorkflowMeta parentMeta = new WorkflowMeta();
    parentMeta.setName("parent");
    WorkflowMeta childMeta = new WorkflowMeta();
    childMeta.setName("setup");
    WorkflowTracker<WorkflowMeta> root = new WorkflowTracker<>(parentMeta);
    root.addWorkflowTracker(new WorkflowTracker<>(parentMeta, start("ping db", 1_000L)));
    root.addWorkflowTracker(
        new WorkflowTracker<>(parentMeta, end("ping db", 2_000L, true, 1_000L)));
    root.addWorkflowTracker(
        new WorkflowTracker<>(parentMeta, start("run-retail-initial-setup-db", 2_000L)));
    WorkflowTracker<WorkflowMeta> nested = new WorkflowTracker<>(childMeta);
    nested.addWorkflowTracker(
        new WorkflowTracker<>(childMeta, start("Drop source tables", 2_100L)));
    nested.addWorkflowTracker(
        new WorkflowTracker<>(childMeta, end("Drop source tables", 2_200L, true, 100L)));
    nested.addWorkflowTracker(new WorkflowTracker<>(childMeta, start("Inner Start", 2_200L)));
    nested.addWorkflowTracker(
        new WorkflowTracker<>(childMeta, end("Inner Start", 2_250L, true, 50L)));
    root.addWorkflowTracker(nested);
    root.addWorkflowTracker(
        new WorkflowTracker<>(
            parentMeta, end("run-retail-initial-setup-db", 15_000L, true, 13_000L)));

    List<GanttTask> tasks = WorkflowGanttTasks.from(root, 20_000L);
    assertEquals(2, tasks.size());
    assertEquals("ping db", tasks.get(0).getLabel());
    assertEquals("run-retail-initial-setup-db", tasks.get(1).getLabel());
    assertEquals(2_000L, tasks.get(1).getStart());
    assertEquals(15_000L, tasks.get(1).getEnd());
    assertTrue(tasks.stream().noneMatch(t -> "Drop source tables".equals(t.getLabel())));
    assertTrue(tasks.stream().noneMatch(t -> "Inner Start".equals(t.getLabel())));
  }

  private static ActionResult start(String name, long when) {
    ActionResult result = new ActionResult();
    result.setActionName(name);
    result.setLogDate(new Date(when));
    return result;
  }

  private static ActionResult end(String name, long when, boolean success, long elapsed) {
    ActionResult result = new ActionResult();
    result.setActionName(name);
    result.setLogDate(new Date(when));
    Result exec = new Result();
    exec.setResult(success);
    exec.setElapsedTimeMillis(elapsed);
    result.setResult(exec);
    return result;
  }
}
