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
package org.apache.hop.datavault.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Parses, validates, and applies structural Data Vault modeling proposals. */
final class DvAiStructuralProposalSupport {

  private DvAiStructuralProposalSupport() {}

  static void apply(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    switch (proposal.getType()) {
      case ADD_HUB -> addHub(model, proposal, metadataProvider, variables);
      case ADD_LINK -> addLink(model, proposal);
      case ADD_SATELLITE -> addSatellite(model, proposal, metadataProvider, variables);
      case SET_BUSINESS_KEYS -> setBusinessKeys(model, proposal, metadataProvider, variables);
      case BIND_RECORD_SOURCE -> bindRecordSource(model, proposal);
      case SET_TABLE_LOCATION -> setTableLocation(model, proposal);
      default -> throw new HopException("Unsupported structural proposal: " + proposal.getType());
    }
  }

  static DvAiProposalValidator.ValidationResult validate(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    try {
      return switch (proposal.getType()) {
        case ADD_HUB -> validateAddHub(model, proposal, metadataProvider, variables);
        case ADD_LINK -> validateAddLink(model, proposal);
        case ADD_SATELLITE -> validateAddSatellite(model, proposal, metadataProvider, variables);
        case SET_BUSINESS_KEYS ->
            validateSetBusinessKeys(model, proposal, metadataProvider, variables);
        case BIND_RECORD_SOURCE ->
            validateBindRecordSource(model, proposal, metadataProvider, variables);
        case SET_TABLE_LOCATION -> validateSetTableLocation(model, proposal);
        default ->
            new DvAiProposalValidator.ValidationResult(
                proposal, DvAiProposalValidator.Status.BLOCKED, "Unsupported structural proposal");
      };
    } catch (HopException e) {
      return new DvAiProposalValidator.ValidationResult(
          proposal, DvAiProposalValidator.Status.BLOCKED, e.getMessage());
    }
  }

  private static void addHub(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    String name = required(proposal, "name");
    String recordSource = required(proposal, "recordSource");
    if (model.findTable(name) != null) {
      throw new HopException("A table named '" + name + "' already exists");
    }

    DvHub hub = new DvHub(name.trim());
    String tableName = proposal.parameter("tableName");
    if (!Utils.isEmpty(tableName)) {
      hub.setTableName(tableName.trim());
    }
    String hashKey = proposal.parameter("hashKeyFieldName");
    if (!Utils.isEmpty(hashKey)) {
      hub.setHashKeyFieldName(hashKey.trim());
    }

    hub.setRecordSources(new ArrayList<>(List.of(recordSource.trim())));
    hub.setBusinessKeys(
        parseBusinessKeys(proposal, recordSource.trim(), model, metadataProvider, variables, true));

    hub.setLocation(defaultLocation(model, proposal));
    model.getTables().add(hub);
    model.setChanged(true);
  }

  private static void addLink(DataVaultModel model, DvAiProposal proposal) throws HopException {
    String name = required(proposal, "name");
    List<String> hubNames = parseCommaList(required(proposal, "hubNames"));
    if (hubNames.size() < 2) {
      throw new HopException("ADD_LINK requires at least two hub names in hubNames");
    }
    if (model.findTable(name) != null) {
      throw new HopException("A table named '" + name + "' already exists");
    }

    DvLink link = new DvLink(name.trim());
    String tableName = proposal.parameter("tableName");
    if (!Utils.isEmpty(tableName)) {
      link.setTableName(tableName.trim());
    }
    String hashKey = proposal.parameter("linkHashKeyFieldName");
    if (!Utils.isEmpty(hashKey)) {
      link.setLinkHashKeyFieldName(hashKey.trim());
    }

    link.setHubNames(new ArrayList<>(hubNames));
    link.setLocation(defaultLocation(model, proposal));
    model.getTables().add(link);
    model.setChanged(true);
  }

