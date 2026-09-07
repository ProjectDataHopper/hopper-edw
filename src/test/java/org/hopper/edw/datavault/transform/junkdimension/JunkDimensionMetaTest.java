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
package org.hopper.edw.datavault.transform.junkdimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.SqlStatement;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.DatabaseImpact;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.combinationlookup.KeyField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JunkDimensionMetaTest {

  private static final Class<?> PKG = JunkDimensionMeta.class;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void getSqlStatementsDoesNotRequireParentPipelineMeta() {
    JunkDimensionMeta meta = new JunkDimensionMeta();
    meta.setConnectionName("vault");
    meta.setTableName("d_orders_junk");

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta transformMeta = new TransformMeta("junk", meta);
    IRowMeta prev = new RowMeta();
    prev.addValueMeta(new ValueMetaString("order_status"));

    // Dialog SQL button wraps a temporary meta; parent pipeline is never set.
    assertNull(transformMeta.getParentPipelineMeta());

    SqlStatement sql =
        meta.getSqlStatements(new Variables(), pipelineMeta, transformMeta, prev, null);

    assertTrue(sql.hasError());
    assertEquals(
        BaseMessages.getString(PKG, "JunkDimensionMeta.ReturnValue.NotConnectionDefined"),
        sql.getError());
  }

  @Test
  void getSqlStatementsResolvesConnectionFromPipelineMeta() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setMetadataProvider(metadataProvider);

    JunkDimensionMeta meta = new JunkDimensionMeta();
    meta.setConnectionName("vault");
    meta.setTableName("d_orders_junk");

    TransformMeta transformMeta = new TransformMeta("junk", meta);
    assertNull(transformMeta.getParentPipelineMeta());

    SqlStatement sql =
        meta.getSqlStatements(
            new Variables(), pipelineMeta, transformMeta, new RowMeta(), metadataProvider);

    assertTrue(sql.hasError());
    assertEquals(
        BaseMessages.getString(PKG, "JunkDimensionMeta.ReturnValue.NotReceivingField"),
        sql.getError());
  }

  @Test
  void analyseImpactDoesNotRequireParentPipelineMeta() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("vault");
    databaseMeta.setDBName("vault_db");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setMetadataProvider(metadataProvider);
    pipelineMeta.setName("update-f-orders");

    JunkDimensionMeta meta = new JunkDimensionMeta();
    meta.setConnectionName("vault");
    meta.setTableName("d_orders_junk");
    meta.getFields().getKeyFields().add(new KeyField("order_status", "order_status"));

    TransformMeta transformMeta = new TransformMeta("junk", meta);
    assertNull(transformMeta.getParentPipelineMeta());

    IRowMeta prev = new RowMeta();
    prev.addValueMeta(new ValueMetaString("order_status"));

    List<DatabaseImpact> impact = new ArrayList<>();
    meta.analyseImpact(
        new Variables(),
        impact,
        pipelineMeta,
        transformMeta,
        prev,
        new String[0],
        new String[0],
        new RowMeta(),
        metadataProvider);

    assertFalse(impact.isEmpty());
    assertEquals("vault_db", impact.get(0).getDatabaseName());
    assertEquals("d_orders_junk", impact.get(0).getTable());
  }
}
