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
package org.apache.hop.quality.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.apache.hop.core.search.SearchQuery;
import org.apache.hop.quality.model.DataQualityRule;
import org.junit.jupiter.api.Test;

class DataQualityRuleSetMetaSearchableAnalyserTest {

  @Test
  void findsRuleFieldName() {
    DataQualityRuleSetMeta ruleSet = new DataQualityRuleSetMeta("source-quality");
    DataQualityRule rule = new DataQualityRule();
    rule.setId("not-null-email");
    rule.setName("Email required");
    rule.setFieldName("email_address");
    ruleSet.getRules().add(rule);

    DataQualityRuleSetMetaSearchableAnalyser analyser =
        new DataQualityRuleSetMetaSearchableAnalyser();
    ISearchable<DataQualityRuleSetMeta> searchable =
        new ISearchable<>() {
          @Override
          public String getLocation() {
            return "metadata";
          }

          @Override
          public String getName() {
            return ruleSet.getName();
          }

          @Override
          public String getType() {
            return "Data quality rule set";
          }

          @Override
          public String getFilename() {
            return null;
          }

          @Override
          public DataQualityRuleSetMeta getSearchableObject() {
            return ruleSet;
          }

          @Override
          public ISearchableCallback getSearchCallback() {
            return null;
          }
        };

    List<ISearchResult> hits =
        analyser.search(searchable, new SearchQuery("email_address", false, false));
    assertFalse(hits.isEmpty());
  }
}
