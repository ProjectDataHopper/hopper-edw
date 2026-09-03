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

import lombok.Getter;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexNode;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;

/** A parsed, allow-listed SQL scalar ready to evaluate against a Hop row. */
@Getter
public final class SqlCompiledExpression {

  private final String fieldName;
  private final String rewrittenSql;
  private final RexNode rexNode;
  private final RelDataType relType;
  private final IRowMeta inputRowMeta;
  private final IValueMeta[] inputValueMetas;
  private final IValueMeta outputValueMeta;

  public SqlCompiledExpression(
      String fieldName,
      String rewrittenSql,
      RexNode rexNode,
      RelDataType relType,
      IRowMeta inputRowMeta,
      IValueMeta outputValueMeta) {
    this.fieldName = fieldName;
    this.rewrittenSql = rewrittenSql;
    this.rexNode = rexNode;
    this.relType = relType;
    this.inputRowMeta = inputRowMeta;
    this.inputValueMetas = snapshot(inputRowMeta);
    this.outputValueMeta = outputValueMeta;
  }

  private static IValueMeta[] snapshot(IRowMeta rowMeta) {
    if (rowMeta == null) {
      return new IValueMeta[0];
    }
    IValueMeta[] metas = new IValueMeta[rowMeta.size()];
    for (int i = 0; i < metas.length; i++) {
      metas[i] = rowMeta.getValueMeta(i);
    }
    return metas;
  }
}
