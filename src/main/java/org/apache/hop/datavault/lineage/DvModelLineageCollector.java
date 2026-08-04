/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.lineage;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.CatalogModelRegistrySupport;
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.metadata.AttributeSource;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.BusinessKeySource;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DependentChildKey;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvReferenceTable;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Builds a {@link LineageSnapshot} from a Data Vault model by walking hubs, links, and satellites.
 *
 * <p>Lineage is a deterministic projection of model metadata (plus optional catalog namespace
 * context). Models remain the source of truth for loads and DDL.
 */
public final class DvModelLineageCollector {

  private DvModelLineageCollector() {}

  public static LineageSnapshot collect(DataVaultModel model, IVariables variables) {
    return collect(model, variables, null, null);
  }

  public static LineageSnapshot collect(
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String catalogConnection) {
    if (model == null) {
      throw new IllegalArgumentException("Data Vault model is required");
    }

    LineageSnapshot snapshot = new LineageSnapshot();
    snapshot.setId(UUID.randomUUID().toString());
    snapshot.setCapturedAt(new Date());
    snapshot.setProjectKey(DvCatalogNamespaces.resolveProjectKey(variables));
    snapshot.setModelLayer(LineageLayer.DV);
    snapshot.setModelName(model.getName());
    snapshot.setModelFilename(
        CatalogModelRegistrySupport.portableModelPath(model.getFilename(), variables));
    snapshot.setCatalogConnection(catalogConnection);

    DataVaultConfiguration config = model.getConfigurationOrDefault();
    String targetDb = config != null ? config.getTargetDatabase() : null;
    String sourcesNamespace =
        variables != null ? DvCatalogNamespaces.projectSourcesNamespace(variables) : null;

    for (IDvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      TableLineage tableLineage =
          switch (table.getTableType()) {
            case HUB ->
                collectHub((DvHub) table, model, config, variables, targetDb, sourcesNamespace);
            case LINK ->
                collectLink((DvLink) table, model, config, variables, targetDb, sourcesNamespace);
            case SATELLITE ->
                collectSatellite(
                    (DvSatellite) table, model, config, variables, targetDb, sourcesNamespace);
            case REFERENCE ->
                collectReference(
                    (DvReferenceTable) table, model, config, variables, targetDb, sourcesNamespace);
            case LINKED_TABLE, TABLE_REFERENCE ->
                collectGenericTable(table, model, config, variables, targetDb);
          };
      if (tableLineage != null) {
        snapshot.addTable(tableLineage);
      }
    }

    return snapshot;
  }

