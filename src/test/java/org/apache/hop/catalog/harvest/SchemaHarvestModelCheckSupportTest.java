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
package org.apache.hop.catalog.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DvModelCheckCache;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.SourceField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SchemaHarvestModelCheckSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void toDiscoveredSourceFieldsSkipsExpectedRole() {
    List<SourceField> fields =
        SchemaHarvestModelCheckSupport.toDiscoveredSourceFields(
            List.of(
                HarvestedField.builder()
                    .role(FieldRole.EXPECTED)
                    .fieldName("id")
                    .hopType("Integer")
                    .length("9")
                    .build(),
                HarvestedField.builder()
                    .role(FieldRole.DISCOVERED)
                    .fieldName("id")
                    .hopType("Integer")
                    .length("10")
                    .primaryKeyPosition(1)
                    .build(),
                HarvestedField.builder()
                    .role(FieldRole.DISCOVERED)
                    .fieldName("name")
                    .hopType("String")
                    .length("50")
                    .build()));
    assertEquals(2, fields.size());
    assertEquals("id", fields.get(0).getName());
    assertEquals(IValueMeta.TYPE_INTEGER, fields.get(0).getHopType());
    assertEquals("10", fields.get(0).getLength());
    assertEquals(1, fields.get(0).getPrimaryKeyPosition());
  }

  @Test
  void buildDatabaseKeyMapAndApplyToCache() throws Exception {
    HarvestResult harvest =
        HarvestResult.builder()
            .harvestRunId("run-1")
            .subjects(
                List.of(
                    HarvestSubjectResult.builder()
                        .subjectKey("hop/retail-example/sources/E2E-customer-hub")
                        .databaseMetaName("CRM")
                        .schemaName("public")
                        .tableName("customer_hub")
                        .discoveryStatus(DiscoveryStatus.OK)
                        .fields(
                            List.of(
                                HarvestedField.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .fieldName("customer_id")
                                    .hopType("Integer")
                                    .length("10")
                                    .build()))
                        .build(),
                    HarvestSubjectResult.builder()
                        .subjectKey("hop/retail-example/sources/csv-feed")
                        .sourceType("CSV")
                        .discoveryStatus(DiscoveryStatus.OK)
                        .fields(
                            List.of(
                                HarvestedField.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .fieldName("x")
                                    .hopType("String")
                                    .build()))
                        .build()))
            .build();

    Map<String, IRowMeta> byKey =
        SchemaHarvestModelCheckSupport.buildDatabaseKeyMap(harvest, new Variables(), null);
    assertEquals(1, byKey.size());
    String key =
        DvModelCheckCache.databaseLiveFieldsKey("CRM", "public", "customer_hub", new Variables());
    assertTrue(byKey.containsKey(key));
    assertEquals(1, byKey.get(key).size());
    assertEquals("customer_id", byKey.get(key).getValueMeta(0).getName());

    try (DvModelCheckOptions options = DvModelCheckOptions.forCheckRun()) {
      SchemaHarvestModelCheckSupport.applyToCache(options.ensureCache(), byKey);
      assertEquals(1, options.getCache().liveFieldsCacheSize());
      assertEquals(byKey.get(key), options.getCache().getLiveFields(key));
    }
  }

  @Test
  void warmCacheIfPreferredDisabled() {
    DvModelCheckOptions options = DvModelCheckOptions.defaults();
    options.setPreferHarvestForLiveFields(false);
    var result =
        SchemaHarvestModelCheckSupport.warmCacheIfPreferred(options, new Variables(), null, null);
    assertFalse(result.usedHarvest());
  }

  @Test
  void resolveHopTypeNames() {
    assertEquals(IValueMeta.TYPE_INTEGER, SchemaHarvestModelCheckSupport.resolveHopType("Integer"));
    assertEquals(IValueMeta.TYPE_STRING, SchemaHarvestModelCheckSupport.resolveHopType("String"));
    assertEquals(IValueMeta.TYPE_DATE, SchemaHarvestModelCheckSupport.resolveHopType("Date"));
  }
}
