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

class IdentityLookupLogicTest {

  private static final Timestamp START = Timestamp.valueOf("2020-01-01 00:00:00");
  private static final Timestamp OPEN = Timestamp.valueOf("9999-12-31 23:59:59");

  @Test
  void mappedRowUsesDurableKeyAndUnmappedSelfKeepsTheRawKey() {
    List<IdentityLookupLogic.Interval> map =
        List.of(new IdentityLookupLogic.Interval("raw-a", "durable-a", "ORACLE-2", START, OPEN));
    List<IdentityLookupLogic.MainRow> rows =
        List.of(
            new IdentityLookupLogic.MainRow("raw-a", Timestamp.valueOf("2021-06-01 00:00:00")),
            new IdentityLookupLogic.MainRow("raw-a", Timestamp.valueOf("2019-01-01 00:00:00")),
            new IdentityLookupLogic.MainRow("raw-b", Timestamp.valueOf("2021-06-01 00:00:00")));

    List<IdentityLookupLogic.Hit> hits =
        IdentityLookupLogic.lookupAll(rows, map, BvIdentityUnmappedPolicy.SELF);

    assertTrue(hits.get(0).emit);
    assertEquals("durable-a", hits.get(0).key);
    assertEquals("raw-a", hits.get(0).raw);
    assertEquals("ORACLE-2", hits.get(0).preferredBk);
    assertEquals(null, hits.get(0).ruleVersion);
    assertEquals("raw-a", hits.get(1).key);
    assertEquals("raw-b", hits.get(2).key);
  }

  @Test
  void dropAndQuarantineDoNotEmit() {
    IdentityLookupLogic.Hit dropped =
        IdentityLookupLogic.lookup(null, null, List.of(), BvIdentityUnmappedPolicy.DROP);
    IdentityLookupLogic.Hit quarantined =
        IdentityLookupLogic.lookup(
            "raw-b",
            Timestamp.valueOf("2021-01-01 00:00:00"),
            List.of(),
            BvIdentityUnmappedPolicy.QUARANTINE);
    assertFalse(dropped.emit);
    assertFalse(dropped.quarantine);
    assertFalse(quarantined.emit);
    assertTrue(quarantined.quarantine);
  }

  @Test
  void binaryRawKeysMatchByContent() {
    byte[] raw = new byte[] {1, 2, 3};
    byte[] rawCopy = new byte[] {1, 2, 3};
    IdentityLookupLogic.Hit hit =
        IdentityLookupLogic.lookup(
            rawCopy,
            Timestamp.valueOf("2021-01-01 00:00:00"),
            List.of(new IdentityLookupLogic.Interval(raw, "durable", null, START, null)),
            BvIdentityUnmappedPolicy.SELF);
    assertEquals("durable", hit.key);
  }

  @Test
  void mappedRowKeepsTheMapRuleVersion() {
    IdentityLookupLogic.Hit hit =
        IdentityLookupLogic.lookup(
            "raw-a",
            Timestamp.valueOf("2021-01-01 00:00:00"),
            List.of(
                new IdentityLookupLogic.Interval("raw-a", "durable-a", "ORACLE", START, OPEN, 4)),
            BvIdentityUnmappedPolicy.SELF);
    assertEquals(4, hit.ruleVersion);
  }
}
