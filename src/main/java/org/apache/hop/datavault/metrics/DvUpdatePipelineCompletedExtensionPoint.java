/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metrics;

import java.util.Optional;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;

/**
 * Collects source read, target read, and target insert metrics for staged Data Vault update
 * pipelines when {@link DvUpdateMetricsConstants#VAR_RUN_ID} is present in the execution variables.
 */
@ExtensionPoint(
    id = "DvUpdatePipelineCompletedExtensionPoint",
    extensionPointId = "PipelineCompleted",
    description =
        "Collects Data Vault update pipeline metrics (source read, target read, target insert) after pipeline completion")
public class DvUpdatePipelineCompletedExtensionPoint
    implements IExtensionPoint<IPipelineEngine<PipelineMeta>> {

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, IPipelineEngine<PipelineMeta> engine)
      throws HopException {
    if (engine == null || engine.getPipelineMeta() == null) {
      return;
    }

    String runId = resolveVariable(variables, engine, DvUpdateMetricsConstants.VAR_RUN_ID);
    if (Utils.isEmpty(runId)) {
      return;
    }

    String pipelineName = engine.getPipelineMeta().getName();
    if (Utils.isEmpty(pipelineName)
        || pipelineName.startsWith(DvUpdateMetricsConstants.ORCHESTRATOR_NAME_PREFIX)) {
      return;
    }

    Optional<DvUpdateMetricsParser.ParsedPipeline> parsed =
        DvUpdateMetricsParser.parse(pipelineName);

    String modelName = resolveVariable(variables, engine, DvUpdateMetricsConstants.VAR_MODEL_NAME);

    DvUpdateTableMetrics metrics;
    if (parsed.isPresent()) {
      metrics = MetadataMetricsResolver.resolve(engine, runId, modelName, parsed);
    } else {
      metrics = MetadataMetricsResolver.resolve(engine, runId, modelName, Optional.empty());
      if (Utils.isEmpty(metrics.getTableType()) && Utils.isEmpty(metrics.getTableName())) {
        return;
      }
    }
    DvUpdateMetricsCollector.record(metrics);
  }

  private static String resolveVariable(
      IVariables variables, IPipelineEngine<PipelineMeta> engine, String name) {
    if (variables != null && !Utils.isEmpty(variables.getVariable(name))) {
      return variables.getVariable(name);
    }
    if (engine instanceof IVariables engineVariables
        && !Utils.isEmpty(engineVariables.getVariable(name))) {
      return engineVariables.getVariable(name);
    }
    return null;
  }
}
