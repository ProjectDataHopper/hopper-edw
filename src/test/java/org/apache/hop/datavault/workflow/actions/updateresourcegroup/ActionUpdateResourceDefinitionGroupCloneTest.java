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
package org.apache.hop.datavault.workflow.actions.updateresourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionUpdateResourceDefinitionGroupCloneTest {

  @Test
  void defaultsIncludeDryRunOffAndModelCheckParallelism() {
    ActionUpdateResourceDefinitionGroup action = new ActionUpdateResourceDefinitionGroup();
    assertFalse(action.isDoNotUpdateTargetDatabase());
    assertEquals("8", action.getModelCheckParallelism());
    assertTrue(action.isLogModelCheckFailures());
    assertTrue(action.isAbortOnModelCheckFailures());
    assertFalse(action.isIgnoreModelCheckWarnings());
    assertFalse(action.isWriteValidationReport());
    assertEquals(
        GroupModelValidationReportFileWriter.ReportFormat.MARKDOWN.name(),
        action.getValidationReportFormat());
  }

  @Test
  void clone_copiesDryRunAndValidationOptions() {
    ActionUpdateResourceDefinitionGroup original = new ActionUpdateResourceDefinitionGroup();
    original.setResourceDefinitionGroup("retail-sources");
    original.setDoNotUpdateTargetDatabase(true);
    original.setModelCheckParallelism("${MODEL_CHECK_PARALLELISM}");
    original.setDetailedDataTypeChecking(false);
    original.setUpdateTargetDatabaseStructure(false);
    original.setIgnoreModelCheckWarnings(true);
    original.setWriteValidationReport(true);
    original.setValidationReportFolder("${PROJECT_HOME}/reports");
    original.setValidationReportBaseName("group-check");
    original.setValidationReportFormat(
        GroupModelValidationReportFileWriter.ReportFormat.BOTH.name());

    ActionUpdateResourceDefinitionGroup copy =
        (ActionUpdateResourceDefinitionGroup) original.clone();
    assertEquals("retail-sources", copy.getResourceDefinitionGroup());
    assertTrue(copy.isDoNotUpdateTargetDatabase());
    assertEquals("${MODEL_CHECK_PARALLELISM}", copy.getModelCheckParallelism());
    assertFalse(copy.isDetailedDataTypeChecking());
    assertFalse(copy.isUpdateTargetDatabaseStructure());
    assertTrue(copy.isIgnoreModelCheckWarnings());
    assertTrue(copy.isWriteValidationReport());
    assertEquals("${PROJECT_HOME}/reports", copy.getValidationReportFolder());
    assertEquals("group-check", copy.getValidationReportBaseName());
    assertEquals(
        GroupModelValidationReportFileWriter.ReportFormat.BOTH.name(),
        copy.getValidationReportFormat());
  }
}
