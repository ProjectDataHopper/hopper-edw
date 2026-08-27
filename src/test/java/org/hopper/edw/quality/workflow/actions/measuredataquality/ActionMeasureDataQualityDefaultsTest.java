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
package org.hopper.edw.quality.workflow.actions.measuredataquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.datavault.metrics.DvUpdateMetricsConstants;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher;
import org.hopper.edw.quality.model.DataQualityReport;
import org.hopper.edw.quality.model.QualityLifecycle;
import org.hopper.edw.quality.profile.DataProfileSnapshot;
import org.junit.jupiter.api.Test;

class ActionMeasureDataQualityDefaultsTest {

  @Test
  void defaultPersistFlagsAndLoadId() {
    ActionMeasureDataQuality action = new ActionMeasureDataQuality();
    assertFalse(action.isPersistHistory());
    assertFalse(action.isFailOnPersistError());
    assertTrue(action.isAutoCreateTables());
    assertTrue(action.isPublishCatalogDefinitions());
    assertEquals(ActionMeasureDataQuality.DEFAULT_LOAD_ID, action.getLoadId());
    assertEquals(
        "${" + DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID + "}", action.getLoadId());
    assertEquals(DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, action.getHistorySchema());
  }

  @Test
  void cloneCopiesHistoryFields() {
    ActionMeasureDataQuality original = new ActionMeasureDataQuality();
    original.setPersistHistory(true);
    original.setHistoryDatabase("OPS");
    original.setHistorySchema("custom_ops");
    original.setLoadId("my-load");
    original.setAutoCreateTables(false);
    original.setPublishCatalogDefinitions(false);
    original.setFailOnPersistError(true);

    ActionMeasureDataQuality copy = new ActionMeasureDataQuality(original);
    assertTrue(copy.isPersistHistory());
    assertEquals("OPS", copy.getHistoryDatabase());
    assertEquals("custom_ops", copy.getHistorySchema());
    assertEquals("my-load", copy.getLoadId());
    assertFalse(copy.isAutoCreateTables());
    assertFalse(copy.isPublishCatalogDefinitions());
    assertTrue(copy.isFailOnPersistError());
  }

  @Test
  void resolveHistoryDatabaseUsesFieldThenVariableThenSubject() {
    ActionMeasureDataQuality action = new ActionMeasureDataQuality();
    Variables variables = new Variables();
    action.copyFrom(variables);

    action.setHistoryDatabase("OPS");
    assertEquals("OPS", action.resolveHistoryDatabase(List.of(), null));

    action.setHistoryDatabase("");
    action.setVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_DATABASE, "FROM_VAR");
    assertEquals("FROM_VAR", action.resolveHistoryDatabase(List.of(), null));

    action.setVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_DATABASE, "");
    RecordDefinition def = new RecordDefinition();
    def.setKey(new RecordDefinitionKey("hop/retail-example/sources", "CRM-customer"));
    PhysicalTableRef table = new PhysicalTableRef();
    table.setDatabaseMetaName("Vault");
    table.setTableName("hub_customer");
    def.setPhysicalTable(table);

    DataQualityReport report = new DataQualityReport(QualityLifecycle.PRE_UPDATE);
    String subjectKey = "hop/retail-example/sources/CRM-customer";
    report.addSubjectKey(subjectKey);
    DataProfileSnapshot profile = new DataProfileSnapshot();
    profile.setSubjectKey(subjectKey);
    report.putProfile(subjectKey, profile);

    assertEquals("Vault", action.resolveHistoryDatabase(List.of(def), report));
  }

  @Test
  void resolveHistorySchemaDefaultsAndVariable() {
    ActionMeasureDataQuality action = new ActionMeasureDataQuality();
    Variables variables = new Variables();
    action.copyFrom(variables);

    action.setHistorySchema("");
    assertEquals(DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, action.resolveHistorySchema());

    action.setVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_SCHEMA, "ops2");
    assertEquals("ops2", action.resolveHistorySchema());

    action.setHistorySchema("explicit");
    assertEquals("explicit", action.resolveHistorySchema());
  }

  @Test
  void resolveLoadIdFallsBackToExecutionVariable() {
    ActionMeasureDataQuality action = new ActionMeasureDataQuality();
    Variables variables = new Variables();
    variables.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID, "exec-abc");
    action.copyFrom(variables);
    action.setLoadId(ActionMeasureDataQuality.DEFAULT_LOAD_ID);

    assertEquals("exec-abc", action.resolveLoadId());
  }
}
