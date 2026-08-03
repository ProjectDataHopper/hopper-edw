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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.metadata.dimensional.DmDimensionLoadStrategySupport.DmDimensionLoadStrategy;
import org.junit.jupiter.api.Test;

class DmDimensionLoadStrategySupportTest {

  @Test
  void allType1AttributesResolvePureType1() {
    DmDimension dimension = new DmDimension();
    dimension.getAttributes().add(new DmDimensionAttribute("name", DmScdUpdatePolicy.TYPE1));
    dimension.getAttributes().add(new DmDimensionAttribute("city", DmScdUpdatePolicy.TYPE1));

    assertEquals(
        DmDimensionLoadStrategy.PURE_TYPE1,
        DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension));
  }

  @Test
  void allType2AttributesResolvePureType2() {
    DmDimension dimension = new DmDimension();
    dimension.getAttributes().add(new DmDimensionAttribute("name", DmScdUpdatePolicy.TYPE2));

    assertEquals(
        DmDimensionLoadStrategy.PURE_TYPE2,
        DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension));
    assertTrue(DmDimensionLoadStrategySupport.usesVersionedLayout(dimension));
  }

  @Test
  void mixedPoliciesResolveDimensionLookup() {
    DmDimension dimension = new DmDimension();
    dimension.getAttributes().add(new DmDimensionAttribute("name", DmScdUpdatePolicy.TYPE1));
    dimension.getAttributes().add(new DmDimensionAttribute("city", DmScdUpdatePolicy.TYPE2));

    assertEquals(
        DmDimensionLoadStrategy.DIMENSION_LOOKUP,
        DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension));
  }

  @Test
  void legacyScdTypeMaterializesPoliciesWhenMissing() {
    DmDimension dimension = new DmDimension();
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.getAttributes().add(new DmDimensionAttribute("name"));

    dimension.normalizeLegacyScdType();

    assertEquals(DmScdUpdatePolicy.TYPE2, dimension.getAttributes().get(0).getScdUpdatePolicy());
    assertEquals(
        DmDimensionLoadStrategy.PURE_TYPE2,
        DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension));
  }

  @Test
  void punchThroughTriggersDimensionLookup() {
    DmDimension dimension = new DmDimension();
    dimension
        .getAttributes()
        .add(new DmDimensionAttribute("birthdate", DmScdUpdatePolicy.TYPE1_PUNCH_THROUGH));

    assertEquals(
        DmDimensionLoadStrategy.DIMENSION_LOOKUP,
        DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension));
  }

  @Test
  void resolvesSourceAndTargetFieldNames() {
    DmDimensionAttribute attribute =
        new DmDimensionAttribute("birthdate_hist", DmScdUpdatePolicy.TYPE2);
    attribute.setSourceFieldName("birthdate");

    assertEquals(
        "birthdate", DmDimensionLoadStrategySupport.resolveSourceFieldName(attribute, null));
    assertEquals(
        "birthdate_hist", DmDimensionLoadStrategySupport.resolveTargetFieldName(attribute, null));
  }
}
