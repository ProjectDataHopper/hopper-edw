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

import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Validates SQL-native source/target type compatibility for sort-sensitive merge pipelines. */
public final class DvSqlPhysicalTypeValidationSupport {

  private static final Class<?> PKG = DvSqlPhysicalTypeValidationSupport.class;

  /** Which kind of field the mismatch applies to — drives remediation text. */
  public enum RemediationKind {
    HUB_BUSINESS_KEY,
    SATELLITE_ORDER_KEY,
    SATELLITE_ATTRIBUTE
  }

  private DvSqlPhysicalTypeValidationSupport() {}

  public static void validatePhysicalSqlTypeMapping(
      IValueMeta sourceMeta,
      IValueMeta targetMeta,
      String context,
      DatabaseMeta targetDatabaseMeta,
      DataVaultConfiguration config,
      ICheckResultSource checkSource,
      java.util.List<ICheckResult> remarks) {
    validatePhysicalSqlTypeMapping(
        sourceMeta,
        targetMeta,
        context,
        targetDatabaseMeta,
        config,
        checkSource,
        remarks,
        RemediationKind.HUB_BUSINESS_KEY);
  }

  public static void validatePhysicalSqlTypeMapping(
      IValueMeta sourceMeta,
      IValueMeta targetMeta,
      String context,
      DatabaseMeta targetDatabaseMeta,
      DataVaultConfiguration config,
      ICheckResultSource checkSource,
      java.util.List<ICheckResult> remarks,
      RemediationKind kind) {
    if (sourceMeta == null || targetMeta == null || targetDatabaseMeta == null) {
      return;
    }
    if (!DvSqlOrderBySupport.isSqlServer(targetDatabaseMeta)) {
      return;
    }

    String sourceSqlType = normalizeSqlTypeName(sourceMeta.getOriginalColumnTypeName());
    String targetSqlType =
        normalizeSqlTypeName(resolveTargetSqlType(targetMeta, targetDatabaseMeta));
    if (Utils.isEmpty(sourceSqlType) || Utils.isEmpty(targetSqlType)) {
      return;
    }

    if (isSortSensitiveStringTypeMismatch(sourceSqlType, targetSqlType)) {
      remarks.add(
          new org.apache.hop.core.CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DvSqlPhysicalTypeValidation.SortSensitiveTypeMismatch",
                  context,
                  sourceSqlType,
                  targetSqlType,
                  remediationHint(kind != null ? kind : RemediationKind.HUB_BUSINESS_KEY)),
              checkSource));
    }
  }

  static String remediationHint(DataVaultConfiguration config) {
    return remediationHint(RemediationKind.HUB_BUSINESS_KEY);
  }

  static String remediationHint(DataVaultConfiguration config, RemediationKind kind) {
    return remediationHint(kind != null ? kind : RemediationKind.HUB_BUSINESS_KEY);
  }

  static String remediationHint(RemediationKind kind) {
    if (kind == RemediationKind.SATELLITE_ATTRIBUTE) {
      return BaseMessages.getString(
          PKG, "DvSqlPhysicalTypeValidation.Remediation.SatelliteAttribute");
    }
    if (kind == RemediationKind.SATELLITE_ORDER_KEY) {
      return BaseMessages.getString(
          PKG, "DvSqlPhysicalTypeValidation.Remediation.AutoOrderByCollation");
    }
    // Hub business key / general order key
    return BaseMessages.getString(
        PKG, "DvSqlPhysicalTypeValidation.Remediation.AutoOrderByCollation");
  }

  static String resolveTargetSqlType(IValueMeta targetMeta, DatabaseMeta targetDatabaseMeta) {
    String fieldDefinition = targetDatabaseMeta.getFieldDefinition(targetMeta, null, null, false);
    return extractSqlTypeName(fieldDefinition);
  }

  static String extractSqlTypeName(String fieldDefinition) {
    if (Utils.isEmpty(fieldDefinition)) {
      return null;
    }
    String trimmed = fieldDefinition.trim();
    int paren = trimmed.indexOf('(');
    if (paren > 0) {
      return trimmed.substring(0, paren).trim();
    }
    int space = trimmed.indexOf(' ');
    if (space > 0) {
      return trimmed.substring(0, space).trim();
    }
    return trimmed;
  }

  static String normalizeSqlTypeName(String sqlType) {
    if (Utils.isEmpty(sqlType)) {
      return null;
    }
    return Const.NVL(sqlType, "").trim().toUpperCase(Locale.ROOT);
  }

  static boolean isSortSensitiveStringTypeMismatch(String sourceSqlType, String targetSqlType) {
    if (Utils.isEmpty(sourceSqlType) || Utils.isEmpty(targetSqlType)) {
      return false;
    }
    if (sourceSqlType.equals(targetSqlType)) {
      return false;
    }
    boolean sourceUnicode = isUnicodeStringType(sourceSqlType);
    boolean targetUnicode = isUnicodeStringType(targetSqlType);
    boolean sourceAnsi = isAnsiStringType(sourceSqlType);
    boolean targetAnsi = isAnsiStringType(targetSqlType);
    return (sourceUnicode && targetAnsi) || (sourceAnsi && targetUnicode);
  }

  static boolean isUnicodeStringType(String sqlType) {
    return sqlType.startsWith("N")
        && (sqlType.contains("CHAR") || sqlType.equals("NTEXT") || sqlType.startsWith("NVARCHAR"));
  }

  static boolean isAnsiStringType(String sqlType) {
    if (isUnicodeStringType(sqlType)) {
      return false;
    }
    return sqlType.contains("CHAR") || sqlType.equals("TEXT");
  }
}
