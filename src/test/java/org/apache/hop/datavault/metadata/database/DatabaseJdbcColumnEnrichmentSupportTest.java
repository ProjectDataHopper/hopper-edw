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
package org.apache.hop.datavault.metadata.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseJdbcColumnEnrichmentSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void enrichValueMetaFixesVarcharDisplaySizeUsingColumnSize() throws Exception {
    IValueMeta bogus = new ValueMetaString("customer_name");
    bogus.setLength(255); // ResultSet display size noise
    bogus.setOriginalColumnTypeName("VARCHAR");

    DatabaseJdbcColumnEnrichmentSupport.JdbcColumn col =
        new DatabaseJdbcColumnEnrichmentSupport.JdbcColumn("customer_name", "VARCHAR", 150, 0);

    IValueMeta enriched = DatabaseJdbcColumnEnrichmentSupport.enrichValueMeta(bogus, col);
    assertEquals(IValueMeta.TYPE_STRING, enriched.getType());
    assertEquals(150, enriched.getLength());
    assertEquals("VARCHAR", enriched.getOriginalColumnTypeName());
  }

  @Test
  void enrichValueMetaMapsDatetimeStringToTimestamp() throws Exception {
    IValueMeta bogus = new ValueMetaString("load_dts");
    bogus.setLength(255);
    bogus.setOriginalColumnTypeName("DATETIME");

    DatabaseJdbcColumnEnrichmentSupport.JdbcColumn col =
        new DatabaseJdbcColumnEnrichmentSupport.JdbcColumn("load_dts", "DATETIME", 26, 6);

    IValueMeta enriched = DatabaseJdbcColumnEnrichmentSupport.enrichValueMeta(bogus, col);
    assertEquals(IValueMeta.TYPE_TIMESTAMP, enriched.getType());
    assertEquals(6, enriched.getLength());
  }

  @Test
  void enrichValueMetaFixesLongtextDisplay255() throws Exception {
    IValueMeta bogus = new ValueMetaString("notes");
    bogus.setLength(255);
    bogus.setOriginalColumnTypeName("LONGTEXT");

    DatabaseJdbcColumnEnrichmentSupport.JdbcColumn col =
        new DatabaseJdbcColumnEnrichmentSupport.JdbcColumn(
            "notes", "LONGTEXT", Integer.MAX_VALUE, 0);

    IValueMeta enriched = DatabaseJdbcColumnEnrichmentSupport.enrichValueMeta(bogus, col);
    assertEquals(IValueMeta.TYPE_STRING, enriched.getType());
    assertTrue(enriched.getLength() >= org.apache.hop.core.database.DatabaseMeta.CLOB_LENGTH);
  }

  @Test
  void identicalEnrichedVarchar150LengthsMatch() throws Exception {
    DatabaseJdbcColumnEnrichmentSupport.JdbcColumn col =
        new DatabaseJdbcColumnEnrichmentSupport.JdbcColumn("name", "VARCHAR", 150, 0);

    IValueMeta source =
        DatabaseJdbcColumnEnrichmentSupport.enrichValueMeta(
            ValueMetaFactory.createValueMeta("name", IValueMeta.TYPE_STRING, 255, -1), col);
    IValueMeta target =
        DatabaseJdbcColumnEnrichmentSupport.enrichValueMeta(
            ValueMetaFactory.createValueMeta("name", IValueMeta.TYPE_STRING, 255, -1), col);

    assertEquals(150, source.getLength());
    assertEquals(150, target.getLength());
    assertEquals(source.getLength(), target.getLength());
  }

  private static void assertTrue(boolean condition) {
    org.junit.jupiter.api.Assertions.assertTrue(condition);
  }
}
