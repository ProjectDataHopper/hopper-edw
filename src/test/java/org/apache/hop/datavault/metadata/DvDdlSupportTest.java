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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvDdlSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void buildCreateTableStatementIncludesShardKeyOnHashKey() {
    DatabaseMeta databaseMeta = singleStoreDatabaseMeta();
    IRowMeta fields = hubLayout();

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            databaseMeta,
            new Variables(),
            "hub_customer",
            fields,
            new String[] {"customer_hk"},
            (String) null,
            true);

    assertTrue(ddl.toUpperCase().contains("CREATE TABLE"));
    assertTrue(ddl.contains("customer_hk"));
    assertTrue(ddl.toUpperCase().contains("BINARY"));
    assertTrue(ddl.toUpperCase().contains("SHARD KEY (CUSTOMER_HK)"));
  }

  @Test
  void buildCreateTableStatementIncludesDrivingKeyInShardKey() {
    DatabaseMeta databaseMeta = singleStoreDatabaseMeta();
    IRowMeta fields = multiActiveSatelliteLayout();

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            databaseMeta,
            new Variables(),
            "sat_customer_phone",
            fields,
            new String[] {"customer_hk", "phone_type"},
            (String) null,
            true);

    assertTrue(ddl.toUpperCase().contains("SHARD KEY (CUSTOMER_HK, PHONE_TYPE)"));
  }

  @Test
  void isShardKeyDdlEnabledRequiresSingleStoreAndCheckbox() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setSingleStoreShardKeyOnHashKey(true);

    assertTrue(DvDdlSupport.isShardKeyDdlEnabled(config, singleStoreDatabaseMeta()));
    assertFalse(DvDdlSupport.isShardKeyDdlEnabled(config, null));

    config.setSingleStoreShardKeyOnHashKey(false);
    assertFalse(DvDdlSupport.isShardKeyDdlEnabled(config, singleStoreDatabaseMeta()));
  }

  @Test
  void isCreateTableDdlDetectsCreateStatements() {
    assertTrue(DvDdlSupport.isCreateTableDdl("CREATE TABLE foo (id INT);"));
    assertFalse(DvDdlSupport.isCreateTableDdl("ALTER TABLE foo ADD COLUMN bar INT;"));
    assertFalse(DvDdlSupport.isCreateTableDdl(""));
  }

  @Test
  void extractCreateTableNameParsesSimpleAndQualifiedNames() {
    assertEquals(
        "sat_syn_product",
        DvDdlSupport.extractCreateTableName(
            "CREATE TABLE sat_syn_product\n(\n  order_hk BYTEA\n);"));
    assertEquals(
        "sat_syn_product",
        DvDdlSupport.extractCreateTableName("CREATE TABLE vault.sat_syn_product (id INT);"));
    assertEquals(
        "sat_syn_product",
        DvDdlSupport.extractCreateTableName("CREATE TABLE \"sat_syn_product\" (id INT);"));
  }

  @Test
  void buildCreateTableStatementIncludesCompositePrimaryKeyClause() {
    DatabaseMeta databaseMeta = singleStoreDatabaseMeta();
    IRowMeta fields = hubLayout();

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            databaseMeta,
            new Variables(),
            "hub_customer",
            fields,
            null,
            java.util.List.of("customer_hk", "customer_id"),
            true);

    assertTrue(ddl.toUpperCase().contains("PRIMARY KEY"));
    assertTrue(ddl.contains("customer_id"));
    assertTrue(ddl.contains("customer_hk"));
  }

  @Test
  void buildCreateTableStatementOmitsPrimaryKeyWhenEmpty() {
    DatabaseMeta databaseMeta = postgresDatabaseMeta();
    IRowMeta fields = hubLayout();

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            databaseMeta, new Variables(), "hub_customer", fields, null, List.of(), true);

    assertFalse(ddl.toUpperCase().contains("PRIMARY KEY"));
    assertFalse(ddl.toUpperCase().contains("FOREIGN KEY"));
  }

  @Test
  void buildCreateTableStatementIncludesForeignKeyOnPostgres() {
    DatabaseMeta databaseMeta = postgresDatabaseMeta();
    IRowMeta fields = multiActiveSatelliteLayout();
    ForeignKeySpec fk =
        new ForeignKeySpec(
            "fk_sat_customer_phone_hub_customer",
            List.of("customer_hk"),
            "hub_customer",
            List.of("customer_hk"));

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            databaseMeta,
            new Variables(),
            "sat_customer_phone",
            fields,
            null,
            List.of("customer_hk", "phone_type", "LOAD_DATE"),
            List.of(fk),
            true);

    assertTrue(ddl.toUpperCase().contains("PRIMARY KEY"));
    assertTrue(ddl.toUpperCase().contains("FOREIGN KEY"));
    assertTrue(ddl.toUpperCase().contains("REFERENCES"));
    assertTrue(ddl.contains("hub_customer"));
  }

  @Test
  void buildCreateTableStatementOmitsForeignKeyOnSingleStore() {
    DatabaseMeta databaseMeta = singleStoreDatabaseMeta();
    IRowMeta fields = multiActiveSatelliteLayout();
    ForeignKeySpec fk =
        new ForeignKeySpec(
            "fk_sat_customer_phone_hub_customer",
            List.of("customer_hk"),
            "hub_customer",
            List.of("customer_hk"));

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            databaseMeta,
            new Variables(),
            "sat_customer_phone",
            fields,
            null,
            List.of("customer_hk"),
            List.of(fk),
            true);

    assertTrue(ddl.toUpperCase().contains("PRIMARY KEY"));
    assertFalse(ddl.toUpperCase().contains("FOREIGN KEY"));
    assertFalse(ddl.toUpperCase().contains("REFERENCES"));
  }

  @Test
  void supportsForeignKeyConstraintsFalseForSingleStore() {
    assertFalse(DvDdlSupport.supportsForeignKeyConstraints(singleStoreDatabaseMeta()));
    assertTrue(DvDdlSupport.supportsForeignKeyConstraints(postgresDatabaseMeta()));
  }

  @Test
  void deduplicateCreateTableDdlKeepsFirstStatementPerTable() {
    String first =
        "CREATE TABLE sat_syn_product\n(\n  product_hk BYTEA\n, product_code VARCHAR(8)\n);";
    String second =
        "CREATE TABLE sat_syn_product\n(\n  order_hk BYTEA\n, product_code VARCHAR(8)\n);";
    String link = "CREATE TABLE lnk_syn_order\n(\n  lnk_syn_order_hk BYTEA\n);";

    List<String> deduplicated =
        DvDdlSupport.deduplicateCreateTableDdl(Arrays.asList(first, second, link));

    assertEquals(2, deduplicated.size());
    assertEquals(first, deduplicated.get(0));
    assertEquals(link, deduplicated.get(1));
  }

  @Test
  void enrichSqlServerFieldDefinitionAppendsUtf8CollationAndExpandsLength() {
    DatabaseMeta sqlServer =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID);
    String definition = "customer_id VARCHAR(50)";
    String enriched = DvDdlSupport.enrichSqlServerFieldDefinition(sqlServer, definition);
    // NVARCHAR(50)-style character length must become UTF-8 byte length (×3) so multi-byte
    // values such as Portuguese addresses do not truncate (issue #91).
    assertEquals(
        "customer_id VARCHAR(150) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION, enriched);
  }

  @Test
  void enrichSqlServerDdlRewritesCreateAndAlterStrings() {
    DatabaseMeta sqlServer =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID);
    String create =
        "CREATE TABLE hub_item (\n  item_hk VARBINARY(16),\n  item_code VARCHAR(50)\n);";
    String rewritten = DvDdlSupport.enrichSqlServerDdl(sqlServer, create);
    assertTrue(
        rewritten.contains(
            "item_code VARCHAR(150) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION));
    assertFalse(rewritten.toUpperCase().contains("VARBINARY(16) COLLATE"));

    String alter = "ALTER TABLE hub_item ADD name VARCHAR(100)";
    assertTrue(
        DvDdlSupport.enrichSqlServerDdl(sqlServer, alter)
            .contains("VARCHAR(300) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION));
  }

  @Test
  void rewriteSqlServerStringCollationsDoesNotDoubleApply() {
    String already = "customer_id VARCHAR(150) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION;
    assertEquals(already, DvDdlSupport.rewriteSqlServerStringCollations(already));

    String plain = "customer_id VARCHAR(50), name CHAR(10), notes TEXT";
    String rewritten = DvDdlSupport.rewriteSqlServerStringCollations(plain);
    assertTrue(
        rewritten.contains("VARCHAR(150) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION));
    assertTrue(rewritten.contains("CHAR(30) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION));
    assertTrue(rewritten.contains("TEXT COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION));
    assertFalse(rewritten.toUpperCase().contains("NVARCHAR"));
  }

  @Test
  void rewriteSqlServerStringCollationsSkipsUnicodeTypes() {
    String sql = "code NVARCHAR(50), label NCHAR(10), body NTEXT, plain VARCHAR(20)";
    String rewritten = DvDdlSupport.rewriteSqlServerStringCollations(sql);
    assertTrue(rewritten.contains("NVARCHAR(50)"));
    assertFalse(rewritten.matches("(?is).*NVARCHAR\\(50\\)\\s+COLLATE.*"));
    assertTrue(rewritten.contains("VARCHAR(60) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION));
  }

  @Test
  void utf8ByteLengthForCharacterLengthUsesFactorThree() {
    assertEquals(0, DvDdlSupport.utf8ByteLengthForCharacterLength(0));
    assertEquals(-1, DvDdlSupport.utf8ByteLengthForCharacterLength(-1));
    assertEquals(150, DvDdlSupport.utf8ByteLengthForCharacterLength(50));
    // 3000 * 3 = 9000 > 8000 → VARCHAR(MAX) path
    assertEquals(9000, DvDdlSupport.utf8ByteLengthForCharacterLength(3000));
    assertTrue(DvDdlSupport.expandSqlServerUtf8StringType("VARCHAR", 3000).equals("VARCHAR(MAX)"));
    assertEquals("VARCHAR(MAX)", DvDdlSupport.expandSqlServerUtf8StringType("CHAR", 4000));
    assertEquals("VARCHAR(MAX)", DvDdlSupport.expandSqlServerUtf8StringType("VARCHAR", 8000));
    assertEquals(
        "notes VARCHAR(MAX) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION,
        DvDdlSupport.rewriteSqlServerStringCollations("notes VARCHAR(MAX)"));
  }

  @Test
  void rewriteSqlServerStringCollationsIsIdempotentOnExpandedDdl() {
    String once = DvDdlSupport.rewriteSqlServerStringCollations("addr VARCHAR(50)");
    String twice = DvDdlSupport.rewriteSqlServerStringCollations(once);
    assertEquals(once, twice);
    assertEquals("addr VARCHAR(150) COLLATE " + DvDdlSupport.SQL_SERVER_UTF8_COLLATION, once);
  }

  @Test
  void enrichSqlServerDdlIsNoOpForPostgres() {
    DatabaseMeta postgres =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);
    String ddl = "CREATE TABLE t (name VARCHAR(50));";
    assertEquals(ddl, DvDdlSupport.enrichSqlServerDdl(postgres, ddl));
  }

  @Test
  void isSnowflakeDetectsPluginId() {
    assertTrue(
        DvDdlSupport.isSnowflake(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID)));
    assertFalse(DvDdlSupport.isSnowflake(postgresDatabaseMeta()));
    assertFalse(DvDdlSupport.isSnowflake(null));
  }

  @Test
  void enrichSnowflakeFieldDefinitionRewritesBinaryVariantAndTimestamps() {
    DatabaseMeta snowflake =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID);
    ValueMetaBinary hashKey = new ValueMetaBinary("customer_hk");
    hashKey.setLength(16);
    assertEquals(
        "customer_hk BINARY(16)",
        DvDdlSupport.enrichSnowflakeFieldDefinition(snowflake, hashKey, "customer_hk VARIANT"));

    assertEquals(
        "x_load_ts TIMESTAMP_NTZ",
        DvDdlSupport.enrichSnowflakeFieldDefinition(
            snowflake, new ValueMetaTimestamp("x_load_ts"), "x_load_ts TIMESTAMP_LTZ"));
    assertEquals(
        "order_date DATE",
        DvDdlSupport.enrichSnowflakeFieldDefinition(
            snowflake, new ValueMetaDate("order_date"), "order_date TIMESTAMP_LTZ"));
  }

  @Test
  void enrichSnowflakeFieldDefinitionLeavesJsonVariantAndOtherEngines() {
    DatabaseMeta snowflake =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID);
    assertEquals(
        "payload VARIANT",
        DvDdlSupport.enrichSnowflakeFieldDefinition(
            snowflake, new ValueMetaInteger("payload"), "payload VARIANT"));
    assertEquals(
        "customer_hk VARIANT",
        DvDdlSupport.enrichSnowflakeFieldDefinition(
            postgresDatabaseMeta(), new ValueMetaBinary("customer_hk"), "customer_hk VARIANT"));
  }

  @Test
  void enrichSnowflakeDdlRewritesTimestampLtzOnly() {
    DatabaseMeta snowflake =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID);
    String ddl =
        "CREATE TABLE hub_customer (customer_hk VARIANT, x_load_ts TIMESTAMP_LTZ, payload VARIANT);";
    String rewritten = DvDdlSupport.enrichSnowflakeDdl(snowflake, ddl);
    assertTrue(rewritten.contains("TIMESTAMP_NTZ"));
    assertFalse(rewritten.contains("TIMESTAMP_LTZ"));
    assertTrue(rewritten.contains("customer_hk VARIANT"));
    assertEquals(ddl, DvDdlSupport.enrichSnowflakeDdl(postgresDatabaseMeta(), ddl));
  }

  @Test
  void snowflakePhysicalSqlTypeMapsBinaryTimestampAndDate() {
    ValueMetaBinary md5 = new ValueMetaBinary("hk");
    md5.setLength(16);
    assertEquals("BINARY(16)", DvDdlSupport.snowflakePhysicalSqlType(md5));
    ValueMetaBinary unbounded = new ValueMetaBinary("blob");
    unbounded.setLength(-1);
    assertEquals("BINARY", DvDdlSupport.snowflakePhysicalSqlType(unbounded));
    assertEquals(
        "TIMESTAMP_NTZ", DvDdlSupport.snowflakePhysicalSqlType(new ValueMetaTimestamp("ts")));
    assertEquals("DATE", DvDdlSupport.snowflakePhysicalSqlType(new ValueMetaDate("d")));
    assertEquals(null, DvDdlSupport.snowflakePhysicalSqlType(new ValueMetaString("name")));
  }

  private static DatabaseMeta singleStoreDatabaseMeta() {
    return new DatabaseMeta(
        "singlestore-test", "SingleStore (MemSQL)", "Native", "", "localhost", "test", "root", "");
  }

  private static DatabaseMeta postgresDatabaseMeta() {
    return databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }
    };
  }

  private static IRowMeta hubLayout() {
    RowMeta rowMeta = new RowMeta();
    ValueMetaBinary hashKey = new ValueMetaBinary("customer_hk");
    hashKey.setLength(16);
    rowMeta.addValueMeta(hashKey);
    rowMeta.addValueMeta(new ValueMetaString("customer_id"));
    return rowMeta;
  }

  private static IRowMeta multiActiveSatelliteLayout() {
    RowMeta rowMeta = new RowMeta();
    ValueMetaBinary hashKey = new ValueMetaBinary("customer_hk");
    hashKey.setLength(16);
    rowMeta.addValueMeta(hashKey);
    rowMeta.addValueMeta(new ValueMetaString("phone_type"));
    rowMeta.addValueMeta(new ValueMetaString("phone_number"));
    return rowMeta;
  }
}
