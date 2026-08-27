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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvModelCheckOptions;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.DvNoteType;
import org.hopper.edw.datavault.metadata.IDvTable;

/** Previews and applies validated AI proposals to an open model. */
public final class DvAiProposalApplier {

  public static final Set<String> ALLOWED_CONFIGURATION_PROPERTIES =
      Set.of(
          "sortRowsSize",
          "targetTableParallelCopies",
          "targetTableBatchSize",
          "targetLoadMode",
          "bulkLoadStagingFolder",
          "bulkLoadDelimiter",
          "bulkLoadEnclosure",
          "bulkLoadEncoding",
          "bulkLoadLocalFileRequired",
          "targetDatabase",
          "dataCatalogConnection",
          "generatedPipelineFolder",
          "generatedWorkflowNamePrefix");

  private DvAiProposalApplier() {}

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
      DataVaultModel model,
      List<DvAiProposal> proposals,
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables)
      throws HopException {
    if (model == null) {
      throw new HopException("No Data Vault model is open");
    }
    if (proposals == null || proposals.isEmpty()) {
      return;
    }
    for (DvAiProposal proposal : proposals) {
      applyOne(model, proposal, metadataProvider, variables);
    }
    if (metadataProvider != null) {
      model.check(metadataProvider, variables, DvModelCheckOptions.defaults());
    }
  }

  private static void applyOne(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables)
      throws HopException {
    if (proposal == null || proposal.getType() == null) {
      return;
    }
    switch (proposal.getType()) {
      case ADD_MODEL_NOTE -> addModelNote(model, proposal);
      case SET_CONFIGURATION_PROPERTY -> setConfigurationProperty(model, proposal);
      case RENAME_TABLE -> renameTable(model, proposal);
      case ADD_HUB,
              ADD_LINK,
              ADD_SATELLITE,
              SET_BUSINESS_KEYS,
              BIND_RECORD_SOURCE,
              SET_TABLE_LOCATION ->
          DvAiStructuralProposalSupport.apply(model, proposal, metadataProvider, variables);
      default -> throw new HopException("Unsupported proposal type: " + proposal.getType());
    }
  }

  private static void addModelNote(DataVaultModel model, DvAiProposal proposal)
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

  private static void setConfigurationProperty(DataVaultModel model, DvAiProposal proposal)
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
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    DvTargetLoadAiConfigurationSupport.applyToDataVault(config, propertyName, value);
    model.setChanged(true);
  }

  private static void renameTable(DataVaultModel model, DvAiProposal proposal) throws HopException {
    String tableName = proposal.parameter("tableName");
    String newName = proposal.parameter("newName");
    if (Utils.isEmpty(tableName) || Utils.isEmpty(newName)) {
      throw new HopException("RENAME_TABLE requires parameters 'tableName' and 'newName'");
    }
    IDvTable table = model.findTable(tableName.trim());
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
