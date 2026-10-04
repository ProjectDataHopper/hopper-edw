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
package org.hopper.edw.datavault.metadata.sourcemodel;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Kind of parent a {@link SourceMasking} card reads before Mask fields runs. */
@Getter
public enum SourceMaskingParentKind implements IEnumHasCodeAndDescription {
  TABLE(
      "TABLE", BaseMessages.getString(SourceMaskingParentKind.class, "SourceMaskingParentKind.Table")),
  QUERY(
      "QUERY", BaseMessages.getString(SourceMaskingParentKind.class, "SourceMaskingParentKind.Query")),
  JSON(
      "JSON", BaseMessages.getString(SourceMaskingParentKind.class, "SourceMaskingParentKind.Json")),
  PIPELINE(
      "PIPELINE",
      BaseMessages.getString(SourceMaskingParentKind.class, "SourceMaskingParentKind.Pipeline")),
  MASKING(
      "MASKING",
      BaseMessages.getString(SourceMaskingParentKind.class, "SourceMaskingParentKind.Masking"));

  private final String code;
  private final String description;

  SourceMaskingParentKind(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(SourceMaskingParentKind.class);
  }

  public static SourceMaskingParentKind lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        SourceMaskingParentKind.class, description, TABLE);
  }

  public static SourceMaskingParentKind lookupCode(String code) {
    return IEnumHasCode.lookupCode(SourceMaskingParentKind.class, code, TABLE);
  }
}
