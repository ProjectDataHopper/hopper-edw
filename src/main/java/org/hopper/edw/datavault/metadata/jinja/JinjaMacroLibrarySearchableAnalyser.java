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
package org.hopper.edw.datavault.metadata.jinja;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.search.BaseMetadataSearchableAnalyser;
import org.apache.hop.core.search.ISearchQuery;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableAnalyser;
import org.apache.hop.core.search.SearchableAnalyserPlugin;

@SearchableAnalyserPlugin(
    id = "JinjaMacroLibrarySearchableAnalyser",
    name = "Search in Jinja macro library metadata")
public class JinjaMacroLibrarySearchableAnalyser
    extends BaseMetadataSearchableAnalyser<JinjaMacroLibraryMeta>
    implements ISearchableAnalyser<JinjaMacroLibraryMeta> {

  @Override
  public Class<JinjaMacroLibraryMeta> getSearchableClass() {
    return JinjaMacroLibraryMeta.class;
  }

  @Override
  public List<ISearchResult> search(
      ISearchable<JinjaMacroLibraryMeta> searchable, ISearchQuery searchQuery) {
    JinjaMacroLibraryMeta library = searchable.getSearchableObject();
    List<ISearchResult> results = new ArrayList<>();
    if (library == null) {
      return results;
    }
    matchProperty(
        searchable, results, searchQuery, "name", library.getName(), getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "description",
        library.getDescription(),
        getMetadataComponent());
    matchProperty(
        searchable,
        results,
        searchQuery,
        "package",
        library.getPackageName(),
        getMetadataComponent());
    for (JinjaMacroVar var : library.getVars()) {
      if (var == null) {
        continue;
      }
      matchProperty(
          searchable, results, searchQuery, "var name", var.getName(), getMetadataComponent());
    }
    for (JinjaMacroDefinition macro : library.getMacros()) {
      if (macro == null) {
        continue;
      }
      matchProperty(
          searchable, results, searchQuery, "macro name", macro.getName(), getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "macro description",
          macro.getDescription(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "macro origin",
          macro.getOriginPath(),
          getMetadataComponent());
      matchProperty(
          searchable,
          results,
          searchQuery,
          "macro source",
          macro.getJinjaSource(),
          getMetadataComponent());
    }
    return results;
  }
}
