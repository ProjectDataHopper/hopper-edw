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
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Calendar cadence for PIT snapshot spine generation. */
@Getter
public enum BvPitCadence implements IEnumHasCodeAndDescription {
  DAILY("DAILY", BaseMessages.getString(BvPitCadence.class, "BvPitCadence.Daily")),
  WEEKLY("WEEKLY", BaseMessages.getString(BvPitCadence.class, "BvPitCadence.Weekly")),
  MONTHLY("MONTHLY", BaseMessages.getString(BvPitCadence.class, "BvPitCadence.Monthly"));

  private final String code;
  private final String description;

  BvPitCadence(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvPitCadence.class);
  }

  public static BvPitCadence lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(BvPitCadence.class, description, DAILY);
  }

  public static BvPitCadence lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvPitCadence.class, code, DAILY);
  }
}
