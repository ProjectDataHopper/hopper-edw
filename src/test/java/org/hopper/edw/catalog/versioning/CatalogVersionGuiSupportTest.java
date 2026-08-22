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
package org.hopper.edw.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.Test;

class CatalogVersionGuiSupportTest {

  @Test
  void listGroupNamesPreferringConnection_putsMatchingGroupsFirst() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    ResourceDefinitionGroupMeta matching = new ResourceDefinitionGroupMeta("retail-sources");
    matching.setDataCatalogConnection("local-catalog");
    ResourceDefinitionGroupMeta other = new ResourceDefinitionGroupMeta("other-group");
    other.setDataCatalogConnection("other-catalog");
    metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).save(other);
    metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).save(matching);

    List<String> names =
        CatalogVersionGuiSupport.listGroupNamesPreferringConnection(
            "local-catalog", new Variables(), metadataProvider);

    assertEquals(2, names.size());
    assertEquals("retail-sources", names.getFirst());
    assertTrue(names.contains("other-group"));
  }

  @Test
  void listGroupNamesPreferringConnection_emptyProvider() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    List<String> names =
        CatalogVersionGuiSupport.listGroupNamesPreferringConnection(
            "local-catalog", new Variables(), metadataProvider);
    assertTrue(names.isEmpty());
  }
}
