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
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Model validation for target load settings shared across DV, BV, and dimensional modelers. */
public final class DvTargetLoadModelCheckSupport {

  private static final Class<?> PKG = DvTargetLoadModelCheckSupport.class;

  private DvTargetLoadModelCheckSupport() {}

  public static void checkTargetLoadMode(
      List<ICheckResult> remarks, IDvTargetLoadConfiguration config, DatabaseMeta targetDatabase) {
    if (config == null) {
      return;
    }
    DvTargetLoadMode mode = config.resolveTargetLoadMode();
    if (mode == DvTargetLoadMode.TABLE_OUTPUT) {
      return;
    }
    if (!DvBulkLoadPluginSupport.isModeAvailable(targetDatabase, mode)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvTargetLoadModelCheckSupport.TargetLoadModeUnavailable",
                  mode.getDescription(),
                  targetDatabase != null ? targetDatabase.getPluginId() : ""),
              null));
    }
  }

  public static void checkTargetLoadModeGuidance(
      List<ICheckResult> remarks,
      IDvTargetLoadConfiguration config,
      DatabaseMeta targetDatabase,
      IVariables variables) {
    if (config == null) {
      return;
    }
    DvTargetLoadMode mode = config.resolveTargetLoadMode();
    if (mode == DvTargetLoadMode.TABLE_OUTPUT) {
      return;
    }

    int parallelCopies = resolveTargetTableParallelCopies(config, variables);
    if (mode == DvTargetLoadMode.NATIVE_BULK && parallelCopies > 1) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DvTargetLoadModelCheckSupport.TargetLoadNativeBulkIgnoresParallelCopies",
                  parallelCopies),
              null));
    }

    if (mode == DvTargetLoadMode.STAGING_FILE) {
      if (config.isBulkLoadLocalFileRequired()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "DvTargetLoadModelCheckSupport.TargetLoadStagingLocalFileRequired"),
                null));
      }
    }
  }

  public static void checkTargetLoadingIntegerSettings(
      List<ICheckResult> remarks,
      String targetTableBatchSize,
      String targetTableParallelCopies,
      IVariables variables,
      String defaultBatchSize,
      String defaultParallelCopies) {
    for (DvIntegerSettingValidationSupport.IntegerSettingValidation validation :
        List.of(
            DvIntegerSettingValidationSupport.validatePositiveInteger(
                targetTableBatchSize,
                variables,
                defaultBatchSize,
                org.apache.hop.i18n.BaseMessages.getString(
                    DvIntegerSettingValidationSupport.class,
                    "DvIntegerSettingValidation.Label.TargetTableBatchSize")),
            DvIntegerSettingValidationSupport.validatePositiveInteger(
                targetTableParallelCopies,
                variables,
                defaultParallelCopies,
                org.apache.hop.i18n.BaseMessages.getString(
                    DvIntegerSettingValidationSupport.class,
                    "DvIntegerSettingValidation.Label.TargetTableParallelCopies")))) {
      if (!validation.isValid()) {
        remarks.add(
            new CheckResult(ICheckResult.TYPE_RESULT_ERROR, validation.errorMessage(), null));
      }
    }
  }

  private static int resolveTargetTableParallelCopies(
      IDvTargetLoadConfiguration config, IVariables variables) {
    try {
      return Integer.parseInt(config.resolveTargetTableParallelCopies(variables).trim());
    } catch (NumberFormatException e) {
      return 1;
    }
  }
}
