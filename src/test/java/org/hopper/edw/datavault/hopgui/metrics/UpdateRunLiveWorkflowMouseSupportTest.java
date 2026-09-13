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
package org.hopper.edw.datavault.hopgui.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.hopgui.file.workflow.extension.HopGuiWorkflowGraphExtension;
import org.apache.hop.workflow.action.ActionMeta;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ActionUpdateResourceDefinitionGroup;
import org.junit.jupiter.api.Test;

class UpdateRunLiveWorkflowMouseSupportTest {

  @Test
  void badgeAreaUsesParentForRunIdAndOwnerForTooltip() {
    AreaOwner areaOwner =
        new AreaOwner(
            AreaType.CUSTOM,
            0,
            0,
            20,
            20,
            new DPoint(0, 0),
            new UpdateRunLiveAreaOwnerData("run-1"),
            "Updating model");

    assertTrue(UpdateRunLiveSnapshotTooltipSupport.isLiveBadgeOwner(areaOwner.getParent()));
    assertTrue(areaOwner.getOwner() instanceof String);
  }

  @Test
  void rejectsNullExtension() {
    assertFalse(UpdateRunLiveWorkflowMouseSupport.openDialogIfBadgeClicked(null));
    assertFalse(UpdateRunLiveWorkflowMouseSupport.handleMouseDownIfBadgeClicked(null));
  }

  @Test
  void mouseDownPreventsDefaultWhenCustomDrawnAreaIsClicked() {
    UpdateRunLiveAreaOwnerData badge =
        UpdateRunLiveAreaOwnerData.forWave("wave-1", "/path/workflow.hwf", "RDG Action", false);
    AreaOwner areaOwner =
        new AreaOwner(
            AreaType.CUSTOM, 0, 0, 20, 20, new DPoint(0, 0), badge, "Updating resource group");
    HopGuiWorkflowGraphExtension extension =
        new HopGuiWorkflowGraphExtension(null, null, new Point(5, 5), areaOwner);

    assertTrue(UpdateRunLiveWorkflowMouseSupport.handleMouseDownIfBadgeClicked(extension));
    assertTrue(extension.isPreventingDefault());
  }

  @Test
  void mouseDownFindsRunningIconWhenActionIconIsTopmost() {
    ActionMeta actionMeta = groupActionAt(100, 100);
    AreaOwner actionIcon =
        new AreaOwner(
            AreaType.ACTION_ICON,
            100,
            100,
            ConstUi.ICON_SIZE,
            ConstUi.ICON_SIZE,
            new DPoint(0, 0),
            null,
            actionMeta);
    // Bottom-right running-icon.svg region (graph coordinates).
    Point click = new Point(126, 126);
    HopGuiWorkflowGraphExtension extension =
        new HopGuiWorkflowGraphExtension(null, null, click, actionIcon);

    assertTrue(
        UpdateRunLiveWorkflowPaintSupport.badgeHitContains(
            actionMeta.getLocation(), ConstUi.ICON_SIZE, ConstUi.ICON_SIZE / 2, click.x, click.y));
    assertTrue(UpdateRunLiveWorkflowMouseSupport.handleMouseDownIfBadgeClicked(extension));
    assertTrue(extension.isPreventingDefault());
    assertEquals(
        "RDG Action", UpdateRunLiveWorkflowMouseSupport.findBadgeData(extension).getActionName());
  }

  @Test
  void mouseDownIgnoresActionIconCenter() {
    ActionMeta actionMeta = groupActionAt(100, 100);
    AreaOwner actionIcon =
        new AreaOwner(
            AreaType.ACTION_ICON,
            100,
            100,
            ConstUi.ICON_SIZE,
            ConstUi.ICON_SIZE,
            new DPoint(0, 0),
            null,
            actionMeta);
    HopGuiWorkflowGraphExtension extension =
        new HopGuiWorkflowGraphExtension(null, null, new Point(116, 116), actionIcon);

    assertFalse(UpdateRunLiveWorkflowMouseSupport.handleMouseDownIfBadgeClicked(extension));
    assertFalse(extension.isPreventingDefault());
  }

  @Test
  void mouseDownTreatsHopBusyIconOnGroupActionAsBadgeClick() {
    ActionMeta actionMeta = groupActionAt(100, 100);
    AreaOwner busy =
        new AreaOwner(
            AreaType.ACTION_BUSY,
            117,
            93,
            ConstUi.ICON_SIZE / 2,
            ConstUi.ICON_SIZE / 2,
            new DPoint(0, 0),
            null,
            actionMeta);
    HopGuiWorkflowGraphExtension extension =
        new HopGuiWorkflowGraphExtension(null, null, new Point(120, 95), busy);

    assertTrue(UpdateRunLiveWorkflowMouseSupport.handleMouseDownIfBadgeClicked(extension));
    assertTrue(extension.isPreventingDefault());
  }

  @Test
  void resolveBadgeDataReadsParentDrawnArea() {
    UpdateRunLiveAreaOwnerData badge =
        UpdateRunLiveAreaOwnerData.forWave("wave-1", "/path/workflow.hwf", "RDG Action", false);
    AreaOwner areaOwner =
        new AreaOwner(AreaType.CUSTOM, 0, 0, 20, 20, new DPoint(0, 0), badge, "Updating");

    assertEquals(badge, UpdateRunLiveWorkflowMouseSupport.resolveBadgeData(areaOwner));
    assertNull(UpdateRunLiveWorkflowMouseSupport.resolveBadgeData(null));
  }

  @Test
  void drawnAreaConstantIsRecognizedAsLiveBadgeOwner() {
    assertTrue(
        UpdateRunLiveSnapshotTooltipSupport.isLiveBadgeOwner(
            UpdateRunLiveAreaOwnerData.AREA_DRAWN_LIVE_UPDATE_BADGE));
  }

  private static ActionMeta groupActionAt(int x, int y) {
    ActionUpdateResourceDefinitionGroup action = new ActionUpdateResourceDefinitionGroup();
    action.setName("RDG Action");
    ActionMeta actionMeta = new ActionMeta(action);
    actionMeta.setLocation(x, y);
    return actionMeta;
  }
}
