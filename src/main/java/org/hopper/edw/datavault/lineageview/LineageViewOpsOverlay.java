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
package org.hopper.edw.datavault.lineageview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.lineageview.backend.HopExportFacet;
import org.hopper.edw.datavault.lineageview.backend.HopOpsFacet;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metrics.LoadRunDurationMetricsLoader;
import org.hopper.edw.datavault.metrics.LoadRunDurationSnapshot;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * OPS last-load overlay for a lineage graph. Groups nodes by {@code (modelName, dv|bv|dm)} and
 * never reads Marquez {@code latestRun.durationMs}.
 */
@Value
public class LineageViewOpsOverlay {

  public static final LineageViewOpsOverlay EMPTY =
      new LineageViewOpsOverlay(Map.of(), false, null);

  Map<GroupKey, LoadRunDurationSnapshot> snapshots;
  boolean suppressed;
  String statusNote;

  @FunctionalInterface
  public interface SnapshotLoader {
    LoadRunDurationSnapshot load(String modelName, String modelType, List<String> tableNames);
  }

  public record GroupKey(String modelName, String opsType) {}

  public record OpsIdentity(
      String modelName, String opsType, String logicalName, String physicalName) {}

  public static LineageViewOpsOverlay empty() {
    return EMPTY;
  }

  public static LineageViewOpsOverlay load(
      LineageGraph graph, IHopMetadataProvider metadataProvider, IVariables variables) {
    return load(
        graph,
        (modelName, modelType, tableNames) ->
            LoadRunDurationMetricsLoader.load(
                modelName, modelType, tableNames, metadataProvider, variables));
  }

  public static LineageViewOpsOverlay load(LineageGraph graph, SnapshotLoader loader) {
    if (graph == null || loader == null) {
      return empty();
    }
    Map<GroupKey, Set<String>> tablesByGroup = new LinkedHashMap<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      OpsIdentity identity = identityOf(node);
      if (identity == null) {
        continue;
      }
      GroupKey key = new GroupKey(identity.modelName(), identity.opsType());
      Set<String> tables = tablesByGroup.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
      addTableName(tables, identity.logicalName());
      addTableName(tables, identity.physicalName());
    }
    if (tablesByGroup.isEmpty()) {
      return empty();
    }

