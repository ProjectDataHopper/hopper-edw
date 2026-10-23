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
package org.hopper.edw.datavault.virtualization.dimensional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.logical.LogicalSort;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.dimensional.service.DimensionalMaskingPolicy;
import org.hopper.edw.datavault.virtualization.generate.DialectSqlSupport;

/**
 * Parses a read-only statement against a dimensional JDBC catalog, applies the masking policy, and
 * rewrites logical table names to the warehouse schema and table.
 */
public final class DimensionalSqlEngine {

  private DimensionalSqlEngine() {}

  public static DimensionalSqlPlan plan(
      DimensionalJdbcCatalog catalog, String sql, String jdbcSchemaAlias, DimensionalMaskingPolicy policy)
      throws DimensionalSqlException {
    if (catalog == null) {
      throw new DimensionalSqlException("A dimensional model is required");
    }
    if (Utils.isEmpty(sql) || sql.isBlank()) {
      throw new DimensionalSqlException("SQL is empty");
    }
    if (DimensionalSqlLiteralRedactor.hasExtraStatement(sql)) {
      throw DimensionalSqlException.rejected(
          "Only a single SELECT statement is accepted.");
    }
    DimensionalCalciteSchema schema = new DimensionalCalciteSchema(catalog);
    SchemaPlus root = Frameworks.createRootSchema(true);
    Set<String> aliases = schemaAliases(jdbcSchemaAlias);
    for (String alias : aliases) {
      root.add(alias, schema);
    }
    SchemaPlus defaultSchema = null;
    for (String alias : aliases) {
      defaultSchema = root.getSubSchema(alias);
      if (defaultSchema != null) {
        break;
      }
    }
    SqlParser.Config parserConfig =
        SqlParser.config()
            .withCaseSensitive(false)
            .withQuotedCasing(Casing.UNCHANGED)
            .withUnquotedCasing(Casing.UNCHANGED);
    FrameworkConfig config =
        Frameworks.newConfigBuilder()
            .defaultSchema(defaultSchema)
            .parserConfig(parserConfig)
            .build();
    try (Planner planner = Frameworks.getPlanner(config)) {
      SqlNode parsed = planner.parse(sql.trim());
      SqlKind kind = parsed.getKind();
      if (!isReadOnly(kind)) {
        throw DimensionalSqlException.rejected(
            "Only a SELECT statement is accepted. Data changes are rejected.");
      }
      SqlNode validated = planner.validate(parsed);
      RelRoot relRoot = planner.rel(validated);
      RelNode rel = relRoot.rel;
      if (relRoot.collation != null
          && !relRoot.collation.getFieldCollations().isEmpty()
          && !(rel instanceof LogicalSort)) {
        rel = LogicalSort.create(rel, relRoot.collation, null, null);
      }
      DimensionalMaskingPolicy effective =
          policy != null ? policy : DimensionalMaskingPolicy.SAFE;
      if (effective == DimensionalMaskingPolicy.SAFE) {
        DimensionalMaskingPolicyChecker.checkSafe(rel);
      }
      RelNode projected = relRoot.project(true);
      boolean[] masked = DimensionalMaskingPolicyChecker.maskedOutputs(projected);
      String[] patterns = DimensionalMaskingPolicyChecker.outputPatterns(projected);
      RelNode sqlRel = projected;
      if (rel instanceof org.apache.calcite.rel.core.Sort sort
          && !(projected instanceof org.apache.calcite.rel.core.Sort)) {
        sqlRel = LogicalSort.create(projected, sort.getCollation(), sort.offset, sort.fetch);
      } else if (relRoot.collation != null
          && !relRoot.collation.getFieldCollations().isEmpty()
          && !(projected instanceof org.apache.calcite.rel.core.Sort)) {
        sqlRel = LogicalSort.create(projected, relRoot.collation, null, null);
      }
      SqlDialect dialect = DialectSqlSupport.dialectFor(catalog.targetDatabase());
      String physical =
          new DimensionalRelToSqlConverter(dialect)
              .visitRoot(sqlRel)
              .asStatement()
              .toSqlString(dialect)
              .getSql();
      physical = expandStar(physical, projected, dialect);
      return new DimensionalSqlPlan(physical, masked, patterns);
    } catch (DimensionalSqlException e) {
      throw e;
    } catch (SqlParseException e) {
      throw new DimensionalSqlException("SQL parse error: " + e.getMessage(), e);
    } catch (ValidationException e) {
      throw new DimensionalSqlException("SQL validation error: " + e.getMessage(), e);
    } catch (RelConversionException e) {
      throw new DimensionalSqlException("SQL planning error: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new DimensionalSqlException(
          "SQL planning failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
          e);
    }
  }

  /**
   * Calcite prints a full projection as {@code SELECT *}. Replace that star with the modeled
   * column list so warehouse columns that are not on the model stay out of the result.
   */
  private static String expandStar(String sql, RelNode projected, SqlDialect dialect) {
    if (sql == null || !sql.matches("(?is)[\\s\\S]*SELECT\\s+\\*.*")) {
      return sql;
    }
    StringBuilder columns = new StringBuilder();
    for (RelDataTypeField field : projected.getRowType().getFieldList()) {
      if (columns.length() > 0) {
        columns.append(", ");
      }
      columns.append(dialect.quoteIdentifier(field.getName()));
    }
    return sql.replaceFirst("(?is)SELECT\\s+\\*", "SELECT " + columns);
  }

  private static boolean isReadOnly(SqlKind kind) {
    return kind == SqlKind.SELECT
        || kind == SqlKind.ORDER_BY
        || kind == SqlKind.WITH
        || kind == SqlKind.UNION
        || kind == SqlKind.INTERSECT
        || kind == SqlKind.EXCEPT;
  }

  private static Set<String> schemaAliases(String jdbcSchemaAlias) {
    Set<String> aliases = new LinkedHashSet<>();
    aliases.add("dimensional");
    if (!Utils.isEmpty(jdbcSchemaAlias)) {
      String trimmed = jdbcSchemaAlias.trim();
      aliases.add(trimmed);
      aliases.add(trimmed.toLowerCase(Locale.ROOT));
    }
    return aliases;
  }
}
