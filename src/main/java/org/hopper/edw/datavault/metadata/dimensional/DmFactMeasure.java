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

/** Numeric or additive measure column on a fact table. */
@Getter
@Setter
@NoArgsConstructor
public class DmFactMeasure {

  @HopMetadataProperty private String fieldName;

  @HopMetadataProperty private boolean additive = true;

  public DmFactMeasure(String fieldName) {
    this.fieldName = fieldName;
  }

  public DmFactMeasure(String fieldName, boolean additive) {
    this.fieldName = fieldName;
    this.additive = additive;
  }
}
