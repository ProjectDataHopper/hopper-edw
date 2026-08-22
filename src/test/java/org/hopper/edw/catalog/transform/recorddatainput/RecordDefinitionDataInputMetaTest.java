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
package org.hopper.edw.catalog.transform.recorddatainput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;

class RecordDefinitionDataInputMetaTest {

  @Test
  void cloneCopiesConfiguration() {
    RecordDefinitionDataInputMeta meta = new RecordDefinitionDataInputMeta();
    meta.setCatalogConnectionName("local-catalog");
    meta.setSelectFromInput(true);
    meta.setNamespaceField("ns");
    meta.setNameField("nm");
    meta.setNamespaceValue("hop/project/sources");
    meta.setNameValue("CRM-customer");
    meta.setRowLimit("100");

    RecordDefinitionDataInputMeta copy = meta.clone();
    assertEquals("local-catalog", copy.getCatalogConnectionName());
    assertTrue(copy.isSelectFromInput());
    assertEquals("ns", copy.getNamespaceField());
    assertEquals("nm", copy.getNameField());
    assertEquals("hop/project/sources", copy.getNamespaceValue());
    assertEquals("CRM-customer", copy.getNameValue());
    assertEquals("100", copy.getRowLimit());
  }

  @Test
  void checkFlagsMissingCatalogAndKeys() {
    RecordDefinitionDataInputMeta meta = new RecordDefinitionDataInputMeta();
    List<ICheckResult> remarks = new ArrayList<>();
    TransformMeta transformMeta = new TransformMeta("t", meta);
    meta.check(
        remarks,
        new PipelineMeta(),
        transformMeta,
        new RowMeta(),
        new String[0],
        new String[0],
        new RowMeta(),
        new Variables(),
        null);

    assertTrue(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        "Expected error remarks for incomplete config");
  }

  @Test
  void checkAcceptsCompleteFixedKeyConfig() {
    RecordDefinitionDataInputMeta meta = new RecordDefinitionDataInputMeta();
    meta.setCatalogConnectionName("local-catalog");
    meta.setNamespaceValue("hop/project/sources");
    meta.setNameValue("CRM-customer");
    List<ICheckResult> remarks = new ArrayList<>();
    TransformMeta transformMeta = new TransformMeta("t", meta);
    meta.check(
        remarks,
        new PipelineMeta(),
        transformMeta,
        new RowMeta(),
        new String[0],
        new String[0],
        new RowMeta(),
        new Variables(),
        null);

    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        "Fixed key config should not produce errors: " + remarks);
    assertTrue(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK),
        "Expected OK remarks");
  }
}
