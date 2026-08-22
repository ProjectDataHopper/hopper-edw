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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;

class SourceQuerySqlGeneratorTest {

  @Test
  void generatesLeftJoinWithRelationshipKeysAndAliases() throws Exception {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");

    String sql = SourceQuerySqlGenerator.generate(model, query, null, new Variables());

    assertTrue(sql.startsWith("SELECT "));
    assertTrue(sql.contains("FROM product product"));
    assertTrue(sql.contains("LEFT OUTER JOIN product_type product_type"));
    assertTrue(sql.contains("product.type_id = product_type.type_id"));
    assertTrue(sql.contains("product.product_id AS product_id"));
    assertTrue(sql.contains("product_type.type_name AS type_name"));
    assertTrue(sql.contains("product_type.type_id AS product_type_id"));
    assertFalse(sql.contains("WHERE"));
  }

  @Test
  void generatesWhereClauseWithoutDuplicatingKeyword() throws Exception {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");
    query.setWhereClause("WHERE product.product_id IS NOT NULL");

    String sql = SourceQuerySqlGenerator.generate(model, query, null, new Variables());

    assertTrue(
        sql.endsWith(" WHERE product.product_id IS NOT NULL")
            || sql.contains(" WHERE product.product_id IS NOT NULL"));
    assertEquals(1, countOccurrences(sql.toUpperCase(), " WHERE "));
  }

  @Test
  void usesExplicitJoinColumnsWhenProvided() throws Exception {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");
    SourceQueryJoin join = query.getJoins().get(0);
    join.setRelationshipName(null);
    join.setLeftColumns(List.of("type_id"));
    join.setRightColumns(List.of("type_id"));
    join.setLeftTableNames(List.of("product"));
    join.setJoinType(SourceJoinType.INNER);

    String sql = SourceQuerySqlGenerator.generate(model, query, null, new Variables());

    assertTrue(sql.contains("INNER JOIN product_type product_type"));
    assertTrue(sql.contains("product.type_id = product_type.type_id"));
  }

  @Test
  void rejectsEmptyProjection() {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");
    query.getColumns().clear();

    HopException ex =
        assertThrows(
            HopException.class,
            () -> SourceQuerySqlGenerator.generate(model, query, null, new Variables()));
    assertTrue(ex.getMessage().contains("no projected columns"));
  }

  @Test
  void rejectsMixedConnections() {
    SourceModel model = productLookupModel();
    model.findTable("product_type").setDatabaseName("OTHER");
    SourceQuery query = model.findQuery("feed_product_enriched");

    HopException ex =
        assertThrows(
            HopException.class,
            () -> SourceQuerySqlGenerator.generate(model, query, null, new Variables()));
    assertTrue(ex.getMessage().toLowerCase().contains("single-connection"));
  }

  @Test
  void resolveEffectiveModePrefersSqlForSameDatabase() {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");
    assertEquals(
        SourceQueryGenerationMode.SQL,
        SourceQueryGenerationSupport.resolveEffectiveMode(model, query));
    assertTrue(SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, query));
  }

  @Test
  void resolveEffectiveModePrefersPipelineWhenForcedOrMixed() {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");
    query.setGenerationMode(SourceQueryGenerationMode.PIPELINE);
    assertEquals(
        SourceQueryGenerationMode.PIPELINE,
        SourceQueryGenerationSupport.resolveEffectiveMode(model, query));

    query.setGenerationMode(SourceQueryGenerationMode.AUTO);
    model.findTable("product_type").setPhysicalType(DvSourceType.CSV);
    assertEquals(
        SourceQueryGenerationMode.PIPELINE,
        SourceQueryGenerationSupport.resolveEffectiveMode(model, query));
  }

  @Test
  void joinKeyResolverUsesRelationshipWhenColumnsEmpty() throws Exception {
    SourceModel model = productLookupModel();
    SourceQueryJoin join = model.findQuery("feed_product_enriched").getJoins().get(0);
    join.setLeftColumns(List.of());
    join.setRightColumns(List.of());

    SourceQueryJoinKeyResolver.ResolvedJoinKeys keys =
        SourceQueryJoinKeyResolver.resolve(model, join, java.util.Set.of("product"));

    assertTrue(keys.isValid());
    assertEquals(List.of("product"), keys.leftTables());
    assertEquals(List.of("type_id"), keys.leftColumns());
    assertEquals(List.of("type_id"), keys.rightColumns());
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) >= 0) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  private static SourceModel productLookupModel() {
    SourceModel model = new SourceModel();

    SourceTable product = new SourceTable("product");
    product.setTableName("product");
    product.setDatabaseName("CRM");
    product.setPhysicalType(DvSourceType.DATABASE);
    SourceColumn productId = new SourceColumn("product_id");
    productId.setPrimaryKeyPosition(1);
    product.getColumns().add(productId);
    product.getColumns().add(new SourceColumn("type_id"));
    model.getTables().add(product);

    SourceTable productType = new SourceTable("product_type");
    productType.setTableName("product_type");
    productType.setDatabaseName("CRM");
    productType.setPhysicalType(DvSourceType.DATABASE);
    SourceColumn typePk = new SourceColumn("type_id");
    typePk.setPrimaryKeyPosition(1);
    productType.getColumns().add(typePk);
    productType.getColumns().add(new SourceColumn("type_name"));
    model.getTables().add(productType);

    SourceRelationship fk = new SourceRelationship("fk_product_type");
    fk.setChildTableName("product");
    fk.setParentTableName("product_type");
    fk.setChildColumns(List.of("type_id"));
    fk.setParentColumns(List.of("type_id"));
    fk.setDefaultJoinType(SourceJoinType.LEFT);
    model.getRelationships().add(fk);

    SourceQuery query = new SourceQuery("feed_product_enriched");
    query.setDrivingTableName("product");
    SourceQueryJoin join = new SourceQueryJoin();
    join.setTableName("product_type");
    join.setRelationshipName("fk_product_type");
    join.setJoinType(SourceJoinType.LEFT);
    query.getJoins().add(join);
    query.getColumns().add(new SourceQueryColumn("product", "product_id"));
    query.getColumns().add(new SourceQueryColumn("product", "type_id"));
    query.getColumns().add(new SourceQueryColumn("product_type", "type_id", "product_type_id"));
    query.getColumns().add(new SourceQueryColumn("product_type", "type_name"));
    model.getQueries().add(query);

    return model;
  }
}
