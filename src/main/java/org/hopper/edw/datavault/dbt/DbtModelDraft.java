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
package org.hopper.edw.datavault.dbt;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlColumnNote;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlMaterialization;

@Getter
@Setter
public class DbtModelDraft {

  private String name;
  private String tableName;
  private String description;
  private String sqlQuery;
  private String originRelativePath;
  private String originAbsolutePath;
  private String firstLevelFolder;
  private String schemaName;
  private String dbtMaterialized;
  private BvSqlMaterialization materialization = BvSqlMaterialization.VIEW;
  private boolean importable = true;
  private final List<BvSqlColumnNote> columnNotes = new ArrayList<>();
  private final List<DbtImportIssue> issues = new ArrayList<>();

  public String issueSummary() {
    if (issues.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (DbtImportIssue issue : issues) {
      if (sb.length() > 0) {
        sb.append("; ");
      }
      sb.append(issue.summary());
    }
    return sb.toString();
  }
}
