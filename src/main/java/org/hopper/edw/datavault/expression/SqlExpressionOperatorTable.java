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
package org.hopper.edw.datavault.expression;

import org.apache.calcite.sql.SqlBasicFunction;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlOperandCountRanges;
import org.apache.calcite.sql.util.SqlOperatorTables;

/**
 * Calcite operators for SQL Expression, including MySQL / SingleStore-style {@code CONCAT}, {@code
 * HEX}, {@code UNHEX}, {@code MD5}, {@code DATE_FORMAT}, and {@code TO_DATE}.
 */
public final class SqlExpressionOperatorTable {

  static final SqlFunction CONCAT =
      SqlBasicFunction.create(
          "CONCAT",
          ReturnTypes.MULTIVALENT_STRING_SUM_PRECISION_NULLABLE,
          OperandTypes.repeat(SqlOperandCountRanges.from(1), OperandTypes.ANY),
          SqlFunctionCategory.STRING);

  static final SqlFunction HEX =
      SqlBasicFunction.create(
          "HEX", ReturnTypes.VARCHAR_NULLABLE, OperandTypes.ANY, SqlFunctionCategory.STRING);

  static final SqlFunction UNHEX =
      SqlBasicFunction.create(
          "UNHEX", ReturnTypes.VARBINARY_NULLABLE, OperandTypes.STRING, SqlFunctionCategory.STRING);

  static final SqlFunction MD5 =
      SqlBasicFunction.create(
          "MD5", ReturnTypes.VARCHAR_NULLABLE, OperandTypes.ANY, SqlFunctionCategory.STRING);

  static final SqlFunction DATE_FORMAT =
      SqlBasicFunction.create(
          "DATE_FORMAT",
          ReturnTypes.VARCHAR_NULLABLE,
          OperandTypes.ANY_ANY,
          SqlFunctionCategory.TIMEDATE);

  static final SqlFunction TO_DATE =
      SqlBasicFunction.create(
          "TO_DATE",
          ReturnTypes.TIMESTAMP_NULLABLE,
          OperandTypes.STRING_STRING,
          SqlFunctionCategory.TIMEDATE);

  private static final SqlOperatorTable INSTANCE =
      SqlOperatorTables.chain(
          SqlStdOperatorTable.instance(),
          SqlOperatorTables.of(CONCAT, HEX, UNHEX, MD5, DATE_FORMAT, TO_DATE));

  private SqlExpressionOperatorTable() {}

  public static SqlOperatorTable instance() {
    return INSTANCE;
  }
}
