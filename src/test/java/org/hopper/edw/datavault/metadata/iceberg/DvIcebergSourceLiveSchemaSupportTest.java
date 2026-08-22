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
package org.hopper.edw.datavault.metadata.iceberg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class DvIcebergSourceLiveSchemaSupportTest {

  @Test
  void supportsLiveFieldResolutionWhenConfigured() {
    DvIcebergSource source = new DvIcebergSource();
    assertTrue(source.supportsLiveFieldResolution());

    DvIcebergSource configured = new DvIcebergSource();
    configured.setCatalogUri("${ICEBERG_CATALOG_URI}");
    configured.setNamespace("${ICEBERG_NAMESPACE}");
    configured.setTableName("${ICEBERG_TABLE}");

    assertTrue(configured.supportsLiveFieldResolution());
    assertFalse(
        DvIcebergSourceLiveSchemaSupport.hasResolvableLocation(configured, new Variables()));

    Variables variables = new Variables();
    variables.setVariable("ICEBERG_CATALOG_URI", "http://iceberg-rest:8181");
    variables.setVariable("ICEBERG_NAMESPACE", "crm");
    variables.setVariable("ICEBERG_TABLE", "customer");
    assertTrue(DvIcebergSourceLiveSchemaSupport.hasResolvableLocation(configured, variables));
  }

  @Test
  void hasResolvableLocationRequiresCatalogNamespaceAndTable() {
    DvIcebergSource source = new DvIcebergSource();
    source.setCatalogUri("${ICEBERG_CATALOG_URI}");

    assertFalse(DvIcebergSourceLiveSchemaSupport.hasResolvableLocation(source, new Variables()));
  }
}
