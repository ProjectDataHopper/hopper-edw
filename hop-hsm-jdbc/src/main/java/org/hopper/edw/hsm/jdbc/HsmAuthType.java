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
package org.hopper.edw.hsm.jdbc;

/** How the thin hop-hsm client authenticates to Hop Server / Hop Web. */
public enum HsmAuthType {
  BASIC,
  BEARER,
  OAUTH2;

  static HsmAuthType parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return BASIC;
    }
    String n = raw.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace("-", "");
    return switch (n) {
      case "bearer", "token" -> BEARER;
      case "oauth2", "oauth", "openid" -> OAUTH2;
      default -> BASIC;
    };
  }

  String wireName() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
