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
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * How a pure Type 1 dimension writes rows that already match the natural key. Attribute policies
 * stay Type 1; this only chooses the generated load pipeline.
 */
@Getter
public enum DmType1UpdateMode implements IEnumHasCodeAndDescription {
  OVERWRITE(
      "OVERWRITE", BaseMessages.getString(DmType1UpdateMode.class, "DmType1UpdateMode.Overwrite")),
  SKIP_UNCHANGED(
      "SKIP_UNCHANGED",
      BaseMessages.getString(DmType1UpdateMode.class, "DmType1UpdateMode.SkipUnchanged"));

  private final String code;
  private final String description;

  DmType1UpdateMode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmType1UpdateMode.class);
  }

  public static DmType1UpdateMode lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmType1UpdateMode.class, description, OVERWRITE);
  }

  public static DmType1UpdateMode lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmType1UpdateMode.class, code, OVERWRITE);
  }
}
