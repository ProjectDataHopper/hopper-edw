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
package org.hopper.edw.datavault.virtualization.sql;

import lombok.Builder;
import lombok.Getter;

/** Options for planning free SQL against a source model. */
@Getter
@Builder
public class SourceModelSqlOptions {

  public static final int DEFAULT_PREVIEW_LIMIT = 100;

  /** Optional name for the generated pipeline. */
  @Builder.Default private final String pipelineName = "source-model-sql";

  /**
   * When true (default), same-connection DATABASE queries are fully pushed to a single Table Input
   * SQL statement via Calcite RelToSql.
   */
  @Builder.Default private final boolean preferFullPushdown = true;

  /**
   * Soft preview limit applied as Table Input row limit when residual path cannot express LIMIT.
   */
  @Builder.Default private final int previewRowLimit = 0;

  /**
   * Optional JDBC / Hop Server schema name (Source model service). When set, Calcite also accepts
   * qualified tables {@code service.table} (DBeaver active-schema qualification).
   */
  @Builder.Default private final String jdbcSchemaAlias = null;

  public static SourceModelSqlOptions defaults() {
    return SourceModelSqlOptions.builder().build();
  }
}
