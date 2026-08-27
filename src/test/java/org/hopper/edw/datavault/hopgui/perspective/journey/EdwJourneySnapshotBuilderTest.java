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
package org.hopper.edw.datavault.hopgui.perspective.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.junit.jupiter.api.Test;

class EdwJourneySnapshotBuilderTest {

  @Test
  void emptyGroupNameReturnsEmptySnapshot() {
    assertFalse(EdwJourneySnapshotBuilder.build(null, new Variables(), null).hasGroup());
    assertFalse(
        EdwJourneySnapshotBuilder.build(new ResourceDefinitionGroupMeta(""), new Variables(), null)
            .hasGroup());
  }

  @Test
  void missingModelFilesAreWarningsNotFatal() {
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("sales-edw");
    group.getDataVaultModelFiles().add("${PROJECT_HOME}/models/missing.hdv");
    group.getBusinessVaultModelFiles().add("${PROJECT_HOME}/models/missing.hbv");

    EdwJourneySnapshot snapshot = EdwJourneySnapshotBuilder.build(group, new Variables(), null);

    assertEquals("sales-edw", snapshot.groupName());
    assertEquals(1, snapshot.dataVaultModels().size());
    assertEquals("missing", snapshot.dataVaultModels().get(0).displayName());
    assertTrue(snapshot.dataVaultModels().get(0).tableNames().isEmpty());
    assertEquals(1, snapshot.businessVaultModels().size());
    assertFalse(snapshot.warnings().isEmpty());
  }
}
