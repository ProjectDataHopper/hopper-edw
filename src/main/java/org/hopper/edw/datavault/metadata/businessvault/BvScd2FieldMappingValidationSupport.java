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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.apache.hop.i18n.BaseMessages;

/** Validation rules for explicit satellite-to-BV SCD2 field mappings. */
final class BvScd2FieldMappingValidationSupport {

  private static final Class<?> PKG = BvScd2FieldMappingValidationSupport.class;

  private BvScd2FieldMappingValidationSupport() {}

  static void validate(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    if (scd2Table == null || dataVaultModel == null) {
      return;
    }

    List<DvSatellite> satellites = resolveSatelliteDerivatives(scd2Table, dataVaultModel);
    if (satellites.isEmpty()) {
      return;
    }

    List<BvScd2FieldMapping> mappings = scd2Table.getFieldMappings();
    boolean hasMappings = mappings != null && !mappings.isEmpty();

    if (satellites.size() > 1 && !hasMappings) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.MappingsRequiredForMultiSatellite",
                  scd2Table.getName()),
              scd2Table));
      return;
    }

    if (!hasMappings) {
      return;
    }

    Set<String> derivativeNames = new HashSet<>();
    for (DvSatellite satellite : satellites) {
      derivativeNames.add(satellite.getName());
    }

    validateSharedParent(remarks, scd2Table, satellites);

    Set<String> targetFieldNames = new HashSet<>();
    Map<String, Integer> mappingsPerSatellite = new HashMap<>();
    for (String derivativeName : derivativeNames) {
      mappingsPerSatellite.put(derivativeName, 0);
    }

    for (BvScd2FieldMapping mapping : mappings) {
      if (mapping == null) {
        continue;
      }
      String satelliteName = variables.resolve(mapping.getSatelliteName());
      String sourceFieldName = variables.resolve(mapping.getSourceFieldName());
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());

      if (Utils.isEmpty(satelliteName)
          || Utils.isEmpty(sourceFieldName)
          || Utils.isEmpty(targetFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.IncompleteMapping",
                    scd2Table.getName()),
                scd2Table));
        continue;
      }

      if (!derivativeNames.contains(satelliteName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.UnknownSatellite",
                    scd2Table.getName(),
                    satelliteName),
                scd2Table));
        continue;
      }

      if (!targetFieldNames.add(targetFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.DuplicateTargetField",
                    scd2Table.getName(),
                    targetFieldName),
                scd2Table));
      }

      DvSatellite satellite = findSatellite(satellites, satelliteName);
      if (satellite != null && !satelliteDefinesAttribute(satellite, sourceFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.MissingSourceField",
                    scd2Table.getName(),
                    satelliteName,
                    sourceFieldName),
                scd2Table));
      }

      mappingsPerSatellite.merge(satelliteName, 1, Integer::sum);
    }

    if (satellites.size() > 1) {
      for (Map.Entry<String, Integer> entry : mappingsPerSatellite.entrySet()) {
        if (entry.getValue() == 0) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvScd2FieldMappingValidationSupport.Error.SatelliteWithoutMappings",
                      scd2Table.getName(),
                      entry.getKey()),
                  scd2Table));
        }
      }
    }

    validateSatelliteConfigs(remarks, scd2Table, satellites, derivativeNames, variables);
    validateFunctionalTimestamps(remarks, scd2Table, satellites, bvConfig, dvConfig, variables);
  }

  private static void validateSharedParent(
      List<ICheckResult> remarks, BvScd2Table scd2Table, List<DvSatellite> satellites) {
    String anchorHub = null;
    String anchorLink = null;
    for (DvSatellite satellite : satellites) {
      if (!Utils.isEmpty(satellite.getHubName())) {
        if (anchorHub == null) {
          anchorHub = satellite.getHubName();
        } else if (!anchorHub.equals(satellite.getHubName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvScd2FieldMappingValidationSupport.Error.MixedHubParents",
                      scd2Table.getName()),
                  scd2Table));
          return;
        }
      } else if (!Utils.isEmpty(satellite.getLinkName())) {
        if (anchorLink == null) {
          anchorLink = satellite.getLinkName();
        } else if (!anchorLink.equals(satellite.getLinkName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvScd2FieldMappingValidationSupport.Error.MixedLinkParents",
                      scd2Table.getName()),
                  scd2Table));
          return;
        }
      } else {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.UnparentedSatellite",
                    scd2Table.getName(),
                    satellite.getName()),
                scd2Table));
      }

      if (anchorHub != null && anchorLink != null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.MixedHubAndLinkParents",
                    scd2Table.getName()),
                scd2Table));
        return;
      }
    }
  }

  private static void validateSatelliteConfigs(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      Set<String> derivativeNames,
      IVariables variables) {
    if (scd2Table.getSatelliteConfigs() == null) {
      return;
    }
    for (BvScd2SatelliteConfig config : scd2Table.getSatelliteConfigs()) {
      if (config == null) {
        continue;
      }
      String satelliteName = variables.resolve(config.getSatelliteName());
      if (Utils.isEmpty(satelliteName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.IncompleteSatelliteConfig",
                    scd2Table.getName()),
                scd2Table));
        continue;
      }
      if (!derivativeNames.contains(satelliteName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.UnknownSatelliteConfig",
                    scd2Table.getName(),
                    satelliteName),
                scd2Table));
      }
    }
  }

  private static void validateFunctionalTimestamps(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (scd2Table.getFieldMappings() == null || scd2Table.getFieldMappings().isEmpty()) {
      return;
    }

    for (DvSatellite satellite : satellites) {
      BvScd2SatelliteConfig satelliteConfig =
          findSatelliteConfig(scd2Table, satellite.getName(), variables);
      String functionalTimestampField =
          BvScd2PipelineSupport.resolveFunctionalTimestampFieldForSatellite(
              scd2Table, satelliteConfig, bvConfig, dvConfig, variables);
      if (Utils.isEmpty(functionalTimestampField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.MissingFunctionalTimestampForSatellite",
                    scd2Table.getName(),
                    satellite.getName()),
                scd2Table));
        continue;
      }
      if (!satelliteDefinesTimelineField(
          satellite, functionalTimestampField, dvConfig, variables)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.MissingFunctionalTimestampColumn",
                    scd2Table.getName(),
                    satellite.getName(),
                    functionalTimestampField),
                scd2Table));
      }
    }
  }

  static List<DvSatellite> resolveSatelliteDerivatives(
      BvScd2Table scd2Table, DataVaultModel dataVaultModel) {
    List<DvSatellite> satellites = new ArrayList<>();
    for (BvDerivativeRef derivative : scd2Table.getDerivatives()) {
      if (derivative == null
          || derivative.getDvTableType() != DvTableType.SATELLITE
          || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      if (dataVaultModel.findTable(derivative.getDvTableName()) instanceof DvSatellite satellite) {
        satellites.add(satellite);
      }
    }
    return satellites;
  }

  static BvScd2SatelliteConfig findSatelliteConfig(
      BvScd2Table scd2Table, String satelliteName, IVariables variables) {
    if (scd2Table.getSatelliteConfigs() == null || Utils.isEmpty(satelliteName)) {
      return null;
    }
    String resolvedName = variables.resolve(satelliteName);
    for (BvScd2SatelliteConfig config : scd2Table.getSatelliteConfigs()) {
      if (config != null && resolvedName.equals(variables.resolve(config.getSatelliteName()))) {
        return config;
      }
    }
    return null;
  }

  static boolean satelliteDefinesAttribute(DvSatellite satellite, String fieldName) {
    if (satellite == null || Utils.isEmpty(fieldName) || satellite.getAttributes() == null) {
      return false;
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      if (attribute != null && fieldName.equals(attribute.getName())) {
        return true;
      }
    }
    return false;
  }

  static boolean satelliteDefinesTimelineField(
      DvSatellite satellite,
      String fieldName,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (satelliteDefinesAttribute(satellite, fieldName)) {
      return true;
    }
    if (dvConfig == null) {
      return false;
    }
    String loadDateField = variables.resolve(dvConfig.getLoadDateField());
    if (!Utils.isEmpty(loadDateField) && loadDateField.equals(fieldName)) {
      return true;
    }
    String loadEndDateField = variables.resolve(dvConfig.getLoadEndDateField());
    return !Utils.isEmpty(loadEndDateField) && loadEndDateField.equals(fieldName);
  }

  private static DvSatellite findSatellite(List<DvSatellite> satellites, String satelliteName) {
    for (DvSatellite satellite : satellites) {
      if (satellite != null && satelliteName.equals(satellite.getName())) {
        return satellite;
      }
    }
    return null;
  }
}
