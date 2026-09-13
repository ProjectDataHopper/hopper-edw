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

import java.util.List;
import java.util.Optional;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EImage;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.svg.SvgFile;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.WorkflowPainter;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveRegistry;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveState;
import org.hopper.edw.datavault.metrics.live.UpdateRunWavePhase;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshotSupport;
import org.hopper.edw.datavault.workflow.actions.businessvaultupdate.ActionBusinessVaultUpdate;
import org.hopper.edw.datavault.workflow.actions.datavaultupdate.ActionDataVaultUpdate;
import org.hopper.edw.datavault.workflow.actions.dimensionalupdate.ActionDimensionalUpdate;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ActionUpdateResourceDefinitionGroup;

/** Draws the live update badge on executing vault/dimensional update workflow actions. */
public final class UpdateRunLiveWorkflowPaintSupport {

  private static final String PLUGIN_DATA_VAULT_UPDATE = "DATA_VAULT_UPDATE";
  private static final String PLUGIN_BUSINESS_VAULT_UPDATE = "BUSINESS_VAULT_UPDATE";
  private static final String PLUGIN_DIMENSIONAL_UPDATE = "DIMENSIONAL_UPDATE";
  private static final String PLUGIN_UPDATE_RESOURCE_GROUP = "UPDATE_RESOURCE_DEFINITION_GROUP";
  static final String RUNNING_ICON_PATH = "ui/images/running-icon.svg";
  static final String IDLE_METRICS_ICON_PATH = "execution-metrics-profile.svg";
  private static final int BADGE_HIT_PADDING = 6;

  private UpdateRunLiveWorkflowPaintSupport() {}

  public static void paintWorkflowEnd(WorkflowPainter painter) {
    paintWorkflowEnd(painter, null);
  }

  public static void paintWorkflowEnd(
      WorkflowPainter painter, org.apache.hop.core.variables.IVariables variables) {
    if (painter == null) {
      return;
    }
    WorkflowMeta workflowMeta = painter.getWorkflowMeta();
    if (workflowMeta == null) {
      return;
    }
    PainterView view = PainterView.from(painter);
    if (view.gc() == null || view.areaOwners() == null || view.offset() == null) {
      return;
    }
    org.apache.hop.core.variables.IVariables effectiveVariables =
        variables != null ? variables : view.variables();
    String workflowFilename = resolveWorkflowFilename(workflowMeta);
    String workflowName = workflowMeta.getName();
    List<ActionMeta> activeActions = painter.getActiveActions();
    if (activeActions != null) {
      for (ActionMeta actionMeta : activeActions) {
        Optional<UpdateRunLiveSnapshot> snapshot =
            findSnapshot(workflowFilename, workflowName, actionMeta, effectiveVariables);
        if (snapshot.isEmpty()) {
          continue;
        }
        paintLiveSnapshotBadge(view, actionMeta, snapshot.get());
      }
    }
    paintGroupBadges(
        view, workflowMeta, activeActions, workflowFilename, workflowName, effectiveVariables);
  }

  private static void paintGroupBadges(
      PainterView view,
      WorkflowMeta workflowMeta,
      List<ActionMeta> activeActions,
      String workflowFilename,
      String workflowName,
      org.apache.hop.core.variables.IVariables variables) {
    List<ActionMeta> actions = workflowMeta.getActions();
    if (actions == null || actions.isEmpty()) {
      return;
    }
    for (ActionMeta actionMeta : actions) {
      if (!isGroupUpdateAction(actionMeta)) {
        continue;
      }
      Optional<UpdateRunWaveSnapshot> wave =
          findWave(workflowFilename, workflowName, actionMeta, variables);
      boolean isRunning = activeActions != null && activeActions.contains(actionMeta);
      boolean live =
          isRunning || (wave.isPresent() && wave.get().getPhase() != UpdateRunWavePhase.FINISHED);
      if (live) {
        paintWaveBadge(view, actionMeta, wave.orElse(null), false);
      } else {
        paintWaveBadge(view, actionMeta, wave.orElse(null), true);
      }
    }
  }

  private static void paintLiveSnapshotBadge(
      PainterView view, ActionMeta actionMeta, UpdateRunLiveSnapshot snapshot) {
    String tooltip = snapshot.getTooltipText();
    if (Utils.isEmpty(tooltip)) {
      tooltip = UpdateRunLiveSnapshotTooltipSupport.defaultTooltip(snapshot);
    }
    paintBadge(
        view,
        actionMeta,
        snapshot.getOverallState(),
        false,
        tooltip,
        new UpdateRunLiveAreaOwnerData(snapshot.getMetricsRunId()));
  }

