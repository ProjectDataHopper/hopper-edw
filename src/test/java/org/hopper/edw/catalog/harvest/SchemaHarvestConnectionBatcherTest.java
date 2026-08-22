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
package org.hopper.edw.catalog.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.junit.jupiter.api.Test;

class SchemaHarvestConnectionBatcherTest {

  @Test
  void partitionCountDistinctConnections() {
    assertEquals(0, SchemaHarvestConnectionBatcher.partitionCount(List.of()));
    assertEquals(1, SchemaHarvestConnectionBatcher.partitionCount(List.of("CRM", "crm", " CRM ")));
    assertEquals(2, SchemaHarvestConnectionBatcher.partitionCount(List.of("CRM", "ERP", "crm")));
  }

  @Test
  void countByConnectionGroupsDatabaseSubjects() {
    List<SchemaHarvestSubjectResolver.ResolvedSubject> subjects = new ArrayList<>();
    subjects.add(subject("ns", "a", "CRM", "public", "t1"));
    subjects.add(subject("ns", "b", "CRM", "public", "t2"));
    subjects.add(subject("ns", "c", "ERP", "dbo", "t3"));

    Map<String, Integer> counts = SchemaHarvestConnectionBatcher.countByConnection(subjects);
    assertEquals(2, counts.get("CRM"));
    assertEquals(1, counts.get("ERP"));
    assertEquals(2, counts.size());
  }

  @Test
  void countByConnectionIgnoresNonDatabase() {
    RecordDefinition fileDef = new RecordDefinition();
    fileDef.setKey(new RecordDefinitionKey("ns", "file1"));
    fileDef.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dv = new DvSourceRecord();
    dv.setSourceType("CSV");
    fileDef.setDvSource(dv);

    List<SchemaHarvestSubjectResolver.ResolvedSubject> subjects =
        List.of(
            new SchemaHarvestSubjectResolver.ResolvedSubject(
                fileDef.getKey(), "cat", fileDef, List.of()));

    assertTrue(SchemaHarvestConnectionBatcher.countByConnection(subjects).isEmpty());
  }

  private static SchemaHarvestSubjectResolver.ResolvedSubject subject(
      String ns, String name, String connection, String schema, String table) {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(ns, name));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dv = new DvSourceRecord();
    dv.setSourceType("DATABASE");
    definition.setDvSource(dv);
    PhysicalTableRef physical = new PhysicalTableRef();
    physical.setDatabaseMetaName(connection);
    physical.setSchemaName(schema);
    physical.setTableName(table);
    definition.setPhysicalTable(physical);
    return new SchemaHarvestSubjectResolver.ResolvedSubject(
        definition.getKey(), "local-catalog", definition, List.of());
  }
}
