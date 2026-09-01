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
package org.hopper.edw.datavault.virtualization.generate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.DateString;
import org.apache.calcite.util.NlsString;
import org.apache.calcite.util.TimeString;
import org.apache.calcite.util.TimestampString;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlException;
import org.hopper.edw.datavault.virtualization.sql.SupportedSqlFeatures;

/**
 * Prints a Calcite {@link RexNode} as a SQL scalar the SQL Expression engine can parse.
 *
 * <p>Used when residual Project expressions are not Calculator-shaped ({@code CASE}, real {@code
 * CAST}, N-arg {@code COALESCE}, and other allow-listed scalars).
 */
public final class RexToSqlExpression {

  private static final Pattern SIMPLE_IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private RexToSqlExpression() {}

  public static String toSql(RexNode expr, List<String> inputNames) throws SourceModelSqlException {
    if (expr == null) {
      throw new SourceModelSqlException(
          "Residual expression is empty. " + SupportedSqlFeatures.SUMMARY);
    }
    try {
      return print(expr, inputNames);
    } catch (SourceModelSqlException e) {
      throw e;
    } catch (Exception e) {
      throw new SourceModelSqlException(
          "Unsupported residual expression: " + expr + ". " + SupportedSqlFeatures.SUMMARY, e);
    }
  }

  private static String print(RexNode expr, List<String> inputNames)
      throws SourceModelSqlException {
    if (expr instanceof RexInputRef ref) {
      if (ref.getIndex() < 0 || ref.getIndex() >= inputNames.size()) {
        throw new SourceModelSqlException("Residual expression refers to an unknown input field");
      }
      return quoteIdent(inputNames.get(ref.getIndex()));
    }
    if (expr instanceof RexLiteral lit) {
      return printLiteral(lit);
    }
    if (expr instanceof RexCall call) {
      return printCall(call, inputNames);
    }
    throw new SourceModelSqlException(
        "Unsupported residual expression: " + expr + ". " + SupportedSqlFeatures.SUMMARY);
  }

  private static String printCall(RexCall call, List<String> inputNames)
      throws SourceModelSqlException {
    SqlKind kind = call.getKind();
    List<RexNode> ops = call.getOperands();
    return switch (kind) {
      case CASE -> printCase(ops, inputNames);
      case CAST -> printCast(ops, call.getType(), inputNames);
      case COALESCE, NVL ->
          printFunction(kind == SqlKind.NVL ? "NVL" : "COALESCE", ops, inputNames);
      case NULLIF -> printFunction("NULLIF", ops, inputNames);
      case AND -> joinBinary("AND", ops, inputNames);
      case OR -> joinBinary("OR", ops, inputNames);
      case NOT -> "NOT (" + print(ops.get(0), inputNames) + ")";
      case IS_NULL -> print(ops.get(0), inputNames) + " IS NULL";
      case IS_NOT_NULL -> print(ops.get(0), inputNames) + " IS NOT NULL";
      case IS_TRUE -> print(ops.get(0), inputNames) + " IS TRUE";
      case IS_FALSE -> print(ops.get(0), inputNames) + " IS FALSE";
      case IS_NOT_TRUE -> print(ops.get(0), inputNames) + " IS NOT TRUE";
      case IS_NOT_FALSE -> print(ops.get(0), inputNames) + " IS NOT FALSE";
      case EQUALS -> binary("=", ops, inputNames);
      case NOT_EQUALS -> binary("<>", ops, inputNames);
      case LESS_THAN -> binary("<", ops, inputNames);
      case LESS_THAN_OR_EQUAL -> binary("<=", ops, inputNames);
      case GREATER_THAN -> binary(">", ops, inputNames);
      case GREATER_THAN_OR_EQUAL -> binary(">=", ops, inputNames);
      case PLUS -> binary("+", ops, inputNames);
      case MINUS -> binary("-", ops, inputNames);
      case TIMES -> binary("*", ops, inputNames);
      case DIVIDE -> binary("/", ops, inputNames);
      case PLUS_PREFIX -> print(ops.get(0), inputNames);
      case MINUS_PREFIX -> "-(" + print(ops.get(0), inputNames) + ")";
      case TRIM -> printTrim(ops, inputNames);
      case OTHER_FUNCTION, OTHER -> printNamedFunction(call, ops, inputNames);
      default -> {
        String name = call.getOperator() != null ? call.getOperator().getName() : kind.name();
        if ("||".equals(name) || "CONCAT".equalsIgnoreCase(name)) {
          yield printFunction("CONCAT", ops, inputNames);
        }
        throw new SourceModelSqlException(
            "Unsupported residual expression: " + call + ". " + SupportedSqlFeatures.SUMMARY);
      }
    };
  }

  private static String printCase(List<RexNode> ops, List<String> inputNames)
      throws SourceModelSqlException {
    StringBuilder sql = new StringBuilder("CASE");
    int i = 0;
    while (i + 1 < ops.size()) {
      sql.append(" WHEN ")
          .append(print(ops.get(i), inputNames))
          .append(" THEN ")
          .append(print(ops.get(i + 1), inputNames));
      i += 2;
    }
    if (i < ops.size()) {
      sql.append(" ELSE ").append(print(ops.get(i), inputNames));
    }
    sql.append(" END");
    return sql.toString();
  }

