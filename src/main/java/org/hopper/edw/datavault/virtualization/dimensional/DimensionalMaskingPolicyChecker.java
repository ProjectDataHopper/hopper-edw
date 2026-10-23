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

import java.util.Set;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.metadata.RelColumnOrigin;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexVisitorImpl;
import org.apache.calcite.util.ImmutableBitSet;

/**
 * Safe masking: a masked column may appear only as a bare select item. Filters, joins, grouping,
 * ordering, distinct, and expressions that touch it are rejected before the warehouse is queried.
 */
public final class DimensionalMaskingPolicyChecker {

  private DimensionalMaskingPolicyChecker() {}

  public static void checkSafe(RelNode rel) throws DimensionalSqlException {
    if (rel == null) {
      return;
    }
    walk(rel);
  }

  private static void walk(RelNode rel) throws DimensionalSqlException {
    if (rel instanceof Project project) {
      RelNode input = project.getInput();
      for (RexNode expression : project.getProjects()) {
        if (!(expression instanceof RexInputRef) && referencesMasked(expression, input)) {
          throw DimensionalSqlException.rejected(
              "A masked column can only appear as a bare select item. "
                  + "Expressions, casts, and concatenations on a masked column are rejected.");
        }
      }
      walk(input);
      return;
    }
    if (rel instanceof Filter filter) {
      if (referencesMasked(filter.getCondition(), filter.getInput())) {
        throw DimensionalSqlException.rejected(
            "A masked column cannot be used in WHERE. Safe masking rejects predicates on masked"
                + " columns.");
      }
      walk(filter.getInput());
      return;
    }
    if (rel instanceof Join join) {
      if (referencesMaskedAcross(join.getCondition(), join.getLeft(), join.getRight())) {
        throw DimensionalSqlException.rejected(
            "A masked column cannot be used in a JOIN. Join on the surrogate or foreign key.");
      }
      walk(join.getLeft());
      walk(join.getRight());
      return;
    }
    if (rel instanceof Aggregate aggregate) {
      checkAggregate(aggregate);
      walk(aggregate.getInput());
      return;
    }
    if (rel instanceof Sort sort) {
      RelNode input = sort.getInput();
      for (var collation : sort.getCollation().getFieldCollations()) {
        if (outputMasked(input, collation.getFieldIndex())) {
          throw DimensionalSqlException.rejected(
              "A masked column cannot be used in ORDER BY.");
        }
      }
      walk(input);
      return;
    }
    for (RelNode input : rel.getInputs()) {
      walk(input);
    }
  }

  private static void checkAggregate(Aggregate aggregate) throws DimensionalSqlException {
    RelNode input = aggregate.getInput();
    ImmutableBitSet groupSet = aggregate.getGroupSet();
    boolean distinctOnly = aggregate.getAggCallList().isEmpty();
    for (int index = groupSet.nextSetBit(0); index >= 0; index = groupSet.nextSetBit(index + 1)) {
      if (!outputMasked(input, index)) {
        continue;
      }
      throw DimensionalSqlException.rejected(
          distinctOnly
              ? "DISTINCT on a masked column is rejected."
              : "A masked column cannot be used in GROUP BY.");
    }
    for (AggregateCall call : aggregate.getAggCallList()) {
      for (int arg : call.getArgList()) {
        if (outputMasked(input, arg)) {
          throw DimensionalSqlException.rejected(
              "An aggregate on a masked column is rejected (" + call.getName() + ").");
        }
      }
    }
  }

  private static boolean referencesMasked(RexNode expression, RelNode input)
      throws DimensionalSqlException {
    if (expression == null) {
      return false;
    }
    MaskedRefVisitor visitor = new MaskedRefVisitor(input);
    expression.accept(visitor);
    if (visitor.failure != null) {
      throw visitor.failure;
    }
    return visitor.masked;
  }

