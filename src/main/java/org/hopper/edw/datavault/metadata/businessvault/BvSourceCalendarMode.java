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

/**
 * How a source speaks inside a calendar window. Phase 1 uses the window itself as the filter;
 * mode is stored for later survivorship (delta defaults to inherit-null, seed fills gaps).
 */
public enum BvSourceCalendarMode implements IEnumHasCode {
  FULL("full"),
  DELTA("delta"),
  SEED("seed");

  private final String code;

  BvSourceCalendarMode(String code) {
    this.code = code;
  }

  @Override
  public String getCode() {
    return code;
  }

  /** Blank is unset. An unknown code is {@code null} so Check model can reject it. */
  public static BvSourceCalendarMode lookupCode(String code) {
    if (Utils.isEmpty(code)) {
      return null;
    }
    String trimmed = code.trim();
    for (BvSourceCalendarMode mode : values()) {
      if (mode.code.equalsIgnoreCase(trimmed)) {
        return mode;
      }
    }
    return null;
  }
}
