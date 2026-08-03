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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DvRunConfigurationSupportTest {

  private String originalPipelineDefault;
  private String originalWorkflowDefault;

  @BeforeEach
  void rememberDefaults() {
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    originalPipelineDefault = config.getDefaultPipelineRunConfiguration();
    originalWorkflowDefault = config.getDefaultWorkflowRunConfiguration();
    config.setDefaultPipelineRunConfiguration(null);
    config.setDefaultWorkflowRunConfiguration(null);
  }

  @AfterEach
  void restoreDefaults() {
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    config.setDefaultPipelineRunConfiguration(originalPipelineDefault);
    config.setDefaultWorkflowRunConfiguration(originalWorkflowDefault);
  }

  @Test
  void resolvePipelineRunConfigurationPrefersActionValue() {
    assertEquals(
        "remote",
        DvRunConfigurationSupport.resolvePipelineRunConfiguration("remote", new Variables()));
  }

  @Test
  void resolvePipelineRunConfigurationUsesGlobalDefault() {
    DataVaultConfigSingleton.getConfig().setDefaultPipelineRunConfiguration("cluster");
    assertEquals(
        "cluster",
        DvRunConfigurationSupport.resolvePipelineRunConfiguration(null, new Variables()));
  }

  @Test
  void resolvePipelineRunConfigurationDefaultsToLocal() {
    assertEquals(
        DvRunConfigurationSupport.DEFAULT_RUN_CONFIGURATION,
        DvRunConfigurationSupport.resolvePipelineRunConfiguration(null, new Variables()));
  }

  @Test
  void resolveWorkflowRunConfigurationPrefersActionValue() {
    assertEquals(
        "remote-wf",
        DvRunConfigurationSupport.resolveWorkflowRunConfiguration("remote-wf", new Variables()));
  }

  @Test
  void resolveWorkflowRunConfigurationUsesGlobalDefault() {
    DataVaultConfigSingleton.getConfig().setDefaultWorkflowRunConfiguration("cluster-wf");
    assertEquals(
        "cluster-wf",
        DvRunConfigurationSupport.resolveWorkflowRunConfiguration(null, new Variables()));
  }

  @Test
  void resolveWorkflowRunConfigurationDefaultsToLocal() {
    assertEquals(
        DvRunConfigurationSupport.DEFAULT_RUN_CONFIGURATION,
        DvRunConfigurationSupport.resolveWorkflowRunConfiguration(null, new Variables()));
  }
}
