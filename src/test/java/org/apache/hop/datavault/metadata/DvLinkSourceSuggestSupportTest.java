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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DvLinkSourceSuggestSupportTest {

  @Test
  void mapsIdentityBusinessKeysForAsnStyleFeed() {
    List<String> fields =
        List.of(
            "asn_id",
            "order_id",
            "customer_id",
            "warehouse_id",
            "product_id",
            "package_id",
            "quantity");

    DvHub order = hub("hub_order", "order_id");
    DvHub product = hub("hub_product", "product_id");
    DvHub warehouse = hub("hub_warehouse", "warehouse_id");
    DvHub customer = hub("hub_customer", "customer_id");
    DvHub packageLine = hub("hub_package_line", "asn_id");

    DvLinkSourceSuggestSupport.SuggestResult result =
        DvLinkSourceSuggestSupport.suggestHubSourceMappings(
            "asn-package-lines", fields, List.of(order, product, warehouse, customer, packageLine));

    assertEquals(5, result.mappedCount());
    assertEquals(0, result.missingCount());
    assertEquals(5, result.suggestedHubNames().size());
    assertEquals("asn-package-lines", result.proposedHubSource().getSourceName());
    assertEquals(5, result.proposedHubSource().getHubSourceKeyFields().size());

    DvLink.HubSourceKeyField orderMap =
        result.proposedHubSource().getHubSourceKeyFields().stream()
            .filter(h -> "hub_order".equals(h.getHubName()))
            .findFirst()
            .orElseThrow();
    assertEquals(1, orderMap.getSourceBusinessKeyFields().size());
    assertEquals("order_id", orderMap.getSourceBusinessKeyFields().get(0).getSourceFieldName());
  }

  @Test
  void reportsMissingKeysWhenFeedLacksColumn() {
    DvHub order = hub("hub_order", "order_id");
    DvHub product = hub("hub_product", "product_id");

    DvLinkSourceSuggestSupport.SuggestResult result =
        DvLinkSourceSuggestSupport.suggestHubSourceMappings(
            "partial", List.of("order_id"), List.of(order, product));

    assertEquals(1, result.mappedCount());
    assertEquals(1, result.missingCount());
    assertTrue(result.suggestedHubNames().contains("hub_order"));
    assertTrue(!result.suggestedHubNames().contains("hub_product"));
  }

  @Test
  void mergeEmptyOnlyPreservesExistingMaps() {
    List<DvLink.DvLinkHubSource> working = new ArrayList<>();
    DvLink.DvLinkHubSource existing = new DvLink.DvLinkHubSource();
    existing.setSourceName("asn-package-lines");
    DvLink.HubSourceKeyField existingHub = new DvLink.HubSourceKeyField();
    existingHub.setHubName("hub_order");
    existingHub.getSourceBusinessKeyFields().add(new BusinessKeySource("order_id", "ORDER_NR"));
    existing.getHubSourceKeyFields().add(existingHub);
    working.add(existing);

    DvLink.DvLinkHubSource proposed = new DvLink.DvLinkHubSource();
    proposed.setSourceName("asn-package-lines");
    DvLink.HubSourceKeyField proposedHub = new DvLink.HubSourceKeyField();
    proposedHub.setHubName("hub_order");
    proposedHub.getSourceBusinessKeyFields().add(new BusinessKeySource("order_id", "order_id"));
    proposed.getHubSourceKeyFields().add(proposedHub);
    DvLink.HubSourceKeyField productHub = new DvLink.HubSourceKeyField();
    productHub.setHubName("hub_product");
    productHub.getSourceBusinessKeyFields().add(new BusinessKeySource("product_id", "product_id"));
    proposed.getHubSourceKeyFields().add(productHub);

    DvLinkSourceSuggestSupport.mergeSuggestedHubSource(working, proposed, true);

    assertEquals(1, working.size());
    assertEquals(2, working.get(0).getHubSourceKeyFields().size());
    DvLink.HubSourceKeyField order =
        working.get(0).getHubSourceKeyFields().stream()
            .filter(h -> "hub_order".equals(h.getHubName()))
            .findFirst()
            .orElseThrow();
    assertEquals("ORDER_NR", order.getSourceBusinessKeyFields().get(0).getSourceFieldName());
  }

  private static DvHub hub(String name, String... businessKeys) {
    DvHub hub = new DvHub(name);
    for (String bk : businessKeys) {
      BusinessKey key = new BusinessKey();
      key.setName(bk);
      key.setDataType("String");
      hub.getBusinessKeys().add(key);
    }
    return hub;
  }
}
