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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "BusinessVaultConfigurationSearchableAnalyser",
    name = "Search in Business Vault configuration metadata")
public class BusinessVaultConfigurationSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<BusinessVaultConfiguration>
    implements ISearchableAnalyser<BusinessVaultConfiguration> {

  @Override
  public Class<BusinessVaultConfiguration> getSearchableClass() {
    return BusinessVaultConfiguration.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<BusinessVaultConfiguration> searchable, ISearchQuery searchQuery) {
    BusinessVaultConfiguration configuration = searchable.getSearchableObject();
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
