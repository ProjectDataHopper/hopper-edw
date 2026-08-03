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
package org.apache.hop.datavault.hopgui.file.dimensional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmSourceType;
import org.junit.jupiter.api.Test;

class DmSourcePipelineOpenSupportTest {

  @Test
  void canOpenWhenPipelineSourceAndFileConfigured() {
    DmDimension dimension = new DmDimension();
    dimension.getSourceOrDefault().setSourceType(DmSourceType.PIPELINE);
    dimension
        .getSourceOrDefault()
        .setSourcePipelineFile("${PROJECT_HOME}/test/streaming-source.hpl");

    assertTrue(DmSourcePipelineOpenSupport.canOpenSourcePipeline(dimension, new Variables()));
  }

  @Test
  void cannotOpenForSqlSourceOrMissingFile() {
    DmDimension sqlDimension = new DmDimension();
    sqlDimension.getSourceOrDefault().setSourceSql("SELECT 1");
    assertFalse(DmSourcePipelineOpenSupport.canOpenSourcePipeline(sqlDimension, new Variables()));

    DmDimension pipelineDimension = new DmDimension();
    pipelineDimension.getSourceOrDefault().setSourceType(DmSourceType.PIPELINE);
    assertFalse(
        DmSourcePipelineOpenSupport.canOpenSourcePipeline(pipelineDimension, new Variables()));
  }
}
