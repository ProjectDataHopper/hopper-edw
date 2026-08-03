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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Validates fact-to-dimension join key types against dimension natural keys. */
public final class DmFactDimensionJoinValidationSupport {

  private static final Class<?> PKG = DmFactDimensionJoinValidationSupport.class;

  private DmFactDimensionJoinValidationSupport() {}

  public static void validateJoinKeyTypes(
      List<ICheckResult> remarks,
      IRowMeta sourceRowMeta,
      IRowMeta dimensionLayout,
      String factName,
      String dimensionName,
      DmFactDimensionRole role,
      String naturalKey,
      ICheckResultSource checkSource,
      IVariables variables) {
    if (remarks == null
        || sourceRowMeta == null
        || sourceRowMeta.isEmpty()
        || dimensionLayout == null
        || dimensionLayout.isEmpty()
        || Utils.isEmpty(naturalKey)
        || checkSource == null
        || role == null) {
      return;
    }

    IValueMeta dimensionKeyMeta = dimensionLayout.searchValueMeta(naturalKey);
    if (dimensionKeyMeta == null) {
      return;
    }

    if (role.isTruncateToDateKey()) {
      validateDateRoleSourceField(
          remarks,
          sourceRowMeta,
          dimensionKeyMeta,
          factName,
          dimensionName,
          role,
          naturalKey,
          checkSource,
          variables);
      return;
    }

    String streamField = resolveStreamKeyField(role, naturalKey, variables);
    if (Utils.isEmpty(streamField)) {
      return;
    }
    IValueMeta sourceMeta = sourceRowMeta.searchValueMeta(streamField);
    if (sourceMeta == null) {
      return;
    }

    String context =
        BaseMessages.getString(
            PKG,
            "DmFactDimensionJoinValidationSupport.CheckResult.JoinKeyContext",
            factName,
            dimensionName,
            streamField,
            naturalKey);
    DmSourceTargetTypeValidationSupport.validateMapping(
        sourceMeta, dimensionKeyMeta, context, checkSource, remarks);
  }

  private static void validateDateRoleSourceField(
      List<ICheckResult> remarks,
      IRowMeta sourceRowMeta,
      IValueMeta dimensionKeyMeta,
      String factName,
      String dimensionName,
      DmFactDimensionRole role,
      String naturalKey,
      ICheckResultSource checkSource,
      IVariables variables) {
    String dateSourceField = resolveSourceFieldName(role, naturalKey, variables);
    if (Utils.isEmpty(dateSourceField)) {
      return;
    }
    IValueMeta sourceMeta = sourceRowMeta.searchValueMeta(dateSourceField);
    if (sourceMeta == null) {
      return;
    }
    if (!isDateLikeType(sourceMeta.getType())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmFactDimensionJoinValidationSupport.CheckResult.DateRoleSourceMustBeDate",
                  factName,
                  dimensionName,
                  dateSourceField,
                  sourceMeta.getTypeDesc()),
              checkSource));
    }
    if (dimensionKeyMeta.getType() != IValueMeta.TYPE_INTEGER) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmFactDimensionJoinValidationSupport.CheckResult.DateRoleNaturalKeyMustBeInteger",
                  factName,
                  dimensionName,
                  naturalKey,
                  dimensionKeyMeta.getTypeDesc()),
              checkSource));
    }
  }

  public static String resolveStreamKeyField(
      DmFactDimensionRole role, String naturalKey, IVariables variables) {
    if (role.isTruncateToDateKey()) {
      return resolve(naturalKey, variables);
    }
    return resolveSourceFieldName(role, naturalKey, variables);
  }

  public static String resolveSourceFieldName(
      DmFactDimensionRole role, String naturalKey, IVariables variables) {
    String sourceField = resolve(role.getSourceFieldName(), variables);
    if (!Utils.isEmpty(sourceField)) {
      return sourceField;
    }
    return resolve(naturalKey, variables);
  }

  private static boolean isDateLikeType(int type) {
    return type == IValueMeta.TYPE_DATE || type == IValueMeta.TYPE_TIMESTAMP;
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
