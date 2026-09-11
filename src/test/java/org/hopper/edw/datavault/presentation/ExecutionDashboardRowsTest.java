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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.execution.Execution;
import org.apache.hop.execution.ExecutionState;
import org.apache.hop.execution.ExecutionType;
import org.junit.jupiter.api.Test;

class ExecutionDashboardRowsTest {

  @Test
  void mapsFinishedSuccess() throws Exception {
    Execution execution = new Execution();
    execution.setName("load-orders");
    execution.setFilename("pipelines/load-orders.hpl");
    execution.setExecutionType(ExecutionType.Pipeline);
    Date start = new Date(1_700_000_000_000L);
    execution.setExecutionStartDate(start);
    ExecutionState state = new ExecutionState();
    state.setFailed(false);
    state.setExecutionEndDate(new Date(start.getTime() + 1500));
    RowMetaAndData row = ExecutionDashboardRows.toRow(execution, state);
    assertEquals("load-orders", row.getString(ExecutionDashboardRows.COL_NAME, ""));
    assertEquals("Pipeline", row.getString(ExecutionDashboardRows.COL_TYPE, ""));
    assertEquals("SUCCESS", row.getString(ExecutionDashboardRows.COL_STATUS, ""));
    assertEquals(1500L, row.getInteger(ExecutionDashboardRows.COL_DURATION_MS, 0L));
    assertEquals(1L, row.getInteger(ExecutionDashboardRows.COL_COUNT, 0L));
  }

  @Test
  void mapsFailed() throws Exception {
    Execution execution = new Execution();
    execution.setName("bad");
    execution.setExecutionType(ExecutionType.Workflow);
    ExecutionState state = new ExecutionState();
    state.setFailed(true);
    assertEquals("FAILED", ExecutionDashboardRows.statusOf(state));
    assertTrue(ExecutionDashboardRows.matchesFilter(execution, "bad"));
    assertFalse(ExecutionDashboardRows.matchesFilter(execution, "other.hpl"));
  }
}
