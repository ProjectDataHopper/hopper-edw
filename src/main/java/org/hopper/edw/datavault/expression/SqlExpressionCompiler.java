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
package org.hopper.edw.datavault.expression;

import java.util.Map;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.RelVisitor;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.core.Values;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.virtualization.calcite.HopTypeSystem;

/** Parses a SQL scalar against a Hop row layout with Calcite, then allow-lists the Rex tree. */
public final class SqlExpressionCompiler {

  public static final String TABLE_NAME = "hop_row";
  public static final String OUTPUT_ALIAS = "_out";

  private SqlExpressionCompiler() {}

  public static SqlCompiledExpression compile(
      SqlExpressionSpec spec, IRowMeta inputRowMeta, IVariables variables)
      throws SqlExpressionException {
    if (spec == null || Utils.isEmpty(spec.getExpression())) {
      throw new SqlExpressionException("SQL expression is empty");
    }
    String fieldName =
        variables != null ? variables.resolve(spec.getFieldName()) : spec.getFieldName();
    if (Utils.isEmpty(fieldName)) {
      throw new SqlExpressionException("SQL expression output field name is empty");
    }
    spec.setFieldName(fieldName);
    if (inputRowMeta == null) {
      throw new SqlExpressionException("Input row layout is required to compile SQL expressions");
    }

    String resolved =
        variables != null ? variables.resolve(spec.getExpression()) : spec.getExpression();
    String rewritten = SqlCastRewrite.rewrite(resolved);
    String wrapped = wrapSelect(rewritten);

    RelDataType relType;
    RexNode rexNode;
    try {
      Planned planned = plan(inputRowMeta, wrapped);
      rexNode = planned.rexNode;
      relType = planned.relType;
    } catch (SqlExpressionException e) {
      throw e;
    } catch (Exception e) {
      throw new SqlExpressionException(
          "Unable to compile SQL expression '" + spec.getFieldName() + "': " + e.getMessage(), e);
    }

    SqlExpressionAllowList.check(rexNode);
    IValueMeta outputMeta = resolveOutputMeta(spec, relType);
    return new SqlCompiledExpression(
        spec.getFieldName(), rewritten, rexNode, relType, inputRowMeta.clone(), outputMeta);
  }

  private static String wrapSelect(String expression) {
    String trimmed = expression.trim();
    if (trimmed.endsWith(";")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    return "SELECT (" + trimmed + ") AS \"" + OUTPUT_ALIAS + "\" FROM " + TABLE_NAME;
  }

  private static Planned plan(IRowMeta inputRowMeta, String sql) throws SqlExpressionException {
    HopRowSchema schema = new HopRowSchema(inputRowMeta);
    SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    rootSchema.add("expr", schema);

    SqlParser.Config parserConfig =
        SqlParser.config()
            .withCaseSensitive(false)
            .withQuotedCasing(org.apache.calcite.avatica.util.Casing.UNCHANGED)
            .withUnquotedCasing(org.apache.calcite.avatica.util.Casing.UNCHANGED);

    FrameworkConfig config =
        Frameworks.newConfigBuilder()
            .defaultSchema(rootSchema.getSubSchema("expr"))
            .parserConfig(parserConfig)
            .operatorTable(SqlExpressionOperatorTable.instance())
            .build();

    try (Planner planner = Frameworks.getPlanner(config)) {
      SqlNode parsed = planner.parse(sql);
      SqlNode validated = planner.validate(parsed);
      RelRoot root = planner.rel(validated);
      RelNode project = root.project();
      RelNode rel = project != null ? project : root.rel;
      assertScalarPlan(rel);
      if (!(rel instanceof Project p) || p.getProjects().isEmpty()) {
        throw new SqlExpressionException("SQL expression did not produce a projected value");
      }
      RexNode rexNode = p.getProjects().get(0);
      RelDataType relType = p.getRowType().getFieldList().get(0).getType();
      return new Planned(rexNode, relType);
    } catch (SqlParseException e) {
      throw new SqlExpressionException("SQL parse error: " + e.getMessage(), e);
    } catch (ValidationException e) {
      throw new SqlExpressionException("SQL validation error: " + e.getMessage(), e);
    } catch (RelConversionException e) {
      throw new SqlExpressionException("SQL planning error: " + e.getMessage(), e);
    }
  }

  private static void assertScalarPlan(RelNode rel) throws SqlExpressionException {
    try {
      new RelVisitor() {
        @Override
        public void visit(RelNode node, int ordinal, RelNode parent) {
          if (!(node instanceof Project || node instanceof TableScan || node instanceof Values)) {
            throw new IllegalStateException(
                "SQL expressions may not contain "
                    + node.getRelTypeName()
                    + " (joins, filters, aggregates, and subqueries are not allowed)");
          }
          super.visit(node, ordinal, parent);
        }
      }.go(rel);
    } catch (RuntimeException e) {
      throw new SqlExpressionException(e.getMessage(), e);
    }
  }

  static IValueMeta resolveOutputMeta(SqlExpressionSpec spec, RelDataType relType)
      throws SqlExpressionException {
    try {
      int hopType;
      int length;
      int precision;
      if (!Utils.isEmpty(spec.getHopTypeName())) {
        hopType = ValueMetaFactory.getIdForValueMeta(spec.getHopTypeName());
        length = spec.getLength();
        precision = spec.getPrecision();
      } else {
        hopType = HopTypeSystem.toHopType(relType);
        length = HopTypeSystem.toHopLength(relType);
        precision = HopTypeSystem.toHopScale(relType);
        if (spec.getLength() > 0) {
          length = spec.getLength();
        }
        if (spec.getPrecision() >= 0) {
          precision = spec.getPrecision();
        }
      }
      IValueMeta valueMeta = ValueMetaFactory.createValueMeta(spec.getFieldName(), hopType);
      if (length > 0) {
        valueMeta.setLength(length, precision >= 0 ? precision : -1);
      } else if (precision >= 0) {
        valueMeta.setPrecision(precision);
      }
      return valueMeta;
    } catch (Exception e) {
      throw new SqlExpressionException(
          "Unable to resolve output type for '" + spec.getFieldName() + "'", e);
    }
  }

  private record Planned(RexNode rexNode, RelDataType relType) {}

  private static final class HopRowSchema extends AbstractSchema {
    private final IRowMeta rowMeta;

    private HopRowSchema(IRowMeta rowMeta) {
      this.rowMeta = rowMeta;
    }

    @Override
    protected Map<String, Table> getTableMap() {
      return Map.of(TABLE_NAME, new HopRowTable(rowMeta));
    }
  }
}
