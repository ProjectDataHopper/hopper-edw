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
package org.hopper.edw.datavault.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.performance.PerformanceSnapShot;

/**
 * Maps Hop transform performance snapshots to in-memory chart rows (elapsed seconds, transform,
 * value). Caps series so the legend stays readable.
 */
public final class PerformanceSnapshotRows {

  public static final String COL_ELAPSED_S = "elapsed_s";
  public static final String COL_TRANSFORM = "transform";
  public static final String COL_VALUE = "value";
  public static final int MAX_SERIES = 12;
  public static final String OTHER_SERIES = "Other";

  public enum Metric {
    ROWS_PER_SECOND,
    LINES_READ,
    LINES_WRITTEN,
    LINES_INPUT,
    LINES_OUTPUT,
    LINES_UPDATED,
    LINES_REJECTED,
    ERRORS,
    INPUT_BUFFER,
    OUTPUT_BUFFER
  }

  private PerformanceSnapshotRows() {}

  /**
   * Prefer the engine's snapshot map. {@link EngineMetrics#getComponentPerformanceSnapshots()}
   * often stays empty because Hop looks up {@code "name.copy"} as a transform name.
   */
  public static List<RowMetaAndData> from(IPipelineEngine<?> engine, Metric metric) {
    if (engine instanceof Pipeline pipeline) {
      Map<String, List<PerformanceSnapShot>> snaps = pipeline.getTransformPerformanceSnapShots();
      if (snaps != null && !snaps.isEmpty()) {
        return from(keyedByTransformLabel(snaps), metric);
      }
    }
    return from(engine != null ? engine.getEngineMetrics() : null, metric);
  }

  public static List<RowMetaAndData> from(EngineMetrics metrics, Metric metric) {
    if (metrics == null || metrics.getComponentPerformanceSnapshots() == null) {
      return List.of();
    }
    Map<String, List<PerformanceSnapShot>> byTransform = new LinkedHashMap<>();
    for (Map.Entry<IEngineComponent, List<PerformanceSnapShot>> entry :
        metrics.getComponentPerformanceSnapshots().entrySet()) {
      IEngineComponent component = entry.getKey();
      String name = componentLabel(component);
      if (StringUtils.isBlank(name)) {
        continue;
      }
      byTransform.put(name, entry.getValue());
    }
    return from(byTransform, metric);
  }

  static Map<String, List<PerformanceSnapShot>> keyedByTransformLabel(
      Map<String, List<PerformanceSnapShot>> snapshots) {
    Map<String, List<PerformanceSnapShot>> byName = new LinkedHashMap<>();
    if (snapshots == null) {
      return byName;
    }
    for (Map.Entry<String, List<PerformanceSnapShot>> entry : snapshots.entrySet()) {
      List<PerformanceSnapShot> list = entry.getValue();
      String label = null;
      if (list != null) {
        for (PerformanceSnapShot snap : list) {
          if (snap != null && StringUtils.isNotBlank(snap.getComponentName())) {
            label = componentLabel(snap.getComponentName(), snap.getCopyNr());
            break;
          }
        }
      }
      if (StringUtils.isBlank(label)) {
        label = displayNameFromKey(entry.getKey());
      }
      if (StringUtils.isBlank(label) || list == null) {
        continue;
      }
      byName.put(label, list);
    }
    return byName;
  }

  static String displayNameFromKey(String key) {
    if (key == null) {
      return "";
    }
    int lastDot = key.lastIndexOf('.');
    if (lastDot <= 0 || lastDot >= key.length() - 1) {
      return key;
    }
    String suffix = key.substring(lastDot + 1);
    for (int i = 0; i < suffix.length(); i++) {
      if (!Character.isDigit(suffix.charAt(i))) {
        return key;
      }
    }
    String name = key.substring(0, lastDot);
    int copy = Integer.parseInt(suffix);
    return copy > 0 ? name + "." + copy : name;
  }