  private static void paintWaveBadge(
      PainterView view, ActionMeta actionMeta, UpdateRunWaveSnapshot wave, boolean idle) {
    UpdateRunLiveState state;
    if (idle) {
      state = null;
    } else if (wave != null && wave.getOverallState() != null) {
      state = wave.getOverallState();
    } else {
      state = UpdateRunLiveState.RUNNING;
    }

    String tooltip;
    if (wave != null && !Utils.isEmpty(wave.getTooltipText())) {
      tooltip = wave.getTooltipText();
    } else if (idle) {
      tooltip =
          BaseMessages.getString(UpdateRunWaveSnapshotSupport.class, "UpdateRunWave.Tooltip.Idle");
    } else {
      tooltip =
          BaseMessages.getString(
              UpdateRunWaveSnapshotSupport.class, "UpdateRunWave.Tooltip.Updating", "in progress");
    }

    String workflowFilename =
        wave != null ? wave.getWorkflowFilename() : resolveWorkflowFilename(actionMeta);
    String actionName = actionMeta.getName();
    String waveId = wave != null ? wave.getWaveId() : null;
    if (waveId == null && !idle) {
      waveId =
          UpdateRunLiveRegistry.findActiveWaveByAction(actionName)
              .map(UpdateRunWaveSnapshot::getWaveId)
              .orElse(null);
    }
    paintBadge(
        view,
        actionMeta,
        state,
        idle,
        tooltip,
        UpdateRunLiveAreaOwnerData.forWave(waveId, workflowFilename, actionName, idle));
  }

  private static String resolveWorkflowFilename(ActionMeta actionMeta) {
    if (actionMeta == null || actionMeta.getParentWorkflowMeta() == null) {
      return null;
    }
    WorkflowMeta workflowMeta = actionMeta.getParentWorkflowMeta();
    if (!Utils.isEmpty(workflowMeta.getFilename())) {
      return workflowMeta.getFilename();
    }
    return workflowMeta.getName();
  }

  private static void paintBadge(
      PainterView view,
      ActionMeta actionMeta,
      UpdateRunLiveState state,
      boolean idleMetricsIcon,
      String tooltip,
      UpdateRunLiveAreaOwnerData badgeData) {
    Point location = actionMeta.getLocation();
    if (location == null) {
      location = new Point(50, 50);
    }
    Point screen = view.real2screen(location.x, location.y);
    Rectangle hit = badgeHitRect(screen.x, screen.y, view.iconSize(), view.miniIconSize());
    int iconX = hit.x + BADGE_HIT_PADDING;
    int iconY = hit.y + BADGE_HIT_PADDING;
    try {
      if (idleMetricsIcon) {
        drawIdleMetricsIcon(view, iconX, iconY);
      } else {
        drawStatusIcon(view, state, iconX, iconY);
      }
    } catch (Exception ignored) {
      return;
    }
    view.areaOwners()
        .add(
            new AreaOwner(
                AreaType.CUSTOM,
                hit.x,
                hit.y,
                hit.width,
                hit.height,
                view.offset(),
                badgeData,
                tooltip));
  }

  /**
   * Hit rectangle of the live-update badge in the same coordinate space as {@code baseX}/{@code
   * baseY}. Pass action location (graph space) for mouse hit-testing, or {@code real2screen}
   * coordinates when registering an {@link AreaOwner}.
   */
  static Rectangle badgeHitRect(int baseX, int baseY, int iconSize, int miniIconSize) {
    int iconX = (baseX + iconSize) - (miniIconSize / 2) + 1;
    int iconY = (baseY + iconSize) - (miniIconSize / 2) + 1;
    int hitSize = miniIconSize + (2 * BADGE_HIT_PADDING);
    return new Rectangle(iconX - BADGE_HIT_PADDING, iconY - BADGE_HIT_PADDING, hitSize, hitSize);
  }

  static boolean badgeHitContains(
      Point location, int iconSize, int miniIconSize, int graphX, int graphY) {
    if (location == null) {
      location = new Point(50, 50);
    }
    if (iconSize <= 0) {
      return false;
    }
    int mini = miniIconSize > 0 ? miniIconSize : Math.max(iconSize / 2, 1);
    return badgeHitRect(location.x, location.y, iconSize, mini).contains(graphX, graphY);
  }

  private static void drawIdleMetricsIcon(PainterView view, int iconX, int iconY) throws Exception {
    SvgFile icon =
        new SvgFile(
            IDLE_METRICS_ICON_PATH, UpdateRunLiveWorkflowPaintSupport.class.getClassLoader());
    view.gc()
        .drawImage(
            icon, iconX, iconY, view.miniIconSize(), view.miniIconSize(), view.magnification(), 0);
  }

