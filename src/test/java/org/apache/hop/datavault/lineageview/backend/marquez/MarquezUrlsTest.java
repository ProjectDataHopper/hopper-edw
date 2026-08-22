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
package org.apache.hop.datavault.lineageview.backend.marquez;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarquezUrlsTest {

  @Test
  void normalizeStripsLineageSuffix() {
    assertEquals("http://localhost:5001", MarquezUrls.normalizeBaseUrl("http://localhost:5001"));
    assertEquals(
        "http://localhost:5001",
        MarquezUrls.normalizeBaseUrl("http://localhost:5001/api/v1/lineage"));
    assertEquals(
        "http://localhost:5001",
        MarquezUrls.normalizeBaseUrl("http://localhost:5001/api/v1/lineage/"));
    assertEquals(
        "http://localhost:5001",
        MarquezUrls.normalizeBaseUrl("http://localhost:5001/api/v1-beta/lineage"));
    assertEquals(
        "http://localhost:5001", MarquezUrls.normalizeBaseUrl("http://localhost:5001/api/v1"));
  }

  @Test
  void lineageUrlEncodesNodeId() {
    String url =
        MarquezUrls.lineageUrl(
            "http://localhost:5001/api/v1/lineage", "dataset:Vault:public.f_orders", 6);
    assertTrue(url.startsWith("http://localhost:5001/api/v1/lineage?nodeId="));
    assertTrue(url.contains("dataset%3AVault%3Apublic.f_orders"));
    assertTrue(url.endsWith("&depth=6"));
  }

  @Test
  void pathSegmentsEncodeSlashes() {
    String url =
        MarquezUrls.jobUrl(
            "http://localhost:5001", "hop-data-vault/retail", "dm/retail-pos/f_orders");
    assertTrue(url.contains("/namespaces/hop-data-vault%2Fretail/jobs/dm%2Fretail-pos%2Ff_orders"));
  }

  @Test
  void searchQueryWrapsHint() {
    assertEquals("%", MarquezUrls.searchQuery(null));
    assertEquals("%", MarquezUrls.searchQuery(""));
    assertEquals("%orders%", MarquezUrls.searchQuery("orders"));
    assertEquals("ord%", MarquezUrls.searchQuery("ord%"));
  }
}
