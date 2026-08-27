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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.ai.DvAiProposal;
import org.hopper.edw.datavault.ai.DvTargetLoadAiConfigurationSupport;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.DvNoteType;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Previews and applies validated AI proposals to an open Business Vault model. */
public final class BvAiProposalApplier {

  public static final Set<String> ALLOWED_CONFIGURATION_PROPERTIES =
      Set.of(
          "targetTableParallelCopies",
          "targetTableBatchSize",
          "targetLoadMode",
          "bulkLoadStagingFolder",
          "bulkLoadDelimiter",
          "bulkLoadEnclosure",
          "bulkLoadEncoding",
          "bulkLoadLocalFileRequired",
          "targetDatabase",
          "generatedPipelineFolder",
          "generatedWorkflowNamePrefix",
          "scd2PipelineNamePrefix",
          "pitPipelineNamePrefix",
          "businessTablePipelineNamePrefix",
          "functionalTimestampField",
          "loadDateFieldFallback",
          "validFromField",
          "validToField");

  private BvAiProposalApplier() {}

  public static String preview(DvAiProposal proposal) {
    if (proposal == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(proposal.getDescription());
    if (!Utils.isEmpty(proposal.getId())) {
      sb.append(" [").append(proposal.getId()).append(']');
    }
    sb.append("\nType: ").append(proposal.getType());
    sb.append("\nRisk: ").append(proposal.getRiskLevel());
    if (proposal.getParameters() != null && !proposal.getParameters().isEmpty()) {
      sb.append("\nParameters:");
      proposal
          .getParameters()
          .forEach((k, v) -> sb.append("\n  ").append(k).append(" = ").append(v));
    }
    return sb.toString();
  }

  public static void apply(
      BusinessVaultModel model,
      List<DvAiProposal> proposals,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    if (model == null) {
      throw new HopException("No Business Vault model is open");
    }
    if (proposals == null || proposals.isEmpty()) {
      return;
    }
    for (DvAiProposal proposal : proposals) {
      applyOne(model, proposal);
    }
    if (metadataProvider != null) {
      model.check(metadataProvider, variables);
    }
  }

  private static void applyOne(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    if (proposal == null || proposal.getType() == null) {
      return;
    }
    switch (proposal.getType()) {
      case ADD_MODEL_NOTE -> addModelNote(model, proposal);
      case SET_CONFIGURATION_PROPERTY -> setConfigurationProperty(model, proposal);
      case RENAME_TABLE -> renameTable(model, proposal);
      default -> throw new HopException("Unsupported proposal type: " + proposal.getType());
    }
  }

  private static void addModelNote(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    String text = proposal.parameter("text");
    if (Utils.isEmpty(text)) {
      throw new HopException("ADD_MODEL_NOTE requires parameter 'text'");
    }
    DvNote note = new DvNote();
    note.setText(text.trim());
    note.setNoteType(DvNoteType.GENERAL);
    note.setLocation(new Point(80, 80));
    if (model.getNotes() == null) {
      model.setNotes(new ArrayList<>());
    }
    model.getNotes().add(note);
    model.setChanged(true);
  }

  private static void setConfigurationProperty(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    String propertyName = proposal.parameter("propertyName");
    String value = proposal.parameter("value");
    if (Utils.isEmpty(propertyName) || value == null) {
      throw new HopException(
          "SET_CONFIGURATION_PROPERTY requires parameters 'propertyName' and 'value'");
    }
    if (!ALLOWED_CONFIGURATION_PROPERTIES.contains(propertyName.trim())) {
      throw new HopException("Configuration property not allowed: " + propertyName);
    }
    BusinessVaultConfiguration config = model.getConfigurationOrDefault();
    DvTargetLoadAiConfigurationSupport.applyToBusinessVault(config, propertyName, value);
    model.setChanged(true);
  }

  private static void renameTable(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    String tableName = proposal.parameter("tableName");
    String newName = proposal.parameter("newName");
    if (Utils.isEmpty(tableName) || Utils.isEmpty(newName)) {
      throw new HopException("RENAME_TABLE requires parameters 'tableName' and 'newName'");
    }
    IBvTable table = model.findTable(tableName.trim());
    if (table == null) {
      throw new HopException("Table not found: " + tableName);
    }
    if (model.findTable(newName.trim()) != null) {
      throw new HopException("A table named '" + newName + "' already exists");
    }
    table.setName(newName.trim());
    model.setChanged(true);
  }
}
