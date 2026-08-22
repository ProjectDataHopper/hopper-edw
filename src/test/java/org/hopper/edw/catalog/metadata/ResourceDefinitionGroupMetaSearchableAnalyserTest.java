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
package org.hopper.edw.catalog.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.apache.hop.core.search.SearchQuery;
import org.junit.jupiter.api.Test;

class ResourceDefinitionGroupMetaSearchableAnalyserTest {

  @Test
  void findsModelPathsOnGroup() {
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail-sources");
    group.setDescription("Retail models");
    group.getDataVaultModelFiles().add("${PROJECT_HOME}/models/retail-360.hdv");
    group.getBusinessVaultModelFiles().add("${PROJECT_HOME}/models/retail-360.hbv");

    ResourceDefinitionGroupMetaSearchableAnalyser analyser =
        new ResourceDefinitionGroupMetaSearchableAnalyser();
    ISearchable<ResourceDefinitionGroupMeta> searchable = searchable(group);

    List<ISearchResult> hits =
        analyser.search(searchable, new SearchQuery("retail-360.hdv", false, false));
    assertFalse(hits.isEmpty());

    List<ISearchResult> hbvHits =
        analyser.search(searchable, new SearchQuery("retail-360.hbv", false, false));
    assertFalse(hbvHits.isEmpty());
  }

  private static ISearchable<ResourceDefinitionGroupMeta> searchable(
      ResourceDefinitionGroupMeta group) {
    return new ISearchable<>() {
      @Override
      public String getLocation() {
        return "metadata";
      }

      @Override
      public String getName() {
        return group.getName();
      }

      @Override
      public String getType() {
        return "Resource definition group";
      }

      @Override
      public String getFilename() {
        return null;
      }

      @Override
      public ResourceDefinitionGroupMeta getSearchableObject() {
        return group;
      }

      @Override
      public ISearchableCallback getSearchCallback() {
        return null;
      }
    };
  }
}
