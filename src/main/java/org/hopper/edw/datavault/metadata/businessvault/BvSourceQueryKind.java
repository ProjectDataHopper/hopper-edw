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
package org.hopper.edw.datavault.metadata.businessvault;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** How a Business Vault source query reads satellite-shaped history. */
@Getter
public enum BvSourceQueryKind implements IEnumHasCodeAndDescription {
  TABLE("TABLE", BaseMessages.getString(BvSourceQueryKind.class, "BvSourceQueryKind.Table")),
  SQL("SQL", BaseMessages.getString(BvSourceQueryKind.class, "BvSourceQueryKind.Sql"));

  private final String code;
  private final String description;

  BvSourceQueryKind(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvSourceQueryKind.class);
  }

  public static BvSourceQueryKind lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        BvSourceQueryKind.class, description, TABLE);
  }

  public static BvSourceQueryKind lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvSourceQueryKind.class, code, TABLE);
  }
}
