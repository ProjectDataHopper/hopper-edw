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
package org.hopper.edw.datavault.virtualization.calcite;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlException;
import org.hopper.edw.datavault.virtualization.sql.SupportedSqlFeatures;

/**
 * Parses and validates free SQL against a {@link SourceModel}, producing a {@link RelNode} tree.
 */
public final class SourceModelRelPlanner {

  /**
   * Default Calcite schema name for bare table references ({@code FROM order_header}). Also
   * registered as an alias so {@code source.order_header} works.
   */
  public static final String DEFAULT_SCHEMA_NAME = "source";

  private SourceModelRelPlanner() {}

  public record PlannedQuery(RelRoot relRoot, RelNode rel, SourceModelSchema schema) {}

  public static PlannedQuery plan(SourceModel model, String sql) throws SourceModelSqlException {
    return plan(model, sql, null);
  }

  /**
   * @param jdbcSchemaAlias optional JDBC / Hop Server service name so tools can write {@code FROM
   *     service.table} (e.g. DBeaver qualifies with the active schema)
   */
  public static PlannedQuery plan(SourceModel model, String sql, String jdbcSchemaAlias)
      throws SourceModelSqlException {
    if (model == null) {
      throw new SourceModelSqlException("Source model is required for SQL planning");
    }
    if (Utils.isEmpty(sql)) {
      throw new SourceModelSqlException("SQL is empty");
    }

    SourceModelSchema schema = new SourceModelSchema(model);
    SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    // Register under every alias DBeaver / SQL clients may use to qualify tables.
    for (String alias : schemaAliases(model, jdbcSchemaAlias)) {
      rootSchema.add(alias, schema);
    }

    SqlParser.Config parserConfig =
        SqlParser.config()
            .withCaseSensitive(false)
            .withQuotedCasing(org.apache.calcite.avatica.util.Casing.UNCHANGED)
            .withUnquotedCasing(org.apache.calcite.avatica.util.Casing.UNCHANGED);

    // Default path for bare names: "source" sub-schema (same tables as jdbcSchemaAlias).
    SchemaPlus defaultPlus = rootSchema.getSubSchema(DEFAULT_SCHEMA_NAME);
    if (defaultPlus == null && !Utils.isEmpty(jdbcSchemaAlias)) {
      defaultPlus = rootSchema.getSubSchema(jdbcSchemaAlias.trim());
    }
    if (defaultPlus == null) {
      // First registered alias
      for (String alias : schemaAliases(model, jdbcSchemaAlias)) {
        defaultPlus = rootSchema.getSubSchema(alias);
        if (defaultPlus != null) {
          break;
        }
      }
    }

    FrameworkConfig config =
        Frameworks.newConfigBuilder().defaultSchema(defaultPlus).parserConfig(parserConfig).build();

    try (Planner planner = Frameworks.getPlanner(config)) {
      SqlNode parsed = planner.parse(sql.trim());
      SqlNode validated = planner.validate(parsed);
      RelRoot root = planner.rel(validated);
      // Prefer the projected form so SELECT aliases match the result row type.
      RelNode project = root.project();
      RelNode rel = project != null ? project : root.rel;
      return new PlannedQuery(root, rel, schema);
    } catch (SqlParseException e) {
      throw new SourceModelSqlException(
          "SQL parse error: " + e.getMessage() + "\n" + SupportedSqlFeatures.SUMMARY, e);
    } catch (ValidationException e) {
      throw new SourceModelSqlException(
          "SQL validation error: " + e.getMessage() + "\n" + SupportedSqlFeatures.SUMMARY, e);
    } catch (RelConversionException e) {
      throw new SourceModelSqlException(
          "SQL planning error: " + e.getMessage() + "\n" + SupportedSqlFeatures.SUMMARY, e);
    } catch (Exception e) {
      throw new SourceModelSqlException(
          "SQL planning failed: " + e.getMessage() + "\n" + SupportedSqlFeatures.SUMMARY, e);
    }
  }

  /**
   * Calcite schema names under which the same table map is exposed: always {@code source}, plus
   * JDBC service alias and model name when distinct (case-insensitive uniqueness).
   */
  static Set<String> schemaAliases(SourceModel model, String jdbcSchemaAlias) {
    Set<String> aliases = new LinkedHashSet<>();
    aliases.add(DEFAULT_SCHEMA_NAME);
    addAlias(aliases, jdbcSchemaAlias);
    if (model != null) {
      addAlias(aliases, model.getName());
    }
    return aliases;
  }

  private static void addAlias(Set<String> aliases, String name) {
    if (Utils.isEmpty(name)) {
      return;
    }
    String trimmed = name.trim();
    for (String existing : aliases) {
      if (existing.equalsIgnoreCase(trimmed)) {
        return;
      }
    }
    aliases.add(trimmed);
    // Calcite schema lookup can be case-sensitive depending on config; keep a lower-case twin
    // when the alias has mixed case so unquoted identifiers still resolve.
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (!lower.equals(trimmed)) {
      boolean hasLower = false;
      for (String existing : aliases) {
        if (existing.equals(lower)) {
          hasLower = true;
          break;
        }
      }
      if (!hasLower) {
        aliases.add(lower);
      }
    }
  }
}
