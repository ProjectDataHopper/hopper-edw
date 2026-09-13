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

import java.lang.reflect.Field;
import java.util.List;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;
import org.apache.hop.ui.hopgui.file.workflow.extension.HopGuiWorkflowGraphExtension;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveRegistry;
import org.hopper.edw.datavault.metrics.live.UpdateRunWavePhase;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshot;

/** Shared workflow-graph mouse handling for live update badges. */
public final class UpdateRunLiveWorkflowMouseSupport {

  private UpdateRunLiveWorkflowMouseSupport() {}

  public static boolean openDialogIfBadgeClicked(HopGuiWorkflowGraphExtension extension) {
    if (extension == null || extension.getWorkflowGraph() == null) {
      return false;
    }
    UpdateRunLiveAreaOwnerData data = findBadgeData(extension);
    if (data == null) {
      return false;
    }
    HopGuiWorkflowGraph graph = extension.getWorkflowGraph();
    if (!Utils.isEmpty(data.getWaveId())
        || !Utils.isEmpty(data.getActionName())
        || data.isHistoryPreferred()) {
      ResourceGroupUpdateMetricsDialog.open(
          graph.getHopGui().getShell(),
          graph.getVariables(),
          graph.getHopGui().getMetadataProvider(),
          data);
    } else {
      UpdateRunLiveAnalysisDialog.open(
          graph.getHopGui().getShell(), graph.getVariables(), data.getMetricsRunId());
    }
    extension.setPreventingDefault(true);
    return true;
  }

  public static boolean handleMouseDownIfBadgeClicked(HopGuiWorkflowGraphExtension extension) {
    if (extension == null) {
      return false;
    }
    if (findBadgeData(extension) == null) {
      return false;
    }
    extension.setPreventingDefault(true);
    return true;
  }

  static UpdateRunLiveAreaOwnerData findBadgeData(HopGuiWorkflowGraphExtension extension) {
    if (extension == null) {
      return null;
    }
    UpdateRunLiveAreaOwnerData data = resolveBadgeData(extension.getAreaOwner());
    if (data != null) {
      return data;
    }

    HopGuiWorkflowGraph graph = extension.getWorkflowGraph();
    Point point = extension.getPoint();
    AreaOwner areaOwner = extension.getAreaOwner();
    if (areaOwner == null && graph != null && point != null) {
      areaOwner = graph.getVisibleAreaOwner(point.x, point.y);
      data = resolveBadgeData(areaOwner);
      if (data != null) {
        return data;
      }
    }

    if (point != null) {
      data = findBadgeInAreaOwners(graph, point.x, point.y);
      if (data != null) {
        return data;
      }
    }

    if (point == null) {
      return null;
    }

    int iconSize = resolveIconSize();
    int miniIconSize = Math.max(iconSize / 2, 1);
    ActionMeta actionMeta = actionMetaFrom(areaOwner);
    if (actionMeta != null && isBadgeAction(actionMeta)) {
      if (isBusyArea(areaOwner)
          || UpdateRunLiveWorkflowPaintSupport.badgeHitContains(
              actionMeta.getLocation(), iconSize, miniIconSize, point.x, point.y)) {
        return dataForAction(graph, actionMeta);
      }
    }

    WorkflowMeta workflowMeta =
        graph != null
            ? graph.getWorkflowMeta()
            : actionMeta != null ? actionMeta.getParentWorkflowMeta() : null;
    if (workflowMeta == null || workflowMeta.getActions() == null) {
      return null;
    }
    for (ActionMeta action : workflowMeta.getActions()) {
      if (!isBadgeAction(action)) {
        continue;
      }
      if (UpdateRunLiveWorkflowPaintSupport.badgeHitContains(
          action.getLocation(), iconSize, miniIconSize, point.x, point.y)) {
        return dataForAction(graph, action);
      }
    }
    return null;
  }

