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
 *
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
        resolveSourceFields(recordSource, options, metadataProvider, variables, checkSource, remarks);
    if (resolved == null) {
      return;
    }

    for (BusinessKey bk : mappedKeys) {
      if (bk == null || Utils.isEmpty(bk.getName())) {
        continue;
      }
      String sourceFieldName = resolveSourceFieldName(bk.getSourceFieldName(), bk.getName(), variables);
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
        IValueMeta targetMeta =
            buildTargetValueMetaForHubBusinessKey(bk, storedField, variables);
        String mappingContext =
            BaseMessages.getString(
                PKG,
                "DvFieldMappingValidation.Context.HubBusinessKey",
                bk.getName(),
                sourceFieldName,
                recordSource.getName());
        validateMapping(sourceMeta, targetMeta, mappingContext, checkSource, remarks);
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
        remarks.add(
            new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
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
        resolveSourceFields(recordSource, options, metadataProvider, variables, checkSource, remarks);
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
          variables,
          checkSource,
          remarks);
    }

    if (satellite.hasDrivingKey()) {
      String drivingKeySourceField =
          resolveName(satellite.getDrivingKeySourceField(), variables);
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
          validateMapping(sourceMeta, targetMeta, mappingContext, checkSource, remarks);
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
          remarks.add(
              new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
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
        validateMapping(sourceMeta, targetMeta, mappingContext, checkSource, remarks);
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
        remarks.add(
            new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
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
        String resolvedRef = variables != null ? variables.resolve(recordSourceRef) : recordSourceRef;
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
    // Parent identity values come from the sat feed; hub defines BK names/order. Optional
    // parentKeySourceFields is an ordered list of source columns only (same length as hub BKs).
    List<DvSatelliteParentKeySupport.ParentKeyField> parentKeys;
    try {
      parentKeys =
          DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, variables);
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
      IValueMeta sourceMeta = resolved.fields.get(sourceFieldName);
      if (sourceMeta == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.SatelliteHubBusinessKeyMissing",
                    sourceFieldName,
                    businessKeyName,
                    hub.getName(),
                    satelliteSourceName),
                checkSource));
        continue;
      }
      BusinessKey bk = logicalBkByName.get(businessKeyName);
      if (bk == null) {
        continue;
      }
      try {
        SourceField storedField = resolved.storedFields.get(sourceFieldName);
        IValueMeta targetMeta =
            buildTargetValueMetaForHubBusinessKey(bk, storedField, variables);
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
        remarks.add(
            new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      }
    }
  }

  /**
   * Business keys that hub load pipelines can use as source PK columns. Matches {@code
   * getQuotedPkFields} which only selects keys with a non-empty {@link BusinessKey#getSourceFieldName()}.
   */
  private static List<BusinessKey> filterBusinessKeysWithSourceField(List<BusinessKey> keys) {
    List<BusinessKey> mapped = new ArrayList<>();
    if (keys == null) {
      return mapped;
    }
    for (BusinessKey bk : keys) {
      if (bk != null && !Utils.isEmpty(bk.getSourceFieldName())) {
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
        validateMapping(sourceMeta, targetMeta, mappingContext, checkSource, remarks);
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
      DvHub hub,
      DvSatellite satellite,
      IVariables variables,
      List<SourceField> sourceFields) {
    Set<String> excluded = new HashSet<>();
    if (hub != null && hub.getBusinessKeys() != null) {
      for (BusinessKey bk : hub.getBusinessKeys()) {
        if (bk == null) {
          continue;
        }
        excluded.add(resolveSourceFieldName(bk.getSourceFieldName(), bk.getName(), variables));
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
    return (sourceType == IValueMeta.TYPE_NUMBER && targetType == IValueMeta.TYPE_BIGNUMBER)
        || (sourceType == IValueMeta.TYPE_BIGNUMBER && targetType == IValueMeta.TYPE_NUMBER);
  }

  static void validateMapping(
      IValueMeta sourceMeta,
      IValueMeta targetMeta,
      String context,
      ICheckResultSource checkSource,
      List<ICheckResult> remarks) {
    if (!typesCompatible(sourceMeta.getType(), targetMeta.getType())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvFieldMappingValidation.TypeMismatch",
                  context,
                  sourceMeta.getTypeDesc(),
                  targetMeta.getTypeDesc()),
              checkSource));
    }

    if (isLengthSensitive(sourceMeta.getType())) {
      int sourceLength = sourceMeta.getLength();
      int targetLength = targetMeta.getLength();
      if (sourceLength > 0 && targetLength > 0 && sourceLength > targetLength) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.SourceLengthExceedsTarget",
                    context,
                    sourceLength,
                    targetLength),
                checkSource));
      } else if (sourceLength > 0 && targetLength > 0 && sourceLength < targetLength) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.SourceLengthLessThanTarget",
                    context,
                    sourceLength,
                    targetLength),
                checkSource));
      }
    }

    if (ValueMetaBase.isNumeric(sourceMeta.getType())) {
      int sourcePrecision = sourceMeta.getPrecision();
      int targetPrecision = targetMeta.getPrecision();
      if (sourcePrecision > 0
          && targetPrecision > 0
          && sourcePrecision > targetPrecision) {
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
  }

  static IValueMeta buildTargetValueMetaForHubBusinessKey(BusinessKey bk, IVariables variables)
      throws HopException {
    return buildTargetValueMetaForHubBusinessKey(bk, null, variables);
  }

  static IValueMeta buildTargetValueMetaForHubBusinessKey(
      BusinessKey bk, SourceField storedField, IVariables variables) throws HopException {
    String name = bk.getName();
    int type =
        DvDataTypeSupport.resolveHopTypeId(
            resolveVariable(variables, bk.getDataType()), storedField);
    int length = Const.toInt(resolveVariable(variables, bk.getLength()), -1);
    int precision = Const.toInt(resolveVariable(variables, bk.getPrecision()), -1);
    IValueMeta meta = ValueMetaFactory.createValueMeta(name, type, length, precision);
    if (meta == null || meta.getType() == IValueMeta.TYPE_NONE) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvFieldMappingValidation.InvalidBusinessKeyType", bk.getName()));
    }
    return meta;
  }

  static IValueMeta buildTargetValueMetaForSatelliteAttribute(
      SatelliteAttribute attr, SourceField storedField, IVariables variables)
      throws HopException {
    String name = attr.getName();
    int typeId =
        DvDataTypeSupport.resolveHopTypeId(
            resolveVariable(variables, attr.getDataType()), storedField);
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
    return ValueMetaFactory.createValueMeta(name, typeId, length, precision);
  }

  static IValueMeta valueMetaFromSourceField(SourceField sf, IVariables variables)
      throws HopPluginException {
    String name = resolveName(sf.getName(), variables);
    int type = sf.getHopType();
    if (type <= 0) {
      type = IValueMeta.TYPE_STRING;
    }
    IValueMeta vm =
        ValueMetaFactory.createValueMeta(
            name,
            type,
            Const.toInt(resolveVariable(variables, sf.getLength()), -1),
            Const.toInt(resolveVariable(variables, sf.getPrecision()), -1));
    return vm;
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
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), checkSource));
      return null;
    }

    boolean detailed =
        options != null && options.isDetailedDataTypeChecking();
    IDvSource dvSource = recordSource.getDvSourceOrDefault();

    if (!detailed) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(
                  PKG,
                  "DvFieldMappingValidation.UsingStoredMetadata",
                  recordSource.getName()),
              checkSource));
      return ResolvedSourceFields.fromStored(storedByName, variables);
    }

    if (!dvSource.supportsLiveFieldResolution()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(
                  PKG,
                  "DvFieldMappingValidation.LiveResolutionSkipped",
                  recordSource.getName()),
              checkSource));
      return ResolvedSourceFields.fromStored(storedByName, variables);
    }

    try {
      IRowMeta liveRowMeta = dvSource.resolveLiveFields(variables, metadataProvider);
      if (liveRowMeta == null || liveRowMeta.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DvFieldMappingValidation.LiveResolutionEmpty",
                    recordSource.getName()),
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
      return metadataProvider
          .getSerializer(DatabaseMeta.class)
          .load(config.getTargetDatabase());
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
          fields.put(name, vm);
        }
      }
      return new ResolvedSourceFields(fields, storedByName, true);
    }
  }
}