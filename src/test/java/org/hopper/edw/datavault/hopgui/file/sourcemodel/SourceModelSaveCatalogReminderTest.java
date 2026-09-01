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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.datavault.metadata.sourcemodel.SourceCatalogPublishSyncSupport.SourceCardKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceCatalogPublishSyncSupport.StalePublishedFeed;
import org.junit.jupiter.api.Test;

class SourceModelSaveCatalogReminderTest {

  @Test
  void formatFeedIncludesCardNameDetailsAndOptionalCatalogAlias() {
    StalePublishedFeed sameName =
        new StalePublishedFeed(
            SourceCardKind.PIPELINE,
            "asn-package-lines",
            "asn-package-lines",
            "asn_id (length 2000 → 7)");
    assertTrue(SourceModelSaveCatalogReminder.formatFeed(sameName).contains("asn-package-lines"));
    assertTrue(SourceModelSaveCatalogReminder.formatFeed(sameName).contains("asn_id"));

    StalePublishedFeed aliased =
        new StalePublishedFeed(
            SourceCardKind.QUERY, "all-customer-info", "feed_customer_enriched", "");
    assertEquals(
        "all-customer-info → feed_customer_enriched",
        SourceModelSaveCatalogReminder.formatFeed(aliased));
  }
}
