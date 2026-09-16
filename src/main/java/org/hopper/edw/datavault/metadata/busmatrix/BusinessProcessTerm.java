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
package org.hopper.edw.datavault.metadata.busmatrix;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Named term in a business process catalog (domain or process level). */
@Getter
@Setter
@NoArgsConstructor
public class BusinessProcessTerm {

  @HopMetadataProperty private String name;

  @HopMetadataProperty private String description;

  /** Parent name: domain for L1, L1 for L2, L2 for L3. Empty on domains. */
  @HopMetadataProperty private String parentName;

  public BusinessProcessTerm(String name, String description, String parentName) {
    this.name = name;
    this.description = description;
    this.parentName = parentName;
  }
}
