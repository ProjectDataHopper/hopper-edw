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
package org.hopper.edw.datavault.virtualization.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonField;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonParentKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.transform.sqlexpression.SqlExpressionMeta;
import org.hopper.edw.datavault.virtualization.sql.SourceModelFreeSqlTableSupport;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlEngine;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HopSourceModelJdbcDriverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void acceptsHopHsmLocalUrlsOnly() throws Exception {
    HopSourceModelJdbcDriver driver = new HopSourceModelJdbcDriver();
    assertTrue(driver.acceptsURL("jdbc:hop-hsm:file=/tmp/model.hsm"));
    assertTrue(driver.acceptsURL("jdbc:hop-hsm:/path/to/x.hsm"));
    // Remote URLs are owned by the thin hop-hsm-jdbc client
    assertFalse(driver.acceptsURL("jdbc:hop-hsm://host:8182/hop/sourceModelData?modelName=x"));
    assertFalse(driver.acceptsURL("jdbc:hop-hsm:https://host/hop/sourceModelData?modelName=x"));
    assertFalse(driver.acceptsURL("jdbc:postgresql://localhost/db"));
    assertFalse(driver.acceptsURL(null));
  }

  @Test
  void parseUrlExtractsFileAndRowLimit() throws Exception {
    Properties info = new Properties();
    HopSourceModelJdbcDriver.ParsedUrl parsed =
        HopSourceModelJdbcDriver.parseUrl("jdbc:hop-hsm:file=/data/crm.hsm;rowLimit=50", info);
    assertEquals("/data/crm.hsm", parsed.hsmPath());
    assertEquals(50, parsed.rowLimit());
  }

  @Test
  void parseUrlPlainPath() throws Exception {
    HopSourceModelJdbcDriver.ParsedUrl parsed =
        HopSourceModelJdbcDriver.parseUrl("jdbc:hop-hsm:/models/a.hsm", new Properties());
    assertEquals("/models/a.hsm", parsed.hsmPath());
  }

  @Test
  void parseUrlMissingPathFails() {
    assertThrows(
        SQLException.class,
        () -> HopSourceModelJdbcDriver.parseUrl("jdbc:hop-hsm:", new Properties()));
  }

  @Test
  void resultSetIteratesRowsByIndexAndName() throws Exception {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaInteger("id"));
    meta.addValueMeta(new ValueMetaString("name"));
    List<RowMetaAndData> rows =
        List.of(new RowMetaAndData(meta, 1L, "alice"), new RowMetaAndData(meta, 2L, "bob"));
    try (HopSourceModelJdbcResultSet rs = new HopSourceModelJdbcResultSet(null, rows)) {
      assertTrue(rs.next());
      assertEquals(1L, rs.getLong(1));
      assertEquals("alice", rs.getString("name"));
      assertTrue(rs.next());
      assertEquals(2, rs.getInt("id"));
      assertEquals("bob", rs.getString(2));
      assertFalse(rs.next());
      assertEquals(2, rs.getMetaData().getColumnCount());
      assertEquals("id", rs.getMetaData().getColumnName(1));
    }
  }

  @Test
  void databaseMetaDataListsTablesAndColumns() throws Exception {
    SourceModel model = new SourceModel();
    model.setName("crm");
    SourceTable customer = new SourceTable();
    customer.setName("customer");
    SourceColumn id = new SourceColumn();
    id.setName("customer_id");
    id.setHopType(IValueMeta.TYPE_INTEGER);
    id.setPrimaryKeyPosition(1);
    SourceColumn name = new SourceColumn();
    name.setName("name");
    name.setHopType(IValueMeta.TYPE_STRING);
    customer.getColumns().add(id);
    customer.getColumns().add(name);
    model.getTables().add(customer);

    HopSourceModelJdbcConnection conn = new HopSourceModelJdbcConnection(model, null, null, 0);
    DatabaseMetaData md = conn.getMetaData();
    assertEquals("Hop Source Model (hop-hsm)", md.getDatabaseProductName());
    assertTrue(md.isReadOnly());

    try (ResultSet tables = md.getTables(null, null, "%", new String[] {"TABLE"})) {
      assertTrue(tables.next());
      assertEquals("customer", tables.getString("TABLE_NAME"));
      assertEquals("TABLE", tables.getString("TABLE_TYPE"));
      assertFalse(tables.next());
    }

    try (ResultSet cols = md.getColumns(null, null, "customer", "%")) {
      assertTrue(cols.next());
      assertEquals("customer_id", cols.getString("COLUMN_NAME"));
      assertTrue(cols.next());
      assertEquals("name", cols.getString("COLUMN_NAME"));
      assertFalse(cols.next());
    }

    try (ResultSet pks = md.getPrimaryKeys(null, "source", "customer")) {
      assertTrue(pks.next());
      assertEquals("customer_id", pks.getString("COLUMN_NAME"));
    }
  }

  @Test
  void connectionIsReadOnlyAndCreatesStatement() throws Exception {
    SourceModel model = new SourceModel();
    model.setName("empty");
    HopSourceModelJdbcConnection conn = new HopSourceModelJdbcConnection(model, null, null, 10);
    assertTrue(conn.isReadOnly());
    assertEquals(10, conn.defaultRowLimit());
    try (Statement st = conn.createStatement()) {
      assertNotNull(st);
      assertEquals(10, st.getMaxRows());
    }
    conn.close();
    assertTrue(conn.isClosed());
  }

  @Test
  void queryableObjectNamesIncludesTablesAndQueries() {
    SourceModel model = new SourceModel();
    SourceTable t = new SourceTable();
    t.setName("customer");
    model.getTables().add(t);
    SourceQuery q = new SourceQuery();
    q.setName("feed_customer");
    model.getQueries().add(q);
    List<String> names = SourceModelFreeSqlTableSupport.queryableObjectNames(model);
    assertTrue(names.contains("customer"));
    assertTrue(names.contains("feed_customer"));
    String snippet =
        SourceModelFreeSqlTableSupport.insertTablesSqlSnippet(List.of("customer", "address"));
    assertTrue(snippet.contains("FROM customer"));
    assertTrue(snippet.contains("address"));
  }

  @Test
  void jdbcPlannerEmitsSqlExpressionForResidualCase() throws Exception {
    SourceModel model = new SourceModel();
    model.setName("crm");
    SourceTable orders = new SourceTable("orders");
    orders.setDatabaseName("CRM");
    orders.setTableName("orders");
    SourceColumn payload = new SourceColumn("payload");
    payload.setHopType(IValueMeta.TYPE_STRING);
    orders.getColumns().add(payload);
    model.getTables().add(orders);

    SourceJson json = new SourceJson("order_events");
    json.setParentSourceKind(SourceJsonParentKind.TABLE);
    json.setParentSourceName("orders");
    json.setJsonFieldName("payload");
    SourceJsonField eventId = new SourceJsonField();
    eventId.setName("event_id");
    eventId.setHopType(IValueMeta.TYPE_STRING);
    eventId.setPath("$.event_id");
    json.getFields().add(eventId);
    model.getJsonSources().add(json);

    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    DatabaseMeta db = new DatabaseMeta();
    db.setName("CRM");
    db.setDatabaseType("POSTGRESQL");
    db.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    db.setHostname("localhost");
    db.setDBName("test");
    db.setPort("5432");
    metadata.getSerializer(DatabaseMeta.class).save(db);

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT CASE WHEN event_id IS NULL THEN 'n' ELSE event_id END AS event_or_n FROM order_events",
            new Variables(),
            metadata);

    assertFalse(plan.fullPushdown(), plan.explainText());
    assertTrue(
        plan.pipelineMeta().getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof SqlExpressionMeta),
        plan.explainText());
  }

  @Test
  void connectRejectsNonMatchingUrl() throws Exception {
    HopSourceModelJdbcDriver driver = new HopSourceModelJdbcDriver();
    assertNull(driver.connect("jdbc:h2:mem:test", new Properties()));
  }
}
