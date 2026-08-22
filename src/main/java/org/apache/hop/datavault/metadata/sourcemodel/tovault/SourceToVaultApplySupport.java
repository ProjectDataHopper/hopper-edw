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
package org.apache.hop.datavault.metadata.sourcemodel.tovault;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.BusinessKeySource;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DependentChildKey;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvLinkedTable;
import org.apache.hop.datavault.metadata.DvOrphanHandlingSupport;
import org.apache.hop.datavault.metadata.DvReferenceTable;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourceJsonCatalogPublisher;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourcePipelineCatalogPublisher;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourceQueryCatalogPublisher;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourceTableCatalogPublisher;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Writes accepted {@link SourceToVaultProposal}s into a {@link DataVaultModel} (new or existing).
 *
 * <p>Order is hubs, then links, then satellites so parents exist before children. Existing objects
 * marked {@code reuseExisting} are not overwritten.
 */
public final class SourceToVaultApplySupport {

  private SourceToVaultApplySupport() {}

  public static SourceToVaultApplyResult apply(
      SourceModel sourceModel,
      DataVaultModel vaultModel,
      SourceToVaultClassification classification,
      boolean publishToCatalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return apply(
        sourceModel,
        vaultModel,
        classification,
        publishToCatalog,
        variables,
        metadataProvider,
        SourceToVaultOptions.defaults());
  }

  public static SourceToVaultApplyResult apply(
      SourceModel sourceModel,
      DataVaultModel vaultModel,
      SourceToVaultClassification classification,
      boolean publishToCatalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceToVaultOptions options)
      throws HopException {
    SourceToVaultApplyResult result = new SourceToVaultApplyResult();
    if (sourceModel == null) {
      throw new HopException("Source model is required");
    }
    if (vaultModel == null) {
      throw new HopException("Data Vault model is required");
    }
    if (classification == null) {
      return result;
    }

    List<ProposedVaultObject> hubs = new ArrayList<>();
    List<ProposedVaultObject> aliases = new ArrayList<>();
    List<ProposedVaultObject> references = new ArrayList<>();
    List<ProposedVaultObject> links = new ArrayList<>();
    List<ProposedVaultObject> sats = new ArrayList<>();
    collectIncluded(classification, hubs, aliases, references, links, sats);

    Map<String, String> actualNames = new LinkedHashMap<>();

    for (ProposedVaultObject object : hubs) {
      applyHub(
          sourceModel,
          vaultModel,
          object,
          publishToCatalog,
          variables,
          metadataProvider,
          result,
          actualNames);
    }
    for (ProposedVaultObject object : aliases) {
      applyLinkedTable(vaultModel, object, result, actualNames);
    }
    for (ProposedVaultObject object : references) {
      applyReference(
          sourceModel,
          vaultModel,
          object,
          publishToCatalog,
          variables,
          metadataProvider,
          result,
          actualNames);
    }
    for (ProposedVaultObject object : links) {
      applyLink(sourceModel, vaultModel, object, result, actualNames);
    }
    for (ProposedVaultObject object : sats) {
      applySatellite(sourceModel, vaultModel, object, result, actualNames);
    }

    if (options != null && options.isSeedParentHubsFromChildFeeds()) {
      seedParentHubsFromAppliedChildren(vaultModel, variables);
    }

    vaultModel.setChanged(true);
    return result;
  }

  private static void seedParentHubsFromAppliedChildren(
      DataVaultModel vaultModel, IVariables variables) {
    if (vaultModel == null || vaultModel.getTables() == null) {
      return;
    }
    for (IDvTable table : vaultModel.getTables()) {
      if (table instanceof DvLink link && link.getLinkHubSources() != null) {
        for (DvLink.DvLinkHubSource source : link.getLinkHubSources()) {
          DvOrphanHandlingSupport.seedParentHubsFromLink(vaultModel, link, source, variables);
        }
      }
      if (table instanceof DvSatellite satellite) {
        DvOrphanHandlingSupport.seedParentHubFromSatellite(vaultModel, satellite, variables);
      }
    }
  }

