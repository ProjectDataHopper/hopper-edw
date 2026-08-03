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
package org.apache.hop.datavault.hopgui.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.gui.IGc.EImage;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveState;
import org.apache.hop.datavault.workflow.actions.datavaultupdate.ActionDataVaultUpdate;
import org.apache.hop.workflow.action.ActionMeta;
import org.junit.jupiter.api.Test;

class UpdateRunLiveWorkflowPaintSupportTest {

  @Test
  void recognizesClonedUpdateActionWithoutPluginId() {
    ActionDataVaultUpdate action = new ActionDataVaultUpdate("Data Vault Update");
    ActionMeta actionMeta = new ActionMeta(action);

    ActionMeta clone = actionMeta.clone();

    assertTrue(UpdateRunLiveWorkflowPaintSupport.isUpdateAction(clone));
  }

  @Test
  void runningStateUsesDistinctSvgBadge() {
    assertTrue(UpdateRunLiveWorkflowPaintSupport.usesRunningStatusIcon(UpdateRunLiveState.RUNNING));
    assertNull(UpdateRunLiveWorkflowPaintSupport.resolveStatusImage(UpdateRunLiveState.RUNNING));
    assertEquals("ui/images/running-icon.svg", UpdateRunLiveWorkflowPaintSupport.RUNNING_ICON_PATH);
  }

  @Test
  void stalledStateKeepsStandardErrorIcon() {
    assertEquals(
        EImage.ERROR,
        UpdateRunLiveWorkflowPaintSupport.resolveStatusImage(UpdateRunLiveState.STALLED));
  }
}
