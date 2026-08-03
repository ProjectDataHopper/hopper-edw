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
package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.impact.ImpactGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaValidationReportFileWriterTest {

  @TempDir Path tempDir;

  @Test
  void write_markdownAndHtml() throws Exception {
    ValidationReport report = new ValidationReport("retail-sources");
    SchemaImpactSimulationResult result =
        new SchemaImpactSimulationResult(
            report,
            ImpactGraph.empty(),
            null,
            null,
            SchemaCompareMode.LIVE_SOURCE,
            Instant.now(),
            SimulationStatus.PASS);

    List<String> paths =
        SchemaValidationReportFileWriter.write(
            tempDir.toString(),
            "gate-report",
            result,
            SchemaValidationReportFileWriter.ReportFormat.BOTH,
            new Variables());

    assertEquals(2, paths.size());
    Path md = tempDir.resolve("gate-report.md");
    Path html = tempDir.resolve("gate-report.html");
    assertTrue(Files.isRegularFile(md), paths.toString());
    assertTrue(Files.isRegularFile(html), paths.toString());
    String mdContent = Files.readString(md);
    assertTrue(
        mdContent.contains("Data Vault Resource Definition Validation Report")
            || mdContent.contains("Data Vault DDL Validation Report"),
        mdContent);
    assertTrue(mdContent.contains("PASS") || mdContent.contains("✅"), mdContent);
    assertTrue(
        mdContent.contains("Expected (baseline)")
            || mdContent.contains("What we compared")
            || mdContent.contains("Baseline"),
        mdContent);
  }

  @Test
  void resolveBaseName_stripsExtensions() {
    assertEquals(
        "my-report",
        SchemaValidationReportFileWriter.resolveBaseName(
            "my-report.md",
            new SchemaImpactSimulationResult(
                new ValidationReport("g"),
                ImpactGraph.empty(),
                null,
                null,
                SchemaCompareMode.LIVE_SOURCE,
                Instant.now(),
                SimulationStatus.PASS),
            null));
  }
}
