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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DvIdentifierLimitSupportTest {

  @Test
  void pluginIdLimits() {
    assertEquals(63, DvIdentifierLimitSupport.maxColumnNameLength("POSTGRESQL"));
    assertEquals(64, DvIdentifierLimitSupport.maxColumnNameLength("MYSQL"));
    assertEquals(64, DvIdentifierLimitSupport.maxColumnNameLength("SINGLESTORE"));
    assertEquals(128, DvIdentifierLimitSupport.maxColumnNameLength("MSSQLNATIVE"));
    assertEquals(255, DvIdentifierLimitSupport.maxColumnNameLength("SNOWFLAKE"));
    assertEquals(63, DvIdentifierLimitSupport.maxColumnNameLength((String) null));
  }

  @Test
  void uniqueIdentifierTruncatesAndSuffixes() {
    String longName = "n".repeat(80);
    Set<String> used = new HashSet<>();
    String first = DvIdentifierLimitSupport.uniqueIdentifier(longName, 63, used);
    assertEquals(63, first.length());
    used.add(first);
    String second = DvIdentifierLimitSupport.uniqueIdentifier(longName, 63, used);
    assertEquals(63, second.length());
    assertTrue(second.endsWith("2"));
    used.add(second);
    String third = DvIdentifierLimitSupport.uniqueIdentifier(longName, 63, used);
    assertEquals(63, third.length());
    assertTrue(third.endsWith("3"));
  }

  @Test
  void uniqueIdentifierKeepsShortUnusedName() {
    assertEquals(
        "customer_name",
        DvIdentifierLimitSupport.uniqueIdentifier("customer_name", 63, Set.of("other")));
  }
}
