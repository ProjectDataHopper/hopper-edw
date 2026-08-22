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
package org.hopper.edw.datavault.workflow.actions.updateresourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelUpdatePlanner.ModelLayer;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelUpdatePlanner.ModelUpdateJob;
import org.junit.jupiter.api.Test;

class ResourceGroupModelUpdatePlannerTest {

  @Test
  void planOrdersLayersDvThenBvThenDmAndKeepsListOrder() {
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail-sources");
    group.getDataVaultModelFiles().add("${PROJECT_HOME}/models/retail-360.hdv");
    group.getBusinessVaultModelFiles().add("${PROJECT_HOME}/models/retail-360.hbv");
    group.getBusinessVaultModelFiles().add("${PROJECT_HOME}/models/retail-sql.hbv");
    group.getDimensionalModelFiles().add("${PROJECT_HOME}/models/retail-conformed-dims.hdm");
    group.getDimensionalModelFiles().add("${PROJECT_HOME}/models/retail-f-orders.hdm");

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/project");

    List<ModelUpdateJob> jobs =
        ResourceGroupModelUpdatePlanner.plan(group, true, true, true, variables);

    assertEquals(5, jobs.size());
    assertEquals(ModelLayer.DATA_VAULT, jobs.get(0).layer());
    assertEquals("/project/models/retail-360.hdv", jobs.get(0).modelFile());
    assertEquals(ModelLayer.BUSINESS_VAULT, jobs.get(1).layer());
    assertEquals("/project/models/retail-360.hbv", jobs.get(1).modelFile());
    assertEquals(ModelLayer.BUSINESS_VAULT, jobs.get(2).layer());
    assertEquals("/project/models/retail-sql.hbv", jobs.get(2).modelFile());
    assertEquals(ModelLayer.DIMENSIONAL, jobs.get(3).layer());
    assertEquals("/project/models/retail-conformed-dims.hdm", jobs.get(3).modelFile());
    assertEquals(ModelLayer.DIMENSIONAL, jobs.get(4).layer());
    assertEquals("/project/models/retail-f-orders.hdm", jobs.get(4).modelFile());
  }

  @Test
  void planRespectsIncludeFlagsAndSkipsBlanks() {
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("g");
    group.getDataVaultModelFiles().add("a.hdv");
    group.getDataVaultModelFiles().add("  ");
    group.getBusinessVaultModelFiles().add("b.hbv");
    group.getDimensionalModelFiles().add("c.hdm");

    List<ModelUpdateJob> jobs =
        ResourceGroupModelUpdatePlanner.plan(group, true, false, true, new Variables());

    assertEquals(2, jobs.size());
    assertEquals(ModelLayer.DATA_VAULT, jobs.get(0).layer());
    assertEquals(ModelLayer.DIMENSIONAL, jobs.get(1).layer());
    assertTrue(jobs.stream().noneMatch(j -> j.layer() == ModelLayer.BUSINESS_VAULT));
  }

  @Test
  void planEmptyGroupReturnsEmptyList() {
    assertTrue(
        ResourceGroupModelUpdatePlanner.plan(
                new ResourceDefinitionGroupMeta("empty"), true, true, true, new Variables())
            .isEmpty());
  }
}
