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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.ai.DvAiProposal;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBridge;
import org.hopper.edw.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceType;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;

/** Parses, validates, and applies structural dimensional modeling proposals. */
final class DmAiStructuralProposalSupport {

  private DmAiStructuralProposalSupport() {}

  static void apply(DimensionalModel model, DvAiProposal proposal) throws HopException {
    switch (proposal.getType()) {
      case ADD_DIMENSION -> addDimension(model, proposal);
      case ADD_FACT -> addFact(model, proposal);
      case ADD_BRIDGE -> addBridge(model, proposal);
      case ADD_JUNK_DIMENSION -> addJunk(model, proposal);
      case BIND_SOURCE -> bindSource(model, proposal);
      case SET_TABLE_LOCATION -> setTableLocation(model, proposal);
      default -> throw new HopException("Unsupported dimensional proposal: " + proposal.getType());
    }
  }

  static DmAiProposalValidator.ValidationResult validate(
      DimensionalModel model, DvAiProposal proposal) {
    try {
      return switch (proposal.getType()) {
        case ADD_DIMENSION, ADD_FACT, ADD_BRIDGE, ADD_JUNK_DIMENSION ->
            validateAddNamedTable(model, proposal);
        case BIND_SOURCE -> validateBindSource(model, proposal);
        case SET_TABLE_LOCATION -> validateSetTableLocation(model, proposal);
        default ->
            new DmAiProposalValidator.ValidationResult(
                proposal,
                DmAiProposalValidator.Status.BLOCKED,
                "Unsupported structural proposal for dimensional model");
      };
    } catch (HopException e) {
      return new DmAiProposalValidator.ValidationResult(
          proposal, DmAiProposalValidator.Status.BLOCKED, e.getMessage());
    }
  }

