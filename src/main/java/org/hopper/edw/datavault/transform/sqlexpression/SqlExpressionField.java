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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.edw.datavault.expression.SqlExpressionDraft;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;

@Getter
@Setter
@NoArgsConstructor
public class SqlExpressionField {

  @HopMetadataProperty(
      key = "field_name",
      injectionKey = "FIELD_NAME",
      injectionKeyDescription = "SqlExpressionMeta.Injection.FIELD_NAME")
  private String fieldName;

  @HopMetadataProperty(
      key = "expression",
      injectionKey = "EXPRESSION",
      injectionKeyDescription = "SqlExpressionMeta.Injection.EXPRESSION")
  private String expression;

  @HopMetadataProperty(
      key = "hop_type",
      injectionKey = "HOP_TYPE",
      injectionKeyDescription = "SqlExpressionMeta.Injection.HOP_TYPE")
  private String hopTypeName;

  @HopMetadataProperty(
      key = "length",
      injectionKey = "LENGTH",
      injectionKeyDescription = "SqlExpressionMeta.Injection.LENGTH")
  private int length = -1;

  @HopMetadataProperty(
      key = "precision",
      injectionKey = "PRECISION",
      injectionKeyDescription = "SqlExpressionMeta.Injection.PRECISION")
  private int precision = -1;

  @HopMetadataProperty(
      key = "description",
      injectionKey = "DESCRIPTION",
      injectionKeyDescription = "SqlExpressionMeta.Injection.DESCRIPTION")
  private String description;

  public SqlExpressionField(String fieldName, String expression) {
    this.fieldName = fieldName;
    this.expression = expression;
  }

  public SqlExpressionField(SqlExpressionField other) {
    if (other == null) {
      return;
    }
    this.fieldName = other.fieldName;
    this.expression = other.expression;
    this.hopTypeName = other.hopTypeName;
    this.length = other.length;
    this.precision = other.precision;
    this.description = other.description;
  }

  public SqlExpressionDraft toDraft() {
    SqlExpressionDraft draft = new SqlExpressionDraft();
    draft.setFieldName(fieldName);
    draft.setExpression(expression);
    draft.setHopTypeName(hopTypeName);
    draft.setLength(length);
    draft.setPrecision(precision);
    draft.setDescription(description);
    return draft;
  }

  public void fromDraft(SqlExpressionDraft draft) {
    if (draft == null) {
      return;
    }
    this.fieldName = draft.getFieldName();
    this.expression = draft.getExpression();
    this.hopTypeName = draft.getHopTypeName();
    this.length = draft.getLength();
    this.precision = draft.getPrecision();
    this.description = draft.getDescription();
  }

  public SqlExpressionSpec toSpec() {
    return new SqlExpressionSpec(fieldName, expression, hopTypeName, length, precision);
  }
}
