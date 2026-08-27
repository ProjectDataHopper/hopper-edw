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
package org.hopper.edw.datavault.metadata.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DmSourcePipelineSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HeadlessPipelineFieldSupportTest {

  private static final Path STREAMING_SOURCE =
      Path.of("retail-example", "test", "streaming-source.hpl").toAbsolutePath();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesStreamingSourceTransformFieldsWithoutGuiExtensionPoints() throws Exception {
    Variables variables = new Variables();
    DmSourceConfiguration source = new DmSourceConfiguration();
    source.setSourceType(DmSourceType.PIPELINE);
    source.setSourcePipelineFile(STREAMING_SOURCE.toString());
    source.setSourcePipelineTransform("id");

    var rowMeta =
        DmSourcePipelineSupport.resolveSourceRowMeta(
            source, variables, new MemoryMetadataProvider());
    assertNotNull(rowMeta);
    assertEquals(1, rowMeta.size());
    assertEquals("id", rowMeta.getValueMeta(0).getName());
    assertEquals(IValueMeta.TYPE_INTEGER, rowMeta.getValueMeta(0).getType());
  }
}
