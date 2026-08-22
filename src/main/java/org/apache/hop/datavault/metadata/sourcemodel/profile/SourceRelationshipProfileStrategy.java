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
package org.apache.hop.datavault.metadata.sourcemodel.profile;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** How to profile a source relationship for multiplicity. */
@Getter
public enum SourceRelationshipProfileStrategy implements IEnumHasCodeAndDescription {
  EXACT_KEY(
      "EXACT_KEY",
      BaseMessages.getString(
          SourceRelationshipProfileStrategy.class, "SourceRelationshipProfileStrategy.ExactKey")),
  SAMPLED_KEY(
      "SAMPLED_KEY",
      BaseMessages.getString(
          SourceRelationshipProfileStrategy.class, "SourceRelationshipProfileStrategy.SampledKey")),
  STATS_ONLY(
      "STATS_ONLY",
      BaseMessages.getString(
          SourceRelationshipProfileStrategy.class, "SourceRelationshipProfileStrategy.StatsOnly")),
  FULL_OUTER(
      "FULL_OUTER",
      BaseMessages.getString(
          SourceRelationshipProfileStrategy.class, "SourceRelationshipProfileStrategy.FullOuter"));

  private final String code;
  private final String description;

  SourceRelationshipProfileStrategy(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(SourceRelationshipProfileStrategy.class);
  }

  public static SourceRelationshipProfileStrategy lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        SourceRelationshipProfileStrategy.class, description, EXACT_KEY);
  }

  public static SourceRelationshipProfileStrategy lookupCode(String code) {
    return IEnumHasCode.lookupCode(SourceRelationshipProfileStrategy.class, code, EXACT_KEY);
  }
}
