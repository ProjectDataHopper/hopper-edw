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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseJdbcCatalogSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveCatalogSubstitutesDatabaseNameVariable() {
    DatabaseMeta meta = databaseMeta("${DB_NAME}");
    Variables variables = new Variables();
    variables.setVariable("DB_NAME", "test");

    assertEquals("test", DatabaseJdbcCatalogSupport.resolveCatalog(variables, meta));
  }

  @Test
  void resolveCatalogReturnsNullWhenVariableIsUnresolved() {
    DatabaseMeta meta = databaseMeta("${DB_NAME}");

    assertNull(DatabaseJdbcCatalogSupport.resolveCatalog(new Variables(), meta));
    assertNull(DatabaseJdbcCatalogSupport.resolveCatalog(null, meta));
  }

  @Test
  void resolveCatalogReturnsNullWhenDatabaseNameIsEmpty() {
    assertNull(DatabaseJdbcCatalogSupport.resolveCatalog(new Variables(), databaseMeta("")));
    assertNull(DatabaseJdbcCatalogSupport.resolveCatalog(new Variables(), null));
  }

  private static DatabaseMeta databaseMeta(String databaseName) {
    DatabaseMeta meta =
        new DatabaseMeta("CRM", "NONE", "Native", "localhost", databaseName, "1433", "sa", "");
    meta.setDBName(databaseName);
    return meta;
  }
}
