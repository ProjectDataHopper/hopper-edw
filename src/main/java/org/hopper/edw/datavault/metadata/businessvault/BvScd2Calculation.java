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
package org.hopper.edw.datavault.metadata.businessvault;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;

/** Deterministic SQL scalar applied after SCD2 collapse, before the Business Vault write. */
@Getter
@Setter
@NoArgsConstructor
public class BvScd2Calculation {

  @HopMetadataProperty private String targetFieldName;

  @HopMetadataProperty private String expression;

  @HopMetadataProperty private String hopTypeName;

  @HopMetadataProperty private int length = -1;

  @HopMetadataProperty private int precision = -1;

  @HopMetadataProperty private String description;

  public BvScd2Calculation(String targetFieldName, String expression) {
    this.targetFieldName = targetFieldName;
    this.expression = expression;
  }

  public BvScd2Calculation(BvScd2Calculation other) {
    if (other == null) {
      return;
    }
    this.targetFieldName = other.targetFieldName;
    this.expression = other.expression;
    this.hopTypeName = other.hopTypeName;
    this.length = other.length;
    this.precision = other.precision;
    this.description = other.description;
  }

  public SqlExpressionSpec toSpec() {
    return new SqlExpressionSpec(targetFieldName, expression, hopTypeName, length, precision);
  }
}
