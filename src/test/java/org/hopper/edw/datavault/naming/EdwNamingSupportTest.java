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
package org.hopper.edw.datavault.naming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.naming.engine.NamingSchemeValidator.Severity;
import org.apache.hop.naming.engine.NamingSchemeWalker;
import org.apache.hop.naming.metadata.NamingCaseStyle;
import org.apache.hop.naming.metadata.NamingScheme;
import org.apache.hop.naming.metadata.NamingWordSeparator;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultNaming;
import org.junit.jupiter.api.Test;

class EdwNamingSupportTest {

  @Test
  void applyLowerUnderscorePrefix() {
    NamingScheme scheme = hubScheme("hub_");
    assertEquals(
        "hub_customer", EdwNamingSupport.apply(scheme, "Customer", EdwNamingSchemeTypes.DV_HUB));
  }

  @Test
  void applySkipsVariablesAndEmpty() {
    NamingScheme scheme = hubScheme("hub_");
    assertEquals("", EdwNamingSupport.apply(scheme, "", EdwNamingSchemeTypes.DV_HUB));
    assertEquals(
        "${TABLE}", EdwNamingSupport.apply(scheme, "${TABLE}", EdwNamingSchemeTypes.DV_HUB));
  }

  @Test
  void walkerFindsNonConformingHubName() {
    DvHub hub = new DvHub("Customer");
    NamingScheme scheme = hubScheme("hub_");
    var findings = NamingSchemeWalker.walk(hub, "test.hdv", List.of(scheme), null);
    assertFalse(findings.isEmpty());
    assertEquals("Customer", findings.get(0).getActual());
    assertEquals("hub_customer", findings.get(0).getExpected());
  }

  @Test
  void walkerAcceptsConformingHubName() {
    DvHub hub = new DvHub("hub_customer");
    NamingScheme scheme = hubScheme("hub_");
    var raw = NamingSchemeWalker.walk(hub, "test.hdv", List.of(scheme), null);
    var findings =
        EdwNamingSupport.filterAffixFixedPoints(raw, List.of(scheme)).stream()
            .filter(f -> f.getSeverity() == Severity.ERROR)
            .toList();
    assertTrue(
        findings.isEmpty(),
        () ->
            findings.stream()
                .map(f -> f.getFieldPath() + "=" + f.getActual() + "->" + f.getExpected())
                .toList()
                .toString());
  }

  @Test
  void prefixSchemeTreatsPrefixedNameAsConforming() {
    NamingScheme scheme = hubScheme("hub_");
    assertTrue(
        EdwNamingSupport.isAffixFixedPoint(scheme, "hub_customer", EdwNamingSchemeTypes.DV_HUB));
    assertFalse(
        EdwNamingSupport.isAffixFixedPoint(scheme, "Customer", EdwNamingSchemeTypes.DV_HUB));
  }

  @Test
  void checkSupportAddsNoRemarksWithoutProviderSchemes() {
    List<ICheckResult> remarks = new java.util.ArrayList<>();
    EdwNamingCheckSupport.addRemarks(new DvHub("Customer"), "x.hdv", remarks, null);
    assertTrue(remarks.isEmpty());
    remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_OK, "keep", null));
    EdwNamingCheckSupport.addRemarks(new DvHub("Customer"), "x.hdv", remarks, null);
    assertEquals(1, remarks.size());
  }

  @Test
  void sourceToVaultFallbackWithoutScheme() {
    assertEquals("hub_customer", SourceToVaultNaming.hubName("Customer"));
    assertEquals("sat_customer", SourceToVaultNaming.hubSatelliteName("Customer"));
    assertEquals("lnk_order_line", SourceToVaultNaming.linkNameFromTable("order_line"));
    assertEquals("ref_country", SourceToVaultNaming.referenceName("country"));
  }

  @Test
  void sourceToVaultUsesTypeSpecificScheme() {
    NamingScheme scheme = hubScheme("h_");
    org.apache.hop.metadata.api.IHopMetadataProvider unused = null;
    SourceToVaultNaming.runWithProvider(
        unused,
        () -> {
          // No provider schemes loaded; fallback remains.
          assertEquals("hub_customer", SourceToVaultNaming.hubName("Customer"));
        });
    assertEquals(
        "h_customer", EdwNamingSupport.apply(scheme, "Customer", EdwNamingSchemeTypes.DV_HUB));
  }

  @Test
  void typeCodesAreStable() {
    assertEquals("dv-hub", EdwNamingSchemeTypes.DV_HUB);
    assertEquals("database-table", EdwNamingSchemeTypes.DATABASE_TABLE);
    assertEquals("edw-record-definition", EdwNamingSchemeTypes.EDW_RECORD_DEFINITION);
  }

  private static NamingScheme hubScheme(String prefix) {
    NamingScheme scheme = new NamingScheme("edw-dv-hub");
    scheme.setType(EdwNamingSchemeTypes.DV_HUB);
    scheme.setCaseStyle(NamingCaseStyle.LOWER.getCode());
    scheme.setWordSeparator(NamingWordSeparator.UNDERSCORE.getCode());
    scheme.setPrefix(prefix);
    scheme.setRemoveSpecialCharacters(true);
    scheme.setCollapseRepeatedSeparators(true);
    scheme.setTrimEdgeSeparators(true);
    return scheme;
  }
}
