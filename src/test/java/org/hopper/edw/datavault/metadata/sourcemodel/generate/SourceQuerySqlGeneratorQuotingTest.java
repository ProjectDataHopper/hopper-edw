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
package org.hopper.edw.datavault.metadata.sourcemodel.generate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJoinType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Ensures {@link SourceQuerySqlGenerator} uses {@link DatabaseMeta} quoting for fields and schema
 * tables across dialect-style quote characters (Postgres, MySQL, SQL Server).
 */
class SourceQuerySqlGeneratorQuotingTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource({"POSTGRESQL, \", \"", "MYSQL, `, `", "MSSQLNATIVE, [, ]"})
  void quotesIdentifiersPerDialect(String pluginId, String open, String close) throws Exception {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");
    DatabaseMeta databaseMeta = quotingDatabaseMeta(pluginId, open, close);

    String sql = SourceQuerySqlGenerator.generate(model, query, databaseMeta, new Variables());

    String schemaTable = open + "public" + close + "." + open + "product" + close;
    String productId = open + "product_id" + close;
    String productTypeIdAlias = open + "product_type_id" + close;
    assertTrue(sql.contains(schemaTable), "expected " + schemaTable + " in: " + sql);
    assertTrue(sql.contains(productId), "expected " + productId + " in: " + sql);
    assertTrue(sql.contains(productTypeIdAlias), "expected " + productTypeIdAlias + " in: " + sql);
    assertTrue(sql.contains("LEFT OUTER JOIN"), sql);
  }

  @Test
  void retailStyleCustomerJoinUsesQuotedSchemaTable() throws Exception {
    SourceModel model = customerEnrichedModel();
    SourceQuery query = model.findQuery("All customer info");
    DatabaseMeta databaseMeta = quotingDatabaseMeta("POSTGRESQL", "\"", "\"");

    String sql = SourceQuerySqlGenerator.generate(model, query, databaseMeta, new Variables());

    assertTrue(sql.contains("FROM \"public\".\"customer_hub\""), sql);
    assertTrue(sql.contains("LEFT OUTER JOIN \"public\".\"customer_address\""), sql);
    // Table aliases are truncated (customer_add); column quotes still apply.
    assertTrue(sql.contains(".\"customer_id\""), sql);
    assertTrue(sql.contains(".\"city\" AS \"city\""), sql);
  }

  private static DatabaseMeta quotingDatabaseMeta(String pluginId, String open, String close) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }

      @Override
      public String quoteField(String field) {
        if (field == null) {
          return null;
        }
        return open + field + close;
      }

      @Override
      public String getQuotedSchemaTableCombination(
          IVariables variables, String schemaName, String tableName) {
        String schema = schemaName;
        String table = tableName;
        if (variables != null) {
          schema = variables.resolve(schema);
          table = variables.resolve(table);
        }
        if (schema != null && !schema.isBlank()) {
          return open + schema + close + "." + open + table + close;
        }
        return open + table + close;
      }
    };
  }

  private static SourceModel productLookupModel() {
    SourceModel model = new SourceModel();

    SourceTable product = new SourceTable("product");
    product.setSchemaName("public");
    product.setTableName("product");
    product.setDatabaseName("CRM");
    product.setPhysicalType(DvSourceType.DATABASE);
    product.getColumns().add(pk("product_id"));
    product.getColumns().add(col("type_id"));
    model.getTables().add(product);

    SourceTable productType = new SourceTable("product_type");
    productType.setSchemaName("public");
    productType.setTableName("product_type");
    productType.setDatabaseName("CRM");
    productType.setPhysicalType(DvSourceType.DATABASE);
    productType.getColumns().add(pk("type_id"));
    productType.getColumns().add(col("type_name"));
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
    query.getColumns().add(new SourceQueryColumn("product_type", "type_id", "product_type_id"));
    query.getColumns().add(new SourceQueryColumn("product_type", "type_name"));
    model.getQueries().add(query);
    return model;
  }

  private static SourceModel customerEnrichedModel() {
    SourceModel model = new SourceModel();
    model.getTables().add(dbTable("customer_hub", "customer_id"));
    model.getTables().add(dbTable("customer_address", "customer_id", "city", "address_line1"));
    model.getTables().add(dbTable("customer_contact", "customer_id", "email"));

    model
        .getRelationships()
        .add(rel("fk_hub_address", "customer_address", "customer_hub", "customer_id"));
    model
        .getRelationships()
        .add(rel("fk_hub_contact", "customer_contact", "customer_hub", "customer_id"));

    SourceQuery query = new SourceQuery("All customer info");
    query.setDrivingTableName("customer_hub");
    SourceQueryJoin j1 = new SourceQueryJoin();
    j1.setTableName("customer_address");
    j1.setRelationshipName("fk_hub_address");
    j1.setJoinType(SourceJoinType.LEFT);
    query.getJoins().add(j1);
    SourceQueryJoin j2 = new SourceQueryJoin();
    j2.setTableName("customer_contact");
    j2.setRelationshipName("fk_hub_contact");
    j2.setJoinType(SourceJoinType.LEFT);
    query.getJoins().add(j2);
    query.getColumns().add(new SourceQueryColumn("customer_hub", "customer_id"));
    query.getColumns().add(new SourceQueryColumn("customer_address", "city"));
    query.getColumns().add(new SourceQueryColumn("customer_contact", "email"));
    model.getQueries().add(query);
    return model;
  }

  private static SourceTable dbTable(String name, String... columns) {
    SourceTable table = new SourceTable(name);
    table.setSchemaName("public");
    table.setTableName(name);
    table.setDatabaseName("CRM");
    table.setPhysicalType(DvSourceType.DATABASE);
    boolean first = true;
    for (String column : columns) {
      SourceColumn c = col(column);
      if (first) {
        c.setPrimaryKeyPosition(1);
        first = false;
      }
      table.getColumns().add(c);
    }
    return table;
  }

  private static SourceRelationship rel(String name, String child, String parent, String column) {
    SourceRelationship relationship = new SourceRelationship(name);
    relationship.setChildTableName(child);
    relationship.setParentTableName(parent);
    relationship.setChildColumns(List.of(column));
    relationship.setParentColumns(List.of(column));
    relationship.setDefaultJoinType(SourceJoinType.LEFT);
    return relationship;
  }

  private static SourceColumn pk(String name) {
    SourceColumn column = col(name);
    column.setPrimaryKeyPosition(1);
    return column;
  }

  private static SourceColumn col(String name) {
    SourceColumn column = new SourceColumn(name);
    column.setHopType(2);
    return column;
  }
}