  private static TableLineage collectHub(
      DvHub hub,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      String targetDb,
      String sourcesNamespace) {
    TableLineage table = baseTable(hub, model, variables, targetDb, DvTableType.HUB.name());
    addNamingReasons(table, hub);

    List<String> recordSources =
        hub.getRecordSources() != null ? hub.getRecordSources() : List.of();
    for (String sourceRef : recordSources) {
      if (Utils.isEmpty(sourceRef)) {
        continue;
      }
      String sourceName = resolve(sourceRef, variables);
      table.addSource(dvSourceRef(sourceName, sourcesNamespace, TableSourceRole.RECORD_SOURCE));
      table.addReason(LineageReasonFactory.feedAttached(sourceName, "record source"));
    }

    Map<String, FieldLineage> fieldsByName = new LinkedHashMap<>();

    // Business keys — one contribution per BK row (per source mapping)
    Map<String, List<BusinessKey>> bksByTarget = new LinkedHashMap<>();
    if (hub.getBusinessKeys() != null) {
      for (BusinessKey bk : hub.getBusinessKeys()) {
        if (bk == null || Utils.isEmpty(bk.getName())) {
          continue;
        }
        String targetName = resolve(bk.getName(), variables);
        bksByTarget.computeIfAbsent(targetName, k -> new ArrayList<>()).add(bk);
      }
    }

    for (Map.Entry<String, List<BusinessKey>> entry : bksByTarget.entrySet()) {
      String targetName = entry.getKey();
      List<BusinessKey> bks = entry.getValue();
      FieldLineage field = fieldsByName.computeIfAbsent(targetName, FieldLineage::new);
      field.setTechnical(false);
      BusinessKey first = bks.get(0);
      field.setDataType(first.getDataType());
      field.setLength(first.getLength());
      field.setPrecision(first.getPrecision());

      for (BusinessKey bk : bks) {
        String sourceName = resolve(bk.getRecordSourceName(), variables);
        String sourceField =
            !Utils.isEmpty(bk.getSourceFieldName())
                ? resolve(bk.getSourceFieldName(), variables)
                : targetName;
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_SOURCE);
        contribution.setSourceName(sourceName);
        contribution.setSourceCatalogKey(catalogKey(sourcesNamespace, sourceName));
        contribution.setSourceFieldName(sourceField);
        contribution.setTransform(
            targetName.equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
        contribution.addReason(
            LineageReasonFactory.userExplicitMapping(targetName, sourceName, sourceField));
        if (bks.size() > 1) {
          contribution.addReason(LineageReasonFactory.multiSourceHub(targetName, bks.size()));
        }
        field.addContribution(contribution);
      }
      table.addField(field);
    }

    // Hash key
    String hashField = resolve(hub.getHashKeyFieldName(), variables);
    if (!Utils.isEmpty(hashField)) {
      String bkList = bksByTarget.keySet().stream().sorted().collect(Collectors.joining(", "));
      FieldLineage hashLineage = new FieldLineage(hashField);
      hashLineage.setTechnical(true);
      FieldContribution hashContribution = new FieldContribution();
      hashContribution.setSourceKind(TableSourceKind.CONFIG);
      hashContribution.setSourceName("model");
      hashContribution.setTransform(FieldTransform.DERIVED);
      hashContribution.addReason(LineageReasonFactory.hashFromBusinessKeys(hashField, bkList));
      // Also note BK fields as hash inputs
      for (String bkName : bksByTarget.keySet()) {
        FieldContribution input = new FieldContribution();
        input.setSourceKind(TableSourceKind.DV_TABLE);
        input.setSourceName(hub.getName());
        input.setSourceFieldName(bkName);
        input.setTransform(FieldTransform.HASH_INPUT);
        input.addReason(LineageReasonFactory.hashFromBusinessKeys(hashField, bkName));
        hashLineage.addContribution(input);
      }
      hashLineage.addContribution(hashContribution);
      table.addField(hashLineage);
    }

    addStandardColumns(table, hub, config, variables);
    return table;
  }

