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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Kimball validation rules for dimensional model tables. */
public final class DmValidationSupport {

  private static final Class<?> PKG = DmValidationSupport.class;

  private DmValidationSupport() {}

  public static void validateConfiguration(
      List<ICheckResult> remarks,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || model == null) {
      return;
    }
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    if (Utils.isEmpty(config.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DmValidationSupport.CheckResult.MissingTargetDatabase"),
              null));
    } else if (metadataProvider != null) {
      try {
        DmTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), null));
      }
    }
    validateConformedDimensions(remarks, model, metadataProvider, variables);
  }

  public static void validateJunkDimension(
      List<ICheckResult> remarks,
      DmJunkDimension junkDimension,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || junkDimension == null) {
      return;
    }
    if (junkDimension.getKeyFieldsOrEmpty().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingJunkKeyFields",
                  junkDimension.getName()),
              junkDimension));
    }
    if (!DmJunkDimensionSupport.isFactEmbedded(junkDimension)) {
      validateSourceConfiguration(remarks, junkDimension, null, metadataProvider, variables);
    } else {
      validateJunkFactTableSource(remarks, junkDimension, null, variables);
    }
    validateJunkSurrogateKey(remarks, junkDimension, variables);
    validateJunkHashCode(remarks, junkDimension, variables);
    validateTargetLayout(remarks, junkDimension, null, metadataProvider, variables);
  }

  public static void validateRangeDimension(
      List<ICheckResult> remarks, DmRangeDimension rangeDimension, IVariables variables) {
    if (remarks == null || rangeDimension == null) {
      return;
    }
    if (rangeDimension.getBandsOrEmpty().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingRangeBands",
                  rangeDimension.getName()),
              rangeDimension));
    }
    if (Utils.isEmpty(rangeDimension.getFallBackLabel())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingRangeFallBackLabel",
                  rangeDimension.getName()),
              rangeDimension));
    }
    int bandIndex = 0;
    for (DmRangeBand band : rangeDimension.getBandsOrEmpty()) {
      bandIndex++;
      if (band == null || Utils.isEmpty(band.getLabel())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyRangeBandLabel",
                    rangeDimension.getName(),
                    bandIndex),
                rangeDimension));
      }
      try {
        parseRangeBound(band != null ? band.getLowerBound() : null, true);
        parseRangeBound(band != null ? band.getUpperBound() : null, false);
      } catch (NumberFormatException e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.InvalidRangeBandBounds",
                    rangeDimension.getName(),
                    bandIndex),
                rangeDimension));
      }
    }
    warnOnOverlappingRangeBands(remarks, rangeDimension);
  }

  public static void validateFactlessFact(
      List<ICheckResult> remarks,
      DmFactlessFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    validateDimensionRoles(
        remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
    validateFactDimensionLookupDate(
        remarks, fact, fact.getDimensionRolesOrEmpty(), model, metadataProvider, variables);
    validateDegenerateDimensions(remarks, fact, model, metadataProvider, variables);
    validateRangeDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateJunkDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateSourceConfiguration(remarks, fact, model, metadataProvider, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  public static void validatePeriodicSnapshotFact(
      List<ICheckResult> remarks,
      DmPeriodicSnapshotFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    if (Utils.isEmpty(resolve(fact.getSnapshotDateField(), variables))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingSnapshotDateField", fact.getName()),
              fact));
    }
    validateDimensionRoles(
        remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
    validateFactDimensionLookupDate(
        remarks, fact, fact.getDimensionRolesOrEmpty(), model, metadataProvider, variables);
    validateMeasures(remarks, fact, variables);
    validateDegenerateDimensions(remarks, fact, model, metadataProvider, variables);
    validateRangeDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateJunkDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateSourceConfiguration(remarks, fact, model, metadataProvider, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  public static void validateAccumulatingSnapshotFact(
      List<ICheckResult> remarks,
      DmAccumulatingSnapshotFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    if (fact.getGrainKeysOrEmpty().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingGrainKeys", fact.getName()),
              fact));
    }
    validateDimensionRoles(
        remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
    validateFactDimensionLookupDate(
        remarks, fact, fact.getDimensionRolesOrEmpty(), model, metadataProvider, variables);
    validateMeasures(remarks, fact, variables);
    validateDegenerateDimensions(remarks, fact, model, metadataProvider, variables);
    validateRangeDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateJunkDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateSourceConfiguration(remarks, fact, model, metadataProvider, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  public static void validateBridge(
      List<ICheckResult> remarks,
      DmBridge bridge,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || bridge == null) {
      return;
    }
    List<DmBridgeDimensionRef> refs = bridge.getDimensionRefsOrEmpty();
    if (refs.size() < 2) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingBridgeDimensions", bridge.getName()),
              bridge));
    }
    Set<String> fkColumns = new HashSet<>();
    for (DmBridgeDimensionRef ref : refs) {
      if (ref == null) {
        continue;
      }
      String dimensionName = resolve(ref.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingBridgeDimension",
                    bridge.getName()),
                bridge));
        continue;
      }
      IDmTable target = model != null ? model.findTable(dimensionName) : null;
      if (!(target instanceof DmDimension)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownBridgeDimension",
                    bridge.getName(),
                    dimensionName),
                bridge));
      }
      String fkColumn = resolve(ref.getForeignKeyColumn(), variables);
      if (!Utils.isEmpty(fkColumn) && !fkColumns.add(fkColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateBridgeForeignKey",
                    bridge.getName(),
                    fkColumn),
                bridge));
      }
    }
    validateSourceConfiguration(remarks, bridge, model, metadataProvider, variables);
    validateTargetLayout(remarks, bridge, model, metadataProvider, variables);
  }

  public static void validateAggregateFact(
      List<ICheckResult> remarks,
      DmAggregateFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    String baseFact = resolve(fact.getBaseFactTableName(), variables);
    if (Utils.isEmpty(baseFact)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingBaseFact", fact.getName()),
              fact));
    } else if (model != null && model.findTable(baseFact) == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.UnknownBaseFact", fact.getName(), baseFact),
              fact));
    }
    validateDimensionRoles(
        remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
    validateFactDimensionLookupDate(
        remarks, fact, fact.getDimensionRolesOrEmpty(), model, metadataProvider, variables);
    validateMeasures(remarks, fact, variables);
    validateDegenerateDimensions(remarks, fact, model, metadataProvider, variables);
    validateRangeDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateJunkDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateSourceConfiguration(remarks, fact, model, metadataProvider, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  public static void validateConformedDimensions(
      List<ICheckResult> remarks,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || model == null) {
      return;
    }
    Set<String> logicalNames = new HashSet<>();
    for (DmConformedDimensionRef ref : model.getConformedDimensionsOrEmpty()) {
      if (ref == null) {
        continue;
      }
      String logicalName = resolve(ref.getLogicalName(), variables);
      if (Utils.isEmpty(logicalName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DmValidationSupport.CheckResult.MissingConformedLogicalName"),
                null));
        continue;
      }
      if (!logicalNames.add(logicalName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateConformedLogicalName",
                    logicalName),
                null));
      }
      String dimensionName = resolve(ref.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DmValidationSupport.CheckResult.MissingConformedDimension", logicalName),
                null));
        continue;
      }
      if (DmDimensionResolutionSupport.resolveDimension(
              model, dimensionName, variables, metadataProvider)
          == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownConformedDimension",
                    logicalName,
                    dimensionName),
                null));
      }
    }
  }

  public static void validateDimensionAlias(
      List<ICheckResult> remarks,
      DmDimensionAlias alias,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || alias == null) {
      return;
    }
    String aliasName = resolve(alias.getName(), variables);
    String referencedName = resolve(alias.getReferencedDimensionName(), variables);
    if (Utils.isEmpty(referencedName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingAliasReferencedDimension",
                  Const.NVL(aliasName, "?")),
              alias));
      return;
    }
    String externalModelPath = resolve(alias.getReferencedModelFilename(), variables);
    if (aliasName != null && aliasName.equals(referencedName) && Utils.isEmpty(externalModelPath)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.AliasReferencesSelf", aliasName),
              alias));
      return;
    }
    if (!Utils.isEmpty(externalModelPath)) {
      validateExternalDimensionAlias(
          remarks,
          alias,
          aliasName,
          referencedName,
          externalModelPath,
          model,
          metadataProvider,
          variables);
    } else {
      validateLocalDimensionAlias(remarks, alias, aliasName, referencedName, model, variables);
    }

    alias.syncPhysicalTableName(model, variables, metadataProvider);
    if (Utils.isEmpty(alias.getTableName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmTableBase.CheckResult.MissingTableName", Const.NVL(aliasName, "?")),
              alias));
    }
  }

  private static void validateLocalDimensionAlias(
      List<ICheckResult> remarks,
      DmDimensionAlias alias,
      String aliasName,
      String referencedName,
      DimensionalModel model,
      IVariables variables) {
    IDmTable referenced = model != null ? model.findTable(referencedName) : null;
    if (referenced instanceof DmDimensionAlias) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.AliasReferencesAlias",
                  aliasName,
                  referencedName),
              alias));
      return;
    }
    if (!(referenced instanceof DmDimension)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.UnknownAliasReferencedDimension",
                  aliasName,
                  referencedName),
              alias));
    }
  }

  private static void validateExternalDimensionAlias(
      List<ICheckResult> remarks,
      DmDimensionAlias alias,
      String aliasName,
      String referencedName,
      String externalModelPath,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    DimensionalModel externalModel;
    try {
      externalModel =
          DmModelLoadSupport.loadDimensionalModel(
              externalModelPath,
              model != null ? model.getFilename() : null,
              variables,
              metadataProvider);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), alias));
      return;
    }

    if (model != null && hasCircularExternalReference(model, externalModel, variables)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.ExternalModelCircularReference",
                  aliasName,
                  externalModelPath),
              alias));
    }

    IDmTable referenced = externalModel.findTable(referencedName);
    if (referenced instanceof DmDimensionAlias) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.ExternalAliasReferencesAlias",
                  aliasName,
                  referencedName),
              alias));
      return;
    }
    if (!(referenced instanceof DmDimension)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.UnknownExternalAliasReferencedDimension",
                  aliasName,
                  referencedName,
                  externalModelPath),
              alias));
      return;
    }

    if (model != null) {
      warnOnExternalConfigurationMismatch(
          remarks, alias, aliasName, model, externalModel, variables);
    }
  }

  private static boolean hasCircularExternalReference(
      DimensionalModel referringModel, DimensionalModel externalModel, IVariables variables) {
    if (referringModel == null
        || externalModel == null
        || Utils.isEmpty(referringModel.getFilename())) {
      return false;
    }
    try {
      String referringPath = comparableModelPath(referringModel.getFilename(), variables);
      for (IDmTable table : externalModel.getTables()) {
        if (!(table instanceof DmDimensionAlias externalAlias)) {
          continue;
        }
        if (Utils.isEmpty(externalAlias.getReferencedModelFilename())) {
          continue;
        }
        String referencedPath =
            comparableModelPath(
                DmModelLoadSupport.resolveModelPath(
                    externalAlias.getReferencedModelFilename(),
                    externalModel.getFilename(),
                    variables),
                variables);
        if (referringPath.equals(referencedPath)) {
          return true;
        }
      }
    } catch (HopException ignored) {
      // handled elsewhere
    }
    return false;
  }

  private static String comparableModelPath(String path, IVariables variables) throws HopException {
    String resolved = variables != null ? variables.resolve(path) : path;
    return java.nio.file.Path.of(resolved).toAbsolutePath().normalize().toString();
  }

  private static void warnOnExternalConfigurationMismatch(
      List<ICheckResult> remarks,
      DmDimensionAlias alias,
      String aliasName,
      DimensionalModel referringModel,
      DimensionalModel externalModel,
      IVariables variables) {
    DimensionalConfiguration referringConfig = referringModel.getConfigurationOrDefault();
    DimensionalConfiguration externalConfig = externalModel.getConfigurationOrDefault();
    String referringDatabase = resolve(referringConfig.getTargetDatabase(), variables);
    String externalDatabase = resolve(externalConfig.getTargetDatabase(), variables);
    if (!Utils.isEmpty(referringDatabase)
        && !Utils.isEmpty(externalDatabase)
        && !referringDatabase.equals(externalDatabase)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.ExternalModelTargetDatabaseMismatch",
                  aliasName,
                  externalDatabase,
                  referringDatabase),
              alias));
    }
    String referringDimKey = resolve(referringConfig.getDimKeyField(), variables);
    String externalDimKey = resolve(externalConfig.getDimKeyField(), variables);
    if (!Utils.isEmpty(referringDimKey)
        && !Utils.isEmpty(externalDimKey)
        && !referringDimKey.equals(externalDimKey)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.ExternalModelDimKeyMismatch",
                  aliasName,
                  externalDimKey,
                  referringDimKey),
              alias));
    }
    String referringLoadDate = resolve(referringConfig.getLoadDateField(), variables);
    String externalLoadDate = resolve(externalConfig.getLoadDateField(), variables);
    if (!Utils.isEmpty(referringLoadDate)
        && !Utils.isEmpty(externalLoadDate)
        && !referringLoadDate.equals(externalLoadDate)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.ExternalModelLoadDateMismatch",
                  aliasName,
                  externalLoadDate,
                  referringLoadDate),
              alias));
    }
  }

  public static void validateDimension(
      List<ICheckResult> remarks,
      DmDimension dimension,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || dimension == null) {
      return;
    }
    validateNaturalKeys(remarks, dimension, variables);
    validateAttributes(remarks, dimension, variables);
    validateOutriggers(remarks, dimension, model, variables);
    validateSourceConfiguration(remarks, dimension, model, metadataProvider, variables);
    validateSurrogateKey(remarks, dimension, variables);
    validateScd2HistorizedSource(remarks, dimension, model, variables);
    validateDimensionSourceFields(remarks, dimension, model, metadataProvider, variables);
    validateTargetLayout(remarks, dimension, model, metadataProvider, variables);
  }

  public static void validateFact(
      List<ICheckResult> remarks,
      DmFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    validateDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateFactDimensionLookupDate(
        remarks, fact, fact.getDimensionRolesOrEmpty(), model, metadataProvider, variables);
    validateMeasures(remarks, fact, variables);
    validateDegenerateDimensions(remarks, fact, model, metadataProvider, variables);
    validateRangeDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateJunkDimensionRoles(remarks, fact, model, metadataProvider, variables);
    validateSourceConfiguration(remarks, fact, model, metadataProvider, variables);
    validateFactSourceFields(remarks, fact, model, metadataProvider, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  private static void validateFactDimensionLookupDate(
      List<ICheckResult> remarks,
      DmTableBase table,
      List<DmFactDimensionRole> roles,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || table == null) {
      return;
    }
    String lookupDateField = resolve(table.getDimensionLookupDateField(), variables);
    List<String> factSourceFields =
        DmSourceFieldResolutionSupport.tryResolveFieldNames(
            metadataProvider, variables, model, table);
    if (!Utils.isEmpty(lookupDateField)
        && !factSourceFields.isEmpty()
        && !factSourceFields.contains(lookupDateField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.DimensionLookupDateMissingFromFactSource",
                  table.getName(),
                  lookupDateField),
              table));
    }

    if (!Utils.isEmpty(lookupDateField) || roles == null || roles.isEmpty()) {
      return;
    }
    for (DmFactDimensionRole role : roles) {
      if (role == null || role.isTruncateToDateKey()) {
        continue;
      }
      String dimensionName = resolve(role.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        continue;
      }
      DmDimension dimension =
          model != null
              ? DmDimensionResolutionSupport.resolveDimension(
                  model, dimensionName, variables, metadataProvider)
              : null;
      if (dimension != null && DmLayoutSupport.dimensionUsesEffectivityLookup(dimension)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingDimensionLookupDateField",
                    table.getName()),
                table));
        return;
      }
    }
  }

  private static void validateNaturalKeys(
      List<ICheckResult> remarks, DmDimension dimension, IVariables variables) {
    List<DmNaturalKeyField> naturalKeys = dimension.getNaturalKeysOrEmpty();
    if (naturalKeys.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingNaturalKeys", dimension.getName()),
              dimension));
      return;
    }
    Set<String> names = new HashSet<>();
    for (DmNaturalKeyField naturalKey : naturalKeys) {
      String fieldName = resolve(naturalKey != null ? naturalKey.getFieldName() : null, variables);
      if (Utils.isEmpty(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyNaturalKeyField",
                    dimension.getName()),
                dimension));
        continue;
      }
      if (!names.add(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateNaturalKeyField",
                    dimension.getName(),
                    fieldName),
                dimension));
      }
    }
  }

  private static void validateAttributes(
      List<ICheckResult> remarks, DmDimension dimension, IVariables variables) {
    Set<String> names = new HashSet<>();
    for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
      String fieldName = resolve(naturalKey != null ? naturalKey.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        names.add(fieldName);
      }
    }
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      String fieldName = resolve(attribute != null ? attribute.getFieldName() : null, variables);
      if (Utils.isEmpty(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyAttributeField",
                    dimension.getName()),
                dimension));
        continue;
      }
      if (!names.add(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateAttributeField",
                    dimension.getName(),
                    fieldName),
                dimension));
      }
    }
  }

  private static void validateOutriggers(
      List<ICheckResult> remarks,
      DmDimension dimension,
      DimensionalModel model,
      IVariables variables) {
    for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
      if (outrigger == null) {
        continue;
      }
      String dimensionName = resolve(outrigger.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingOutriggerDimension",
                    dimension.getName()),
                dimension));
        continue;
      }
      IDmTable target = model != null ? model.findTable(dimensionName) : null;
      if (!(target instanceof DmDimension)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownOutriggerDimension",
                    dimension.getName(),
                    dimensionName),
                dimension));
      }
      String fkColumn = resolve(outrigger.getForeignKeyColumn(), variables);
      if (Utils.isEmpty(fkColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingOutriggerForeignKey",
                    dimension.getName(),
                    dimensionName),
                dimension));
      }
    }
  }

  private static void validateDimensionRoles(
      List<ICheckResult> remarks,
      DmFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    validateDimensionRoles(
        remarks,
        fact.getName(),
        fact.getDimensionRolesOrEmpty(),
        model,
        metadataProvider,
        variables,
        fact);
  }

  private static void validateDimensionRoles(
      List<ICheckResult> remarks,
      String tableName,
      List<DmFactDimensionRole> roles,
      DimensionalModel model,
      IVariables variables,
      IDmTable source) {
    validateDimensionRoles(remarks, tableName, roles, model, null, variables, source);
  }

  private static void validateDimensionRoles(
      List<ICheckResult> remarks,
      String tableName,
      List<DmFactDimensionRole> roles,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      IDmTable source) {
    if (roles.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingDimensionRoles", tableName),
              source));
      return;
    }
    Set<String> fkColumns = new HashSet<>();
    for (DmFactDimensionRole role : roles) {
      if (role == null) {
        continue;
      }
      String dimensionName = resolve(role.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DmValidationSupport.CheckResult.MissingRoleDimension", tableName),
                source));
        continue;
      }
      DmDimension resolvedDimension =
          model != null
              ? DmDimensionResolutionSupport.resolveDimension(
                  model, dimensionName, variables, metadataProvider)
              : null;
      if (resolvedDimension == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownRoleDimension",
                    tableName,
                    dimensionName),
                source));
      }
      if (resolvedDimension != null) {
        validateFactDimensionJoin(
            remarks,
            tableName,
            role,
            dimensionName,
            resolvedDimension,
            model,
            metadataProvider,
            variables,
            source);
      }
      String fkColumn = resolve(role.getForeignKeyColumn(), variables);
      if (Utils.isEmpty(fkColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingFactKeyColumn",
                    tableName,
                    dimensionName),
                source));
      } else if (!fkColumns.add(fkColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateForeignKeyColumn",
                    tableName,
                    fkColumn),
                source));
      }
    }
  }

  private static void validateDegenerateDimensions(
      List<ICheckResult> remarks,
      IDmFactLikeTable factLike,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || factLike == null) {
      return;
    }
    Set<String> names = new HashSet<>();
    Set<String> measureNames = new HashSet<>();
    for (DmFactMeasure measure : factLike.getMeasuresOrEmpty()) {
      String fieldName = resolve(measure != null ? measure.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        measureNames.add(fieldName);
      }
    }
    Set<String> roleSourceFields = new HashSet<>();
    Set<String> roleForeignKeys = new HashSet<>();
    for (DmFactDimensionRole role : factLike.getDimensionRolesOrEmpty()) {
      String sourceField = resolve(role != null ? role.getSourceFieldName() : null, variables);
      if (!Utils.isEmpty(sourceField)) {
        roleSourceFields.add(sourceField);
      }
      String foreignKey = resolve(role != null ? role.getForeignKeyColumn() : null, variables);
      if (!Utils.isEmpty(foreignKey)) {
        roleForeignKeys.add(foreignKey);
      }
    }
    Set<String> reservedFields = collectReservedDegenerateDimensionFields(factLike, variables);
    Set<String> available = new HashSet<>();
    if (factLike instanceof DmTableBase factTable) {
      IRowMeta sourceRowMeta =
          DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
              metadataProvider, variables, model, factTable);
      if (sourceRowMeta != null) {
        available.addAll(
            sourceRowMeta.getValueMetaList().stream().map(IValueMeta::getName).toList());
      }
    }
    for (DmFactDegenerateDimension degenerateDimension :
        factLike.getDegenerateDimensionsOrEmpty()) {
      String fieldName =
          resolve(
              degenerateDimension != null ? degenerateDimension.getFieldName() : null, variables);
      if (Utils.isEmpty(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyDegenerateDimensionField",
                    factLike.getName()),
                factLike));
        continue;
      }
      if (!names.add(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateDegenerateDimensionField",
                    factLike.getName(),
                    fieldName),
                factLike));
      }
      if (measureNames.contains(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DegenerateDimensionOverlapsMeasure",
                    factLike.getName(),
                    fieldName),
                factLike));
      }
      if (roleSourceFields.contains(fieldName) || roleForeignKeys.contains(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DegenerateDimensionOverlapsDimensionRole",
                    factLike.getName(),
                    fieldName),
                factLike));
      }
      if (reservedFields.contains(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DegenerateDimensionOverlapsReservedField",
                    factLike.getName(),
                    fieldName),
                factLike));
      }
      if (!available.isEmpty() && !available.contains(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DegenerateDimensionMissingFromFactSource",
                    factLike.getName(),
                    fieldName),
                factLike));
      }
    }
  }

  private static Set<String> collectReservedDegenerateDimensionFields(
      IDmFactLikeTable factLike, IVariables variables) {
    Set<String> reserved = new HashSet<>();
    if (factLike instanceof DmPeriodicSnapshotFact periodicSnapshotFact) {
      String snapshotDateField = resolve(periodicSnapshotFact.getSnapshotDateField(), variables);
      if (!Utils.isEmpty(snapshotDateField)) {
        reserved.add(snapshotDateField);
      }
    }
    if (factLike instanceof DmAccumulatingSnapshotFact accumulatingSnapshotFact) {
      for (DmNaturalKeyField grainKey : accumulatingSnapshotFact.getGrainKeysOrEmpty()) {
        String fieldName = resolve(grainKey != null ? grainKey.getFieldName() : null, variables);
        if (!Utils.isEmpty(fieldName)) {
          reserved.add(fieldName);
        }
      }
    }
    if (factLike instanceof DmTableBase table
        && !Utils.isEmpty(resolve(table.getDimensionLookupDateField(), variables))) {
      reserved.add(resolve(table.getDimensionLookupDateField(), variables));
    }
    return reserved;
  }

  private static void validateRangeDimensionRoles(
      List<ICheckResult> remarks,
      IDmFactLikeTable factLike,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || factLike == null) {
      return;
    }
    Set<String> targetNames = new HashSet<>();
    Set<String> measureNames = new HashSet<>();
    for (DmFactMeasure measure : factLike.getMeasuresOrEmpty()) {
      String fieldName = resolve(measure != null ? measure.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        measureNames.add(fieldName);
      }
    }
    Set<String> degenerateNames = new HashSet<>();
    for (DmFactDegenerateDimension degenerateDimension :
        factLike.getDegenerateDimensionsOrEmpty()) {
      String fieldName =
          resolve(
              degenerateDimension != null ? degenerateDimension.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        degenerateNames.add(fieldName);
      }
    }
    Set<String> roleSourceFields = new HashSet<>();
    Set<String> roleForeignKeys = new HashSet<>();
    for (DmFactDimensionRole role : factLike.getDimensionRolesOrEmpty()) {
      String sourceField = resolve(role != null ? role.getSourceFieldName() : null, variables);
      if (!Utils.isEmpty(sourceField)) {
        roleSourceFields.add(sourceField);
      }
      String foreignKey = resolve(role != null ? role.getForeignKeyColumn() : null, variables);
      if (!Utils.isEmpty(foreignKey)) {
        roleForeignKeys.add(foreignKey);
      }
    }
    Set<String> reservedFields = collectReservedDegenerateDimensionFields(factLike, variables);
    Set<String> available = new HashSet<>();
    IRowMeta sourceRowMeta = null;
    if (factLike instanceof DmTableBase factTable) {
      sourceRowMeta =
          DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
              metadataProvider, variables, model, factTable);
      if (sourceRowMeta != null) {
        available.addAll(
            sourceRowMeta.getValueMetaList().stream().map(IValueMeta::getName).toList());
      }
    }
    for (DmFactRangeDimensionRole rangeRole : factLike.getRangeDimensionRolesOrEmpty()) {
      String rangeDimensionName =
          resolve(rangeRole != null ? rangeRole.getRangeDimensionTableName() : null, variables);
      String sourceFieldName =
          resolve(rangeRole != null ? rangeRole.getSourceFieldName() : null, variables);
      String targetFieldName =
          resolve(rangeRole != null ? rangeRole.getTargetFieldName() : null, variables);
      if (Utils.isEmpty(rangeDimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyRangeDimensionRole",
                    factLike.getName()),
                factLike));
        continue;
      }
      DmRangeDimension rangeDimension =
          DmRangeDimensionSupport.resolveRangeDimension(model, rangeDimensionName, variables);
      if (rangeDimension == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownRangeDimension",
                    factLike.getName(),
                    rangeDimensionName),
                factLike));
      }
      if (Utils.isEmpty(sourceFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyRangeDimensionSourceField",
                    factLike.getName(),
                    rangeDimensionName),
                factLike));
      } else if (!available.isEmpty() && !available.contains(sourceFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.RangeDimensionSourceMissingFromFactSource",
                    factLike.getName(),
                    sourceFieldName),
                factLike));
      } else if (sourceRowMeta != null && !Utils.isEmpty(sourceFieldName)) {
        IValueMeta sourceMeta = sourceRowMeta.searchValueMeta(sourceFieldName);
        if (sourceMeta != null && !sourceMeta.isNumeric()) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  BaseMessages.getString(
                      PKG,
                      "DmValidationSupport.CheckResult.RangeDimensionSourceNotNumeric",
                      factLike.getName(),
                      sourceFieldName),
                  factLike));
        }
      }
      if (Utils.isEmpty(targetFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyRangeDimensionTargetField",
                    factLike.getName(),
                    rangeDimensionName),
                factLike));
        continue;
      }
      if (!targetNames.add(targetFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateRangeDimensionTargetField",
                    factLike.getName(),
                    targetFieldName),
                factLike));
      }
      if (degenerateNames.contains(targetFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.RangeDimensionTargetOverlapsDegenerate",
                    factLike.getName(),
                    targetFieldName),
                factLike));
      }
      if (measureNames.contains(targetFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.RangeDimensionTargetOverlapsMeasure",
                    factLike.getName(),
                    targetFieldName),
                factLike));
      }
      if (roleSourceFields.contains(targetFieldName) || roleForeignKeys.contains(targetFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.RangeDimensionTargetOverlapsDimensionRole",
                    factLike.getName(),
                    targetFieldName),
                factLike));
      }
      if (reservedFields.contains(targetFieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.RangeDimensionTargetOverlapsReservedField",
                    factLike.getName(),
                    targetFieldName),
                factLike));
      }
    }
  }

  private static void validateJunkDimensionRoles(
      List<ICheckResult> remarks,
      IDmFactLikeTable factLike,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || factLike == null) {
      return;
    }
    Set<String> foreignKeys = new HashSet<>();
    Set<String> roleForeignKeys = new HashSet<>();
    for (DmFactDimensionRole role : factLike.getDimensionRolesOrEmpty()) {
      String foreignKey = resolve(role != null ? role.getForeignKeyColumn() : null, variables);
      if (!Utils.isEmpty(foreignKey)) {
        roleForeignKeys.add(foreignKey);
      }
    }
    Set<String> available = new HashSet<>();
    IRowMeta sourceRowMeta = null;
    if (factLike instanceof DmTableBase factTable) {
      sourceRowMeta =
          DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
              metadataProvider, variables, model, factTable);
      if (sourceRowMeta != null) {
        available.addAll(
            sourceRowMeta.getValueMetaList().stream().map(IValueMeta::getName).toList());
      }
    }
    for (DmFactJunkDimensionRole junkRole : factLike.getJunkDimensionRolesOrEmpty()) {
      String junkDimensionName =
          resolve(junkRole != null ? junkRole.getJunkDimensionTableName() : null, variables);
      String foreignKeyColumn =
          resolve(junkRole != null ? junkRole.getForeignKeyColumn() : null, variables);
      if (Utils.isEmpty(junkDimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyJunkDimensionRole",
                    factLike.getName()),
                factLike));
        continue;
      }
      DmJunkDimension junkDimension =
          DmJunkDimensionSupport.resolveJunkDimension(model, junkDimensionName, variables);
      if (junkDimension == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownJunkDimension",
                    factLike.getName(),
                    junkDimensionName),
                factLike));
        continue;
      }
      if (!DmJunkDimensionSupport.isFactEmbedded(junkDimension)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.JunkDimensionNotFactEmbedded",
                    factLike.getName(),
                    junkDimensionName),
                factLike));
      }
      for (DmNaturalKeyField keyField : junkDimension.getKeyFieldsOrEmpty()) {
        String keyName = resolve(keyField != null ? keyField.getFieldName() : null, variables);
        if (!Utils.isEmpty(keyName) && !available.isEmpty() && !available.contains(keyName)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DmValidationSupport.CheckResult.JunkKeyMissingFromFactSource",
                      factLike.getName(),
                      keyName,
                      junkDimensionName),
                  factLike));
        }
      }
      if (Utils.isEmpty(foreignKeyColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyJunkForeignKey",
                    factLike.getName(),
                    junkDimensionName),
                factLike));
        continue;
      }
      if (!foreignKeys.add(foreignKeyColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateJunkForeignKey",
                    factLike.getName(),
                    foreignKeyColumn),
                factLike));
      }
      if (roleForeignKeys.contains(foreignKeyColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.JunkForeignKeyOverlapsDimensionRole",
                    factLike.getName(),
                    foreignKeyColumn),
                factLike));
      }
    }
  }

  private static double parseRangeBound(String bound, boolean lower) {
    if (Utils.isEmpty(bound)) {
      return lower ? -Double.MAX_VALUE : Double.MAX_VALUE;
    }
    return Double.parseDouble(bound.trim());
  }

  private static void warnOnOverlappingRangeBands(
      List<ICheckResult> remarks, DmRangeDimension rangeDimension) {
    List<DmRangeBand> bands = rangeDimension.getBandsOrEmpty();
    for (int i = 0; i < bands.size(); i++) {
      DmRangeBand left = bands.get(i);
      if (left == null) {
        continue;
      }
      double leftLower = parseRangeBound(left.getLowerBound(), true);
      double leftUpper = parseRangeBound(left.getUpperBound(), false);
      for (int j = i + 1; j < bands.size(); j++) {
        DmRangeBand right = bands.get(j);
        if (right == null) {
          continue;
        }
        double rightLower = parseRangeBound(right.getLowerBound(), true);
        double rightUpper = parseRangeBound(right.getUpperBound(), false);
        if (leftLower < rightUpper && rightLower < leftUpper) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  BaseMessages.getString(
                      PKG,
                      "DmValidationSupport.CheckResult.OverlappingRangeBands",
                      rangeDimension.getName(),
                      Const.NVL(left.getLabel(), "?"),
                      Const.NVL(right.getLabel(), "?")),
                  rangeDimension));
        }
      }
    }
  }

  private static void validateMeasures(
      List<ICheckResult> remarks, IDmFactLikeTable factLike, IVariables variables) {
    if (remarks == null || factLike == null) {
      return;
    }
    List<DmFactMeasure> measures = factLike.getMeasuresOrEmpty();
    if (measures.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingMeasures", factLike.getName()),
              factLike));
      return;
    }
    Set<String> names = new HashSet<>();
    for (DmFactMeasure measure : measures) {
      String fieldName = resolve(measure != null ? measure.getFieldName() : null, variables);
      if (Utils.isEmpty(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DmValidationSupport.CheckResult.EmptyMeasureField", factLike.getName()),
                factLike));
        continue;
      }
      if (!names.add(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateMeasureField",
                    factLike.getName(),
                    fieldName),
                factLike));
      }
    }
  }

  private static void validateFactDimensionJoin(
      List<ICheckResult> remarks,
      String tableName,
      DmFactDimensionRole role,
      String dimensionName,
      DmDimension dimension,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      IDmTable source) {
    List<String> naturalKeys =
        dimension.getNaturalKeysOrEmpty().stream()
            .filter(key -> key != null && !Utils.isEmpty(key.getFieldName()))
            .map(key -> resolve(key.getFieldName(), variables))
            .toList();
    if (naturalKeys.size() > 1) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.CompositeNaturalKeyJoin",
                  tableName,
                  dimensionName),
              source));
      return;
    }

    String naturalKey = naturalKeys.isEmpty() ? null : naturalKeys.get(0);
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    validateFactDimensionSkipLookup(
        remarks, tableName, role, dimensionName, dimension, config, variables, source);

    String sourceField = resolve(role.getSourceFieldName(), variables);
    List<String> factSourceFields = List.of();
    if (source instanceof DmTableBase factTable) {
      factSourceFields =
          DmSourceFieldResolutionSupport.tryResolveFieldNames(
              metadataProvider, variables, model, factTable);
    }
    if (Utils.isEmpty(sourceField)) {
      if (!Utils.isEmpty(naturalKey)
          && (factSourceFields.contains(naturalKey)
              || sourceContainsField(source, naturalKey, variables, metadataProvider, model))) {
        sourceField = naturalKey;
      } else {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingJoinSourceField",
                    tableName,
                    dimensionName,
                    naturalKey != null ? naturalKey : "?"),
                source));
      }
    } else if (!factSourceFields.isEmpty() && !factSourceFields.contains(sourceField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.JoinSourceFieldMissingFromFactSource",
                  tableName,
                  sourceField,
                  dimensionName),
              source));
    }

    if (!DmSurrogateKeySupport.shouldSkipFactDimensionLookup(role, dimension, config, variables)) {
      String lookupKeyField =
          DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, variables);
      try {
        IRowMeta dimensionLayout =
            dimension.getTargetTableLayout(metadataProvider, variables, model);
        if (!DmLayoutSupport.layoutFieldNames(dimensionLayout).contains(lookupKeyField)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DmValidationSupport.CheckResult.DimensionLookupKeyMissingFromTarget",
                      tableName,
                      dimensionName,
                      lookupKeyField,
                      dimension.getName()),
                  source));
        }
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), source));
      }
    }

    String fkColumn = resolve(role.getForeignKeyColumn(), variables);
    if (!Utils.isEmpty(naturalKey)
        && naturalKey.equals(fkColumn)
        && DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension)
            != DmDimensionLoadStrategySupport.DmDimensionLoadStrategy.PURE_TYPE1) {
      String lookupKeyField =
          DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, variables);
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.FactKeyUsesNaturalKey",
                  tableName,
                  fkColumn,
                  dimensionName,
                  lookupKeyField),
              source));
    }

    if (!Utils.isEmpty(naturalKey)
        && source instanceof DmTableBase factTable
        && !DmSurrogateKeySupport.shouldSkipFactDimensionLookup(
            role, dimension, config, variables)) {
      IRowMeta sourceRowMeta =
          DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
              metadataProvider, variables, model, factTable);
      if (sourceRowMeta != null && !sourceRowMeta.isEmpty()) {
        try {
          IRowMeta dimensionLayout =
              dimension.getTargetTableLayout(metadataProvider, variables, model);
          DmFactDimensionJoinValidationSupport.validateJoinKeyTypes(
              remarks,
              sourceRowMeta,
              dimensionLayout,
              tableName,
              dimensionName,
              role,
              naturalKey,
              source,
              variables);
        } catch (HopException e) {
          remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), source));
        }
      }
    }
  }

  private static void validateFactDimensionSkipLookup(
      List<ICheckResult> remarks,
      String tableName,
      DmFactDimensionRole role,
      String dimensionName,
      DmDimension dimension,
      DimensionalConfiguration config,
      IVariables variables,
      IDmTable source) {
    if (remarks == null || role == null || dimension == null) {
      return;
    }
    if (role.isSkipDimensionLookup() && role.isForceDimensionLookup()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.ConflictingSkipLookupFlags",
                  tableName,
                  dimensionName),
              source));
    }
    if (role.isTruncateToDateKey()
        && (role.isSkipDimensionLookup()
            || DmSurrogateKeySupport.autoDetectSurrogateKeyPassthrough(
                role, dimension, config, variables)
            || DmSurrogateKeySupport.autoDetectNaturalKeyPassthrough(
                role, dimension, config, variables))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.SkipLookupWithDateKeyTruncation",
                  tableName,
                  dimensionName),
              source));
    }
    if (role.isSkipDimensionLookup()
        && !DmSurrogateKeySupport.supportsExplicitSkipDimensionLookup(dimension)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.SkipLookupRequiresSourceFieldStrategy",
                  tableName,
                  dimensionName),
              source));
    }
    if (role.isSkipDimensionLookup()
        && DmSurrogateKeySupport.isNaturalKeyOnlyDimension(dimension)) {
      String lookupKey =
          DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, variables);
      String fkColumn = resolve(role.getForeignKeyColumn(), variables);
      if (!Utils.isEmpty(lookupKey) && !Utils.isEmpty(fkColumn) && !lookupKey.equals(fkColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.SkipLookupNaturalKeyFkMismatch",
                    tableName,
                    dimensionName,
                    fkColumn,
                    lookupKey),
                source));
      }
    }
    if (DmSurrogateKeySupport.shouldSkipFactDimensionLookup(role, dimension, config, variables)
        && Utils.isEmpty(
            DmSurrogateKeySupport.resolveFactRoleSourceField(role, dimension, variables))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingSkipLookupSourceField",
                  tableName,
                  dimensionName),
              source));
    }
    if (role.isPreloadLookupCache()
        && DmSurrogateKeySupport.shouldSkipFactDimensionLookup(
            role, dimension, config, variables)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.PreloadCacheIgnoredForSkipLookup",
                  tableName,
                  dimensionName),
              source));
    }
  }

  private static void validateFactSourceFields(
      List<ICheckResult> remarks,
      DmFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    IRowMeta sourceRowMeta =
        DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
            metadataProvider, variables, model, fact);
    if (sourceRowMeta == null || sourceRowMeta.isEmpty()) {
      return;
    }
    Set<String> available =
        new HashSet<>(sourceRowMeta.getValueMetaList().stream().map(IValueMeta::getName).toList());
    for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
      String fieldName = resolve(measure != null ? measure.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName) && !available.contains(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MeasureMissingFromFactSource",
                    fact.getName(),
                    fieldName),
                fact));
      }
    }
    validateFactSourceTargetTypes(remarks, fact, model, metadataProvider, variables, sourceRowMeta);
  }

  private static void validateFactSourceTargetTypes(
      List<ICheckResult> remarks,
      DmFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      IRowMeta sourceRowMeta) {
    if (remarks == null || fact == null || sourceRowMeta == null || sourceRowMeta.isEmpty()) {
      return;
    }
    IRowMeta targetLayout;
    try {
      targetLayout = fact.getTargetTableLayout(metadataProvider, variables, model);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), fact));
      return;
    }
    String factName = fact.getName();
    for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
      String fieldName = resolve(measure != null ? measure.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        DmSourceTargetTypeValidationSupport.validateMappedField(
            remarks,
            sourceRowMeta,
            targetLayout,
            fieldName,
            "Fact '" + factName + "' measure '" + fieldName + "'",
            fact);
      }
    }
    for (DmFactDegenerateDimension degenerateDimension : fact.getDegenerateDimensionsOrEmpty()) {
      String fieldName =
          resolve(
              degenerateDimension != null ? degenerateDimension.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        DmSourceTargetTypeValidationSupport.validateMappedField(
            remarks,
            sourceRowMeta,
            targetLayout,
            fieldName,
            "Fact '" + factName + "' degenerate dimension '" + fieldName + "'",
            fact);
      }
    }
  }

  private static void validateDimensionSourceFields(
      List<ICheckResult> remarks,
      DmDimension dimension,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || dimension == null) {
      return;
    }
    IRowMeta sourceRowMeta =
        DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
            metadataProvider, variables, model, dimension);
    if (sourceRowMeta == null || sourceRowMeta.isEmpty()) {
      return;
    }
    List<String> sourceFields =
        sourceRowMeta.getValueMetaList().stream().map(IValueMeta::getName).toList();
    Set<String> available = new HashSet<>(sourceFields);
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
      String fieldName = resolve(naturalKey != null ? naturalKey.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName) && !available.contains(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DimensionSourceMissingField",
                    dimension.getName(),
                    fieldName),
                dimension));
      }
    }
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      String sourceField =
          DmDimensionLoadStrategySupport.resolveSourceFieldName(attribute, variables);
      if (!Utils.isEmpty(sourceField) && !available.contains(sourceField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DimensionSourceMissingField",
                    dimension.getName(),
                    sourceField),
                dimension));
      }
    }
    if (DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension)
        == DmDimensionLoadStrategySupport.DmDimensionLoadStrategy.PURE_TYPE1) {
      String loadDateField = resolve(config.resolveLoadDateField(variables), variables);
      if (!Utils.isEmpty(loadDateField) && !available.contains(loadDateField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DimensionSourceMissingLoadDate",
                    dimension.getName(),
                    loadDateField),
                dimension));
      }
    }
    if (DmSurrogateKeySupport.resolveStrategy(dimension)
        == DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      String sourceField =
          DmSurrogateKeySupport.resolveSurrogateKeySourceField(dimension, config, variables);
      if (!Utils.isEmpty(sourceField) && !available.contains(sourceField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DimensionSourceMissingSurrogateField",
                    dimension.getName(),
                    sourceField),
                dimension));
      }
    }
    validateDimensionSourceTargetTypes(
        remarks, dimension, model, metadataProvider, variables, sourceRowMeta);
  }

  private static void validateDimensionSourceTargetTypes(
      List<ICheckResult> remarks,
      DmDimension dimension,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      IRowMeta sourceRowMeta) {
    if (remarks == null || dimension == null || sourceRowMeta == null || sourceRowMeta.isEmpty()) {
      return;
    }
    IRowMeta targetLayout;
    try {
      targetLayout = dimension.getTargetTableLayout(metadataProvider, variables, model);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), dimension));
      return;
    }
    String dimensionName = dimension.getName();
    for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
      String fieldName = resolve(naturalKey != null ? naturalKey.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        DmSourceTargetTypeValidationSupport.validateMappedField(
            remarks,
            sourceRowMeta,
            targetLayout,
            fieldName,
            "Dimension '" + dimensionName + "' natural key '" + fieldName + "'",
            dimension);
      }
    }
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      String fieldName = resolve(attribute != null ? attribute.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        DmSourceTargetTypeValidationSupport.validateMappedField(
            remarks,
            sourceRowMeta,
            targetLayout,
            fieldName,
            "Dimension '" + dimensionName + "' attribute '" + fieldName + "'",
            dimension);
      }
    }
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    if (DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension)
        == DmDimensionLoadStrategySupport.DmDimensionLoadStrategy.PURE_TYPE1) {
      String loadDateField = resolve(config.resolveLoadDateField(variables), variables);
      if (!Utils.isEmpty(loadDateField)) {
        DmSourceTargetTypeValidationSupport.validateMappedField(
            remarks,
            sourceRowMeta,
            targetLayout,
            loadDateField,
            "Dimension '" + dimensionName + "' load timestamp '" + loadDateField + "'",
            dimension);
      }
    }
    if (DmSurrogateKeySupport.resolveStrategy(dimension)
        == DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      String sourceField =
          DmSurrogateKeySupport.resolveSurrogateKeySourceField(dimension, config, variables);
      if (!Utils.isEmpty(sourceField)) {
        DmSourceTargetTypeValidationSupport.validateMappedField(
            remarks,
            sourceRowMeta,
            targetLayout,
            sourceField,
            "Dimension '" + dimensionName + "' surrogate key '" + sourceField + "'",
            dimension);
      }
    }
  }

  private static void validateScd2HistorizedSource(
      List<ICheckResult> remarks,
      DmDimension dimension,
      DimensionalModel model,
      IVariables variables) {
    if (remarks == null || dimension == null || model == null) {
      return;
    }
    if (DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension)
        != DmDimensionLoadStrategySupport.DmDimensionLoadStrategy.PURE_TYPE2) {
      return;
    }
    DmSourceConfiguration source = dimension.getSourceOrDefault();
    if (source.getSourceType() != DmSourceType.SQL) {
      return;
    }
    String sourceSql = source.resolveSourceSql(variables);
    if (Utils.isEmpty(sourceSql)) {
      return;
    }
    String normalized = sourceSql.toLowerCase(Locale.ROOT);
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    String openEnd = config.getOpenEndSentinel();
    if (variables != null) {
      openEnd = variables.resolve(openEnd);
    }
    String dateToField = config.resolveDateToField(variables).toLowerCase(Locale.ROOT);
    boolean referencesHistorizedSource =
        normalized.contains("_bv")
            || normalized.contains(dateToField)
            || normalized.contains("x_to_ts")
            || normalized.contains("x_from_ts");
    if (!referencesHistorizedSource) {
      return;
    }
    boolean filtersCurrentRows =
        normalized.contains(openEnd.toLowerCase(Locale.ROOT))
            || normalized.contains("x_to_ts =")
            || normalized.contains(dateToField + " =");
    if (!filtersCurrentRows) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.Scd2HistorizedSourceMissingCurrentFilter",
                  dimension.getName()),
              dimension));
    }
  }

  private static void validateSurrogateKey(
      List<ICheckResult> remarks, DmDimension dimension, IVariables variables) {
    if (remarks == null || dimension == null) {
      return;
    }
    DmSurrogateKeyStrategy strategy = DmSurrogateKeySupport.resolveStrategy(dimension);
    if (strategy == DmSurrogateKeyStrategy.NONE
        && DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension)
            == DmDimensionLoadStrategySupport.DmDimensionLoadStrategy.PURE_TYPE2) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.Scd2WithoutSurrogateKey",
                  dimension.getName()),
              dimension));
    }
    if (strategy == DmSurrogateKeyStrategy.USE_SOURCE_FIELD
        && Utils.isEmpty(dimension.getSurrogateKeySourceField())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingSurrogateSourceField",
                  dimension.getName()),
              dimension));
    }
  }

  private static void validateJunkSurrogateKey(
      List<ICheckResult> remarks, DmJunkDimension junkDimension, IVariables variables) {
    if (remarks == null || junkDimension == null) {
      return;
    }
    DmJunkSurrogateKeyStrategy strategy = DmSurrogateKeySupport.resolveJunkStrategy(junkDimension);
    if (strategy == DmJunkSurrogateKeyStrategy.USE_SOURCE_FIELD
        && Utils.isEmpty(junkDimension.getSurrogateKeySourceField())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingJunkSurrogateSourceField",
                  junkDimension.getName()),
              junkDimension));
    }
  }

  private static void validateJunkFactTableSource(
      List<ICheckResult> remarks,
      DmJunkDimension junkDimension,
      DimensionalModel model,
      IVariables variables) {
    if (remarks == null || junkDimension == null) {
      return;
    }
    String factTableName = DmJunkDimensionSupport.resolveFactTableName(junkDimension, variables);
    if (Utils.isEmpty(factTableName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingJunkFactTableSource",
                  junkDimension.getName()),
              junkDimension));
      return;
    }
    if (model != null
        && DmJunkDimensionSupport.resolveFactTable(model, factTableName, variables) == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.UnknownJunkFactTableSource",
                  junkDimension.getName(),
                  factTableName),
              junkDimension));
    }
  }

  private static void validateJunkHashCode(
      List<ICheckResult> remarks, DmJunkDimension junkDimension, IVariables variables) {
    if (remarks == null || junkDimension == null) {
      return;
    }
    DmJunkHashCodeStrategy strategy = junkDimension.getHashCodeStrategyOrDefault();
    if (!strategy.usesHashColumn()) {
      return;
    }
    if (junkDimension.isUseSurrogateKeyAsHashCodeField()) {
      DmJunkSurrogateKeyStrategy surrogateStrategy =
          DmSurrogateKeySupport.resolveJunkStrategy(junkDimension);
      if (surrogateStrategy == DmJunkSurrogateKeyStrategy.AUTO_INCREMENT) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.JunkSharedHashAutoIncrement",
                    junkDimension.getName()),
                junkDimension));
      }
      String surrogateField =
          DmSurrogateKeySupport.resolveJunkSurrogateKeyField(
              junkDimension, new DimensionalConfiguration(), variables);
      if (Utils.isEmpty(surrogateField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingJunkSharedHashSurrogateField",
                    junkDimension.getName()),
                junkDimension));
      }
      return;
    }
    if (Utils.isEmpty(junkDimension.getHashCodeField())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.MissingJunkHashCodeField",
                  junkDimension.getName()),
              junkDimension));
    }
  }

  private static void validateSourceConfiguration(
      List<ICheckResult> remarks,
      IDmTable table,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || table == null) {
      return;
    }
    DmSourceConfiguration source = table.getSourceOrDefault();
    if (source.isPipelineSource()) {
      if (Utils.isEmpty(source.resolveSourcePipelineFile(variables))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingSourcePipelineFile",
                    table.getName()),
                table));
      }
      if (Utils.isEmpty(source.resolveSourcePipelineTransform(variables))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingSourcePipelineTransform",
                    table.getName()),
                table));
      }
      return;
    }
    if (source.isDateGeneratorSource()) {
      DmDateGeneratorConfiguration generator =
          source.getDateGenerator() != null
              ? source.getDateGenerator()
              : DmDateGeneratorConfiguration.createDefault();
      boolean hasField = false;
      for (var field : generator.getFieldsOrEmpty()) {
        if (field != null && !Utils.isEmpty(field.getName())) {
          hasField = true;
          break;
        }
      }
      if (!hasField) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingDateGeneratorFields",
                    table.getName()),
                table));
      }
      if (Utils.isEmpty(generator.getStartDate()) || Utils.isEmpty(generator.getEndDate())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingDateGeneratorRange",
                    table.getName()),
                table));
      } else {
        try {
          DateDimensionGeneratorLogic.resolveDateRange(
              generator.getStartDate(), generator.getEndDate(), variables);
          DateDimensionGeneratorLogic.resolveContext(
              generator.getReferenceDate(),
              generator.getDayOffset(),
              generator.getWeekOffset(),
              generator.getMonthOffset(),
              variables);
        } catch (HopException e) {
          remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), table));
        }
      }
      return;
    }
    if (source.isRecordDefinitionSource()) {
      DimensionalConfiguration config = model != null ? model.getConfigurationOrDefault() : null;
      if (Utils.isEmpty(source.resolveSourceCatalogConnection(variables))
          && (config == null || Utils.isEmpty(config.getDataCatalogConnection()))) {
        if (metadataProvider == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DmValidationSupport.CheckResult.MissingSourceCatalogConnection",
                      table.getName()),
                  table));
        } else {
          try {
            DmSourceRecordDefinitionSupport.resolveCatalogConnection(
                config, source, variables, metadataProvider);
          } catch (HopException e) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    BaseMessages.getString(
                        PKG,
                        "DmValidationSupport.CheckResult.MissingSourceCatalogConnection",
                        table.getName()),
                    table));
          }
        }
      }
      if (Utils.isEmpty(source.resolveSourceRecordNamespace(variables))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingSourceRecordNamespace",
                    table.getName()),
                table));
      }
      if (Utils.isEmpty(source.resolveSourceRecordName(variables))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingSourceRecordName",
                    table.getName()),
                table));
      }
      return;
    }

    String sourceSql = source.resolveSourceSql(variables);
    if (Utils.isEmpty(sourceSql)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingSourceSql", table.getName()),
              table));
    }
  }

  private static void validateTargetLayout(
      List<ICheckResult> remarks,
      IDmTable table,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (table == null) {
      return;
    }
    try {
      table.getTargetTableLayout(metadataProvider, variables, model);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), table));
    }
  }

  private static boolean sourceContainsField(
      IDmTable table,
      String fieldName,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DimensionalModel model) {
    if (table == null || Utils.isEmpty(fieldName)) {
      return false;
    }
    DmSourceConfiguration source = table.getSourceOrDefault();
    if (source.isPipelineSource()
        || source.isRecordDefinitionSource()
        || source.isDateGeneratorSource()) {
      if (metadataProvider == null || model == null) {
        return false;
      }
      return DmSourceFieldResolutionSupport.tryResolveFieldNames(
              metadataProvider, variables, model, table)
          .contains(fieldName);
    }
    String sql = source.resolveSourceSql(variables);
    if (Utils.isEmpty(sql)) {
      return false;
    }
    return Pattern.compile("(?i)\\b" + Pattern.quote(fieldName) + "\\b").matcher(sql).find();
  }

  private static String resolve(String value, IVariables variables) {
    if (variables != null) {
      value = variables.resolve(value);
    }
    return value;
  }
}
