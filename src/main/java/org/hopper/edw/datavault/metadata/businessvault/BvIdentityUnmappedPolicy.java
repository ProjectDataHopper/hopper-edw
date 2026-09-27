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

import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IEnumHasCode;

/** What an SCD2 row does when no identity-map interval covers its raw key at that time. */
public enum BvIdentityUnmappedPolicy implements IEnumHasCode {
  SELF("self"),
  QUARANTINE("quarantine"),
  DROP("drop");

  private final String code;

  BvIdentityUnmappedPolicy(String code) {
    this.code = code;
  }

  @Override
  public String getCode() {
    return code;
  }

  public static BvIdentityUnmappedPolicy lookupCode(String code) {
    if (Utils.isEmpty(code)) {
      return null;
    }
    String trimmed = code.trim();
    for (BvIdentityUnmappedPolicy policy : values()) {
      if (policy.code.equalsIgnoreCase(trimmed)) {
        return policy;
      }
    }
    return null;
  }
}
