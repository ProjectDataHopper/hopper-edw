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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.ai.DvAiProposal;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBridge;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvDvTableReference;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceQuery;
import org.hopper.edw.datavault.metadata.businessvault.BvTableType;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Parses, validates, and applies structural Business Vault modeling proposals. */
final class BvAiStructuralProposalSupport {

  private BvAiStructuralProposalSupport() {}

  static void apply(
      BusinessVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    switch (proposal.getType()) {
      case ADD_SCD2 -> addScd2(model, proposal);
      case ADD_PIT -> addPit(model, proposal);
      case ADD_BUSINESS_TABLE -> addBusinessTable(model, proposal);
      case ADD_SOURCE_QUERY -> addSourceQuery(model, proposal);
      case ADD_BRIDGE -> addBridge(model, proposal);
      case BIND_DV_TABLE -> bindDvTable(model, proposal, metadataProvider, variables);
      case SET_SQL_QUERY -> setSqlQuery(model, proposal);
      case SET_TABLE_LOCATION -> setTableLocation(model, proposal);
      default ->
          throw new HopException("Unsupported Business Vault proposal: " + proposal.getType());
    }
  }

  static BvAiProposalValidator.ValidationResult validate(
      BusinessVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    try {
      return switch (proposal.getType()) {
        case ADD_SCD2 -> validateAddNamedTable(model, proposal);
        case ADD_PIT -> validateAddNamedTable(model, proposal);
        case ADD_BUSINESS_TABLE -> validateAddNamedTable(model, proposal);
        case ADD_SOURCE_QUERY -> validateAddNamedTable(model, proposal);
        case ADD_BRIDGE -> validateAddNamedTable(model, proposal);
        case BIND_DV_TABLE -> validateBindDvTable(model, proposal, metadataProvider, variables);
        case SET_SQL_QUERY -> validateSetSqlQuery(model, proposal);
        case SET_TABLE_LOCATION -> validateSetTableLocation(model, proposal);
        default ->
            new BvAiProposalValidator.ValidationResult(
                proposal,
                BvAiProposalValidator.Status.BLOCKED,
                "Unsupported structural proposal for Business Vault");
      };
    } catch (HopException e) {
      return new BvAiProposalValidator.ValidationResult(
          proposal, BvAiProposalValidator.Status.BLOCKED, e.getMessage());
    }
  }

