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
package org.apache.hop.datavault.metadata.dimensional;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Fact-to-range-dimension role: bucket a numeric source field into a band label column. */
@Getter
@Setter
@NoArgsConstructor
public class DmFactRangeDimensionRole {

  @HopMetadataProperty private String rangeDimensionTableName;

  @HopMetadataProperty private String sourceFieldName;

  @HopMetadataProperty private String targetFieldName;

  public DmFactRangeDimensionRole(
      String rangeDimensionTableName, String sourceFieldName, String targetFieldName) {
    this.rangeDimensionTableName = rangeDimensionTableName;
    this.sourceFieldName = sourceFieldName;
    this.targetFieldName = targetFieldName;
  }
}
