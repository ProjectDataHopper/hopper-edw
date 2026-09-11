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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;

/**
 * Turns on transform performance snapshots for local {@link Pipeline} engines so the results-pane
 * Performance chart has data. Does not mark the pipeline changed and does not write the flag to the
 * {@code .hpl} file. Remote/Beam/Spark engines are left unchanged.
 */
@ExtensionPoint(
    id = "EnablePipelinePerformanceSnapshotsExtensionPoint",
    extensionPointId = "PipelinePrepareExecution",
    description = "Enable transform performance snapshots for local pipeline runs")
public class EnablePipelinePerformanceSnapshotsExtensionPoint
    implements IExtensionPoint<IPipelineEngine<PipelineMeta>> {

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, IPipelineEngine<PipelineMeta> pipeline)
      throws HopException {
    enable(pipeline);
  }

  static boolean enable(IPipelineEngine<PipelineMeta> pipeline) {
    if (!(pipeline instanceof Pipeline local)) {
      return false;
    }
    return enableOn(local.getPipelineMeta());
  }

  static boolean enableOn(PipelineMeta meta) {
    if (meta == null || meta.isCapturingTransformPerformanceSnapShots()) {
      return false;
    }
    meta.setCapturingTransformPerformanceSnapShots(true);
    return true;
  }
}
