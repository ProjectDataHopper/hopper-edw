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
package org.hopper.edw.datavault.metadata.dimensional;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Descriptive attribute on a dimension with an optional SCD update policy override. */
@Getter
@Setter
@NoArgsConstructor
public class DmDimensionAttribute {

  /** Warehouse / target column name. */
  @HopMetadataProperty private String fieldName;

  /** Source stream column; defaults to {@link #fieldName} when empty. */
  @HopMetadataProperty private String sourceFieldName;

  @HopMetadataProperty(storeWithCode = true)
  private DmScdUpdatePolicy scdUpdatePolicy;

  /**
   * Target column for {@link DmScdUpdatePolicy#TYPE3_PREVIOUS}; defaults to {@code fieldName_prev}.
   */
  @HopMetadataProperty private String previousFieldName;

  public DmDimensionAttribute(String fieldName) {
    this.fieldName = fieldName;
  }

  public DmDimensionAttribute(String fieldName, DmScdUpdatePolicy scdUpdatePolicy) {
    this.fieldName = fieldName;
    this.scdUpdatePolicy = scdUpdatePolicy;
  }
}
