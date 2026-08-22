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
package org.apache.hop.datavault.dbt;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.datavault.metadata.jinja.JinjaMacroLibraryMeta;

@Getter
@Setter
public class DbtImportResult {

  private int importedTables;
  private int skippedTables;
  private int replacedTables;
  private JinjaMacroLibraryMeta library;
  private final List<String> writtenModelFiles = new ArrayList<>();
  private final List<DbtImportIssue> issues = new ArrayList<>();

  public String reportText() {
    StringBuilder sb = new StringBuilder();
    sb.append("Imported ")
        .append(importedTables)
        .append(", replaced ")
        .append(replacedTables)
        .append(", skipped ")
        .append(skippedTables)
        .append('.');
    if (library != null) {
      sb.append(" Macros library: ").append(library.getName()).append('.');
    }
    if (!writtenModelFiles.isEmpty()) {
      sb.append(" Wrote ").append(writtenModelFiles.size()).append(" .hbv file(s).");
    }
    if (!issues.isEmpty()) {
      sb.append("\n");
      for (DbtImportIssue issue : issues) {
        sb.append('\n').append(issue.severity()).append(" [").append(issue.code()).append("] ");
        if (issue.modelName() != null) {
          sb.append(issue.modelName()).append(": ");
        }
        sb.append(issue.message());
      }
    }
    return sb.toString();
  }
}
