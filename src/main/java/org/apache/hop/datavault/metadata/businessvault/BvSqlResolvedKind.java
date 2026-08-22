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
package org.apache.hop.datavault.metadata.businessvault;

import lombok.Getter;
import org.apache.hop.metadata.api.IEnumHasCode;

/** Kind of object a SQL {@code ref()} resolved to. */
@Getter
public enum BvSqlResolvedKind implements IEnumHasCode {
  BV_TABLE("BV_TABLE"),
  DV_TABLE("DV_TABLE"),
  UNKNOWN("UNKNOWN");

  private final String code;

  BvSqlResolvedKind(String code) {
    this.code = code;
  }

  public static BvSqlResolvedKind lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvSqlResolvedKind.class, code, UNKNOWN);
  }
}
