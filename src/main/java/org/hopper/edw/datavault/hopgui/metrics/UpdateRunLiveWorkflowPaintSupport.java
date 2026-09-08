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
import java.util.Optional;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.BasePainter;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EImage;
import org.apache.hop.core.gui.Point;
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
    if (painter == null) {
      return;
    }
    WorkflowMeta workflowMeta = painter.getWorkflowMeta();
    if (workflowMeta == null) {
      return;
    }
    PainterView view;
    try {
      view = PainterView.from(painter);
    } catch (ReflectiveOperationException ignored) {
      return;
    }
    String workflowFilename = resolveWorkflowFilename(workflowMeta);
    String workflowName = workflowMeta.getName();
    List<ActionMeta> activeActions = painter.getActiveActions();
    if (activeActions != null) {
      for (ActionMeta actionMeta : activeActions) {
        Optional<UpdateRunLiveSnapshot> snapshot =
            findSnapshot(workflowFilename, workflowName, actionMeta);
        if (snapshot.isEmpty()) {
          continue;
        }
        paintLiveSnapshotBadge(view, actionMeta, snapshot.get());
      }
    }
    paintGroupBadges(view, workflowMeta, activeActions, workflowFilename, workflowName);
  }

  private static void paintGroupBadges(
      PainterView view,
      WorkflowMeta workflowMeta,
      List<ActionMeta> activeActions,
      String workflowFilename,
      String workflowName) {
    List<ActionMeta> actions = workflowMeta.getActions();
    if (actions == null || actions.isEmpty()) {
      return;
    }
    for (ActionMeta actionMeta : actions) {
      if (!isGroupUpdateAction(actionMeta)) {
        continue;
      }
      Optional<UpdateRunWaveSnapshot> wave = findWave(workflowFilename, workflowName, actionMeta);
      boolean live =
          wave.isPresent()
              && wave.get().getPhase() != UpdateRunWavePhase.FINISHED
              && activeActions != null
              && activeActions.contains(actionMeta);
      if (live) {
        paintWaveBadge(view, actionMeta, wave.get(), false);
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
    UpdateRunLiveState state =
        idle || wave == null
            ? null
            : wave.getOverallState() != null ? wave.getOverallState() : UpdateRunLiveState.RUNNING;
    String tooltip;
    if (wave != null && !Utils.isEmpty(wave.getTooltipText())) {
      tooltip = wave.getTooltipText();
    } else {
      tooltip =
          BaseMessages.getString(UpdateRunWaveSnapshotSupport.class, "UpdateRunWave.Tooltip.Idle");
    }
    String workflowFilename =
        wave != null ? wave.getWorkflowFilename() : resolveWorkflowFilename(actionMeta);
    String actionName = actionMeta.getName();
    String waveId = wave != null ? wave.getWaveId() : null;
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
    int x = screen.x;
    int y = screen.y;
    int iconX = (x + view.iconSize()) - (view.miniIconSize() / 2) + 1;
    int iconY = (y + view.iconSize()) - (view.miniIconSize() / 2) + 1;
    try {
      if (idleMetricsIcon) {
        drawIdleMetricsIcon(view, iconX, iconY);
      } else {
        drawStatusIcon(view, state, iconX, iconY);
      }
    } catch (Exception ignored) {
      return;
    }
    int hitX = iconX - BADGE_HIT_PADDING;
    int hitY = iconY - BADGE_HIT_PADDING;
    int hitSize = view.miniIconSize() + (2 * BADGE_HIT_PADDING);
    view.areaOwners()
        .add(
            new AreaOwner(
                AreaType.CUSTOM, hitX, hitY, hitSize, hitSize, view.offset(), badgeData, tooltip));
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
      String workflowFilename, String workflowName, ActionMeta actionMeta) {
    if (actionMeta == null || Utils.isEmpty(actionMeta.getName())) {
      return Optional.empty();
    }
    Optional<UpdateRunWaveSnapshot> snapshot =
        UpdateRunLiveRegistry.findWaveByWorkflowAction(workflowFilename, actionMeta.getName());
    if (snapshot.isPresent()) {
      return snapshot;
    }
    if (!Utils.isEmpty(workflowName) && !workflowName.equals(workflowFilename)) {
      return UpdateRunLiveRegistry.findWaveByWorkflowAction(workflowName, actionMeta.getName());
    }
    return Optional.empty();
  }

  private static Optional<UpdateRunLiveSnapshot> findSnapshot(
      String workflowFilename, String workflowName, ActionMeta actionMeta) {
    if (actionMeta == null || Utils.isEmpty(actionMeta.getName())) {
      return Optional.empty();
    }
    Optional<UpdateRunLiveSnapshot> snapshot =
        UpdateRunLiveRegistry.findByWorkflowAction(workflowFilename, actionMeta.getName());
    if (snapshot.isPresent()) {
      return snapshot;
    }
    if (!Utils.isEmpty(workflowName) && !workflowName.equals(workflowFilename)) {
      return UpdateRunLiveRegistry.findByWorkflowAction(workflowName, actionMeta.getName());
    }
    return Optional.empty();
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
      float magnification) {

    static PainterView from(WorkflowPainter painter) throws ReflectiveOperationException {
      return new PainterView(
          readField(painter, "gc", IGc.class),
          readField(painter, "areaOwners", List.class),
          readField(painter, "offset", DPoint.class),
          readField(painter, "iconSize", int.class),
          readField(painter, "miniIconSize", int.class),
          readField(painter, "magnification", float.class));
    }

    Point real2screen(int x, int y) {
      return new Point((int) (x + offset.x), (int) (y + offset.y));
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String name, Class<T> type)
        throws ReflectiveOperationException {
      Field field = BasePainter.class.getDeclaredField(name);
      field.setAccessible(true);
      Object value = field.get(target);
      return (T) value;
    }
  }
}
