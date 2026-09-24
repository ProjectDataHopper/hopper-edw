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
package org.hopper.edw.semantic.calcite;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.core.AggregationMethod;
import org.hopper.edw.datavault.virtualization.generate.DialectSqlSupport;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticJoinType;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticRelationship;
import org.hopper.edw.semantic.model.SemanticSelection;
import org.hopper.edw.semantic.model.SemanticSelectionField;

/**
 * Compiles a {@link SemanticSelection} to warehouse SQL: structured SQL over logical entity names,
 * Calcite validate/plan (same path as source-model SQL), then RelToSql with physical table aliases.
 */
public final class SemanticSqlEngine {

  private SemanticSqlEngine() {}

  public static String sql(
      SemanticModel model,
      SemanticSelection selection,
      DatabaseMeta databaseMeta,
      IVariables variables)
      throws HopException {
    RelNode rel = plan(model, selection);
    SqlDialect dialect = DialectSqlSupport.dialectFor(databaseMeta);
    return SemanticRelToSqlConverter.convert(rel, dialect, databaseMeta, variables);
  }

  public static RelNode plan(SemanticModel model, SemanticSelection selection) throws HopException {
    if (model == null) {
      throw new HopException("Semantic model is required");
    }
    if (selection == null || selection.isEmpty()) {
      throw new HopException("Select at least one field");
    }
    SemanticEntity fact = model.findEntity(selection.getEntityName());
    if (fact == null) {
      throw new HopException("Unknown semantic entity: " + selection.getEntityName());
    }

    String logicalSql = logicalSql(model, selection, fact);
    SemanticSchema schema = new SemanticSchema(model);
    SchemaPlus root = Frameworks.createRootSchema(true);
    SchemaPlus semantic = root.add(SemanticSchema.DEFAULT_SCHEMA_NAME, schema);
    FrameworkConfig config =
        Frameworks.newConfigBuilder()
            .defaultSchema(semantic)
            .parserConfig(
                SqlParser.config()
                    .withCaseSensitive(false)
                    .withQuotedCasing(org.apache.calcite.avatica.util.Casing.UNCHANGED)
                    .withUnquotedCasing(org.apache.calcite.avatica.util.Casing.UNCHANGED))
            .build();
    try (Planner planner = Frameworks.getPlanner(config)) {
      SqlNode parsed = planner.parse(logicalSql);
      SqlNode validated = planner.validate(parsed);
      RelRoot rootRel = planner.rel(validated);
      RelNode project = rootRel.project();
      return project != null ? project : rootRel.rel;
    } catch (Exception e) {
      throw new HopException("Unable to plan semantic selection SQL:\n" + logicalSql, e);
    }
  }

