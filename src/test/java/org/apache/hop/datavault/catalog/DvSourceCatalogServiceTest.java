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
package org.apache.hop.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DvSourceCatalogServiceTest {

  @Test
  void sourceNotFoundMessageIncludesNamespace() {
    assertEquals(
        "Data Vault source 'E2E-customer-hub' was not found in data catalog connection"
            + " 'local-catalog' in namespace 'hop/retail-example/sources'",
        DvSourceCatalogService.sourceNotFoundMessage(
            "E2E-customer-hub", "local-catalog", "hop/retail-example/sources"));
  }
}
