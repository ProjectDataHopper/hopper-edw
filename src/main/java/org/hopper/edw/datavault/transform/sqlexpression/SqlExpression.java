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
package org.hopper.edw.datavault.transform.sqlexpression;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.expression.SqlExpressionProgram;

/** Evaluates deterministic SQL scalar expressions per input row. */
public class SqlExpression extends BaseTransform<SqlExpressionMeta, SqlExpressionData> {

  private static final Class<?> PKG = SqlExpressionMeta.class;

  public SqlExpression(
      TransformMeta transformMeta,
      SqlExpressionMeta meta,
      SqlExpressionData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;
      data.program =
          SqlExpressionProgram.compile(
              meta.resolveSpecs(this, getMetadataProvider()),
              getInputRowMeta(),
              this,
              meta.isKeepInputFields());
      data.outputRowMeta = data.program.getOutputRowMeta();
    }

    try {
      Object[] output = data.program.evaluate(row);
      putRow(data.outputRowMeta, output);
    } catch (Exception e) {
      if (getTransformMeta().isDoingErrorHandling()) {
        putError(getInputRowMeta(), row, 1L, e.getMessage(), null, "SQLEXPR001");
      } else {
        throw new HopException(
            BaseMessages.getString(PKG, "SqlExpression.Error.Evaluating", e.getMessage()), e);
      }
    }

    if (checkFeedback(getLinesRead()) && isBasic()) {
      logBasic(BaseMessages.getString(PKG, "SqlExpression.Log.LineNumber", getLinesRead()));
    }
    return true;
  }
}
