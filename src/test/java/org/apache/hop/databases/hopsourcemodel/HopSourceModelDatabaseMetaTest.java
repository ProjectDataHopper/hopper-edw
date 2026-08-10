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
package org.apache.hop.databases.hopsourcemodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HopSourceModelDatabaseMetaTest {

  private HopSourceModelDatabaseMeta meta;

  @BeforeEach
  void setUp() {
    meta = new HopSourceModelDatabaseMeta();
    meta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    meta.addDefaultOptions();
  }

  @Test
  void settings() {
    assertArrayEquals(new int[] {DatabaseMeta.TYPE_ACCESS_NATIVE}, meta.getAccessTypeList());
    assertEquals(HopSourceModelDatabaseMeta.DEFAULT_PORT, meta.getDefaultDatabasePort());
    assertEquals(HopSourceModelDatabaseMeta.DRIVER_CLASS, meta.getDriverClass());
    assertEquals("?", meta.getExtraOptionIndicator());
    assertEquals("&", meta.getExtraOptionSeparator());
    assertEquals("=", meta.getExtraOptionValueSeparator());
    assertTrue(meta.isSupportsSchemas());
    assertTrue(meta.isSupportsViews());
    assertTrue(meta.isSupportsBooleanDataType());
    assertFalse(meta.isSupportsTransactions());
    assertFalse(meta.isSupportsAutoInc());
    assertFalse(meta.isSupportsSequences());
    assertFalse(meta.isSupportsBitmapIndex());
    assertFalse(meta.isSupportsCatalogs());
    assertEquals(" LIMIT 5", meta.getLimitClause(5));
    assertEquals("\"", meta.getStartQuote());
    assertEquals("\"", meta.getEndQuote());
  }

  @Test
  void urlWithService() {
    assertEquals("jdbc:hop-hsm://hop.example:8182/crm", meta.getURL("hop.example", "8182", "crm"));
  }

  @Test
  void urlWithoutServiceListsAllSchemas() {
    assertEquals("jdbc:hop-hsm://localhost:8080", meta.getURL("localhost", "8080", null));
    assertEquals("jdbc:hop-hsm://localhost:8080", meta.getURL("localhost", "8080", ""));
  }

  @Test
  void urlDefaultsHostWhenBlank() {
    assertEquals("jdbc:hop-hsm://localhost:8080/retail", meta.getURL("", "8080", "retail"));
  }

  @Test
  void ddlNotSupported() {
    ValueMetaString col = new ValueMetaString("x");
    assertNull(meta.getAddColumnStatement("t", col, null, false, null, false));
    assertNull(meta.getModifyColumnStatement("t", col, null, false, null, false));
    assertNull(meta.getDropColumnStatement("t", col, null, false, null, false));
  }

  @Test
  void fieldExploreSql() {
    assertEquals("SELECT * FROM sat_order LIMIT 0", meta.getSqlQueryFields("sat_order"));
    assertEquals(
        "SELECT order_id FROM sat_order LIMIT 0", meta.getSqlColumnExists("order_id", "sat_order"));
  }
}
