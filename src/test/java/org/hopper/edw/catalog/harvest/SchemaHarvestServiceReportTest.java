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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestStatus;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.junit.jupiter.api.Test;

class SchemaHarvestServiceReportTest {

  @Test
  void formatMarkdownReportIncludesRunAndChanges() {
    HarvestSubjectResult subject =
        HarvestSubjectResult.builder()
            .subjectKey("hop/retail-example/sources/E2E-customer")
            .sourceType("DATABASE")
            .databaseMetaName("CRM")
            .schemaName("public")
            .tableName("customer")
            .discoveryStatus(DiscoveryStatus.OK)
            .inSync(false)
            .changes(
                List.of(
                    HarvestChange.builder()
                        .changeKind("CHANGED")
                        .fieldName("address_line1")
                        .actualDetail("length 50 -> 75")
                        .severity("WARNING")
                        .build()))
            .build();

    HarvestResult result =
        HarvestResult.builder()
            .harvestRunId("run-1")
            .startedAt(Instant.parse("2026-04-01T10:00:00Z"))
            .finishedAt(Instant.parse("2026-04-01T10:01:00Z"))
            .resourceGroupName("retail-sources")
            .expectedBaseline("WORKING")
            .status(HarvestStatus.SUCCESS)
            .scopeSummary("group=retail-sources; subjects=1; dbPartitions=1")
            .subjects(List.of(subject))
            .build();

    String md = SchemaHarvestService.formatMarkdownReport(result);
    assertTrue(md.contains("run-1"));
    assertTrue(md.contains("retail-sources"));
    assertTrue(md.contains("E2E-customer"));
    assertTrue(md.contains("address_line1"));
    assertTrue(md.contains("Subjects with changes"));
  }
}
