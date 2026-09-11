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
package org.hopper.edw.datavault.xp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.pipeline.PipelineMeta;
import org.junit.jupiter.api.Test;

class EnablePipelinePerformanceSnapshotsExtensionPointTest {

  @Test
  void enablesWhenOffWithoutMarkingChanged() {
    PipelineMeta meta = new PipelineMeta();
    assertFalse(meta.isCapturingTransformPerformanceSnapShots());
    assertFalse(meta.hasChanged());
    assertTrue(EnablePipelinePerformanceSnapshotsExtensionPoint.enableOn(meta));
    assertTrue(meta.isCapturingTransformPerformanceSnapShots());
    assertFalse(meta.hasChanged());
    assertFalse(EnablePipelinePerformanceSnapshotsExtensionPoint.enableOn(meta));
  }

  @Test
  void nullMeta() {
    assertFalse(EnablePipelinePerformanceSnapshotsExtensionPoint.enableOn(null));
  }

  @Test
  void enableNonLocalEngineIsNoOp() {
    assertFalse(EnablePipelinePerformanceSnapshotsExtensionPoint.enable(null));
  }
}
