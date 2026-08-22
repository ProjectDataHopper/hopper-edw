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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.config.DataVaultConfigSingleton;
import org.hopper.edw.datavault.layout.DvPipelineElkLayout;
import org.hopper.edw.datavault.layout.ElkLayout;
import org.hopper.edw.datavault.layout.ElkLayoutAlgorithm;
import org.hopper.edw.datavault.metadata.DvGeneratedPipelineSupport;
import org.hopper.edw.datavault.metadata.IDvTargetLoadConfiguration;
import org.apache.hop.pipeline.PipelineMeta;

/** Saves generated Business Vault build pipelines when configured to do so. */
public final class BvGeneratedPipelineSupport {

  private BvGeneratedPipelineSupport() {}

  public static void applyLayout(PipelineMeta pipelineMeta) throws HopException {
    DvPipelineElkLayout.layout(pipelineMeta);
  }

  public static void applyLayout(List<PipelineMeta> pipelines) throws HopException {
    DvPipelineElkLayout.layout(pipelines);
  }

  /** Applies ELK layered layout for generated SCD2 pipelines (multi-input graphs). */
  public static void applyScd2Layout(PipelineMeta pipelineMeta) throws HopException {
    DvPipelineElkLayout.layout(pipelineMeta, scd2ElkLayout());
  }

  public static void applyScd2Layout(List<PipelineMeta> pipelines) throws HopException {
    DvPipelineElkLayout.layout(pipelines, scd2ElkLayout());
  }

  private static ElkLayout scd2ElkLayout() {
    ElkLayout layout = new ElkLayout(DataVaultConfigSingleton.getConfig().getElkLayout());
    layout.setAlgorithm(ElkLayoutAlgorithm.LAYERED);
    return layout;
  }

  public static String saveBeforeExecution(
      BusinessVaultConfiguration config, IVariables variables, PipelineMeta pipelineMeta)
      throws HopException {
    return saveBeforeExecution((IDvTargetLoadConfiguration) config, variables, pipelineMeta);
  }

  public static String saveBeforeExecution(
      IDvTargetLoadConfiguration config, IVariables variables, PipelineMeta pipelineMeta)
      throws HopException {
    return DvGeneratedPipelineSupport.saveBeforeExecution(config, variables, pipelineMeta);
  }

  public static String saveWorkflowBeforeExecution(
      IDvTargetLoadConfiguration config,
      IVariables variables,
      org.apache.hop.workflow.WorkflowMeta workflowMeta)
      throws HopException {
    return DvGeneratedPipelineSupport.saveWorkflowBeforeExecution(config, variables, workflowMeta);
  }
}
