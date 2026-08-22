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
package org.apache.hop.datavault.ai.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.DvAiProposal;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Validates AI proposals against the open dimensional model before the user applies them. */
public final class DmAiProposalValidator {

  private DmAiProposalValidator() {}

  public enum Status {
    OK,
    WARNING,
    BLOCKED
  }

  @lombok.Getter
  @lombok.AllArgsConstructor
  public static class ValidationResult {
    private final DvAiProposal proposal;
    private final Status status;
    private final String message;
  }

  public static List<ValidationResult> validate(
      DimensionalModel model,
      List<DvAiProposal> proposals,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    List<ValidationResult> results = new ArrayList<>();
    if (proposals == null) {
      return results;
    }
    for (DvAiProposal proposal : proposals) {
      results.add(validateOne(model, proposal));
    }
    return results;
  }

  private static ValidationResult validateOne(DimensionalModel model, DvAiProposal proposal) {
    if (proposal == null || proposal.getType() == null) {
      return new ValidationResult(proposal, Status.BLOCKED, "Missing proposal type");
    }
    return switch (proposal.getType()) {
      case ADD_MODEL_NOTE -> validateAddModelNote(proposal);
      case SET_CONFIGURATION_PROPERTY -> validateSetConfigurationProperty(proposal);
      case RENAME_TABLE -> validateRenameTable(model, proposal);
      default ->
          new ValidationResult(
              proposal, Status.BLOCKED, "Unsupported proposal type for dimensional model");
    };
  }

  private static ValidationResult validateAddModelNote(DvAiProposal proposal) {
    if (Utils.isEmpty(proposal.parameter("text"))) {
      return new ValidationResult(proposal, Status.BLOCKED, "Note text is required");
    }
    return new ValidationResult(proposal, Status.OK, "");
  }

  private static ValidationResult validateSetConfigurationProperty(DvAiProposal proposal) {
    String propertyName = proposal.parameter("propertyName");
    String value = proposal.parameter("value");
    if (Utils.isEmpty(propertyName)) {
      return new ValidationResult(proposal, Status.BLOCKED, "propertyName is required");
    }
    if (value == null) {
      return new ValidationResult(proposal, Status.BLOCKED, "value is required");
    }
    if (!DmAiProposalApplier.ALLOWED_CONFIGURATION_PROPERTIES.contains(propertyName.trim())) {
      return new ValidationResult(
          proposal, Status.BLOCKED, "Configuration property not allowed: " + propertyName);
    }
    return new ValidationResult(proposal, Status.OK, "");
  }

  private static ValidationResult validateRenameTable(
      DimensionalModel model, DvAiProposal proposal) {
    String tableName = proposal.parameter("tableName");
    String newName = proposal.parameter("newName");
    if (Utils.isEmpty(tableName)) {
      return new ValidationResult(proposal, Status.BLOCKED, "tableName is required");
    }
    if (Utils.isEmpty(newName)) {
      return new ValidationResult(proposal, Status.BLOCKED, "newName is required");
    }
    if (model == null) {
      return new ValidationResult(proposal, Status.BLOCKED, "No model is open");
    }
    IDmTable table = model.findTable(tableName.trim());
    if (table == null) {
      return new ValidationResult(proposal, Status.BLOCKED, "Table not found: " + tableName);
    }
    if (model.findTable(newName.trim()) != null && !newName.trim().equals(tableName.trim())) {
      return new ValidationResult(
          proposal, Status.BLOCKED, "A table named '" + newName + "' already exists");
    }
    if (tableName.trim().equals(newName.trim())) {
      return new ValidationResult(
          proposal, Status.WARNING, "New name is the same as the current name");
    }
    return new ValidationResult(proposal, Status.OK, "");
  }
}