  private static void addSatellite(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    String name = required(proposal, "name");
    String recordSource = required(proposal, "recordSource");
    String hubName = trimToNull(proposal.parameter("hubName"));
    String linkName = trimToNull(proposal.parameter("linkName"));
    if (hubName == null && linkName == null) {
      throw new HopException("ADD_SATELLITE requires hubName or linkName");
    }
    if (hubName != null && linkName != null) {
      throw new HopException("ADD_SATELLITE requires hubName or linkName, not both");
    }
    if (model.findTable(name) != null) {
      throw new HopException("A table named '" + name + "' already exists");
    }

    DvSatellite satellite = new DvSatellite(name.trim());
    String tableName = proposal.parameter("tableName");
    if (!Utils.isEmpty(tableName)) {
      satellite.setTableName(tableName.trim());
    }
    satellite.setRecordSource(recordSource.trim());
    satellite.setAttributes(
        parseAttributes(proposal, recordSource.trim(), model, metadataProvider, variables));
    satellite.setLocation(defaultLocation(model, proposal));

    if (hubName != null) {
      satellite.setHubName(hubName);
      satellite.setLinkName(null);
    } else {
      satellite.setLinkName(linkName);
      satellite.setHubName(null);
      wireLinkSatellite(model, linkName, name.trim());
    }

    model.getTables().add(satellite);
    model.setChanged(true);
  }

  private static void setBusinessKeys(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    DvHub hub = requireHub(model, required(proposal, "tableName"));
    List<BusinessKey> keys =
        parseBusinessKeys(proposal, null, model, metadataProvider, variables, false);
    hub.setBusinessKeys(keys);
    Set<String> sources = new LinkedHashSet<>();
    for (BusinessKey key : keys) {
      if (!Utils.isEmpty(key.getRecordSourceName())) {
        sources.add(key.getRecordSourceName());
      }
    }
    if (!sources.isEmpty()) {
      hub.setRecordSources(new ArrayList<>(sources));
    }
    model.setChanged(true);
  }

  private static void bindRecordSource(DataVaultModel model, DvAiProposal proposal)
      throws HopException {
    String tableName = required(proposal, "tableName");
    String recordSource = required(proposal, "recordSource");
    IDvTable table = model.findTable(tableName.trim());
    if (table == null) {
      throw new HopException("Table not found: " + tableName);
    }
    if (table instanceof DvHub hub) {
      List<String> sources =
          hub.getRecordSources() != null
              ? new ArrayList<>(hub.getRecordSources())
              : new ArrayList<>();
      if (!sources.contains(recordSource.trim())) {
        sources.add(recordSource.trim());
      }
      hub.setRecordSources(sources);
    } else if (table instanceof DvSatellite satellite) {
      satellite.setRecordSource(recordSource.trim());
    } else {
      throw new HopException("BIND_RECORD_SOURCE supports hubs and satellites only");
    }
    model.setChanged(true);
  }

  private static void setTableLocation(DataVaultModel model, DvAiProposal proposal)
      throws HopException {
    IDvTable table = model.findTable(required(proposal, "tableName").trim());
    if (table == null) {
      throw new HopException("Table not found: " + proposal.parameter("tableName"));
    }
    table.setLocation(
        new Point(
            parseCoordinate(proposal.parameter("locationX"), 80),
            parseCoordinate(proposal.parameter("locationY"), 80)));
    model.setChanged(true);
  }

  private static void wireLinkSatellite(
      DataVaultModel model, String linkName, String satelliteName) {
    DvLink link = model.findLink(linkName);
    if (link == null) {
      return;
    }
    List<String> names =
        link.getLinkSatelliteNames() != null
            ? new ArrayList<>(link.getLinkSatelliteNames())
            : new ArrayList<>();
    if (!names.contains(satelliteName)) {
      names.add(satelliteName);
      link.setLinkSatelliteNames(names);
    }
  }

  private static DvAiProposalValidator.ValidationResult validateAddHub(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    String name = required(proposal, "name");
    String recordSource = required(proposal, "recordSource");
    if (model.findTable(name.trim()) != null) {
      return blocked(proposal, "A table named '" + name + "' already exists");
    }
    resolveCatalogSource(recordSource, model, metadataProvider, variables);
    parseBusinessKeys(proposal, recordSource.trim(), model, metadataProvider, variables, true);
    return ok(proposal);
  }

