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
package org.apache.hop.datavault.resourcedefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.BusinessKeySource;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.datavault.metadata.businessvault.BvScd2FieldMapping;
import org.apache.hop.datavault.metadata.businessvault.BvScd2Table;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmSourceType;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;

/** Builds a reverse index from catalog record definitions to model usages. */
public final class SourceUsageIndexBuilder {

  public static final String MODEL_TYPE_DATA_VAULT = "DATA_VAULT_MODEL";
  public static final String MODEL_TYPE_BUSINESS_VAULT = "BUSINESS_VAULT_MODEL";
  public static final String MODEL_TYPE_DIMENSIONAL = "DIMENSIONAL_MODEL";

  private SourceUsageIndexBuilder() {}

  public static Map<RecordDefinitionKey, List<SourceUsage>> build(
      ValidationModels models, IVariables variables) {
    Map<RecordDefinitionKey, List<SourceUsage>> index = new LinkedHashMap<>();
    if (models == null) {
      return index;
    }
    String sourcesNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    for (ValidationModels.LoadedDataVaultModel loaded : models.dataVaultModels()) {
      indexDataVaultModel(index, loaded, variables, sourcesNamespace);
    }
    for (ValidationModels.LoadedDimensionalModel loaded : models.dimensionalModels()) {
      indexDimensionalModel(index, loaded, variables);
    }
    for (ValidationModels.LoadedBusinessVaultModel loaded : models.businessVaultModels()) {
      indexBusinessVaultModel(index, loaded, variables, sourcesNamespace);
    }
    return index;
  }

  private static void indexDataVaultModel(
      Map<RecordDefinitionKey, List<SourceUsage>> index,
      ValidationModels.LoadedDataVaultModel loaded,
      IVariables variables,
      String sourcesNamespace) {
    if (loaded == null || loaded.model() == null) {
      return;
    }
    DataVaultModel model = loaded.model();
    String namespace = sourcesNamespace;
    for (IDvTable table : model.getTables()) {
      if (table instanceof DvHub hub) {
        indexHub(index, model, loaded.catalogConnection(), namespace, hub, variables);
      } else if (table instanceof DvSatellite satellite) {
        indexSatellite(index, model, loaded.catalogConnection(), namespace, satellite, variables);
      } else if (table instanceof DvLink link) {
        indexLink(index, model, loaded.catalogConnection(), namespace, link, variables);
      }
    }
  }

  private static void indexHub(
      Map<RecordDefinitionKey, List<SourceUsage>> index,
      DataVaultModel model,
      String catalogConnection,
      String namespace,
      DvHub hub,
      IVariables variables) {
    if (hub.getRecordSources() == null) {
      return;
    }
    List<String> mappedFields = new ArrayList<>();
    if (hub.getBusinessKeys() != null) {
      for (BusinessKey bk : hub.getBusinessKeys()) {
        if (bk != null && !Utils.isEmpty(bk.getSourceFieldName())) {
          mappedFields.add(bk.getSourceFieldName());
        }
      }
    }
    for (String sourceName : hub.getRecordSources()) {
      if (Utils.isEmpty(sourceName)) {
        continue;
      }
      addUsage(
          index,
          new RecordDefinitionKey(namespace, resolveName(variables, sourceName)),
          SourceUsage.builder()
              .modelType(MODEL_TYPE_DATA_VAULT)
              .modelName(model.getName())
              .modelFilename(model.getFilename())
              .modelElementName(hub.getName())
              .catalogConnection(catalogConnection)
              .mappedFields(mappedFields)
              .build());
    }
  }

  private static void indexSatellite(
      Map<RecordDefinitionKey, List<SourceUsage>> index,
      DataVaultModel model,
      String catalogConnection,
      String namespace,
      DvSatellite satellite,
      IVariables variables) {
    String sourceName = satellite.getRecordSourceName();
    if (Utils.isEmpty(sourceName)) {
      return;
    }
    List<String> mappedFields = new ArrayList<>();
    if (!Utils.isEmpty(satellite.getDrivingKeySourceField())) {
      mappedFields.add(satellite.getDrivingKeySourceField());
    }
    List<SatelliteAttribute> attributes = satellite.getAttributes();
    if (attributes != null && !attributes.isEmpty()) {
      for (SatelliteAttribute attribute : attributes) {
        if (attribute != null && !Utils.isEmpty(attribute.getName())) {
          mappedFields.add(attribute.getName());
        }
      }
    }
    addUsage(
        index,
        new RecordDefinitionKey(namespace, resolveName(variables, sourceName)),
        SourceUsage.builder()
            .modelType(MODEL_TYPE_DATA_VAULT)
            .modelName(model.getName())
            .modelFilename(model.getFilename())
            .modelElementName(satellite.getName())
            .catalogConnection(catalogConnection)
            .mappedFields(mappedFields)
            .build());
  }