  private static void collectIncluded(
      SourceToVaultClassification classification,
      List<ProposedVaultObject> hubs,
      List<ProposedVaultObject> aliases,
      List<ProposedVaultObject> references,
      List<ProposedVaultObject> links,
      List<ProposedVaultObject> sats) {
    for (SourceToVaultProposal proposal : classification.getProposals()) {
      if (proposal == null
          || !proposal.isIncluded()
          || proposal.getRole() == SourceTableRole.SKIP) {
        continue;
      }
      for (ProposedVaultObject object : proposal.getObjects()) {
        if (object == null
            || !object.isIncluded()
            || object.getKind() == null
            || Utils.isEmpty(object.getName())) {
          continue;
        }
        switch (object.getKind()) {
          case HUB -> addIfNew(hubs, object);
          case LINKED_TABLE -> addIfNew(aliases, object);
          case REFERENCE -> addIfNew(references, object);
          case LINK -> addIfNew(links, object);
          case SATELLITE -> addIfNew(sats, object);
        }
      }
    }
  }

  private static void addIfNew(List<ProposedVaultObject> list, ProposedVaultObject object) {
    for (ProposedVaultObject existing : list) {
      if (object.getKind() == existing.getKind()
          && object.getName().equalsIgnoreCase(existing.getName())) {
        return;
      }
    }
    list.add(object);
  }

  private static void applyHub(
      SourceModel sourceModel,
      DataVaultModel vaultModel,
      ProposedVaultObject object,
      boolean publishToCatalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceToVaultApplyResult result,
      Map<String, String> actualNames)
      throws HopException {
    if (object.isReuseExisting() && vaultModel.findHub(object.getName()) != null) {
      actualNames.put(object.getName(), object.getName());
      result.getReusedTableNames().add(object.getName());
      return;
    }
    IDvTable existing = vaultModel.findTable(object.getName());
    if (existing instanceof DvHub) {
      actualNames.put(object.getName(), existing.getName());
      result.getReusedTableNames().add(existing.getName());
      return;
    }

    String name = uniqueName(vaultModel, object.getName());
    actualNames.put(object.getName(), name);

    ClassifiableSource source = resolveSource(sourceModel, object);
    String feed =
        resolveFeed(
            sourceModel, source, object, publishToCatalog, variables, metadataProvider, result);

    DvHub hub = new DvHub(name);
    hub.setTableName(name);
    hub.setHashKeyFieldName(SourceToVaultNaming.entityName(name) + "_hk");
    if (!Utils.isEmpty(feed)) {
      hub.getRecordSources().add(feed);
    }
    for (String columnName : object.getBusinessKeyColumns()) {
      SourceColumn column = source != null ? source.findColumn(columnName) : null;
      hub.getBusinessKeys().add(businessKeyFromColumn(column, columnName, feed));
    }
    vaultModel.getTables().add(hub);
    result.getCreatedTableNames().add(name);
  }

