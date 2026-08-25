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
package org.hopper.edw.catalog.hopgui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.catalog.discovery.CatalogDiscoverySnapshot;
import org.junit.jupiter.api.Test;

class CatalogDiscoveryPreviewDialogTest {

  @Test
  void formatSummaryIncludesResolvedPathAndEmptyVersionsHint() {
    CatalogDiscoverySnapshot snapshot = new CatalogDiscoverySnapshot();
    snapshot.setConnectionName("local-catalog");
    snapshot.setPluginId("FILE");
    snapshot.setStorageDirectory("${PROJECT_HOME}/work/edw-catalog");
    snapshot.setResolvedStorageDirectory("/tmp/work/edw-catalog");
    snapshot.setWorkingTreeCount(0);
    snapshot.setVersionSnapshotsPresent(true);

    String text = CatalogDiscoveryPreviewDialog.formatSummary(snapshot);
    assertTrue(text.contains("local-catalog"));
    assertTrue(text.contains("/tmp/work/edw-catalog"));
    assertTrue(text.contains("snapshots") || text.contains("versions"));
  }
}
