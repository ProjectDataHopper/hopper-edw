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
package org.hopper.edw.datavault.metadata;

import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Shared resolve helpers for {@link IDvTargetLoadConfiguration} implementations. */
public final class DvTargetLoadConfigurationSupport {

  public static final String DEFAULT_BULK_LOAD_STAGING_FOLDER = "${java.io.tmpdir}/dv2/bulk/";
  public static final String DEFAULT_BULK_LOAD_DELIMITER = ",";
  public static final String DEFAULT_BULK_LOAD_ENCLOSURE = "\"";
  public static final String DEFAULT_BULK_LOAD_ENCODING = "UTF-8";

  private DvTargetLoadConfigurationSupport() {}

  public static String resolveTargetTableCommitSize(
      String batchSize, String defaultBatchSize, IVariables variables) {
    String size = batchSize;
    if (Utils.isEmpty(size)) {
      return defaultBatchSize;
    }
    if (variables != null) {
      size = variables.resolve(size);
    }
    return size;
  }

  public static String resolveTargetTableParallelCopies(
      String parallelCopies, String defaultParallelCopies, IVariables variables) {
    String copies = parallelCopies;
    if (Utils.isEmpty(copies)) {
      return defaultParallelCopies;
    }
    if (variables != null) {
      copies = variables.resolve(copies);
    }
    return copies;
  }

  public static String resolveBulkLoadStagingFolder(
      String folder, String defaultFolder, IVariables variables, String modelName) {
    String resolved = folder;
    if (Utils.isEmpty(resolved)) {
      resolved = defaultFolder;
    }
    if (variables != null) {
      resolved = variables.resolve(resolved);
    }
    if (!Utils.isEmpty(modelName) && !resolved.contains("${") && !resolved.endsWith("/")) {
      resolved = resolved + "/" + modelName + "/";
    }
    return resolved;
  }

  public static String resolveBulkLoadTextSetting(
      String value, String defaultValue, IVariables variables) {
    String resolved = Utils.isEmpty(value) ? defaultValue : value;
    if (variables != null) {
      resolved = variables.resolve(resolved);
    }
    return resolved;
  }

  public static void setTargetLoadModeFromDescriptionOrCode(
      String descriptionOrCode, TargetLoadModeHolder holder) {
    if (holder == null) {
      return;
    }
    if (Utils.isEmpty(descriptionOrCode)) {
      holder.targetLoadMode = DvTargetLoadMode.TABLE_OUTPUT.getCode();
      return;
    }
    String value = descriptionOrCode.trim();
    for (DvTargetLoadMode mode : DvTargetLoadMode.values()) {
      if (value.equals(mode.getDescription()) || value.equals(mode.getCode())) {
        holder.targetLoadMode = mode.getCode();
        return;
      }
    }
    holder.targetLoadMode = DvTargetLoadMode.lookupCode(value).getCode();
  }

  public static String getTargetLoadModeDescription(String targetLoadModeCode) {
    return DvTargetLoadMode.lookupCode(targetLoadModeCode).getDescription();
  }

  public static List<String> getTargetLoadModeOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider, String targetDatabaseName) {
    DatabaseMeta targetDatabase = resolveTargetDatabase(metadataProvider, targetDatabaseName);
    List<String> available = DvBulkLoadPluginSupport.getAvailableModeDescriptions(targetDatabase);
    if (available.isEmpty()) {
      return List.of(DvTargetLoadMode.TABLE_OUTPUT.getDescription());
    }
    return available;
  }

  public static String resolveGeneratedWorkflowName(
      String prefix, String defaultPrefix, IVariables variables, String modelName) {
    String resolvedPrefix = Utils.isEmpty(prefix) ? defaultPrefix : prefix;
    if (variables != null) {
      resolvedPrefix = variables.resolve(resolvedPrefix);
    }
    if (Utils.isEmpty(modelName)) {
      return resolvedPrefix;
    }
    return resolvedPrefix + modelName;
  }

  private static DatabaseMeta resolveTargetDatabase(
      IHopMetadataProvider metadataProvider, String targetDatabaseName) {
    if (metadataProvider == null || Utils.isEmpty(targetDatabaseName)) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(DatabaseMeta.class).load(targetDatabaseName);
    } catch (Exception e) {
      return null;
    }
  }

  /** Mutable holder for target load mode code when setting from GUI combo values. */
  public static final class TargetLoadModeHolder {
    public String targetLoadMode = DvTargetLoadMode.TABLE_OUTPUT.getCode();
  }
}
