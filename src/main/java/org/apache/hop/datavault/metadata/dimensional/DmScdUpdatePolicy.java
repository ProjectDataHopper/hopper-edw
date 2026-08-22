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

/** How a dimension attribute is maintained in the warehouse (routes pipeline builders). */
@Getter
public enum DmScdUpdatePolicy implements IEnumHasCodeAndDescription {
  TYPE1("TYPE1", BaseMessages.getString(DmScdUpdatePolicy.class, "DmScdUpdatePolicy.Type1")),
  TYPE1_PUNCH_THROUGH(
      "TYPE1_PUNCH_THROUGH",
      BaseMessages.getString(DmScdUpdatePolicy.class, "DmScdUpdatePolicy.Type1PunchThrough")),
  TYPE2("TYPE2", BaseMessages.getString(DmScdUpdatePolicy.class, "DmScdUpdatePolicy.Type2")),
  TYPE3_CURRENT(
      "TYPE3_CURRENT",
      BaseMessages.getString(DmScdUpdatePolicy.class, "DmScdUpdatePolicy.Type3Current")),
  TYPE3_PREVIOUS(
      "TYPE3_PREVIOUS",
      BaseMessages.getString(DmScdUpdatePolicy.class, "DmScdUpdatePolicy.Type3Previous"));

  private final String code;
  private final String description;

  DmScdUpdatePolicy(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmScdUpdatePolicy.class);
  }

  public static DmScdUpdatePolicy lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmScdUpdatePolicy.class, description, TYPE1);
  }

  public static DmScdUpdatePolicy lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmScdUpdatePolicy.class, code, TYPE1);
  }
}
