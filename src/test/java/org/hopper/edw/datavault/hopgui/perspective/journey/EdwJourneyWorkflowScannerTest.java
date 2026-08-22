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
package org.hopper.edw.datavault.hopgui.perspective.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.WorkflowRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EdwJourneyWorkflowScannerTest {

  @TempDir Path projectHome;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void matchesGroupActionsAndIgnoresUnrelatedWorkflows() throws Exception {
    Path workflows = projectHome.resolve("workflows");
    Files.createDirectories(workflows);
    Files.writeString(
        workflows.resolve("run-update.hwf"),
        workflowXml(
            "run-update",
            """
            <action>
              <name>Update resource definition group</name>
              <type>UPDATE_RESOURCE_DEFINITION_GROUP</type>
              <resourceDefinitionGroup>retail-sources</resourceDefinitionGroup>
            </action>
            <action>
              <name>Harvest source metadata</name>
              <type>HARVEST_SOURCE_METADATA</type>
              <resourceDefinitionGroup>retail-sources</resourceDefinitionGroup>
            </action>
            """),
        StandardCharsets.UTF_8);
    Files.writeString(
        workflows.resolve("other.hwf"),
        workflowXml(
            "other",
            """
            <action>
              <name>Update other group</name>
              <type>UPDATE_RESOURCE_DEFINITION_GROUP</type>
              <resourceDefinitionGroup>other-group</resourceDefinitionGroup>
            </action>
            """),
        StandardCharsets.UTF_8);
    Files.createDirectories(projectHome.resolve("work/generated"));
    Files.writeString(
        projectHome.resolve("work/generated/copy.hwf"),
        workflowXml(
            "copy",
            """
            <action>
              <name>Should be skipped</name>
              <type>UPDATE_RESOURCE_DEFINITION_GROUP</type>
              <resourceDefinitionGroup>retail-sources</resourceDefinitionGroup>
            </action>
            """),
        StandardCharsets.UTF_8);

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", projectHome.toAbsolutePath().toString());

    List<WorkflowRef> hits =
        EdwJourneyWorkflowScanner.scan("retail-sources", variables, projectHome);

    assertEquals(1, hits.size());
    assertEquals("run-update", hits.get(0).workflowName());
    assertEquals(2, hits.get(0).actions().size());
    assertEquals("HARVEST_SOURCE_METADATA", hits.get(0).actions().get(1).type());
    assertTrue(hits.get(0).storedPath().contains("run-update.hwf"));
  }

  @Test
  void resolvesVariableGroupName() throws Exception {
    Path workflows = projectHome.resolve("workflows");
    Files.createDirectories(workflows);
    Files.writeString(
        workflows.resolve("gated.hwf"),
        workflowXml(
            "gated",
            """
            <action>
              <name>Validate resource definitions</name>
              <type>VALIDATE_RESOURCE_DEFINITIONS</type>
              <resourceDefinitionGroup>${GROUP_NAME}</resourceDefinitionGroup>
            </action>
            """),
        StandardCharsets.UTF_8);
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", projectHome.toAbsolutePath().toString());
    variables.setVariable("GROUP_NAME", "sales-edw");

    List<WorkflowRef> hits = EdwJourneyWorkflowScanner.scan("sales-edw", variables, projectHome);
    assertEquals(1, hits.size());
    assertEquals("VALIDATE_RESOURCE_DEFINITIONS", hits.get(0).actions().get(0).type());
  }

  private static String workflowXml(String name, String actions) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <workflow>
          <name>%s</name>
          <actions>
            %s
          </actions>
        </workflow>
        """
        .formatted(name, actions);
  }
}