    Map<GroupKey, LoadRunDurationSnapshot> loaded = new LinkedHashMap<>();
    for (Map.Entry<GroupKey, Set<String>> entry : tablesByGroup.entrySet()) {
      List<String> tableNames = List.copyOf(entry.getValue());
      LoadRunDurationSnapshot snapshot =
          loader.load(entry.getKey().modelName(), entry.getKey().opsType(), tableNames);
      if (snapshot == null) {
        continue;
      }
      if (snapshot.getStatus() == LoadRunDurationSnapshot.Status.NO_DATABASE
          || snapshot.getStatus() == LoadRunDurationSnapshot.Status.ERROR) {
        return new LineageViewOpsOverlay(Map.of(), true, "OPS unavailable");
      }
      loaded.put(entry.getKey(), snapshot);
    }
    return new LineageViewOpsOverlay(Map.copyOf(loaded), false, null);
  }

  public LineageViewOpsBadge badgeFor(LineageNode node) {
    if (suppressed || node == null) {
      return null;
    }
    OpsIdentity identity = identityOf(node);
    LoadRunDurationSnapshot snapshot = identity != null ? snapshots.get(groupKey(identity)) : null;
    LineageViewOpsBadge ops = badgeFromSnapshot(snapshot, identity);
    if (ops != null) {
      return ops;
    }
    return badgeFromHopOps(node.getHopOps());
  }

  public static OpsIdentity identityOf(LineageNode node) {
    if (node == null) {
      return null;
    }
    HopExportFacet export = node.getHopExport();
    if (export != null) {
      String opsType = opsTypeForLayer(export.getModelLayer());
      if (opsType != null && !Utils.isEmpty(export.getModelName())) {
        return new OpsIdentity(
            export.getModelName(), opsType, export.getLogicalName(), export.getPhysicalTableName());
      }
    }
    if (node.getKind() == LineageNodeKind.JOB) {
      return parseJobName(node.getName());
    }
    return null;
  }

  public static String opsTypeForLayer(String modelLayer) {
    if (Utils.isEmpty(modelLayer)) {
      return null;
    }
    return switch (modelLayer.trim().toUpperCase()) {
      case "DV" -> GeneratedPipelineMetadataConstants.MODEL_TYPE_DV;
      case "BV" -> GeneratedPipelineMetadataConstants.MODEL_TYPE_BV;
      case "DM" -> GeneratedPipelineMetadataConstants.MODEL_TYPE_DM;
      default -> null;
    };
  }

  static OpsIdentity parseJobName(String jobName) {
    if (Utils.isEmpty(jobName)) {
      return null;
    }
    String[] parts = jobName.split("/");
    if (parts.length < 3) {
      return null;
    }
    String opsType = opsTypeForLayer(parts[0]);
    if (opsType == null || Utils.isEmpty(parts[1]) || Utils.isEmpty(parts[2])) {
      return null;
    }
    return new OpsIdentity(parts[1], opsType, parts[2], null);
  }

  static void addTableName(Set<String> tables, String name) {
    if (Utils.isEmpty(name)) {
      return;
    }
    tables.add(name);
  }

  private static GroupKey groupKey(OpsIdentity identity) {
    return new GroupKey(identity.modelName(), identity.opsType());
  }

  private static LineageViewOpsBadge badgeFromSnapshot(
      LoadRunDurationSnapshot snapshot, OpsIdentity identity) {
    if (snapshot == null
        || snapshot.getStatus() != LoadRunDurationSnapshot.Status.LOADED
        || identity == null) {
      return null;
    }
    long[] durations = firstDurations(snapshot, lookupKeys(identity));
    if (durations == null) {
      return null;
    }
    long last = lastPositive(durations);
    if (last <= 0L) {
      return null;
    }
    long average = averagePriorPositive(durations);
    boolean slow = average > 0L && last > average * 2L;
    String lastLabel = formatDuration(last);
    String label =
        average > 0L && average != last ? lastLabel + " / " + formatDuration(average) : lastLabel;
    String tooltip =
        average > 0L
            ? "Last load " + lastLabel + " (OPS). Recent average " + formatDuration(average) + "."
            : "Last load " + lastLabel + " (OPS).";
    return new LineageViewOpsBadge(last, average, false, slow, label, tooltip);
  }

  private static LineageViewOpsBadge badgeFromHopOps(HopOpsFacet hopOps) {
    if (hopOps == null || hopOps.getDurationMs() == null || hopOps.getDurationMs() <= 0L) {
      return null;
    }
    long last = hopOps.getDurationMs();
    String lastLabel = formatDuration(last);
    return new LineageViewOpsBadge(
        last,
        0L,
        true,
        false,
        lastLabel + " export",
        "Last load " + lastLabel + " as of last lineage export (stale).");
  }

  private static List<String> lookupKeys(OpsIdentity identity) {
    List<String> keys = new ArrayList<>();
    addLookupKey(keys, identity.logicalName());
    addLookupKey(keys, identity.physicalName());
    return keys;
  }

  private static void addLookupKey(List<String> keys, String name) {
    if (Utils.isEmpty(name) || keys.contains(name)) {
      return;
    }
    keys.add(name);
  }

  private static long[] firstDurations(LoadRunDurationSnapshot snapshot, List<String> keys) {
    for (String key : keys) {
      long[] durations = snapshot.getDurationsByElement().get(key);
      if (durations != null && lastPositive(durations) > 0L) {
        return durations;
      }
    }
    return null;
  }

  private static long lastPositive(long[] durations) {
    if (durations == null || durations.length == 0) {
      return 0L;
    }
    for (int i = durations.length - 1; i >= 0; i--) {
      if (durations[i] > 0L) {
        return durations[i];
      }
    }
    return 0L;
  }

  private static long averagePriorPositive(long[] durations) {
    if (durations == null || durations.length < 2) {
      return 0L;
    }
    int lastIndex = -1;
    for (int i = durations.length - 1; i >= 0; i--) {
      if (durations[i] > 0L) {
        lastIndex = i;
        break;
      }
    }
    long sum = 0L;
    int count = 0;
    for (int i = 0; i < lastIndex; i++) {
      if (durations[i] > 0L) {
        sum += durations[i];
        count++;
      }
    }
    return count == 0 ? 0L : sum / count;
  }

  static String formatDuration(long durationMs) {
    if (durationMs <= 0L) {
      return "0s";
    }
    long totalSeconds = durationMs / 1000L;
    long hours = totalSeconds / 3600L;
    long minutes = (totalSeconds % 3600L) / 60L;
    long seconds = totalSeconds % 60L;
    if (hours > 0L) {
      return String.format(Locale.ROOT, "%dh %dm %ds", hours, minutes, seconds);
    }
    if (minutes > 0L) {
      return String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
    }
    return String.format(Locale.ROOT, "%ds", seconds);
  }
}
