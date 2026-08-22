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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceLiveSchemaSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Validates source-to-target field type mappings for Data Vault load pipelines. */
public final class DvFieldMappingValidationSupport {

  private static final Class<?> PKG = DvFieldMappingValidationSupport.class;

  private DvFieldMappingValidationSupport() {}

  public static void validateHubBusinessKeys(
      DvHub hub,
      DataVaultSource recordSource,
      DataVaultConfiguration config,
      DatabaseMeta targetDatabaseMeta,
      DvModelCheckOptions options,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (hub == null || recordSource == null || hub.getBusinessKeys() == null) {
      return;
    }
    String sourceName = resolveName(recordSource.getName(), variables);
    List<BusinessKey> sourceKeys = hub.getBusinessKeysForSource(sourceName, variables);
    List<BusinessKey> mappedKeys = filterBusinessKeysWithSourceField(sourceKeys);
    if (mappedKeys.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvFieldMappingValidation.HubBusinessKeyNotMappedForSource",
                  hub.getName(),
                  sourceName),
              checkSource));
      return;
    }

    ResolvedSourceFields resolved =
        resolveSourceFields(
            recordSource, options, metadataProvider, variables, checkSource, remarks);
    if (resolved == null) {
      return;
    }

    for (BusinessKey bk : mappedKeys) {
      if (bk == null || Utils.isEmpty(bk.getName())) {
        continue;
      }
      List<String> sourceParts = bk.resolveSourceParts();
      if (sourceParts.isEmpty()) {
        sourceParts =
            List.of(resolveSourceFieldName(bk.getSourceFieldName(), bk.getName(), variables));
      }
      for (String part : sourceParts) {
        String sourceFieldName = resolveName(part, variables);
        if (Utils.isEmpty(sourceFieldName)) {
          continue;
        }
        IValueMeta sourceMeta = resolved.fields.get(sourceFieldName);
        if (sourceMeta == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DvFieldMappingValidation.SourceFieldMissing",
                      sourceFieldName,
                      recordSource.getName(),
                      bk.getName()),
                  checkSource));
          continue;
        }
        try {
          SourceField storedField = resolved.storedFields.get(sourceFieldName);
          IValueMeta targetMeta = buildTargetValueMetaForHubBusinessKey(bk, storedField, variables);
          String mappingContext =
              BaseMessages.getString(
                  PKG,
                  "DvFieldMappingValidation.Context.HubBusinessKey",
                  bk.getName(),
                  sourceFieldName,
                  recordSource.getName());
          validateMapping(
              sourceMeta, targetMeta, mappingContext, targetDatabaseMeta, checkSource, remarks);
          if (options != null
              && options.isDetailedDataTypeChecking()
              && resolved.usedLive
              && targetDatabaseMeta != null) {
            DvSqlPhysicalTypeValidationSupport.validatePhysicalSqlTypeMapping(
                sourceMeta,
                targetMeta,
                mappingContext,
                targetDatabaseMeta,
                config,
                checkSource,
                remarks);
          }
          if (resolved.usedLive) {
            addStoredDriftWarnings(
                resolved.storedFields,
                sourceFieldName,
                sourceMeta,
                recordSource.getName(),
                bk.getName(),
                variables,
                checkSource,
                remarks);
          }
        } catch (HopException e) {
          remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
        }
      }
    }
  }

  public static void validateSatelliteMappings(
      DvSatellite satellite,
      DataVaultModel model,
      DvModelCheckOptions options,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (satellite == null || Utils.isEmpty(satellite.getRecordSourceName())) {
      return;
    }
    DataVaultSource recordSource;
    try {
      recordSource = satellite.resolveRecordSource(variables, metadataProvider, model);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      return;
    }
    ResolvedSourceFields resolved =
        resolveSourceFields(
            recordSource, options, metadataProvider, variables, checkSource, remarks);
    if (resolved == null) {
      return;
    }
    DataVaultConfiguration config = model != null ? model.getConfigurationOrDefault() : null;
    DatabaseMeta targetDatabaseMeta = resolveTargetDatabaseMeta(model, metadataProvider);

    if (!Utils.isEmpty(satellite.getHubName()) && model != null) {
      validateSatelliteHubBusinessKeys(
          satellite,
          model,
          resolved,
          recordSource,
          targetDatabaseMeta,
          variables,
          checkSource,
          remarks);
    }

    if (satellite.hasDrivingKey()) {
      String drivingKeySourceField = resolveName(satellite.getDrivingKeySourceField(), variables);
      IValueMeta sourceMeta = resolved.fields.get(drivingKeySourceField);
      if (sourceMeta == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.SourceFieldMissing",
                    drivingKeySourceField,
                    recordSource.getName(),
                    satellite.getDrivingKey()),
                checkSource));
      } else {
        try {
          SourceField stored = resolved.storedFields.get(drivingKeySourceField);
          IValueMeta targetMeta =
              stored != null
                  ? valueMetaFromSourceField(stored, variables)
                  : cloneValueMeta(sourceMeta);
          targetMeta.setName(resolveName(satellite.getDrivingKey(), variables));
          String mappingContext =
              BaseMessages.getString(
                  PKG,
                  "DvFieldMappingValidation.Context.SatelliteDrivingKey",
                  satellite.getDrivingKey(),
                  drivingKeySourceField,
                  recordSource.getName());
          validateMapping(
              sourceMeta, targetMeta, mappingContext, targetDatabaseMeta, checkSource, remarks);
          validatePhysicalSqlTypeIfDetailed(
              options,
              resolved.usedLive,
              sourceMeta,
              targetMeta,
              mappingContext,
              targetDatabaseMeta,
              config,
              checkSource,
              remarks,
              DvSqlPhysicalTypeValidationSupport.RemediationKind.SATELLITE_ORDER_KEY);
          if (resolved.usedLive && stored != null) {
            addStoredDriftWarnings(
                resolved.storedFields,
                drivingKeySourceField,
                sourceMeta,
                recordSource.getName(),
                satellite.getDrivingKey(),
                variables,
                checkSource,
                remarks);
          }
        } catch (HopPluginException e) {
          remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
        }
      }
    }

    List<SatelliteAttribute> attributes = satellite.getAttributes();
    if (attributes == null || attributes.isEmpty()) {
      validateSatelliteAutoAttributes(
          satellite,
          model,
          resolved,
          recordSource,
          options,
          targetDatabaseMeta,
          config,
          variables,
          checkSource,
          remarks);
      return;
    }

    for (SatelliteAttribute attr : attributes) {
      if (attr == null || Utils.isEmpty(attr.getName())) {
        continue;
      }
      String sourceFieldName = resolveName(attr.getName(), variables);
      SourceField storedField = resolved.storedFields.get(sourceFieldName);
      IValueMeta sourceMeta = resolved.fields.get(sourceFieldName);
      if (sourceMeta == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.SourceFieldMissing",
                    sourceFieldName,
                    recordSource.getName(),
                    attr.getName()),
                checkSource));
        continue;
      }
      try {
        IValueMeta targetMeta =
            buildTargetValueMetaForSatelliteAttribute(attr, storedField, variables);
        String mappingContext =
            BaseMessages.getString(
                PKG,
                "DvFieldMappingValidation.Context.SatelliteAttribute",
                attr.getName(),
                sourceFieldName,
                recordSource.getName());
        validateMapping(
            sourceMeta, targetMeta, mappingContext, targetDatabaseMeta, checkSource, remarks);
        validatePhysicalSqlTypeIfDetailed(
            options,
            resolved.usedLive,
            sourceMeta,
            targetMeta,
            mappingContext,
            targetDatabaseMeta,
            config,
            checkSource,
            remarks,
            DvSqlPhysicalTypeValidationSupport.RemediationKind.SATELLITE_ATTRIBUTE);
        if (resolved.usedLive && storedField != null) {
          addStoredDriftWarnings(
              resolved.storedFields,
              sourceFieldName,
              sourceMeta,
              recordSource.getName(),
              attr.getName(),
              variables,
              checkSource,
              remarks);
        }
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      }
    }
  }

  public static void validateHubRecordSourceFields(
      DvHub hub,
      DataVaultModel model,
      DvModelCheckOptions options,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (hub == null || model == null || hub.getRecordSources() == null) {
      return;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    try {
      DvSourceFieldMappingSupport.resolveRecordSourceFieldName(config, hub, variables);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      return;
    }

    for (String recordSourceRef : hub.getRecordSources()) {
      if (Utils.isEmpty(recordSourceRef)) {
        continue;
      }
      DataVaultSource recordSource;
      try {
        String resolvedRef =
            variables != null ? variables.resolve(recordSourceRef) : recordSourceRef;
        recordSource =
            org.apache.hop.datavault.catalog.DvSourceCatalogService.resolveSource(
                resolvedRef, model, variables, metadataProvider);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
        continue;
      }
      if (recordSource == null) {
        continue;
      }
      validateRecordSourceIndicator(
          recordSource,
          DvTableType.HUB,
          hub.getName(),
          options,
          metadataProvider,
          variables,
          checkSource,
          remarks);
    }
  }

  public static void validateSatelliteRecordSourceFields(
      DvSatellite satellite,
      DataVaultModel model,
      DvModelCheckOptions options,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (satellite == null || model == null || Utils.isEmpty(satellite.getRecordSourceName())) {
      return;
    }
    // VaultSpeed-style satellites omit the physical source-indicator column; feed binding remains
    // required, but the catalog feed need not configure a static/field indicator for this sat.
    if (!satellite.isStoreRecordSource()) {
      return;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    try {
      DvSourceFieldMappingSupport.resolveRecordSourceFieldNameForSatellite(
          config, model, satellite, variables);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      return;
    }

    DataVaultSource recordSource;
    try {
      recordSource = satellite.resolveRecordSource(variables, metadataProvider, model);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      return;
    }
    if (recordSource == null) {
      return;
    }
    validateRecordSourceIndicator(
        recordSource,
        DvTableType.SATELLITE,
        satellite.getName(),
        options,
        metadataProvider,
        variables,
        checkSource,
        remarks);
  }

  public static void validateLinkRecordSourceFields(
      DvLink link,
      DataVaultModel model,
      DvModelCheckOptions options,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (link == null || model == null || link.getLinkHubSources() == null) {
      return;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    try {
      DvSourceFieldMappingSupport.resolveRecordSourceFieldName(config, link, variables);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      return;
    }

    for (DvLink.DvLinkHubSource linkHubSource : link.getLinkHubSources()) {
      if (linkHubSource == null || Utils.isEmpty(linkHubSource.getSourceName())) {
        continue;
      }
      DataVaultSource recordSource;
      try {
        recordSource = linkHubSource.resolveSource(variables, metadataProvider, model);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
        continue;
      }
      if (recordSource == null) {
        continue;
      }
      validateRecordSourceIndicator(
          recordSource,
          DvTableType.LINK,
          link.getName(),
          options,
          metadataProvider,
          variables,
          checkSource,
          remarks);
    }
  }

  private static void validateRecordSourceIndicator(
      DataVaultSource recordSource,
      DvTableType tableType,
      String tableName,
      DvModelCheckOptions options,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    String indicatorField = resolveName(recordSource.getSourceIndicatorField(), variables);
    String staticIndicator = recordSource.getSourceIndicator();
    if (Utils.isEmpty(indicatorField) && Utils.isEmpty(staticIndicator)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvSourceFieldMapping.MissingSourceIndicator",
                  recordSource.getName(),
                  tableType,
                  tableName),
              checkSource));
      return;
    }

    if (!Utils.isEmpty(indicatorField)) {
      ResolvedSourceFields resolved =
          resolveSourceFields(
              recordSource, options, metadataProvider, variables, checkSource, remarks);
      if (resolved == null) {
        return;
      }
      if (!resolved.fields.containsKey(indicatorField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvSourceFieldMapping.SourceIndicatorFieldMissing",
                    indicatorField,
                    recordSource.getName(),
                    tableType,
                    tableName),
                checkSource));
      }
    }
  }

  public static void validateLinkHubKeyFields(
      DvLink link,
      DataVaultModel model,
      DvModelCheckOptions options,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (link == null || link.getLinkHubSources() == null || model == null) {
      return;
    }
    for (DvLink.DvLinkHubSource linkHubSource : link.getLinkHubSources()) {
      if (linkHubSource == null || Utils.isEmpty(linkHubSource.getSourceName())) {
        continue;
      }
      DataVaultSource recordSource;
      try {
        recordSource = linkHubSource.resolveSource(variables, metadataProvider, model);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
        continue;
      }
      ResolvedSourceFields resolved =
          resolveSourceFields(
              recordSource, options, metadataProvider, variables, checkSource, remarks);
      if (resolved == null) {
        continue;
      }
      if (link.getHubNames() == null) {
        continue;
      }
      for (String hubName : link.getHubNames()) {
        if (Utils.isEmpty(hubName)) {
          continue;
        }
        DvLink.HubSourceKeyField hubSourceKeyField =
            DvLinkHubSourceKeyFieldSupport.findHubSourceKeyFieldOrNull(linkHubSource, hubName);
        if (hubSourceKeyField == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DvFieldMappingValidation.LinkHubMappingMissing",
                      hubName,
                      linkHubSource.getSourceName(),
                      link.getName()),
                  checkSource));
          continue;
        }
        DvHub hub = model.findHub(hubName, variables, metadataProvider);
        if (hub == null) {
          continue;
        }
        for (String partCountError :
            DvLinkHubSourceKeyFieldSupport.findCompositePartCountMismatches(
                hub, hubSourceKeyField, variables)) {
          remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, partCountError, checkSource));
        }
        List<DvLinkHubSourceKeyFieldSupport.ResolvedBusinessKeySource> resolvedMappings =
            DvLinkHubSourceKeyFieldSupport.resolveBusinessKeySources(
                hub, hubSourceKeyField, variables);
        if (resolvedMappings.isEmpty()) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DvFieldMappingValidation.LinkHubKeyFieldsMissing",
                      hubName,
                      linkHubSource.getSourceName(),
                      link.getName()),
                  checkSource));
          continue;
        }
        for (DvLinkHubSourceKeyFieldSupport.ResolvedBusinessKeySource resolvedMapping :
            resolvedMappings) {
          String businessKeyField = resolvedMapping.getBusinessKeyField();
          String sourceFieldName = resolvedMapping.getSourceFieldName();
          IValueMeta sourceMeta = resolved.fields.get(sourceFieldName);
          if (sourceMeta == null) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    BaseMessages.getString(
                        PKG,
                        "DvFieldMappingValidation.SourceFieldMissing",
                        sourceFieldName,
                        recordSource.getName(),
                        businessKeyField),
                    checkSource));
            continue;
          }
          BusinessKey hubBk = findHubBusinessKey(hub, businessKeyField);
          if (hubBk == null) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    BaseMessages.getString(
                        PKG,
                        "DvFieldMappingValidation.HubBusinessKeyMissing",
                        businessKeyField,
                        hubName),
                    checkSource));
            continue;
          }
          try {
            SourceField storedField = resolved.storedFields.get(sourceFieldName);
            IValueMeta targetMeta =
                buildTargetValueMetaForHubBusinessKey(hubBk, storedField, variables);
            validateMapping(
                sourceMeta,
                targetMeta,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.Context.LinkHubKey",
                    businessKeyField,
                    sourceFieldName,
                    recordSource.getName(),
                    hubName),
                resolveTargetDatabaseMeta(model, metadataProvider),
                checkSource,
                remarks);
            if (resolved.usedLive) {
              addStoredDriftWarnings(
                  resolved.storedFields,
                  sourceFieldName,
                  sourceMeta,
                  recordSource.getName(),
                  businessKeyField,
                  variables,
                  checkSource,
                  remarks);
            }
          } catch (HopException e) {
            remarks.add(
                new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
          }
        }
      }
    }
  }

  static void validateSatelliteHubBusinessKeys(
      DvSatellite satellite,
      DataVaultModel model,
      ResolvedSourceFields resolved,
      DataVaultSource recordSource,
      DatabaseMeta targetDatabaseMeta,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (satellite == null
        || model == null
        || resolved == null
        || recordSource == null
        || Utils.isEmpty(satellite.getHubName())) {
      return;
    }
    DvHub hub = model.findHub(satellite.getHubName(), variables, null);
    if (hub == null || hub.getBusinessKeys() == null) {
      return;
    }
    String satelliteSourceName = resolveName(recordSource.getName(), variables);
    // Parent identity values come from the sat feed; hub defines vault BKs + composite part order.
    // Optional parentKeySourceFields length = total hub hash-input part count.
    List<DvSatelliteParentKeySupport.ParentKeyField> parentKeys;
    try {
      parentKeys = DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, variables);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      return;
    }
    Map<String, BusinessKey> logicalBkByName = new HashMap<>();
    for (BusinessKey bk : hub.getDistinctBusinessKeys()) {
      if (bk != null && !Utils.isEmpty(bk.getName())) {
        logicalBkByName.putIfAbsent(resolveName(bk.getName(), variables), bk);
      }
    }
    for (DvSatelliteParentKeySupport.ParentKeyField parentKey : parentKeys) {
      String sourceFieldName = parentKey.getSourceFieldName();
      String businessKeyName = parentKey.getBusinessKeyName();
      String vaultBkName = parentKey.getVaultBusinessKeyName();
      IValueMeta sourceMeta = resolved.fields.get(sourceFieldName);
      if (sourceMeta == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.SatelliteHubBusinessKeyMissing",
                    sourceFieldName,
                    vaultBkName,
                    hub.getName(),
                    satelliteSourceName),
                checkSource));
        continue;
      }
      BusinessKey bk = logicalBkByName.get(resolveName(vaultBkName, variables));
      if (bk == null) {
        continue;
      }
      try {
        SourceField storedField = resolved.storedFields.get(sourceFieldName);
        IValueMeta targetMeta = buildTargetValueMetaForHubBusinessKey(bk, storedField, variables);
        validateMapping(
            sourceMeta,
            targetMeta,
            BaseMessages.getString(
                PKG,
                "DvFieldMappingValidation.Context.SatelliteHubBusinessKey",
                businessKeyName,
                sourceFieldName,
                satelliteSourceName,
                hub.getName()),
            targetDatabaseMeta,
            checkSource,
            remarks);
        if (resolved.usedLive) {
          addStoredDriftWarnings(
              resolved.storedFields,
              sourceFieldName,
              sourceMeta,
              satelliteSourceName,
              businessKeyName,
              variables,
              checkSource,
              remarks);
        }
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      }
    }
  }

  /**
   * Business keys that hub load pipelines can use as source PK columns. Matches {@code
   * getQuotedPkFields} which only selects keys with a non-empty {@link
   * BusinessKey#getSourceFieldName()}.
   */
  private static List<BusinessKey> filterBusinessKeysWithSourceField(List<BusinessKey> keys) {
    List<BusinessKey> mapped = new ArrayList<>();
    if (keys == null) {
      return mapped;
    }
    for (BusinessKey bk : keys) {
      if (bk != null && !bk.resolveSourceParts().isEmpty()) {
        mapped.add(bk);
      }
    }
    return mapped;
  }

  private static void validateSatelliteAutoAttributes(
      DvSatellite satellite,
      DataVaultModel model,
      ResolvedSourceFields resolved,
      DataVaultSource recordSource,
      DvModelCheckOptions options,
      DatabaseMeta targetDatabaseMeta,
      DataVaultConfiguration config,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    List<SourceField> autoFields = new ArrayList<>();
    try {
      List<SourceField> storedList = new ArrayList<>(resolved.storedFields.values());
      if (!Utils.isEmpty(satellite.getHubName()) && model != null) {
        DvHub hub = model.findHub(satellite.getHubName(), variables, null);
        if (hub != null) {
          autoFields =
              selectHubSatelliteAutoAttributeSourceFields(hub, satellite, variables, storedList);
        } else {
          autoFields = storedList;
        }
      } else {
        autoFields = storedList;
      }
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Error determining auto satellite attributes: " + e.getMessage(),
              checkSource));
      return;
    }

    for (SourceField sf : autoFields) {
      if (sf == null || Utils.isEmpty(sf.getName())) {
        continue;
      }
      String fieldName = resolveName(sf.getName(), variables);
      IValueMeta sourceMeta = resolved.fields.get(fieldName);
      if (sourceMeta == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.SourceFieldMissing",
                    fieldName,
                    recordSource.getName(),
                    fieldName),
                checkSource));
        continue;
      }
      try {
        IValueMeta targetMeta = valueMetaFromSourceField(sf, variables);
        String mappingContext =
            BaseMessages.getString(
                PKG,
                "DvFieldMappingValidation.Context.SatelliteAutoAttribute",
                fieldName,
                recordSource.getName());
        validateMapping(
            sourceMeta, targetMeta, mappingContext, targetDatabaseMeta, checkSource, remarks);
        validatePhysicalSqlTypeIfDetailed(
            options,
            resolved.usedLive,
            sourceMeta,
            targetMeta,
            mappingContext,
            targetDatabaseMeta,
            config,
            checkSource,
            remarks,
            DvSqlPhysicalTypeValidationSupport.RemediationKind.SATELLITE_ATTRIBUTE);
        if (resolved.usedLive) {
          addStoredDriftWarnings(
              resolved.storedFields,
              fieldName,
              sourceMeta,
              recordSource.getName(),
              fieldName,
              variables,
              checkSource,
              remarks);
        }
      } catch (HopPluginException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      }
    }
  }

  static List<SourceField> selectHubSatelliteAutoAttributeSourceFields(
      DvHub hub, DvSatellite satellite, IVariables variables, List<SourceField> sourceFields) {
    Set<String> excluded = new HashSet<>();
    if (hub != null && hub.getBusinessKeys() != null) {
      for (BusinessKey bk : hub.getBusinessKeys()) {
        if (bk == null) {
          continue;
        }
        for (String part : bk.resolveSourceParts()) {
          excluded.add(resolveName(part, variables));
        }
        if (bk.resolveSourceParts().isEmpty()) {
          excluded.add(resolveSourceFieldName(bk.getSourceFieldName(), bk.getName(), variables));
        }
      }
    }
    if (satellite.hasDrivingKey()) {
      excluded.add(resolveName(satellite.getDrivingKeySourceField(), variables));
      excluded.add(resolveName(satellite.getDrivingKey(), variables));
    }
    List<SourceField> selected = new ArrayList<>();
    for (SourceField sf : sourceFields) {
      if (sf == null || Utils.isEmpty(sf.getName())) {
        continue;
      }
      String name = resolveName(sf.getName(), variables);
      if (!excluded.contains(name)) {
        selected.add(sf);
      }
    }
    return selected;
  }

  private static boolean typesCompatible(int sourceType, int targetType) {
    if (sourceType == targetType) {
      return true;
    }
    // DECIMAL/NUMERIC: MySQL JDBC maps to BigNumber; PostgreSQL maps to Number.
    if ((sourceType == IValueMeta.TYPE_NUMBER && targetType == IValueMeta.TYPE_BIGNUMBER)
        || (sourceType == IValueMeta.TYPE_BIGNUMBER && targetType == IValueMeta.TYPE_NUMBER)) {
      return true;
    }
    // DATE vs TIMESTAMP: vault DDL often uses DATETIME(6) for both; JDBC/source may report either.
    return (sourceType == IValueMeta.TYPE_DATE && targetType == IValueMeta.TYPE_TIMESTAMP)
        || (sourceType == IValueMeta.TYPE_TIMESTAMP && targetType == IValueMeta.TYPE_DATE);
  }

  /**
   * Type compatibility using Hop type ids and, when JDBC mis-mapped a column to String, the native
   * SQL type on {@link IValueMeta#getOriginalColumnTypeName()}.
   */
  static boolean typesCompatible(IValueMeta sourceMeta, IValueMeta targetMeta) {
    if (sourceMeta == null || targetMeta == null) {
      return false;
    }
    int sourceType =
        DvDataTypeSupport.effectiveHopTypeId(
            sourceMeta.getType(), sourceMeta.getOriginalColumnTypeName());
    int targetType =
        DvDataTypeSupport.effectiveHopTypeId(
            targetMeta.getType(), targetMeta.getOriginalColumnTypeName());
    if (typesCompatible(sourceType, targetType)) {
      return true;
    }
    // Identical physical SQL types (e.g. both DATETIME) are compatible even if hop ids diverged.
    String sourceSql =
        DvDataTypeSupport.normalizeSqlTypeBase(sourceMeta.getOriginalColumnTypeName());
    String targetSql =
        DvDataTypeSupport.normalizeSqlTypeBase(targetMeta.getOriginalColumnTypeName());
    if (!Utils.isEmpty(sourceSql) && sourceSql.equals(targetSql)) {
      return true;
    }
    return false;
  }

  static void validateMapping(
      IValueMeta sourceMeta,
      IValueMeta targetMeta,
      String context,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    validateMapping(sourceMeta, targetMeta, context, null, checkSource, remarks);
  }

  /**
   * Validates source → target type/length/precision.
   *
   * <p><b>Length contract:</b> model/target {@link IValueMeta} lengths are <em>characters</em>
   * (e.g. satellite attribute 50). On SQL Server the vault stores UTF-8 {@code VARCHAR} with
   * capacity {@code modelLength × 3} (e.g. {@code VARCHAR(150)}). Overflow checks use {@link
   * DvDdlSupport#effectiveStringCapacity} so a character model length 50 is not reported as
   * capacity 50 when the vault column is 150 bytes.
   */
  static void validateMapping(
      IValueMeta sourceMeta,
      IValueMeta targetMeta,
      String context,
      DatabaseMeta targetDatabaseMeta,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (sourceMeta == null || targetMeta == null) {
      return;
    }

    // Align hop types with native SQL names before comparing (fixes DATETIME stored as String).
    int sourceType =
        DvDataTypeSupport.effectiveHopTypeId(
            sourceMeta.getType(), sourceMeta.getOriginalColumnTypeName());
    int targetType =
        DvDataTypeSupport.effectiveHopTypeId(
            targetMeta.getType(), targetMeta.getOriginalColumnTypeName());

    if (!typesCompatible(sourceMeta, targetMeta)) {
      String sourceDesc =
          sourceType != sourceMeta.getType()
              ? ValueMetaFactory.getValueMetaName(sourceType)
              : sourceMeta.getTypeDesc();
      String targetDesc =
          targetType != targetMeta.getType()
              ? ValueMetaFactory.getValueMetaName(targetType)
              : targetMeta.getTypeDesc();
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DvFieldMappingValidation.TypeMismatch", context, sourceDesc, targetDesc),
              checkSource));
    }

    if (isLengthSensitive(sourceType) || isLengthSensitive(targetType)) {
      // LONGTEXT/CLOB on SingleStore/MySQL often report display size 255; normalize before compare.
      DvSqlStringTypeSupport.normalizeStringLength(sourceMeta);
      DvSqlStringTypeSupport.normalizeStringLength(targetMeta);
      if (!DvSqlStringTypeSupport.skipStringLengthOverflowCheck(sourceMeta, targetMeta)) {
        int sourceLength = DvSqlStringTypeSupport.lengthForValidation(sourceMeta);
        int targetModelLength = DvSqlStringTypeSupport.lengthForValidation(targetMeta);
        if (sourceLength > 0 && targetModelLength > 0) {
          int targetCapacity =
              DvDdlSupport.effectiveStringCapacity(targetDatabaseMeta, targetModelLength);
          if (sourceLength > targetCapacity) {
            // Message args: context, sourceLen, capacity, modelLen (SQL Server may expand capacity)
            if (targetCapacity != targetModelLength) {
              remarks.add(
                  new CheckResult(
                      ICheckResult.TYPE_RESULT_ERROR,
                      BaseMessages.getString(
                          PKG,
                          "DvFieldMappingValidation.SourceLengthExceedsTargetCapacity",
                          context,
                          sourceLength,
                          targetCapacity,
                          targetModelLength),
                      checkSource));
            } else {
              remarks.add(
                  new CheckResult(
                      ICheckResult.TYPE_RESULT_ERROR,
                      BaseMessages.getString(
                          PKG,
                          "DvFieldMappingValidation.SourceLengthExceedsTarget",
                          context,
                          sourceLength,
                          targetModelLength),
                      checkSource));
            }
          }
          // Do not warn when source length < target: a wider target is safe (no truncation) and
          // JDBC display sizes (e.g. SingleStore reporting 255) often inflate the target number.
        }
      }
    }

    if (ValueMetaBase.isNumeric(sourceType) && ValueMetaBase.isNumeric(targetType)) {
      int sourcePrecision = sourceMeta.getPrecision();
      int targetPrecision = targetMeta.getPrecision();
      if (sourcePrecision > 0 && targetPrecision > 0 && sourcePrecision > targetPrecision) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.SourcePrecisionExceedsTarget",
                    context,
                    sourcePrecision,
                    targetPrecision),
                checkSource));
      }
    }

    validateTemporalFractionalPrecision(
        sourceMeta, targetMeta, context, targetDatabaseMeta, checkSource, remarks);
  }

  /**
   * Warns when source date/time fractional-second precision exceeds the target model or engine
   * capacity (e.g. nanoseconds → SingleStore/MySQL {@code DATETIME(6)}). Never an error: truncation
   * is often the best available mapping.
   */
  static void validateTemporalFractionalPrecision(
      IValueMeta sourceMeta,
      IValueMeta targetMeta,
      String context,
      DatabaseMeta targetDatabaseMeta,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (!isWarnTimestampFractionalPrecisionLossEnabled()) {
      return;
    }
    if (sourceMeta == null || targetMeta == null) {
      return;
    }
    int sourceType =
        DvDataTypeSupport.effectiveHopTypeId(
            sourceMeta.getType(), sourceMeta.getOriginalColumnTypeName());
    int targetType =
        DvDataTypeSupport.effectiveHopTypeId(
            targetMeta.getType(), targetMeta.getOriginalColumnTypeName());
    if (!isTemporalType(sourceType) || !isTemporalType(targetType)) {
      return;
    }
    int sourceFrac = temporalFractionalDigits(sourceMeta);
    if (sourceFrac <= 0) {
      return;
    }
    int targetFrac = temporalFractionalDigits(targetMeta);
    int engineMax = maxTemporalFractionalDigits(targetDatabaseMeta);
    int capacity = targetFrac >= 0 ? targetFrac : engineMax;
    if (engineMax >= 0) {
      capacity = targetFrac >= 0 ? Math.min(targetFrac, engineMax) : engineMax;
    }
    if (capacity < 0 || sourceFrac <= capacity) {
      return;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_WARNING,
            BaseMessages.getString(
                PKG,
                "DvFieldMappingValidation.TimestampFractionalPrecisionLoss",
                context,
                sourceFrac,
                capacity),
            checkSource));
  }

  static boolean isTemporalType(int hopType) {
    return hopType == IValueMeta.TYPE_DATE || hopType == IValueMeta.TYPE_TIMESTAMP;
  }

  /**
   * Fractional-second digits for a temporal value meta.
   *
   * <p>Preference order:
   *
   * <ol>
   *   <li>SQL type label ({@code DATETIME(6)}, {@code TIMESTAMP(3)}) — most reliable for reverse
   *       import
   *   <li>TIMESTAMP {@link IValueMeta#getLength()} (Hop/Database stores JDBC scale in length)
   *   <li>{@link IValueMeta#getOriginalScale()} when length is not a usable scale
   *   <li>{@link IValueMeta#getPrecision()}
   * </ol>
   *
   * <p>Note: {@code originalScale} defaults to {@code 0} on new value metas, so length must be
   * preferred over an unset scale of zero.
   */
  static int temporalFractionalDigits(IValueMeta meta) {
    if (meta == null) {
      return -1;
    }
    int effectiveType =
        DvDataTypeSupport.effectiveHopTypeId(meta.getType(), meta.getOriginalColumnTypeName());
    if (!isTemporalType(effectiveType)) {
      return -1;
    }
    int fromSql =
        DvDataTypeSupport.fractionalSecondsFromSqlTypeName(meta.getOriginalColumnTypeName());
    if (fromSql >= 0 && fromSql <= 9) {
      return fromSql;
    }
    // Hop stores TIMESTAMP scale in length (see Database.getValueFromSqlType Types.TIMESTAMP).
    int length = meta.getLength();
    if (length >= 0 && length <= 9) {
      return length;
    }
    int originalScale = meta.getOriginalScale();
    // Only trust originalScale when length was not a scale (e.g. display width) and scale is set.
    if (originalScale > 0 && originalScale <= 9) {
      return originalScale;
    }
    int precision = meta.getPrecision();
    if (precision >= 0 && precision <= 9) {
      return precision;
    }
    return -1;
  }

  /**
   * Engine-specific maximum fractional digits for vault datetime storage, or {@code -1} when
   * unknown / unlimited for this check.
   */
  static int maxTemporalFractionalDigits(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return -1;
    }
    String pluginId = databaseMeta.getPluginId();
    // SingleStore / MySQL / MariaDB: DATETIME(fsp) max fsp is 6 (microseconds).
    if (DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || "MARIADB".equalsIgnoreCase(pluginId)) {
      return 6;
    }
    // SQL Server DATETIME2 max scale is 7.
    if (DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || "MSSQL".equalsIgnoreCase(pluginId)) {
      return 7;
    }
    // PostgreSQL timestamp typically 0–6.
    if (DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return 6;
    }
    return -1;
  }

  private static boolean isWarnTimestampFractionalPrecisionLossEnabled() {
    try {
      return DataVaultConfigSingleton.getConfig().isWarnTimestampFractionalPrecisionLoss();
    } catch (Exception e) {
      return true;
    }
  }

  static IValueMeta buildTargetValueMetaForHubBusinessKey(BusinessKey bk, IVariables variables)
      throws HopException {
    return buildTargetValueMetaForHubBusinessKey(bk, null, variables);
  }

  static IValueMeta buildTargetValueMetaForHubBusinessKey(
      BusinessKey bk, SourceField storedField, IVariables variables) throws HopException {
    String name = bk.getName();
    String dataType = resolveVariable(variables, bk.getDataType());
    // resolveHopTypeId: explicit Hop labels win; SQL labels and source-field SQL correct JDBC
    // noise.
    int type = DvDataTypeSupport.resolveHopTypeId(dataType, storedField);
    int length = Const.toInt(resolveVariable(variables, bk.getLength()), -1);
    int precision = Const.toInt(resolveVariable(variables, bk.getPrecision()), -1);
    if (precision < 0) {
      int fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(dataType);
      if (fsp < 0 && storedField != null) {
        fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(storedField.getSourceDataType());
      }
      if (fsp >= 0) {
        precision = fsp;
        if (isTemporalType(type) && length < 0) {
          length = fsp;
        }
      }
    }
    IValueMeta meta = ValueMetaFactory.createValueMeta(name, type, length, precision);
    if (meta == null || meta.getType() == IValueMeta.TYPE_NONE) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvFieldMappingValidation.InvalidBusinessKeyType", bk.getName()));
    }
    applySqlTypeHint(meta, dataType, storedField);
    return meta;
  }

  static IValueMeta buildTargetValueMetaForSatelliteAttribute(
      SatelliteAttribute attr, SourceField storedField, IVariables variables) throws HopException {
    String name = attr.getName();
    String dataType = resolveVariable(variables, attr.getDataType());
    int typeId = DvDataTypeSupport.resolveHopTypeId(dataType, storedField);
    int length = Const.toInt(resolveVariable(variables, attr.getLength()), -1);
    int precision = Const.toInt(resolveVariable(variables, attr.getPrecision()), -1);
    if (storedField != null) {
      if (length <= 0) {
        length = Const.toInt(resolveVariable(variables, storedField.getLength()), -1);
      }
      if (precision <= 0) {
        precision = Const.toInt(resolveVariable(variables, storedField.getPrecision()), -1);
      }
    }
    if (precision < 0) {
      int fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(dataType);
      if (fsp < 0 && storedField != null) {
        fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(storedField.getSourceDataType());
      }
      if (fsp >= 0) {
        precision = fsp;
        if (isTemporalType(typeId) && length < 0) {
          length = fsp;
        }
      }
    }
    String sqlTypeHint = resolveSqlTypeHint(dataType, storedField);
    if (DvSqlStringTypeSupport.isLargeTextSqlType(sqlTypeHint)) {
      length = DvSqlStringTypeSupport.capacityForSqlStringType(sqlTypeHint, length);
    }
    int fromTypeName = DvDataTypeSupport.characterLengthFromSqlTypeName(sqlTypeHint);
    if (fromTypeName > 0 && (length <= 0 || length == DvSqlStringTypeSupport.TINYTEXT_CAPACITY)) {
      length = fromTypeName;
    }
    IValueMeta meta = ValueMetaFactory.createValueMeta(name, typeId, length, precision);
    applySqlTypeHint(meta, dataType, storedField);
    DvSqlStringTypeSupport.normalizeStringLength(meta);
    return meta;
  }

  static IValueMeta valueMetaFromSourceField(SourceField sf, IVariables variables)
      throws HopPluginException {
    String name = resolveName(sf.getName(), variables);
    // Prefer SQL type when hop type is missing or wrongly stored as String (DATETIME→String).
    int type = DvDataTypeSupport.effectiveHopTypeId(sf.getHopType(), sf.getSourceDataType());
    if (type <= 0) {
      type = IValueMeta.TYPE_STRING;
    }
    int length = Const.toInt(resolveVariable(variables, sf.getLength()), -1);
    int precision = Const.toInt(resolveVariable(variables, sf.getPrecision()), -1);
    if (DvSqlStringTypeSupport.isLargeTextSqlType(sf.getSourceDataType())) {
      length = DvSqlStringTypeSupport.capacityForSqlStringType(sf.getSourceDataType(), length);
    }
    int fromTypeName = DvDataTypeSupport.characterLengthFromSqlTypeName(sf.getSourceDataType());
    if (fromTypeName > 0 && (length <= 0 || length == DvSqlStringTypeSupport.TINYTEXT_CAPACITY)) {
      length = fromTypeName;
    }
    if (isTemporalType(type)) {
      int fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(sf.getSourceDataType());
      if (fsp >= 0) {
        if (precision < 0) {
          precision = fsp;
        }
        // Hop stores TIMESTAMP scale in length; prefer SQL fsp over a bogus JDBC scale (e.g. 9).
        if (length < 0 || length > fsp) {
          length = fsp;
        }
      }
    }
    IValueMeta vm = ValueMetaFactory.createValueMeta(name, type, length, precision);
    if (!Utils.isEmpty(sf.getSourceDataType())) {
      vm.setOriginalColumnTypeName(sf.getSourceDataType());
      int declared = DvDataTypeSupport.characterLengthFromSqlTypeName(sf.getSourceDataType());
      if (declared > 0) {
        vm.setOriginalPrecision(declared);
      }
    }
    DvSqlStringTypeSupport.normalizeStringLength(vm);
    return vm;
  }

  /**
   * Native SQL type to attach on the target value meta. Never substitutes the source field's SQL
   * type when the model label is an explicit Hop type name ({@code String}, {@code Integer}, …) —
   * that would make {@code effectiveHopTypeId(String, "int2")} look like Integer and hide real
   * mismatches.
   */
  private static String resolveSqlTypeHint(String dataType, SourceField storedField) {
    if (!Utils.isEmpty(dataType)) {
      if (DvSqlStringTypeSupport.isLargeTextSqlType(dataType)
          || DvDataTypeSupport.hopTypeIdFromSqlTypeName(dataType) > 0) {
        return dataType;
      }
      // Explicit Hop type name — do not attach source SQL type name.
      return null;
    }
    return storedField != null ? storedField.getSourceDataType() : null;
  }

  private static void applySqlTypeHint(IValueMeta meta, String dataType, SourceField storedField) {
    if (meta == null) {
      return;
    }
    String sqlTypeHint = resolveSqlTypeHint(dataType, storedField);
    if (!Utils.isEmpty(sqlTypeHint)) {
      meta.setOriginalColumnTypeName(sqlTypeHint);
      int declared = DvDataTypeSupport.characterLengthFromSqlTypeName(sqlTypeHint);
      if (declared > 0) {
        meta.setOriginalPrecision(declared);
      }
      int fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(sqlTypeHint);
      if (fsp >= 0) {
        meta.setOriginalScale(fsp);
      }
    }
  }

  private static ResolvedSourceFields resolveSourceFields(
      DataVaultSource recordSource,
      DvModelCheckOptions options,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    Map<String, SourceField> storedByName = new HashMap<>();
    try {
      for (SourceField sf : recordSource.getFields(metadataProvider)) {
        if (sf != null && !Utils.isEmpty(sf.getName())) {
          storedByName.put(resolveName(sf.getName(), variables), sf);
        }
      }
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      return null;
    }

    boolean detailed = options != null && options.isDetailedDataTypeChecking();
    IDvSource dvSource = recordSource.getDvSourceOrDefault();

    if (!detailed) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(
                  PKG, "DvFieldMappingValidation.UsingStoredMetadata", recordSource.getName()),
              checkSource));
      return ResolvedSourceFields.fromStored(storedByName, variables);
    }

    if (!dvSource.supportsLiveFieldResolution()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(
                  PKG, "DvFieldMappingValidation.LiveResolutionSkipped", recordSource.getName()),
              checkSource));
      return ResolvedSourceFields.fromStored(storedByName, variables);
    }

    try {
      IRowMeta liveRowMeta = resolveLiveFields(dvSource, options, variables, metadataProvider);
      if (liveRowMeta == null || liveRowMeta.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "DvFieldMappingValidation.LiveResolutionEmpty", recordSource.getName()),
                checkSource));
        return ResolvedSourceFields.fromStored(storedByName, variables);
      }
      return ResolvedSourceFields.fromLive(liveRowMeta, storedByName, variables);
    } catch (HopException e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvFieldMappingValidation.LiveResolutionFailed",
                  recordSource.getName(),
                  e.getMessage()),
              checkSource));
      return ResolvedSourceFields.fromStored(storedByName, variables);
    }
  }

  /**
   * Live field resolution with optional check-run cache (shared JDBC connections + schema
   * memoization).
   */
  private static IRowMeta resolveLiveFields(
      IDvSource dvSource,
      DvModelCheckOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    DvModelCheckCache cache = options != null ? options.getCache() : null;
    if (dvSource instanceof DvDatabaseSource dbSource) {
      return DvDatabaseSourceLiveSchemaSupport.resolveLiveFields(
          dbSource, variables, metadataProvider, cache);
    }
    if (cache != null) {
      String key =
          DvModelCheckCache.genericLiveFieldsKey(
              dvSource.getSourceType() != null ? dvSource.getSourceType().name() : "source",
              Const.NVL(
                  dvSource.getName(), Integer.toHexString(System.identityHashCode(dvSource))));
      IRowMeta cached = cache.getLiveFields(key);
      if (cached != null) {
        return cached;
      }
      IRowMeta liveRowMeta = dvSource.resolveLiveFields(variables, metadataProvider);
      if (liveRowMeta != null) {
        cache.putLiveFields(key, liveRowMeta);
      }
      return liveRowMeta;
    }
    return dvSource.resolveLiveFields(variables, metadataProvider);
  }

  private static void addStoredDriftWarnings(
      Map<String, SourceField> storedFields,
      String sourceFieldName,
      IValueMeta liveMeta,
      String recordSourceName,
      String targetFieldName,
      IVariables variables,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    SourceField stored = storedFields.get(sourceFieldName);
    if (stored == null) {
      return;
    }
    try {
      IValueMeta storedMeta = valueMetaFromSourceField(stored, variables);
      if (storedMeta.getType() != liveMeta.getType()
          || (isLengthSensitive(storedMeta.getType())
              && storedMeta.getLength() > 0
              && liveMeta.getLength() > 0
              && storedMeta.getLength() != liveMeta.getLength())
          || (ValueMetaBase.isNumeric(storedMeta.getType())
              && storedMeta.getPrecision() > 0
              && liveMeta.getPrecision() > 0
              && storedMeta.getPrecision() != liveMeta.getPrecision())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.StoredSourceDrift",
                    targetFieldName,
                    sourceFieldName,
                    recordSourceName),
                checkSource));
      }
    } catch (HopPluginException e) {
      // ignore drift check if stored meta is invalid
    }
  }

  private static BusinessKey findHubBusinessKey(DvHub hub, String businessKeyField) {
    if (hub == null || Utils.isEmpty(businessKeyField) || hub.getBusinessKeys() == null) {
      return null;
    }
    for (BusinessKey bk : hub.getBusinessKeys()) {
      if (bk != null && businessKeyField.equals(bk.getName())) {
        return bk;
      }
    }
    return null;
  }

  private static boolean isLengthSensitive(int hopType) {
    return hopType == IValueMeta.TYPE_STRING || hopType == IValueMeta.TYPE_BINARY;
  }

  private static String resolveSourceFieldName(
      String sourceFieldName, String fallbackName, IVariables variables) {
    String field = Utils.isEmpty(sourceFieldName) ? fallbackName : sourceFieldName;
    return resolveName(field, variables);
  }

  private static String resolveName(String name, IVariables variables) {
    return resolveVariable(variables, name);
  }

  private static String resolveVariable(IVariables variables, String value) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static IValueMeta cloneValueMeta(IValueMeta sourceMeta) throws HopPluginException {
    return ValueMetaFactory.cloneValueMeta(sourceMeta);
  }

  private static void validatePhysicalSqlTypeIfDetailed(
      DvModelCheckOptions options,
      boolean usedLive,
      IValueMeta sourceMeta,
      IValueMeta targetMeta,
      String mappingContext,
      DatabaseMeta targetDatabaseMeta,
      DataVaultConfiguration config,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    validatePhysicalSqlTypeIfDetailed(
        options,
        usedLive,
        sourceMeta,
        targetMeta,
        mappingContext,
        targetDatabaseMeta,
        config,
        checkSource,
        remarks,
        DvSqlPhysicalTypeValidationSupport.RemediationKind.HUB_BUSINESS_KEY);
  }

  private static void validatePhysicalSqlTypeIfDetailed(
      DvModelCheckOptions options,
      boolean usedLive,
      IValueMeta sourceMeta,
      IValueMeta targetMeta,
      String mappingContext,
      DatabaseMeta targetDatabaseMeta,
      DataVaultConfiguration config,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks,
      DvSqlPhysicalTypeValidationSupport.RemediationKind kind) {
    if (options == null
        || !options.isDetailedDataTypeChecking()
        || !usedLive
        || targetDatabaseMeta == null) {
      return;
    }
    DvSqlPhysicalTypeValidationSupport.validatePhysicalSqlTypeMapping(
        sourceMeta,
        targetMeta,
        mappingContext,
        targetDatabaseMeta,
        config,
        checkSource,
        remarks,
        kind);
  }

  private static DatabaseMeta resolveTargetDatabaseMeta(
      DataVaultModel model, IHopMetadataProvider metadataProvider) {
    if (model == null || metadataProvider == null) {
      return null;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    if (config == null || Utils.isEmpty(config.getTargetDatabase())) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(DatabaseMeta.class).load(config.getTargetDatabase());
    } catch (Exception e) {
      return null;
    }
  }

  private static final class ResolvedSourceFields {
    final Map<String, IValueMeta> fields;
    final Map<String, SourceField> storedFields;
    final boolean usedLive;

    private ResolvedSourceFields(
        Map<String, IValueMeta> fields, Map<String, SourceField> storedFields, boolean usedLive) {
      this.fields = fields;
      this.storedFields = storedFields;
      this.usedLive = usedLive;
    }

    static ResolvedSourceFields fromStored(
        Map<String, SourceField> storedByName, IVariables variables) {
      Map<String, IValueMeta> fields = new HashMap<>();
      for (Map.Entry<String, SourceField> entry : storedByName.entrySet()) {
        try {
          fields.put(entry.getKey(), valueMetaFromSourceField(entry.getValue(), variables));
        } catch (HopPluginException e) {
          // skip invalid stored field
        }
      }
      return new ResolvedSourceFields(fields, storedByName, false);
    }

    static ResolvedSourceFields fromLive(
        IRowMeta liveRowMeta, Map<String, SourceField> storedByName, IVariables variables) {
      Map<String, IValueMeta> fields = new HashMap<>();
      for (IValueMeta vm : liveRowMeta.getValueMetaList()) {
        if (vm != null && !Utils.isEmpty(vm.getName())) {
          String name = resolveName(vm.getName(), variables);
          try {
            IValueMeta copy = ValueMetaFactory.cloneValueMeta(vm);
            SourceField stored = storedByName.get(name);
            IValueMeta reconciled = reconcileLiveWithStoredCatalog(copy, stored, variables);
            fields.put(name, reconciled);
          } catch (HopPluginException e) {
            SourceField stored = storedByName.get(name);
            try {
              fields.put(name, reconcileLiveWithStoredCatalog(vm, stored, variables));
            } catch (HopPluginException ignored) {
              DvSqlStringTypeSupport.normalizeStringLength(vm);
              fields.put(name, vm);
            }
          }
        }
      }
      return new ResolvedSourceFields(fields, storedByName, true);
    }
  }

  /**
   * Merges live JDBC {@link IValueMeta} with catalog/stored {@link SourceField}.
   *
   * <p>SingleStore/MySQL {@code getTableFieldsMeta} often reports wrong display sizes (255 for
   * {@code VARCHAR(150)}) and sometimes maps {@code DATETIME} to String. When the data catalog
   * already holds the correct declared type/length (matching the physical table), prefer those
   * dimensions over JDBC noise so source→target validation does not false-fail.
   */
  static IValueMeta reconcileLiveWithStoredCatalog(
      IValueMeta live, SourceField stored, IVariables variables) throws HopPluginException {
    if (live == null) {
      return null;
    }
    if (stored == null) {
      DvSqlStringTypeSupport.normalizeStringLength(live);
      return live;
    }

    String sqlType =
        !Utils.isEmpty(live.getOriginalColumnTypeName())
            ? live.getOriginalColumnTypeName()
            : stored.getSourceDataType();
    int hopType = DvDataTypeSupport.effectiveHopTypeId(live.getType(), sqlType);
    // Catalog hop type + SQL can correct a live String mis-map when live TYPE_NAME is missing.
    hopType = DvDataTypeSupport.effectiveHopTypeId(hopType, stored.getSourceDataType());
    if (stored.getHopType() > 0
        && hopType == IValueMeta.TYPE_STRING
        && stored.getHopType() != IValueMeta.TYPE_STRING
        && DvDataTypeSupport.hopTypeIdFromSqlTypeName(stored.getSourceDataType())
            == stored.getHopType()) {
      hopType = stored.getHopType();
    }

    int length = live.getLength();
    int precision = live.getPrecision();
    int storedLength = Const.toInt(resolveVariable(variables, stored.getLength()), -1);
    int storedPrecision = Const.toInt(resolveVariable(variables, stored.getPrecision()), -1);

    if (isLengthSensitive(hopType) || hopType == IValueMeta.TYPE_STRING) {
      // Prefer declared catalog length over classic JDBC display-size bugs.
      if (storedLength > 0
          && (length <= 0
              || length == storedLength
              || isClassicJdbcDisplaySizeNoise(length, storedLength))) {
        length = storedLength;
      }
      if (DvSqlStringTypeSupport.isLargeTextSqlType(sqlType)
          || DvSqlStringTypeSupport.isLargeTextSqlType(stored.getSourceDataType())) {
        String largeType =
            DvSqlStringTypeSupport.isLargeTextSqlType(sqlType)
                ? sqlType
                : stored.getSourceDataType();
        length = DvSqlStringTypeSupport.capacityForSqlStringType(largeType, length);
      }
    } else if (isTemporalType(hopType)) {
      int fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(sqlType);
      if (fsp < 0) {
        fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(stored.getSourceDataType());
      }
      if (fsp < 0 && storedPrecision >= 0 && storedPrecision <= 9) {
        fsp = storedPrecision;
      }
      if (fsp >= 0) {
        // Prefer catalog/SQL fsp over bogus live scale (e.g. 9 for DATETIME(6)).
        if (length < 0 || length > 9 || (fsp > 0 && length != fsp && length == 9)) {
          length = fsp;
        }
        if (precision < 0 || precision > 9 || (fsp > 0 && precision != fsp && precision == 9)) {
          precision = fsp;
        }
      }
    } else if (ValueMetaBase.isNumeric(hopType) && storedPrecision >= 0 && precision < 0) {
      precision = storedPrecision;
    }

    IValueMeta reconciled;
    if (hopType != live.getType()
        || length != live.getLength()
        || precision != live.getPrecision()) {
      reconciled = ValueMetaFactory.createValueMeta(live.getName(), hopType, length, precision);
    } else {
      reconciled = live;
      reconciled.setLength(length, precision);
    }

    if (!Utils.isEmpty(sqlType)) {
      reconciled.setOriginalColumnTypeName(sqlType);
    } else if (!Utils.isEmpty(stored.getSourceDataType())) {
      reconciled.setOriginalColumnTypeName(stored.getSourceDataType());
    }

    if (storedLength > 0
        && (reconciled.getOriginalPrecision() <= 0
            || isClassicJdbcDisplaySizeNoise(reconciled.getOriginalPrecision(), storedLength))) {
      reconciled.setOriginalPrecision(storedLength);
    }

    int fspForScale =
        DvDataTypeSupport.fractionalSecondsFromSqlTypeName(reconciled.getOriginalColumnTypeName());
    if (fspForScale >= 0) {
      reconciled.setOriginalScale(fspForScale);
    } else if (storedPrecision >= 0 && storedPrecision <= 9 && isTemporalType(hopType)) {
      reconciled.setOriginalScale(storedPrecision);
    }

    DvSqlStringTypeSupport.normalizeStringLength(reconciled);
    return reconciled;
  }

  /**
   * True when {@code liveLength} looks like MySQL/SingleStore display-size noise relative to the
   * catalog-declared length (255 default, or utf8/utf8mb4 byte multipliers).
   */
  static boolean isClassicJdbcDisplaySizeNoise(int liveLength, int declaredLength) {
    if (declaredLength <= 0 || liveLength <= 0) {
      return false;
    }
    if (liveLength == declaredLength) {
      return false;
    }
    if (liveLength == DvSqlStringTypeSupport.TINYTEXT_CAPACITY
        && declaredLength != DvSqlStringTypeSupport.TINYTEXT_CAPACITY
        && declaredLength < DvSqlStringTypeSupport.CLOB_LENGTH) {
      return true;
    }
    return liveLength == declaredLength * 3 || liveLength == declaredLength * 4;
  }
}
