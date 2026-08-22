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

import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveMonitor;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveState;

/** Builds fallback tooltip text for workflow live-update badges. */
public final class UpdateRunLiveSnapshotTooltipSupport {

  private UpdateRunLiveSnapshotTooltipSupport() {}

  public static String defaultTooltip(UpdateRunLiveSnapshot snapshot) {
    if (snapshot == null) {
      return "";
    }
    return UpdateRunLiveMonitor.buildTooltipText(
        snapshot.getCurrentElementName(),
        snapshot.getModelName(),
        snapshot.getOverallState() != null
            ? snapshot.getOverallState()
            : UpdateRunLiveState.RUNNING);
  }

  public static boolean isLiveBadgeOwner(Object owner) {
    return owner instanceof UpdateRunLiveAreaOwnerData data
        && !Utils.isEmpty(data.getMetricsRunId());
  }
}
