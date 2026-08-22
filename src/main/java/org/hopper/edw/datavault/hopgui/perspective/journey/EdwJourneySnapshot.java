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
package org.hopper.edw.datavault.hopgui.perspective.journey;

import java.util.List;
import org.hopper.edw.catalog.model.RecordDefinitionKey;

/**
 * Headless picture of one resource definition group's EDW journey. Built off the UI thread and
 * painted into the perspective tree.
 */
public record EdwJourneySnapshot(
    String groupName,
    String catalogConnection,
    List<ModelRef> sourceModels,
    List<CatalogFeed> catalogFeeds,
    List<ModelRef> dataVaultModels,
    List<ModelRef> businessVaultModels,
    List<ModelRef> dimensionalModels,
    List<String> catalogVersionTags,
    List<WorkflowRef> workflows,
    List<OutputRef> reports,
    List<OutputRef> executionMaps,
    List<OutputRef> lineageViews,
    List<String> warnings) {

  public static final String MODEL_TYPE_SOURCE = "SOURCE_MODEL";
  public static final String MODEL_TYPE_DATA_VAULT = "DATA_VAULT_MODEL";
  public static final String MODEL_TYPE_BUSINESS_VAULT = "BUSINESS_VAULT_MODEL";
  public static final String MODEL_TYPE_DIMENSIONAL = "DIMENSIONAL_MODEL";

  public static final String OUTPUT_REPORTS = "reports";
  public static final String OUTPUT_EXECUTION_MAPS = "execution-maps";
  public static final String OUTPUT_LINEAGE_VIEWS = "lineage-views";

  public EdwJourneySnapshot {
    sourceModels = copy(sourceModels);
    catalogFeeds = copy(catalogFeeds);
    dataVaultModels = copy(dataVaultModels);
    businessVaultModels = copy(businessVaultModels);
    dimensionalModels = copy(dimensionalModels);
    catalogVersionTags = copy(catalogVersionTags);
    workflows = copy(workflows);
    reports = copy(reports);
    executionMaps = copy(executionMaps);
    lineageViews = copy(lineageViews);
    warnings = copy(warnings);
  }

  public static EdwJourneySnapshot empty() {
    return new EdwJourneySnapshot(
        null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of());
  }

  public boolean hasGroup() {
    return groupName != null && !groupName.isBlank();
  }

  public record ModelRef(
      String storedPath, String displayName, String modelType, List<String> tableNames) {
    public ModelRef {
      tableNames = copy(tableNames);
    }
  }

  public record CatalogFeed(
      String catalogConnection,
      RecordDefinitionKey key,
      String originFilename,
      String originModelType) {}

  public record WorkflowRef(String storedPath, String workflowName, List<ActionRef> actions) {
    public WorkflowRef {
      actions = copy(actions);
    }
  }

  public record ActionRef(String name, String type) {}

  public record OutputRef(String storedPath, String displayName) {}

  private static <T> List<T> copy(List<T> list) {
    return list != null ? List.copyOf(list) : List.of();
  }
}
