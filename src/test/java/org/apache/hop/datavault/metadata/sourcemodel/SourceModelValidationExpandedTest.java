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
package org.apache.hop.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceModelValidationExpandedTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void relationshipFlagsMissingJoinColumnsOnEndpoints() {
    SourceModel model = productLookupModel();
    model.getRelationships().get(0).setChildColumns(List.of("missing_fk"));
    model.getRelationships().get(0).setParentColumns(List.of("type_id"));

    List<ICheckResult> remarks = model.check(null, new Variables());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("missing_fk")),
        () -> "Expected missing child column error: " + remarks);
  }

  @Test
  void tableFlagsMissingHopTypes() {
    SourceModel model = productLookupModel();
    model.findTable("product").getColumns().get(0).setHopType(0);

    List<ICheckResult> remarks =
        SourceTableValidationSupport.check(model, model.findTable("product"), new Variables());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("without a Hop data type")));
  }

  @Test
  void queryFlagsColumnNotOnTable() {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");
    query.getColumns().add(new SourceQueryColumn("product", "not_a_real_column"));

    List<ICheckResult> remarks =
        SourceQueryValidationSupport.check(model, query, new Variables(), null);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("not_a_real_column")),
        () -> "Expected column-not-on-table error: " + remarks);
  }

  @Test
  void columnDiffDetectsOnlyInModelAndOnlyInLive() {
    SourceColumn modelOnly = new SourceColumn("legacy_col");
    modelOnly.setHopType(IValueMeta.TYPE_STRING);
    SourceColumn sharedModel = new SourceColumn("id");
    sharedModel.setHopType(IValueMeta.TYPE_INTEGER);
    sharedModel.setLength("9");
    SourceColumn sharedLive = new SourceColumn("id");
    sharedLive.setHopType(IValueMeta.TYPE_INTEGER);
    sharedLive.setLength("9");
    SourceColumn liveOnly = new SourceColumn("new_col");
    liveOnly.setHopType(IValueMeta.TYPE_STRING);

    List<SourceTableLiveSchemaSupport.ColumnDiff> diffs =
        SourceTableLiveSchemaSupport.compareColumns(
            List.of(modelOnly, sharedModel), List.of(sharedLive, liveOnly));
    assertTrue(
        diffs.stream()
            .anyMatch(
                d ->
                    d.kind() == SourceTableLiveSchemaSupport.DiffKind.ONLY_IN_MODEL
                        && "legacy_col".equals(d.columnName())));
    assertTrue(
        diffs.stream()
            .anyMatch(
                d ->
                    d.kind() == SourceTableLiveSchemaSupport.DiffKind.ONLY_IN_LIVE
                        && "new_col".equals(d.columnName())));
    assertFalse(
        diffs.stream().anyMatch(d -> "id".equalsIgnoreCase(d.columnName())),
        () -> "Shared matching column should not differ: " + diffs);
  }

  @Test
  void columnDiffIgnoresIntegerAndTimestampLengthPrecisionNoise() {
    SourceColumn modelInt = new SourceColumn("qty");
    modelInt.setHopType(IValueMeta.TYPE_INTEGER);
    modelInt.setLength("9");
    modelInt.setPrecision("0");
    SourceColumn liveInt = new SourceColumn("qty");
    liveInt.setHopType(IValueMeta.TYPE_INTEGER);
    liveInt.setLength("10"); // JDBC display size
    liveInt.setPrecision("");

    SourceColumn modelTs = new SourceColumn("load_dts");
    modelTs.setHopType(IValueMeta.TYPE_TIMESTAMP);
    modelTs.setLength("");
    modelTs.setPrecision("");
    SourceColumn liveTs = new SourceColumn("load_dts");
    liveTs.setHopType(IValueMeta.TYPE_TIMESTAMP);
    liveTs.setLength("6");
    liveTs.setPrecision("6");

    SourceColumn modelStr = new SourceColumn("name");
    modelStr.setHopType(IValueMeta.TYPE_STRING);
    modelStr.setLength("50");
    SourceColumn liveStr = new SourceColumn("name");
    liveStr.setHopType(IValueMeta.TYPE_STRING);
    liveStr.setLength("100");

    List<SourceTableLiveSchemaSupport.ColumnDiff> diffs =
        SourceTableLiveSchemaSupport.compareColumns(
            List.of(modelInt, modelTs, modelStr), List.of(liveInt, liveTs, liveStr));

    assertFalse(
        diffs.stream().anyMatch(d -> "qty".equals(d.columnName())),
        () -> "Integer length/precision noise should be ignored: " + diffs);
    assertFalse(
        diffs.stream().anyMatch(d -> "load_dts".equals(d.columnName())),
        () -> "Timestamp length/precision noise should be ignored: " + diffs);
    assertTrue(
        diffs.stream()
            .anyMatch(
                d ->
                    "name".equals(d.columnName())
                        && d.kind() == SourceTableLiveSchemaSupport.DiffKind.LENGTH_CHANGED),
        () -> "String length change should still be reported: " + diffs);
  }

  private static SourceModel productLookupModel() {
    SourceModel model = new SourceModel();

    SourceTable product = new SourceTable("product");
    product.setTableName("product");
    product.setDatabaseName("CRM");
    product.setLocation(new Point(50, 60));
    SourceColumn productId = new SourceColumn("product_id");
    productId.setPrimaryKeyPosition(1);
    productId.setHopType(IValueMeta.TYPE_INTEGER);
    product.getColumns().add(productId);
    SourceColumn typeId = new SourceColumn("type_id");
    typeId.setHopType(IValueMeta.TYPE_INTEGER);
    product.getColumns().add(typeId);
    model.getTables().add(product);

    SourceTable productType = new SourceTable("product_type");
    productType.setTableName("product_type");
    productType.setDatabaseName("CRM");
    SourceColumn typePk = new SourceColumn("type_id");
    typePk.setPrimaryKeyPosition(1);
    typePk.setHopType(IValueMeta.TYPE_INTEGER);
    productType.getColumns().add(typePk);
    SourceColumn typeName = new SourceColumn("type_name");
    typeName.setHopType(IValueMeta.TYPE_STRING);
    productType.getColumns().add(typeName);
    model.getTables().add(productType);

    SourceRelationship fk = new SourceRelationship("fk_product_type");
    fk.setChildTableName("product");
    fk.setParentTableName("product_type");
    fk.setChildColumns(List.of("type_id"));
    fk.setParentColumns(List.of("type_id"));
    model.getRelationships().add(fk);

    SourceQuery query = new SourceQuery("feed_product_enriched");
    query.setDrivingTableName("product");
    SourceQueryJoin join = new SourceQueryJoin();
    join.setTableName("product_type");
    query.getJoins().add(join);
    SourceQueryColumn qPk = new SourceQueryColumn("product", "product_id");
    qPk.setPrimaryKeyPosition(1);
    query.getColumns().add(qPk);
    query.getColumns().add(new SourceQueryColumn("product", "type_id"));
    query.getColumns().add(new SourceQueryColumn("product_type", "type_name"));
    model.getQueries().add(query);
    return model;
  }
}