  private static void applyLink(
      SourceModel sourceModel,
      DataVaultModel vaultModel,
      ProposedVaultObject object,
      SourceToVaultApplyResult result,
      Map<String, String> actualNames) {
    List<String> resolvedHubs = new ArrayList<>();
    for (String hubName : object.getParticipatingHubNames()) {
      String actual = actualNames.getOrDefault(hubName, hubName);
      if (vaultModel.findHub(actual) == null) {
        result
            .getWarnings()
            .add("Skipped link " + object.getName() + ": hub " + actual + " is missing");
        return;
      }
      resolvedHubs.add(actual);
    }
    if (resolvedHubs.size() < 2) {
      result.getWarnings().add("Skipped link " + object.getName() + ": fewer than two hubs");
      return;
    }

    if (vaultModel.findLink(object.getName()) != null) {
      actualNames.put(object.getName(), object.getName());
      result.getReusedTableNames().add(object.getName());
      return;
    }

    String name = uniqueName(vaultModel, object.getName());
    actualNames.put(object.getName(), name);

    ClassifiableSource source = resolveSource(sourceModel, object);
    String feed = object.getCatalogSourceName();
    if (Utils.isEmpty(feed) && source != null) {
      feed = source.getCatalogSourceName();
    }

    DvLink link = new DvLink(name);
    link.setTableName(name);
    link.setLinkHashKeyFieldName(name + "_hk");
    link.setHubNames(new ArrayList<>(resolvedHubs));

    DvLink.DvLinkHubSource hubSource = new DvLink.DvLinkHubSource();
    hubSource.setSource(feed);
    for (String proposedHub : object.getParticipatingHubNames()) {
      String actualHub = actualNames.getOrDefault(proposedHub, proposedHub);
      DvHub hub = vaultModel.findHub(actualHub);
      List<String> sourceCols = object.getHubSourceKeyColumns().get(proposedHub);
      if (sourceCols == null) {
        sourceCols = object.getHubSourceKeyColumns().get(actualHub);
      }
      DvLink.HubSourceKeyField mapping = new DvLink.HubSourceKeyField();
      mapping.setHubName(actualHub);
      if (hub != null && hub.getBusinessKeys() != null) {
        for (int i = 0; i < hub.getBusinessKeys().size(); i++) {
          BusinessKey key = hub.getBusinessKeys().get(i);
          String sourceCol =
              sourceCols != null && i < sourceCols.size()
                  ? sourceCols.get(i)
                  : (sourceCols != null && !sourceCols.isEmpty()
                      ? sourceCols.get(0)
                      : key.getName());
          mapping.getSourceBusinessKeyFields().add(new BusinessKeySource(key.getName(), sourceCol));
        }
      }
      hubSource.getHubSourceKeyFields().add(mapping);
    }
    link.getLinkHubSources().add(hubSource);

    for (String dckName : object.getDependentChildKeyColumns()) {
      DependentChildKey dck = new DependentChildKey(dckName);
      dck.setSourceFieldName(dckName);
      SourceColumn column = source != null ? source.findColumn(dckName) : null;
      if (column != null) {
        dck.setDataType(hopTypeName(column));
        dck.setLength(column.getLength());
        dck.setPrecision(column.getPrecision());
      }
      link.getDependentChildKeys().add(dck);
    }

    vaultModel.getTables().add(link);
    result.getCreatedTableNames().add(name);
  }

  private static void applySatellite(
      SourceModel sourceModel,
      DataVaultModel vaultModel,
      ProposedVaultObject object,
      SourceToVaultApplyResult result,
      Map<String, String> actualNames) {
    String parentHub =
        !Utils.isEmpty(object.getParentHubName())
            ? actualNames.getOrDefault(object.getParentHubName(), object.getParentHubName())
            : null;
    String parentLink =
        !Utils.isEmpty(object.getParentLinkName())
            ? actualNames.getOrDefault(object.getParentLinkName(), object.getParentLinkName())
            : null;
    if (!Utils.isEmpty(parentHub) && vaultModel.findHub(parentHub) == null) {
      result
          .getWarnings()
          .add("Skipped satellite " + object.getName() + ": hub " + parentHub + " is missing");
      return;
    }
    if (!Utils.isEmpty(parentLink) && vaultModel.findLink(parentLink) == null) {
      result
          .getWarnings()
          .add("Skipped satellite " + object.getName() + ": link " + parentLink + " is missing");
      return;
    }

    if (vaultModel.findTable(object.getName()) instanceof DvSatellite) {
      actualNames.put(object.getName(), object.getName());
      result.getReusedTableNames().add(object.getName());
      return;
    }

    String name = uniqueName(vaultModel, object.getName());
    actualNames.put(object.getName(), name);

    ClassifiableSource source = resolveSource(sourceModel, object);
    String feed = object.getCatalogSourceName();
    if (Utils.isEmpty(feed) && source != null) {
      feed = source.getCatalogSourceName();
    }

    DvSatellite satellite = new DvSatellite(name);
    satellite.setTableName(name);
    satellite.setRecordSource(feed);
    if (!Utils.isEmpty(object.getDrivingKeyColumn())) {
      satellite.setDrivingKey(object.getDrivingKeyColumn());
      satellite.setDrivingKeySourceField(object.getDrivingKeyColumn());
    }
    if (!Utils.isEmpty(parentHub)) {
      satellite.setHubName(parentHub);
      DvHub hub = vaultModel.findHub(parentHub);
      if (hub != null && hub.getBusinessKeys() != null) {
        for (BusinessKey key : hub.getBusinessKeys()) {
          String sourceField = key.getName();
          if (source != null && source.findColumn(key.getName()) == null) {
            List<String> parts = key.resolveSourceParts();
            if (!parts.isEmpty() && source.findColumn(parts.get(0)) != null) {
              sourceField = parts.get(0);
            }
          }
          satellite.getParentKeySourceFields().add(sourceField);
        }
      }
    } else {
      satellite.setLinkName(parentLink);
      DvLink link = vaultModel.findLink(parentLink);
      if (link != null) {
        List<String> names =
            link.getLinkSatelliteNames() != null
                ? new ArrayList<>(link.getLinkSatelliteNames())
                : new ArrayList<>();
        names.add(name);
        link.setLinkSatelliteNames(names);
        link.setHasDescriptiveAttributes(true);
      }
    }

    for (String columnName : object.getSatelliteAttributeColumns()) {
      SourceColumn column = source != null ? source.findColumn(columnName) : null;
      satellite.getAttributes().add(attributeFromColumn(column, columnName));
    }

    vaultModel.getTables().add(satellite);
    result.getCreatedTableNames().add(name);
  }

