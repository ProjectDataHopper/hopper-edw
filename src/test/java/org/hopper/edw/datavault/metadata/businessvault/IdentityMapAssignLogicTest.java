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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityMapAssignLogicTest {

  private static final Timestamp OPEN = Timestamp.valueOf("9999-12-31 23:59:59");
  private static final Timestamp DAY1 = Timestamp.valueOf("2020-01-01 00:00:00");
  private static final Timestamp DAY2 = Timestamp.valueOf("2024-03-01 00:00:00");

  @Test
  void twoRawKeysShareOneDurableKeyAndALaterPreferredKeyDoesNotChangeIt() {
    IdentityMapAssignLogic.Edge edge =
        new IdentityMapAssignLogic.Edge("raw-master", "raw-dup", "ORACLE-1", null, DAY1, null);
    List<IdentityMapAssignLogic.Assignment> first =
        IdentityMapAssignLogic.assign(List.of(), List.of(edge), value -> "D:" + value, OPEN);

    assertEquals(2, first.size());
    IdentityMapAssignLogic.Assignment master = assignment(first, "raw-master");
    IdentityMapAssignLogic.Assignment duplicate = assignment(first, "raw-dup");
    assertEquals("D:raw-master", master.durable);
    assertEquals(master.durable, duplicate.durable);
    assertEquals("ORACLE-1", master.preferredBk);
    assertFalse(master.update);

    IdentityMapAssignLogic.Edge later =
        new IdentityMapAssignLogic.Edge("raw-master", "raw-dup", "ORACLE-2", null, DAY2, null);
    List<IdentityMapAssignLogic.Assignment> second =
        IdentityMapAssignLogic.assign(asExisting(first), List.of(later), value -> "CHANGED", OPEN);

    assertEquals(2, second.size());
    assertTrue(second.stream().allMatch(row -> row.update));
    assertEquals("D:raw-master", assignment(second, "raw-master").durable);
    assertEquals("D:raw-master", assignment(second, "raw-dup").durable);
    assertEquals("ORACLE-2", assignment(second, "raw-master").preferredBk);
  }

  @Test
  void newDuplicateInheritsTheFrozenDurableKey() {
    IdentityMapAssignLogic.Existing stored =
        new IdentityMapAssignLogic.Existing(
            "raw-master", "D:raw-master", "ORACLE-1", "raw-master", DAY1, OPEN, 1);
    IdentityMapAssignLogic.Edge edge =
        new IdentityMapAssignLogic.Edge("raw-master", "raw-new", "ORACLE-1", null, DAY2, null);
    List<IdentityMapAssignLogic.Assignment> changes =
        IdentityMapAssignLogic.assign(List.of(stored), List.of(edge), value -> "NO", OPEN);

    IdentityMapAssignLogic.Assignment created = assignment(changes, "raw-new");
    assertEquals("D:raw-master", created.durable);
    assertFalse(created.update);
    assertTrue(changes.stream().noneMatch(row -> "raw-master".equals(row.raw) && !row.update));
  }

  @Test
  void mdmIdIsHashedForANewCluster() {
    IdentityMapAssignLogic.Edge edge =
        new IdentityMapAssignLogic.Edge("raw-master", "raw-dup", null, "MDM-9", DAY1, null);
    List<IdentityMapAssignLogic.Assignment> changes =
        IdentityMapAssignLogic.assign(List.of(), List.of(edge), value -> "H:" + value, OPEN);
    assertEquals("H:MDM-9", assignment(changes, "raw-master").durable);
    assertEquals("H:MDM-9", assignment(changes, "raw-dup").durable);
  }

  private static List<IdentityMapAssignLogic.Existing> asExisting(
      List<IdentityMapAssignLogic.Assignment> rows) {
    return rows.stream()
        .map(
            row ->
                new IdentityMapAssignLogic.Existing(
                    row.raw, row.durable, row.preferredBk, row.master, row.validFrom, row.validTo, row.ruleVersion))
        .toList();
  }

  private static IdentityMapAssignLogic.Assignment assignment(
      List<IdentityMapAssignLogic.Assignment> rows, String raw) {
    return rows.stream().filter(row -> raw.equals(row.raw)).findFirst().orElseThrow();
  }
}