  static String logicalSql(SemanticModel model, SemanticSelection selection, SemanticEntity fact)
      throws HopException {
    Set<String> needed = neededEntities(selection, fact.getName());
    Set<String> usedAliases = new LinkedHashSet<>();
    List<String> selectList = new ArrayList<>();
    List<String> groupBy = new ArrayList<>();
    Set<String> grouped = new LinkedHashSet<>();
    for (SemanticSelectionField field : selection.allFields()) {
      if (field == null || Utils.isEmpty(field.getFieldName())) {
        continue;
      }
      String entityName = Const.NVL(field.getEntityName(), fact.getName());
      SemanticEntity entity = model.findEntity(entityName);
      if (entity == null) {
        throw new HopException("Unknown field entity: " + entityName);
      }
      String physical = physicalColumn(entity, field.getFieldName());
      String alias = uniqueAlias(field, usedAliases);
      usedAliases.add(alias);
      String expr = quoteIdent(entityName) + "." + quoteIdent(physical);
      if (selection.getMeasures().contains(field)) {
        selectList.add(aggregateCall(aggregationOf(entity, field)) + "(" + expr + ") AS " + quoteIdent(alias));
      } else {
        selectList.add(expr + " AS " + quoteIdent(alias));
        if (grouped.add(expr)) {
          groupBy.add(expr);
        }
      }
    }
    if (selectList.isEmpty()) {
      throw new HopException("Select at least one field");
    }
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT ");
    sql.append(String.join(", ", selectList));
    sql.append(" FROM ").append(quoteIdent(fact.getName()));
    Set<String> joined = new LinkedHashSet<>();
    joined.add(fact.getName());
    for (SemanticRelationship relationship : model.relationshipsFrom(fact.getName())) {
      if (relationship == null || !needed.contains(relationship.getToEntity())) {
        continue;
      }
      if (!joined.add(relationship.getToEntity())) {
        continue;
      }
      String joinKw =
          relationship.resolveJoinType() == SemanticJoinType.LEFT ? " LEFT JOIN " : " JOIN ";
      sql.append(joinKw)
          .append(quoteIdent(relationship.getToEntity()))
          .append(" ON ")
          .append(quoteIdent(fact.getName()))
          .append(".")
          .append(quoteIdent(relationship.getFromField()))
          .append(" = ")
          .append(quoteIdent(relationship.getToEntity()))
          .append(".")
          .append(quoteIdent(relationship.getToField()));
    }
    if (!groupBy.isEmpty()) {
      sql.append(" GROUP BY ");
      sql.append(String.join(", ", groupBy));
    }
    return sql.toString();
  }

  private static AggregationMethod aggregationOf(SemanticEntity entity, SemanticSelectionField field) {
    AggregationMethod method = field.resolveAggregation();
    if (method != null) {
      return method;
    }
    SemanticMeasure measure = entity.findMeasure(field.getFieldName());
    if (measure != null) {
      return measure.resolveAggregation();
    }
    return AggregationMethod.SUM;
  }

  private static String aggregateCall(AggregationMethod method) {
    return switch (method) {
      case COUNT -> "COUNT";
      case AVERAGE -> "AVG";
      case SUM -> "SUM";
    };
  }

  private static Set<String> neededEntities(SemanticSelection selection, String factName) {
    Set<String> names = new LinkedHashSet<>();
    names.add(factName);
    for (SemanticSelectionField field : selection.allFields()) {
      if (field != null && !Utils.isEmpty(field.getEntityName())) {
        names.add(field.getEntityName());
      }
    }
    return names;
  }

  private static String quoteIdent(String ident) {
    if (Utils.isEmpty(ident)) {
      return ident;
    }
    return "\"" + ident.replace("\"", "\"\"") + "\"";
  }

  private static String physicalColumn(SemanticEntity entity, String fieldName) {
    var attribute = entity.findAttribute(fieldName);
    if (attribute != null) {
      return attribute.resolvePhysicalColumn();
    }
    var measure = entity.findMeasure(fieldName);
    if (measure != null) {
      return measure.resolvePhysicalColumn();
    }
    return fieldName;
  }

  private static String uniqueAlias(SemanticSelectionField field, Set<String> used) {
    String columnAlias = sanitize(field.getFieldName());
    if (!used.contains(columnAlias)) {
      return columnAlias;
    }
    String tablePrefixed =
        sanitize(Const.NVL(field.getEntityName(), "") + "_" + field.getFieldName());
    String candidate = tablePrefixed;
    int n = 2;
    while (used.contains(candidate)) {
      candidate = tablePrefixed + "_" + n;
      n++;
    }
    return candidate;
  }

  private static String sanitize(String name) {
    if (Utils.isEmpty(name)) {
      return "col";
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (Character.isLetterOrDigit(ch) || ch == '_') {
        builder.append(Character.toLowerCase(ch));
      } else {
        builder.append('_');
      }
    }
    String alias = builder.toString().replaceAll("_+", "_");
    alias = alias.replaceAll("^_+", "").replaceAll("_+$", "");
    if (alias.isEmpty() || !Character.isLetter(alias.charAt(0))) {
      alias = "t_" + alias;
    }
    return alias;
  }
}
