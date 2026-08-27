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
package org.hopper.edw.datavault.metadata.sourcemodel.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.hopper.edw.catalog.transform.recorddatainput.RecordDefinitionDataInputMeta;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipelineCatalogSource;
import org.junit.jupiter.api.Test;

class SourcePipelineCatalogImportSupportTest {

  @Test
  void findsZeroOneOrMoreRecordDefinitionDataInputs() {
    PipelineMeta empty = new PipelineMeta();
    empty.addTransform(new TransformMeta("Dummy", "dummy", new DummyMeta()));
    assertTrue(SourcePipelineCatalogImportSupport.importFromPipelineMeta(empty, null).isEmpty());

    PipelineMeta one = new PipelineMeta();
    RecordDefinitionDataInputMeta meta1 = new RecordDefinitionDataInputMeta();
    meta1.setCatalogConnectionName("local-catalog");
    meta1.setNamespaceValue("hop/project/sources");
    meta1.setNameValue("CRM-customer");
    one.addTransform(new TransformMeta("RecordDefinitionDataInput", "read customer", meta1));
    one.addTransform(new TransformMeta("Dummy", "output", new DummyMeta()));

    List<SourcePipelineCatalogSource> found =
        SourcePipelineCatalogImportSupport.importFromPipelineMeta(one, null);
    assertEquals(1, found.size());
    assertEquals("read customer", found.get(0).getTransformName());
    assertEquals("local-catalog", found.get(0).getCatalogConnection());
    assertEquals("hop/project/sources", found.get(0).getNamespace());
    assertEquals("CRM-customer", found.get(0).getRecordName());
    assertFalse(found.get(0).isSelectFromInput());

    PipelineMeta two = new PipelineMeta();
    RecordDefinitionDataInputMeta meta2 = new RecordDefinitionDataInputMeta();
    meta2.setCatalogConnectionName("local-catalog");
    meta2.setSelectFromInput(true);
    meta2.setNamespaceField("ns");
    meta2.setNameField("nm");
    two.addTransform(new TransformMeta("RecordDefinitionDataInput", "read a", meta1));
    two.addTransform(new TransformMeta("RecordDefinitionDataInput", "read b", meta2));
    List<SourcePipelineCatalogSource> multi =
        SourcePipelineCatalogImportSupport.importFromPipelineMeta(two, null);
    assertEquals(2, multi.size());
    assertTrue(multi.get(1).isSelectFromInput());
    assertEquals("ns", multi.get(1).getNamespaceField());
    assertEquals("nm", multi.get(1).getNameField());
  }
}
