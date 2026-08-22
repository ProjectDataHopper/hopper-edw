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
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;

/** How a dimension table obtains its warehouse surrogate/technical key column. */
@Getter
public enum DmSurrogateKeyStrategy implements IEnumHasCodeAndDescription {
  NONE("NONE", BaseMessages.getString(DmSurrogateKeyStrategy.class, "DmSurrogateKeyStrategy.None")),
  AUTO_INCREMENT(
      "AUTO_INCREMENT",
      BaseMessages.getString(DmSurrogateKeyStrategy.class, "DmSurrogateKeyStrategy.AutoIncrement")),
  USE_SOURCE_FIELD(
      "USE_SOURCE_FIELD",
      BaseMessages.getString(
          DmSurrogateKeyStrategy.class, "DmSurrogateKeyStrategy.UseSourceField")),
  SEQUENCE(
      "SEQUENCE",
      BaseMessages.getString(DmSurrogateKeyStrategy.class, "DmSurrogateKeyStrategy.Sequence")),
  TABLE_MAXIMUM(
      "TABLE_MAXIMUM",
      BaseMessages.getString(DmSurrogateKeyStrategy.class, "DmSurrogateKeyStrategy.TableMaximum"));

  private final String code;
  private final String description;

  DmSurrogateKeyStrategy(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmSurrogateKeyStrategy.class);
  }

  public static DmSurrogateKeyStrategy lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmSurrogateKeyStrategy.class, description, AUTO_INCREMENT);
  }

  public static DmSurrogateKeyStrategy lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmSurrogateKeyStrategy.class, code, null);
  }

  public DimensionLookupMeta.TechnicalKeyCreationMethod toDimensionLookupCreationMethod() {
    return switch (this) {
      case AUTO_INCREMENT -> DimensionLookupMeta.TechnicalKeyCreationMethod.AUTO_INCREMENT;
      case USE_SOURCE_FIELD -> DimensionLookupMeta.TechnicalKeyCreationMethod.FIELD;
      case SEQUENCE -> DimensionLookupMeta.TechnicalKeyCreationMethod.SEQUENCE;
      case TABLE_MAXIMUM -> DimensionLookupMeta.TechnicalKeyCreationMethod.TABLE_MAXIMUM;
      case NONE -> DimensionLookupMeta.TechnicalKeyCreationMethod.AUTO_INCREMENT;
    };
  }
}
