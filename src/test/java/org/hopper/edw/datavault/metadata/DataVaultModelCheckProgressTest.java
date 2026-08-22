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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataVaultModelCheckProgressTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void checkReportsProgressPerTable() {
    DataVaultModel model = modelWithTables(3);
    RecordingMonitor monitor = new RecordingMonitor();

    model.check(
        new MemoryMetadataProvider(), new Variables(), DvModelCheckOptions.fastOnly(), monitor);

    assertEquals(3, monitor.beginTaskWork);
    assertEquals(3, monitor.workedTotal);
    assertTrue(monitor.done);
    assertEquals(3, monitor.subTasks.size());
    assertTrue(monitor.subTasks.get(0).contains("hub_0"));
    assertTrue(monitor.subTasks.get(2).contains("hub_2"));
  }

  @Test
  void checkStopsWhenCancelled() {
    DataVaultModel model = modelWithTables(5);
    RecordingMonitor monitor = new RecordingMonitor();
    monitor.cancelAfterWorked = 2;

    List<ICheckResult> remarks =
        model.check(
            new MemoryMetadataProvider(), new Variables(), DvModelCheckOptions.fastOnly(), monitor);

    assertEquals(2, monitor.workedTotal);
    assertTrue(monitor.done);
    // Partial remarks are still returned (model-level + checked tables).
    assertTrue(remarks != null);
  }

  private static DataVaultModel modelWithTables(int hubCount) {
    DataVaultModel model = new DataVaultModel();
    for (int i = 0; i < hubCount; i++) {
      DvHub hub = new DvHub();
      hub.setName("hub_" + i);
      hub.setTableName("h_hub_" + i);
      model.getTables().add(hub);
    }
    return model;
  }

  private static final class RecordingMonitor implements IProgressMonitor {
    int beginTaskWork;
    int workedTotal;
    boolean done;
    int cancelAfterWorked = -1;
    final List<String> subTasks = new ArrayList<>();
    private final AtomicInteger worked = new AtomicInteger();

    @Override
    public void beginTask(String message, int nrWorks) {
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
      done = true;
    }

    @Override
    public void setTaskName(String taskName) {
      // unused
    }
  }
}
