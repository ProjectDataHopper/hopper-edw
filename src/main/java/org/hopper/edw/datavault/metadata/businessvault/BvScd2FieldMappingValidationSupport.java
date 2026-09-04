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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvIdentifierLimitSupport;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;
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
    validate(
        remarks, scd2Table, bvConfig, dvConfig, dataVaultModel, null, variables, metadataProvider);
  }

  static void validate(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      DataVaultModel dataVaultModel,
      BusinessVaultModel bvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (scd2Table == null) {
      return;
    }

    List<DvSatellite> satellites =
        resolveSatelliteDerivatives(scd2Table, dataVaultModel, variables, metadataProvider);
    List<BvSourceQuery> sourceQueries =
        BusinessVaultSourceQuerySupport.resolveSourceQueries(scd2Table, bvModel);

    validateParentHub(
        remarks, scd2Table, satellites, sourceQueries, dataVaultModel, bvModel, variables);
    validateIdentifierLengths(
        remarks,
        scd2Table,
        satellites,
        sourceQueries,
        bvConfig,
        dvConfig,
        dataVaultModel,
        variables,
        metadataProvider);
    validateValidityFieldNames(
        remarks,
        scd2Table,
        satellites,
        sourceQueries,
        bvConfig,
        dvConfig,
        dataVaultModel,
        variables);
    validateIncrementalWatermark(remarks, scd2Table, bvConfig, dvConfig, variables);
    validateSharedParent(remarks, scd2Table, satellites, variables);
    validateHubBusinessKeys(
        remarks, scd2Table, satellites, bvConfig, dvConfig, dataVaultModel, variables);
    validateCalculationOnlyMappings(remarks, scd2Table, variables);
    validateSourceQueryColumnsPresent(remarks, scd2Table, sourceQueries);

    List<BvScd2FieldMapping> mappings = scd2Table.getFieldMappings();
    boolean hasMappings = mappings != null && !mappings.isEmpty();

    if (satellites.size() + sourceQueries.size() > 1 && !hasMappings) {
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
    for (BvSourceQuery sourceQuery : sourceQueries) {
      if (sourceQuery != null && !Utils.isEmpty(sourceQuery.getName())) {
        derivativeNames.add(sourceQuery.getName());
      }
    }

    Set<String> technicalNames =
        technicalColumnNames(
            scd2Table, satellites, sourceQueries, bvConfig, dvConfig, dataVaultModel, variables);

    if (hasMappings) {
      Set<String> targetFieldNames = new HashSet<>();
      Set<String> sourceKeys = new HashSet<>();
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

        String sourceKey = satelliteName.toLowerCase() + "|" + sourceFieldName.toLowerCase();
        if (!sourceKeys.add(sourceKey)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvScd2FieldMappingValidationSupport.Error.DuplicateSourceField",
                      scd2Table.getName(),
                      satelliteName,
                      sourceFieldName),
                  scd2Table));
        }

        if (!targetFieldNames.add(targetFieldName.toLowerCase())) {
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

        if (technicalNames.contains(targetFieldName.toLowerCase())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvScd2FieldMappingValidationSupport.Error.ReservedTargetField",
                      scd2Table.getName(),
                      targetFieldName),
                  scd2Table));
        }

        DvSatellite satellite = findSatellite(satellites, satelliteName);
        BvSourceQuery sourceQuery = findSourceQuery(sourceQueries, satelliteName);
        if (isGrainOrTimelineSource(
            satellite,
            sourceQuery,
            sourceFieldName,
            scd2Table,
            bvConfig,
            dvConfig,
            dataVaultModel,
            variables)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvScd2FieldMappingValidationSupport.Error.MappedGrainOrTimelineField",
                      scd2Table.getName(),
                      satelliteName,
                      sourceFieldName),
                  scd2Table));
        } else if (satellite != null && !satelliteDefinesAttribute(satellite, sourceFieldName)) {
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
        } else if (sourceQuery != null
            && !sourceQuery.getColumns().isEmpty()
            && !sourceQuery.definesColumn(sourceFieldName)) {
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

      if (satellites.size() + sourceQueries.size() > 1) {
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
    }

    validateSatelliteConfigs(remarks, scd2Table, derivativeNames, variables);
    validateFunctionalTimestamps(remarks, scd2Table, satellites, bvConfig, dvConfig, variables);
    validateSourceQueryTimestamps(remarks, scd2Table, sourceQueries, bvConfig, dvConfig, variables);
    validateSourceQueryHashKeyMapping(
        remarks, scd2Table, sourceQueries, satellites, dataVaultModel, variables);
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

    String hubName = resolveParentHubName(scd2Table, satellites, variables);
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
    return resolveSharedParentHub(null, satellites, dataVaultModel, null);
  }

  public static DvHub resolveSharedParentHub(
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    String hubName = resolveParentHubName(scd2Table, satellites, variables);
    if (Utils.isEmpty(hubName) || dataVaultModel == null) {
      return null;
    }
    return dataVaultModel.findHub(hubName);
  }

  static String resolveParentHubName(
      BvScd2Table scd2Table, List<DvSatellite> satellites, IVariables variables) {
    String declared = BusinessVaultDerivativeSupport.resolveDeclaredParentHubName(scd2Table);
    if (!Utils.isEmpty(declared)) {
      return variables != null ? variables.resolve(declared) : declared;
    }
    return resolveSharedHubName(satellites);
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
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      IVariables variables) {
    String declaredHub =
        scd2Table != null && !Utils.isEmpty(scd2Table.getParentHubName())
            ? (variables != null
                ? variables.resolve(scd2Table.getParentHubName())
                : scd2Table.getParentHubName())
            : null;
    String anchorHub = declaredHub;
    String anchorLink = null;
    for (DvSatellite satellite : satellites) {
      if (satellite == null) {
        continue;
      }
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
      } else if (Utils.isEmpty(declaredHub)) {
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
      Set<String> derivativeNames,
      IVariables variables) {
    if (scd2Table.getSatelliteConfigs() == null) {
      return;
    }
    Set<String> seen = new HashSet<>();
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
      if (!seen.add(satelliteName.toLowerCase())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.DuplicateSatelliteConfig",
                    scd2Table.getName(),
                    satelliteName),
                scd2Table));
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
      if (resolution.satellite() != null) {
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
      if (attribute != null && fieldName.equalsIgnoreCase(attribute.getName())) {
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

  private static BvSourceQuery findSourceQuery(List<BvSourceQuery> sourceQueries, String name) {
    if (sourceQueries == null || Utils.isEmpty(name)) {
      return null;
    }
    for (BvSourceQuery sourceQuery : sourceQueries) {
      if (sourceQuery != null && name.equalsIgnoreCase(sourceQuery.getName())) {
        return sourceQuery;
      }
    }
    return null;
  }

  private static void validateSourceQueryTimestamps(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<BvSourceQuery> sourceQueries,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (sourceQueries == null || sourceQueries.isEmpty()) {
      return;
    }
    for (BvSourceQuery sourceQuery : sourceQueries) {
      if (sourceQuery == null) {
        continue;
      }
      BvScd2SatelliteConfig config =
          findSatelliteConfig(scd2Table, sourceQuery.getName(), variables);
      String timestamp =
          BvScd2PipelineSupport.resolveFunctionalTimestampFieldForSourceQuery(
              scd2Table, sourceQuery, config, bvConfig, dvConfig, variables);
      if (Utils.isEmpty(timestamp)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.MissingFunctionalTimestampForSatellite",
                    scd2Table.getName(),
                    sourceQuery.getName()),
                scd2Table));
        continue;
      }
      if (!sourceQuery.getColumns().isEmpty() && !sourceQuery.definesColumn(timestamp)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.MissingFunctionalTimestampColumn",
                    scd2Table.getName(),
                    sourceQuery.getName(),
                    timestamp),
                scd2Table));
      }
    }
  }

  private static void validateParentHub(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      List<BvSourceQuery> sourceQueries,
      DataVaultModel dataVaultModel,
      BusinessVaultModel bvModel,
      IVariables variables) {
    String declared =
        scd2Table != null && !Utils.isEmpty(scd2Table.getParentHubName())
            ? (variables != null
                ? variables.resolve(scd2Table.getParentHubName())
                : scd2Table.getParentHubName())
            : null;
    boolean sourceQueryOnly =
        (satellites == null || satellites.isEmpty())
            && sourceQueries != null
            && !sourceQueries.isEmpty();
    if (Utils.isEmpty(declared)) {
      if (sourceQueryOnly
          && scd2Table.isIncludeHashKey()
          && !scd2Table.isIncludeHubBusinessKeys()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Warning.MissingParentHubSourceQuery",
                    scd2Table.getName()),
                scd2Table));
      }
      return;
    }
    if (!parentHubExists(declared, dataVaultModel, bvModel)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.UnknownParentHub",
                  scd2Table.getName(),
                  declared),
              scd2Table));
    }
  }

  private static void validateIdentifierLengths(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      List<BvSourceQuery> sourceQueries,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    int maxLength = DvIdentifierLimitSupport.DEFAULT_MAX;
    if (metadataProvider != null && bvConfig != null) {
      try {
        DatabaseMeta targetDatabase =
            BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
        maxLength = DvIdentifierLimitSupport.maxColumnNameLength(targetDatabase);
      } catch (Exception ignored) {
        // Keep the PostgreSQL default when the connection is missing.
      }
    }
    Set<String> names = new LinkedHashSet<>();
    if (scd2Table.getFieldMappings() != null) {
      for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
        if (mapping == null || !mapping.isIncludeInTarget()) {
          continue;
        }
        String target = resolve(mapping.getTargetFieldName(), variables);
        if (!Utils.isEmpty(target)) {
          names.add(target);
        }
      }
    }
    if (scd2Table.getCalculations() != null) {
      for (BvScd2Calculation calculation : scd2Table.getCalculations()) {
        if (calculation == null) {
          continue;
        }
        String target = resolve(calculation.getTargetFieldName(), variables);
        if (!Utils.isEmpty(target)) {
          names.add(target);
        }
      }
    }
    names.addAll(
        technicalColumnNames(
            scd2Table, satellites, sourceQueries, bvConfig, dvConfig, dataVaultModel, variables));
    if (scd2Table.isIncludeHubBusinessKeys() && scd2Table.isLoadHubBusinessKeys()) {
      DvHub hub = resolveSharedParentHub(scd2Table, satellites, dataVaultModel, variables);
      if (hub != null) {
        for (BusinessKey businessKey : hub.getDistinctBusinessKeys()) {
          if (businessKey == null || Utils.isEmpty(businessKey.getName())) {
            continue;
          }
          names.add(resolve(businessKey.getName(), variables));
        }
      }
    }
    for (String name : names) {
      if (Utils.isEmpty(name) || name.length() <= maxLength) {
        continue;
      }
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.IdentifierTooLong",
                  scd2Table.getName(),
                  name,
                  Integer.toString(name.length()),
                  Integer.toString(maxLength)),
              scd2Table));
    }
  }

  static boolean parentHubExists(
      String hubName, DataVaultModel dataVaultModel, BusinessVaultModel bvModel) {
    if (Utils.isEmpty(hubName)) {
      return false;
    }
    if (dataVaultModel != null) {
      if (dataVaultModel.findHub(hubName) != null) {
        return true;
      }
      IDvTable table = dataVaultModel.findTable(hubName);
      if (table instanceof DvHub) {
        return true;
      }
    }
    for (String listed :
        BusinessVaultSourceQuerySupport.listParentHubNames(bvModel, dataVaultModel)) {
      if (hubName.equals(listed)) {
        return true;
      }
    }
    return false;
  }

  private static void validateValidityFieldNames(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      List<BvSourceQuery> sourceQueries,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    String validFrom = BvScd2PipelineSupport.resolveValidFromField(scd2Table, bvConfig, variables);
    String validTo = BvScd2PipelineSupport.resolveValidToField(scd2Table, bvConfig, variables);
    if (!Utils.isEmpty(validFrom)
        && !Utils.isEmpty(validTo)
        && validFrom.equalsIgnoreCase(validTo)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.ValidFromEqualsValidTo",
                  scd2Table.getName(),
                  validFrom),
              scd2Table));
    }
    Set<String> others =
        technicalColumnNames(
            scd2Table, satellites, sourceQueries, bvConfig, dvConfig, dataVaultModel, variables);
    others.remove(emptyLower(validFrom));
    others.remove(emptyLower(validTo));
    addMappedAndCalculatedNames(others, scd2Table, variables);
    checkValidityCollision(remarks, scd2Table, validFrom, others);
    checkValidityCollision(remarks, scd2Table, validTo, others);
  }

  private static void checkValidityCollision(
      List<ICheckResult> remarks, BvScd2Table scd2Table, String fieldName, Set<String> others) {
    if (Utils.isEmpty(fieldName) || !others.contains(fieldName.toLowerCase())) {
      return;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_ERROR,
            BaseMessages.getString(
                PKG,
                "BvScd2FieldMappingValidationSupport.Error.ValidityFieldCollision",
                scd2Table.getName(),
                fieldName),
            scd2Table));
  }

  private static void validateIncrementalWatermark(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (scd2Table == null
        || !scd2Table.isIncrementalBuild()
        || Utils.isEmpty(scd2Table.getIncrementalWatermarkField())) {
      return;
    }
    String watermark = variables.resolve(scd2Table.getIncrementalWatermarkField());
    if (Utils.isEmpty(watermark)) {
      return;
    }
    Set<String> allowed = new HashSet<>();
    addReserved(
        allowed,
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            scd2Table, bvConfig, dvConfig, variables));
    addReserved(
        allowed, BvScd2PipelineSupport.resolveValidFromField(scd2Table, bvConfig, variables));
    addReserved(allowed, BvScd2PipelineSupport.resolveValidToField(scd2Table, bvConfig, variables));
    addMappedAndCalculatedNames(allowed, scd2Table, variables);
    if (!allowed.contains(watermark.toLowerCase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2FieldMappingValidationSupport.Error.UnknownIncrementalWatermark",
                  scd2Table.getName(),
                  watermark),
              scd2Table));
    }
  }

  private static void validateSourceQueryColumnsPresent(
      List<ICheckResult> remarks, BvScd2Table scd2Table, List<BvSourceQuery> sourceQueries) {
    if (sourceQueries == null) {
      return;
    }
    for (BvSourceQuery sourceQuery : sourceQueries) {
      if (sourceQuery != null && sourceQuery.getColumns().isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Warning.SourceQueryColumnsEmpty",
                    scd2Table.getName(),
                    sourceQuery.getName()),
                scd2Table));
      }
    }
  }

  private static boolean isGrainOrTimelineSource(
      DvSatellite satellite,
      BvSourceQuery sourceQuery,
      String sourceFieldName,
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    if (Utils.isEmpty(sourceFieldName)) {
      return false;
    }
    if (satellite != null && dataVaultModel != null) {
      String hashKey =
          BvScd2PipelineSupport.resolveHashKeyFieldName(satellite, dataVaultModel, variables);
      if (sourceFieldName.equalsIgnoreCase(hashKey)) {
        return true;
      }
      BvScd2SatelliteConfig config = findSatelliteConfig(scd2Table, satellite.getName(), variables);
      String timestamp =
          BvScd2PipelineSupport.resolveFunctionalTimestampFieldForSatellite(
              scd2Table, config, bvConfig, dvConfig, variables);
      if (sourceFieldName.equalsIgnoreCase(timestamp)) {
        return true;
      }
      if (dvConfig != null) {
        String loadDate = variables.resolve(dvConfig.getLoadDateField());
        if (sourceFieldName.equalsIgnoreCase(loadDate)) {
          return true;
        }
      }
    }
    if (sourceQuery != null) {
      if (sourceFieldName.equalsIgnoreCase(sourceQuery.resolvedHashKeyField(variables))) {
        return true;
      }
      if (sourceFieldName.equalsIgnoreCase(sourceQuery.resolvedHubHashKeyField(variables))) {
        return true;
      }
      if (sourceFieldName.equalsIgnoreCase(
          resolve(sourceQuery.getFunctionalTimestampField(), variables))) {
        return true;
      }
      if (sourceFieldName.equalsIgnoreCase(resolve(sourceQuery.getLoadDateField(), variables))) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> technicalColumnNames(
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      List<BvSourceQuery> sourceQueries,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    Set<String> names = new HashSet<>();
    addReserved(
        names,
        BvScd2PipelineSupport.resolveSharedHashKeyFieldName(
            scd2Table, satellites, sourceQueries, dataVaultModel, variables));
    addReserved(
        names,
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            scd2Table, bvConfig, dvConfig, variables));
    addReserved(names, BvScd2PipelineSupport.resolveValidFromField(scd2Table, bvConfig, variables));
    addReserved(names, BvScd2PipelineSupport.resolveValidToField(scd2Table, bvConfig, variables));
    addReserved(names, BvScd2PipelineSupport.resolveRecordSourceField(dvConfig, variables));
    if (bvConfig != null && bvConfig.isStoreLoadCycleId()) {
      addReserved(
          names, DvLoadCycleSupport.resolveFieldName(bvConfig.getLoadCycleIdField(), variables));
    }
    return names;
  }

  private static void addMappedAndCalculatedNames(
      Set<String> names, BvScd2Table scd2Table, IVariables variables) {
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
  }

  private static String emptyLower(String value) {
    return Utils.isEmpty(value) ? "" : value.toLowerCase();
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static void validateSourceQueryHashKeyMapping(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      List<BvSourceQuery> sourceQueries,
      List<DvSatellite> satellites,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    if (sourceQueries == null || sourceQueries.isEmpty()) {
      return;
    }
    String grainHashKey =
        BvScd2PipelineSupport.resolveSharedHashKeyFieldName(
            scd2Table, satellites, sourceQueries, dataVaultModel, variables);
    for (BvSourceQuery sourceQuery : sourceQueries) {
      if (sourceQuery == null) {
        continue;
      }
      String sourceColumn = sourceQuery.resolvedHashKeyField(variables);
      String mappedHubField = sourceQuery.resolvedHubHashKeyField(variables);
      if (Utils.isEmpty(sourceColumn) || Utils.isEmpty(grainHashKey)) {
        continue;
      }
      if (!sourceColumn.equals(grainHashKey) && Utils.isEmpty(sourceQuery.getHubHashKeyField())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.SourceQueryHashKeyMappingRequired",
                    scd2Table.getName(),
                    sourceQuery.getName(),
                    sourceColumn,
                    grainHashKey),
                scd2Table));
      } else if (sourceQuery.hashKeyNeedsRename(variables)
          && !mappedHubField.equals(grainHashKey)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2FieldMappingValidationSupport.Error.SourceQueryHashKeyMappingMismatch",
                    scd2Table.getName(),
                    sourceQuery.getName(),
                    mappedHubField,
                    grainHashKey),
                scd2Table));
      }
    }
  }
}
