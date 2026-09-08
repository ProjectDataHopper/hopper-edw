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

import java.util.concurrent.TimeUnit;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveModelProgress;
import org.hopper.edw.datavault.metrics.live.UpdateRunWavePhase;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshotSupport;

/** Formats a resource-group update wave as markdown for the metrics dialog. */
public final class UpdateRunWaveDiagnosticsFormatter {

  private static final Class<?> PKG = UpdateRunLiveAnalysisDialog.class;

  private UpdateRunWaveDiagnosticsFormatter() {}

  public static String formatMarkdown(UpdateRunWaveSnapshot wave) {
    if (wave == null) {
      return "";
    }
    StringBuilder markdown = new StringBuilder();
    markdown
        .append("## ")
        .append(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Markdown.StatusSection"))
        .append("\n\n");
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Group"),
        wave.getResourceGroupName());
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Phase"),
        wave.getPhase() != null ? wave.getPhase().name() : "");
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.State"),
        wave.getOverallState() != null ? wave.getOverallState().name() : "");
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Progress"),
        wave.getModelsCompleted() + "/" + wave.getModelsTotal());
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Elapsed"),
        UpdateRunWaveSnapshotSupport.formatDuration(wave));
    UpdateRunWaveModelProgress current =
        UpdateRunWaveSnapshotSupport.currentModel(wave.getModels());
    if (current != null) {
      appendField(
          markdown,
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.CurrentModel"),
          UpdateRunWaveSnapshotSupport.displayName(current));
      appendField(
          markdown,
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.CurrentTable"),
          current.getCurrentElementName());
      appendField(
          markdown,
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Bottleneck"),
          current.getBottleneckMessage());
    }
    if (wave.getPhase() == UpdateRunWavePhase.VALIDATING) {
      appendField(
          markdown,
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Validating"),
          Integer.toString(wave.getValidatingTotal()));
    }
    if (wave.getCurrentLiveSnapshot() != null) {
      markdown.append('\n');
      markdown.append(
          UpdateRunLiveDiagnosticsFormatter.formatMarkdown(wave.getCurrentLiveSnapshot()));
    }
    return markdown.toString().trim();
  }

  public static String formatModelMarkdown(UpdateRunWaveModelProgress model) {
    if (model == null) {
      return "";
    }
    StringBuilder markdown = new StringBuilder();
    markdown.append("## ").append(UpdateRunWaveSnapshotSupport.displayName(model)).append("\n\n");
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Layer"),
        formatLayer(model.getLayer()));
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.State"),
        model.getState() != null ? model.getState().name() : "");
    if (model.isSkipped()) {
      appendField(
          markdown,
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Skipped"),
          model.getSkipReason());
    }
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.CurrentTable"),
        model.getCurrentElementName());
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Elapsed"),
        formatElapsed(model));
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.RowsIn"),
        Long.toString(model.getSourceRowsRead()));
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.RowsOut"),
        Long.toString(model.getTargetRowsInserted()));
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Errors"),
        Long.toString(model.getErrors()));
    appendField(
        markdown,
        BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Summary.Bottleneck"),
        model.getBottleneckMessage());
    return markdown.toString().trim();
  }

  public static String formatLayer(String layer) {
    if (Utils.isEmpty(layer)) {
      return "";
    }
    return switch (layer) {
      case "DATA_VAULT" ->
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Layer.DataVault");
      case "BUSINESS_VAULT" ->
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Layer.BusinessVault");
      case "DIMENSIONAL" ->
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Layer.Dimensional");
      default -> layer;
    };
  }

  public static String formatElapsed(UpdateRunWaveModelProgress model) {
    if (model == null) {
      return "";
    }
    long durationMs = model.getDurationMs();
    if (durationMs <= 0L && model.getStartedAt() != null && model.getFinishedAt() == null) {
      durationMs = Math.max(0L, System.currentTimeMillis() - model.getStartedAt().getTime());
    }
    long seconds = Math.max(0L, durationMs / 1000L);
    long minutes = TimeUnit.SECONDS.toMinutes(seconds);
    long remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(minutes);
    return minutes + "m " + remainingSeconds + "s";
  }

  private static void appendField(StringBuilder markdown, String label, String value) {
    if (Utils.isEmpty(value)) {
      return;
    }
    markdown.append("- **").append(label).append(":** ").append(value).append('\n');
  }
}
