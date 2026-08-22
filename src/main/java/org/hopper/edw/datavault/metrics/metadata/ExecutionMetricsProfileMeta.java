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
package org.hopper.edw.datavault.metrics.metadata;

import lombok.Getter;
import lombok.Setter;
import org.hopper.edw.datavault.metrics.LoadRunInsightEngine;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Reusable configuration for load-run metrics collection, catalog publishing, and insight rules on
 * DV/BV/DM update orchestrations.
 */
@HopMetadata(
    key = "execution-metrics-profile",
    name = "i18n::ExecutionMetricsProfileMeta.name",
    description = "i18n::ExecutionMetricsProfileMeta.description",
    image = "execution-metrics-profile.svg",
    documentationUrl = "/metadata-types/execution-metrics-profile.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class ExecutionMetricsProfileMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  @HopMetadataProperty private boolean enabled = true;

  @HopMetadataProperty private String metricsOutputFolder;

  /** Data catalog connection for operations record definitions (load_run, etc.). */
  @HopMetadataProperty private String dataCatalogConnection;

  /** Optional override; empty inherits the model target database connection. */
  @HopMetadataProperty private String targetDatabaseConnection;

  /** Empty = connection default schema/database; set only when isolation is desired. */
  @HopMetadataProperty private String operationsSchema = "";

  @HopMetadataProperty private boolean autoCreateTables = true;

  @HopMetadataProperty private boolean publishCatalogDefinitions = true;

  @HopMetadataProperty private boolean publishDatabaseRows = true;

  @HopMetadataProperty
  private long dimLookupPreloadRatioThreshold = LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD;

  @HopMetadataProperty
  private long targetReadRatioThreshold = LoadRunInsightEngine.DEFAULT_TARGET_READ_RATIO_THRESHOLD;

  @HopMetadataProperty
  private long sortRowsRiskThreshold = LoadRunInsightEngine.DEFAULT_SORT_ROWS_RISK_THRESHOLD;

  @HopMetadataProperty
  private long highTransformDurationMs = LoadRunInsightEngine.DEFAULT_HIGH_TRANSFORM_DURATION_MS;

  public ExecutionMetricsProfileMeta() {
    super();
  }

  public ExecutionMetricsProfileMeta(String name) {
    super(name);
  }

  public String getOperationsSchemaOrDefault() {
    if (operationsSchema == null || operationsSchema.isBlank()) {
      return "";
    }
    return operationsSchema.trim();
  }
}
