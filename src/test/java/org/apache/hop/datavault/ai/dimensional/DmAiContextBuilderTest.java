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
package org.apache.hop.datavault.ai.dimensional;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.metadata.DvTargetLoadMode;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.junit.jupiter.api.Test;

class DmAiContextBuilderTest {

  @Test
  void serializeModelSummaryIncludesTargetLoadMode() {
    DimensionalModel model = new DimensionalModel();
    model.setName("DM_TEST");
    DmDimension dimension = new DmDimension();
    dimension.setName("DIM_CUSTOMER");
    model.setTables(java.util.List.of(dimension));
    model.getConfigurationOrDefault().setTargetLoadMode(DvTargetLoadMode.STAGING_FILE.getCode());

    String summary = DmAiContextBuilder.serializeModelSummary(model);

    assertTrue(summary.contains("\"targetLoadMode\":\"STAGING_FILE\""));
    assertTrue(summary.contains("\"name\":\"DM_TEST\""));
  }
}
