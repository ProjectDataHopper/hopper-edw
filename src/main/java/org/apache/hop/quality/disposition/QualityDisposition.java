/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.quality.disposition;

import org.apache.hop.quality.model.DataQualityReport;
import org.apache.hop.quality.model.QualitySeverity;

/** Maps a {@link DataQualityReport} to a pass/fail disposition without measuring data. */
public final class QualityDisposition {

  private QualityDisposition() {}

  public static DispositionResult apply(DataQualityReport report, QualityDispositionMode mode) {
    if (report == null) {
      return DispositionResult.fail(1, "No data quality report available");
    }
    QualityDispositionMode effective =
        mode != null ? mode : QualityDispositionMode.FAIL_ON_BLOCKING;

    if (report.hasInfraErrors()) {
      return DispositionResult.fail(
          report.getInfraErrors().size(),
          "Data quality measure hit infrastructure errors: "
              + String.join("; ", report.getInfraErrors()));
    }

    long blocking = report.countBySeverity(QualitySeverity.BLOCKING);
    long warnings = report.countBySeverity(QualitySeverity.WARNING);
    long info = report.countBySeverity(QualitySeverity.INFO);
    String stats =
        "findings="
            + report.getFindingCount()
            + " (blocking="
            + blocking
            + ", warning="
            + warnings
            + ", info="
            + info
            + ")";

    return switch (effective) {
      case ALERT_ONLY -> DispositionResult.pass("Alert-only disposition; " + stats);
      case FAIL_ON_WARNINGS -> {
        if (blocking > 0 || warnings > 0) {
          yield DispositionResult.fail(
              (int) (blocking + warnings), "Quality gate failed (blocking or warnings); " + stats);
        }
        yield DispositionResult.pass("Quality gate passed; " + stats);
      }
      case FAIL_ON_BLOCKING -> {
        if (blocking > 0) {
          yield DispositionResult.fail(
              (int) blocking, "Quality gate failed (blocking findings); " + stats);
        }
        yield DispositionResult.pass("Quality gate passed; " + stats);
      }
    };
  }
}
