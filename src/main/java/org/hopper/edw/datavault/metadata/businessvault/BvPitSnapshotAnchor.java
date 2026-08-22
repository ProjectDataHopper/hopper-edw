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

/** Where inside each cadence period the snapshot timestamp is placed. */
@Getter
public enum BvPitSnapshotAnchor implements IEnumHasCodeAndDescription {
  END_OF_PERIOD(
      "END_OF_PERIOD",
      BaseMessages.getString(BvPitSnapshotAnchor.class, "BvPitSnapshotAnchor.EndOfPeriod")),
  START_OF_PERIOD(
      "START_OF_PERIOD",
      BaseMessages.getString(BvPitSnapshotAnchor.class, "BvPitSnapshotAnchor.StartOfPeriod"));

  private final String code;
  private final String description;

  BvPitSnapshotAnchor(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvPitSnapshotAnchor.class);
  }

  public static BvPitSnapshotAnchor lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        BvPitSnapshotAnchor.class, description, END_OF_PERIOD);
  }

  public static BvPitSnapshotAnchor lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvPitSnapshotAnchor.class, code, END_OF_PERIOD);
  }
}
