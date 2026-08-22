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
package org.hopper.edw.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;
import org.apache.hop.core.util.Utils;

@SearchableAnalyserPlugin(
    id = "DataVaultModelSearchAnalyser",
    name = "Search in Data Vault model metadata")
public class DataVaultModelSearchAnalyser extends BaseSearchableAnalyser<DataVaultModel>
    implements ISearchableAnalyser<DataVaultModel> {

  @Override
  public Class<DataVaultModel> getSearchableClass() {
    return DataVaultModel.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<DataVaultModel> searchable, ISearchQuery searchQuery) {
    DataVaultModel model = searchable.getSearchableObject();

    List<ISearchResult> results = new ArrayList<>();

    matchProperty(searchable, results, searchQuery, "data vault model name", model.getName(), null);
    matchProperty(
        searchable,
        results,
        searchQuery,
        "data vault model description",
        model.getDescription(),
        null);

    // The embedded Data Vault configuration and its properties
    //
    DataVaultConfiguration configuration = model.getConfigurationOrDefault();
    matchObjectFields(
        searchable,
        results,
        searchQuery,
        configuration,
        "data vault configuration property",
        "configuration");

    // The tables (Hubs, Links and Satellites) and their properties
    //
    for (IDvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }

      String componentName = table.getName();
      if (Utils.isEmpty(componentName)) {
        componentName = table.getTableName();
      }

      matchProperty(
          searchable,
          results,
          searchQuery,
          "data vault table name",
          table.getName(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "data vault table physical name",
          table.getTableName(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "data vault table description",
          table.getDescription(),
          componentName);

      DvTableType tableType = table.getTableType();
      if (tableType != null) {
        matchProperty(
            searchable,
            results,
            searchQuery,
            "data vault table type",
            tableType.name(),
            componentName);
      }

      matchObjectFields(
          searchable, results, searchQuery, table, "data vault table property", componentName);
    }

    // Canvas notes
    //
    if (model.getNotes() != null) {
      for (DvNote note : model.getNotes()) {
        if (note == null) {
          continue;
        }
        matchProperty(
            searchable, results, searchQuery, "data vault note text", note.getText(), null);
      }
    }

    return results;
  }
}
