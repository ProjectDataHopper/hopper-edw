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
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveRegistry;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveState;
import org.hopper.edw.datavault.workflow.actions.businessvaultupdate.ActionBusinessVaultUpdate;
import org.hopper.edw.datavault.workflow.actions.datavaultupdate.ActionDataVaultUpdate;
import org.hopper.edw.datavault.workflow.actions.dimensionalupdate.ActionDimensionalUpdate;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.WorkflowPainter;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;

/** Draws the live update badge on executing vault/dimensional update workflow actions. */
public final class UpdateRunLiveWorkflowPaintSupport {

  private static final String PLUGIN_DATA_VAULT_UPDATE = "DATA_VAULT_UPDATE";
  private static final String PLUGIN_BUSINESS_VAULT_UPDATE = "BUSINESS_VAULT_UPDATE";
  private static final String PLUGIN_DIMENSIONAL_UPDATE = "DIMENSIONAL_UPDATE";
  static final String RUNNING_ICON_PATH = "ui/images/running-icon.svg";
  private static final int BADGE_HIT_PADDING = 6;

  private UpdateRunLiveWorkflowPaintSupport() {}

  public static void paintWorkflowEnd(WorkflowPainter painter) {
    if (painter == null) {
      return;
    }
    List<ActionMeta> activeActions = painter.getActiveActions();
    if (activeActions == null || activeActions.isEmpty()) {
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
    for (ActionMeta actionMeta : activeActions) {
      Optional<UpdateRunLiveSnapshot> snapshot =
          findSnapshot(workflowFilename, workflowName, actionMeta);
      if (snapshot.isEmpty()) {
        continue;
      }
      paintBadge(view, actionMeta, snapshot.get());
    }
  }

  private static void paintBadge(
      PainterView view, ActionMeta actionMeta, UpdateRunLiveSnapshot snapshot) {
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
      drawStatusIcon(view, snapshot.getOverallState(), iconX, iconY);
    } catch (Exception ignored) {
      return;
    }
    String tooltip = snapshot.getTooltipText();
    if (Utils.isEmpty(tooltip)) {
      tooltip = UpdateRunLiveSnapshotTooltipSupport.defaultTooltip(snapshot);
    }
    UpdateRunLiveAreaOwnerData badgeData =
        new UpdateRunLiveAreaOwnerData(snapshot.getMetricsRunId());
    int hitX = iconX - BADGE_HIT_PADDING;
    int hitY = iconY - BADGE_HIT_PADDING;
    int hitSize = view.miniIconSize() + (2 * BADGE_HIT_PADDING);
    view.areaOwners()
        .add(
            new AreaOwner(
                AreaType.CUSTOM, hitX, hitY, hitSize, hitSize, view.offset(), badgeData, tooltip));
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
