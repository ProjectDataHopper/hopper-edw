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

/**
 * How a SQL-sourced Business Vault business table is persisted in the target database.
 *
 * <ul>
 *   <li>{@link #VIEW} — {@code CREATE OR REPLACE VIEW t AS query}
 *   <li>{@link #TABLE} — {@code CREATE OR REPLACE TABLE t AS query}
 * </ul>
 */
@Getter
public enum BvSqlMaterialization implements IEnumHasCodeAndDescription {
  VIEW("VIEW", BaseMessages.getString(BvSqlMaterialization.class, "BvSqlMaterialization.View")),
  TABLE("TABLE", BaseMessages.getString(BvSqlMaterialization.class, "BvSqlMaterialization.Table"));

  private final String code;
  private final String description;

  BvSqlMaterialization(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvSqlMaterialization.class);
  }

  public static BvSqlMaterialization lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        BvSqlMaterialization.class, description, VIEW);
  }

  public static BvSqlMaterialization lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvSqlMaterialization.class, code, VIEW);
  }
}