  private static boolean referencesMaskedAcross(RexNode expression, RelNode left, RelNode right)
      throws DimensionalSqlException {
    if (expression == null) {
      return false;
    }
    int leftCount = left.getRowType().getFieldCount();
    final boolean[] masked = {false};
    final DimensionalSqlException[] failure = {null};
    expression.accept(
        new RexVisitorImpl<Void>(true) {
          @Override
          public Void visitInputRef(RexInputRef ref) {
            try {
              int index = ref.getIndex();
              if (index < leftCount) {
                masked[0] = masked[0] || outputMasked(left, index);
              } else {
                masked[0] = masked[0] || outputMasked(right, index - leftCount);
              }
            } catch (DimensionalSqlException e) {
              failure[0] = e;
            }
            return null;
          }

          @Override
          public Void visitCall(RexCall call) {
            for (RexNode operand : call.getOperands()) {
              operand.accept(this);
            }
            return null;
          }
        });
    if (failure[0] != null) {
      throw failure[0];
    }
    return masked[0];
  }

  static boolean outputMasked(RelNode rel, int ordinal) throws DimensionalSqlException {
    if (rel == null || ordinal < 0 || ordinal >= rel.getRowType().getFieldCount()) {
      return false;
    }
    try {
      RelMetadataQuery query = rel.getCluster().getMetadataQuery();
      Set<RelColumnOrigin> origins = query.getColumnOrigins(rel, ordinal);
      if (origins == null) {
        return false;
      }
      for (RelColumnOrigin origin : origins) {
        if (origin == null || origin.getOriginTable() == null) {
          continue;
        }
        DimensionalCalciteTable calcite =
            origin.getOriginTable().unwrap(DimensionalCalciteTable.class);
        if (calcite == null) {
          continue;
        }
        DimensionalJdbcColumn column = calcite.getTable().column(origin.getOriginColumnOrdinal());
        if (column != null && column.masked()) {
          return true;
        }
      }
      return false;
    } catch (RuntimeException e) {
      throw new DimensionalSqlException(
          "Could not verify the masking policy for this statement.", e);
    }
  }

  /** Output ordinals whose lineage includes a masked column. */
  public static boolean[] maskedOutputs(RelNode rel) throws DimensionalSqlException {
    int count = rel.getRowType().getFieldCount();
    boolean[] masked = new boolean[count];
    for (int i = 0; i < count; i++) {
      masked[i] = outputMasked(rel, i);
    }
    return masked;
  }

  public static String[] outputPatterns(RelNode rel) throws DimensionalSqlException {
    int count = rel.getRowType().getFieldCount();
    String[] patterns = new String[count];
    if (rel == null) {
      return patterns;
    }
    try {
      RelMetadataQuery query = rel.getCluster().getMetadataQuery();
      for (int i = 0; i < count; i++) {
        Set<RelColumnOrigin> origins = query.getColumnOrigins(rel, i);
        if (origins == null) {
          continue;
        }
        for (RelColumnOrigin origin : origins) {
          if (origin == null || origin.getOriginTable() == null) {
            continue;
          }
          DimensionalCalciteTable calcite =
              origin.getOriginTable().unwrap(DimensionalCalciteTable.class);
          if (calcite == null) {
            continue;
          }
          DimensionalJdbcColumn column =
              calcite.getTable().column(origin.getOriginColumnOrdinal());
          if (column != null && column.masked()) {
            patterns[i] = column.maskingPattern();
            break;
          }
        }
      }
      return patterns;
    } catch (RuntimeException e) {
      throw new DimensionalSqlException(
          "Could not verify the masking policy for this statement.", e);
    }
  }

  private static final class MaskedRefVisitor extends RexVisitorImpl<Void> {
    private final RelNode input;
    private boolean masked;
    private DimensionalSqlException failure;

    private MaskedRefVisitor(RelNode input) {
      super(true);
      this.input = input;
    }

    @Override
    public Void visitInputRef(RexInputRef ref) {
      try {
        masked = masked || outputMasked(input, ref.getIndex());
      } catch (DimensionalSqlException e) {
        failure = e;
      }
      return null;
    }

    @Override
    public Void visitCall(RexCall call) {
      for (RexNode operand : call.getOperands()) {
        operand.accept(this);
        if (failure != null) {
          return null;
        }
      }
      return null;
    }
  }
}
