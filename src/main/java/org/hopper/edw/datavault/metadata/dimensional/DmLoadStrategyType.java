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

/** How much of the source a dimensional table reads on each load. */
@Getter
public enum DmLoadStrategyType implements IEnumHasCodeAndDescription {
  INITIAL_RELOAD(
      "INITIAL_RELOAD",
      BaseMessages.getString(DmLoadStrategyType.class, "DmLoadStrategyType.InitialReload")),
  INITIAL_LOAD(
      "INITIAL_LOAD",
      BaseMessages.getString(DmLoadStrategyType.class, "DmLoadStrategyType.InitialLoad")),
  INCREMENTAL(
      "INCREMENTAL",
      BaseMessages.getString(DmLoadStrategyType.class, "DmLoadStrategyType.Incremental")),
  INCREMENTAL_LATE_ARRIVING(
      "INCREMENTAL_LATE_ARRIVING",
      BaseMessages.getString(DmLoadStrategyType.class, "DmLoadStrategyType.IncrementalLateArriving"));

  private final String code;
  private final String description;

  DmLoadStrategyType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public boolean isExecutable() {
    return this == INITIAL_RELOAD || this == INCREMENTAL;
  }

  public boolean limitsExtract() {
    return this == INCREMENTAL || this == INCREMENTAL_LATE_ARRIVING;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmLoadStrategyType.class);
  }

  public static DmLoadStrategyType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DmLoadStrategyType.class, description, INITIAL_RELOAD);
  }

  public static DmLoadStrategyType lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmLoadStrategyType.class, code, INITIAL_RELOAD);
  }
}
