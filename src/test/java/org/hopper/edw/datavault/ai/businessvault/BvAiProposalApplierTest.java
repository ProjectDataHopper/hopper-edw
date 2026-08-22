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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.ai.DvAiProposal;
import org.hopper.edw.datavault.metadata.DvTargetLoadMode;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.junit.jupiter.api.Test;

class BvAiProposalApplierTest {

  @Test
  void appliesTargetLoadModeConfigurationProperty() throws Exception {
    BusinessVaultModel model = new BusinessVaultModel();
    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.SET_CONFIGURATION_PROPERTY);
    proposal.setParameters(
        Map.of("propertyName", "targetLoadMode", "value", DvTargetLoadMode.STAGING_FILE.getCode()));

    BvAiProposalApplier.apply(model, List.of(proposal), null, new Variables());

    assertEquals(
        DvTargetLoadMode.STAGING_FILE, model.getConfigurationOrDefault().resolveTargetLoadMode());
  }
}
