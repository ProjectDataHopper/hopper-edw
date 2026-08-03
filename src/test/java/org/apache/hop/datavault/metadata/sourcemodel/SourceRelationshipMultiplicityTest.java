/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.metadata.sourcemodel.profile.SourceRelationshipMultiplicityInference;
import org.apache.hop.datavault.metadata.sourcemodel.profile.SourceRelationshipProfileOptions;
import org.apache.hop.datavault.metadata.sourcemodel.profile.SourceRelationshipProfileStrategy;
import org.apache.hop.datavault.metadata.sourcemodel.profile.SourceRelationshipProfiler;
import org.junit.jupiter.api.Test;

class SourceRelationshipMultiplicityTest {

  @Test
  void parseLegacyNto1() {
    SourceRelationshipMultiplicity.MultiplicityPair pair =
        SourceRelationshipMultiplicity.parseLegacyCardinality("N:1");
    assertNotNull(pair);
    assertEquals(SourceRelationshipMultiplicity.ONE_OR_MANY, pair.child());
    assertEquals(SourceRelationshipMultiplicity.ONE, pair.parent());
  }

  @Test
  void migrateLegacyCardinalityOnRelationship() {
    SourceRelationship relationship = new SourceRelationship("fk");
    relationship.setCardinality("0..N:1");
    relationship.setChildMultiplicity(SourceRelationshipMultiplicity.UNKNOWN);
    relationship.setParentMultiplicity(SourceRelationshipMultiplicity.ONE);
    assertEquals(
        SourceRelationshipMultiplicity.ZERO_OR_MANY, relationship.resolveChildMultiplicity());
    assertEquals(SourceRelationshipMultiplicity.ONE, relationship.resolveParentMultiplicity());
  }

  @Test
  void inferenceTypicalFk() {
    // Non-null FK, many children per parent, some parents lonely, parent unique.
    SourceRelationshipMultiplicity.MultiplicityPair pair =
        SourceRelationshipMultiplicityInference.fromMetrics(false, 5, true, true);
    assertEquals(SourceRelationshipMultiplicity.ONE_OR_MANY, pair.child());
    assertEquals(SourceRelationshipMultiplicity.ZERO_OR_ONE, pair.parent());
  }

  @Test
  void inferenceOptionalFkOneToOne() {
    SourceRelationshipMultiplicity.MultiplicityPair pair =
        SourceRelationshipMultiplicityInference.fromMetrics(true, 1, false, true);
    assertEquals(SourceRelationshipMultiplicity.ZERO_OR_ONE, pair.child());
    assertEquals(SourceRelationshipMultiplicity.ONE, pair.parent());
  }

  @Test
  void recommendStrategyBySize() {
    SourceRelationshipProfileOptions opts = SourceRelationshipProfileOptions.defaults();
    assertEquals(
        SourceRelationshipProfileStrategy.EXACT_KEY,
        SourceRelationshipProfiler.recommendStrategy(1_000, 2_000, opts));
    assertEquals(
        SourceRelationshipProfileStrategy.SAMPLED_KEY,
        SourceRelationshipProfiler.recommendStrategy(500_000, 10_000, opts));
    assertEquals(
        SourceRelationshipProfileStrategy.STATS_ONLY,
        SourceRelationshipProfiler.recommendStrategy(10_000_000, 1_000, opts));
  }

  @Test
  void compactLabels() {
    assertEquals("0..N", SourceRelationshipMultiplicity.ZERO_OR_MANY.compactLabel());
    assertEquals("1", SourceRelationshipMultiplicity.ONE.compactLabel());
    assertTrue(SourceRelationshipMultiplicity.ONE_OR_MANY.isMany());
    assertTrue(SourceRelationshipMultiplicity.ONE.isMandatory());
  }
}