  private static Optional<UpdateRunWaveSnapshot> findWave(
      String workflowFilename,
      String workflowName,
      ActionMeta actionMeta,
      org.apache.hop.core.variables.IVariables variables) {
    if (actionMeta == null || Utils.isEmpty(actionMeta.getName())) {
      return Optional.empty();
    }
    return UpdateRunLiveRegistry.findWave(
        workflowFilename, workflowName, actionMeta.getName(), variables);
  }

  private static Optional<UpdateRunLiveSnapshot> findSnapshot(
      String workflowFilename,
      String workflowName,
      ActionMeta actionMeta,
      org.apache.hop.core.variables.IVariables variables) {
    if (actionMeta == null || Utils.isEmpty(actionMeta.getName())) {
      return Optional.empty();
    }
    return UpdateRunLiveRegistry.findSnapshot(
        workflowFilename, workflowName, actionMeta.getName(), variables);
  }

  static boolean isUpdateAction(ActionMeta actionMeta) {
    if (actionMeta == null) {
      return false;
    }
    IAction action = actionMeta.getAction();
    if (action == null) {
      return false;
    }
    if (action instanceof ActionDataVaultUpdate
        || action instanceof ActionBusinessVaultUpdate
        || action instanceof ActionDimensionalUpdate) {
      return true;
    }
    return matchesUpdatePluginId(resolvePluginId(action));
  }

  static boolean isGroupUpdateAction(ActionMeta actionMeta) {
    if (actionMeta == null) {
      return false;
    }
    IAction action = actionMeta.getAction();
    if (action == null) {
      return false;
    }
    if (action instanceof ActionUpdateResourceDefinitionGroup) {
      return true;
    }
    return PLUGIN_UPDATE_RESOURCE_GROUP.equals(resolvePluginId(action));
  }

  private static String resolvePluginId(IAction action) {
    String pluginId = action.getPluginId();
    if (!Utils.isEmpty(pluginId)) {
      return pluginId;
    }
    try {
      return PluginRegistry.getInstance().getPluginId(ActionPluginType.class, action);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean matchesUpdatePluginId(String pluginId) {
    return PLUGIN_DATA_VAULT_UPDATE.equals(pluginId)
        || PLUGIN_BUSINESS_VAULT_UPDATE.equals(pluginId)
        || PLUGIN_DIMENSIONAL_UPDATE.equals(pluginId);
  }

  static boolean usesRunningStatusIcon(UpdateRunLiveState state) {
    return state == null || state == UpdateRunLiveState.RUNNING;
  }

  static EImage resolveStatusImage(UpdateRunLiveState state) {
    if (state == null || state == UpdateRunLiveState.RUNNING) {
      return null;
    }
    return switch (state) {
      case STALLED -> EImage.ERROR;
      case FAILED -> EImage.FAILURE;
      case COMPLETED -> EImage.TRUE;
      default -> null;
    };
  }

  private static void drawStatusIcon(
      PainterView view, UpdateRunLiveState state, int iconX, int iconY) throws Exception {
    if (usesRunningStatusIcon(state)) {
      SvgFile runningIcon =
          new SvgFile(RUNNING_ICON_PATH, UpdateRunLiveWorkflowPaintSupport.class.getClassLoader());
      view.gc()
          .drawImage(
              runningIcon,
              iconX,
              iconY,
              view.miniIconSize(),
              view.miniIconSize(),
              view.magnification(),
              0);
      return;
    }
    EImage image = resolveStatusImage(state);
    if (image != null) {
      view.gc().drawImage(image, iconX, iconY, view.magnification());
    }
  }

  static String resolveWorkflowFilename(WorkflowMeta workflowMeta) {
    if (!Utils.isEmpty(workflowMeta.getFilename())) {
      return workflowMeta.getFilename();
    }
    return workflowMeta.getName();
  }

  private record PainterView(
      IGc gc,
      List<AreaOwner> areaOwners,
      DPoint offset,
      int iconSize,
      int miniIconSize,
      float magnification,
      org.apache.hop.core.variables.IVariables variables) {

    static PainterView from(WorkflowPainter painter) {
      return new PainterView(
          painter.getGc(),
          painter.getAreaOwners(),
          painter.getOffset(),
          painter.getIconSize(),
          painter.getMiniIconSize(),
          painter.getMagnification(),
          painter.getVariables());
    }

    Point real2screen(int x, int y) {
      return new Point((int) (x + offset.x), (int) (y + offset.y));
    }
  }
}
