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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BvSqlValidationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void missingSqlIsError() {
    BusinessVaultModel model = modelWithTarget();
    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("  ");

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(hasErrorContaining(remarks, "SQL"));
  }

  @Test
  void missingSourceDeclarationIsError() {
    BusinessVaultModel model = modelWithTarget();
    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT * FROM {{ source('refdata', 't1') }}");

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(hasErrorContaining(remarks, "source"));
  }

  @Test
  void unresolvedRefIsError() {
    BusinessVaultModel model = modelWithTarget();
    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT * FROM {{ ref('missing_object') }}");

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(hasErrorContaining(remarks, "unresolved") || hasErrorContaining(remarks, "ref"));
  }

  @Test
  void selfReferenceIsError() {
    BusinessVaultModel model = modelWithTarget();
    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT * FROM {{ ref('v1') }}");
    model.getTables().add(table);

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(hasErrorContaining(remarks, "itself") || hasErrorContaining(remarks, "Self"));
  }

  @Test
  void jinjaForLoopWithResolvedRefIsNotMalformed() {
    BusinessVaultModel model = modelWithTarget();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("s_product");
    scd2.setTableName("s_product");
    model.getTables().add(scd2);

    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery(
        "{% set cols = ['a'] %}SELECT {% for c in cols %}{{ c }}{% endfor %} FROM {{ ref('s_product') }}");
    model.getTables().add(table);

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertFalse(hasError(remarks), () -> remarks.toString());
  }

  @Test
  void malformedTemplateIsError() {
    BusinessVaultModel model = modelWithTarget();
    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT * FROM {{ ref('ok') }} AND {{ broken");

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(
        hasErrorContaining(remarks, "malformed")
            || hasErrorContaining(remarks, "template")
            || hasErrorContaining(remarks, "Jinja")
            || hasErrorContaining(remarks, "render"));
  }

  @Test
  void unusedSourceIsWarning() {
    BusinessVaultModel model = modelWithTarget();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("s_product");
    scd2.setTableName("s_product");
    model.getTables().add(scd2);

    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT * FROM {{ ref('s_product') }}");
    table.getSources().add(new BvSqlSource("refdata", null, "ref", "unused_table"));
    model.getTables().add(table);

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertFalse(hasError(remarks), () -> remarks.toString());
    assertTrue(hasWarningContaining(remarks, "unused") || hasWarningContaining(remarks, "Unused"));
  }

  @Test
  void bareTableIdentifierIsWarning() {
    BusinessVaultModel model = modelWithTarget();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("s_product");
    scd2.setTableName("s_product");
    model.getTables().add(scd2);

    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT * FROM s_product");
    model.getTables().add(table);

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(
        hasWarningContaining(remarks, "s_product") || hasWarningContaining(remarks, "outside"));
  }

  @Test
  void validRefAndSourcePasses() {
    BusinessVaultModel model = modelWithTarget();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("s_product");
    scd2.setTableName("s_product");
    model.getTables().add(scd2);

    BvBusinessTable table = bareTable("product_v");
    table.setSqlQuery(
        "SELECT * FROM {{ ref('s_product') }} p JOIN {{ source('refdata', 'lookup') }} l ON 1=1");
    table.getSources().add(new BvSqlSource("refdata", null, "ref", "lookup"));
    model.getTables().add(table);

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertFalse(hasError(remarks), () -> remarks.toString());
  }

  @Test
  void resolvesDvSatelliteRef() {
    BusinessVaultModel model = modelWithTarget();
    DataVaultModel dvModel = new DataVaultModel();
    DvSatellite sat = new DvSatellite();
    sat.setName("sat_customer");
    sat.setTableName("sat_customer");
    sat.setTableType(DvTableType.SATELLITE);
    dvModel.getTables().add(sat);

    BvBusinessTable table = bareTable("satb_customer_hb");
    table.setSqlQuery("SELECT * FROM {{ ref('sat_customer') }}");
    model.getTables().add(table);

    List<ICheckResult> remarks = check(table, model, dvModel);
    assertFalse(hasError(remarks), () -> remarks.toString());
  }

  @Test
  void missingBvTargetDatabaseIsError() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT 1");
    model.getTables().add(table);

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(hasErrorContaining(remarks, "target database"));
  }

  @Test
  void incompleteSourceDeclarationIsError() {
    BusinessVaultModel model = modelWithTarget();
    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT 1");
    BvSqlSource incomplete = new BvSqlSource();
    incomplete.setSourceName("refdata");
    table.getSources().add(incomplete);

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(
        hasErrorContaining(remarks, "physical table") || hasErrorContaining(remarks, "source"));
  }

  @Test
  void modelLevelCycleReportedOnce() {
    BusinessVaultModel model = modelWithTarget();
    BvBusinessTable a = bareTable("a");
    a.setSqlQuery("SELECT * FROM {{ ref('b') }}");
    BvBusinessTable b = bareTable("b");
    b.setSqlQuery("SELECT * FROM {{ ref('a') }}");
    model.getTables().add(a);
    model.getTables().add(b);

    List<ICheckResult> remarks = new ArrayList<>();
    a.check(remarks, null, new Variables(), model, new DataVaultModel());
    b.check(remarks, null, new Variables(), model, new DataVaultModel());
    int before = countErrors(remarks);
    BvSqlValidationSupport.validateModelSqlGraph(remarks, model);
    long cycleErrors =
        remarks.stream()
            .filter(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR)
            .filter(r -> r.getText() != null && r.getText().toLowerCase().contains("cycle"))
            .count();
    assertEquals(1, cycleErrors, () -> remarks.toString());
    assertTrue(countErrors(remarks) > before || cycleErrors == 1);
  }

  @Test
  void duplicateSourceIsWarning() {
    BusinessVaultModel model = modelWithTarget();
    BvBusinessTable table = bareTable("v1");
    table.setSqlQuery("SELECT * FROM {{ source('refdata', 't1') }}");
    table.getSources().add(new BvSqlSource("refdata", null, "ref", "t1"));
    table.getSources().add(new BvSqlSource("refdata", null, "ref", "t1"));

    List<ICheckResult> remarks = check(table, model, new DataVaultModel());
    assertTrue(
        hasWarningContaining(remarks, "more than once")
            || hasWarningContaining(remarks, "Duplicate"));
  }

  private static BusinessVaultModel modelWithTarget() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    return model;
  }

  private static BvBusinessTable bareTable(String name) {
    BvBusinessTable table = new BvBusinessTable();
    table.setName(name);
    table.setTableName(name);
    return table;
  }

  private static List<ICheckResult> check(
      BvBusinessTable table, BusinessVaultModel model, DataVaultModel dvModel) {
    List<ICheckResult> remarks = new ArrayList<>();
    table.check(remarks, null, new Variables(), model, dvModel);
    return remarks;
  }

  private static boolean hasError(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  private static int countErrors(List<ICheckResult> remarks) {
    return (int)
        remarks.stream().filter(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR).count();
  }

  private static boolean hasErrorContaining(List<ICheckResult> remarks, String fragment) {
    String f = fragment.toLowerCase();
    return remarks.stream()
        .filter(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR)
        .anyMatch(r -> r.getText() != null && r.getText().toLowerCase().contains(f));
  }

  private static boolean hasWarningContaining(List<ICheckResult> remarks, String fragment) {
    String f = fragment.toLowerCase();
    return remarks.stream()
        .filter(r -> r.getType() == ICheckResult.TYPE_RESULT_WARNING)
        .anyMatch(r -> r.getText() != null && r.getText().toLowerCase().contains(f));
  }
}