  private static void indexLink(
      Map<RecordDefinitionKey, List<SourceUsage>> index,
      DataVaultModel model,
      String catalogConnection,
      String namespace,
      DvLink link,
      IVariables variables) {
    if (link.getLinkHubSources() == null) {
      return;
    }
    for (DvLink.DvLinkHubSource linkSource : link.getLinkHubSources()) {
      if (linkSource == null || Utils.isEmpty(linkSource.getSourceName())) {
        continue;
      }
      List<String> mappedFields = new ArrayList<>();
      if (linkSource.getHubSourceKeyFields() != null) {
        for (DvLink.HubSourceKeyField hubSourceKeyField : linkSource.getHubSourceKeyFields()) {
          if (hubSourceKeyField == null || hubSourceKeyField.getSourceBusinessKeyFields() == null) {
            continue;
          }
          for (BusinessKeySource businessKeySource :
              hubSourceKeyField.getSourceBusinessKeyFields()) {
            if (businessKeySource != null
                && !Utils.isEmpty(businessKeySource.getSourceFieldName())) {
              mappedFields.add(businessKeySource.getSourceFieldName());
            }
          }
        }
      }
      if (link.getDependentChildKeys() != null) {
        for (org.apache.hop.datavault.metadata.DependentChildKey dck :
            link.getDependentChildKeys()) {
          if (dck != null && !Utils.isEmpty(dck.resolveSourceFieldName())) {
            mappedFields.add(dck.resolveSourceFieldName());
          }
        }
      }
      addUsage(
          index,
          new RecordDefinitionKey(namespace, resolveName(variables, linkSource.getSourceName())),
          SourceUsage.builder()
              .modelType(MODEL_TYPE_DATA_VAULT)
              .modelName(model.getName())
              .modelFilename(model.getFilename())
              .modelElementName(link.getName())
              .catalogConnection(catalogConnection)
              .mappedFields(mappedFields)
              .build());
    }
  }

  private static void indexDimensionalModel(
      Map<RecordDefinitionKey, List<SourceUsage>> index,
      ValidationModels.LoadedDimensionalModel loaded,
      IVariables variables) {
    if (loaded == null || loaded.model() == null) {
      return;
    }
    for (IDmTable table : loaded.model().getTables()) {
      if (table == null) {
        continue;
      }
      DmSourceConfiguration source = table.getSourceOrDefault();
      if (source.resolveSourceType() != DmSourceType.RECORD_DEFINITION) {
        continue;
      }
      String namespace =
          variables != null
              ? variables.resolve(source.getSourceRecordNamespace())
              : source.getSourceRecordNamespace();
      String name =
          variables != null
              ? variables.resolve(source.getSourceRecordName())
              : source.getSourceRecordName();
      if (Utils.isEmpty(namespace) || Utils.isEmpty(name)) {
        continue;
      }
      addUsage(
          index,
          new RecordDefinitionKey(namespace, name),
          SourceUsage.builder()
              .modelType(MODEL_TYPE_DIMENSIONAL)
              .modelName(loaded.model().getName())
              .modelFilename(loaded.model().getFilename())
              .modelElementName(table.getName())
              .catalogConnection(loaded.catalogConnection())
              .build());
    }
  }

  private static void indexBusinessVaultModel(
      Map<RecordDefinitionKey, List<SourceUsage>> index,
      ValidationModels.LoadedBusinessVaultModel loaded,
      IVariables variables,
      String sourcesNamespace) {
    if (loaded == null || loaded.model() == null || loaded.dvModel() == null) {
      return;
    }
    DataVaultModel dvModel = loaded.dvModel();
    String namespace = sourcesNamespace;
    for (IBvTable table : loaded.model().getTables()) {
      if (!(table instanceof BvScd2Table scd2Table)) {
        continue;
      }
      List<String> mappedFields = new ArrayList<>();
      if (scd2Table.getFieldMappings() != null) {
        for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
          if (mapping != null && !Utils.isEmpty(mapping.getSourceFieldName())) {
            mappedFields.add(mapping.getSourceFieldName());
          }
        }
      }
      if (scd2Table.getSatelliteConfigs() == null) {
        continue;
      }
      for (var config : scd2Table.getSatelliteConfigs()) {
        if (config == null || Utils.isEmpty(config.getSatelliteName())) {
          continue;
        }
        if (!(dvModel.findTable(config.getSatelliteName()) instanceof DvSatellite satellite)) {
          continue;
        }
        String sourceName = satellite.getRecordSourceName();
        if (Utils.isEmpty(sourceName)) {
          continue;
        }
        addUsage(
            index,
            new RecordDefinitionKey(namespace, resolveName(variables, sourceName)),
            SourceUsage.builder()
                .modelType(MODEL_TYPE_BUSINESS_VAULT)
                .modelName(loaded.model().getName())
                .modelFilename(loaded.model().getFilename())
                .modelElementName(scd2Table.getName())
                .catalogConnection(loaded.catalogConnection())
                .mappedFields(mappedFields)
                .build());
      }
    }
  }

  private static void addUsage(
      Map<RecordDefinitionKey, List<SourceUsage>> index,
      RecordDefinitionKey key,
      SourceUsage usage) {
    if (key == null || usage == null || Utils.isEmpty(key.getName())) {
      return;
    }
    index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(usage);
  }

  private static String resolveName(IVariables variables, String value) {
    if (variables != null && !Utils.isEmpty(value)) {
      return variables.resolve(value);
    }
    return value;
  }

  public static RecordDefinitionKey resolveKey(
      RecordDefinitionKey templateKey,
      String catalogConnection,
      IVariables variables,
      String defaultNamespace) {
    String namespace =
        templateKey != null && !Utils.isEmpty(templateKey.getNamespace())
            ? templateKey.getNamespace()
            : defaultNamespace;
    String name = templateKey != null ? templateKey.getName() : null;
    if (variables != null) {
      if (!Utils.isEmpty(namespace)) {
        namespace = variables.resolve(namespace);
      }
      if (!Utils.isEmpty(name)) {
        name = variables.resolve(name);
      }
    }
    return new RecordDefinitionKey(namespace, name);
  }
}
