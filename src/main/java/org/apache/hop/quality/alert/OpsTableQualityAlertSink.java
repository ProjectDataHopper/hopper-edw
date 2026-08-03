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
import org.apache.hop.core.util.Utils;
import org.apache.hop.quality.history.DataQualityHistoryPublisher;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishContext;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishResult;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishStatus;
import org.apache.hop.quality.model.DataQualityReport;

/**
 * Ensures a quality_run exists (publish if missing / skip if present — never duplicates findings),
 * then inserts a {@code quality_alert} disposition header row.
 */
public final class OpsTableQualityAlertSink implements IQualityAlertSink {

  public static final String ID = "ops_table";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void publish(QualityAlertContext context) throws HopException {
    if (context == null || context.getReport() == null) {
      throw new HopException("Quality alert context/report is required for ops_table sink");
    }
    PublishContext publishContext = context.getPublishContext();
    if (publishContext == null || Utils.isEmpty(publishContext.targetDatabaseName())) {
      throw new HopException(
          "ops_table alert sink requires a history database (set History database or "
              + DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_DATABASE
              + ")");
    }
    DataQualityReport report = context.getReport();
    ILogChannel log = context.getLog();

    // Persist run+findings when missing; immutable skip when measure already stored.
    // Never re-insert findings for an existing quality_run_id.
    PublishResult publishResult =
        DataQualityHistoryPublisher.publish(
            log,
            report,
            publishContext,
            context.getLoadId(),
            context.getWorkflowName(),
            context.getWorkflowExecutionId(),
            context.getVariables(),
            context.getMetadataProvider());
    if (publishResult.status() == PublishStatus.FAILED) {
      throw new HopException(
          "Unable to ensure quality history before alert insert: " + publishResult.message());
    }
    if (log != null) {
      log.logBasic(
          "ops_table alert: quality_run "
              + publishResult.status().name()
              + " for runId="
              + report.getRunId()
              + " — "
              + publishResult.message());
    }

    PublishResult alertResult =
        DataQualityHistoryPublisher.insertQualityAlert(
            log,
            report,
            context.getDisposition(),
            context.getMode(),
            publishContext,
            context.getVariables(),
            context.getMetadataProvider());
    if (alertResult.status() == PublishStatus.FAILED) {
      throw new HopException("Unable to insert quality_alert: " + alertResult.message());
    }
  }
}
