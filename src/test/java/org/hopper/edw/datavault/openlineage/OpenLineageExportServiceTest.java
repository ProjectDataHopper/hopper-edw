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
package org.hopper.edw.datavault.openlineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.junit.jupiter.api.Test;

class OpenLineageExportServiceTest {

  @Test
  void stampHopIdentityFillsEmptyFieldsOnly() {
    LineageSnapshot snapshot = new LineageSnapshot();
    OpenLineageExportService.stampHopIdentity(snapshot, "retail-sources", "edw-catalog");
    assertEquals("retail-sources", snapshot.getResourceGroup());
    assertEquals("edw-catalog", snapshot.getCatalogConnection());

    snapshot.setResourceGroup("already-set");
    snapshot.setCatalogConnection("already-catalog");
    OpenLineageExportService.stampHopIdentity(snapshot, "other-group", "other-catalog");
    assertEquals("already-set", snapshot.getResourceGroup());
    assertEquals("already-catalog", snapshot.getCatalogConnection());
  }

  @Test
  void stampHopIdentityIgnoresNullSnapshot() {
    OpenLineageExportService.stampHopIdentity(null, "g", "c");
  }

  @Test
  void stampHopIdentitySkipsBlankFallbacks() {
    LineageSnapshot snapshot = new LineageSnapshot();
    OpenLineageExportService.stampHopIdentity(snapshot, "", null);
    assertNull(snapshot.getResourceGroup());
    assertNull(snapshot.getCatalogConnection());
  }
}
