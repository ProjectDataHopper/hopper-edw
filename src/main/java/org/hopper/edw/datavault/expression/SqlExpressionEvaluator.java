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

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.hopper.edw.datavault.virtualization.calcite.HopTypeSystem;

/** Evaluates a compiled SQL scalar against one Hop row. */
public final class SqlExpressionEvaluator {

  private SqlExpressionEvaluator() {}

  public static Object evaluate(SqlCompiledExpression compiled, Object[] row)
      throws SqlExpressionException {
    if (compiled == null) {
      throw new SqlExpressionException("Compiled expression is required");
    }
    try {
      Object javaValue = eval(compiled.getRexNode(), compiled.getInputValueMetas(), row);
      return convertToHop(javaValue, compiled.getOutputValueMeta());
    } catch (SqlExpressionException e) {
      throw e;
    } catch (Exception e) {
      throw new SqlExpressionException(
          "Error evaluating SQL expression '" + compiled.getFieldName() + "': " + e.getMessage(),
          e);
    }
  }

  private static Object eval(RexNode node, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    if (node == null) {
      return null;
    }
    if (node instanceof RexInputRef ref) {
      return hopToJava(valueMetas, row, ref.getIndex());
    }
    if (node instanceof RexLiteral lit) {
      return literalToJava(lit);
    }
    if (node instanceof RexCall call) {
      return evalCall(call, valueMetas, row);
    }
    throw new SqlExpressionException("Unsupported expression node: " + node.getKind());
  }

