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
package org.hopper.edw.datavault.metadata.coaching;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoachingSourceNodeTest {

  @Test
  void matchesFilterOnSourceNameTypeAndTargetTable() {
    CoachingSourceRef ref =
        CoachingSourceRef.forRecordDefinition("local-catalog", "hop/test/sources", "CRM-customer");
    CoachingSourceNode node =
        CoachingSourceNode.builder()
            .sourceRef(ref)
            .displayLabel("CRM-customer")
            .typeLabel("RECORD_DEFINITION")
            .target(
                CoachingTargetUsage.builder()
                    .tableName("sat_customer")
                    .tableRole("satellite")
                    .summary("mapped from CRM")
                    .build())
            .build();

    assertTrue(node.matchesFilter(""));
    assertTrue(node.matchesFilter("  "));
    assertTrue(node.matchesFilter("crm"));
    assertTrue(node.matchesFilter("RECORD"));
    assertTrue(node.matchesFilter("sat_customer"));
    assertTrue(node.matchesFilter("satellite"));
    assertFalse(node.matchesFilter("orders"));
  }
}
