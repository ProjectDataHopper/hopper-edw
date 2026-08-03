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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Shared helpers for DV table integration modes. */
public final class DvIntegrationSupport {

  private static final Class<?> PKG = DvIntegrationSupport.class;

  private DvIntegrationSupport() {}

  public static DvIntegrationMode resolveMode(DvTableBase table) {
    if (table == null) {
      return DvIntegrationMode.HOP_MANAGED;
    }
    DvIntegrationMode mode = table.getIntegrationMode();
    return mode != null ? mode : DvIntegrationMode.HOP_MANAGED;
  }

  public static boolean isHopManaged(IDvTable table) {
    return table instanceof DvTableBase base && resolveMode(base) == DvIntegrationMode.HOP_MANAGED;
  }

  public static boolean isLinkedTable(IDvTable table) {
    return table instanceof DvLinkedTable;
  }

  public static boolean isExternalRead(IDvTable table) {
    return table instanceof DvTableBase base
        && resolveMode(base) == DvIntegrationMode.EXTERNAL_READ;
  }

  public static boolean isCustomPipelines(IDvTable table) {
    return table instanceof DvTableBase base
        && resolveMode(base) == DvIntegrationMode.CUSTOM_PIPELINES;
  }

  public static boolean shouldSkipDdl(IDvTable table) {
    return isLinkedTable(table) || isExternalRead(table) || isCustomPipelines(table);
  }

  public static boolean shouldSkipSentinelRows(IDvTable table) {
    return isLinkedTable(table) || isExternalRead(table);
  }

  public static boolean relaxesSourceValidation(IDvTable table) {
    return isLinkedTable(table) || isExternalRead(table);
  }

  public static boolean shouldSkipUpdatePipeline(IDvTable table) {
    return isLinkedTable(table) || isExternalRead(table);
  }

  public static String integrationCanvasSuffix(IDvTable table) {
    if (isLinkedTable(table)) {
      return "ref";
    }
    if (isExternalRead(table)) {
      return "ext";
    }
    if (isCustomPipelines(table)) {
      return "custom";
    }
    return null;
  }

  public static List<PipelineMeta> loadCustomUpdatePipelines(
      DvTableBase table, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopException {
    List<String> paths = table.getCustomUpdatePipelinePaths();
    if (paths == null || paths.isEmpty()) {
      return Collections.emptyList();
    }

    List<PipelineMeta> result = new ArrayList<>();
    for (String path : paths) {
      if (Utils.isEmpty(path)) {
        continue;
      }
      String resolved = variables != null ? variables.resolve(path) : path;
      if (Utils.isEmpty(resolved)) {
        continue;
      }
      try {
        PipelineMeta pipelineMeta = new PipelineMeta(resolved, metadataProvider, variables);
        pipelineMeta.lookupReferencesAfterLoading();
        result.add(pipelineMeta);
      } catch (Exception e) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "DvIntegrationSupport.Error.LoadCustomPipeline", resolved, table.getName()),
            e);
      }
    }
    return result;
  }

  public static List<PipelineMeta> resolveUpdatePipelines(
      IDvTable table,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      java.util.Date loadDate,
      String recordSourceGroup,
      PipelineGenerator generator)
      throws HopException {
    if (isExternalRead(table)) {
      return Collections.emptyList();
    }
    if (table instanceof DvTableBase base && isCustomPipelines(table)) {
      return loadCustomUpdatePipelines(base, metadataProvider, variables);
    }
    return generator.generate(metadataProvider, variables, model, loadDate, recordSourceGroup);
  }

  @FunctionalInterface
  public interface PipelineGenerator {
    List<PipelineMeta> generate(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultModel model,
        java.util.Date loadDate,
        String recordSourceGroup)
        throws HopException;
  }

  public static void checkIntegrationMode(
      DvTableBase table,
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model) {
    DvIntegrationMode mode = resolveMode(table);
    switch (mode) {
      case EXTERNAL_READ ->
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_OK,
                  BaseMessages.getString(PKG, "DvIntegrationSupport.CheckResult.ExternalRead"),
                  table));
      case CUSTOM_PIPELINES ->
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_OK,
                  BaseMessages.getString(PKG, "DvIntegrationSupport.CheckResult.CustomPipelines"),
                  table));
      default ->
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_OK,
                  BaseMessages.getString(PKG, "DvIntegrationSupport.CheckResult.HopManaged"),
                  table));
    }

    if (mode == DvIntegrationMode.EXTERNAL_READ && model != null && metadataProvider != null) {
      try {
        IRowMeta layout = table.getTargetTableLayout(metadataProvider, variables, model);
        if (layout == null || layout.isEmpty()) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG, "DvIntegrationSupport.CheckResult.ExternalNeedsLayout"),
                  table));
        } else {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_OK,
                  BaseMessages.getString(
                      PKG, "DvIntegrationSupport.CheckResult.ExternalHasLayout", layout.size()),
                  table));
        }
      } catch (HopException e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DvIntegrationSupport.CheckResult.ExternalLayoutError", e.getMessage()),
                table));
      }
    }

    if (mode == DvIntegrationMode.CUSTOM_PIPELINES) {
      List<String> paths = table.getCustomUpdatePipelinePaths();
      if (paths == null || paths.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "DvIntegrationSupport.CheckResult.CustomPipelinesMissingPaths"),
                table));
        return;
      }

      int found = 0;
      for (String path : paths) {
        if (Utils.isEmpty(path)) {
          continue;
        }
        String resolved = variables != null ? variables.resolve(path) : path;
        if (Utils.isEmpty(resolved)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  BaseMessages.getString(
                      PKG, "DvIntegrationSupport.CheckResult.CustomPipelineUnresolved", path),
                  table));
          continue;
        }
        try {
          FileObject file = HopVfs.getFileObject(resolved, variables);
          if (file.exists()) {
            found++;
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_OK,
                    BaseMessages.getString(
                        PKG, "DvIntegrationSupport.CheckResult.CustomPipelineFound", resolved),
                    table));
          } else {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_WARNING,
                    BaseMessages.getString(
                        PKG, "DvIntegrationSupport.CheckResult.CustomPipelineMissing", resolved),
                    table));
          }
        } catch (Exception e) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  BaseMessages.getString(
                      PKG,
                      "DvIntegrationSupport.CheckResult.CustomPipelineCheckFailed",
                      resolved,
                      e.getMessage()),
                  table));
        }
      }
      if (found == 0) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DvIntegrationSupport.CheckResult.CustomPipelinesNoValidPaths"),
                table));
      }
    }
  }
}