  private static void applyLinkedTable(
      DataVaultModel vaultModel,
      ProposedVaultObject object,
      SourceToVaultApplyResult result,
      Map<String, String> actualNames) {
    if (vaultModel.findTable(object.getName()) instanceof DvLinkedTable) {
      actualNames.put(object.getName(), object.getName());
      result.getReusedTableNames().add(object.getName());
      return;
    }
    String referenced =
        actualNames.getOrDefault(object.getReferencedTableName(), object.getReferencedTableName());
    String name = uniqueName(vaultModel, object.getName());
    actualNames.put(object.getName(), name);
    DvLinkedTable alias = new DvLinkedTable();
    alias.setName(name);
    alias.setTableName(referenced);
    alias.setReferencedTableName(referenced);
    alias.setReferencedTableType(
        object.getReferencedTableType() != null
            ? object.getReferencedTableType()
            : DvTableType.HUB);
    alias.setHashKeyFieldName(object.getRoleHashKeyFieldName());
    vaultModel.getTables().add(alias);
    result.getCreatedTableNames().add(name);
  }

  private static void applyReference(
      SourceModel sourceModel,
      DataVaultModel vaultModel,
      ProposedVaultObject object,
      boolean publishToCatalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceToVaultApplyResult result,
      Map<String, String> actualNames)
      throws HopException {
    if (vaultModel.findTable(object.getName()) instanceof DvReferenceTable) {
      actualNames.put(object.getName(), object.getName());
      result.getReusedTableNames().add(object.getName());
      return;
    }
    String name = uniqueName(vaultModel, object.getName());
    actualNames.put(object.getName(), name);
    ClassifiableSource source = resolveSource(sourceModel, object);
    String feed =
        resolveFeed(
            sourceModel, source, object, publishToCatalog, variables, metadataProvider, result);
    DvReferenceTable reference = new DvReferenceTable(name);
    reference.setTableName(name);
    if (!Utils.isEmpty(feed)) {
      reference.getRecordSources().add(feed);
    }
    for (String columnName : object.getBusinessKeyColumns()) {
      SourceColumn column = source != null ? source.findColumn(columnName) : null;
      reference.getNaturalKeys().add(businessKeyFromColumn(column, columnName, feed));
    }
    for (String columnName : object.getSatelliteAttributeColumns()) {
      SourceColumn column = source != null ? source.findColumn(columnName) : null;
      reference.getAttributes().add(attributeFromColumn(column, columnName));
    }
    vaultModel.getTables().add(reference);
    result.getCreatedTableNames().add(name);
  }

  private static ClassifiableSource resolveSource(
      SourceModel sourceModel, ProposedVaultObject object) {
    if (sourceModel == null || object == null || Utils.isEmpty(object.getSourceTableName())) {
      return null;
    }
    SourceEndpointKind kind =
        object.getSourceKind() != null ? object.getSourceKind() : SourceEndpointKind.TABLE;
    return ClassifiableSource.of(sourceModel, kind, object.getSourceTableName());
  }

