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
package org.hopper.edw.datavault.ai.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.ai.DvAiProposal;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.junit.jupiter.api.Test;

class DmAiProposalApplierTest {

  @Test
  void appliesBulkLoadStagingFolderConfigurationProperty() throws Exception {
    DimensionalModel model = new DimensionalModel();
    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.SET_CONFIGURATION_PROPERTY);
    proposal.setParameters(
        Map.of("propertyName", "bulkLoadStagingFolder", "value", "${PROJECT_HOME}/staging"));

    DmAiProposalApplier.apply(model, List.of(proposal), null, new Variables());

    assertEquals(
        "${PROJECT_HOME}/staging", model.getConfigurationOrDefault().getBulkLoadStagingFolder());
  }
}
