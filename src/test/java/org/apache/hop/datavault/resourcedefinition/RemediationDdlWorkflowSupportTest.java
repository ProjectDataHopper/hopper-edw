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
 *
 */

package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.resourcedefinition.RemediationDdlWorkflowSupport.ConnectionDdl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemediationDdlWorkflowSupportTest {

  @TempDir Path tempDir;

  @Test
  void buildBaseNameSanitizesTokensAndUsesPrefix() {
    String base =
        RemediationDdlWorkflowSupport.buildBaseName(
            "retail/customer", "product name", LocalDateTime.of(2026, 7, 29, 15, 30, 45));
    assertEquals("apply-ddl_retail_customer_product_name_20260729-153045", base);
  }

  @Test
  void formatSqlScriptIncludesConnectionCommentsAndStatements() {
    String script =
        RemediationDdlWorkflowSupport.formatSqlScript(
            List.of(
                new ConnectionDdl(
                    "edw", List.of("ALTER TABLE sat_customer_demo ALTER COLUMN email TYPE varchar(100)"))));
    assertTrue(script.contains("Connection: edw"));
    assertTrue(script.contains("ALTER TABLE sat_customer_demo"));
  }

  @Test
  void groupByConnectionKeepsStatementsPerConnection() {
    Map<String, List<String>> byConnection = RemediationDdlWorkflowSupport.newConnectionMap();
    RemediationDdlWorkflowSupport.addStatement(
        byConnection, "edw", "ALTER TABLE sat_x ALTER COLUMN c TYPE varchar(80)");
    RemediationDdlWorkflowSupport.addStatement(
        byConnection, "edw", "ALTER TABLE sat_y ALTER COLUMN d TYPE varchar(40)");
    RemediationDdlWorkflowSupport.addStatement(
        byConnection, "staging", "ALTER TABLE stg_x ALTER COLUMN e TYPE varchar(20)");

    List<ConnectionDdl> grouped = RemediationDdlWorkflowSupport.groupByConnection(byConnection);
    assertEquals(2, grouped.size());
    assertEquals("edw", grouped.get(0).connectionName());
    assertEquals(2, grouped.get(0).statements().size());
    assertEquals("staging", grouped.get(1).connectionName());
  }

  @Test
  void groupByTableKeepsOneEntryPerTable() {
    Map<String, List<String>> byTable = RemediationDdlWorkflowSupport.newTableMap();
    RemediationDdlWorkflowSupport.addTableStatement(
        byTable, "edw", "sat_x", "ALTER TABLE sat_x ALTER COLUMN c TYPE varchar(80)");
    RemediationDdlWorkflowSupport.addTableStatement(
        byTable, "edw", "sat_y", "ALTER TABLE sat_y ALTER COLUMN d TYPE varchar(40)");
    RemediationDdlWorkflowSupport.addTableStatement(
        byTable, "edw", "sat_x", "ALTER TABLE sat_x ALTER COLUMN e TYPE varchar(20)");

    var grouped = RemediationDdlWorkflowSupport.groupByTable(byTable);
    assertEquals(2, grouped.size());
    assertEquals("sat_x", grouped.get(0).tableName());
    assertEquals(2, grouped.get(0).statements().size());
    assertEquals("sat_y", grouped.get(1).tableName());
  }

  @Test
  void formatSqlScriptForTablesIncludesTableComments() {
    String script =
        RemediationDdlWorkflowSupport.formatSqlScriptForTables(
            List.of(
                new RemediationDdlWorkflowSupport.TableDdl(
                    "edw",
                    "sat_customer_demo",
                    List.of("ALTER TABLE sat_customer_demo ALTER COLUMN email TYPE varchar(100)"))));
    assertTrue(script.contains("Table: sat_customer_demo"));
    assertTrue(script.contains("one SQL action per target table"));
  }

  @Test
  void defaultFolderResolvesProjectHome() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());
    String folder = RemediationDdlWorkflowSupport.defaultFolder(variables);
    assertTrue(folder.contains("schema-remediation"));
    assertTrue(folder.startsWith(tempDir.toString()) || folder.contains("workflows"));
  }

  @Test
  void generatedArtifacts_workflowWrittenRequiresNoError() {
    RemediationDdlWorkflowSupport.GeneratedArtifacts ok =
        new RemediationDdlWorkflowSupport.GeneratedArtifacts(
            "/tmp", "base", "/tmp/base.sql", "/tmp/base.hwf", 2, List.of("edw"), List.of("sat_x"), null);
    assertTrue(ok.workflowWritten());

    RemediationDdlWorkflowSupport.GeneratedArtifacts failedWorkflow =
        new RemediationDdlWorkflowSupport.GeneratedArtifacts(
            "/tmp",
            "base",
            "/tmp/base.sql",
            null,
            2,
            List.of("edw"),
            List.of("sat_x"),
            "NoClassDefFoundError: ActionSql");
    assertTrue(!failedWorkflow.workflowWritten());
    assertEquals("NoClassDefFoundError: ActionSql", failedWorkflow.workflowError());
    assertEquals("/tmp/base.sql", failedWorkflow.sqlFilename());
  }

  @Test
  void formatSqlScriptForTables_includesLengthForExpandCase() {
    String script =
        RemediationDdlWorkflowSupport.formatSqlScriptForTables(
            List.of(
                new RemediationDdlWorkflowSupport.TableDdl(
                    "edw",
                    "sat_customer_address",
                    List.of(
                        "ALTER TABLE sat_customer_address ALTER COLUMN address_line1 TYPE varchar(75)"))));
    assertTrue(script.contains("address_line1"));
    assertTrue(script.contains("75"));
    assertTrue(script.contains("Table: sat_customer_address"));
  }
}