  private static TableLineage collectLink(
      DvLink link,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      String targetDb,
      String sourcesNamespace) {
    TableLineage table = baseTable(link, model, variables, targetDb, DvTableType.LINK.name());
    addNamingReasons(table, link);

    if (link.getHubNames() != null) {
      for (String hubName : link.getHubNames()) {
        if (Utils.isEmpty(hubName)) {
          continue;
        }
        String resolved = resolve(hubName, variables);
        TableSourceRef ref =
            new TableSourceRef(
                TableSourceKind.DV_TABLE, resolved, TableSourceRole.PARTICIPATING_HUB);
        table.addSource(ref);
      }
    }

    List<DvLink.DvLinkHubSource> hubSources =
        link.getLinkHubSources() != null ? link.getLinkHubSources() : List.of();
    for (DvLink.DvLinkHubSource hubSource : hubSources) {
      if (hubSource == null || Utils.isEmpty(hubSource.getSource())) {
        continue;
      }
      String sourceName = resolve(hubSource.getSource(), variables);
      table.addSource(dvSourceRef(sourceName, sourcesNamespace, TableSourceRole.RECORD_SOURCE));
      table.addReason(LineageReasonFactory.feedAttached(sourceName, "link hub source"));

      if (hubSource.getHubSourceKeyFields() == null) {
        continue;
      }
      for (DvLink.HubSourceKeyField hubKey : hubSource.getHubSourceKeyFields()) {
        if (hubKey == null) {
          continue;
        }
        String hubName = resolve(hubKey.getHubName(), variables);
        DvHub hub = model.findHub(hubName, variables, null);
        String parentHash =
            hub != null && !Utils.isEmpty(hub.getHashKeyFieldName())
                ? resolve(hub.getHashKeyFieldName(), variables)
                : hubName + "_hk";

        // Parent hash on link table
        FieldLineage parentHashField =
            table
                .findField(parentHash)
                .orElseGet(
                    () -> {
                      FieldLineage f = new FieldLineage(parentHash);
                      f.setTechnical(true);
                      table.addField(f);
                      return f;
                    });
        FieldContribution parentHashContribution = new FieldContribution();
        parentHashContribution.setSourceKind(TableSourceKind.DV_SOURCE);
        parentHashContribution.setSourceName(sourceName);
        parentHashContribution.setSourceCatalogKey(catalogKey(sourcesNamespace, sourceName));
        parentHashContribution.setTransform(FieldTransform.DERIVED);
        parentHashContribution.addReason(
            LineageReasonFactory.parentHashKey(parentHash, hubName, parentHash));
        parentHashField.addContribution(parentHashContribution);

        if (hubKey.getSourceBusinessKeyFields() == null) {
          continue;
        }
        for (BusinessKeySource bks : hubKey.getSourceBusinessKeyFields()) {
          if (bks == null || Utils.isEmpty(bks.getBusinessKeyField())) {
            continue;
          }
          String targetBk = resolve(bks.getBusinessKeyField(), variables);
          String sourceField =
              !Utils.isEmpty(bks.getSourceFieldName())
                  ? resolve(bks.getSourceFieldName(), variables)
                  : targetBk;
          // Document the source mapping used to compute the hub hash (not always a physical BK col)
          FieldLineage mappingField =
              table
                  .findField(targetBk)
                  .orElseGet(
                      () -> {
                        FieldLineage f = new FieldLineage(targetBk);
                        f.setTechnical(false);
                        table.addField(f);
                        return f;
                      });
          FieldContribution contribution = new FieldContribution();
          contribution.setSourceKind(TableSourceKind.DV_SOURCE);
          contribution.setSourceName(sourceName);
          contribution.setSourceCatalogKey(catalogKey(sourcesNamespace, sourceName));
          contribution.setSourceFieldName(sourceField);
          contribution.setTransform(
              targetBk.equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
          contribution.addReason(
              LineageReasonFactory.linkHubKeyMapping(hubName, targetBk, sourceName, sourceField));
          mappingField.addContribution(contribution);
        }
      }
    }

    // Dependent child keys
    if (link.getDependentChildKeys() != null) {
      for (DependentChildKey dck : link.getDependentChildKeys()) {
        if (dck == null || Utils.isEmpty(dck.getName())) {
          continue;
        }
        String targetName = resolve(dck.getName(), variables);
        String sourceField =
            !Utils.isEmpty(dck.getSourceFieldName())
                ? resolve(dck.getSourceFieldName(), variables)
                : targetName;
        FieldLineage field = new FieldLineage(targetName);
        field.setTechnical(false);
        field.setDataType(dck.getDataType());
        field.setLength(dck.getLength());
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_SOURCE);
        String feed =
            hubSources.isEmpty() || Utils.isEmpty(hubSources.get(0).getSource())
                ? ""
                : resolve(hubSources.get(0).getSource(), variables);
        contribution.setSourceName(feed);
        contribution.setSourceCatalogKey(catalogKey(sourcesNamespace, feed));
        contribution.setSourceFieldName(sourceField);
        contribution.setTransform(
            targetName.equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
        contribution.addReason(LineageReasonFactory.dependentChildKey(targetName, sourceField));
        field.addContribution(contribution);
        table.addField(field);
      }
    }

    // Link hash key
    String linkHash = resolve(link.getLinkHashKeyFieldName(), variables);
    if (Utils.isEmpty(linkHash)) {
      linkHash = resolve(link.getName(), variables) + "_LK";
    }
    FieldLineage linkHashField = new FieldLineage(linkHash);
    linkHashField.setTechnical(true);
    FieldContribution linkHashContribution = new FieldContribution();
    linkHashContribution.setSourceKind(TableSourceKind.CONFIG);
    linkHashContribution.setSourceName("model");
    linkHashContribution.setTransform(FieldTransform.DERIVED);
    linkHashContribution.addReason(
        LineageReasonFactory.hashFromBusinessKeys(linkHash, "participating hub hashes + DCKs"));
    linkHashField.addContribution(linkHashContribution);
    table.addField(linkHashField);

    addStandardColumns(table, link, config, variables);
    return table;
  }

  private static TableLineage collectSatellite(
      DvSatellite satellite,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      String targetDb,
      String sourcesNamespace) {
    TableLineage table =
        baseTable(satellite, model, variables, targetDb, DvTableType.SATELLITE.name());
    addNamingReasons(table, satellite);

    boolean linkSatellite = !Utils.isEmpty(satellite.getLinkName());
    String parentName =
        linkSatellite
            ? resolve(satellite.getLinkName(), variables)
            : resolve(satellite.getHubName(), variables);

    if (!Utils.isEmpty(parentName)) {
      table.addSource(
          new TableSourceRef(
              TableSourceKind.DV_TABLE,
              parentName,
              linkSatellite ? TableSourceRole.PARENT_LINK : TableSourceRole.PARENT_HUB));
    }

    String recordSource = resolve(satellite.getRecordSource(), variables);
    Map<String, String> attributeSourceMap = new LinkedHashMap<>();

    if (linkSatellite) {
      // Attribute source mappings live on the parent link
      DvLink parentLink = model.findLink(parentName, variables, null);
      if (parentLink != null && parentLink.getLinkSatelliteSources() != null) {
        for (DvLink.DvLinkSatelliteSource satSource : parentLink.getLinkSatelliteSources()) {
          if (satSource == null || Utils.isEmpty(satSource.getSource())) {
            continue;
          }
          String sourceName = resolve(satSource.getSource(), variables);
          if (Utils.isEmpty(recordSource)) {
            recordSource = sourceName;
          }
          table.addSource(dvSourceRef(sourceName, sourcesNamespace, TableSourceRole.SAT_FEED));
          table.addReason(LineageReasonFactory.feedAttached(sourceName, "link satellite source"));
          if (satSource.getSatelliteSourceKeyFields() == null) {
            continue;
          }
          for (DvLink.SatelliteSourceKeyField skf : satSource.getSatelliteSourceKeyFields()) {
            if (skf == null
                || (!Utils.isEmpty(skf.getSatelliteName())
                    && !satellite
                        .getName()
                        .equalsIgnoreCase(resolve(skf.getSatelliteName(), variables)))) {
              continue;
            }
            if (skf.getAttributeSources() == null) {
              continue;
            }
            for (AttributeSource attrSrc : skf.getAttributeSources()) {
              if (attrSrc == null || Utils.isEmpty(attrSrc.getAttributeField())) {
                continue;
              }
              String attr = resolve(attrSrc.getAttributeField(), variables);
              String srcField =
                  !Utils.isEmpty(attrSrc.getSourceFieldName())
                      ? resolve(attrSrc.getSourceFieldName(), variables)
                      : attr;
              attributeSourceMap.put(attr, srcField + "\0" + sourceName);
            }
          }
        }
      }
    } else if (!Utils.isEmpty(recordSource)) {
      table.addSource(dvSourceRef(recordSource, sourcesNamespace, TableSourceRole.RECORD_SOURCE));
      table.addReason(LineageReasonFactory.feedAttached(recordSource, "satellite record source"));
    }

    // Parent hash key
    if (!Utils.isEmpty(parentName)) {
      String parentHashField = null;
      if (linkSatellite) {
        DvLink parentLink = model.findLink(parentName, variables, null);
        if (parentLink != null) {
          parentHashField = resolve(parentLink.getLinkHashKeyFieldName(), variables);
          if (Utils.isEmpty(parentHashField)) {
            parentHashField = parentName + "_LK";
          }
        }
      } else {
        DvHub parentHub = model.findHub(parentName, variables, null);
        if (parentHub != null) {
          parentHashField = resolve(parentHub.getHashKeyFieldName(), variables);
        }
      }
      if (!Utils.isEmpty(parentHashField)) {
        FieldLineage hashLineage = new FieldLineage(parentHashField);
        hashLineage.setTechnical(true);
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_TABLE);
        contribution.setSourceName(parentName);
        contribution.setSourceFieldName(parentHashField);
        contribution.setTransform(FieldTransform.DERIVED);
        contribution.addReason(
            LineageReasonFactory.parentHashKey(parentHashField, parentName, parentHashField));
        hashLineage.addContribution(contribution);
        table.addField(hashLineage);
      }
    }

    // Attributes
    if (satellite.getAttributes() != null) {
      for (SatelliteAttribute attr : satellite.getAttributes()) {
        if (attr == null || Utils.isEmpty(attr.getName())) {
          continue;
        }
        String targetName = resolve(attr.getName(), variables);
        FieldLineage field = new FieldLineage(targetName);
        field.setTechnical(false);
        field.setDataType(attr.getDataType());
        field.setLength(attr.getLength());
        field.setPrecision(attr.getPrecision());

        String mapped = attributeSourceMap.get(targetName);
        String sourceField = targetName;
        String sourceName = recordSource;
        if (mapped != null) {
          int sep = mapped.indexOf('\0');
          sourceField = mapped.substring(0, sep);
          sourceName = mapped.substring(sep + 1);
        }

        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_SOURCE);
        contribution.setSourceName(sourceName);
        contribution.setSourceCatalogKey(catalogKey(sourcesNamespace, sourceName));
        contribution.setSourceFieldName(sourceField);
        contribution.setTransform(
            targetName.equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
        if (targetName.equals(sourceField)) {
          contribution.addReason(LineageReasonFactory.defaultSameAsSource(targetName, sourceName));
        } else {
          contribution.addReason(
              LineageReasonFactory.userExplicitMapping(targetName, sourceName, sourceField));
        }
        field.addContribution(contribution);
        table.addField(field);
      }
    }

    // Driving key
    if (!Utils.isEmpty(satellite.getDrivingKey())) {
      String dk = resolve(satellite.getDrivingKey(), variables);
      String dkSource =
          !Utils.isEmpty(satellite.getDrivingKeySourceField())
              ? resolve(satellite.getDrivingKeySourceField(), variables)
              : dk;
      FieldLineage field = new FieldLineage(dk);
      field.setTechnical(false);
      FieldContribution contribution = new FieldContribution();
      contribution.setSourceKind(TableSourceKind.DV_SOURCE);
      contribution.setSourceName(recordSource);
      contribution.setSourceCatalogKey(catalogKey(sourcesNamespace, recordSource));
      contribution.setSourceFieldName(dkSource);
      contribution.setTransform(
          dk.equals(dkSource) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
      contribution.addReason(LineageReasonFactory.drivingKey(dk, dkSource, recordSource));
      field.addContribution(contribution);
      table.addField(field);
    }

    addStandardColumns(table, satellite, config, variables);
    return table;
  }

  private static TableLineage collectReference(
      DvReferenceTable reference,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      String targetDb,
      String sourcesNamespace) {
    TableLineage table =
        baseTable(reference, model, variables, targetDb, DvTableType.REFERENCE.name());
    addNamingReasons(table, reference);

    List<String> recordSources =
        reference.getRecordSources() != null ? reference.getRecordSources() : List.of();
    for (String sourceRef : recordSources) {
      if (Utils.isEmpty(sourceRef)) {
        continue;
      }
      String sourceName = resolve(sourceRef, variables);
      table.addSource(dvSourceRef(sourceName, sourcesNamespace, TableSourceRole.RECORD_SOURCE));
      table.addReason(LineageReasonFactory.feedAttached(sourceName, "record source"));
    }

    if (reference.getNaturalKeys() != null) {
      for (BusinessKey key : reference.getNaturalKeys()) {
        if (key == null || Utils.isEmpty(key.getName())) {
          continue;
        }
        String targetName = resolve(key.getName(), variables);
        FieldLineage field = new FieldLineage(targetName);
        field.setTechnical(false);
        field.setDataType(key.getDataType());
        field.setLength(key.getLength());
        field.setPrecision(key.getPrecision());
        String sourceName = resolve(key.getRecordSourceName(), variables);
        String sourceField =
            !Utils.isEmpty(key.getSourceFieldName())
                ? resolve(key.getSourceFieldName(), variables)
                : targetName;
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_SOURCE);
        contribution.setSourceName(sourceName);
        contribution.setSourceCatalogKey(catalogKey(sourcesNamespace, sourceName));
        contribution.setSourceFieldName(sourceField);
        contribution.setTransform(
            targetName.equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
        contribution.addReason(
            LineageReasonFactory.userExplicitMapping(targetName, sourceName, sourceField));
        field.addContribution(contribution);
        table.addField(field);
      }
    }

    if (reference.getAttributes() != null) {
      for (SatelliteAttribute attr : reference.getAttributes()) {
        if (attr == null || Utils.isEmpty(attr.getName())) {
          continue;
        }
        String targetName = resolve(attr.getName(), variables);
        FieldLineage field = new FieldLineage(targetName);
        field.setTechnical(false);
        field.setDataType(attr.getDataType());
        field.setLength(attr.getLength());
        field.setPrecision(attr.getPrecision());
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_SOURCE);
        contribution.setSourceFieldName(targetName);
        contribution.setTransform(FieldTransform.IDENTITY);
        contribution.addReason(
            LineageReasonFactory.userExplicitMapping(targetName, "", targetName));
        field.addContribution(contribution);
        table.addField(field);
      }
    }

    addStandardColumns(table, reference, config, variables);
    return table;
  }

