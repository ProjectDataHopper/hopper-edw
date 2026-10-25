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

/** Where an incremental extract reads its lower bound. */
@Getter
public enum DmLoadBaselineKind implements IEnumHasCodeAndDescription {
  TARGET_COLUMN(
      "TARGET_COLUMN",
      BaseMessages.getString(DmLoadBaselineKind.class, "DmLoadBaselineKind.TargetColumn")),
  SENTINEL(
      "SENTINEL", BaseMessages.getString(DmLoadBaselineKind.class, "DmLoadBaselineKind.Sentinel"));

  private final String code;
  private final String description;

  DmLoadBaselineKind(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmLoadBaselineKind.class);
  }

  public static DmLoadBaselineKind lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmLoadBaselineKind.class, description, TARGET_COLUMN);
  }

  public static DmLoadBaselineKind lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmLoadBaselineKind.class, code, TARGET_COLUMN);
  }
}
