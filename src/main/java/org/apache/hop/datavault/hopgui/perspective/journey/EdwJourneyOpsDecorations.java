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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.ModelLoadSummary;
import org.apache.hop.quality.history.DataQualityHistoryReader.QualityRunSummary;

/** Short last-run suffixes for journey tree labels. */
public final class EdwJourneyOpsDecorations {

  private static final SimpleDateFormat TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  private EdwJourneyOpsDecorations() {}

  public static String decorate(String label, String extra) {
    if (Utils.isEmpty(extra)) {
      return label;
    }
    return Const.NVL(label, "") + "  ·  " + extra;
  }

  public static String harvest(HarvestRunSummary run) {
    if (run == null) {
      return null;
    }
    String status = Const.NVL(run.status(), "ran");
    if (run.changeCount() != null && run.changeCount() > 0) {
      return status + ", " + run.changeCount() + " changes";
    }
    return status;
  }

  public static String quality(QualityRunSummary run) {
    if (run == null) {
      return null;
    }
    if (run.blockingCount() != null && run.blockingCount() > 0) {
      return run.blockingCount() + " blocking";
    }
    if (Boolean.FALSE.equals(run.success())) {
      return "failed";
    }
    if (run.findingCount() != null && run.findingCount() > 0) {
      return run.findingCount() + " findings";
    }
    return "PASS";
  }

  public static String load(LoadOverviewSummary summary) {
    if (summary == null) {
      return null;
    }
    String duration = formatDuration(summary.durationMs());
    if (Boolean.FALSE.equals(summary.success())
        || (summary.errors() != null && summary.errors() > 0)) {
      return "failed" + (duration != null ? ", " + duration : "");
    }
    return duration != null ? duration : "ok";
  }

  public static String modelLoad(ModelLoadSummary summary) {
    if (summary == null) {
      return null;
    }
    String duration = formatDuration(summary.durationMs());
    if (Boolean.FALSE.equals(summary.success())
        || (summary.errors() != null && summary.errors() > 0)) {
      return "failed" + (duration != null ? ", " + duration : "");
    }
    return duration != null ? duration : "ok";
  }

  public static String formatWhen(Date date) {
    if (date == null) {
      return null;
    }
    return TIME.format(date);
  }

  public static String formatDuration(Long durationMs) {
    if (durationMs == null || durationMs < 0) {
      return null;
    }
    long seconds = durationMs / 1000L;
    if (seconds < 60) {
      return seconds + "s";
    }
    long minutes = seconds / 60L;
    long rem = seconds % 60L;
    if (minutes < 60) {
      return rem == 0 ? minutes + "m" : minutes + "m " + rem + "s";
    }
    long hours = minutes / 60L;
    long minRem = minutes % 60L;
    return minRem == 0 ? hours + "h" : hours + "h " + minRem + "m";
  }
}
