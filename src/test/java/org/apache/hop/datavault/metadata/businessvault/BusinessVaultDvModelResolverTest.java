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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BusinessVaultDvModelResolverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void buildEffectiveModelFromCanvasAliasWithoutLinkedPath() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    BusinessVaultModel bv = new BusinessVaultModel();
    bv.setFilename(Path.of("integration-tests/tests/basic/vault1.hbv").toAbsolutePath().toString());
    // No dataVaultModelPath — multi-model style.
    BvDvTableReference hubRef = new BvDvTableReference("hub_customer", DvTableType.HUB);
    hubRef.setReferencedModelFilename(dvPath.toString());
    bv.getDvReferences().add(hubRef);

    DataVaultModel effective =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bv, new Variables(), null);
    assertNotNull(effective);
    assertNotNull(effective.findTable("hub_customer"));
    assertTrue(effective.getTables().size() >= 1);
  }

  @Test
  void resolveDvTableUsesAliasPath() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    BusinessVaultModel bv = new BusinessVaultModel();
    bv.setFilename(Path.of("integration-tests/tests/basic/vault1.hbv").toAbsolutePath().toString());
    BvDvTableReference satRef = new BvDvTableReference("sat_customer", DvTableType.SATELLITE);
    satRef.setReferencedModelFilename(dvPath.toString());
    bv.getDvReferences().add(satRef);

    IDvTable table =
        BusinessVaultDvModelResolver.resolveDvTable(bv, "sat_customer", new Variables(), null);
    assertNotNull(table);
    assertEquals("sat_customer", table.getName());
  }

  @Test
  void resolveDvTableReturnsNullWithoutAliasOrPath() throws Exception {
    BusinessVaultModel bv = new BusinessVaultModel();
    assertNull(
        BusinessVaultDvModelResolver.resolveDvTable(bv, "hub_customer", new Variables(), null));
  }

  @Test
  void emptyModelWhenNoRefsAndNoPath() throws Exception {
    BusinessVaultModel bv = new BusinessVaultModel();
    DataVaultModel effective =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bv, new Variables(), null);
    assertNotNull(effective);
    assertTrue(effective.getTables().isEmpty());
  }
}
