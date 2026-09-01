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

import java.util.List;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;

/** Builds {@link SqlExpressionMeta} for generated pipelines. */
public final class SqlExpressionMetaFactory {

  private SqlExpressionMetaFactory() {}

  public static SqlExpressionMeta create(List<SqlExpressionSpec> specs) {
    SqlExpressionMeta meta = new SqlExpressionMeta();
    meta.setKeepInputFields(true);
    if (specs == null) {
      return meta;
    }
    for (SqlExpressionSpec spec : specs) {
      if (spec == null
          || Utils.isEmpty(spec.getFieldName())
          || Utils.isEmpty(spec.getExpression())) {
        continue;
      }
      SqlExpressionField field = new SqlExpressionField(spec.getFieldName(), spec.getExpression());
      field.setHopTypeName(spec.getHopTypeName());
      field.setLength(spec.getLength());
      field.setPrecision(spec.getPrecision());
      meta.getFields().add(field);
    }
    return meta;
  }

  /**
   * Unit-test / capture pipelines load calculations from the Business Vault SCD2 table at runtime.
   * Production SCD2 generate still copies expressions via {@link #create(List)}.
   */
  public static SqlExpressionMeta createFromBvTable(
      String businessVaultModelFilename, String scd2TableName) {
    SqlExpressionMeta meta = new SqlExpressionMeta();
    meta.setKeepInputFields(true);
    meta.setBusinessVaultModelFilename(businessVaultModelFilename);
    meta.setScd2TableName(scd2TableName);
    return meta;
  }
}
