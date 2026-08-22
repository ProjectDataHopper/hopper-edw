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
 * How a {@linkplain DvReferenceTable reference table} is loaded into the vault database.
 *
 * <p>{@link #MERGE} is reserved for a later phase; new objects default to {@link #FULL_REPLACE}.
 */
@Getter
public enum DvReferenceLoadMode implements IEnumHasCodeAndDescription {
  FULL_REPLACE(
      "FULL_REPLACE",
      BaseMessages.getString(DvReferenceLoadMode.class, "DvReferenceLoadMode.FullReplace")),
  DELETE_INSERT(
      "DELETE_INSERT",
      BaseMessages.getString(DvReferenceLoadMode.class, "DvReferenceLoadMode.DeleteInsert")),
  MERGE("MERGE", BaseMessages.getString(DvReferenceLoadMode.class, "DvReferenceLoadMode.Merge"));

  private final String code;
  private final String description;

  DvReferenceLoadMode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvReferenceLoadMode.class);
  }

  public static DvReferenceLoadMode lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DvReferenceLoadMode.class, description, FULL_REPLACE);
  }

  public static DvReferenceLoadMode lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvReferenceLoadMode.class, code, FULL_REPLACE);
  }
}
