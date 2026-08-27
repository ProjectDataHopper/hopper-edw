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
package org.hopper.edw.catalog.harvest.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestStatus;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryPublisher.PublishContext;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryPublisher.PublishResult;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryPublisher.PublishStatus;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestSubjectSummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Round-trip publish + browse queries on H2 (same pattern as quality history publisher tests). */
class SchemaHarvestHistoryReaderBrowseTest {

  private static final String OPS_NAME = "OPS";

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void listRunsSubjectsChangesAndFields() throws Exception {
    String mem = "mem:schema_harvest_browse_" + UUID.randomUUID();
    DatabaseMeta h2 = buildH2DatabaseMeta(OPS_NAME, mem);
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(h2);

    String runId = "run-" + UUID.randomUUID();
    String subjectKey = "hop/retail-example/sources/E2E-customer";
    HarvestResult result =
        HarvestResult.builder()
            .harvestRunId(runId)
            .resourceGroupName("retail-sources")
            .status(HarvestStatus.SUCCESS)
            .expectedBaseline("WORKING")
            .scopeSummary("group=retail-sources")
            .subjects(
                List.of(
                    HarvestSubjectResult.builder()
                        .subjectKey(subjectKey)
                        .sourceType("DATABASE")
                        .databaseMetaName("CRM")
                        .schemaName("public")
                        .tableName("customer")
                        .discoveryStatus(DiscoveryStatus.OK)
                        .inSync(false)
                        .fields(
                            List.of(
                                HarvestedField.builder()
                                    .role(FieldRole.EXPECTED)
                                    .fieldName("id")
                                    .hopType("Integer")
                                    .primaryKeyPosition(1)
                                    .build(),
                                HarvestedField.builder()
                                    .role(FieldRole.DISCOVERED)
                                    .fieldName("id")
                                    .hopType("Integer")
                                    .primaryKeyPosition(1)
                                    .build()))
                        .changes(
                            List.of(
                                HarvestChange.builder()
                                    .changeKind("CHANGED")
                                    .fieldName("address")
                                    .actualDetail("length 50 -> 75")
                                    .severity("WARNING")
                                    .build()))
                        .build()))
            .build();

    PublishResult published =
        SchemaHarvestHistoryPublisher.publish(
            new LogChannel("test"),
            result,
            new PublishContext(OPS_NAME, "", null, false, true, true),
            new Variables(),
            metadataProvider);
    assertEquals(PublishStatus.INSERTED, published.status());

    Variables variables = new Variables();
    List<HarvestRunSummary> runs =
        SchemaHarvestHistoryReader.listRuns(h2, "", "retail-sources", variables, 20);
    assertTrue(runs.stream().anyMatch(r -> runId.equals(r.harvestRunId())));

    List<HarvestSubjectSummary> subjects =
        SchemaHarvestHistoryReader.listSubjectsForRun(
            h2, "", runId, "CRM", "DATABASE", true, variables);
    assertEquals(1, subjects.size());
    assertEquals(subjectKey, subjects.get(0).subjectKey());
    assertEquals(1L, subjects.get(0).changeCount());

    List<HarvestSubjectSummary> timeline =
        SchemaHarvestHistoryReader.listSubjectHistory(h2, "", subjectKey, variables, 20);
    assertTrue(timeline.stream().anyMatch(s -> runId.equals(s.harvestRunId())));

    List<HarvestChange> changes =
        SchemaHarvestHistoryReader.listChangesForSubject(h2, "", runId, subjectKey, variables);
    assertEquals(1, changes.size());
    assertEquals("CHANGED", changes.get(0).getChangeKind());

    List<HarvestedField> fields =
        SchemaHarvestHistoryReader.listFieldsForSubject(h2, "", runId, subjectKey, variables);
    assertEquals(2, fields.size());
    assertNotNull(fields.get(0).getRole());
  }

  private static DatabaseMeta buildH2DatabaseMeta(String name, String dbName) {
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName(name);
    databaseMeta.setDatabaseType("H2");
    databaseMeta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    databaseMeta.setDBName(dbName + ";DB_CLOSE_DELAY=-1");
    databaseMeta.setHostname("");
    databaseMeta.setPort("");
    databaseMeta.setUsername("sa");
    databaseMeta.setPassword("");
    return databaseMeta;
  }
}
