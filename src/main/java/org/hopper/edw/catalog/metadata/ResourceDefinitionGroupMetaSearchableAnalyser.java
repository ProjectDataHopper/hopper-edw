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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "ResourceDefinitionGroupMetaSearchableAnalyser",
    name = "Search in resource definition group metadata")
public class ResourceDefinitionGroupMetaSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<ResourceDefinitionGroupMeta>
    implements ISearchableAnalyser<ResourceDefinitionGroupMeta> {

  @Override
  public Class<ResourceDefinitionGroupMeta> getSearchableClass() {
    return ResourceDefinitionGroupMeta.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<ResourceDefinitionGroupMeta> searchable, ISearchQuery searchQuery) {
    ResourceDefinitionGroupMeta group = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (group == null) {
      return results;
    }

    matchProperty(
        searchable, results, searchQuery, "name", group.getName(), getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "description",
        group.getDescription(),
        getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "data catalog connection",
        group.getDataCatalogConnection(),
        getMetadataComponent());

    for (String path : group.getDataVaultModelFiles()) {
      matchProperty(
          searchable, results, searchQuery, "data vault model file", path, getMetadataComponent());
    }
    for (String path : group.getBusinessVaultModelFiles()) {
      matchProperty(
          searchable,
          results,
          searchQuery,
          "business vault model file",
          path,
          getMetadataComponent());
    }
    for (String path : group.getDimensionalModelFiles()) {
      matchProperty(
          searchable, results, searchQuery, "dimensional model file", path, getMetadataComponent());
    }

    return results;
  }
}
