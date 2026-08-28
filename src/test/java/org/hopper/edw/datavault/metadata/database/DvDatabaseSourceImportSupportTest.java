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
package org.hopper.edw.datavault.metadata.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.IProgressMonitor;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSourceImportSupport.TableImportRequest;
import org.junit.jupiter.api.Test;

class DvDatabaseSourceImportSupportTest {

  @Test
  void shouldPreselectAllTablesForSmallSchemas() {
    assertTrue(DvDatabaseSourceImportSupport.shouldPreselectAllTables(1));
    assertTrue(
        DvDatabaseSourceImportSupport.shouldPreselectAllTables(
            DvDatabaseSourceImportSupport.LARGE_SCHEMA_TABLE_THRESHOLD));
    assertFalse(
        DvDatabaseSourceImportSupport.shouldPreselectAllTables(
            DvDatabaseSourceImportSupport.LARGE_SCHEMA_TABLE_THRESHOLD + 1));
    assertFalse(DvDatabaseSourceImportSupport.shouldPreselectAllTables(0));
  }

  @Test
  void defaultPreselectedTableIndexesSelectsAllForSmallSchemas() {
    List<Integer> indexes = DvDatabaseSourceImportSupport.defaultPreselectedTableIndexes(3);
    assertEquals(List.of(0, 1, 2), indexes);
  }

  @Test
  void defaultPreselectedTableIndexesIsEmptyForLargeSchemas() {
    assertTrue(
        DvDatabaseSourceImportSupport.defaultPreselectedTableIndexes(
                DvDatabaseSourceImportSupport.LARGE_SCHEMA_TABLE_THRESHOLD + 5)
            .isEmpty());
  }

  @Test
  void sortedStrippedTableNamesSortsCaseInsensitiveAndStripsQuotes() {
    String[] sorted =
        DvDatabaseSourceImportSupport.sortedStrippedTableNames(
            new String[] {"\"Zebra\"", "alpha", "`Beta`"});

    assertArrayEquals(new String[] {"alpha", "Beta", "Zebra"}, sorted);
  }

  @Test
  void tableNamesForSelectionIndexesPreservesDialogOrder() {
    String[] choices = {"alpha", "beta", "gamma"};
    Set<String> picked =
        DvDatabaseSourceImportSupport.tableNamesForSelectionIndexes(choices, new int[] {2, 0});

    assertEquals(List.of("gamma", "alpha"), List.copyOf(picked));
  }

  @Test
  void tableImportRequestsFromRowsSkipsIncompleteRows() {
    List<TableImportRequest> requests =
        DvDatabaseSourceImportSupport.tableImportRequestsFromRows(
            java.util.Arrays.asList(
                new Object[] {"\"orders\"", "crm-orders"},
                new Object[] {null, "missing-table"},
                new Object[] {"customers"},
                null,
                new Object[] {"customers", "crm-customers"}));

    assertEquals(2, requests.size());
    assertEquals("orders", requests.get(0).tableName());
    assertEquals("crm-orders", requests.get(0).recordDefinitionName());
    assertEquals("customers", requests.get(1).tableName());
  }

  @Test
  void forEachTableImportReportsProgressPerTable() throws Exception {
    List<TableImportRequest> requests =
        List.of(
            new TableImportRequest("alpha", "src-alpha"),
            new TableImportRequest("beta", "src-beta"),
            new TableImportRequest("gamma", "src-gamma"));
    RecordingMonitor monitor = new RecordingMonitor();
    List<String> imported = new ArrayList<>();

    boolean cancelled =
        DvDatabaseSourceImportSupport.forEachTableImport(
            requests,
            "Importing 3 table(s)",
            monitor,
            request -> imported.add(request.tableName()));

    assertFalse(cancelled);
    assertEquals(3, monitor.beginTaskWork);
    assertEquals("Importing 3 table(s)", monitor.beginTaskName);
    assertEquals(List.of("alpha", "beta", "gamma"), imported);
    assertEquals(List.of("alpha", "beta", "gamma"), monitor.subTasks);
    assertEquals(3, monitor.workedTotal);
  }

  @Test
  void forEachTableImportStopsWhenCancelled() throws Exception {
    List<TableImportRequest> requests =
        List.of(
            new TableImportRequest("alpha", "src-alpha"),
            new TableImportRequest("beta", "src-beta"),
            new TableImportRequest("gamma", "src-gamma"),
            new TableImportRequest("delta", "src-delta"));
    RecordingMonitor monitor = new RecordingMonitor();
    monitor.cancelAfterWorked = 2;
    List<String> imported = new ArrayList<>();

    boolean cancelled =
        DvDatabaseSourceImportSupport.forEachTableImport(
            requests, "Importing", monitor, request -> imported.add(request.tableName()));

    assertTrue(cancelled);
    assertEquals(List.of("alpha", "beta"), imported);
    assertEquals(2, monitor.workedTotal);
  }

  private static final class RecordingMonitor implements IProgressMonitor {
    int beginTaskWork;
    String beginTaskName;
    int workedTotal;
    int cancelAfterWorked = -1;
    final List<String> subTasks = new ArrayList<>();
    private final AtomicInteger worked = new AtomicInteger();

    @Override
    public void beginTask(String message, int nrWorks) {
      beginTaskName = message;
      beginTaskWork = nrWorks;
    }

    @Override
    public void subTask(String message) {
      subTasks.add(message);
    }

    @Override
    public boolean isCanceled() {
      return cancelAfterWorked >= 0 && worked.get() >= cancelAfterWorked;
    }

    @Override
    public void worked(int nrWorks) {
      worked.addAndGet(nrWorks);
      workedTotal += nrWorks;
    }

    @Override
    public void done() {
      // unused
    }

    @Override
    public void setTaskName(String taskName) {
      // unused
    }
  }
}
