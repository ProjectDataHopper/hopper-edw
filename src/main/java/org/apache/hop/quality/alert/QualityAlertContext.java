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
package org.apache.hop.quality.alert;

import lombok.Builder;
import lombok.Getter;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.disposition.DispositionResult;
import org.apache.hop.quality.disposition.QualityDispositionMode;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishContext;
import org.apache.hop.quality.model.DataQualityReport;

/** Inputs for {@link IQualityAlertSink#publish(QualityAlertContext)}. */
@Getter
@Builder
public final class QualityAlertContext {

  private final DataQualityReport report;
  private final DispositionResult disposition;
  private final QualityDispositionMode mode;
  private final ILogChannel log;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  /** Optional; required by {@code ops_table} sink for history DB writes. */
  private final PublishContext publishContext;

  /** Optional correlation fields when the ops_table sink must persist a quality_run first. */
  private final String loadId;

  private final String workflowName;
  private final String workflowExecutionId;
}