  private static String resolveFeed(
      SourceModel sourceModel,
      ClassifiableSource source,
      ProposedVaultObject object,
      boolean publishToCatalog,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceToVaultApplyResult result) {
    if (source != null && !Utils.isEmpty(source.getCatalogSourceName())) {
      if (publishToCatalog && metadataProvider != null) {
        String published = publishSource(sourceModel, source, variables, metadataProvider, result);
        if (!Utils.isEmpty(published)) {
          return published;
        }
      }
      return source.getCatalogSourceName();
    }
    if (!Utils.isEmpty(object.getCatalogSourceName())) {
      return object.getCatalogSourceName();
    }
    return object.getSourceTableName();
  }

  private static String publishSource(
      SourceModel sourceModel,
      ClassifiableSource source,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceToVaultApplyResult result) {
    try {
      String catalogName =
          switch (source.getKind()) {
            case TABLE -> {
              var published =
                  SourceTableCatalogPublisher.publish(
                      sourceModel,
                      sourceModel.findTable(source.getName()),
                      null,
                      variables,
                      metadataProvider);
              yield published != null ? published.catalogName() : null;
            }
            case QUERY -> {
              var published =
                  SourceQueryCatalogPublisher.publish(
                      sourceModel,
                      sourceModel.findQuery(source.getName()),
                      null,
                      variables,
                      metadataProvider);
              yield published != null ? published.catalogName() : null;
            }
            case JSON -> {
              var published =
                  SourceJsonCatalogPublisher.publish(
                      sourceModel,
                      sourceModel.findJsonSource(source.getName()),
                      null,
                      variables,
                      metadataProvider);
              yield published != null ? published.catalogName() : null;
            }
            case PIPELINE -> {
              var published =
                  SourcePipelineCatalogPublisher.publish(
                      sourceModel,
                      sourceModel.findPipelineSource(source.getName()),
                      null,
                      variables,
                      metadataProvider);
              yield published != null ? published.catalogName() : null;
            }
          };
      if (!Utils.isEmpty(catalogName)) {
        result.getPublishedFeeds().add(catalogName);
      }
      return catalogName;
    } catch (Exception e) {
      result
          .getWarnings()
          .add("Could not publish catalog feed for " + source.getName() + ": " + e.getMessage());
      return null;
    }
  }

  private static BusinessKey businessKeyFromColumn(
      SourceColumn column, String fallbackName, String recordSource) {
    String name =
        column != null && !Utils.isEmpty(column.getName()) ? column.getName() : fallbackName;
    BusinessKey key = new BusinessKey(name);
    key.setSourceFieldName(name);
    key.setRecordSourceName(recordSource);
    if (column != null) {
      key.setDescription(column.getDescription());
      key.setDataType(hopTypeName(column));
      key.setLength(column.getLength());
      key.setPrecision(column.getPrecision());
    }
    return key;
  }

  private static SatelliteAttribute attributeFromColumn(SourceColumn column, String fallbackName) {
    String name =
        column != null && !Utils.isEmpty(column.getName()) ? column.getName() : fallbackName;
    SatelliteAttribute attribute = new SatelliteAttribute(name);
    attribute.setIncludeInChangeDataCapture(true);
    if (column != null) {
      attribute.setDescription(column.getDescription());
      attribute.setDataType(hopTypeName(column));
      attribute.setLength(column.getLength());
      attribute.setPrecision(column.getPrecision());
    }
    return attribute;
  }

  private static String hopTypeName(SourceColumn column) {
    if (column == null || column.getHopType() <= 0) {
      return "String";
    }
    try {
      String name = ValueMetaFactory.getValueMetaName(column.getHopType());
      if (!Utils.isEmpty(name) && !"-".equals(name) && !"None".equalsIgnoreCase(name)) {
        return name;
      }
    } catch (Exception ignored) {
      // Fall through to String.
    }
    return "String";
  }

  static String uniqueName(DataVaultModel model, String requested) {
    if (model.findTable(requested) == null) {
      return requested;
    }
    int index = 2;
    String candidate = requested + "_" + index;
    while (model.findTable(candidate) != null) {
      index++;
      candidate = requested + "_" + index;
    }
    return candidate;
  }
}
