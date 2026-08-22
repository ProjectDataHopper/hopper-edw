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

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.virtualization.calcite.SourceModelRelPlanner;
import org.hopper.edw.datavault.virtualization.generate.RelToPipelineGenerator;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Entry point for free SQL against a {@link SourceModel}: parse → plan → Hop pipeline generation.
 */
public final class SourceModelSqlEngine {

  private SourceModelSqlEngine() {}

  /** Validates SQL only (parse + schema check); does not generate a pipeline. */
  public static void validate(SourceModel model, String sql, IVariables variables)
      throws SourceModelSqlException {
    String resolved = resolveSql(sql, variables);
    SourceModelRelPlanner.plan(model, resolved, null);
  }

  public static SourceModelSqlPlan plan(
      SourceModel model,
      String sql,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceModelSqlOptions options)
      throws SourceModelSqlException {
    if (metadataProvider == null) {
      throw new SourceModelSqlException(
          "Metadata provider is required for SQL pipeline generation");
    }
    String resolved = resolveSql(sql, variables);
    SourceModelSqlOptions opts = options != null ? options : SourceModelSqlOptions.defaults();
    SourceModelRelPlanner.PlannedQuery planned =
        SourceModelRelPlanner.plan(model, resolved, opts.getJdbcSchemaAlias());
    return RelToPipelineGenerator.generate(planned.rel(), model, variables, metadataProvider, opts);
  }

  public static SourceModelSqlPlan plan(
      SourceModel model, String sql, IVariables variables, IHopMetadataProvider metadataProvider)
      throws SourceModelSqlException {
    return plan(model, sql, variables, metadataProvider, SourceModelSqlOptions.defaults());
  }

  private static String resolveSql(String sql, IVariables variables) {
    if (Utils.isEmpty(sql)) {
      return sql;
    }
    return variables != null ? variables.resolve(sql) : sql;
  }
}
