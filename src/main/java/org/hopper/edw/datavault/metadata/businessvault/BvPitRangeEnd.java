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

/** How the PIT snapshot spine end date is resolved. */
@Getter
public enum BvPitRangeEnd implements IEnumHasCodeAndDescription {
  NOW("NOW", BaseMessages.getString(BvPitRangeEnd.class, "BvPitRangeEnd.Now")),
  FIXED_DATE("FIXED_DATE", BaseMessages.getString(BvPitRangeEnd.class, "BvPitRangeEnd.FixedDate")),
  NOW_MINUS_HORIZON(
      "NOW_MINUS_HORIZON",
      BaseMessages.getString(BvPitRangeEnd.class, "BvPitRangeEnd.NowMinusHorizon"));

  private final String code;
  private final String description;

  BvPitRangeEnd(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvPitRangeEnd.class);
  }

  public static BvPitRangeEnd lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        BvPitRangeEnd.class, description, NOW_MINUS_HORIZON);
  }

  public static BvPitRangeEnd lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvPitRangeEnd.class, code, NOW_MINUS_HORIZON);
  }
}
