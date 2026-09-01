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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Ordered list of compiled SQL expressions. Later expressions may reference earlier output field
 * names. Evaluation always starts from the original input row plus previously computed values.
 */
@Getter
public final class SqlExpressionProgram {

  private final IRowMeta inputRowMeta;
  private final IRowMeta outputRowMeta;
  private final List<SqlCompiledExpression> expressions;
  private final boolean keepInputFields;

  private SqlExpressionProgram(
      IRowMeta inputRowMeta,
      IRowMeta outputRowMeta,
      List<SqlCompiledExpression> expressions,
      boolean keepInputFields) {
    this.inputRowMeta = inputRowMeta;
    this.outputRowMeta = outputRowMeta;
    this.expressions = expressions;
    this.keepInputFields = keepInputFields;
  }

  public static SqlExpressionProgram compile(
      List<SqlExpressionSpec> specs, IRowMeta inputRowMeta, IVariables variables)
      throws SqlExpressionException {
    return compile(specs, inputRowMeta, variables, true);
  }

  public static SqlExpressionProgram compile(
      List<SqlExpressionSpec> specs,
      IRowMeta inputRowMeta,
      IVariables variables,
      boolean keepInputFields)
      throws SqlExpressionException {
    if (inputRowMeta == null) {
      throw new SqlExpressionException("Input row layout is required");
    }
    IRowMeta working = inputRowMeta.clone();
    List<SqlCompiledExpression> compiled = new ArrayList<>();
    if (specs != null) {
      for (SqlExpressionSpec spec : specs) {
        if (spec == null
            || Utils.isEmpty(spec.getExpression())
            || Utils.isEmpty(spec.getFieldName())) {
          continue;
        }
        SqlCompiledExpression expression = SqlExpressionCompiler.compile(spec, working, variables);
        compiled.add(expression);
        int existing = working.indexOfValue(expression.getFieldName());
        if (existing >= 0) {
          working.setValueMeta(existing, expression.getOutputValueMeta().clone());
        } else {
          working.addValueMeta(expression.getOutputValueMeta().clone());
        }
      }
    }
    IRowMeta output;
    if (keepInputFields) {
      output = SqlExpressionEvaluator.outputRowMeta(inputRowMeta, compiled);
    } else {
      output = SqlExpressionEvaluator.outputRowMeta(null, compiled);
    }
    return new SqlExpressionProgram(inputRowMeta.clone(), output, compiled, keepInputFields);
  }

  public Object[] evaluate(Object[] inputRow) throws SqlExpressionException {
    Object[] working = inputRow == null ? new Object[inputRowMeta.size()] : inputRow.clone();
    IRowMeta workingMeta = inputRowMeta.clone();
    for (SqlCompiledExpression expression : expressions) {
      Object value = SqlExpressionEvaluator.evaluate(expression, working);
      int existing = workingMeta.indexOfValue(expression.getFieldName());
      if (existing >= 0) {
        working[existing] = value;
      } else {
        working = RowDataUtil.addValueData(working, workingMeta.size(), value);
        workingMeta.addValueMeta(expression.getOutputValueMeta().clone());
      }
    }
    if (keepInputFields) {
      return alignToOutput(working, workingMeta);
    }
    Object[] output = RowDataUtil.allocateRowData(outputRowMeta.size());
    for (int i = 0; i < expressions.size(); i++) {
      int idx = workingMeta.indexOfValue(expressions.get(i).getFieldName());
      output[i] = idx >= 0 ? working[idx] : null;
    }
    return output;
  }

  private Object[] alignToOutput(Object[] working, IRowMeta workingMeta) {
    Object[] output = RowDataUtil.allocateRowData(outputRowMeta.size());
    for (int i = 0; i < outputRowMeta.size(); i++) {
      String name = outputRowMeta.getValueMeta(i).getName();
      int idx = workingMeta.indexOfValue(name);
      if (idx >= 0 && idx < working.length) {
        output[i] = working[idx];
      }
    }
    return output;
  }
}
