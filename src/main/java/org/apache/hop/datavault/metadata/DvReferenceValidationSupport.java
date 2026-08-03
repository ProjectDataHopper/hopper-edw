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

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Validation helpers for cross-model {@link DvTableReference} tables. */
public final class DvReferenceValidationSupport {

  private static final Class<?> PKG = DvReferenceValidationSupport.class;

  private DvReferenceValidationSupport() {}

  public static void validateTableReference(
      List<ICheckResult> remarks,
      DvTableReference reference,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    String referenceName = reference != null ? reference.getName() : null;
    if (reference == null) {
      return;
    }

    String referencedName = resolve(reference.getReferencedTableName(), variables);
    if (Utils.isEmpty(referencedName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.MissingReferencedTableName",
                  Const.NVL(referenceName, "?")),
              reference));
      return;
    }

    if (reference.getReferencedTableType() == null
        || reference.getReferencedTableType() == DvTableType.TABLE_REFERENCE) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.MissingReferencedTableType",
                  Const.NVL(referenceName, "?")),
              reference));
      return;
    }

    String externalModelPath = resolve(reference.getReferencedModelFilename(), variables);
    if (referenceName != null
        && referenceName.equals(referencedName)
        && Utils.isEmpty(externalModelPath)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.ReferenceReferencesSelf",
                  referenceName),
              reference));
      return;
    }

    if (!Utils.isEmpty(externalModelPath)) {
      validateExternalTableReference(
          remarks,
          reference,
          referenceName,
          referencedName,
          externalModelPath,
          model,
          metadataProvider,
          variables);
    } else {
      validateLocalTableReference(
          remarks, reference, referenceName, referencedName, model, variables);
    }
  }

  private static void validateLocalTableReference(
      List<ICheckResult> remarks,
      DvTableReference reference,
      String referenceName,
      String referencedName,
      DataVaultModel model,
      IVariables variables) {
    IDvTable referenced = model != null ? model.findTable(referencedName) : null;
    if (referenced instanceof DvTableReference) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.ReferenceReferencesReference",
                  referenceName,
                  referencedName),
              reference));
      return;
    }
    if (referenced == null || referenced.getTableType() != reference.getReferencedTableType()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.UnknownReferencedTable",
                  referenceName,
                  referencedName),
              reference));
    }
  }

  private static void validateExternalTableReference(
      List<ICheckResult> remarks,
      DvTableReference reference,
      String referenceName,
      String referencedName,
      String externalModelPath,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    DataVaultModel externalModel;
    try {
      externalModel =
          DvModelLoadSupport.loadDataVaultModel(
              reference.getReferencedModelFilename(),
              model != null ? model.getFilename() : null,
              variables,
              metadataProvider);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), reference));
      return;
    }

    if (model != null && hasCircularExternalReference(model, externalModel, variables)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.ExternalModelCircularReference",
                  referenceName,
                  externalModelPath),
              reference));
    }

    IDvTable referenced = externalModel.findTable(referencedName);
    if (referenced instanceof DvTableReference) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.ExternalReferenceReferencesReference",
                  referenceName,
                  referencedName),
              reference));
      return;
    }
    if (referenced == null || referenced.getTableType() != reference.getReferencedTableType()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.UnknownExternalReferencedTable",
                  referenceName,
                  referencedName,
                  externalModelPath),
              reference));
      return;
    }

    if (model != null) {
      warnOnExternalConfigurationMismatch(
          remarks, reference, referenceName, model, externalModel, variables);
    }
  }

  private static boolean hasCircularExternalReference(
      DataVaultModel referringModel, DataVaultModel externalModel, IVariables variables) {
    if (referringModel == null
        || externalModel == null
        || Utils.isEmpty(referringModel.getFilename())) {
      return false;
    }
    try {
      String referringPath = comparableModelPath(referringModel.getFilename(), variables);
      for (IDvTable table : externalModel.getTables()) {
        if (!(table instanceof DvTableReference externalReference)) {
          continue;
        }
        if (Utils.isEmpty(externalReference.getReferencedModelFilename())) {
          continue;
        }
        String referencedPath =
            comparableModelPath(
                DvModelLoadSupport.resolveModelPath(
                    externalReference.getReferencedModelFilename(),
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

  private static void warnOnExternalConfigurationMismatch(
      List<ICheckResult> remarks,
      DvTableReference reference,
      String referenceName,
      DataVaultModel referringModel,
      DataVaultModel externalModel,
      IVariables variables) {
    DataVaultConfiguration referringConfig = referringModel.getConfigurationOrDefault();
    DataVaultConfiguration externalConfig = externalModel.getConfigurationOrDefault();
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
                  "DvReferenceValidationSupport.CheckResult.ExternalModelTargetDatabaseMismatch",
                  referenceName,
                  externalDatabase,
                  referringDatabase),
              reference));
    }
    String referringHashAlgorithm = resolve(referringConfig.getHashAlgorithm(), variables);
    String externalHashAlgorithm = resolve(externalConfig.getHashAlgorithm(), variables);
    if (!Utils.isEmpty(referringHashAlgorithm)
        && !Utils.isEmpty(externalHashAlgorithm)
        && !referringHashAlgorithm.equals(externalHashAlgorithm)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.ExternalModelHashAlgorithmMismatch",
                  referenceName,
                  externalHashAlgorithm,
                  referringHashAlgorithm),
              reference));
    }
    String referringHashKeyType = resolve(referringConfig.getHashKeyDataType(), variables);
    String externalHashKeyType = resolve(externalConfig.getHashKeyDataType(), variables);
    if (!Utils.isEmpty(referringHashKeyType)
        && !Utils.isEmpty(externalHashKeyType)
        && !referringHashKeyType.equals(externalHashKeyType)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DvReferenceValidationSupport.CheckResult.ExternalModelHashKeyTypeMismatch",
                  referenceName,
                  externalHashKeyType,
                  referringHashKeyType),
              reference));
    }
  }

  private static String comparableModelPath(String path, IVariables variables) throws HopException {
    String resolved = variables != null ? variables.resolve(path) : path;
    return Path.of(resolved).toAbsolutePath().normalize().toString();
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