  private static TableLineage collectGenericTable(
      IDvTable table,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      String targetDb) {
    TableLineage lineage =
        baseTable(
            table,
            model,
            variables,
            targetDb,
            table.getTableType() != null ? table.getTableType().name() : "UNKNOWN");
    addNamingReasons(lineage, table);
    return lineage;
  }

  private static TableLineage baseTable(
      IDvTable table,
      DataVaultModel model,
      IVariables variables,
      String targetDb,
      String tableType) {
    TableLineage lineage = new TableLineage();
    lineage.setLayer(LineageLayer.DV);
    lineage.setLogicalName(table.getName());
    String physical = !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    lineage.setPhysicalTableName(physical);
    lineage.setTableType(tableType);
    lineage.setModelName(model.getName());
    lineage.setModelFilename(
        CatalogModelRegistrySupport.portableModelPath(model.getFilename(), variables));
    lineage.setTargetDatabaseMetaName(targetDb);
    lineage.setDescription(table.getDescription());
    return lineage;
  }

  private static void addNamingReasons(TableLineage lineage, IDvTable table) {
    lineage.addReason(
        LineageReasonFactory.userExplicitName(table.getName(), lineage.getPhysicalTableName()));
    if (table.getTableType() != null) {
      lineage.addReason(
          LineageReasonFactory.tableTypeRole(table.getTableType().name(), table.getName()));
    }
  }

