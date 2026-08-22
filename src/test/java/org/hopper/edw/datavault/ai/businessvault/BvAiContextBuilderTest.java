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
package org.hopper.edw.datavault.ai.businessvault;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.datavault.metadata.DvTargetLoadMode;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.junit.jupiter.api.Test;

class BvAiContextBuilderTest {

  @Test
  void serializeModelSummaryIncludesTargetLoadMode() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.setName("BV_TEST");
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("SCD2_CUSTOMER");
    model.setTables(java.util.List.of(scd2));
    model.getConfigurationOrDefault().setTargetLoadMode(DvTargetLoadMode.STAGING_FILE.getCode());

    String summary = BvAiContextBuilder.serializeModelSummary(model);

    assertTrue(summary.contains("\"targetLoadMode\":\"STAGING_FILE\""));
    assertTrue(summary.contains("\"name\":\"BV_TEST\""));
  }
}
