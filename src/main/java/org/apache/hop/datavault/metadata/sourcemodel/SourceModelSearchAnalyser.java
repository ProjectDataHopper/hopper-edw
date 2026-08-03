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
import org.apache.hop.core.search.BaseSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvNote;

@SearchableAnalyserPlugin(
    id = "SourceModelSearchAnalyser",
    name = "Search in source model metadata")
public class SourceModelSearchAnalyser extends BaseSearchableAnalyser<SourceModel>
    implements ISearchableAnalyser<SourceModel> {

  @Override
  public Class<SourceModel> getSearchableClass() {
    return SourceModel.class;
  }

  @Override
  public List<ISearchResult> search(ISearchable<SourceModel> searchable, ISearchQuery searchQuery) {
    SourceModel model = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (model == null) {
      return results;
    }

    matchProperty(searchable, results, searchQuery, "source model name", model.getName(), null);
    matchProperty(
        searchable, results, searchQuery, "source model description", model.getDescription(), null);

    if (model.getConfiguration() != null) {
      matchObjectFields(
          searchable,
          results,
          searchQuery,
          model.getConfiguration(),
          "source model configuration property",
          "configuration");
    }

    for (SourceTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      String componentName = table.getName();
      matchProperty(
          searchable, results, searchQuery, "source table name", table.getName(), componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "source table schema",
          table.getSchemaName(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "source table physical name",
          table.getTableName(),
          componentName);
      matchObjectFields(
          searchable, results, searchQuery, table, "source table property", componentName);
    }

    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship == null) {
        continue;
      }
      matchProperty(
          searchable,
          results,
          searchQuery,
          "source relationship name",
          relationship.getName(),
          relationship.getName());
      matchObjectFields(
          searchable,
          results,
          searchQuery,
          relationship,
          "source relationship property",
          relationship.getName());
    }

    for (SourceQuery query : model.getQueries()) {
      if (query == null) {
        continue;
      }
      String componentName = query.getName();
      matchProperty(
          searchable, results, searchQuery, "source query name", query.getName(), componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "source query description",
          query.getDescription(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "source query driving table",
          query.getDrivingTableName(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "source query where clause",
          query.getWhereClause(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "source query published catalog name",
          query.getPublishedCatalogName(),
          componentName);
      for (SourceQueryColumn column : query.getColumns()) {
        if (column == null) {
          continue;
        }
        matchProperty(
            searchable,
            results,
            searchQuery,
            "source query column alias",
            column.getAlias(),
            componentName);
        matchProperty(
            searchable,
            results,
            searchQuery,
            "source query column name",
            column.getColumnName(),
            componentName);
      }
      matchObjectFields(
          searchable, results, searchQuery, query, "source query property", componentName);
    }

    if (model.getNotes() != null) {
      for (DvNote note : model.getNotes()) {
        if (note != null && !Utils.isEmpty(note.getText())) {
          matchProperty(
              searchable, results, searchQuery, "source model note text", note.getText(), null);
        }
      }
    }

    return results;
  }
}
