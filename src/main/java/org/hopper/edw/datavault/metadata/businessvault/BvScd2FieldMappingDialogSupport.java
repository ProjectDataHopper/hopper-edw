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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;

/** Pure helpers for the SCD2 field-mapping dialog. */
public final class BvScd2FieldMappingDialogSupport {

  private BvScd2FieldMappingDialogSupport() {}

  public static List<String> satelliteDerivativeNames(
      BvScd2Table scd2Table, DataVaultModel dataVaultModel) {
    List<String> names = new ArrayList<>();
    if (scd2Table == null) {
      return names;
    }
    for (BvDerivativeRef derivative : scd2Table.getDerivatives()) {
      if (derivative == null
          || derivative.getDvTableType() != DvTableType.SATELLITE
          || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      if (dataVaultModel != null
          && !(dataVaultModel.findTable(derivative.getDvTableName()) instanceof DvSatellite)) {
        continue;
      }
      names.add(derivative.getDvTableName());
    }
    return names;
  }

  public static List<String> satelliteAttributeNames(
      String satelliteName, DataVaultModel dataVaultModel) {
    List<String> names = new ArrayList<>();
    if (dataVaultModel == null || Utils.isEmpty(satelliteName)) {
      return names;
    }
    if (dataVaultModel.findTable(satelliteName) instanceof DvSatellite satellite) {
      if (satellite.getAttributes() != null) {
        for (SatelliteAttribute attribute : satellite.getAttributes()) {
          if (attribute != null && !Utils.isEmpty(attribute.getName())) {
            names.add(attribute.getName());
          }
        }
      }
    }
    return names;
  }

  public static List<BvScd2FieldMapping> suggestMappings(
      BvScd2Table scd2Table, DataVaultModel dataVaultModel) {
    List<BvScd2FieldMapping> suggestions = new ArrayList<>();
    if (scd2Table == null || dataVaultModel == null) {
      return suggestions;
    }

    Set<String> existingKeys = new LinkedHashSet<>();
    Set<String> usedTargets = new HashSet<>();
    if (scd2Table.getFieldMappings() != null) {
      for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
        if (mapping == null) {
          continue;
        }
        existingKeys.add(mappingKey(mapping.getSatelliteName(), mapping.getSourceFieldName()));
        if (!Utils.isEmpty(mapping.getTargetFieldName())) {
          usedTargets.add(mapping.getTargetFieldName());
        }
      }
    }

    for (String satelliteName : satelliteDerivativeNames(scd2Table, dataVaultModel)) {
      for (String sourceFieldName : satelliteAttributeNames(satelliteName, dataVaultModel)) {
        String key = mappingKey(satelliteName, sourceFieldName);
        if (existingKeys.contains(key)) {
          continue;
        }
        String targetFieldName =
            suggestTargetFieldName(satelliteName, sourceFieldName, usedTargets);
        usedTargets.add(targetFieldName);
        existingKeys.add(key);
        suggestions.add(new BvScd2FieldMapping(satelliteName, sourceFieldName, targetFieldName));
      }
    }
    return suggestions;
  }

  public static void pruneMappingsAndConfigs(BvScd2Table scd2Table, Set<String> activeSatellites) {
    if (scd2Table == null || activeSatellites == null) {
      return;
    }
    scd2Table
        .getFieldMappings()
        .removeIf(
            mapping ->
                mapping == null
                    || Utils.isEmpty(mapping.getSatelliteName())
                    || !activeSatellites.contains(mapping.getSatelliteName()));
    scd2Table
        .getSatelliteConfigs()
        .removeIf(
            config ->
                config == null
                    || Utils.isEmpty(config.getSatelliteName())
                    || !activeSatellites.contains(config.getSatelliteName()));
  }

  public static List<BvScd2SatelliteConfig> syncSatelliteConfigs(
      BvScd2Table scd2Table, List<String> satelliteNames) {
    Map<String, BvScd2SatelliteConfig> existing = new LinkedHashMap<>();
    if (scd2Table.getSatelliteConfigs() != null) {
      for (BvScd2SatelliteConfig config : scd2Table.getSatelliteConfigs()) {
        if (config != null && !Utils.isEmpty(config.getSatelliteName())) {
          existing.putIfAbsent(config.getSatelliteName(), config);
        }
      }
    }

    List<BvScd2SatelliteConfig> synced = new ArrayList<>();
    for (String satelliteName : satelliteNames) {
      BvScd2SatelliteConfig config = existing.get(satelliteName);
      if (config == null) {
        config = new BvScd2SatelliteConfig(satelliteName);
      }
      synced.add(config);
    }
    return synced;
  }

  public static List<ICheckResult> validateForDialog(
      BvScd2Table scd2Table,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (scd2Table == null) {
      return remarks;
    }
    scd2Table.check(remarks, null, variables, businessVaultModel, dataVaultModel);
    return remarks;
  }

  public static boolean hasValidationErrors(List<ICheckResult> remarks) {
    if (remarks == null) {
      return false;
    }
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  public static String formatValidationErrors(List<ICheckResult> remarks) {
    StringBuilder builder = new StringBuilder();
    if (remarks == null) {
      return builder.toString();
    }
    for (ICheckResult remark : remarks) {
      if (remark != null && remark.getType() == ICheckResult.TYPE_RESULT_ERROR) {
        if (builder.length() > 0) {
          builder.append(System.lineSeparator());
        }
        builder.append(remark.getText());
      }
    }
    return builder.toString();
  }

  static String suggestTargetFieldName(
      String satelliteName, String sourceFieldName, Set<String> usedTargets) {
    if (Utils.isEmpty(sourceFieldName)) {
      return sourceFieldName;
    }
    if (usedTargets == null || !usedTargets.contains(sourceFieldName)) {
      return sourceFieldName;
    }
    String prefix = satelliteName;
    if (!Utils.isEmpty(prefix) && prefix.startsWith("sat_")) {
      prefix = prefix.substring(4);
    }
    String candidate = prefix + "_" + sourceFieldName;
    if (!usedTargets.contains(candidate)) {
      return candidate;
    }
    int suffix = 2;
    while (usedTargets.contains(candidate + suffix)) {
      suffix++;
    }
    return candidate + suffix;
  }

  private static String mappingKey(String satelliteName, String sourceFieldName) {
    return emptyIfNull(satelliteName) + "|" + emptyIfNull(sourceFieldName);
  }

  private static String emptyIfNull(String value) {
    return value == null ? "" : value;
  }
}
