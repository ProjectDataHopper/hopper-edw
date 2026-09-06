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
package org.hopper.edw.datavault.documentation.writers;

import java.text.SimpleDateFormat;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.model.SearchEntry;
import org.hopper.edw.datavault.documentation.render.DocumentationIo;
import org.hopper.edw.datavault.documentation.render.JsonStrings;

/** Writes {@code assets/js/search-index.js} as a global (file:// safe). */
public final class SearchIndexWriter {

  private SearchIndexWriter() {}

  public static void write(DocumentationSite site) throws HopException {
    String generated =
        site.getGeneratedAt() == null
            ? ""
            : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(site.getGeneratedAt());
    StringBuilder js = new StringBuilder();
    js.append("window.HOP_DOC_INDEX = {")
        .append("\"generatedAt\":")
        .append(JsonStrings.quote(generated))
        .append(",\"projectName\":")
        .append(JsonStrings.quote(site.getProjectName()))
        .append(",\"entries\":[\n");
    boolean first = true;
    for (SearchEntry entry : site.getSearchEntries()) {
      if (!first) {
        js.append(",\n");
      }
      first = false;
      js.append("  {\"id\":")
          .append(JsonStrings.quote(entry.getId()))
          .append(",\"kind\":")
          .append(JsonStrings.quote(entry.getKind() == null ? "" : entry.getKind().indexKey()))
          .append(",\"name\":")
          .append(JsonStrings.quote(entry.getName()))
          .append(",\"aliases\":[");
      boolean firstAlias = true;
      for (String alias : entry.getAliases()) {
        if (!firstAlias) {
          js.append(',');
        }
        firstAlias = false;
        js.append(JsonStrings.quote(alias));
      }
      js.append("],\"path\":")
          .append(JsonStrings.quote(entry.getPath()))
          .append(",\"parent\":")
          .append(JsonStrings.quote(entry.getParent()))
          .append(",\"description\":")
          .append(JsonStrings.quote(entry.getDescription()))
          .append(",\"keywords\":")
          .append(JsonStrings.quote(entry.getKeywords()))
          .append("}");
    }
    js.append("\n]};\n");
    DocumentationIo.writeUtf8(
        DocumentationIo.child(site.getTargetRoot(), "assets/js/search-index.js"), js.toString());
    site.getResult().setSearchEntries(site.getSearchEntries().size());
  }
}
