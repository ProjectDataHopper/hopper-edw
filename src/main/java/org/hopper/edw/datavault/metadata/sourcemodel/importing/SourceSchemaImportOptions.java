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

import lombok.Getter;
import lombok.Setter;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;

/** Options collected before importing database tables into a source model. */
@Getter
@Setter
public class SourceSchemaImportOptions {

  private String databaseName;
  private String schemaName;

  /** Optional prefix for canvas/catalog source names. */
  private String sourceNamePrefix;

  /** When true, also create/update catalog {@code DV_SOURCE} records for each imported table. */
  private boolean publishToCatalog = true;

  private String catalogConnectionName;
  private RecordSourceIndicatorOptions recordSourceOptions;

  public static SourceSchemaImportOptions defaults() {
    return new SourceSchemaImportOptions();
  }
}
