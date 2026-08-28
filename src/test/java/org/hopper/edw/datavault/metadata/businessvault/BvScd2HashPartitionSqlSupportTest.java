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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.database.DatabaseMeta;
import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.junit.jupiter.api.Test;

class BvScd2HashPartitionSqlSupportTest {

  @Test
  void emptyColumnReturnsNull() {
    assertNull(
        BvScd2HashPartitionSqlSupport.buildPredicate(
            database("POSTGRESQL"), HashKeyDataType.HEX, null));
  }

  @Test
  void postgresHexUsesBitCastOfFirstTwoChars() {
    String sql =
        BvScd2HashPartitionSqlSupport.buildPredicate(
            database("POSTGRESQL"), HashKeyDataType.HEX, "customer_hk");
    assertEquals(
        "('x' || substr(customer_hk, 1, 2))::bit(8)::int % ${PARTITION_COUNT} = ${PARTITION_NUMBER}",
        sql);
  }

  @Test
  void postgresBinaryUsesGetByte() {
    String sql =
        BvScd2HashPartitionSqlSupport.buildPredicate(
            database("POSTGRESQL"), HashKeyDataType.BINARY, "customer_hk");
    assertEquals("get_byte(customer_hk, 0) % ${PARTITION_COUNT} = ${PARTITION_NUMBER}", sql);
  }

  @Test
  void postgresStringUsesSplitPart() {
    String sql =
        BvScd2HashPartitionSqlSupport.buildPredicate(
            database("POSTGRESQL"), HashKeyDataType.STRING, "customer_hk");
    assertEquals(
        "split_part(customer_hk, '-', 1)::int % ${PARTITION_COUNT} = ${PARTITION_NUMBER}", sql);
  }

  @Test
  void mysqlAndSinglestoreBinaryMatchIssueSample() {
    for (String pluginId : new String[] {"MYSQL", "SINGLESTORE"}) {
      String sql =
          BvScd2HashPartitionSqlSupport.buildPredicate(
              database(pluginId), HashKeyDataType.BINARY, "hash_key_field");
      assertEquals(
          "CONV(HEX(SUBSTRING(hash_key_field, 1, 1)), 16, 10) % ${PARTITION_COUNT} = ${PARTITION_NUMBER}",
          sql, pluginId);
    }
  }

  @Test
  void mysqlHexUsesTwoCharacters() {
    String sql =
        BvScd2HashPartitionSqlSupport.buildPredicate(
            database("MYSQL"), HashKeyDataType.HEX, "customer_hk");
    assertEquals(
        "CONV(SUBSTRING(customer_hk, 1, 2), 16, 10) % ${PARTITION_COUNT} = ${PARTITION_NUMBER}",
        sql);
  }

  @Test
  void sqlServerNativeUsesMssqlDialect() {
    String sql =
        BvScd2HashPartitionSqlSupport.buildPredicate(
            database("MSSQLNATIVE"), HashKeyDataType.HEX, "customer_hk");
    assertTrue(sql.contains("CONVERT(varbinary(1), LEFT(customer_hk, 2), 2)"));
    assertTrue(sql.contains("${PARTITION_COUNT}"));
  }

  @Test
  void snowflakeHexUsesToNumber() {
    String sql =
        BvScd2HashPartitionSqlSupport.buildPredicate(
            database("SNOWFLAKE"), HashKeyDataType.HEX, "\"CUSTOMER_HK\"");
    assertEquals(
        "TO_NUMBER(SUBSTR(\"CUSTOMER_HK\", 1, 2), 'XX') % ${PARTITION_COUNT} = ${PARTITION_NUMBER}",
        sql);
  }

  @Test
  void unknownPluginIdDefaultsToPostgres() {
    String sql =
        BvScd2HashPartitionSqlSupport.buildPredicate(
            database("ORACLE"), HashKeyDataType.BINARY, "hk");
    assertEquals("get_byte(hk, 0) % ${PARTITION_COUNT} = ${PARTITION_NUMBER}", sql);
  }

  private static DatabaseMeta database(String pluginId) {
    return new TestDatabaseMeta("Vault", pluginId);
  }
}
