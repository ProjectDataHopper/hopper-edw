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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.apache.hop.core.search.SearchQuery;
import org.junit.jupiter.api.Test;

class SourceModelSearchAnalyserTest {

  @Test
  void findsQueryNameAndWhereClause() {
    SourceModel model = new SourceModel();
    model.setName("source-tables-crm");

    SourceQuery query = new SourceQuery("All customer info");
    query.setWhereClause("status = 'ACTIVE'");
    query.setPublishedCatalogName("feed_customer_enriched");
    model.getQueries().add(query);

    SourceModelSearchAnalyser analyser = new SourceModelSearchAnalyser();
    ISearchable<SourceModel> searchable = searchable(model);

    List<ISearchResult> queryHits =
        analyser.search(searchable, new SearchQuery("All customer info", false, false));
    assertFalse(queryHits.isEmpty());
    assertTrue(queryHits.stream().anyMatch(r -> "All customer info".equals(r.getComponent())));

    List<ISearchResult> feedHits =
        analyser.search(searchable, new SearchQuery("feed_customer_enriched", false, false));
    assertFalse(feedHits.isEmpty());

    List<ISearchResult> whereHits =
        analyser.search(searchable, new SearchQuery("ACTIVE", false, false));
    assertFalse(whereHits.isEmpty());
  }

  private static ISearchable<SourceModel> searchable(SourceModel model) {
    return new ISearchable<>() {
      @Override
      public String getLocation() {
        return "test";
      }

      @Override
      public String getName() {
        return model.getName();
      }

      @Override
      public String getType() {
        return "Source Model";
      }

      @Override
      public String getFilename() {
        return model.getFilename();
      }

      @Override
      public SourceModel getSearchableObject() {
        return model;
      }

      @Override
      public ISearchableCallback getSearchCallback() {
        return null;
      }
    };
  }
}
