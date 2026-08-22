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
package org.apache.hop.catalog.metadata;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.spi.IDataCatalog;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "DataCatalogMetaSearchableAnalyser",
    name = "Search in data catalog connection metadata")
public class DataCatalogMetaSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<DataCatalogMeta>
    implements ISearchableAnalyser<DataCatalogMeta> {

  @Override
  public Class<DataCatalogMeta> getSearchableClass() {
    return DataCatalogMeta.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<DataCatalogMeta> searchable, ISearchQuery searchQuery) {
    DataCatalogMeta meta = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (meta == null) {
      return results;
    }

    matchProperty(searchable, results, searchQuery, "name", meta.getName(), getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "description",
        meta.getDescription(),
        getMetadataComponent());

    IDataCatalog catalog = meta.getCatalogOrDefault();
    if (catalog != null) {
      matchObjectFields(
          searchable,
          results,
          searchQuery,
          catalog,
          "data catalog property",
          getMetadataComponent());
    }

    return results;
  }
}
