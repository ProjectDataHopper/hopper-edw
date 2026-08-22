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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.actions.sql.ActionSql;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvReferenceDeleteInsertSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void sameDatabaseRequiresMatchingConnectionNames() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DataVaultSource source = databaseSource("CRM-country", "Vault");
    assertTrue(
        DvReferenceDeleteInsertSupport.isSameDatabaseAsTarget(
            source, "Vault", new Variables(), provider));
    assertFalse(
        DvReferenceDeleteInsertSupport.isSameDatabaseAsTarget(
            source, "Other", new Variables(), provider));
  }

  @Test
  void deleteSqlUsesExistsJoinOnNaturalKeysPostgresStyle() throws Exception {
    DatabaseMeta db = postgresMeta("Vault");
    DvDatabaseSource source = new DvDatabaseSource();
    source.setDatabaseName("Vault");
    source.setSchemaName("stage");
    source.setTableName("country_delta");

    List<BusinessKey> keys = new ArrayList<>();
    BusinessKey code = new BusinessKey("code");
    code.setSourceFieldName("country_cd");
    keys.add(code);
    BusinessKey cdc = new BusinessKey("x_src_cdc_ts");
    cdc.setSourceFieldName("x_src_cdc_ts");
    keys.add(cdc);

    String sql =
        DvReferenceDeleteInsertSupport.buildDeleteByNaturalKeysSql(
            db, "ref_country", source, keys, new Variables());

    assertTrue(sql.startsWith("DELETE FROM "), () -> sql);
    assertTrue(sql.contains("EXISTS"), () -> sql);
    assertTrue(sql.contains("country_delta"), () -> sql);
    assertTrue(sql.contains("ref_country"), () -> sql);
    assertTrue(sql.contains("country_cd"), () -> sql);
    assertTrue(sql.contains("x_src_cdc_ts"), () -> sql);
    assertFalse(sql.contains("DELETE t FROM"), () -> sql);
  }

  @Test
  void deleteSqlUsesMysqlAliasForm() throws Exception {
    DatabaseMeta db =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return "MYSQL";
          }
        };
    db.setName("Vault");
    DvDatabaseSource source = new DvDatabaseSource();
    source.setDatabaseName("Vault");
    source.setTableName("country_delta");
    BusinessKey code = new BusinessKey("code");
    code.setSourceFieldName("code");

    String sql =
        DvReferenceDeleteInsertSupport.buildDeleteByNaturalKeysSql(
            db, "ref_country", source, List.of(code), new Variables());

    assertTrue(sql.startsWith("DELETE t FROM "), () -> sql);
    assertTrue(sql.contains("EXISTS"), () -> sql);
  }

  @Test
  void buildDeleteInsertWorkflowChainsSqlThenPipeline() throws Exception {
    PipelineMeta insert = new PipelineMeta();
    insert.setName("ref_country-insert");
    String deleteSql =
        "DELETE FROM ref_country WHERE EXISTS (SELECT 1 FROM country_delta s WHERE ref_country.code = s.code)";

    WorkflowMeta workflow =
        DvReferenceDeleteInsertSupport.buildDeleteInsertWorkflow(
            "ref-country-delete-insert",
            "Vault",
            List.of(new DvReferenceDeleteInsertSupport.DeleteInsertStep(deleteSql, insert)),
            StubPipelineAction::new);

    assertEquals("ref-country-delete-insert", workflow.getName());
    List<ActionMeta> actions = workflow.getActions();
    assertTrue(actions.size() >= 3, "start + sql + pipeline");

    ActionSql sqlAction =
        (ActionSql)
            actions.stream()
                .map(ActionMeta::getAction)
                .filter(a -> a instanceof ActionSql)
                .findFirst()
                .orElseThrow();
    assertEquals("Vault", sqlAction.getConnection());
    assertTrue(sqlAction.getSql().contains("DELETE"), sqlAction.getSql());
    assertTrue(sqlAction.isSendOneStatement());
  }

  private static final class StubPipelineAction extends ActionBase implements IAction {
    private String filename;

    StubPipelineAction(String name) {
      super(name, "");
      setPluginId(DvMultiSourceUpdateWorkflowSupport.PIPELINE_ACTION_ID);
    }

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      this.filename = filename;
    }

    @Override
    public org.apache.hop.core.Result execute(org.apache.hop.core.Result prevResult, int nr) {
      return prevResult != null ? prevResult : new org.apache.hop.core.Result();
    }
  }

  private static DataVaultSource databaseSource(String name, String databaseName) {
    DataVaultSource source = new DataVaultSource(name);
    DvDatabaseSource db = new DvDatabaseSource();
    db.setDatabaseName(databaseName);
    db.setTableName("country_delta");
    source.setSource(db);
    return source;
  }

  private static DatabaseMeta postgresMeta(String name) {
    DatabaseMeta db =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return "POSTGRESQL";
          }
        };
    db.setName(name);
    return db;
  }
}
