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
package org.hopper.edw.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewHistoryLoader.HistoryList;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewHistoryLoader.OverviewHistoryRow;
import org.junit.jupiter.api.Test;

class WorkflowLoadOverviewHistoryLoaderTest {

  @Test
  void assembleListPrefersMatchingWorkflowName() {
    OverviewHistoryRow other = row("other-wf", "exec-1");
    OverviewHistoryRow match = row("update-retail", "exec-2");
    HistoryList list =
        WorkflowLoadOverviewHistoryLoader.assembleList(List.of(other, match), "update-retail", 20);
    assertFalse(list.workflowFilterMissed());
    assertEquals(1, list.rows().size());
    assertEquals("exec-2", list.rows().get(0).workflowExecutionId());
  }

  @Test
  void assembleListFallsBackWhenNoWorkflowMatch() {
    OverviewHistoryRow first = row("a", "exec-1");
    OverviewHistoryRow second = row("b", "exec-2");
    HistoryList list =
        WorkflowLoadOverviewHistoryLoader.assembleList(List.of(first, second), "missing", 20);
    assertTrue(list.workflowFilterMissed());
    assertEquals(2, list.rows().size());
  }

  @Test
  void assembleListEmpty() {
    HistoryList list = WorkflowLoadOverviewHistoryLoader.assembleList(List.of(), "wf", 20);
    assertEquals("no-rows", list.emptyReason());
    assertTrue(list.rows().isEmpty());
  }

  private static OverviewHistoryRow row(String workflowName, String executionId) {
    return new OverviewHistoryRow(
        "ov-" + executionId,
        executionId,
        workflowName,
        workflowName,
        new Date(),
        1000L,
        2L,
        4L,
        10L,
        8L,
        0L,
        true);
  }
}
