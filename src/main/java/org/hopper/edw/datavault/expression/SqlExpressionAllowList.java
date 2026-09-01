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

import java.util.Locale;
import java.util.Set;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexVisitorImpl;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;

/**
 * Deterministic scalar SQL allow-list for issue #150. Non-deterministic functions, subqueries, and
 * aggregates are rejected at compile time.
 */
public final class SqlExpressionAllowList {

  private static final Set<SqlKind> ALLOWED_KINDS =
      Set.of(
          SqlKind.INPUT_REF,
          SqlKind.LITERAL,
          SqlKind.CASE,
          SqlKind.CAST,
          SqlKind.COALESCE,
          SqlKind.NVL,
          SqlKind.AND,
          SqlKind.OR,
          SqlKind.NOT,
          SqlKind.EQUALS,
          SqlKind.NOT_EQUALS,
          SqlKind.LESS_THAN,
          SqlKind.LESS_THAN_OR_EQUAL,
          SqlKind.GREATER_THAN,
          SqlKind.GREATER_THAN_OR_EQUAL,
          SqlKind.IS_NULL,
          SqlKind.IS_NOT_NULL,
          SqlKind.IS_TRUE,
          SqlKind.IS_FALSE,
          SqlKind.IS_NOT_TRUE,
          SqlKind.IS_NOT_FALSE,
          SqlKind.PLUS,
          SqlKind.MINUS,
          SqlKind.TIMES,
          SqlKind.DIVIDE,
          SqlKind.PLUS_PREFIX,
          SqlKind.MINUS_PREFIX,
          SqlKind.OTHER_FUNCTION,
          SqlKind.OTHER,
          SqlKind.TRIM,
          SqlKind.CEIL,
          SqlKind.FLOOR);

  private static final Set<String> ALLOWED_FUNCTIONS =
      Set.of(
          "COALESCE",
          "NVL",
          "NULLIF",
          "TRIM",
          "UPPER",
          "LOWER",
          "SUBSTRING",
          "SUBSTR",
          "CONCAT",
          "HEX",
          "UNHEX",
          "MD5",
          "DATE_FORMAT",
          "TO_DATE",
          "CHAR_LENGTH",
          "CHARACTER_LENGTH",
          "LENGTH",
          "CAST");

  private static final Set<String> FORBIDDEN_FUNCTIONS =
      Set.of(
          "NOW",
          "CURRENT_TIMESTAMP",
          "CURRENT_DATE",
          "CURRENT_TIME",
          "LOCALTIMESTAMP",
          "LOCALTIME",
          "SYSDATE",
          "GETDATE",
          "RANDOM",
          "RAND",
          "UUID",
          "GEN_RANDOM_UUID",
          "USER",
          "SESSION_USER",
          "SYSTEM_USER",
          "CURRENT_USER");

  private static final Set<SqlKind> FORBIDDEN_KINDS =
      Set.of(
          SqlKind.SCALAR_QUERY,
          SqlKind.EXISTS,
          SqlKind.IN,
          SqlKind.NOT_IN,
          SqlKind.SOME,
          SqlKind.ALL,
          SqlKind.MULTISET_VALUE_CONSTRUCTOR,
          SqlKind.ARRAY_VALUE_CONSTRUCTOR,
          SqlKind.MAP_VALUE_CONSTRUCTOR,
          SqlKind.OVER,
          SqlKind.AGGREGATE_FN,
          SqlKind.COUNT,
          SqlKind.SUM,
          SqlKind.AVG,
          SqlKind.MIN,
          SqlKind.MAX,
          SqlKind.ROW);

  private SqlExpressionAllowList() {}

  public static void check(RexNode node) throws SqlExpressionException {
    if (node == null) {
      throw new SqlExpressionException("Expression compiled to an empty node");
    }
    try {
      node.accept(new AllowListVisitor());
    } catch (AllowListRuntimeException e) {
      throw e.cause;
    }
  }

  private static final class AllowListVisitor extends RexVisitorImpl<Void> {
    private AllowListVisitor() {
      super(true);
    }

    @Override
    public Void visitInputRef(RexInputRef inputRef) {
      return null;
    }

    @Override
    public Void visitLiteral(RexLiteral literal) {
      return null;
    }

    @Override
    public Void visitCall(RexCall call) {
      SqlKind kind = call.getKind();
      if (FORBIDDEN_KINDS.contains(kind)) {
        throw reject("Function or operator '" + operatorName(call) + "' is not allowed");
      }
      String name = operatorName(call);
      if (FORBIDDEN_FUNCTIONS.contains(name)) {
        throw reject("Non-deterministic function '" + name + "' is not allowed");
      }
      if (kind == SqlKind.OTHER_FUNCTION || kind == SqlKind.OTHER || kind == SqlKind.TRIM) {
        if (!ALLOWED_FUNCTIONS.contains(name) && !isConcatOperator(call)) {
          throw reject("Function '" + name + "' is not allowed");
        }
      } else if (!ALLOWED_KINDS.contains(kind)) {
        throw reject("Operator '" + name + "' (" + kind + ") is not allowed");
      }
      return super.visitCall(call);
    }

    @Override
    public Void visitSubQuery(org.apache.calcite.rex.RexSubQuery subQuery) {
      throw reject("Subqueries are not allowed in SQL expressions");
    }

    @Override
    public Void visitOver(org.apache.calcite.rex.RexOver over) {
      throw reject("Window functions are not allowed in SQL expressions");
    }

    @Override
    public Void visitDynamicParam(org.apache.calcite.rex.RexDynamicParam dynamicParam) {
      throw reject("Dynamic parameters are not allowed in SQL expressions");
    }

    @Override
    public Void visitFieldAccess(org.apache.calcite.rex.RexFieldAccess fieldAccess) {
      throw reject("Field access is not allowed in SQL expressions");
    }

    @Override
    public Void visitCorrelVariable(org.apache.calcite.rex.RexCorrelVariable correlVariable) {
      throw reject("Correlated variables are not allowed in SQL expressions");
    }

    @Override
    public Void visitLambda(org.apache.calcite.rex.RexLambda lambda) {
      throw reject("Lambda expressions are not allowed");
    }
  }

  private static boolean isConcatOperator(RexCall call) {
    String name = operatorName(call);
    return "||".equals(call.getOperator().getName()) || "CONCAT".equals(name);
  }

  private static String operatorName(RexCall call) {
    SqlOperator op = call.getOperator();
    if (op == null || op.getName() == null) {
      return call.getKind().name();
    }
    return op.getName().trim().toUpperCase(Locale.ROOT);
  }

  private static AllowListRuntimeException reject(String message) {
    return new AllowListRuntimeException(new SqlExpressionException(message));
  }

  private static final class AllowListRuntimeException extends RuntimeException {
    private final SqlExpressionException cause;

    private AllowListRuntimeException(SqlExpressionException cause) {
      super(cause);
      this.cause = cause;
    }
  }
}
