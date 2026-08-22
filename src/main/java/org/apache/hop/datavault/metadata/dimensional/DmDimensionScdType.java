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
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Default SCD strategy for a dimension table. */
@Getter
public enum DmDimensionScdType implements IEnumHasCodeAndDescription {
  TYPE1("TYPE1", BaseMessages.getString(DmDimensionScdType.class, "DmDimensionScdType.Type1")),
  TYPE2("TYPE2", BaseMessages.getString(DmDimensionScdType.class, "DmDimensionScdType.Type2")),
  TYPE3("TYPE3", BaseMessages.getString(DmDimensionScdType.class, "DmDimensionScdType.Type3")),
  /**
   * Derived label when attribute policies mix Type 1 and Type 2 (or punch-through). Not stored on
   * dimensions.
   */
  HYBRID("HYBRID", BaseMessages.getString(DmDimensionScdType.class, "DmDimensionScdType.Hybrid"));

  private final String code;
  private final String description;

  DmDimensionScdType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmDimensionScdType.class);
  }

  public static DmDimensionScdType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmDimensionScdType.class, description, TYPE1);
  }

  public static DmDimensionScdType lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmDimensionScdType.class, code, TYPE1);
  }
}
