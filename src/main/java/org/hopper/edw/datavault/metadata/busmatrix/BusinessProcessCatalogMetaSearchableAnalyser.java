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
package org.hopper.edw.datavault.metadata.busmatrix;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "BusinessProcessCatalogMetaSearchableAnalyser",
    name = "Search in business process catalog metadata")
public class BusinessProcessCatalogMetaSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<BusinessProcessCatalogMeta>
    implements ISearchableAnalyser<BusinessProcessCatalogMeta> {

  @Override
  public Class<BusinessProcessCatalogMeta> getSearchableClass() {
    return BusinessProcessCatalogMeta.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<BusinessProcessCatalogMeta> searchable, ISearchQuery searchQuery) {
    BusinessProcessCatalogMeta catalog = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (catalog == null) {
      return results;
    }
    matchProperty(
        searchable, results, searchQuery, "name", catalog.getName(), getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "description",
        catalog.getDescription(),
        getMetadataComponent());
    matchTerms(searchable, results, searchQuery, "domain", catalog.getDomains());
    matchTerms(searchable, results, searchQuery, "level 1", catalog.getLevel1());
    matchTerms(searchable, results, searchQuery, "level 2", catalog.getLevel2());
    matchTerms(searchable, results, searchQuery, "level 3", catalog.getLevel3());
    return results;
  }

  private void matchTerms(
      ISearchable<BusinessProcessCatalogMeta> searchable,
      List<ISearchResult> results,
      ISearchQuery searchQuery,
      String label,
      List<BusinessProcessTerm> terms) {
    if (terms == null) {
      return;
    }
    for (BusinessProcessTerm term : terms) {
      if (term == null) {
        continue;
      }
      matchProperty(
          searchable,
          results,
          searchQuery,
          label + " name",
          term.getName(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          label + " description",
          term.getDescription(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          label + " parent",
          term.getParentName(),
          getMetadataComponent());
    }
  }
}
