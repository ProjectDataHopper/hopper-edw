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
package org.hopper.edw.quality.alert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.quality.disposition.DispositionResult;
import org.hopper.edw.quality.disposition.QualityDispositionMode;
import org.hopper.edw.quality.model.DataQualityReport;

/**
 * Static registry and wiring matrix for quality alert sinks (Phase 2 — no ServiceLoader).
 *
 * <pre>
 * ALERT_ONLY + findings==0 → none
 * ALERT_ONLY + findings&gt;0  → all configured sinks
 * FAIL modes + findings==0 → none
 * FAIL modes + findings&gt;0  → log if listed (blank→log); ops_table only if listed AND alertOnGateFailure
 * </pre>
 */
public final class QualityAlertSupport {

  public static final String DEFAULT_SINK_ID = LogQualityAlertSink.ID;

  private static final Map<String, IQualityAlertSink> REGISTRY = buildRegistry();

  private QualityAlertSupport() {}

  private static Map<String, IQualityAlertSink> buildRegistry() {
    Map<String, IQualityAlertSink> map = new LinkedHashMap<>();
    IQualityAlertSink log = new LogQualityAlertSink();
    IQualityAlertSink ops = new OpsTableQualityAlertSink();
    map.put(log.id(), log);
    map.put(ops.id(), ops);
    return Map.copyOf(map);
  }

  /** Registered sink ids (immutable). */
  public static Map<String, IQualityAlertSink> registry() {
    return REGISTRY;
  }

  /**
   * Parse comma/semicolon-separated sink ids. Blank/null → {@code log}. Unknown tokens are kept
   * (resolved later as no-ops with a log warning).
   */
  public static List<String> parseSinkIds(String alertSinks) {
    if (Utils.isEmpty(alertSinks) || alertSinks.trim().isEmpty()) {
      return List.of(DEFAULT_SINK_ID);
    }
    List<String> ids = new ArrayList<>();
    for (String part : alertSinks.split("[,;]")) {
      if (part == null) {
        continue;
      }
      String id = part.trim().toLowerCase();
      if (!id.isEmpty() && !ids.contains(id)) {
        ids.add(id);
      }
    }
    if (ids.isEmpty()) {
      return List.of(DEFAULT_SINK_ID);
    }
    return ids;
  }

  /**
   * Select sinks to invoke for the disposition matrix. Returns empty when {@code findingCount ==
   * 0}.
   */
  public static List<IQualityAlertSink> resolveSinks(
      QualityDispositionMode mode,
      int findingCount,
      String alertSinks,
      boolean alertOnGateFailure) {
    if (findingCount <= 0) {
      return List.of();
    }
    QualityDispositionMode effective =
        mode != null ? mode : QualityDispositionMode.FAIL_ON_BLOCKING;
    List<String> configured = parseSinkIds(alertSinks);
    List<IQualityAlertSink> sinks = new ArrayList<>();

    if (effective == QualityDispositionMode.ALERT_ONLY) {
      for (String id : configured) {
        IQualityAlertSink sink = REGISTRY.get(id);
        if (sink != null) {
          sinks.add(sink);
        }
      }
      return sinks;
    }

    // FAIL_ON_BLOCKING / FAIL_ON_WARNINGS: log when listed (blank→log); ops_table only if flagged.
    for (String id : configured) {
      if (LogQualityAlertSink.ID.equals(id)) {
        IQualityAlertSink sink = REGISTRY.get(id);
        if (sink != null) {
          sinks.add(sink);
        }
      } else if (OpsTableQualityAlertSink.ID.equals(id) && alertOnGateFailure) {
        IQualityAlertSink sink = REGISTRY.get(id);
        if (sink != null) {
          sinks.add(sink);
        }
      }
    }
    return sinks;
  }

  /**
   * Apply the sink matrix and publish to each selected sink. Swallows nothing — sink failures
   * propagate as {@link HopException}. Unknown sink ids are logged and skipped.
   */
  public static void publish(
      QualityAlertContext context, String alertSinks, boolean alertOnGateFailure)
      throws HopException {
    if (context == null || context.getReport() == null) {
      return;
    }
    DataQualityReport report = context.getReport();
    QualityDispositionMode mode = context.getMode();
    DispositionResult disposition = context.getDisposition();
    ILogChannel log = context.getLog();

    List<String> configured = parseSinkIds(alertSinks);
    // Warn about unknown ids once (even when findingCount==0 we skip work but can still note
    // config).
    for (String id : configured) {
      if (!REGISTRY.containsKey(id) && log != null) {
        log.logBasic("Unknown quality alert sink id ignored: " + id);
      }
    }

    List<IQualityAlertSink> sinks =
        resolveSinks(mode, report.getFindingCount(), alertSinks, alertOnGateFailure);
    if (sinks.isEmpty()) {
      return;
    }

    if (log != null) {
      log.logBasic(
          "Publishing quality alert to "
              + sinks.size()
              + " sink(s) for runId="
              + report.getRunId()
              + " mode="
              + (mode != null ? mode.name() : "")
              + " findings="
              + report.getFindingCount()
              + (disposition != null ? " failed=" + disposition.isFailed() : ""));
    }

    for (IQualityAlertSink sink : sinks) {
      sink.publish(context);
    }
  }
}
