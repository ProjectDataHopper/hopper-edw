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
package org.apache.hop.datavault.config;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Resolves pipeline and workflow run configurations for DV/BV/DM update actions. */
public final class DvRunConfigurationSupport {

  public static final String DEFAULT_RUN_CONFIGURATION = "local";

  private DvRunConfigurationSupport() {}

  public static String resolvePipelineRunConfiguration(String actionValue, IVariables variables) {
    return resolveRunConfiguration(
        actionValue,
        DataVaultConfigSingleton.getConfig().getDefaultPipelineRunConfiguration(),
        variables);
  }

  public static String resolveWorkflowRunConfiguration(String actionValue, IVariables variables) {
    return resolveRunConfiguration(
        actionValue,
        DataVaultConfigSingleton.getConfig().getDefaultWorkflowRunConfiguration(),
        variables);
  }

  private static String resolveRunConfiguration(
      String actionValue, String globalDefault, IVariables variables) {
    String resolved = resolve(actionValue, variables);
    if (!Utils.isEmpty(resolved)) {
      return resolved;
    }
    resolved = resolve(globalDefault, variables);
    if (!Utils.isEmpty(resolved)) {
      return resolved;
    }
    return DEFAULT_RUN_CONFIGURATION;
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    if (variables != null) {
      return variables.resolve(value);
    }
    return value;
  }
}
