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
package org.hopper.edw.datavault.opsanalytics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.hopgui.opsanalytics.OpsAnalyticsDialogOptions;

/** Headless checks and request mapping for the operational analytics dialog. */
public final class OpsAnalyticsDialogSupport {

  public static final String DEFAULT_OUTPUT_FOLDER = "${PROJECT_HOME}/models/ops-analytics";

  /** What to do when the output folder already exists. */
  public enum ReplaceDecision {
    PROCEED,
    REFUSE,
    CONFIRM
  }

  private OpsAnalyticsDialogSupport() {}

  public static ReplaceDecision replaceDecision(boolean folderExists, boolean replaceChecked) {
    if (!folderExists) {
      return ReplaceDecision.PROCEED;
    }
    return replaceChecked ? ReplaceDecision.CONFIRM : ReplaceDecision.REFUSE;
  }

  public static boolean folderExists(String folder, IVariables variables) throws HopException {
    if (Utils.isEmpty(folder)) {
      return false;
    }
    String resolved = variables != null ? variables.resolve(folder) : folder;
    try {
      FileObject file = HopVfs.getFileObject(resolved, variables);
      return file.exists();
    } catch (Exception e) {
      throw new HopException("Unable to check folder " + folder, e);
    }
  }

  /**
   * Names from the catalog that are not in {@code existingPhysicalNames}. Comparison ignores case.
   */
  public static List<String> missingTables(
      Set<String> existingPhysicalNames, boolean includeExecutions) {
    Set<String> present = new LinkedHashSet<>();
    if (existingPhysicalNames != null) {
      for (String name : existingPhysicalNames) {
        if (!Utils.isEmpty(name)) {
          present.add(name.toLowerCase(Locale.ROOT));
        }
      }
    }
    List<String> missing = new ArrayList<>();
    for (OpsAnalyticsTable table : OpsAnalyticsCatalog.tables(includeExecutions)) {
      if (!present.contains(table.physicalTable().toLowerCase(Locale.ROOT))) {
        missing.add(table.physicalTable());
      }
    }
    return missing;
  }

  /**
   * Reads which catalog tables exist. A connection failure returns a warning and an empty missing
   * list so generation can continue from the catalog.
   */
  public static Probe probe(
      DatabaseMeta databaseMeta, IVariables variables, String schema, boolean includeExecutions) {
    if (databaseMeta == null) {
      return new Probe(List.of(), "No OPS connection is selected.");
    }
    Database database =
        new Database(new LoggingObject(OpsAnalyticsDialogSupport.class), variables, databaseMeta);
    try {
      database.connect();
      List<String> missing = new ArrayList<>();
      for (OpsAnalyticsTable table : OpsAnalyticsCatalog.tables(includeExecutions)) {
        String tableSchema = table.executionInfo() ? null : schema;
        if (!database.checkTableExists(tableSchema, table.physicalTable())) {
          missing.add(table.physicalTable());
        }
      }
      return new Probe(missing, null);
    } catch (Exception e) {
      String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      return new Probe(List.of(), "Could not read the OPS database: " + message);
    } finally {
      database.disconnect();
    }
  }

  public static String validate(OpsAnalyticsDialogOptions options) {
    if (options == null || Utils.isEmpty(options.getConnectionName())) {
      return "OpsAnalyticsDialog.Validate.Connection";
    }
    if (Utils.isEmpty(options.getOutputFolder())) {
      return "OpsAnalyticsDialog.Validate.Folder";
    }
    if (Utils.isEmpty(options.getGroupName())) {
      return "OpsAnalyticsDialog.Validate.Group";
    }
    return null;
  }

  public static OpsAnalyticsRequest toRequest(
      OpsAnalyticsDialogOptions options, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    OpsAnalyticsRequest request = OpsAnalyticsRequest.defaults();
    request.setConnectionName(options.getConnectionName().trim());
    request.setSchemaName(options.getSchemaName());
    request.setOutputFolder(variables != null ? variables.resolve(options.getOutputFolder()) : options.getOutputFolder());
    request.setGroupName(options.getGroupName().trim());
    request.setIncludeExecutions(options.isIncludeExecutions());
    request.setExecutionSchema(options.getExecutionSchema());
    request.setDvTargetConnection(orOps(options.getDvTargetConnection(), options.getConnectionName()));
    request.setBvTargetConnection(orOps(options.getBvTargetConnection(), options.getConnectionName()));
    request.setDmTargetConnection(orOps(options.getDmTargetConnection(), options.getConnectionName()));
    request.setPipelineRunConfiguration(options.getPipelineRunConfiguration());
    request.setWorkflowRunConfiguration(options.getWorkflowRunConfiguration());
    request.setVariables(variables);
    if (metadataProvider != null) {
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(request.getConnectionName());
      request.setDatabaseMeta(databaseMeta);
    }
    return request;
  }

  private static String orOps(String target, String opsConnection) {
    return Utils.isEmpty(target) ? opsConnection.trim() : target.trim();
  }

  public record Probe(List<String> missingTables, String warning) {
    public Probe {
      missingTables = missingTables == null ? List.of() : List.copyOf(missingTables);
    }
  }
}
