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

/** How resolved load-strategy values are handed to source SQL. */
@Getter
public enum DmLoadValueBinding implements IEnumHasCodeAndDescription {
  VARIABLES(
      "VARIABLES",
      BaseMessages.getString(DmLoadValueBinding.class, "DmLoadValueBinding.Variables")),
  JDBC_PARAMETERS(
      "JDBC_PARAMETERS",
      BaseMessages.getString(DmLoadValueBinding.class, "DmLoadValueBinding.JdbcParameters")),
  BOTH("BOTH", BaseMessages.getString(DmLoadValueBinding.class, "DmLoadValueBinding.Both"));

  private final String code;
  private final String description;

  DmLoadValueBinding(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public boolean includesVariables() {
    return this == VARIABLES || this == BOTH;
  }

  public boolean includesJdbcParameters() {
    return this == JDBC_PARAMETERS || this == BOTH;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmLoadValueBinding.class);
  }

  public static DmLoadValueBinding lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmLoadValueBinding.class, description, VARIABLES);
  }

  public static DmLoadValueBinding lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmLoadValueBinding.class, code, VARIABLES);
  }
}
