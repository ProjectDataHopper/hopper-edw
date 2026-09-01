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
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Engine-facing calculation: output name, SQL scalar, optional Hop type override. */
@Getter
@Setter
@NoArgsConstructor
public class SqlExpressionSpec {

  private String fieldName;
  private String expression;
  private String hopTypeName;
  private int length = -1;
  private int precision = -1;

  public SqlExpressionSpec(String fieldName, String expression) {
    this.fieldName = fieldName;
    this.expression = expression;
  }

  public SqlExpressionSpec(
      String fieldName, String expression, String hopTypeName, int length, int precision) {
    this.fieldName = fieldName;
    this.expression = expression;
    this.hopTypeName = hopTypeName;
    this.length = length;
    this.precision = precision;
  }

  public SqlExpressionSpec(SqlExpressionSpec other) {
    if (other == null) {
      return;
    }
    this.fieldName = other.fieldName;
    this.expression = other.expression;
    this.hopTypeName = other.hopTypeName;
    this.length = other.length;
    this.precision = other.precision;
  }
}
