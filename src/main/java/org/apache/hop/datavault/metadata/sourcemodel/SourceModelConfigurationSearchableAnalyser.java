/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "SourceModelConfigurationSearchableAnalyser",
    name = "Search in source model configuration metadata")
public class SourceModelConfigurationSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<SourceModelConfiguration>
    implements ISearchableAnalyser<SourceModelConfiguration> {

  @Override
  public Class<SourceModelConfiguration> getSearchableClass() {
    return SourceModelConfiguration.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<SourceModelConfiguration> searchable, ISearchQuery searchQuery) {
    SourceModelConfiguration configuration = searchable.getSearchableObject();
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
        "default database",
        configuration.getDefaultDatabase(),
        getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "catalog connection",
        configuration.getCatalogConnection(),
        getMetadataComponent());
    return results;
  }
}
