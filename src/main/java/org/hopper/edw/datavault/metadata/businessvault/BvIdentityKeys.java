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

import java.util.Arrays;
import java.util.Objects;

/** Column names and key equality for a Business Vault identity map. */
public final class BvIdentityKeys {

  public static final String HK_RAW = "hk_raw";
  public static final String HK_DURABLE = "hk_durable";
  public static final String PREFERRED_BK = "preferred_bk";
  public static final String HK_MASTER = "hk_master";
  public static final String VALID_FROM = "valid_from";
  public static final String VALID_TO = "valid_to";
  public static final String RECORD_SOURCE = "record_source";
  public static final String RULE_VERSION = "rule_version";

  /** SCD2 copy of {@link #RULE_VERSION}, so a later load can see that the map row changed. */
  public static final String MAP_RULE_VERSION = "map_rule_version";

  private BvIdentityKeys() {}

  public static boolean same(Object left, Object right) {
    if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
      return Arrays.equals(leftBytes, rightBytes);
    }
    return Objects.equals(left, right);
  }
}
