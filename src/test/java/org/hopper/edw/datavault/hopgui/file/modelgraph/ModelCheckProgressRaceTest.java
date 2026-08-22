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
package org.hopper.edw.datavault.hopgui.file.modelgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.junit.jupiter.api.Test;

/**
 * Ensures progress-based model checks still return remarks when work calls {@code monitor.done()}
 * before returning (as {@code DataVaultModel.check} does in a finally block).
 */
class ModelCheckProgressRaceTest {

  @Test
  void nullShellStillReturnsRemarks() {
    List<ICheckResult> produced =
        List.of(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "x", null));
    ModelDialogValidationSupport.ModelCheckProgressResult result =
        ModelDialogValidationSupport.runChecksWithProgress(
            null,
            monitor -> {
              if (monitor != null) {
                monitor.beginTask("t", 1);
                monitor.done();
              }
              return produced;
            });
    assertFalse(result.cancelled());
    assertEquals(1, result.remarks().size());
    assertEquals("x", result.remarks().get(0).getText());
  }

  @Test
  void workThatCallsDoneInFinallyStillReturnsRemarks() {
    ModelDialogValidationSupport.ModelCheckProgressResult result =
        ModelDialogValidationSupport.runChecksWithProgress(
            null,
            monitor -> {
              List<ICheckResult> remarks = new ArrayList<>();
              remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_WARNING, "warn", null));
              try {
                return remarks;
              } finally {
                if (monitor != null) {
                  monitor.done();
                }
              }
            });
    assertEquals(1, result.remarks().size());
    assertEquals("warn", result.remarks().get(0).getText());
  }
}