  private static DvAiProposalValidator.ValidationResult validateAddLink(
      DataVaultModel model, DvAiProposal proposal) throws HopException {
    String name = required(proposal, "name");
    List<String> hubNames = parseCommaList(required(proposal, "hubNames"));
    if (hubNames.size() < 2) {
      return blocked(proposal, "hubNames must list at least two hubs");
    }
    if (model.findTable(name.trim()) != null) {
      return blocked(proposal, "A table named '" + name + "' already exists");
    }
    for (String hubName : hubNames) {
      if (model.findHub(hubName) == null) {
        return blocked(proposal, "Hub not found: " + hubName);
      }
    }
    return ok(proposal);
  }

  private static DvAiProposalValidator.ValidationResult validateAddSatellite(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    String name = required(proposal, "name");
    String recordSource = required(proposal, "recordSource");
    String hubName = trimToNull(proposal.parameter("hubName"));
    String linkName = trimToNull(proposal.parameter("linkName"));
    if (hubName == null && linkName == null) {
      return blocked(proposal, "hubName or linkName is required");
    }
    if (hubName != null && linkName != null) {
      return blocked(proposal, "Provide hubName or linkName, not both");
    }
    if (model.findTable(name.trim()) != null) {
      return blocked(proposal, "A table named '" + name + "' already exists");
    }
    if (hubName != null && model.findHub(hubName) == null) {
      return blocked(proposal, "Hub not found: " + hubName);
    }
    if (linkName != null && model.findLink(linkName) == null) {
      return blocked(proposal, "Link not found: " + linkName);
    }
    resolveCatalogSource(recordSource, model, metadataProvider, variables);
    parseAttributes(proposal, recordSource.trim(), model, metadataProvider, variables);
    return ok(proposal);
  }

  private static DvAiProposalValidator.ValidationResult validateSetBusinessKeys(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    requireHub(model, required(proposal, "tableName"));
    parseBusinessKeys(proposal, null, model, metadataProvider, variables, false);
    return ok(proposal);
  }

  private static DvAiProposalValidator.ValidationResult validateBindRecordSource(
      DataVaultModel model,
      DvAiProposal proposal,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    String tableName = required(proposal, "tableName");
    String recordSource = required(proposal, "recordSource");
    IDvTable table = model.findTable(tableName.trim());
    if (table == null) {
      return blocked(proposal, "Table not found: " + tableName);
    }
    if (!(table instanceof DvHub) && !(table instanceof DvSatellite)) {
      return blocked(proposal, "BIND_RECORD_SOURCE supports hubs and satellites only");
    }
    resolveCatalogSource(recordSource, model, metadataProvider, variables);
    return ok(proposal);
  }

  private static DvAiProposalValidator.ValidationResult validateSetTableLocation(
      DataVaultModel model, DvAiProposal proposal) throws HopException {
    String tableName = required(proposal, "tableName");
    if (model.findTable(tableName.trim()) == null) {
      return blocked(proposal, "Table not found: " + tableName);
    }
    if (Utils.isEmpty(proposal.parameter("locationX"))
        || Utils.isEmpty(proposal.parameter("locationY"))) {
      return blocked(proposal, "locationX and locationY are required");
    }
    return ok(proposal);
  }

