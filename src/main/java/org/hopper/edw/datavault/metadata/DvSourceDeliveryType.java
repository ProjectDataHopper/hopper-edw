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
package org.hopper.edw.datavault.metadata;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * Describes what each load from a {@link DataVaultSource} represents.
 *
 * <p>This distinction matters for Status Tracking Satellites and other snapshot-based CDC: deletion
 * detection is only valid when the feed is a complete image of the active source population, not a
 * delta of changes since the last run.
 */
@Getter
public enum DvSourceDeliveryType implements IEnumHasCodeAndDescription {
  CHANGES_ONLY(
      "CHANGES_ONLY",
      BaseMessages.getString(DvSourceDeliveryType.class, "DvSourceDeliveryType.ChangesOnly")),
  FULL_SNAPSHOT(
      "FULL_SNAPSHOT",
      BaseMessages.getString(DvSourceDeliveryType.class, "DvSourceDeliveryType.FullSnapshot"));

  private final String code;
  private final String description;

  DvSourceDeliveryType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvSourceDeliveryType.class);
  }

  public static DvSourceDeliveryType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DvSourceDeliveryType.class, description, CHANGES_ONLY);
  }

  public static DvSourceDeliveryType lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvSourceDeliveryType.class, code, CHANGES_ONLY);
  }
}
