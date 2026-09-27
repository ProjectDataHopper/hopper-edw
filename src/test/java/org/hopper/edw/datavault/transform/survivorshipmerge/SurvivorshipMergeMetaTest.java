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
package org.hopper.edw.datavault.transform.survivorshipmerge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SurvivorshipMergeMetaTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void nullInfoStreamKeepsTheIncomingFields() throws HopException {
    SurvivorshipMergeMeta meta = new SurvivorshipMergeMeta();
    IRowMeta incoming = new RowMeta();
    incoming.addValueMeta(new ValueMetaString("customer_hk"));
    incoming.addValueMeta(new ValueMetaString("name"));

    meta.getFields(
        incoming, "survivorship", new IRowMeta[] {null}, null, new Variables(), null);

    assertEquals(2, incoming.size());
    assertTrue(incoming.indexOfValue("customer_hk") >= 0);
    assertTrue(incoming.indexOfValue("name") >= 0);
  }

  @Test
  void infoLayoutsReplaceTheOutputFields() throws HopException {
    SurvivorshipMergeMeta meta = new SurvivorshipMergeMeta();
    IRowMeta incoming = new RowMeta();
    incoming.addValueMeta(new ValueMetaString("stale"));

    IRowMeta legA = new RowMeta();
    legA.addValueMeta(new ValueMetaString("customer_hk"));
    legA.addValueMeta(new ValueMetaString("name"));
    IRowMeta legB = new RowMeta();
    legB.addValueMeta(new ValueMetaString("customer_hk"));
    legB.addValueMeta(new ValueMetaString("city"));

    meta.getFields(incoming, "survivorship", new IRowMeta[] {legA, null, legB}, null, new Variables(), null);

    assertEquals(-1, incoming.indexOfValue("stale"));
    assertTrue(incoming.indexOfValue("customer_hk") >= 0);
    assertTrue(incoming.indexOfValue("name") >= 0);
    assertTrue(incoming.indexOfValue("city") >= 0);
    assertEquals("survivorship", incoming.searchValueMeta("city").getOrigin());
  }
}
