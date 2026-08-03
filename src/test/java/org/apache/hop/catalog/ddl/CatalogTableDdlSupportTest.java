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
package org.apache.hop.catalog.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.SourceField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CatalogTableDdlSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveTargetPrefersOverridesOverPhysicalTableRef() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/test/sources", "CRM-customer"));
    PhysicalTableRef physicalTable = new PhysicalTableRef();
    physicalTable.setDatabaseMetaName("CRM");
    physicalTable.setSchemaName("public");
    physicalTable.setTableName("customer");
    definition.setPhysicalTable(physicalTable);

    CatalogTableDdlSupport.ResolvedTarget target =
        CatalogTableDdlSupport.resolveTarget(
            definition, "Vault", "dv", "hub_customer", new Variables());

    assertEquals("Vault", target.connectionName());
    assertEquals("dv", target.schemaName());
    assertEquals("hub_customer", target.tableName());
  }

  @Test
  void buildTableDdlScriptIncludesDropWhenRequested() throws HopException {
    SourceField id = field("id", IValueMeta.TYPE_INTEGER);
    id.setPrimaryKeyPosition(1);

    String ddl =
        CatalogTableDdlSupport.buildTableDdlScript(
            postgreSqlDatabaseMeta(),
            new Variables(),
            "public",
            "customer",
            List.of(id),
            true,
            true);

    assertTrue(ddl.toUpperCase().contains("DROP TABLE IF EXISTS"));
    assertTrue(ddl.toUpperCase().contains("CREATE TABLE"));
  }

  @Test
  void generateCreateTableDdlIncludesSingleColumnPrimaryKeyClause() throws HopException {
    SourceField customerId = field("customer_id", IValueMeta.TYPE_INTEGER);
    customerId.setLength("9");
    customerId.setPrecision("0");
    customerId.setPrimaryKeyPosition(1);
    SourceField loadDate = field("load_date", IValueMeta.TYPE_TIMESTAMP);
    SourceField recordSource = field("record_source", IValueMeta.TYPE_STRING);
    recordSource.setLength("30");

    String ddl =
        CatalogTableDdlSupport.generateCreateTableDdl(
            postgreSqlDatabaseMeta(),
            new Variables(),
            "",
            "customer_hub",
            List.of(customerId, loadDate, recordSource),
            true);

    assertTrue(ddl.toUpperCase().contains("CREATE TABLE"));
    assertTrue(ddl.contains("PRIMARY KEY"));
    assertTrue(ddl.contains("customer_id"));
    assertTrue(ddl.toUpperCase().contains("INTEGER"));
    assertFalse(ddl.toUpperCase().contains("BIGSERIAL"));
  }

  @Test
  void generateCreateTableDdlIncludesCompositePrimaryKeyClause() throws HopException {
    SourceField tenantId = field("tenant_id", IValueMeta.TYPE_INTEGER);
    tenantId.setPrimaryKeyPosition(1);
    SourceField customerId = field("customer_id", IValueMeta.TYPE_INTEGER);
    customerId.setPrimaryKeyPosition(2);
    SourceField name = field("name", IValueMeta.TYPE_STRING);
    name.setLength("100");

    String ddl =
        CatalogTableDdlSupport.generateCreateTableDdl(
            postgreSqlDatabaseMeta(),
            new Variables(),
            "public",
            "customer",
            List.of(tenantId, customerId, name),
            true);

    assertTrue(ddl.toUpperCase().contains("CREATE TABLE"));
    assertTrue(ddl.contains("PRIMARY KEY"));
    assertTrue(ddl.contains("tenant_id"));
    assertTrue(ddl.contains("customer_id"));
  }

  private static SourceField field(String name, int hopType) {
    SourceField field = new SourceField(name);
    field.setHopType(hopType);
    field.setSourceDataType("String");
    return field;
  }

  private static DatabaseMeta postgreSqlDatabaseMeta() {
    return new DatabaseMeta(
        "postgres-test", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
  }
}
