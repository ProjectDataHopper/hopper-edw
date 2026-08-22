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
package org.hopper.edw.datavault.metrics;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves OPS database and catalog settings for workflow overview publishing. */
public final class WorkflowOverviewMetricsResolver {

  public record ResolvedOverviewMetrics(
      String catalogConnectionName,
      String targetDatabaseName,
      String operationsSchema,
      boolean publishCatalogDefinitions,
      boolean publishDatabaseRows,
      boolean autoCreateTables) {}

  private WorkflowOverviewMetricsResolver() {}

  public static ResolvedOverviewMetrics resolve(
      String profileName,
      String actionCatalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(profileName)) {
      throw new HopException("Execution metrics profile is required for workflow load overview");
    }
    ExecutionMetricsProfileMeta profile =
        metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).load(profileName);
    if (profile == null) {
      throw new HopException("Execution metrics profile not found: " + profileName);
    }
    String targetDatabase = resolveValue(profile.getTargetDatabaseConnection(), variables);
    if (Utils.isEmpty(targetDatabase)) {
      throw new HopException(
          "Target database connection is required on execution metrics profile: " + profileName);
    }
    String catalogConnection =
        firstNonEmpty(
            resolveValue(profile.getDataCatalogConnection(), variables),
            resolveValue(actionCatalogConnection, variables));
    return new ResolvedOverviewMetrics(
        catalogConnection,
        targetDatabase,
        profile.getOperationsSchemaOrDefault(),
        profile.isPublishCatalogDefinitions(),
        profile.isPublishDatabaseRows(),
        profile.isAutoCreateTables());
  }

  private static String resolveValue(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String firstNonEmpty(String first, String second) {
    if (!Utils.isEmpty(first)) {
      return first;
    }
    return second;
  }
}
