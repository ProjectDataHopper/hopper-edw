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

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.simple.HSimplePresentation;

/** Builds on-the-fly EDW dashboards from Hop execution information. */
public final class EdwPresentationDashboards {

  public static final String PROJECT_OVERVIEW_NAME = "Project overview";
  public static final int DEFAULT_LIMIT = 40;

  private EdwPresentationDashboards() {}

  public static HGeneratedCatalog projectOverview(
      IHopMetadataProvider hopMetadata, IVariables variables) throws Exception {
    String projectName = resolveProjectName(variables);
    List<RowMetaAndData> rows =
        ExecutionDashboardRows.collect(hopMetadata, variables, null, DEFAULT_LIMIT);
    HSimplePresentation builder =
        HSimplePresentation.dashboard(PROJECT_OVERVIEW_NAME)
            .description("Generated from Hop execution information")
            .addTitle("Project overview");
    if (StringUtils.isNotBlank(projectName) && !"Project".equalsIgnoreCase(projectName)) {
      builder.addNote("Project: " + projectName);
    }
    addRunTiles(
        builder,
        rows,
        "No execution information was found. Configure an execution information location on a run configuration, then run a pipeline or workflow.");
    return builder.build();
  }

  public static HGeneratedCatalog pipelineOrWorkflow(
      IHopMetadataProvider hopMetadata, IVariables variables, String filenameOrName)
      throws Exception {
    String title =
        StringUtils.isBlank(filenameOrName) ? "Pipeline or workflow" : shortName(filenameOrName);
    List<RowMetaAndData> rows =
        ExecutionDashboardRows.collect(hopMetadata, variables, filenameOrName, DEFAULT_LIMIT);
    HSimplePresentation builder =
        HSimplePresentation.dashboard(title).description("Generated run history").addTitle(title);
    addRunTiles(
        builder,
        rows,
        "No execution information was found for this file. Run it with an execution information location configured.");
    return builder.build();
  }

  static String resolveProjectName(IVariables variables) {
    if (variables == null) {
      return "Project";
    }
    String name = Const.NVL(variables.getVariable("HOP_PROJECT_NAME"), "");
    if (StringUtils.isBlank(name)) {
      name = Const.NVL(variables.getVariable("PROJECT_NAME"), "");
    }
    return StringUtils.isBlank(name) ? "Project" : name;
  }

  static String shortName(String filenameOrName) {
    String value = filenameOrName.replace('\\', '/');
    int slash = value.lastIndexOf('/');
    return slash >= 0 ? value.substring(slash + 1) : value;
  }

  private static void addRunTiles(
      HSimplePresentation builder, List<RowMetaAndData> rows, String emptyMessage) {
    if (rows == null || rows.isEmpty()) {
      builder.addNote(emptyMessage);
      return;
    }
    builder
        .addTrend(
            "Run duration (ms)",
            rows,
            ExecutionDashboardRows.COL_STARTED,
            ExecutionDashboardRows.COL_DURATION_MS)
        .addComparison(
            "Runs by status",
            rows,
            ExecutionDashboardRows.COL_STATUS,
            null,
            ExecutionDashboardRows.COL_COUNT)
        .addTable(
            "Recent executions",
            rows,
            ExecutionDashboardRows.COL_NAME,
            ExecutionDashboardRows.COL_TYPE,
            ExecutionDashboardRows.COL_STATUS,
            ExecutionDashboardRows.COL_DURATION_MS,
            ExecutionDashboardRows.COL_STARTED);
  }
}
