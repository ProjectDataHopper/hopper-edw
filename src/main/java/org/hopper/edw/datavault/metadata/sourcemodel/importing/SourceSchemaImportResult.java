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
package org.hopper.edw.datavault.metadata.sourcemodel.importing;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.jspecify.annotations.NonNull;

/** Outcome of a source-schema import (tables, relationships, warnings, errors). */
@Getter
public class SourceSchemaImportResult {

  private final List<SourceTable> importedTables;
  private final List<SourceRelationship> importedRelationships;
  private final List<String> publishedCatalogNames;
  private final List<String> warnings;
  private final List<String> errors;

  public SourceSchemaImportResult(
      List<SourceTable> importedTables,
      List<SourceRelationship> importedRelationships,
      List<String> publishedCatalogNames,
      List<String> warnings,
      List<String> errors) {
    this.importedTables = importedTables != null ? importedTables : new ArrayList<>();
    this.importedRelationships =
        importedRelationships != null ? importedRelationships : new ArrayList<>();
    this.publishedCatalogNames =
        publishedCatalogNames != null ? publishedCatalogNames : new ArrayList<>();
    this.warnings = warnings != null ? warnings : new ArrayList<>();
    this.errors = errors != null ? errors : new ArrayList<>();
  }

  public static SourceSchemaImportResult empty() {
    return new SourceSchemaImportResult(List.of(), List.of(), List.of(), List.of(), List.of());
  }

  public @NonNull List<SourceTable> getImportedTablesOrEmpty() {
    return importedTables;
  }

  public @NonNull List<SourceRelationship> getImportedRelationshipsOrEmpty() {
    return importedRelationships;
  }

  public @NonNull List<String> getPublishedCatalogNamesOrEmpty() {
    return publishedCatalogNames;
  }

  public @NonNull List<String> getWarningsOrEmpty() {
    return warnings;
  }

  public @NonNull List<String> getErrorsOrEmpty() {
    return errors;
  }
}
