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
package org.apache.hop.datavault.lineageview;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "HopLineageViewDocumentSearchAnalyser",
    name = "Search in Hop Lineage View files")
public class HopLineageViewDocumentSearchAnalyser
    extends BaseSearchableAnalyser<HopLineageViewDocument>
    implements ISearchableAnalyser<HopLineageViewDocument> {

  @Override
  public Class<HopLineageViewDocument> getSearchableClass() {
    return HopLineageViewDocument.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<HopLineageViewDocument> searchable, ISearchQuery searchQuery) {
    HopLineageViewDocument document = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (document == null) {
      return results;
    }
    matchProperty(searchable, results, searchQuery, "lineage view name", document.getName(), null);
    matchProperty(
        searchable,
        results,
        searchQuery,
        "lineage view description",
        document.getDescription(),
        null);
    matchProperty(
        searchable, results, searchQuery, "lineage backend", document.getBackendName(), null);
    matchProperty(
        searchable, results, searchQuery, "logical table", document.getLogicalTable(), null);
    matchProperty(
        searchable, results, searchQuery, "dataset name", document.getDatasetName(), null);
    matchProperty(searchable, results, searchQuery, "job name", document.getJobName(), null);
    matchProperty(searchable, results, searchQuery, "model name", document.getModelName(), null);
    matchProperty(
        searchable, results, searchQuery, "model file", document.getModelFilename(), null);
    return results;
  }
}