  public static List<RowMetaAndData> from(
      Map<String, List<PerformanceSnapShot>> snapshotsByTransform, Metric metric) {
    Metric chosen = metric != null ? metric : Metric.ROWS_PER_SECOND;
    if (snapshotsByTransform == null || snapshotsByTransform.isEmpty()) {
      return List.of();
    }
    long t0 = Long.MAX_VALUE;
    Map<String, Double> totals = new HashMap<>();
    for (Map.Entry<String, List<PerformanceSnapShot>> entry : snapshotsByTransform.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      double total = 0;
      for (PerformanceSnapShot snap : entry.getValue()) {
        if (snap == null || snap.getDate() == null) {
          continue;
        }
        t0 = Math.min(t0, snap.getDate().getTime());
        total += Math.abs(metricValue(snap, chosen));
      }
      totals.put(entry.getKey(), total);
    }
    if (t0 == Long.MAX_VALUE) {
      return List.of();
    }
    List<String> ranked = new ArrayList<>(totals.keySet());
    ranked.sort(Comparator.comparingDouble((String n) -> totals.getOrDefault(n, 0d)).reversed());
    Map<String, String> seriesAlias = new HashMap<>();
    for (int i = 0; i < ranked.size(); i++) {
      seriesAlias.put(ranked.get(i), i < MAX_SERIES ? ranked.get(i) : OTHER_SERIES);
    }

    RowMeta meta = rowMeta();
    Map<String, double[]> folded = new LinkedHashMap<>();
    Map<String, String> seriesByKey = new LinkedHashMap<>();
    Map<String, Long> elapsedByKey = new LinkedHashMap<>();
    for (Map.Entry<String, List<PerformanceSnapShot>> entry : snapshotsByTransform.entrySet()) {
      String series = seriesAlias.getOrDefault(entry.getKey(), entry.getKey());
      if (entry.getValue() == null) {
        continue;
      }
      for (PerformanceSnapShot snap : entry.getValue()) {
        if (snap == null || snap.getDate() == null) {
          continue;
        }
        long elapsed = Math.max(0L, (snap.getDate().getTime() - t0) / 1000L);
        String key = elapsed + "\0" + series;
        double value = metricValue(snap, chosen);
        double[] acc = folded.get(key);
        if (acc == null) {
          folded.put(key, new double[] {value});
          seriesByKey.put(key, series);
          elapsedByKey.put(key, elapsed);
        } else {
          acc[0] += value;
        }
      }
    }
    List<RowMetaAndData> rows = new ArrayList<>(folded.size());
    for (String key : folded.keySet()) {
      rows.add(
          new RowMetaAndData(
              meta, elapsedByKey.get(key), seriesByKey.get(key), folded.get(key)[0]));
    }
    return rows;
  }

  public static double metricValue(PerformanceSnapShot snap, Metric metric) {
    if (snap == null) {
      return 0;
    }
    Metric chosen = metric != null ? metric : Metric.ROWS_PER_SECOND;
    return switch (chosen) {
      case ROWS_PER_SECOND -> rowsPerSecond(snap);
      case LINES_READ -> snap.getLinesRead();
      case LINES_WRITTEN -> snap.getLinesWritten();
      case LINES_INPUT -> snap.getLinesInput();
      case LINES_OUTPUT -> snap.getLinesOutput();
      case LINES_UPDATED -> snap.getLinesUpdated();
      case LINES_REJECTED -> snap.getLinesRejected();
      case ERRORS -> snap.getErrors();
      case INPUT_BUFFER -> snap.getInputBufferSize();
      case OUTPUT_BUFFER -> snap.getOutputBufferSize();
    };
  }

  /**
   * Same row accounting as Hop's Metrics speed column ({@code TransformStatus}): max of inbound vs
   * outbound rows in the snapshot interval, not only read+input (generators write, they do not
   * read).
   */
  static double rowsPerSecond(PerformanceSnapShot snap) {
    long inProc = Math.max(snap.getLinesInput(), snap.getLinesRead());
    long outProc =
        Math.max(
            snap.getLinesOutput() + snap.getLinesUpdated(),
            snap.getLinesWritten() + snap.getLinesRejected());
    long rows = Math.max(inProc, outProc);
    long intervalMs = snap.getTimeDifference();
    if (intervalMs <= 0 || rows <= 0) {
      return 0.0;
    }
    return rows * 1000.0 / intervalMs;
  }

  public static String fingerprint(List<RowMetaAndData> rows) {
    if (rows == null || rows.isEmpty()) {
      return "0";
    }
    long sum = 0;
    for (RowMetaAndData row : rows) {
      try {
        sum += Double.doubleToLongBits(row.getNumber(COL_VALUE, 0));
      } catch (Exception ignored) {
        // skip
      }
    }
    return rows.size() + ":" + sum;
  }

  static String componentLabel(IEngineComponent component) {
    if (component == null) {
      return "";
    }
    return componentLabel(component.getName(), component.getCopyNr());
  }

  static String componentLabel(String name, int copy) {
    String n = StringUtils.defaultString(name);
    return copy > 0 ? n + "." + copy : n;
  }

  private static RowMeta rowMeta() {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaInteger(COL_ELAPSED_S));
    meta.addValueMeta(new ValueMetaString(COL_TRANSFORM));
    meta.addValueMeta(new ValueMetaNumber(COL_VALUE));
    return meta;
  }
}
