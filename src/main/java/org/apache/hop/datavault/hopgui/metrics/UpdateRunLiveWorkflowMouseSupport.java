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
package org.apache.hop.datavault.hopgui.metrics;

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.ui.hopgui.file.workflow.extension.HopGuiWorkflowGraphExtension;

/** Shared workflow-graph mouse handling for live update badges. */
public final class UpdateRunLiveWorkflowMouseSupport {

  private UpdateRunLiveWorkflowMouseSupport() {}

  public static boolean openDialogIfBadgeClicked(HopGuiWorkflowGraphExtension extension) {
    if (extension == null || extension.getWorkflowGraph() == null) {
      return false;
    }
    AreaOwner areaOwner = extension.getAreaOwner();
    if (areaOwner == null || areaOwner.getAreaType() != AreaType.CUSTOM) {
      return false;
    }
    UpdateRunLiveAreaOwnerData data = resolveBadgeData(areaOwner);
    if (data == null) {
      return false;
    }
    UpdateRunLiveAnalysisDialog.open(
        extension.getWorkflowGraph().getHopGui().getShell(),
        extension.getWorkflowGraph().getVariables(),
        data.getMetricsRunId());
    extension.setPreventingDefault(true);
    return true;
  }

  private static UpdateRunLiveAreaOwnerData resolveBadgeData(AreaOwner areaOwner) {
    if (UpdateRunLiveSnapshotTooltipSupport.isLiveBadgeOwner(areaOwner.getOwner())
        && areaOwner.getOwner() instanceof UpdateRunLiveAreaOwnerData ownerData) {
      return ownerData;
    }
    if (UpdateRunLiveSnapshotTooltipSupport.isLiveBadgeOwner(areaOwner.getParent())
        && areaOwner.getParent() instanceof UpdateRunLiveAreaOwnerData parentData) {
      return parentData;
    }
    return null;
  }
}
