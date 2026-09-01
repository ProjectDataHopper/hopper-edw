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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.expression.SqlExpressionException;
import org.hopper.edw.datavault.expression.SqlExpressionProgram;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;
import org.hopper.edw.datavault.metadata.DataVaultModel;

/** Check-model rules for SCD2 SQL calculations and their stored tests. */
final class BvScd2CalculationValidationSupport {

  private static final Class<?> PKG = BvScd2CalculationValidationSupport.class;

  private BvScd2CalculationValidationSupport() {}

  static void validate(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    if (scd2Table == null || dataVaultModel == null) {
      return;
    }
    List<BvScd2Calculation> calculations = scd2Table.getCalculations();
    if (calculations.isEmpty()
        && scd2Table.getCalculationTests().isEmpty()
        && scd2Table.getCollapseTests().isEmpty()) {
      return;
    }

    IRowMeta collapseLayout;
    try {
      collapseLayout =
          BvScd2PipelineSupport.buildCollapseRowLayout(
              scd2Table, bvConfig, dataVaultModel, variables);
    } catch (Exception e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), scd2Table));
      return;
    }

    Set<String> reserved = reservedNames(collapseLayout);
    Set<String> calcNames = new HashSet<>();
    for (BvScd2Calculation calculation : calculations) {
      if (calculation == null) {
        continue;
      }
      String name = variables.resolve(calculation.getTargetFieldName());
      String expression = variables.resolve(calculation.getExpression());
      if (Utils.isEmpty(name) || Utils.isEmpty(expression)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2CalculationValidationSupport.Error.IncompleteCalculation",
                    scd2Table.getName()),
                scd2Table));
        continue;
      }
      if (reserved.contains(name.toLowerCase())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2CalculationValidationSupport.Error.ReservedName",
                    scd2Table.getName(),
                    name),
                scd2Table));
      }
      if (!calcNames.add(name.toLowerCase())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2CalculationValidationSupport.Error.DuplicateTarget",
                    scd2Table.getName(),
                    name),
                scd2Table));
      }
    }

    try {
      SqlExpressionProgram.compile(toSpecs(calculations, variables), collapseLayout, variables);
    } catch (SqlExpressionException e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2CalculationValidationSupport.Error.CompileFailed",
                  scd2Table.getName(),
                  e.getMessage()),
              scd2Table));
      return;
    }

    BvScd2CalculationTestRunner.runAll(remarks, scd2Table, collapseLayout, variables);
  }

  public static List<SqlExpressionSpec> toSpecs(
      List<BvScd2Calculation> calculations, IVariables variables) {
    java.util.ArrayList<SqlExpressionSpec> specs = new java.util.ArrayList<>();
    if (calculations == null) {
      return specs;
    }
    for (BvScd2Calculation calculation : calculations) {
      if (calculation == null || Utils.isEmpty(calculation.getExpression())) {
        continue;
      }
      specs.add(calculation.toSpec());
    }
    return specs;
  }

  private static Set<String> reservedNames(IRowMeta collapseLayout) {
    Set<String> names = new HashSet<>();
    if (collapseLayout == null) {
      return names;
    }
    for (int i = 0; i < collapseLayout.size(); i++) {
      String name = collapseLayout.getValueMeta(i).getName();
      if (!Utils.isEmpty(name)) {
        names.add(name.toLowerCase());
      }
    }
    return names;
  }
}
