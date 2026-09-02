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
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;

/** Validation rules for explicit satellite-to-BV SCD2 field mappings. */
public final class BvScd2FieldMappingValidationSupport {

  private static final Class<?> PKG = BvScd2FieldMappingValidationSupport.class;

  private BvScd2FieldMappingValidationSupport() {}

  static void validate(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (scd2Table == null || dataVaultModel == null) {
      return;
    }

    List<DvSatellite> satellites =
        resolveSatelliteDerivatives(scd2Table, dataVaultModel, variables, metadataProvider);
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
    }

    Set<String> derivativeNames = new HashSet<>();
    for (DvSatellite satellite : satellites) {
      derivativeNames.add(satellite.getName());
    }

    validateSharedParent(remarks, scd2Table, satellites);
    validateHubBusinessKeys(
        remarks, scd2Table, satellites, bvConfig, dvConfig, dataVaultModel, variables);
    validateCalculationOnlyMappings(remarks, scd2Table, variables);

    if (!hasMappings) {
      return;
    }

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

  static void validateHubBusinessKeys(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    if (scd2Table == null || !scd2Table.isIncludeHubBusinessKeys()) {
      return;
    }
    if (!scd2Table.isIncludeHashKey()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.HubBusinessKeysNeedHashKey",
                  scd2Table.getName()),
              scd2Table));
      return;
    }

    ParentKind parentKind = resolveParentKind(satellites);
    if (parentKind == ParentKind.LINK || parentKind == ParentKind.MIXED) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  parentKind == ParentKind.LINK
                      ? "BvScd2FieldMappingValidationSupport.Error.HubBusinessKeysLinkParent"
                      : "BvScd2FieldMappingValidationSupport.Error.HubBusinessKeysNeedHubParent",
                  scd2Table.getName()),
              scd2Table));
      return;
    }
    if (parentKind != ParentKind.HUB) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.HubBusinessKeysNeedHubParent",
                  scd2Table.getName()),
              scd2Table));
      return;
    }

    String hubName = resolveSharedHubName(satellites);
    if (Utils.isEmpty(hubName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.HubBusinessKeysNeedHubParent",
                  scd2Table.getName()),
              scd2Table));
      return;
    }

    DvHub hub = dataVaultModel != null ? dataVaultModel.findHub(hubName) : null;
    if (hub == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.HubBusinessKeysUnknownHub",
                  scd2Table.getName(),
                  hubName),
              scd2Table));
      return;
    }
    List<BusinessKey> businessKeys = hub.getDistinctBusinessKeys();
    if (businessKeys == null || businessKeys.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.HubBusinessKeysMissing",
                  scd2Table.getName(),
                  hubName),
              scd2Table));
      return;
    }

    Set<String> reserved = reservedScd2ColumnNames(scd2Table, bvConfig, dvConfig, variables);
    addReserved(reserved, variables.resolve(hub.getHashKeyFieldName()));
    for (BusinessKey businessKey : businessKeys) {
      if (businessKey == null || Utils.isEmpty(businessKey.getName())) {
        continue;
      }
      String bkName = variables.resolve(businessKey.getName());
      if (reserved.contains(bkName.toLowerCase())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.HubBusinessKeyCollision",
                    scd2Table.getName(),
                    bkName),
                scd2Table));
      }
    }
  }

  private static void validateCalculationOnlyMappings(
      List<ICheckResult> remarks, BvScd2Table scd2Table, IVariables variables) {
    if (scd2Table == null
        || !scd2Table.isIncrementalBuild()
        || scd2Table.getFieldMappings() == null) {
      return;
    }
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null || mapping.isIncludeInTarget()) {
        continue;
      }
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.CalculationOnlyIncremental",
                  scd2Table.getName(),
                  Utils.isEmpty(targetFieldName) ? mapping.getSourceFieldName() : targetFieldName),
              scd2Table));
    }
  }

  private static Set<String> reservedScd2ColumnNames(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    Set<String> names = new HashSet<>();
    addReserved(
        names,
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            scd2Table, bvConfig, dvConfig, variables));
    addReserved(names, BvScd2PipelineSupport.resolveValidFromField(scd2Table, bvConfig, variables));
    addReserved(names, BvScd2PipelineSupport.resolveValidToField(scd2Table, bvConfig, variables));
    addReserved(names, BvScd2PipelineSupport.resolveRecordSourceField(dvConfig, variables));
    if (scd2Table.getFieldMappings() != null) {
      for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
        if (mapping != null) {
          addReserved(names, variables.resolve(mapping.getTargetFieldName()));
        }
      }
    }
    if (scd2Table.getCalculations() != null) {
      for (BvScd2Calculation calculation : scd2Table.getCalculations()) {
        if (calculation != null) {
          addReserved(names, variables.resolve(calculation.getTargetFieldName()));
        }
      }
    }
    return names;
  }

  private static void addReserved(Set<String> names, String name) {
    if (!Utils.isEmpty(name)) {
      names.add(name.toLowerCase());
    }
  }

  public static DvHub resolveSharedParentHub(
      List<DvSatellite> satellites, DataVaultModel dataVaultModel) {
    String hubName = resolveSharedHubName(satellites);
    if (Utils.isEmpty(hubName) || dataVaultModel == null) {
      return null;
    }
    return dataVaultModel.findHub(hubName);
  }

  static String resolveSharedHubName(List<DvSatellite> satellites) {
    if (satellites == null
        || satellites.isEmpty()
        || resolveParentKind(satellites) != ParentKind.HUB) {
      return null;
    }
    for (DvSatellite satellite : satellites) {
      if (satellite != null && !Utils.isEmpty(satellite.getHubName())) {
        return satellite.getHubName();
      }
    }
    return null;
  }

  private enum ParentKind {
    HUB,
    LINK,
    MIXED,
    NONE
  }

  private static ParentKind resolveParentKind(List<DvSatellite> satellites) {
    boolean hub = false;
    boolean link = false;
    String hubName = null;
    String linkName = null;
    for (DvSatellite satellite : satellites) {
      if (satellite == null) {
        continue;
      }
      if (!Utils.isEmpty(satellite.getHubName())) {
        if (hubName != null && !hubName.equals(satellite.getHubName())) {
          return ParentKind.MIXED;
        }
        hubName = satellite.getHubName();
        hub = true;
      }
      if (!Utils.isEmpty(satellite.getLinkName())) {
        if (linkName != null && !linkName.equals(satellite.getLinkName())) {
          return ParentKind.MIXED;
        }
        linkName = satellite.getLinkName();
        link = true;
      }
    }
    if (hub && link) {
      return ParentKind.MIXED;
    }
    if (hub) {
      return ParentKind.HUB;
    }
    if (link) {
      return ParentKind.LINK;
    }
    return ParentKind.NONE;
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

  public static List<DvSatellite> resolveSatelliteDerivatives(
      BvScd2Table scd2Table, DataVaultModel dataVaultModel) {
    return resolveSatelliteDerivatives(scd2Table, dataVaultModel, null, null);
  }

  public static List<DvSatellite> resolveSatelliteDerivatives(
      BvScd2Table scd2Table,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<DvSatellite> satellites = new ArrayList<>();
    if (scd2Table == null) {
      return satellites;
    }
    for (BvScd2FieldMappingDialogSupport.SatelliteResolution resolution :
        BvScd2FieldMappingDialogSupport.resolveSatellites(
            scd2Table, dataVaultModel, variables, metadataProvider)) {
      if (resolution.resolved()) {
        satellites.add(resolution.satellite());
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
