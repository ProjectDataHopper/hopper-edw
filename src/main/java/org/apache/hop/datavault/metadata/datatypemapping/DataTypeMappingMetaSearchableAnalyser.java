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
package org.apache.hop.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "DataTypeMappingMetaSearchableAnalyser",
    name = "Search in data type mapping metadata")
public class DataTypeMappingMetaSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<DataTypeMappingMeta>
    implements ISearchableAnalyser<DataTypeMappingMeta> {

  @Override
  public Class<DataTypeMappingMeta> getSearchableClass() {
    return DataTypeMappingMeta.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<DataTypeMappingMeta> searchable, ISearchQuery searchQuery) {
    DataTypeMappingMeta mapping = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (mapping == null) {
      return results;
    }

    matchProperty(
        searchable, results, searchQuery, "name", mapping.getName(), getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "description",
        mapping.getDescription(),
        getMetadataComponent());

    for (DataTypeMappingRule rule : mapping.getRules()) {
      if (rule == null) {
        continue;
      }
      matchProperty(
          searchable, results, searchQuery, "rule id", rule.getId(), getMetadataComponent());
      matchProperty(
          searchable, results, searchQuery, "rule name", rule.getName(), getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "match hop type",
          rule.getMatchHopType(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "match source type",
          rule.getMatchSourceDataTypePattern(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "match field name",
          rule.getMatchFieldNamePattern(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "target length",
          rule.getTargetLength(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "conversion mask",
          rule.getConversion().getConversionMask(),
          getMetadataComponent());
    }

    return results;
  }
}
