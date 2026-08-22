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
 * The physical data type used to store hash keys in the Data Vault tables.
 *
 * <p>The actual representation depends on the HashKeyDataType configured on the DvHashKey
 * transform:
 *
 * <ul>
 *   <li>STRING: decimal numbers 0-255 separated by "-" (DvHashKey STRING mode). For MD5: max 63
 *       chars. Larger for SHA*.
 *   <li>HEX: lowercase hexadecimal (DvHashKey HEX mode). MD5=32, SHA256=64, etc.
 *   <li>BINARY: raw bytes (16 for MD5, 32 for SHA256, etc.). Compact for production once Hop sorts
 *       binary values correctly (Apache Hop 2.19.0 or later; see <a
 *       href="https://github.com/apache/hop/issues/7346">issue 7346</a>).
 * </ul>
 *
 * HEX is the plugin default until Hop 2.19 is widely available. STRING and HEX are easier for
 * debugging; BINARY uses less storage but requires a Hop version that sorts binary fields
 * correctly.
 */
@Getter
public enum HashKeyDataType implements IEnumHasCodeAndDescription {
  STRING("STRING", BaseMessages.getString(HashKeyDataType.class, "HashKeyDataType.String")),
  HEX("HEX", BaseMessages.getString(HashKeyDataType.class, "HashKeyDataType.Hex")),
  BINARY("BINARY", BaseMessages.getString(HashKeyDataType.class, "HashKeyDataType.Binary"));

  private final String code;
  private final String description;

  HashKeyDataType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(HashKeyDataType.class);
  }

  public static HashKeyDataType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(HashKeyDataType.class, description, HEX);
  }

  public static HashKeyDataType lookupCode(String code) {
    return IEnumHasCode.lookupCode(HashKeyDataType.class, code, HEX);
  }
}
