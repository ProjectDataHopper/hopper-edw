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
package org.hopper.edw.datavault.layout;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.pipeline.PipelineMeta;

/** Applies {@link ElkLayout} to Hop {@link PipelineMeta} graphs using the Eclipse Layout Kernel. */
public final class DvPipelineElkLayout {

  private DvPipelineElkLayout() {}

  public static void layout(PipelineMeta pipelineMeta) throws HopException {
    layout(pipelineMeta, DataVaultConfigSingleton.getConfig().getElkLayout());
  }

  public static void layout(List<PipelineMeta> pipelines) throws HopException {
    layout(pipelines, DataVaultConfigSingleton.getConfig().getElkLayout());
  }

  public static void layout(List<PipelineMeta> pipelines, ElkLayout layout) throws HopException {
    if (pipelines == null) {
      return;
    }
    for (PipelineMeta pipeline : pipelines) {
      layout(pipeline, layout);
    }
  }

  public static void layout(PipelineMeta pipelineMeta, ElkLayout layout) throws HopException {
    if (pipelineMeta == null) {
      return;
    }
    ElkGraphLayout.fromPipeline(pipelineMeta).layout(layout);
  }
}
