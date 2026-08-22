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

import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Validates source SQL field types against dimensional target table layouts. */
public final class DmSourceTargetTypeValidationSupport {

  private static final Class<?> PKG = DmSourceTargetTypeValidationSupport.class;

  private DmSourceTargetTypeValidationSupport() {}

  public static void validateMappedField(
      List<ICheckResult> remarks,
      IRowMeta sourceRowMeta,
      IRowMeta targetLayout,
      String fieldName,
      String context,
      ICheckResultSource checkSource) {
    if (remarks == null
        || sourceRowMeta == null
        || targetLayout == null
        || Utils.isEmpty(fieldName)
        || checkSource == null) {
      return;
    }
    IValueMeta sourceMeta = sourceRowMeta.searchValueMeta(fieldName);
    IValueMeta targetMeta = targetLayout.searchValueMeta(fieldName);
    if (sourceMeta == null || targetMeta == null) {
      return;
    }
    validateMapping(sourceMeta, targetMeta, context, checkSource, remarks);
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
                  "DmSourceTargetTypeValidationSupport.CheckResult.TypeMismatch",
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
                    "DmSourceTargetTypeValidationSupport.CheckResult.SourceLengthExceedsTarget",
                    context,
                    sourceLength,
                    targetLength),
                checkSource));
      }
    }

    if (ValueMetaBase.isNumeric(sourceMeta.getType())) {
      int sourcePrecision = sourceMeta.getPrecision();
      int targetPrecision = targetMeta.getPrecision();
      if (sourcePrecision > 0 && targetPrecision > 0 && sourcePrecision > targetPrecision) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmSourceTargetTypeValidationSupport.CheckResult.SourcePrecisionExceedsTarget",
                    context,
                    sourcePrecision,
                    targetPrecision),
                checkSource));
      }
    }
  }

  static boolean typesCompatible(int sourceType, int targetType) {
    if (sourceType == targetType) {
      return true;
    }
    if ((sourceType == IValueMeta.TYPE_INTEGER && targetType == IValueMeta.TYPE_NUMBER)
        || (sourceType == IValueMeta.TYPE_NUMBER && targetType == IValueMeta.TYPE_INTEGER)) {
      return true;
    }
    if ((sourceType == IValueMeta.TYPE_NUMBER && targetType == IValueMeta.TYPE_BIGNUMBER)
        || (sourceType == IValueMeta.TYPE_BIGNUMBER && targetType == IValueMeta.TYPE_NUMBER)) {
      return true;
    }
    // DATE vs TIMESTAMP (e.g. JDBC Timestamp vs model Date / SingleStore DATETIME).
    return (sourceType == IValueMeta.TYPE_DATE && targetType == IValueMeta.TYPE_TIMESTAMP)
        || (sourceType == IValueMeta.TYPE_TIMESTAMP && targetType == IValueMeta.TYPE_DATE);
  }

  private static boolean isLengthSensitive(int type) {
    return type == IValueMeta.TYPE_STRING || type == IValueMeta.TYPE_BINARY;
  }
}