  static UpdateRunLiveAreaOwnerData resolveBadgeData(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getOwner() instanceof UpdateRunLiveAreaOwnerData ownerData) {
      return ownerData;
    }
    if (areaOwner.getParent() instanceof UpdateRunLiveAreaOwnerData parentData) {
      return parentData;
    }
    return null;
  }

  static UpdateRunLiveAreaOwnerData findBadgeInAreaOwners(
      HopGuiWorkflowGraph graph, int graphX, int graphY) {
    List<AreaOwner> areaOwners = areaOwnersOf(graph);
    for (int i = areaOwners.size() - 1; i >= 0; i--) {
      AreaOwner areaOwner = areaOwners.get(i);
      if (areaOwner == null || !areaOwner.contains(graphX, graphY)) {
        continue;
      }
      UpdateRunLiveAreaOwnerData data = resolveBadgeData(areaOwner);
      if (data != null) {
        return data;
      }
    }
    return null;
  }

  static ActionMeta actionMetaFrom(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getOwner() instanceof ActionMeta ownerAction) {
      return ownerAction;
    }
    if (areaOwner.getParent() instanceof ActionMeta parentAction) {
      return parentAction;
    }
    return null;
  }

  static boolean isBusyArea(AreaOwner areaOwner) {
    return areaOwner != null && areaOwner.getAreaType() == AreaType.ACTION_BUSY;
  }

  static boolean isBadgeAction(ActionMeta actionMeta) {
    return UpdateRunLiveWorkflowPaintSupport.isGroupUpdateAction(actionMeta)
        || UpdateRunLiveWorkflowPaintSupport.isUpdateAction(actionMeta);
  }

  static UpdateRunLiveAreaOwnerData dataForAction(
      HopGuiWorkflowGraph graph, ActionMeta actionMeta) {
    if (actionMeta == null) {
      return null;
    }
    WorkflowMeta workflowMeta =
        graph != null && graph.getWorkflowMeta() != null
            ? graph.getWorkflowMeta()
            : actionMeta.getParentWorkflowMeta();
    IVariables variables = graph != null ? graph.getVariables() : null;
    String workflowFilename =
        workflowMeta != null
            ? UpdateRunLiveWorkflowPaintSupport.resolveWorkflowFilename(workflowMeta)
            : null;
    String workflowName = workflowMeta != null ? workflowMeta.getName() : null;
    String actionName = actionMeta.getName();

    if (UpdateRunLiveWorkflowPaintSupport.isGroupUpdateAction(actionMeta)) {
      UpdateRunWaveSnapshot wave =
          UpdateRunLiveRegistry.findWave(workflowFilename, workflowName, actionName, variables)
              .orElse(null);
      boolean historyPreferred = wave == null || wave.getPhase() == UpdateRunWavePhase.FINISHED;
      return UpdateRunLiveAreaOwnerData.forWave(
          wave != null ? wave.getWaveId() : null, workflowFilename, actionName, historyPreferred);
    }
    if (UpdateRunLiveWorkflowPaintSupport.isUpdateAction(actionMeta)) {
      return UpdateRunLiveRegistry.findSnapshot(
              workflowFilename, workflowName, actionName, variables)
          .map(snapshot -> new UpdateRunLiveAreaOwnerData(snapshot.getMetricsRunId()))
          .orElse(null);
    }
    return null;
  }

  static int resolveIconSize() {
    try {
      int iconSize = PropsUi.getInstance().getIconSize();
      if (iconSize > 0) {
        return iconSize;
      }
    } catch (Throwable ignored) {
      // Headless unit tests have no PropsUi display.
    }
    return ConstUi.ICON_SIZE;
  }

  @SuppressWarnings("unchecked")
  static List<AreaOwner> areaOwnersOf(HopGuiWorkflowGraph graph) {
    if (graph == null) {
      return List.of();
    }
    try {
      Field field = HopGuiWorkflowGraph.class.getDeclaredField("areaOwners");
      field.setAccessible(true);
      Object value = field.get(graph);
      if (value instanceof List<?> list) {
        return (List<AreaOwner>) list;
      }
    } catch (Exception ignored) {
      // Hop keeps areaOwners private; geometric fallback still applies.
    }
    return List.of();
  }
}
