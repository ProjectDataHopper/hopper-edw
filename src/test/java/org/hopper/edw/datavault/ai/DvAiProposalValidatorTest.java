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
package org.hopper.edw.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.junit.jupiter.api.Test;

class DvAiProposalValidatorTest {

  @Test
  void blocksRenameWhenTableMissing() {
    DataVaultModel model = new DataVaultModel();
    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.RENAME_TABLE);
    proposal.setParameters(Map.of("tableName", "missing", "newName", "HUB_CUSTOMER"));

    DvAiProposalValidator.ValidationResult result =
        DvAiProposalValidator.validate(model, List.of(proposal)).get(0);

    assertEquals(DvAiProposalValidator.Status.BLOCKED, result.getStatus());
  }

  @Test
  void blocksDisallowedConfigurationProperty() {
    DataVaultModel model = new DataVaultModel();
    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.SET_CONFIGURATION_PROPERTY);
    proposal.setParameters(Map.of("propertyName", "unknownProperty", "value", "1"));

    DvAiProposalValidator.ValidationResult result =
        DvAiProposalValidator.validate(model, List.of(proposal)).get(0);

    assertEquals(DvAiProposalValidator.Status.BLOCKED, result.getStatus());
  }

  @Test
  void acceptsValidRename() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("Hub");
    model.setTables(List.of(hub));

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.RENAME_TABLE);
    proposal.setParameters(Map.of("tableName", "Hub", "newName", "HUB_CUSTOMER"));

    DvAiProposalValidator.ValidationResult result =
        DvAiProposalValidator.validate(model, List.of(proposal)).get(0);

    assertEquals(DvAiProposalValidator.Status.OK, result.getStatus());
  }
}
