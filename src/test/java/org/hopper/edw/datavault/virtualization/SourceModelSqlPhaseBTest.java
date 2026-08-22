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
package org.hopper.edw.datavault.virtualization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonField;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlEngine;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlPlan;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceModelSqlPhaseBTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void groupByPushesDownOnSingleConnection() throws Exception {
    SourceModel model = sampleDbModel("CRM");
    IHopMetadataProvider metadata = memoryWithDb("CRM");

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT city, COUNT(*) AS cnt, SUM(amount) AS total " + "FROM orders GROUP BY city",
            new Variables(),
            metadata);

    assertTrue(plan.fullPushdown(), plan.explainText());
    assertEqualsOneTableInput(plan);
    String sql = plan.pushdownSqlFragments().get(0).toUpperCase();
    assertTrue(sql.contains("GROUP"), plan.explainText());
    assertTrue(sql.contains("COUNT") || sql.contains("SUM"), plan.explainText());
  }

  @Test
  void arithmeticExpressionPushesDownOnSingleConnection() throws Exception {
    SourceModel model = sampleDbModel("CRM");
    IHopMetadataProvider metadata = memoryWithDb("CRM");

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT order_id, amount * 1.1 AS amount_tax FROM orders",
            new Variables(),
            metadata);

    assertTrue(plan.fullPushdown(), plan.explainText());
    assertNotNull(plan.outputRowMeta());
    assertTrue(plan.outputRowMeta().size() >= 2);
  }

  @Test
  void residualMergeJoinInfoStreamsAreResolved() throws Exception {
    // Two DB connections force residual Sort+MergeJoin (not full pushdown).
    SourceModel model = new SourceModel();
    model.setName("dual");
    model.getTables().add(dbTable("orders", "CRM", "order_id", "city"));
    model.getTables().add(dbTable("shipments", "WMS", "order_id", "message_id"));
    IHopMetadataProvider metadata = memoryWithDb("CRM", "WMS");

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT o.order_id, s.message_id FROM orders o "
                + "INNER JOIN shipments s ON o.order_id = s.order_id",
            new Variables(),
            metadata);

    assertFalse(plan.fullPushdown(), plan.explainText());
    boolean foundWiredMerge = false;
    for (var tm : plan.pipelineMeta().getTransforms()) {
      if (tm.getTransform()
          instanceof org.apache.hop.pipeline.transforms.mergejoin.MergeJoinMeta mj) {
        var streams = mj.getTransformIOMeta().getInfoStreams();
        assertTrue(streams.size() >= 2);
        assertNotNull(streams.get(0).getTransformMeta(), "left INFO stream unresolved");
        assertNotNull(streams.get(1).getTransformMeta(), "right INFO stream unresolved");
        foundWiredMerge = true;
      }
    }
    assertTrue(foundWiredMerge, "Expected a MergeJoin in residual plan: " + plan.explainText());
  }

  @Test
  void jsonTableIsInSchemaAndForcesResidual() throws Exception {
    SourceModel model = sampleDbModel("CRM");
    // Parent DB table needs a JSON payload column for SourceJson expansion.
    SourceTable orders = model.findTable("orders");
    orders.getColumns().add(col("payload", 2));

    SourceJson json = new SourceJson("order_events");
    json.setParentSourceKind(
        org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonParentKind.TABLE);
    json.setParentSourceName("orders");
    json.setJsonFieldName("payload");
    SourceJsonField f = new SourceJsonField();
    f.setName("event_id");
    f.setHopType(2);
    f.setPath("$.event_id");
    json.getFields().add(f);
    SourceJsonField f2 = new SourceJsonField();
    f2.setName("order_id");
    f2.setHopType(5);
    f2.setPassThrough(true);
    f2.setParentFieldName("order_id");
    json.getFields().add(f2);
    model.getJsonSources().add(json);

    IHopMetadataProvider metadata = memoryWithDb("CRM");

    // Validate parses against JSON table (schema check).
    SourceModelSqlEngine.validate(model, "SELECT event_id FROM order_events", new Variables());

    // Join DB + JSON cannot full-pushdown.
    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT o.order_id, e.event_id FROM orders o "
                + "INNER JOIN order_events e ON o.order_id = e.order_id",
            new Variables(),
            metadata);

    assertFalse(plan.fullPushdown(), plan.explainText());
    assertTrue(
        plan.explainText().toLowerCase().contains("json")
            || plan.residualOperators().stream().anyMatch(s -> s.toLowerCase().contains("json")),
        plan.explainText());
    // Residual plan should include JSON expansion subgraph and a join.
    assertTrue(plan.pipelineMeta().nrTransforms() > 1, plan.explainText());
  }

  private static void assertEqualsOneTableInput(SourceModelSqlPlan plan) {
    assertTrue(plan.pipelineMeta().nrTransforms() >= 1);
    assertTrue(
        plan.pipelineMeta().getTransforms().stream()
            .anyMatch(t -> "TableInput".equals(t.getTransformPluginId())));
  }

  private static SourceModel sampleDbModel(String dbName) {
    SourceModel model = new SourceModel();
    model.setName("sample");
    SourceTable orders = dbTable("orders", dbName, "order_id", "city", "amount");
    // amount is TYPE_NUMBER (1) for SUM tests
    orders.findColumn("amount").setHopType(1);
    model.getTables().add(orders);
    return model;
  }

  private static SourceTable dbTable(String name, String dbName, String... columns) {
    SourceTable table = new SourceTable(name);
    table.setDatabaseName(dbName);
    table.setSchemaName("public");
    table.setTableName(name);
    for (String c : columns) {
      int hopType = "order_id".equals(c) || c.endsWith("_id") ? 5 : 2;
      table.getColumns().add(col(c, hopType));
    }
    return table;
  }

  private static SourceColumn col(String name, int hopType) {
    SourceColumn c = new SourceColumn(name);
    c.setHopType(hopType);
    return c;
  }

  private static IHopMetadataProvider memoryWithDb(String... names) throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    for (String name : names) {
      if (provider.getSerializer(DatabaseMeta.class).exists(name)) {
        continue;
      }
      DatabaseMeta db = new DatabaseMeta();
      db.setName(name);
      db.setDatabaseType("POSTGRESQL");
      db.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
      db.setHostname("localhost");
      db.setDBName("test");
      db.setPort("5432");
      db.setUsername("test");
      db.setPassword("test");
      provider.getSerializer(DatabaseMeta.class).save(db);
    }
    return provider;
  }
}
