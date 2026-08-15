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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataVaultConfigurationTest {

  @Test
  void blankHashContentCasingFallsBackToUpper() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    configuration.setHashContentCasing("");
    assertEquals(HashContentCasing.UPPER.name(), configuration.getHashContentCasing());
    assertEquals(HashContentCasing.UPPER, configuration.resolveHashContentCasing());
  }

  @Test
  void unknownHashAlgorithmFallsBackToMd5() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    configuration.setHashAlgorithm("not-a-real-algorithm");
    assertEquals(HashAlgorithm.MD5.name(), configuration.getHashAlgorithm());
    assertEquals(HashAlgorithm.MD5, configuration.resolveHashAlgorithm());
  }

  @Test
  void blankHashKeyDataTypeFallsBackToHex() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    configuration.setHashKeyDataType("   ");
    assertEquals(HashKeyDataType.HEX.name(), configuration.getHashKeyDataType());
    assertEquals(HashKeyDataType.HEX, configuration.resolveHashKeyDataType());
  }

  @Test
  void newConfigurationDefaultsHashKeyDataTypeToHex() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    assertEquals(HashKeyDataType.HEX.name(), configuration.getHashKeyDataType());
    assertEquals(HashKeyDataType.HEX, configuration.resolveHashKeyDataType());
  }

  @Test
  void newConfigurationDefaultsHashUsesComposedBusinessKeyToFalse() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    assertEquals(false, configuration.isHashUsesComposedBusinessKey());
  }

  @Test
  void newConfigurationDefaultsOrphanPolicyToPass() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    assertEquals(DvOrphanPolicy.PASS.name(), configuration.getOrphanPolicy());
    assertEquals(DvOrphanPolicy.PASS, configuration.resolveOrphanPolicy());
    assertEquals("INFERRED", configuration.resolveInferredRecordSource(null));
    assertEquals("is_inferred", configuration.resolveInferredFlagField(null));
    assertEquals("dv_orphan_quarantine", configuration.resolveQuarantineTableName(null));
    assertEquals(false, configuration.isStoreInferredFlag());
  }

  @Test
  void newConfigurationDefaultsStoreLoadCycleIdToFalse() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    assertEquals(false, configuration.isStoreLoadCycleId());
    assertEquals("LOAD_CYCLE_ID", configuration.getLoadCycleIdField());
  }

  @Test
  void blankSortRowsSizeFallsBackToHopDefault() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    configuration.setSortRowsSize("");
    assertEquals(
        DataVaultConfiguration.DEFAULT_SORT_ROWS_SIZE, configuration.resolveSortRowsSize(null));
  }

  @Test
  void hashComboOptionsIncludeAllEnumValues() {
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    assertEquals(
        java.util.Arrays.stream(HashAlgorithm.values()).map(Enum::name).toList(),
        configuration.getHashAlgorithmOptions(null, null));
    assertEquals(
        java.util.Arrays.stream(HashKeyDataType.values()).map(Enum::name).toList(),
        configuration.getHashKeyDataTypeOptions(null, null));
    assertEquals(
        java.util.Arrays.stream(HashContentCasing.values()).map(Enum::name).toList(),
        configuration.getHashContentCasingOptions(null, null));
    assertTrue(configuration.getHashAlgorithmOptions(null, null).contains("MD5"));
    assertTrue(configuration.getHashKeyDataTypeOptions(null, null).contains("BINARY"));
    assertTrue(configuration.getHashContentCasingOptions(null, null).contains("UPPER"));
  }
}