  private static Object evalCall(RexCall call, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    SqlKind kind = call.getKind();
    List<RexNode> operands = call.getOperands();
    return switch (kind) {
      case CASE -> evalCase(operands, valueMetas, row);
      case CAST ->
          evalCast(operands.isEmpty() ? null : eval(operands.get(0), valueMetas, row), call);
      case COALESCE, NVL -> evalCoalesce(operands, valueMetas, row);
      case AND -> evalAnd(operands, valueMetas, row);
      case OR -> evalOr(operands, valueMetas, row);
      case NOT -> not3(asBoolean(eval(operands.get(0), valueMetas, row)));
      case IS_NULL -> eval(operands.get(0), valueMetas, row) == null;
      case IS_NOT_NULL -> eval(operands.get(0), valueMetas, row) != null;
      case IS_TRUE -> Boolean.TRUE.equals(asBoolean(eval(operands.get(0), valueMetas, row)));
      case IS_FALSE -> Boolean.FALSE.equals(asBoolean(eval(operands.get(0), valueMetas, row)));
      case IS_NOT_TRUE -> !Boolean.TRUE.equals(asBoolean(eval(operands.get(0), valueMetas, row)));
      case IS_NOT_FALSE -> !Boolean.FALSE.equals(asBoolean(eval(operands.get(0), valueMetas, row)));
      case EQUALS ->
          eq(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      case NOT_EQUALS -> {
        Boolean equal =
            eq(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
        yield not3(equal);
      }
      case LESS_THAN ->
          cmp(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row), -1);
      case LESS_THAN_OR_EQUAL ->
          cmpLe(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      case GREATER_THAN ->
          cmp(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row), 1);
      case GREATER_THAN_OR_EQUAL ->
          cmpGe(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      case PLUS ->
          add(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      case MINUS ->
          subtract(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      case TIMES ->
          multiply(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      case DIVIDE ->
          divide(eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      case PLUS_PREFIX -> eval(operands.get(0), valueMetas, row);
      case MINUS_PREFIX -> negate(eval(operands.get(0), valueMetas, row));
      case TRIM -> evalTrim(operands, valueMetas, row);
      case OTHER_FUNCTION, OTHER -> evalFunction(call, operands, valueMetas, row);
      default ->
          throw new SqlExpressionException("Unsupported operator '" + call.getOperator() + "'");
    };
  }

  private static Object evalFunction(
      RexCall call, List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    String name = call.getOperator().getName();
    if ("||".equals(name) || "CONCAT".equalsIgnoreCase(name)) {
      return evalConcat(operands, valueMetas, row);
    }
    return switch (name.toUpperCase(Locale.ROOT)) {
      case "COALESCE", "NVL" -> evalCoalesce(operands, valueMetas, row);
      case "NULLIF" -> evalNullIf(operands, valueMetas, row);
      case "UPPER" -> {
        Object v = eval(operands.get(0), valueMetas, row);
        yield v == null ? null : String.valueOf(v).toUpperCase(Locale.ROOT);
      }
      case "LOWER" -> {
        Object v = eval(operands.get(0), valueMetas, row);
        yield v == null ? null : String.valueOf(v).toLowerCase(Locale.ROOT);
      }
      case "TRIM" -> evalTrim(operands, valueMetas, row);
      case "SUBSTRING", "SUBSTR" -> evalSubstring(operands, valueMetas, row);
      case "CHAR_LENGTH", "CHARACTER_LENGTH", "LENGTH" -> {
        Object v = eval(operands.get(0), valueMetas, row);
        yield v == null ? null : (long) String.valueOf(v).length();
      }
      case "HEX" -> SqlExpressionMysqlFunctions.hex(eval(operands.get(0), valueMetas, row));
      case "UNHEX" -> SqlExpressionMysqlFunctions.unhex(eval(operands.get(0), valueMetas, row));
      case "MD5" -> SqlExpressionMysqlFunctions.md5(eval(operands.get(0), valueMetas, row));
      case "DATE_FORMAT" ->
          SqlExpressionMysqlFunctions.dateFormat(
              eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      case "TO_DATE" ->
          SqlExpressionMysqlFunctions.toDate(
              eval(operands.get(0), valueMetas, row), eval(operands.get(1), valueMetas, row));
      default -> throw new SqlExpressionException("Unsupported function '" + name + "'");
    };
  }

  private static Object evalCase(List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    int i = 0;
    while (i + 1 < operands.size()) {
      Object when = eval(operands.get(i), valueMetas, row);
      RexNode thenNode = operands.get(i + 1);
      i += 2;
      if (Boolean.TRUE.equals(asBoolean(when))) {
        return eval(thenNode, valueMetas, row);
      }
    }
    if (i < operands.size()) {
      return eval(operands.get(i), valueMetas, row);
    }
    return null;
  }

  private static Object evalCoalesce(List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    for (RexNode operand : operands) {
      Object value = eval(operand, valueMetas, row);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static Object evalNullIf(List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    Object left = eval(operands.get(0), valueMetas, row);
    Object right = eval(operands.get(1), valueMetas, row);
    Boolean equal = eq(left, right);
    if (Boolean.TRUE.equals(equal)) {
      return null;
    }
    return left;
  }

  private static Object evalConcat(List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    StringBuilder builder = new StringBuilder();
    for (RexNode operand : operands) {
      Object value = eval(operand, valueMetas, row);
      if (value == null) {
        return null;
      }
      builder.append(value);
    }
    return builder.toString();
  }

  private static Object evalTrim(List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    if (operands.isEmpty()) {
      return null;
    }
    // Calcite TRIM(flag, char, string) or TRIM(string)
    Object value;
    if (operands.size() >= 3) {
      value = eval(operands.get(2), valueMetas, row);
    } else {
      value = eval(operands.get(operands.size() - 1), valueMetas, row);
    }
    return value == null ? null : String.valueOf(value).trim();
  }

  private static Object evalSubstring(List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    Object source = eval(operands.get(0), valueMetas, row);
    if (source == null) {
      return null;
    }
    String text = String.valueOf(source);
    Object startObj = eval(operands.get(1), valueMetas, row);
    if (startObj == null) {
      return null;
    }
    int start = toNumber(startObj).intValue();
    if (start == 0) {
      start = 1;
    }
    if (start < 0) {
      start = text.length() + start + 1;
    }
    int from = Math.max(start - 1, 0);
    if (from >= text.length()) {
      return "";
    }
    if (operands.size() < 3) {
      return text.substring(from);
    }
    Object lenObj = eval(operands.get(2), valueMetas, row);
    if (lenObj == null) {
      return null;
    }
    int length = Math.max(toNumber(lenObj).intValue(), 0);
    int to = Math.min(from + length, text.length());
    return text.substring(from, to);
  }

  private static Boolean evalAnd(List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    Boolean result = Boolean.TRUE;
    for (RexNode operand : operands) {
      result = and3(result, asBoolean(eval(operand, valueMetas, row)));
      if (Boolean.FALSE.equals(result)) {
        return false;
      }
    }
    return result;
  }

  private static Boolean evalOr(List<RexNode> operands, IValueMeta[] valueMetas, Object[] row)
      throws HopException, SqlExpressionException {
    Boolean result = Boolean.FALSE;
    for (RexNode operand : operands) {
      result = or3(result, asBoolean(eval(operand, valueMetas, row)));
      if (Boolean.TRUE.equals(result)) {
        return true;
      }
    }
    return result;
  }

  private static Object evalCast(Object value, RexCall call)
      throws SqlExpressionException, HopException {
    if (value == null) {
      return null;
    }
    RelDataType type = call.getType();
    SqlTypeName sqlType = type.getSqlTypeName();
    try {
      int hopType = HopTypeSystem.toHopType(type);
      IValueMeta target = ValueMetaFactory.createValueMeta("cast", hopType);
      int length = HopTypeSystem.toHopLength(type);
      int scale = HopTypeSystem.toHopScale(type);
      if (length > 0) {
        target.setLength(length, scale);
      }
      Object converted = convertToHop(value, target);
      if (converted instanceof String text
          && (sqlType == SqlTypeName.VARCHAR || sqlType == SqlTypeName.CHAR)
          && length > 0
          && text.length() > length) {
        return text.substring(0, length);
      }
      return converted;
    } catch (Exception e) {
      throw new SqlExpressionException("CAST failed: " + e.getMessage(), e);
    }
  }

  private static Boolean eq(Object left, Object right) {
    if (left == null || right == null) {
      return null;
    }
    if (left instanceof Number || right instanceof Number) {
      return toNumber(left).compareTo(toNumber(right)) == 0;
    }
    if (left instanceof Date || right instanceof Date) {
      return toDate(left).getTime() == toDate(right).getTime();
    }
    if (left instanceof Boolean || right instanceof Boolean) {
      return asBoolean(left).equals(asBoolean(right));
    }
    return String.valueOf(left).equals(String.valueOf(right));
  }

  private static Boolean cmp(Object left, Object right, int wantedSign) {
    Integer c = compareSql(left, right);
    if (c == null) {
      return null;
    }
    int sign = Integer.compare(c, 0);
    return sign == wantedSign;
  }

  private static Boolean cmpLe(Object left, Object right) {
    Integer c = compareSql(left, right);
    if (c == null) {
      return null;
    }
    return c <= 0;
  }

  private static Boolean cmpGe(Object left, Object right) {
    Integer c = compareSql(left, right);
    if (c == null) {
      return null;
    }
    return c >= 0;
  }

  private static Integer compareSql(Object left, Object right) {
    if (left == null || right == null) {
      return null;
    }
    if (left instanceof Number || right instanceof Number) {
      return toNumber(left).compareTo(toNumber(right));
    }
    if (left instanceof Date || right instanceof Date) {
      return Long.compare(toDate(left).getTime(), toDate(right).getTime());
    }
    return String.valueOf(left).compareTo(String.valueOf(right));
  }

  private static Object add(Object left, Object right) {
    if (left == null || right == null) {
      return null;
    }
    return toNumber(left).add(toNumber(right));
  }

  private static Object subtract(Object left, Object right) {
    if (left == null || right == null) {
      return null;
    }
    return toNumber(left).subtract(toNumber(right));
  }

  private static Object multiply(Object left, Object right) {
    if (left == null || right == null) {
      return null;
    }
    return toNumber(left).multiply(toNumber(right));
  }

  private static Object divide(Object left, Object right) {
    if (left == null || right == null) {
      return null;
    }
    BigDecimal divisor = toNumber(right);
    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return toNumber(left).divide(divisor, MathContext.DECIMAL64);
  }

  private static Object negate(Object value) {
    if (value == null) {
      return null;
    }
    return toNumber(value).negate();
  }

  private static Boolean and3(Boolean a, Boolean b) {
    if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) {
      return false;
    }
    if (a == null || b == null) {
      return null;
    }
    return true;
  }

  private static Boolean or3(Boolean a, Boolean b) {
    if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) {
      return true;
    }
    if (a == null || b == null) {
      return null;
    }
    return false;
  }

  private static Boolean not3(Boolean value) {
    if (value == null) {
      return null;
    }
    return !value;
  }

  private static Boolean asBoolean(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Number n) {
      return n.intValue() != 0;
    }
    String text = String.valueOf(value).trim();
    if (text.equalsIgnoreCase("Y")
        || text.equalsIgnoreCase("YES")
        || text.equalsIgnoreCase("TRUE")
        || text.equals("1")) {
      return true;
    }
    if (text.equalsIgnoreCase("N")
        || text.equalsIgnoreCase("NO")
        || text.equalsIgnoreCase("FALSE")
        || text.equals("0")) {
      return false;
    }
    return Boolean.parseBoolean(text);
  }

  private static BigDecimal toNumber(Object value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      if (n instanceof Double || n instanceof Float) {
        return BigDecimal.valueOf(n.doubleValue());
      }
      return BigDecimal.valueOf(n.longValue());
    }
    return new BigDecimal(String.valueOf(value).trim());
  }

  private static Date toDate(Object value) {
    if (value instanceof Date date) {
      return date;
    }
    if (value instanceof Calendar calendar) {
      return calendar.getTime();
    }
    return Timestamp.valueOf(String.valueOf(value));
  }

  private static Object hopToJava(IValueMeta[] valueMetas, Object[] row, int index)
      throws HopValueException {
    if (valueMetas == null || index < 0 || index >= valueMetas.length) {
      return null;
    }
    IValueMeta valueMeta = valueMetas[index];
    Object value = row != null && index < row.length ? row[index] : null;
    if (valueMeta == null || valueMeta.isNull(value)) {
      return null;
    }
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_BOOLEAN -> valueMeta.getBoolean(value);
      case IValueMeta.TYPE_INTEGER -> valueMeta.getInteger(value);
      case IValueMeta.TYPE_NUMBER -> valueMeta.getNumber(value);
      case IValueMeta.TYPE_BIGNUMBER -> valueMeta.getBigNumber(value);
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> valueMeta.getDate(value);
      case IValueMeta.TYPE_BINARY -> valueMeta.getBinary(value);
      default -> valueMeta.getString(value);
    };
  }

  private static Object literalToJava(RexLiteral lit) {
    if (lit == null || lit.isNull()) {
      return null;
    }
    Object value = lit.getValue2();
    if (value == null) {
      value = lit.getValue();
    }
    if (value instanceof NlsString nls) {
      return nls.getValue();
    }
    if (value instanceof DateString dateString) {
      return java.sql.Date.valueOf(dateString.toString());
    }
    if (value instanceof TimeString || value instanceof TimestampString) {
      return Timestamp.valueOf(String.valueOf(value).replace('T', ' '));
    }
    if (value instanceof Calendar calendar) {
      return calendar.getTime();
    }
    return value;
  }

  static Object convertToHop(Object javaValue, IValueMeta target) throws HopException {
    if (target == null || javaValue == null) {
      return null;
    }
    if (compatibleHopValue(javaValue, target)) {
      if (javaValue instanceof String text
          && target.getLength() > 0
          && text.length() > target.getLength()) {
        return text.substring(0, target.getLength());
      }
      return javaValue;
    }
    IValueMeta source = guessSourceMeta(javaValue);
    Object converted = target.convertData(source, javaValue);
    if (converted instanceof String text
        && target.getLength() > 0
        && text.length() > target.getLength()) {
      return text.substring(0, target.getLength());
    }
    return converted;
  }

  private static boolean compatibleHopValue(Object javaValue, IValueMeta target) {
    return switch (target.getType()) {
      case IValueMeta.TYPE_STRING -> javaValue instanceof String;
      case IValueMeta.TYPE_BOOLEAN -> javaValue instanceof Boolean;
      case IValueMeta.TYPE_INTEGER -> javaValue instanceof Long || javaValue instanceof Integer;
      case IValueMeta.TYPE_NUMBER -> javaValue instanceof Double;
      case IValueMeta.TYPE_BIGNUMBER -> javaValue instanceof BigDecimal;
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> javaValue instanceof Date;
      case IValueMeta.TYPE_BINARY -> javaValue instanceof byte[];
      default -> false;
    };
  }

  private static IValueMeta guessSourceMeta(Object javaValue) throws HopException {
    int type;
    if (javaValue instanceof Boolean) {
      type = IValueMeta.TYPE_BOOLEAN;
    } else if (javaValue instanceof BigDecimal) {
      type = IValueMeta.TYPE_BIGNUMBER;
    } else if (javaValue instanceof Double || javaValue instanceof Float) {
      type = IValueMeta.TYPE_NUMBER;
    } else if (javaValue instanceof Number) {
      type = IValueMeta.TYPE_INTEGER;
    } else if (javaValue instanceof Date) {
      type = IValueMeta.TYPE_DATE;
    } else if (javaValue instanceof byte[]) {
      type = IValueMeta.TYPE_BINARY;
    } else {
      type = IValueMeta.TYPE_STRING;
    }
    return ValueMetaFactory.createValueMeta("src", type);
  }

  static IRowMeta outputRowMeta(IRowMeta inputRowMeta, List<SqlCompiledExpression> compiled)
      throws SqlExpressionException {
    try {
      IRowMeta output = inputRowMeta != null ? inputRowMeta.clone() : new RowMeta();
      if (compiled == null) {
        return output;
      }
      for (SqlCompiledExpression expression : compiled) {
        if (expression == null || expression.getOutputValueMeta() == null) {
          continue;
        }
        IValueMeta valueMeta = expression.getOutputValueMeta().clone();
        int existing = output.indexOfValue(valueMeta.getName());
        if (existing >= 0) {
          output.setValueMeta(existing, valueMeta);
        } else {
          output.addValueMeta(valueMeta);
        }
      }
      return output;
    } catch (Exception e) {
      throw new SqlExpressionException("Unable to build SQL expression output layout", e);
    }
  }
}
