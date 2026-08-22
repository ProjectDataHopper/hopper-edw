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

/** How junk dimension rows are hashed for lookup/indexing. */
@Getter
public enum DmJunkHashCodeStrategy implements IEnumHasCodeAndDescription {
  NONE("NONE", "DmJunkHashCodeStrategy.None"),
  INTEGER_LEGACY("INTEGER_LEGACY", "DmJunkHashCodeStrategy.IntegerLegacy"),
  MD5("MD5", "DmJunkHashCodeStrategy.Md5"),
  SHA1("SHA1", "DmJunkHashCodeStrategy.Sha1"),
  SHA256("SHA256", "DmJunkHashCodeStrategy.Sha256"),
  SHA512("SHA512", "DmJunkHashCodeStrategy.Sha512");

  private final String code;
  private final String descriptionKey;

  DmJunkHashCodeStrategy(String code, String descriptionKey) {
    this.code = code;
    this.descriptionKey = descriptionKey;
  }

  @Override
  public String getDescription() {
    return BaseMessages.getString(DmJunkHashCodeStrategy.class, descriptionKey);
  }

  public boolean usesHashColumn() {
    return this != NONE;
  }

  public boolean usesIntegerHash() {
    return this == INTEGER_LEGACY;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmJunkHashCodeStrategy.class);
  }

  public static DmJunkHashCodeStrategy lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmJunkHashCodeStrategy.class, description, INTEGER_LEGACY);
  }

  public static DmJunkHashCodeStrategy lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmJunkHashCodeStrategy.class, code, INTEGER_LEGACY);
  }
}