  private static List<BusinessKey> parseBusinessKeys(
      DvAiProposal proposal,
      String defaultRecordSource,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      boolean requiredNonEmpty)
      throws HopException {
    List<BusinessKey> keys = new ArrayList<>();
    String json = proposal.parameter("businessKeysJson");
    if (!Utils.isEmpty(json)) {
      keys.addAll(
          parseBusinessKeysJson(json, defaultRecordSource, model, metadataProvider, variables));
    } else {
      String keyName = trimToNull(proposal.parameter("businessKeyName"));
      if (keyName != null) {
        BusinessKey key = new BusinessKey(keyName);
        String sourceField = trimToNull(proposal.parameter("businessKeySourceField"));
        key.setSourceFieldName(sourceField != null ? sourceField : keyName);
        String rs = trimToNull(proposal.parameter("businessKeyRecordSource"));
        key.setRecordSourceName(rs != null ? rs : defaultRecordSource);
        enrichBusinessKeyFromCatalog(key, model, metadataProvider, variables);
        keys.add(key);
      }
    }
    if (requiredNonEmpty && keys.isEmpty()) {
      throw new HopException(
          "At least one business key is required (businessKeysJson or businessKeyName)");
    }
    if (!keys.isEmpty() && defaultRecordSource != null) {
      for (BusinessKey key : keys) {
        if (Utils.isEmpty(key.getRecordSourceName())) {
          key.setRecordSourceName(defaultRecordSource);
        }
        requireCatalogField(
            key.getRecordSourceName(),
            key.getSourceFieldName(),
            model,
            metadataProvider,
            variables);
      }
    } else if (!keys.isEmpty()) {
      for (BusinessKey key : keys) {
        requireCatalogField(
            key.getRecordSourceName(),
            key.getSourceFieldName(),
            model,
            metadataProvider,
            variables);
      }
    }
    return keys;
  }

