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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.junit.jupiter.api.Test;

class DvTargetLoadConfigurationSupportTest {

  @Test
  void dataVaultConfigurationResolvesWorkflowNameWithPrefix() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setGeneratedWorkflowNamePrefix("dv-test-");
    assertEquals(
        "dv-test-customers", config.resolveGeneratedWorkflowName(new Variables(), "customers"));
  }

  @Test
  void businessVaultConfigurationDefaultsToTableOutput() {
    BusinessVaultConfiguration config = new BusinessVaultConfiguration();
    assertEquals(DvTargetLoadMode.TABLE_OUTPUT, config.resolveTargetLoadMode());
    assertEquals(
        BusinessVaultConfiguration.DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX + "bv1",
        config.resolveGeneratedWorkflowName(new Variables(), "bv1"));
  }

  @Test
  void dimensionalConfigurationSupportsStagingFolderPerModel() {
    DimensionalConfiguration config = new DimensionalConfiguration();
    config.setBulkLoadStagingFolder("/tmp/bulk");
    Variables variables = new Variables();
    assertEquals(
        "/tmp/bulk/warehouse/", config.resolveBulkLoadStagingFolder(variables, "warehouse"));
  }
}
