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
package org.apache.hop.quality.alert;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.quality.disposition.DispositionResult;
import org.apache.hop.quality.disposition.QualityDispositionMode;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityReport;
import org.apache.hop.quality.model.QualitySeverity;

/** Logs a structured {@code QUALITY_ALERT} line plus findings. */
public final class LogQualityAlertSink implements IQualityAlertSink {

  public static final String ID = "log";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void publish(QualityAlertContext context) throws HopException {
    if (context == null || context.getReport() == null) {
      return;
    }
    ILogChannel log = context.getLog();
    if (log == null) {
      return;
    }
    DataQualityReport report = context.getReport();
    DispositionResult disposition = context.getDisposition();
    QualityDispositionMode mode = context.getMode();

    long blocking = report.countBySeverity(QualitySeverity.BLOCKING);
    long warning = report.countBySeverity(QualitySeverity.WARNING);
    long info = report.countBySeverity(QualitySeverity.INFO);

    String header =
        "QUALITY_ALERT runId="
            + report.getRunId()
            + " lifecycle="
            + (report.getLifecycle() != null ? report.getLifecycle().name() : "")
            + " mode="
            + (mode != null ? mode.name() : "")
            + " failed="
            + (disposition != null && disposition.isFailed())
            + " findings="
            + report.getFindingCount()
            + " blocking="
            + blocking
            + " warning="
            + warning
            + " info="
            + info;
    if (disposition != null
        && disposition.getSummary() != null
        && !disposition.getSummary().isBlank()) {
      header = header + " summary=" + disposition.getSummary();
    }
    log.logBasic(header);

    for (DataQualityFinding finding : report.getFindings()) {
      if (finding == null) {
        continue;
      }
      log.logBasic(
          "QUALITY_ALERT finding subject="
              + nullToEmpty(finding.getSubjectKey())
              + " rule="
              + nullToEmpty(finding.getRuleId())
              + " severity="
              + (finding.getSeverity() != null ? finding.getSeverity().name() : "")
              + " field="
              + nullToEmpty(finding.getFieldName())
              + " message="
              + nullToEmpty(finding.getMessage()));
    }
  }

  private static String nullToEmpty(String value) {
    return value != null ? value : "";
  }
}
