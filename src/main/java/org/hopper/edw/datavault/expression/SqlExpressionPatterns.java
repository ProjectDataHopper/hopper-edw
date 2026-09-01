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

import java.util.List;

/** Built-in SQL scalar patterns for the expression editor (issue #150). */
public final class SqlExpressionPatterns {

  public static final String CAT_CONDITIONAL = "SqlExpressionPattern.Category.Conditional";
  public static final String CAT_NULL = "SqlExpressionPattern.Category.Null";
  public static final String CAT_CAST = "SqlExpressionPattern.Category.Cast";
  public static final String CAT_COMPARE = "SqlExpressionPattern.Category.Comparison";
  public static final String CAT_STRING = "SqlExpressionPattern.Category.String";
  public static final String CAT_ARITH = "SqlExpressionPattern.Category.Arithmetic";

  private static final List<SqlExpressionPattern> PATTERNS =
      List.of(
          p(
              CAT_CONDITIONAL,
              "SqlExpressionPattern.SearchedCase.Label",
              "CASE WHEN  THEN  WHEN  THEN  ELSE  END",
              "SqlExpressionPattern.SearchedCase.Hint"),
          p(
              CAT_CONDITIONAL,
              "SqlExpressionPattern.SimpleCase.Label",
              "CASE  WHEN  THEN  ELSE  END",
              "SqlExpressionPattern.SimpleCase.Hint"),
          p(
              CAT_CONDITIONAL,
              "SqlExpressionPattern.DeletedFlag.Label",
              "CASE WHEN  = 'Y' THEN NULL ELSE  END",
              "SqlExpressionPattern.DeletedFlag.Hint"),
          p(
              CAT_NULL,
              "SqlExpressionPattern.Coalesce2.Label",
              "COALESCE(, )",
              "SqlExpressionPattern.Coalesce.Hint"),
          p(
              CAT_NULL,
              "SqlExpressionPattern.Coalesce4.Label",
              "COALESCE(, , , )",
              "SqlExpressionPattern.Coalesce.Hint"),
          p(
              CAT_NULL,
              "SqlExpressionPattern.Coalesce6.Label",
              "COALESCE(, , , , , )",
              "SqlExpressionPattern.Coalesce.Hint"),
          p(CAT_NULL, "SqlExpressionPattern.Nvl.Label", "NVL(, )", "SqlExpressionPattern.Nvl.Hint"),
          p(
              CAT_NULL,
              "SqlExpressionPattern.NullIf.Label",
              "NULLIF(, )",
              "SqlExpressionPattern.NullIf.Hint"),
          p(
              CAT_CAST,
              "SqlExpressionPattern.CastVarchar.Label",
              "CAST( AS VARCHAR())",
              "SqlExpressionPattern.CastVarchar.Hint"),
          p(
              CAT_CAST,
              "SqlExpressionPattern.CastInteger.Label",
              "CAST( AS INTEGER)",
              "SqlExpressionPattern.CastInteger.Hint"),
          p(
              CAT_CAST,
              "SqlExpressionPattern.CastTimestamp.Label",
              "CAST( AS TIMESTAMP)",
              "SqlExpressionPattern.CastTimestamp.Hint"),
          p(
              CAT_CAST,
              "SqlExpressionPattern.CastBoolean.Label",
              "CAST( AS BOOLEAN)",
              "SqlExpressionPattern.CastBoolean.Hint"),
          p(
              CAT_COMPARE,
              "SqlExpressionPattern.IsNull.Label",
              " IS NULL",
              "SqlExpressionPattern.IsNull.Hint"),
          p(
              CAT_COMPARE,
              "SqlExpressionPattern.IsNotNull.Label",
              " IS NOT NULL",
              "SqlExpressionPattern.IsNotNull.Hint"),
          p(
              CAT_COMPARE,
              "SqlExpressionPattern.AndOr.Label",
              "(  OR  ) AND ",
              "SqlExpressionPattern.AndOr.Hint"),
          p(
              CAT_STRING,
              "SqlExpressionPattern.Upper.Label",
              "UPPER()",
              "SqlExpressionPattern.Upper.Hint"),
          p(
              CAT_STRING,
              "SqlExpressionPattern.Lower.Label",
              "LOWER()",
              "SqlExpressionPattern.Lower.Hint"),
          p(
              CAT_STRING,
              "SqlExpressionPattern.Trim.Label",
              "TRIM()",
              "SqlExpressionPattern.Trim.Hint"),
          p(
              CAT_STRING,
              "SqlExpressionPattern.Substring.Label",
              "SUBSTRING(, , )",
              "SqlExpressionPattern.Substring.Hint"),
          p(
              CAT_STRING,
              "SqlExpressionPattern.Concat.Label",
              "CONCAT(, )",
              "SqlExpressionPattern.Concat.Hint"),
          p(CAT_ARITH, "SqlExpressionPattern.Add.Label", " + ", "SqlExpressionPattern.Arith.Hint"),
          p(
              CAT_ARITH,
              "SqlExpressionPattern.Subtract.Label",
              " - ",
              "SqlExpressionPattern.Arith.Hint"),
          p(
              CAT_ARITH,
              "SqlExpressionPattern.Multiply.Label",
              " * ",
              "SqlExpressionPattern.Arith.Hint"),
          p(
              CAT_ARITH,
              "SqlExpressionPattern.Divide.Label",
              " / ",
              "SqlExpressionPattern.Arith.Hint"));

  private SqlExpressionPatterns() {}

  public static List<SqlExpressionPattern> all() {
    return PATTERNS;
  }

  public static String quoteIdentifier(String name) {
    if (name == null || name.isEmpty()) {
      return "";
    }
    if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      return name;
    }
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  private static SqlExpressionPattern p(
      String categoryKey, String labelKey, String snippet, String hintKey) {
    return new SqlExpressionPattern(categoryKey, labelKey, snippet, hintKey);
  }
}
