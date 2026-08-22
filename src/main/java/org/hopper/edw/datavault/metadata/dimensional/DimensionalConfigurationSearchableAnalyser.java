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
package org.hopper.edw.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "DimensionalConfigurationSearchableAnalyser",
    name = "Search in dimensional configuration metadata")
public class DimensionalConfigurationSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<DimensionalConfiguration>
    implements ISearchableAnalyser<DimensionalConfiguration> {

  @Override
  public Class<DimensionalConfiguration> getSearchableClass() {
    return DimensionalConfiguration.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<DimensionalConfiguration> searchable, ISearchQuery searchQuery) {
    DimensionalConfiguration configuration = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (configuration == null) {
      return results;
    }
    matchProperty(
        searchable, results, searchQuery, "name", configuration.getName(), getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "description",
        configuration.getDescription(),
        getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "target database",
        configuration.getTargetDatabase(),
        getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "data catalog",
        configuration.getDataCatalogConnection(),
        getMetadataComponent());
    return results;
  }
}
