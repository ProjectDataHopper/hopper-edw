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
package org.apache.hop.datavault.metadata.dimensional.dbimport;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;

/** Result of importing database tables into a dimensional model. */
@Getter
public final class DmDatabaseImportResult {

  private final List<IDmTable> importedTables;
  private final List<String> warnings;
  private final List<String> errors;

  public DmDatabaseImportResult(
      List<IDmTable> importedTables, List<String> warnings, List<String> errors) {
    this.importedTables = importedTables != null ? List.copyOf(importedTables) : List.of();
    this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
    this.errors = errors != null ? List.copyOf(errors) : List.of();
  }

  public static DmDatabaseImportResult empty() {
    return new DmDatabaseImportResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }

  public List<IDmTable> getImportedTablesOrEmpty() {
    return importedTables != null ? importedTables : List.of();
  }

  public List<String> getWarningsOrEmpty() {
    return warnings != null ? warnings : List.of();
  }

  public List<String> getErrorsOrEmpty() {
    return errors != null ? errors : List.of();
  }
}
