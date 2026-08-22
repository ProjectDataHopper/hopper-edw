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
package org.apache.hop.datavault.metadata;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * How to normalize (case) the business key content before hashing. This ensures consistent hash
 * keys regardless of source case variations.
 */
@Getter
public enum HashContentCasing implements IEnumHasCodeAndDescription {
  UPPER("UPPER", BaseMessages.getString(HashContentCasing.class, "HashContentCasing.Upper")),
  LOWER("LOWER", BaseMessages.getString(HashContentCasing.class, "HashContentCasing.Lower")),
  NONE("NONE", BaseMessages.getString(HashContentCasing.class, "HashContentCasing.None"));

  private final String code;
  private final String description;

  HashContentCasing(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(HashContentCasing.class);
  }

  public static HashContentCasing lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        HashContentCasing.class, description, UPPER);
  }

  public static HashContentCasing lookupCode(String code) {
    return IEnumHasCode.lookupCode(HashContentCasing.class, code, UPPER);
  }
}
