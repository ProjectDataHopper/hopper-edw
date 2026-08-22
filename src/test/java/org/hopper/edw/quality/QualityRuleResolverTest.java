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
package org.hopper.edw.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.quality.metadata.DataQualityRuleSetMeta;
import org.hopper.edw.quality.model.DataQualityRule;
import org.hopper.edw.quality.model.DataQualityRuleType;
import org.hopper.edw.quality.model.QualitySeverity;
import org.hopper.edw.quality.model.RecordQualityRuleBinding;
import org.hopper.edw.quality.resolve.QualityRuleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QualityRuleResolverTest {

  private MemoryMetadataProvider provider;

  @BeforeEach
  void setUp() throws Exception {
    provider = new MemoryMetadataProvider();
    provider.getSerializer(DataQualityRuleSetMeta.class).save(sampleSet());
  }

  @Test
  void expandsEntireRuleSetWhenRuleIdEmpty() throws Exception {
    RecordQualityRuleBinding binding = new RecordQualityRuleBinding();
    binding.setRuleSetName("retail-source-quality");
    binding.setRuleId(""); // whole set
    binding.setEnabled(true);

    List<DataQualityRule> rules = QualityRuleResolver.resolve(List.of(binding), provider);
    assertEquals(2, rules.size());
    assertTrue(rules.stream().anyMatch(r -> "table-not-empty".equals(r.getId())));
    assertTrue(rules.stream().anyMatch(r -> "customer-id-not-null".equals(r.getId())));
  }

  @Test
  void actionLevelRuleSetMergesWithCatalogBindingsWithoutDuplicateIds() throws Exception {
    RecordQualityRuleBinding catalog = new RecordQualityRuleBinding();
    catalog.setRuleSetName("retail-source-quality");
    catalog.setRuleId("table-not-empty");
    catalog.setEnabled(true);

    List<DataQualityRule> rules =
        QualityRuleResolver.resolve(List.of(catalog), provider, "retail-source-quality");
    // Whole set + single catalog rule de-dupes to 2 unique ids
    assertEquals(2, rules.size());
  }

  @Test
  void singleRuleBindingStillWorks() throws Exception {
    RecordQualityRuleBinding binding = new RecordQualityRuleBinding();
    binding.setRuleSetName("retail-source-quality");
    binding.setRuleId("customer-id-not-null");
    binding.setFieldNameOverride("cust_id");
    binding.setSeverityOverride(QualitySeverity.WARNING);
    binding.setEnabled(true);

    List<DataQualityRule> rules = QualityRuleResolver.resolve(List.of(binding), provider);
    assertEquals(1, rules.size());
    assertEquals("customer-id-not-null", rules.get(0).getId());
    assertEquals("cust_id", rules.get(0).getFieldName());
    assertEquals(QualitySeverity.WARNING, rules.get(0).getSeverity());
  }

  private static DataQualityRuleSetMeta sampleSet() {
    DataQualityRuleSetMeta set = new DataQualityRuleSetMeta("retail-source-quality");
    DataQualityRule a = new DataQualityRule();
    a.setId("table-not-empty");
    a.setName("Table not empty");
    a.setType(DataQualityRuleType.MIN_ROW_COUNT);
    a.setSeverity(QualitySeverity.BLOCKING);
    a.setEnabled(true);
    a.getParameters().put("min", "1");

    DataQualityRule b = new DataQualityRule();
    b.setId("customer-id-not-null");
    b.setName("customer_id never null");
    b.setType(DataQualityRuleType.NOT_NULL);
    b.setFieldName("customer_id");
    b.setSeverity(QualitySeverity.BLOCKING);
    b.setEnabled(true);

    set.getRules().add(a);
    set.getRules().add(b);
    return set;
  }
}
