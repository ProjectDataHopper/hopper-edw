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
package org.apache.hop.datavault.metadata.targettypemapping;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "TargetTypeMappingMetaSearchableAnalyser",
    name = "Search in target type mapping metadata")
public class TargetTypeMappingMetaSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<TargetTypeMappingMeta>
    implements ISearchableAnalyser<TargetTypeMappingMeta> {

  @Override
  public Class<TargetTypeMappingMeta> getSearchableClass() {
    return TargetTypeMappingMeta.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<TargetTypeMappingMeta> searchable, ISearchQuery searchQuery) {
    TargetTypeMappingMeta mapping = searchable.getSearchableObject();
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
    matchProperty(
        searchable,
        results,
        searchQuery,
        "target database",
        mapping.getTargetDatabase(),
        getMetadataComponent());

    for (TargetTypeMappingRule rule : mapping.getRules()) {
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
          "match field name",
          rule.getMatchFieldNamePattern(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "target sql type",
          rule.getTargetSqlType(),
          getMetadataComponent());
    }

    return results;
  }
}
