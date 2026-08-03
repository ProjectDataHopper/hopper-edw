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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.execution.ExecutionInfoLocation;
import org.apache.hop.execution.IExecutionInfoLocation;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;

/**
 * Resolves and initializes Hop Execution Information Locations from pipeline run configurations.
 */
public final class ExecutionInfoLocationSupport {

  private ExecutionInfoLocationSupport() {}

  public record ResolvedExecutionInfoLocation(
      String pipelineRunConfiguration,
      String executionInfoLocationName,
      IExecutionInfoLocation location) {}

  public static ResolvedExecutionInfoLocation resolveInitializedLocation(
      String pipelineRunConfigurationName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (metadataProvider == null || Utils.isEmpty(pipelineRunConfigurationName)) {
      return null;
    }
    String resolvedRunConfig =
        variables != null
            ? variables.resolve(pipelineRunConfigurationName)
            : pipelineRunConfigurationName;
    if (Utils.isEmpty(resolvedRunConfig)) {
      return null;
    }

    PipelineRunConfiguration runConfiguration =
        metadataProvider.getSerializer(PipelineRunConfiguration.class).load(resolvedRunConfig);
    if (runConfiguration == null) {
      return null;
    }

    String locationName = runConfiguration.getExecutionInfoLocationName();
    if (Utils.isEmpty(locationName)) {
      return null;
    }
    if (variables != null) {
      locationName = variables.resolve(locationName);
    }
    if (Utils.isEmpty(locationName)) {
      return null;
    }

    ExecutionInfoLocation locationMetadata =
        metadataProvider.getSerializer(ExecutionInfoLocation.class).load(locationName);
    if (locationMetadata == null || locationMetadata.getExecutionInfoLocation() == null) {
      return null;
    }

    IExecutionInfoLocation location = locationMetadata.getExecutionInfoLocation();
    location.initialize(variables, metadataProvider);
    return new ResolvedExecutionInfoLocation(resolvedRunConfig, locationName, location);
  }
}
