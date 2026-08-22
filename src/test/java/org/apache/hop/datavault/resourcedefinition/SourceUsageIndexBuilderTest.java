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
package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmSourceType;
import org.junit.jupiter.api.Test;

class SourceUsageIndexBuilderTest {

  @Test
  void indexesDataVaultSatelliteSourceWithMappedAttributes() {
    DataVaultModel model = new DataVaultModel();
    model.setName("retail-360");
    model.setFilename("/tmp/retail-360.hdv");

    DvSatellite satellite = new DvSatellite();
    satellite.setName("sat_customer_demo");
    satellite.setRecordSourceName("customer_demo");
    satellite.getAttributes().add(new SatelliteAttribute("first_name"));
    model.getTables().add(satellite);

    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail");
    ValidationModels models =
        new ValidationModels(
            group,
            List.of(new ValidationModels.LoadedDataVaultModel(model, "local-catalog")),
            List.of(),
            List.of());

    Map<RecordDefinitionKey, List<SourceUsage>> index =
        SourceUsageIndexBuilder.build(models, new Variables());

    RecordDefinitionKey key = index.keySet().stream().findFirst().orElseThrow();
    assertEquals("customer_demo", key.getName());
    assertEquals(1, index.get(key).size());
    assertEquals("sat_customer_demo", index.get(key).getFirst().modelElementName());
    assertTrue(index.get(key).getFirst().mappedFields().contains("first_name"));
  }

  @Test
  void indexesDimensionalRecordDefinitionSource() {
    DimensionalModel model = new DimensionalModel();
    model.setName("retail-f-orders");
    model.setFilename("/tmp/retail-f-orders.hdm");

    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    DmSourceConfiguration source = dimension.getSourceOrDefault();
    source.setSourceType(DmSourceType.RECORD_DEFINITION);
    source.setSourceRecordNamespace("hop/retail-example/sources");
    source.setSourceRecordName("customer_demo");
    model.getTables().add(dimension);

    ValidationModels models =
        new ValidationModels(
            new ResourceDefinitionGroupMeta("retail"),
            List.of(),
            List.of(),
            List.of(new ValidationModels.LoadedDimensionalModel(model, "local-catalog")));

    Map<RecordDefinitionKey, List<SourceUsage>> index =
        SourceUsageIndexBuilder.build(models, new Variables());

    RecordDefinitionKey key =
        new RecordDefinitionKey("hop/retail-example/sources", "customer_demo");
    assertTrue(index.containsKey(key));
    assertEquals("dim_customer", index.get(key).getFirst().modelElementName());
  }

  @Test
  void indexesHubRecordSourcesWithBusinessKeyMappings() {
    DataVaultModel model = new DataVaultModel();
    model.setName("retail-360");

    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.getRecordSources().add("customer_core");
    BusinessKey businessKey = new BusinessKey();
    businessKey.setName("customer_id");
    businessKey.setSourceFieldName("id");
    hub.getBusinessKeys().add(businessKey);
    model.getTables().add(hub);

    ValidationModels models =
        new ValidationModels(
            new ResourceDefinitionGroupMeta("retail"),
            List.of(new ValidationModels.LoadedDataVaultModel(model, "local-catalog")),
            List.of(),
            List.of());

    Map<RecordDefinitionKey, List<SourceUsage>> index =
        SourceUsageIndexBuilder.build(models, new Variables());

    RecordDefinitionKey key =
        index.keySet().stream()
            .filter(k -> "customer_core".equals(k.getName()))
            .findFirst()
            .orElseThrow();
    assertEquals("hub_customer", index.get(key).getFirst().modelElementName());
    assertTrue(index.get(key).getFirst().mappedFields().contains("id"));
  }
}
