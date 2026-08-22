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
package org.apache.hop.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.IValueMeta;
import org.junit.jupiter.api.Test;

class SourcePipelineValidationSupportTest {

  @Test
  void flagsMissingRequiredFields() {
    SourcePipeline pipeline = new SourcePipeline();
    List<ICheckResult> remarks = SourcePipelineValidationSupport.check(pipeline, null, null);
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void acceptsCompletePipelineDefinition() {
    SourcePipeline pipeline = new SourcePipeline("feed_enriched");
    pipeline.setPipelineFilename("${PROJECT_HOME}/pipelines/source-enriched.hpl");
    pipeline.setOutputTransformName("output");
    SourceColumn id = new SourceColumn("customer_id");
    id.setHopType(IValueMeta.TYPE_INTEGER);
    id.setPrimaryKeyPosition(1);
    pipeline.getFields().add(id);
    SourceColumn name = new SourceColumn("full_name");
    name.setHopType(IValueMeta.TYPE_STRING);
    pipeline.getFields().add(name);

    List<ICheckResult> remarks = SourcePipelineValidationSupport.check(pipeline, null, null);
    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        "Unexpected errors: " + remarks);
  }
}
