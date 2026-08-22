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
package org.hopper.edw.datavault.metrics.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.util.Set;
import org.apache.hop.workflow.Workflow;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.actions.start.ActionStart;
import org.apache.hop.workflow.engines.local.LocalWorkflowEngine;
import org.junit.jupiter.api.Test;

class UpdateRunLiveStagingPipelineSupportTest {

  @Test
  void derivesElementNameFromGeneratedFactPipelineAction() throws Exception {
    LocalWorkflowEngine engine = engineWithActiveAction("run_0001-dm-fact-f_orders");
    assertEquals(
        "f_orders", UpdateRunLiveStagingPipelineSupport.resolveFallbackElementName(engine));
  }

  @Test
  void derivesTableNameFromBulkLoadAction() throws Exception {
    LocalWorkflowEngine engine = engineWithActiveAction("bulk_load_f_orders_0");
    assertEquals(
        "f-orders", UpdateRunLiveStagingPipelineSupport.resolveFallbackElementName(engine));
  }

  @Test
  void returnsNullWhenNoActiveActions() {
    LocalWorkflowEngine engine = new LocalWorkflowEngine(new WorkflowMeta());
    assertNull(UpdateRunLiveStagingPipelineSupport.resolveFallbackElementName(engine));
  }

  private static LocalWorkflowEngine engineWithActiveAction(String actionName) throws Exception {
    LocalWorkflowEngine engine = new LocalWorkflowEngine(new WorkflowMeta());
    ActionMeta actionMeta = new ActionMeta(new ActionStart(actionName));
    actionMeta.setName(actionName);
    Field field = Workflow.class.getDeclaredField("activeActions");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<ActionMeta> activeActions = (Set<ActionMeta>) field.get(engine);
    activeActions.add(actionMeta.clone());
    return engine;
  }
}
