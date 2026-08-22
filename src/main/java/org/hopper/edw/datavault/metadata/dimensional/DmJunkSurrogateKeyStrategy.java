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

/** How a junk dimension table obtains its surrogate/technical key column. */
@Getter
public enum DmJunkSurrogateKeyStrategy implements IEnumHasCodeAndDescription {
  AUTO_INCREMENT(
      "AUTO_INCREMENT",
      BaseMessages.getString(
          DmJunkSurrogateKeyStrategy.class, "DmJunkSurrogateKeyStrategy.AutoIncrement")),
  USE_SOURCE_FIELD(
      "USE_SOURCE_FIELD",
      BaseMessages.getString(
          DmJunkSurrogateKeyStrategy.class, "DmJunkSurrogateKeyStrategy.UseSourceField")),
  COMPUTE_HASH_KEY(
      "COMPUTE_HASH_KEY",
      BaseMessages.getString(
          DmJunkSurrogateKeyStrategy.class, "DmJunkSurrogateKeyStrategy.ComputeHashKey"));

  private final String code;
  private final String description;

  DmJunkSurrogateKeyStrategy(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmJunkSurrogateKeyStrategy.class);
  }

  public static DmJunkSurrogateKeyStrategy lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmJunkSurrogateKeyStrategy.class, description, AUTO_INCREMENT);
  }

  public static DmJunkSurrogateKeyStrategy lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmJunkSurrogateKeyStrategy.class, code, AUTO_INCREMENT);
  }

  public boolean isPipelineSupported() {
    return true;
  }
}