  private static void addStandardColumns(
      TableLineage table, IDvTable dvTable, DataVaultConfiguration config, IVariables variables) {
    if (config == null) {
      return;
    }

    String loadDate = resolve(config.getLoadDateField(), variables);
    if (Utils.isEmpty(loadDate)) {
      loadDate = "LOAD_DATE";
    }
    if (table.findField(loadDate).isEmpty()) {
      FieldLineage field = new FieldLineage(loadDate);
      field.setTechnical(true);
      field.setDataType("Timestamp");
      FieldContribution contribution = new FieldContribution();
      contribution.setSourceKind(TableSourceKind.CONFIG);
      contribution.setSourceName("DataVaultConfiguration");
      contribution.setTransform(FieldTransform.CONSTANT);
      contribution.addReason(
          LineageReasonFactory.standardColumn(loadDate, "loadDateField", loadDate));
      field.addContribution(contribution);
      table.addField(field);
    }

    String recordSourceField = null;
    if (dvTable instanceof DvHub hub && !Utils.isEmpty(hub.getRecordSourceFieldName())) {
      recordSourceField = resolve(hub.getRecordSourceFieldName(), variables);
    } else if (dvTable instanceof DvLink link && !Utils.isEmpty(link.getRecordSourceFieldName())) {
      recordSourceField = resolve(link.getRecordSourceFieldName(), variables);
    }
    if (Utils.isEmpty(recordSourceField)) {
      recordSourceField = resolve(config.getRecordSourceField(), variables);
    }
    if (Utils.isEmpty(recordSourceField)) {
      recordSourceField = "RECORD_SOURCE";
    }
    if (table.findField(recordSourceField).isEmpty()) {
      FieldLineage field = new FieldLineage(recordSourceField);
      field.setTechnical(true);
      field.setDataType("String");
      FieldContribution contribution = new FieldContribution();
      contribution.setSourceKind(TableSourceKind.CONFIG);
      contribution.setSourceName("DataVaultConfiguration");
      contribution.setTransform(FieldTransform.CONSTANT);
      contribution.addReason(
          LineageReasonFactory.standardColumn(
              recordSourceField, "recordSourceField", recordSourceField));
      field.addContribution(contribution);
      table.addField(field);
    }

    // Load end date only for satellites when the model enables the pattern.
    if (dvTable instanceof DvSatellite && config.isUseLoadEndDate()) {
      String loadEnd = resolve(config.getLoadEndDateField(), variables);
      if (Utils.isEmpty(loadEnd)) {
        loadEnd = "LOAD_END_DATE";
      }
      if (table.findField(loadEnd).isEmpty()) {
        FieldLineage field = new FieldLineage(loadEnd);
        field.setTechnical(true);
        field.setDataType("Timestamp");
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.CONFIG);
        contribution.setSourceName("DataVaultConfiguration");
        contribution.setTransform(FieldTransform.DERIVED);
        contribution.addReason(
            LineageReasonFactory.standardColumn(loadEnd, "loadEndDateField", loadEnd));
        field.addContribution(contribution);
        table.addField(field);
      }
    }
  }

  private static TableSourceRef dvSourceRef(
      String sourceName, String sourcesNamespace, TableSourceRole role) {
    TableSourceRef ref = new TableSourceRef(TableSourceKind.DV_SOURCE, sourceName, role);
    ref.setCatalogKey(catalogKey(sourcesNamespace, sourceName));
    return ref;
  }

  private static String catalogKey(String sourcesNamespace, String sourceName) {
    if (Utils.isEmpty(sourceName)) {
      return null;
    }
    if (Utils.isEmpty(sourcesNamespace)) {
      return sourceName;
    }
    return sourcesNamespace + "/" + sourceName;
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
