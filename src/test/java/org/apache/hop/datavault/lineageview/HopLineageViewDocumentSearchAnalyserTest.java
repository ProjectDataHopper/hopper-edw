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
package org.apache.hop.datavault.lineageview;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.apache.hop.core.search.SearchQuery;
import org.junit.jupiter.api.Test;

class HopLineageViewDocumentSearchAnalyserTest {

  @Test
  void findsBackendAndLogicalTable() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setName("f_orders upstream");
    document.setBackendName("local-marquez");
    document.setLogicalTable("f_orders");
    document.setDatasetName("public.f_orders");
    document.setJobName("dm/retail-pos/f_orders");

    HopLineageViewDocumentSearchAnalyser analyser = new HopLineageViewDocumentSearchAnalyser();
    ISearchable<HopLineageViewDocument> searchable =
        new ISearchable<>() {
          @Override
          public String getLocation() {
            return "project";
          }

          @Override
          public String getName() {
            return document.getName();
          }

          @Override
          public String getType() {
            return "Hop Lineage View";
          }

          @Override
          public String getFilename() {
            return "view.hlv";
          }

          @Override
          public HopLineageViewDocument getSearchableObject() {
            return document;
          }

          @Override
          public ISearchableCallback getSearchCallback() {
            return null;
          }
        };

    List<ISearchResult> backendHits =
        analyser.search(searchable, new SearchQuery("local-marquez", false, false));
    assertFalse(backendHits.isEmpty());
    List<ISearchResult> tableHits =
        analyser.search(searchable, new SearchQuery("f_orders", false, false));
    assertFalse(tableHits.isEmpty());
  }
}