  private static List<BusinessKey> parseBusinessKeysJson(
      String json,
      String defaultRecordSource,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    List<BusinessKey> keys = new ArrayList<>();
    try {
      ObjectMapper mapper = HopJson.newMapper();
      JsonNode array = mapper.readTree(json.trim());
      if (!array.isArray()) {
        throw new HopException("businessKeysJson must be a JSON array");
      }
      for (JsonNode node : array) {
        String name = node.path("name").asText(null);
        if (Utils.isEmpty(name)) {
          throw new HopException("Each business key requires a name");
        }
        BusinessKey key = new BusinessKey(name.trim());
        String sourceField = node.path("sourceFieldName").asText(null);
        key.setSourceFieldName(!Utils.isEmpty(sourceField) ? sourceField.trim() : name.trim());
        String rs = node.path("recordSourceName").asText(null);
        key.setRecordSourceName(!Utils.isEmpty(rs) ? rs.trim() : defaultRecordSource);
        if (!Utils.isEmpty(node.path("dataType").asText(null))) {
          key.setDataType(node.path("dataType").asText());
        }
        if (!Utils.isEmpty(node.path("length").asText(null))) {
          key.setLength(node.path("length").asText());
        }
        enrichBusinessKeyFromCatalog(key, model, metadataProvider, variables);
        keys.add(key);
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Invalid businessKeysJson: " + e.getMessage(), e);
    }
    return keys;
  }

  private static List<SatelliteAttribute> parseAttributes(
      DvAiProposal proposal,
      String recordSource,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    List<SatelliteAttribute> attributes = new ArrayList<>();
    String json = proposal.parameter("attributesJson");
    if (!Utils.isEmpty(json)) {
      attributes.addAll(
          parseAttributesJson(json, recordSource, model, metadataProvider, variables));
    } else {
      for (String name : parseCommaList(proposal.parameter("attributeNames"))) {
        SatelliteAttribute attribute = new SatelliteAttribute();
        attribute.setName(name);
        attribute.setDataType("String");
        enrichAttributeFromCatalog(
            attribute, name, recordSource, model, metadataProvider, variables);
        attributes.add(attribute);
      }
    }
    return attributes;
  }

  private static List<SatelliteAttribute> parseAttributesJson(
      String json,
      String recordSource,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    List<SatelliteAttribute> attributes = new ArrayList<>();
    try {
      ObjectMapper mapper = HopJson.newMapper();
      JsonNode array = mapper.readTree(json.trim());
      if (!array.isArray()) {
        throw new HopException("attributesJson must be a JSON array");
      }
      for (JsonNode node : array) {
        String name = node.path("name").asText(null);
        if (Utils.isEmpty(name)) {
          throw new HopException("Each satellite attribute requires a name");
        }
        SatelliteAttribute attribute = new SatelliteAttribute();
        attribute.setName(name.trim());
        if (!Utils.isEmpty(node.path("dataType").asText(null))) {
          attribute.setDataType(node.path("dataType").asText());
        }
        String sourceField = node.path("sourceFieldName").asText(null);
        enrichAttributeFromCatalog(
            attribute,
            !Utils.isEmpty(sourceField) ? sourceField.trim() : name.trim(),
            recordSource,
            model,
            metadataProvider,
            variables);
        attributes.add(attribute);
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Invalid attributesJson: " + e.getMessage(), e);
    }
    return attributes;
  }

  private static void enrichBusinessKeyFromCatalog(
      BusinessKey key,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    if (Utils.isEmpty(key.getRecordSourceName()) || Utils.isEmpty(key.getSourceFieldName())) {
      return;
    }
    SourceField field =
        findCatalogField(
            key.getRecordSourceName(),
            key.getSourceFieldName(),
            model,
            metadataProvider,
            variables);
    if (field == null) {
      return;
    }
    if (Utils.isEmpty(key.getDataType()) && !Utils.isEmpty(field.getSourceDataType())) {
      key.setDataType(field.getSourceDataType());
    }
    if (Utils.isEmpty(key.getLength()) && !Utils.isEmpty(field.getLength())) {
      key.setLength(field.getLength());
    }
    if (Utils.isEmpty(key.getDescription()) && !Utils.isEmpty(field.getDescription())) {
      key.setDescription(field.getDescription());
    }
  }

  private static void enrichAttributeFromCatalog(
      SatelliteAttribute attribute,
      String sourceFieldName,
      String recordSource,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    SourceField field =
        findCatalogField(recordSource, sourceFieldName, model, metadataProvider, variables);
    if (field == null) {
      return;
    }
    if (Utils.isEmpty(attribute.getDataType()) && !Utils.isEmpty(field.getSourceDataType())) {
      attribute.setDataType(field.getSourceDataType());
    }
    if (Utils.isEmpty(attribute.getLength()) && !Utils.isEmpty(field.getLength())) {
      attribute.setLength(field.getLength());
    }
    if (Utils.isEmpty(attribute.getDescription()) && !Utils.isEmpty(field.getDescription())) {
      attribute.setDescription(field.getDescription());
    }
  }

  private static void requireCatalogField(
      String recordSource,
      String fieldName,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    if (Utils.isEmpty(recordSource) || Utils.isEmpty(fieldName)) {
      throw new HopException("Business key requires recordSourceName and sourceFieldName");
    }
    if (findCatalogField(recordSource, fieldName, model, metadataProvider, variables) == null) {
      throw new HopException(
          "Field '" + fieldName + "' was not found in catalog source '" + recordSource + "'");
    }
  }

  private static SourceField findCatalogField(
      String recordSource,
      String fieldName,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    if (metadataProvider == null) {
      return null;
    }
    DataVaultSource source = resolveCatalogSource(recordSource, model, metadataProvider, variables);
    for (SourceField field : source.getFields()) {
      if (field != null
          && field.getName() != null
          && field.getName().equalsIgnoreCase(fieldName.trim())) {
        return field;
      }
    }
    return null;
  }

  private static DataVaultSource resolveCatalogSource(
      String recordSource,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    if (metadataProvider == null) {
      throw new HopException(
          "Metadata provider is required to resolve catalog source '" + recordSource + "'");
    }
    return DvSourceCatalogService.resolveSource(recordSource, model, variables, metadataProvider);
  }

  private static DvHub requireHub(DataVaultModel model, String tableName) throws HopException {
    DvHub hub = model.findHub(tableName.trim());
    if (hub == null) {
      throw new HopException("Hub not found: " + tableName);
    }
    return hub;
  }

  private static Point defaultLocation(DataVaultModel model, DvAiProposal proposal) {
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
    if (Utils.isEmpty(value)) {
      return null;
    }
    return value.trim();
  }

  private static DvAiProposalValidator.ValidationResult ok(DvAiProposal proposal) {
    return new DvAiProposalValidator.ValidationResult(
        proposal, DvAiProposalValidator.Status.OK, "");
  }

  private static DvAiProposalValidator.ValidationResult blocked(
      DvAiProposal proposal, String message) {
    return new DvAiProposalValidator.ValidationResult(
        proposal, DvAiProposalValidator.Status.BLOCKED, message);
  }
}