  private static String printCast(List<RexNode> ops, RelDataType type, List<String> inputNames)
      throws SourceModelSqlException {
    if (ops.isEmpty()) {
      throw new SourceModelSqlException("CAST needs an argument");
    }
    return "CAST(" + print(ops.get(0), inputNames) + " AS " + sqlTypeName(type) + ")";
  }

  private static String printTrim(List<RexNode> ops, List<String> inputNames)
      throws SourceModelSqlException {
    if (ops.isEmpty()) {
      throw new SourceModelSqlException("TRIM needs an argument");
    }
    RexNode value = ops.size() >= 3 ? ops.get(2) : ops.get(ops.size() - 1);
    return "TRIM(" + print(value, inputNames) + ")";
  }

  private static String printNamedFunction(RexCall call, List<RexNode> ops, List<String> inputNames)
      throws SourceModelSqlException {
    String name =
        call.getOperator() != null && call.getOperator().getName() != null
            ? call.getOperator().getName().trim().toUpperCase(Locale.ROOT)
            : "";
    if ("||".equals(call.getOperator() != null ? call.getOperator().getName() : "")
        || "CONCAT".equals(name)) {
      return printFunction("CONCAT", ops, inputNames);
    }
    if (name.isEmpty()) {
      throw new SourceModelSqlException(
          "Unsupported residual expression: " + call + ". " + SupportedSqlFeatures.SUMMARY);
    }
    return printFunction(name, ops, inputNames);
  }

  private static String printFunction(String name, List<RexNode> ops, List<String> inputNames)
      throws SourceModelSqlException {
    StringBuilder sql = new StringBuilder(name).append('(');
    for (int i = 0; i < ops.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append(print(ops.get(i), inputNames));
    }
    return sql.append(')').toString();
  }

  private static String binary(String op, List<RexNode> ops, List<String> inputNames)
      throws SourceModelSqlException {
    if (ops.size() != 2) {
      throw new SourceModelSqlException("Operator " + op + " needs two arguments");
    }
    return "("
        + print(ops.get(0), inputNames)
        + " "
        + op
        + " "
        + print(ops.get(1), inputNames)
        + ")";
  }

  private static String joinBinary(String op, List<RexNode> ops, List<String> inputNames)
      throws SourceModelSqlException {
    if (ops.isEmpty()) {
      throw new SourceModelSqlException("Operator " + op + " needs arguments");
    }
    StringBuilder sql = new StringBuilder();
    for (int i = 0; i < ops.size(); i++) {
      if (i > 0) {
        sql.append(' ').append(op).append(' ');
      }
      sql.append('(').append(print(ops.get(i), inputNames)).append(')');
    }
    return sql.toString();
  }

  private static String printLiteral(RexLiteral lit) {
    if (lit == null || lit.isNull()) {
      return "NULL";
    }
    Object value = lit.getValue2();
    if (value == null) {
      value = lit.getValue();
    }
    if (value == null) {
      return "NULL";
    }
    if (value instanceof NlsString nls) {
      return quoteString(nls.getValue());
    }
    if (value instanceof DateString dateString) {
      return "DATE " + quoteString(dateString.toString());
    }
    if (value instanceof TimeString timeString) {
      return "TIME " + quoteString(timeString.toString());
    }
    if (value instanceof TimestampString timestampString) {
      return "TIMESTAMP " + quoteString(String.valueOf(timestampString).replace('T', ' '));
    }
    if (value instanceof Boolean bool) {
      return bool ? "TRUE" : "FALSE";
    }
    if (value instanceof BigDecimal bd) {
      return bd.toPlainString();
    }
    if (value instanceof Number) {
      return String.valueOf(value);
    }
    return quoteString(String.valueOf(value));
  }

  static String sqlTypeName(RelDataType type) {
    if (type == null || type.getSqlTypeName() == null) {
      return "VARCHAR";
    }
    SqlTypeName name = type.getSqlTypeName();
    return switch (name) {
      case VARCHAR, CHAR -> {
        int precision = type.getPrecision();
        if (precision > 0 && precision < 1_000_000) {
          yield "VARCHAR(" + precision + ")";
        }
        yield "VARCHAR";
      }
      case DECIMAL -> {
        int p = type.getPrecision();
        int s = type.getScale();
        if (p > 0) {
          yield s >= 0 ? "DECIMAL(" + p + ", " + s + ")" : "DECIMAL(" + p + ")";
        }
        yield "DECIMAL";
      }
      case BOOLEAN -> "BOOLEAN";
      case TINYINT, SMALLINT, INTEGER -> "INTEGER";
      case BIGINT -> "BIGINT";
      case FLOAT, REAL, DOUBLE -> "DOUBLE";
      case DATE -> "DATE";
      case TIME -> "TIME";
      case TIMESTAMP, TIMESTAMP_WITH_LOCAL_TIME_ZONE -> "TIMESTAMP";
      default -> name.getName();
    };
  }

  static String quoteIdent(String name) {
    if (name == null) {
      return "\"\"";
    }
    if (SIMPLE_IDENT.matcher(name).matches()) {
      return name;
    }
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  private static String quoteString(String value) {
    String text = value == null ? "" : value;
    return "'" + text.replace("'", "''") + "'";
  }
}
