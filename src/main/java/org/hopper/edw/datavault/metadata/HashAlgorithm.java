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
 * Supported hashing algorithms for Data Vault 2.0 hash keys. These are the most commonly used in
 * DV2.0 implementations for generating deterministic surrogate hash keys from business keys.
 */
@Getter
public enum HashAlgorithm implements IEnumHasCodeAndDescription {
  MD5("MD5", BaseMessages.getString(HashAlgorithm.class, "HashAlgorithm.Md5")),
  SHA1("SHA1", BaseMessages.getString(HashAlgorithm.class, "HashAlgorithm.Sha1")),
  SHA256("SHA256", BaseMessages.getString(HashAlgorithm.class, "HashAlgorithm.Sha256")),
  SHA512("SHA512", BaseMessages.getString(HashAlgorithm.class, "HashAlgorithm.Sha512"));

  private final String code;
  private final String description;

  HashAlgorithm(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /** Returns the length in bytes of the raw digest produced by this algorithm. */
  public int getDigestLength() {
    return switch (this) {
      case MD5 -> 16;
      case SHA1 -> 20;
      case SHA256 -> 32;
      case SHA512 -> 64;
    };
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(HashAlgorithm.class);
  }

  public static HashAlgorithm lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(HashAlgorithm.class, description, MD5);
  }

  public static HashAlgorithm lookupCode(String code) {
    return IEnumHasCode.lookupCode(HashAlgorithm.class, code, MD5);
  }
}
