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
package org.hopper.edw.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvNote;

@SearchableAnalyserPlugin(
    id = "DimensionalModelSearchAnalyser",
    name = "Search in dimensional model metadata")
public class DimensionalModelSearchAnalyser extends BaseSearchableAnalyser<DimensionalModel>
    implements ISearchableAnalyser<DimensionalModel> {

  @Override
  public Class<DimensionalModel> getSearchableClass() {
    return DimensionalModel.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<DimensionalModel> searchable, ISearchQuery searchQuery) {
    DimensionalModel model = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (model == null) {
      return results;
    }

    matchProperty(
        searchable, results, searchQuery, "dimensional model name", model.getName(), null);
    matchProperty(
        searchable,
        results,
        searchQuery,
        "dimensional model description",
        model.getDescription(),
        null);

    if (model.getConfiguration() != null) {
      matchObjectFields(
          searchable,
          results,
          searchQuery,
          model.getConfiguration(),
          "dimensional configuration property",
          "configuration");
    }

    for (IDmTable table : model.getTables()) {
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
          "dimensional table name",
          table.getName(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "dimensional table physical name",
          table.getTableName(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "dimensional table description",
          table.getDescription(),
          componentName);
      matchProperty(
          searchable,
          results,
          searchQuery,
          "dimensional table grain",
          table.getGrain(),
          componentName);
      matchDocumentedFields(searchable, results, searchQuery, table, componentName);
      matchObjectFields(
          searchable, results, searchQuery, table, "dimensional table property", componentName);
    }

    if (model.getNotes() != null) {
      for (DvNote note : model.getNotes()) {
        if (note != null) {
          matchProperty(
              searchable, results, searchQuery, "dimensional note text", note.getText(), null);
        }
      }
    }

    return results;
  }

  private void matchDocumentedFields(
      ISearchable<DimensionalModel> searchable,
      List<ISearchResult> results,
      ISearchQuery searchQuery,
      IDmTable table,
      String componentName) {
    if (table instanceof DmDimension dimension) {
      for (DmNaturalKeyField key : dimension.getNaturalKeysOrEmpty()) {
        matchFieldDocumentation(searchable, results, searchQuery, key, componentName);
      }
      for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
        matchFieldDocumentation(searchable, results, searchQuery, attribute, componentName);
      }
    }
    if (table instanceof IDmFactLikeTable fact) {
      for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
        matchFieldDocumentation(searchable, results, searchQuery, measure, componentName);
      }
      for (DmFactDegenerateDimension degenerate : fact.getDegenerateDimensionsOrEmpty()) {
        matchFieldDocumentation(searchable, results, searchQuery, degenerate, componentName);
      }
    }
  }

  private void matchFieldDocumentation(
      ISearchable<DimensionalModel> searchable,
      List<ISearchResult> results,
      ISearchQuery searchQuery,
      IDmDocumentedField field,
      String componentName) {
    if (field == null) {
      return;
    }
    matchProperty(
        searchable,
        results,
        searchQuery,
        "dimensional field name",
        field.getFieldName(),
        componentName);
    DmFieldDocumentation docs = field.getDocumentation();
    if (docs == null) {
      return;
    }
    matchProperty(
        searchable,
        results,
        searchQuery,
        "dimensional field description",
        docs.getDescription(),
        componentName);
    matchProperty(
        searchable,
        results,
        searchQuery,
        "dimensional field notes",
        docs.getNotes(),
        componentName);
    matchProperty(
        searchable,
        results,
        searchQuery,
        "dimensional field requirements",
        docs.getRequirements(),
        componentName);
  }
}
