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
package org.apache.hop.datavault.metadata.composite;

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQueryGenerationSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQuerySqlGenerator;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Loads a live {@link SourceQuery} for a {@link DvCompositeSource} and resolves SQL when possible.
 */
public final class DvCompositeSourceResolver {

  private DvCompositeSourceResolver() {}

  public record ResolvedComposite(
      SourceModel model,
      SourceQuery query,
      SourceQueryGenerationMode effectiveMode,
      String sharedDatabaseName,
      DatabaseMeta databaseMeta,
      String sql,
      boolean usedCachedSql) {}

  public static ResolvedComposite resolve(
      DvCompositeSource composite, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (composite == null) {
      throw new HopException("Composite source is required");
    }
    String modelFile =
        variables != null
            ? variables.resolve(composite.getSourceModelFilename())
            : composite.getSourceModelFilename();
    String queryName =
        variables != null
            ? variables.resolve(composite.getSourceQueryName())
            : composite.getSourceQueryName();

    SourceModel model = null;
    SourceQuery query = null;
    HopException loadError = null;
    if (!Utils.isEmpty(modelFile) && !Utils.isEmpty(queryName)) {
      try {
        model = SourceModelLoadSupport.load(modelFile, variables, metadataProvider);
        query = model.findQuery(queryName);
        if (query == null) {
          loadError =
              new HopException(
                  "Source query '" + queryName + "' not found in source model '" + modelFile + "'");
        }
      } catch (HopException e) {
        loadError = e;
      }
    }

    if (query != null && model != null) {
      SourceQueryGenerationMode mode =
          SourceQueryGenerationSupport.resolveEffectiveMode(model, query);
      String sharedDb = SourceQueryGenerationSupport.resolveSharedDatabaseName(model, query);
      DatabaseMeta databaseMeta = null;
      String sql = null;
      if (mode == SourceQueryGenerationMode.SQL && !Utils.isEmpty(sharedDb)) {
        databaseMeta =
            metadataProvider
                .getSerializer(DatabaseMeta.class)
                .load(variables != null ? variables.resolve(sharedDb) : sharedDb);
        if (databaseMeta == null) {
          throw new HopException("Database connection '" + sharedDb + "' not found");
        }
        sql = SourceQuerySqlGenerator.generate(model, query, databaseMeta, variables);
      }
      return new ResolvedComposite(model, query, mode, sharedDb, databaseMeta, sql, false);
    }

    // Fallback: cached SQL only.
    if (!Utils.isEmpty(composite.getGeneratedSql())) {
      String sql =
          variables != null
              ? variables.resolve(composite.getGeneratedSql())
              : composite.getGeneratedSql();
      return new ResolvedComposite(
          null, null, SourceQueryGenerationMode.SQL, null, null, sql, true);
    }

    if (loadError != null) {
      throw loadError;
    }
    throw new HopException(
        "Composite source is missing source model filename / query name and has no cached SQL");
  }

  /**
   * Resolves SQL for Table Input generation. Prefers live model generation; falls back to cache.
   * Requires SQL mode (or cached SQL).
   */
  public static ResolvedComposite resolveForSql(
      DvCompositeSource composite, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    ResolvedComposite resolved = resolve(composite, variables, metadataProvider);
    if (Utils.isEmpty(resolved.sql())) {
      throw new HopException(
          "Composite source does not resolve to single-connection SQL"
              + (resolved.usedCachedSql() ? " (cache empty)" : " (pipeline mode)"));
    }
    if (resolved.databaseMeta() == null && !resolved.usedCachedSql()) {
      throw new HopException("Composite SQL resolution did not load a database connection");
    }
    return resolved;
  }
}
