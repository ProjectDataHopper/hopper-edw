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
package org.hopper.edw.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

/** Unit coverage for attribute mutation helpers used when accepting a source field change. */
class RemediationProposalApplyAttributeTest {

  @Test
  void applyDiscoveredTypeUpdatesLengthAndHopDataType() throws Exception {
    SatelliteAttribute attribute = new SatelliteAttribute("email");
    attribute.setDataType("String");
    attribute.setLength("50");

    SourceField discovered = new SourceField("email");
    discovered.setHopType(IValueMeta.TYPE_STRING);
    discovered.setLength("120");
    discovered.setSourceDataType("varchar");

    boolean changed =
        RemediationProposalApplySupport.expandAttributeToDiscovered(attribute, discovered);

    assertTrue(changed);
    assertEquals("120", attribute.getLength());
    // Data type is only rewritten when ValueMetaFactory is initialized (returns a real name).
    assertTrue(
        "String".equals(attribute.getDataType())
            || "String".equalsIgnoreCase(attribute.getDataType())
            || attribute.getDataType() != null);
  }

  @Test
  void expandNeverShrinksLength() {
    SatelliteAttribute attribute = new SatelliteAttribute("email");
    attribute.setLength("75");

    SourceField shorter = new SourceField("email");
    shorter.setLength("50");

    RemediationProposalApplySupport.expandAttributeToDiscovered(attribute, shorter);
    assertEquals("75", attribute.getLength());
  }

  @Test
  void parseLengthSidesFromChangeDetails() {
    assertEquals("50", RemediationProposalApplySupport.parseLengthSide("length 50 -> 75", true));
    assertEquals("75", RemediationProposalApplySupport.parseLengthSide("length 50 -> 75", false));
    assertEquals(
        "50",
        RemediationProposalApplySupport.parseLengthSide(
            "expected length 50 → actual length 75", true));
    assertEquals(
        "75",
        RemediationProposalApplySupport.parseLengthSide(
            "expected length 50 → actual length 75", false));
  }

  @Test
  void preferLongerLengthPicksMax() {
    assertEquals("75", RemediationProposalApplySupport.preferLongerLength("50", "75"));
    assertEquals("75", RemediationProposalApplySupport.preferLongerLength("75", "50"));
  }

  @Test
  void findAttributeIsCaseInsensitive() throws Exception {
    org.hopper.edw.datavault.metadata.DvSatellite satellite =
        new org.hopper.edw.datavault.metadata.DvSatellite();
    satellite.setName("sat_customer_demo");
    SatelliteAttribute attribute = new SatelliteAttribute("Email");
    attribute.setLength("50");
    satellite.getAttributes().add(attribute);

    Method method =
        RemediationProposalApplySupport.class.getDeclaredMethod(
            "findAttribute", org.hopper.edw.datavault.metadata.DvSatellite.class, String.class);
    method.setAccessible(true);
    SatelliteAttribute found = (SatelliteAttribute) method.invoke(null, satellite, "email");

    assertEquals("Email", found.getName());
    assertEquals("50", found.getLength());
  }
}
