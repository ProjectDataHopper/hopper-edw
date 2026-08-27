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
package org.hopper.edw.datavault.executionmap;

import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.hopper.edw.datavault.config.DataVaultConfigSingleton;

/** Edge drawing style for execution map graphs and SVG export. */
public enum ExecutionMapLineStyle implements IEnumHasCodeAndDescription {
  DIRECT_CENTER(
      "DIRECT_CENTER",
      BaseMessages.getString(ExecutionMapLineStyle.class, "ExecutionMapLineStyle.DirectCenter")),
  SPLINE(
      "SPLINE",
      BaseMessages.getString(ExecutionMapLineStyle.class, "ExecutionMapLineStyle.Spline")),
  ORTHOGONAL(
      "ORTHOGONAL",
      BaseMessages.getString(ExecutionMapLineStyle.class, "ExecutionMapLineStyle.Orthogonal"));

  private final String code;
  private final String description;

  ExecutionMapLineStyle(String code, String description) {
    this.code = code;
    this.description = description;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(ExecutionMapLineStyle.class);
  }

  public static ExecutionMapLineStyle lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        ExecutionMapLineStyle.class, description, DIRECT_CENTER);
  }

  public static ExecutionMapLineStyle lookupCode(String code) {
    return IEnumHasCode.lookupCode(ExecutionMapLineStyle.class, code, DIRECT_CENTER);
  }

  /** Returns the style configured in Hop plugin settings. */
  public static ExecutionMapLineStyle configured() {
    return DataVaultConfigSingleton.getConfig().getExecutionMapLineStyleOrDefault();
  }
}
