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

package org.apache.hop.quality.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;
import org.apache.hop.quality.model.DataQualityRule;

@SearchableAnalyserPlugin(
    id = "DataQualityRuleSetMetaSearchableAnalyser",
    name = "Search in data quality rule set metadata")
public class DataQualityRuleSetMetaSearchableAnalyser
    extends BaseMetadataSearchableAnalyser<DataQualityRuleSetMeta>
    implements ISearchableAnalyser<DataQualityRuleSetMeta> {

  @Override
  public Class<DataQualityRuleSetMeta> getSearchableClass() {
    return DataQualityRuleSetMeta.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<DataQualityRuleSetMeta> searchable, ISearchQuery searchQuery) {
    DataQualityRuleSetMeta ruleSet = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (ruleSet == null) {
      return results;
    }

    matchProperty(
        searchable, results, searchQuery, "name", ruleSet.getName(), getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "description",
        ruleSet.getDescription(),
        getMetadataComponent());

    for (DataQualityRule rule : ruleSet.getRules()) {
      if (rule == null) {
        continue;
      }
      String label = rule.getName() != null ? rule.getName() : rule.getId();
      matchProperty(
          searchable, results, searchQuery, "rule id", rule.getId(), getMetadataComponent());
      matchProperty(
          searchable, results, searchQuery, "rule name", rule.getName(), getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "rule description",
          rule.getDescription(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "rule field name",
          rule.getFieldName(),
          getMetadataComponent());
      if (rule.getParameters() != null) {
        for (Map.Entry<String, String> entry : rule.getParameters().entrySet()) {
          matchProperty(
              searchable,
              results,
              searchQuery,
              "rule parameter " + entry.getKey() + " of " + label,
              entry.getValue(),
              getMetadataComponent());
        }
      }
    }

    return results;
  }
}
