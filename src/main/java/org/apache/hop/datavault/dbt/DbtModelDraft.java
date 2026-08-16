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
package org.apache.hop.datavault.dbt;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.datavault.metadata.businessvault.BvSqlColumnNote;
import org.apache.hop.datavault.metadata.businessvault.BvSqlMaterialization;

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
