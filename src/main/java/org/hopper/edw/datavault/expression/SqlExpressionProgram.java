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
  private final int workingRowSize;
  private final int[] expressionWriteIndexes;

  private SqlExpressionProgram(
      IRowMeta inputRowMeta,
      IRowMeta outputRowMeta,
      List<SqlCompiledExpression> expressions,
      boolean keepInputFields,
      int workingRowSize,
      int[] expressionWriteIndexes) {
    this.inputRowMeta = inputRowMeta;
    this.outputRowMeta = outputRowMeta;
    this.expressions = expressions;
    this.keepInputFields = keepInputFields;
    this.workingRowSize = workingRowSize;
    this.expressionWriteIndexes = expressionWriteIndexes;
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
    List<Integer> writeIndexes = new ArrayList<>();
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
          writeIndexes.add(existing);
          working.setValueMeta(existing, expression.getOutputValueMeta().clone());
        } else {
          writeIndexes.add(working.size());
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
    int[] expressionWriteIndexes = new int[writeIndexes.size()];
    for (int i = 0; i < writeIndexes.size(); i++) {
      expressionWriteIndexes[i] = writeIndexes.get(i);
    }
    return new SqlExpressionProgram(
        inputRowMeta.clone(),
        output,
        compiled,
        keepInputFields,
        working.size(),
        expressionWriteIndexes);
  }

  public Object[] evaluate(Object[] inputRow) throws SqlExpressionException {
    Object[] working = RowDataUtil.resizeArray(inputRow, workingRowSize);
    for (int i = 0; i < expressions.size(); i++) {
      working[expressionWriteIndexes[i]] =
          SqlExpressionEvaluator.evaluate(expressions.get(i), working);
    }
    if (keepInputFields) {
      return working;
    }
    Object[] output = RowDataUtil.allocateRowData(outputRowMeta.size());
    for (int i = 0; i < expressions.size(); i++) {
      output[i] = working[expressionWriteIndexes[i]];
    }
    return output;
  }
}
