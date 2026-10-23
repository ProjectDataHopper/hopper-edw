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
package org.hopper.edw.datavault.metadata.dimensional.service;

import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** How a dimensional model service treats columns that name a Hop masking pattern. */
public enum DimensionalMaskingPolicy implements IEnumHasCodeAndDescription {
  SAFE(
      "SAFE",
      "Safe — masked columns may only appear as bare select items"),
  DISPLAY(
      "DISPLAY",
      "Display — push the statement down, then mask result cells");

  private final String code;
  private final String description;

  DimensionalMaskingPolicy(String code, String description) {
    this.code = code;
    this.description = description;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public static DimensionalMaskingPolicy lookupCode(String code) {
    return IEnumHasCode.lookupCode(DimensionalMaskingPolicy.class, code, SAFE);
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DimensionalMaskingPolicy.class);
  }
}