  private static void addScd2(BusinessVaultModel model, DvAiProposal proposal) throws HopException {
    BvScd2Table table = new BvScd2Table();
    initNamedTable(model, table, proposal);
    String parentHub = trimToNull(proposal.parameter("parentHubName"));
    if (parentHub != null) {
      table.setParentHubName(parentHub);
      BusinessVaultDerivativeSupport.addDerivative(
          table, new BvDvTableReference(parentHub, DvTableType.HUB));
    }
    for (String satellite : parseCommaList(proposal.parameter("satelliteNames"))) {
      BusinessVaultDerivativeSupport.addDerivative(
          table, new BvDvTableReference(satellite, DvTableType.SATELLITE));
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void addPit(BusinessVaultModel model, DvAiProposal proposal) throws HopException {
    BvPitTable table = new BvPitTable();
    initNamedTable(model, table, proposal);
    String hubName = trimToNull(proposal.parameter("hubName"));
    if (hubName != null) {
      BusinessVaultDerivativeSupport.addDerivative(
          table, new BvDvTableReference(hubName, DvTableType.HUB));
    }
    for (String satellite : parseCommaList(proposal.parameter("satelliteNames"))) {
      BusinessVaultDerivativeSupport.addDerivative(
          table, new BvDvTableReference(satellite, DvTableType.SATELLITE));
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void addBusinessTable(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    BvBusinessTable table = new BvBusinessTable();
    initNamedTable(model, table, proposal);
    String sql = proposal.parameter("sqlQuery");
    if (!Utils.isEmpty(sql)) {
      table.setSqlQuery(sql);
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void addSourceQuery(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    BvSourceQuery table = new BvSourceQuery();
    initNamedTable(model, table, proposal);
    String sql = proposal.parameter("sqlQuery");
    if (!Utils.isEmpty(sql)) {
      table.setSqlQuery(sql);
    }
    String hashKey = trimToNull(proposal.parameter("hashKeyField"));
    if (hashKey != null) {
      table.setHashKeyField(hashKey);
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void addBridge(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    BvBridge table = new BvBridge();
    initNamedTable(model, table, proposal);
    String weight = trimToNull(proposal.parameter("weightField"));
    if (weight != null) {
      table.setWeightField(weight);
    }
    String sql = proposal.parameter("sqlQuery");
    if (!Utils.isEmpty(sql)) {
      table.setSqlQuery(sql);
    }
    String linkName = trimToNull(proposal.parameter("linkName"));
    if (linkName != null) {
      BusinessVaultDerivativeSupport.addDerivative(
          table, new BvDvTableReference(linkName, DvTableType.LINK));
    }
    for (String hubName : parseCommaList(proposal.parameter("hubNames"))) {
      BusinessVaultDerivativeSupport.addDerivative(
          table, new BvDvTableReference(hubName, DvTableType.HUB));
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void bindDvTable(
      BusinessVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    IBvTable table = requireTable(model, required(proposal, "tableName"));
    String dvTableName = required(proposal, "dvTableName");
    DvTableType dvType =
        resolveDvTableType(model, dvTableName, proposal, metadataProvider, variables);
    boolean added =
        BusinessVaultDerivativeSupport.addDerivative(
            table, new BvDvTableReference(dvTableName, dvType));
    if (!added) {
      throw new HopException(
          "Cannot bind "
              + dvTableName
              + " ("
              + dvType
              + ") to "
              + table.getName()
              + " ("
              + table.getTableType()
              + ")");
    }
    model.setChanged(true);
  }

  private static void setSqlQuery(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    IBvTable table = requireTable(model, required(proposal, "tableName"));
    String sql = required(proposal, "sqlQuery");
    if (table instanceof BvBusinessTable businessTable) {
      businessTable.setSqlQuery(sql);
    } else if (table instanceof BvSourceQuery sourceQuery) {
      sourceQuery.setSqlQuery(sql);
    } else if (table instanceof BvBridge bridge) {
      bridge.setSqlQuery(sql);
    } else {
      throw new HopException(
          "SET_SQL_QUERY supports business tables, source queries, and bridges only");
    }
    model.setChanged(true);
  }

  private static void setTableLocation(BusinessVaultModel model, DvAiProposal proposal)
      throws HopException {
    IBvTable table = requireTable(model, required(proposal, "tableName"));
    table.setLocation(
        parseCoordinate(proposal.parameter("locationX"), 80),
        parseCoordinate(proposal.parameter("locationY"), 80));
    model.setChanged(true);
  }

  private static BvAiProposalValidator.ValidationResult validateAddNamedTable(
      BusinessVaultModel model, DvAiProposal proposal) throws HopException {
    String name = required(proposal, "name");
    if (model == null) {
      return blocked(proposal, "No model is open");
    }
    if (model.findTable(name) != null) {
      return blocked(proposal, "A table named '" + name + "' already exists");
    }
    return ok(proposal);
  }

  private static BvAiProposalValidator.ValidationResult validateBindDvTable(
      BusinessVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    IBvTable table = requireTable(model, required(proposal, "tableName"));
    String dvTableName = required(proposal, "dvTableName");
    if (table.getTableType() == BvTableType.SOURCE_QUERY) {
      return blocked(proposal, "Source queries cannot bind Data Vault tables");
    }
    if (BusinessVaultDerivativeSupport.hasDerivative(table, dvTableName)) {
      return blocked(proposal, "Already bound to " + dvTableName);
    }
    DvTableType dvType =
        resolveDvTableType(model, dvTableName, proposal, metadataProvider, variables);
    if (!BusinessVaultDerivativeSupport.isValidDerivativePair(table.getTableType(), dvType)) {
      return blocked(proposal, table.getTableType() + " cannot bind a Data Vault " + dvType);
    }
    IDvTable resolved = resolveDvTableQuietly(model, dvTableName, metadataProvider, variables);
    if (resolved == null && metadataProvider != null) {
      return warning(proposal, "Data Vault table not found on linked model: " + dvTableName);
    }
    return ok(proposal);
  }

  private static BvAiProposalValidator.ValidationResult validateSetSqlQuery(
      BusinessVaultModel model, DvAiProposal proposal) throws HopException {
    IBvTable table = requireTable(model, required(proposal, "tableName"));
    required(proposal, "sqlQuery");
    if (!(table instanceof BvBusinessTable)
        && !(table instanceof BvSourceQuery)
        && !(table instanceof BvBridge)) {
      return blocked(
          proposal, "SET_SQL_QUERY supports business tables, source queries, and bridges only");
    }
    return ok(proposal);
  }

  private static BvAiProposalValidator.ValidationResult validateSetTableLocation(
      BusinessVaultModel model, DvAiProposal proposal) throws HopException {
    requireTable(model, required(proposal, "tableName"));
    return ok(proposal);
  }

  private static void initNamedTable(
      BusinessVaultModel model, IBvTable table, DvAiProposal proposal) throws HopException {
    String name = required(proposal, "name");
    if (model.findTable(name) != null) {
      throw new HopException("A table named '" + name + "' already exists");
    }
    table.setName(name);
    String tableName = trimToNull(proposal.parameter("tableName"));
    table.setTableName(tableName != null ? tableName : name.toLowerCase().replace(' ', '_'));
    Point location = defaultLocation(model, proposal);
    table.setLocation(location.x, location.y);
  }

  private static IBvTable requireTable(BusinessVaultModel model, String tableName)
      throws HopException {
    if (model == null) {
      throw new HopException("No model is open");
    }
    IBvTable table = model.findTable(tableName);
    if (table == null) {
      throw new HopException("Table not found: " + tableName);
    }
    return table;
  }

  private static DvTableType resolveDvTableType(
      BusinessVaultModel model,
      String dvTableName,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    IDvTable resolved = resolveDvTableQuietly(model, dvTableName, metadataProvider, variables);
    if (resolved != null && resolved.getTableType() != null) {
      return resolved.getTableType();
    }
    String typeName = trimToNull(proposal.parameter("dvTableType"));
    if (typeName == null) {
      throw new HopException("BIND_DV_TABLE requires dvTableType when the DV table is not loaded");
    }
    try {
      return DvTableType.valueOf(typeName.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new HopException("Unknown Data Vault table type: " + typeName);
    }
  }

  private static IDvTable resolveDvTableQuietly(
      BusinessVaultModel model,
      String dvTableName,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    try {
      return BusinessVaultDvModelResolver.resolveDvTable(
          model, dvTableName, variables, metadataProvider);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Point defaultLocation(BusinessVaultModel model, DvAiProposal proposal) {
    int x = parseCoordinate(proposal.parameter("locationX"), -1);
    int y = parseCoordinate(proposal.parameter("locationY"), -1);
    if (x >= 0 && y >= 0) {
      return new Point(x, y);
    }
    int count = model != null && model.getTables() != null ? model.getTables().size() : 0;
    return new Point(80 + (count * 48) % 400, 80 + (count * 32) % 320);
  }

  private static int parseCoordinate(String value, int defaultValue) {
    if (Utils.isEmpty(value)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static List<String> parseCommaList(String value) {
    if (Utils.isEmpty(value)) {
      return List.of();
    }
    List<String> items = new ArrayList<>();
    for (String part : value.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        items.add(trimmed);
      }
    }
    return items;
  }

  private static String required(DvAiProposal proposal, String name) throws HopException {
    String value = proposal.parameter(name);
    if (Utils.isEmpty(value)) {
      throw new HopException(proposal.getType() + " requires parameter '" + name + "'");
    }
    return value.trim();
  }

  private static String trimToNull(String value) {
    return Utils.isEmpty(value) ? null : value.trim();
  }

  private static BvAiProposalValidator.ValidationResult ok(DvAiProposal proposal) {
    return new BvAiProposalValidator.ValidationResult(
        proposal, BvAiProposalValidator.Status.OK, "");
  }

  private static BvAiProposalValidator.ValidationResult warning(
      DvAiProposal proposal, String message) {
    return new BvAiProposalValidator.ValidationResult(
        proposal, BvAiProposalValidator.Status.WARNING, message);
  }

  private static BvAiProposalValidator.ValidationResult blocked(
      DvAiProposal proposal, String message) {
    return new BvAiProposalValidator.ValidationResult(
        proposal, BvAiProposalValidator.Status.BLOCKED, message);
  }
}