  private static void addDimension(DimensionalModel model, DvAiProposal proposal)
      throws HopException {
    DmDimension table = new DmDimension();
    initNamedTable(model, table, proposal);
    String surrogate = trimToNull(proposal.parameter("surrogateKeyField"));
    if (surrogate != null) {
      table.setSurrogateKeyField(surrogate);
    }
    for (String key : parseCommaList(proposal.parameter("naturalKeys"))) {
      table.getNaturalKeys().add(new DmNaturalKeyField(key));
    }
    for (String attribute : parseCommaList(proposal.parameter("attributes"))) {
      table.getAttributes().add(new DmDimensionAttribute(attribute));
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void addFact(DimensionalModel model, DvAiProposal proposal) throws HopException {
    DmFact table = new DmFact();
    initNamedTable(model, table, proposal);
    String grain = trimToNull(proposal.parameter("grain"));
    if (grain != null) {
      table.setGrain(grain);
    }
    for (String measure : parseCommaList(proposal.parameter("measures"))) {
      table.getMeasures().add(new DmFactMeasure(measure));
    }
    for (String dimension : parseCommaList(proposal.parameter("dimensionTableNames"))) {
      String fk = dimension.toLowerCase().replace(' ', '_') + "_key";
      table.getDimensionRoles().add(new DmFactDimensionRole(dimension, fk));
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void addBridge(DimensionalModel model, DvAiProposal proposal) throws HopException {
    DmBridge table = new DmBridge();
    initNamedTable(model, table, proposal);
    for (String dimension : parseCommaList(proposal.parameter("dimensionTableNames"))) {
      String fk = dimension.toLowerCase().replace(' ', '_') + "_key";
      table.getDimensionRefs().add(new DmBridgeDimensionRef(dimension, fk));
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void addJunk(DimensionalModel model, DvAiProposal proposal) throws HopException {
    DmJunkDimension table = new DmJunkDimension();
    initNamedTable(model, table, proposal);
    String surrogate = trimToNull(proposal.parameter("surrogateKeyField"));
    if (surrogate != null) {
      table.setSurrogateKeyField(surrogate);
    }
    for (String key : parseCommaList(proposal.parameter("keyFields"))) {
      table.getKeyFields().add(new DmNaturalKeyField(key));
    }
    model.getTables().add(table);
    model.setChanged(true);
  }

  private static void bindSource(DimensionalModel model, DvAiProposal proposal)
      throws HopException {
    IDmTable table = requireTable(model, required(proposal, "tableName"));
    DmSourceConfiguration source = table.getSourceOrDefault();
    String typeName = trimToNull(proposal.parameter("sourceType"));
    if (typeName != null) {
      source.setSourceType(parseSourceType(typeName));
    }
    String sql = proposal.parameter("sourceSql");
    if (sql != null) {
      source.setSourceSql(sql);
    }
    String pipeline = trimToNull(proposal.parameter("sourcePipelineFile"));
    if (pipeline != null) {
      source.setSourcePipelineFile(pipeline);
    }
    String transform = trimToNull(proposal.parameter("sourcePipelineTransform"));
    if (transform != null) {
      source.setSourcePipelineTransform(transform);
    }
    String catalog = trimToNull(proposal.parameter("sourceCatalogConnection"));
    if (catalog != null) {
      source.setSourceCatalogConnection(catalog);
    }
    String namespace = trimToNull(proposal.parameter("sourceRecordNamespace"));
    if (namespace != null) {
      source.setSourceRecordNamespace(namespace);
    }
    String recordName = trimToNull(proposal.parameter("sourceRecordName"));
    if (recordName != null) {
      source.setSourceRecordName(recordName);
    }
    String factTable = trimToNull(proposal.parameter("sourceFactTableName"));
    if (factTable != null) {
      source.setSourceFactTableName(factTable);
    }
    model.setChanged(true);
  }

  private static void setTableLocation(DimensionalModel model, DvAiProposal proposal)
      throws HopException {
    IDmTable table = requireTable(model, required(proposal, "tableName"));
    table.setLocation(
        parseCoordinate(proposal.parameter("locationX"), 80),
        parseCoordinate(proposal.parameter("locationY"), 80));
    model.setChanged(true);
  }

  private static DmAiProposalValidator.ValidationResult validateAddNamedTable(
      DimensionalModel model, DvAiProposal proposal) throws HopException {
    String name = required(proposal, "name");
    if (model == null) {
      return blocked(proposal, "No model is open");
    }
    if (model.findTable(name) != null) {
      return blocked(proposal, "A table named '" + name + "' already exists");
    }
    return ok(proposal);
  }

  private static DmAiProposalValidator.ValidationResult validateBindSource(
      DimensionalModel model, DvAiProposal proposal) throws HopException {
    requireTable(model, required(proposal, "tableName"));
    String typeName = trimToNull(proposal.parameter("sourceType"));
    if (typeName != null) {
      parseSourceType(typeName);
    }
    return ok(proposal);
  }

  private static DmAiProposalValidator.ValidationResult validateSetTableLocation(
      DimensionalModel model, DvAiProposal proposal) throws HopException {
    requireTable(model, required(proposal, "tableName"));
    return ok(proposal);
  }

  private static void initNamedTable(DimensionalModel model, IDmTable table, DvAiProposal proposal)
      throws HopException {
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

  private static IDmTable requireTable(DimensionalModel model, String tableName)
      throws HopException {
    if (model == null) {
      throw new HopException("No model is open");
    }
    IDmTable table = model.findTable(tableName);
    if (table == null) {
      throw new HopException("Table not found: " + tableName);
    }
    return table;
  }

  private static DmSourceType parseSourceType(String typeName) throws HopException {
    try {
      return DmSourceType.valueOf(typeName.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new HopException("Unknown source type: " + typeName);
    }
  }

  private static Point defaultLocation(DimensionalModel model, DvAiProposal proposal) {
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

  private static DmAiProposalValidator.ValidationResult ok(DvAiProposal proposal) {
    return new DmAiProposalValidator.ValidationResult(
        proposal, DmAiProposalValidator.Status.OK, "");
  }

  private static DmAiProposalValidator.ValidationResult blocked(
      DvAiProposal proposal, String message) {
    return new DmAiProposalValidator.ValidationResult(
        proposal, DmAiProposalValidator.Status.BLOCKED, message);
  }
}
