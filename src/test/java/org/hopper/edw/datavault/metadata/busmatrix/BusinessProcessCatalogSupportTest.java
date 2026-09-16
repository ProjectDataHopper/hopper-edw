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
package org.hopper.edw.datavault.metadata.busmatrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.dimensional.DmBusinessProcessRef;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmValidationSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BusinessProcessCatalogSupportTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void filtersChildrenByParentAndValidatesUnknownTerms() throws Exception {
    BusinessProcessCatalogMeta catalog = new BusinessProcessCatalogMeta("retail-bus");
    catalog.getDomains().add(new BusinessProcessTerm("Retail", "Retail domain", null));
    catalog.getLevel1().add(new BusinessProcessTerm("Sales", "", "Retail"));
    catalog.getLevel1().add(new BusinessProcessTerm("Inventory", "", "Retail"));
    catalog.getLevel2().add(new BusinessProcessTerm("Order management", "", "Sales"));

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(BusinessProcessCatalogMeta.class).save(catalog);

    BusinessProcessCatalogSupport.ResolvedCatalog resolved =
        BusinessProcessCatalogSupport.load(provider, "retail-bus");
    String[] salesChildren =
        resolved.names(BusinessProcessCatalogSupport.TermLevel.LEVEL2, "Sales", null);
    assertTrue(java.util.Arrays.asList(salesChildren).contains("Order management"));

    DmBusinessProcessRef ok = new DmBusinessProcessRef();
    ok.setBusiness("Retail");
    ok.setLevel1("Sales");
    ok.setLevel2("Order management");
    assertTrue(resolved.validate(ok).isEmpty());

    DmBusinessProcessRef bad = new DmBusinessProcessRef();
    bad.setBusiness("Retail");
    bad.setLevel1("Not a process");
    assertFalse(resolved.validate(bad).isEmpty());
  }

  @Test
  void factCheckWarnsWhenProcessIsMissing() {
    DmFact fact = new DmFact();
    fact.setName("f_orders");
    java.util.List<org.apache.hop.core.ICheckResult> remarks = new java.util.ArrayList<>();
    DmValidationSupport.validateBusinessProcess(remarks, fact, null, null);
    assertEquals(1, remarks.size());
    assertTrue(remarks.get(0).getText().toLowerCase().contains("business process"));
  }
}
