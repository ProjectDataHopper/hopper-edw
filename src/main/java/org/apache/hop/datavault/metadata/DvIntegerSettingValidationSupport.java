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
import java.util.List;
import java.util.regex.Pattern;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Validates string settings that must resolve to positive integers (e.g. parallel copies). */
public final class DvIntegerSettingValidationSupport {

  private static final Class<?> PKG = DvIntegerSettingValidationSupport.class;

  private static final Pattern UNRESOLVED_VARIABLE = Pattern.compile(".*\\$\\{[^}]+}.*");

  private DvIntegerSettingValidationSupport() {}

  public record IntegerSettingValidation(
      String settingLabel, String configuredValue, String resolvedValue, String errorMessage) {

    public boolean isValid() {
      return Utils.isEmpty(errorMessage);
    }
  }

  public static IntegerSettingValidation validatePositiveInteger(
      String configuredValue, IVariables variables, String defaultWhenEmpty, String settingLabel) {
    String resolved = resolve(variables, configuredValue);
    if (Utils.isEmpty(resolved)) {
      resolved = defaultWhenEmpty;
    }
    resolved = resolved != null ? resolved.trim() : "";

    if (Utils.isEmpty(resolved)) {
      return new IntegerSettingValidation(
          settingLabel,
          configuredValue,
          resolved,
          BaseMessages.getString(
              PKG,
              "DvIntegerSettingValidation.NotPositiveInteger",
              settingLabel,
              configuredValue,
              resolved));
    }

    if (looksLikeUnresolvedVariable(resolved)) {
      return new IntegerSettingValidation(
          settingLabel,
          configuredValue,
          resolved,
          BaseMessages.getString(
              PKG,
              "DvIntegerSettingValidation.UnresolvedVariable",
              settingLabel,
              configuredValue,
              resolved));
    }

    try {
      int value = Integer.parseInt(resolved);
      if (value < 1) {
        return new IntegerSettingValidation(
            settingLabel,
            configuredValue,
            resolved,
            BaseMessages.getString(
                PKG,
                "DvIntegerSettingValidation.NotPositiveInteger",
                settingLabel,
                configuredValue,
                resolved));
      }
      return new IntegerSettingValidation(settingLabel, configuredValue, resolved, null);
    } catch (NumberFormatException e) {
      return new IntegerSettingValidation(
          settingLabel,
          configuredValue,
          resolved,
          BaseMessages.getString(
              PKG,
              "DvIntegerSettingValidation.NotPositiveInteger",
              settingLabel,
              configuredValue,
              resolved));
    }
  }

  public static int requirePositiveInteger(
      String configuredValue, IVariables variables, String defaultWhenEmpty, String settingLabel)
      throws HopException {
    IntegerSettingValidation validation =
        validatePositiveInteger(configuredValue, variables, defaultWhenEmpty, settingLabel);
    if (!validation.isValid()) {
      throw new HopException(validation.errorMessage());
    }
    String resolved = resolve(variables, configuredValue);
    if (Utils.isEmpty(resolved)) {
      resolved = defaultWhenEmpty;
    }
    return Integer.parseInt(resolved.trim());
  }

  public static List<IntegerSettingValidation> validateModelPipelineIntegerSettings(
      DataVaultConfiguration config, IVariables variables) {
    List<IntegerSettingValidation> results = new ArrayList<>();
    if (config == null) {
      return results;
    }
    results.add(
        validatePositiveInteger(
            config.getTargetTableBatchSize(),
            variables,
            DataVaultConfiguration.DEFAULT_TARGET_TABLE_BATCH_SIZE,
            BaseMessages.getString(PKG, "DvIntegerSettingValidation.Label.TargetTableBatchSize")));
    results.add(
        validatePositiveInteger(
            config.getTargetTableParallelCopies(),
            variables,
            DataVaultConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES,
            BaseMessages.getString(
                PKG, "DvIntegerSettingValidation.Label.TargetTableParallelCopies")));
    results.add(
        validatePositiveInteger(
            config.getSortRowsSize(),
            variables,
            DataVaultConfiguration.DEFAULT_SORT_ROWS_SIZE,
            BaseMessages.getString(PKG, "DvIntegerSettingValidation.Label.SortRowsSize")));
    return results;
  }

  public static void requireModelPipelineIntegerSettings(
      DataVaultConfiguration config, IVariables variables) throws HopException {
    for (IntegerSettingValidation validation :
        validateModelPipelineIntegerSettings(config, variables)) {
      if (!validation.isValid()) {
        throw new HopException(validation.errorMessage());
      }
    }
  }

  public static void requireTargetLoadIntegerSettings(
      String targetTableBatchSize,
      String targetTableParallelCopies,
      IVariables variables,
      String defaultBatchSize,
      String defaultParallelCopies)
      throws HopException {
    for (IntegerSettingValidation validation :
        List.of(
            validatePositiveInteger(
                targetTableBatchSize,
                variables,
                defaultBatchSize,
                BaseMessages.getString(
                    PKG, "DvIntegerSettingValidation.Label.TargetTableBatchSize")),
            validatePositiveInteger(
                targetTableParallelCopies,
                variables,
                defaultParallelCopies,
                BaseMessages.getString(
                    PKG, "DvIntegerSettingValidation.Label.TargetTableParallelCopies")))) {
      if (!validation.isValid()) {
        throw new HopException(validation.errorMessage());
      }
    }
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return "";
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static boolean looksLikeUnresolvedVariable(String resolved) {
    return resolved != null && UNRESOLVED_VARIABLE.matcher(resolved).matches();
  }
}
