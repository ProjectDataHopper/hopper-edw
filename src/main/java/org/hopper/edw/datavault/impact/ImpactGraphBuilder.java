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
package org.hopper.edw.datavault.impact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMapping;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2SatelliteConfig;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlRef;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlResolvedKind;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceType;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.resourcedefinition.SourceUsage;
import org.hopper.edw.datavault.resourcedefinition.SourceUsageIndexBuilder;
import org.hopper.edw.datavault.resourcedefinition.ValidationModels;

/**
 * Builds a directed impact graph from models loaded for a resource definition group.
 *
 * <p>High-confidence edges: source→DV mappings, DV parent links, BV SCD2 field maps, PIT
 * derivatives. Medium-confidence: BV SQL refs and DM SQL table-name scans.
 */
public final class ImpactGraphBuilder {

  private static final Pattern SQL_IDENTIFIER =
      Pattern.compile("(?i)(?<![\\w.])([A-Za-z_][A-Za-z0-9_]*)(?![\\w.])");

  private ImpactGraphBuilder() {}

  public static ImpactGraph build(ValidationModels models, IVariables variables) {
    if (models == null) {
      return ImpactGraph.empty();
    }
    Builder builder = new Builder(variables);
    Map<RecordDefinitionKey, List<SourceUsage>> usageIndex =
        SourceUsageIndexBuilder.build(models, variables);
    builder.addSourceUsages(usageIndex);

    for (ValidationModels.LoadedDataVaultModel loaded : models.dataVaultModels()) {
      builder.addDataVaultParents(loaded);
    }
    for (ValidationModels.LoadedBusinessVaultModel loaded : models.businessVaultModels()) {
      builder.addBusinessVault(loaded);
    }
    for (ValidationModels.LoadedDimensionalModel loaded : models.dimensionalModels()) {
      builder.addDimensional(loaded);
    }
    return builder.build();
  }

  private static final class Builder {
    private final IVariables variables;
    private final String sourcesNamespace;
    private final Map<String, ImpactNode> nodes = new LinkedHashMap<>();
    private final Map<String, List<ImpactEdge>> outgoing = new LinkedHashMap<>();

    /** physical/logical table name (lower) → DV/BV table nodes for SQL scans */
    private final Map<String, List<ImpactNode>> tableNameIndex = new LinkedHashMap<>();

    private Builder(IVariables variables) {
      this.variables = variables;
      this.sourcesNamespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    }

    void addSourceUsages(Map<RecordDefinitionKey, List<SourceUsage>> usageIndex) {
      if (usageIndex == null) {
        return;
      }
      for (Map.Entry<RecordDefinitionKey, List<SourceUsage>> entry : usageIndex.entrySet()) {
        RecordDefinitionKey key = entry.getKey();
        if (key == null || Utils.isEmpty(key.getName())) {
          continue;
        }
        String namespace =
            !Utils.isEmpty(key.getNamespace()) ? key.getNamespace() : sourcesNamespace;
        String sourceName = resolve(key.getName());
        ImpactNode sourceObject =
            addNode(
                new ImpactNode(
                    ImpactNodeKind.SOURCE_OBJECT,
                    null,
                    null,
                    null,
                    null,
                    null,
                    namespace,
                    sourceName));

        for (SourceUsage usage : entry.getValue()) {
          if (usage == null) {
            continue;
          }
          boolean dimensional =
              SourceUsageIndexBuilder.MODEL_TYPE_DIMENSIONAL.equals(usage.modelType());
          ImpactNode tableNode =
              addNode(
                  new ImpactNode(
                      dimensional ? ImpactNodeKind.DM_TABLE : ImpactNodeKind.DV_TABLE,
                      usage.modelType(),
                      usage.modelName(),
                      usage.modelFilename(),
                      usage.modelElementName(),
                      null,
                      null,
                      null));
          indexTableName(tableNode);

          addEdge(
              dimensional ? ImpactEdgeType.SOURCE_TO_DM_RECORD : ImpactEdgeType.SOURCE_TO_DV,
              sourceObject,
              tableNode);

          if (usage.mappedFields() == null) {
            continue;
          }
          for (String field : usage.mappedFields()) {
            if (Utils.isEmpty(field)) {
              continue;
            }
            String resolvedField = resolve(field);
            ImpactNode sourceField =
                addNode(
                    new ImpactNode(
                        ImpactNodeKind.SOURCE_FIELD,
                        null,
                        null,
                        null,
                        null,
                        resolvedField,
                        namespace,
                        sourceName));
            addEdge(ImpactEdgeType.SOURCE_TO_DV, sourceObject, sourceField);

            ImpactNode dvField =
                addNode(
                    new ImpactNode(
                        dimensional ? ImpactNodeKind.DM_TABLE : ImpactNodeKind.DV_FIELD,
                        usage.modelType(),
                        usage.modelName(),
                        usage.modelFilename(),
                        usage.modelElementName(),
                        resolvedField,
                        null,
                        null));
            addEdge(
                dimensional ? ImpactEdgeType.SOURCE_TO_DM_RECORD : ImpactEdgeType.SOURCE_TO_DV,
                sourceField,
                dvField);
            addEdge(
                dimensional ? ImpactEdgeType.SOURCE_TO_DM_RECORD : ImpactEdgeType.SOURCE_TO_DV,
                sourceField,
                tableNode);
          }
        }
      }
    }

    void addDataVaultParents(ValidationModels.LoadedDataVaultModel loaded) {
      if (loaded == null || loaded.model() == null) {
        return;
      }
      DataVaultModel model = loaded.model();
      for (IDvTable table : model.getTables()) {
        if (table instanceof DvSatellite satellite) {
          String hubName = satellite.getHubName();
          if (!Utils.isEmpty(hubName)) {
            ImpactNode satNode =
                addNode(
                    new ImpactNode(
                        ImpactNodeKind.DV_TABLE,
                        SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
                        model.getName(),
                        model.getFilename(),
                        satellite.getName(),
                        null,
                        null,
                        null));
            ImpactNode hubNode =
                addNode(
                    new ImpactNode(
                        ImpactNodeKind.DV_TABLE,
                        SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
                        model.getName(),
                        model.getFilename(),
                        resolve(hubName),
                        null,
                        null,
                        null));
            indexTableName(satNode);
            indexTableName(hubNode);
            // Downstream from sat toward parent hub for "sat field changed → hub grain" awareness.
            addEdge(ImpactEdgeType.DV_PARENT, satNode, hubNode);
          }
        } else if (table instanceof DvLink link) {
          ImpactNode linkNode =
              addNode(
                  new ImpactNode(
                      ImpactNodeKind.DV_TABLE,
                      SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
                      model.getName(),
                      model.getFilename(),
                      link.getName(),
                      null,
                      null,
                      null));
          indexTableName(linkNode);
          if (link.getHubNames() != null) {
            for (String hubName : link.getHubNames()) {
              if (Utils.isEmpty(hubName)) {
                continue;
              }
              ImpactNode hubNode =
                  addNode(
                      new ImpactNode(
                          ImpactNodeKind.DV_TABLE,
                          SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
                          model.getName(),
                          model.getFilename(),
                          resolve(hubName),
                          null,
                          null,
                          null));
              indexTableName(hubNode);
              addEdge(ImpactEdgeType.DV_PARENT, linkNode, hubNode);
            }
          }
        } else if (table instanceof DvHub hub) {
          ImpactNode hubNode =
              addNode(
                  new ImpactNode(
                      ImpactNodeKind.DV_TABLE,
                      SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
                      model.getName(),
                      model.getFilename(),
                      hub.getName(),
                      null,
                      null,
                      null));
          indexTableName(hubNode);
        }
      }
    }

    void addBusinessVault(ValidationModels.LoadedBusinessVaultModel loaded) {
      if (loaded == null || loaded.model() == null) {
        return;
      }
      BusinessVaultModel bvModel = loaded.model();
      DataVaultModel dvModel = loaded.dvModel();
      for (IBvTable table : bvModel.getTables()) {
        if (table instanceof BvScd2Table scd2) {
          addScd2(bvModel, dvModel, scd2);
        } else if (table instanceof BvPitTable pit) {
          addPit(bvModel, pit);
        } else if (table instanceof BvBusinessTable businessTable) {
          addBvSql(bvModel, businessTable);
        }
      }
    }

    private void addScd2(BusinessVaultModel bvModel, DataVaultModel dvModel, BvScd2Table scd2) {
      ImpactNode bvTable =
          addNode(
              new ImpactNode(
                  ImpactNodeKind.BV_TABLE,
                  SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT,
                  bvModel.getName(),
                  bvModel.getFilename(),
                  scd2.getName(),
                  null,
                  null,
                  null));
      indexTableName(bvTable);

      // Table-level: each contributing satellite → BV table
      if (scd2.getSatelliteConfigs() != null) {
        for (BvScd2SatelliteConfig config : scd2.getSatelliteConfigs()) {
          if (config == null || Utils.isEmpty(config.getSatelliteName())) {
            continue;
          }
          ImpactNode satNode =
              addNode(
                  new ImpactNode(
                      ImpactNodeKind.DV_TABLE,
                      SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
                      dvModel != null ? dvModel.getName() : null,
                      dvModel != null ? dvModel.getFilename() : null,
                      resolve(config.getSatelliteName()),
                      null,
                      null,
                      null));
          indexTableName(satNode);
          addEdge(ImpactEdgeType.DV_TO_BV_SCD2, satNode, bvTable);
        }
      }

      if (scd2.getFieldMappings() == null) {
        return;
      }
      for (BvScd2FieldMapping mapping : scd2.getFieldMappings()) {
        if (mapping == null
            || Utils.isEmpty(mapping.getSatelliteName())
            || Utils.isEmpty(mapping.getSourceFieldName())) {
          continue;
        }
        String satName = resolve(mapping.getSatelliteName());
        String sourceField = resolve(mapping.getSourceFieldName());
        String targetField =
            !Utils.isEmpty(mapping.getTargetFieldName())
                ? resolve(mapping.getTargetFieldName())
                : sourceField;

        ImpactNode dvField =
            addNode(
                new ImpactNode(
                    ImpactNodeKind.DV_FIELD,
                    SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
                    dvModel != null ? dvModel.getName() : null,
                    dvModel != null ? dvModel.getFilename() : null,
                    satName,
                    sourceField,
                    null,
                    null));
        ImpactNode bvField =
            addNode(
                new ImpactNode(
                    ImpactNodeKind.BV_FIELD,
                    SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT,
                    bvModel.getName(),
                    bvModel.getFilename(),
                    scd2.getName(),
                    targetField,
                    null,
                    null));
        addEdge(ImpactEdgeType.DV_TO_BV_SCD2, dvField, bvField);
        addEdge(ImpactEdgeType.DV_TO_BV_SCD2, dvField, bvTable);
      }
    }

    private void addPit(BusinessVaultModel bvModel, BvPitTable pit) {
      ImpactNode pitNode =
          addNode(
              new ImpactNode(
                  ImpactNodeKind.BV_TABLE,
                  SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT,
                  bvModel.getName(),
                  bvModel.getFilename(),
                  pit.getName(),
                  null,
                  null,
                  null));
      indexTableName(pitNode);
      if (pit.getDerivatives() == null) {
        return;
      }
      for (BvDerivativeRef derivative : pit.getDerivatives()) {
        if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
          continue;
        }
        ImpactNode dvNode =
            addNode(
                new ImpactNode(
                    ImpactNodeKind.DV_TABLE,
                    SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT,
                    null,
                    null,
                    resolve(derivative.getDvTableName()),
                    null,
                    null,
                    null));
        indexTableName(dvNode);
        addEdge(ImpactEdgeType.DV_TO_BV_PIT, dvNode, pitNode);
      }
    }

    private void addBvSql(BusinessVaultModel bvModel, BvBusinessTable businessTable) {
      ImpactNode bvNode =
          addNode(
              new ImpactNode(
                  ImpactNodeKind.BV_TABLE,
                  SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT,
                  bvModel.getName(),
                  bvModel.getFilename(),
                  businessTable.getName(),
                  null,
                  null,
                  null));
      indexTableName(bvNode);
      if (businessTable.getSqlRefs() == null) {
        return;
      }
      for (BvSqlRef ref : businessTable.getSqlRefs()) {
        if (ref == null) {
          continue;
        }
        String objectName =
            !Utils.isEmpty(ref.getResolvedTableName())
                ? ref.getResolvedTableName()
                : ref.getObjectName();
        if (Utils.isEmpty(objectName)) {
          continue;
        }
        ImpactNodeKind kind =
            ref.getResolvedKind() == BvSqlResolvedKind.BV_TABLE
                ? ImpactNodeKind.BV_TABLE
                : ImpactNodeKind.DV_TABLE;
        String modelType =
            kind == ImpactNodeKind.BV_TABLE
                ? SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT
                : SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT;
        ImpactNode upstream =
            addNode(
                new ImpactNode(
                    kind,
                    modelType,
                    null,
                    ref.getResolvedModelFilename(),
                    resolve(objectName),
                    null,
                    null,
                    null));
        indexTableName(upstream);
        addEdge(ImpactEdgeType.DV_TO_BV_SQL, upstream, bvNode);
      }
    }

    void addDimensional(ValidationModels.LoadedDimensionalModel loaded) {
      if (loaded == null || loaded.model() == null) {
        return;
      }
      DimensionalModel model = loaded.model();
      for (IDmTable table : model.getTables()) {
        if (table == null) {
          continue;
        }
        ImpactNode dmNode =
            addNode(
                new ImpactNode(
                    ImpactNodeKind.DM_TABLE,
                    SourceUsageIndexBuilder.MODEL_TYPE_DIMENSIONAL,
                    model.getName(),
                    model.getFilename(),
                    table.getName(),
                    null,
                    null,
                    null));
        indexTableName(dmNode);

        DmSourceConfiguration source = table.getSourceOrDefault();
        if (source == null || source.resolveSourceType() != DmSourceType.SQL) {
          continue;
        }
        String sql = resolve(source.getSourceSql());
        if (Utils.isEmpty(sql)) {
          continue;
        }
        Set<String> identifiers = extractIdentifiers(sql);
        for (String identifier : identifiers) {
          List<ImpactNode> matches = tableNameIndex.get(identifier.toLowerCase(Locale.ROOT));
          if (matches == null) {
            continue;
          }
          for (ImpactNode upstream : matches) {
            if (upstream.kind() == ImpactNodeKind.DV_TABLE
                || upstream.kind() == ImpactNodeKind.BV_TABLE) {
              addEdge(ImpactEdgeType.DV_BV_TO_DM_SQL, upstream, dmNode);
            }
          }
        }
      }
    }

    private void indexTableName(ImpactNode node) {
      if (node == null || Utils.isEmpty(node.elementName())) {
        return;
      }
      if (node.kind() != ImpactNodeKind.DV_TABLE
          && node.kind() != ImpactNodeKind.BV_TABLE
          && node.kind() != ImpactNodeKind.DM_TABLE) {
        return;
      }
      tableNameIndex
          .computeIfAbsent(
              node.elementName().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
          .add(node);
    }

    private ImpactNode addNode(ImpactNode node) {
      nodes.putIfAbsent(node.id(), node);
      return nodes.get(node.id());
    }

    private void addEdge(ImpactEdgeType type, ImpactNode from, ImpactNode to) {
      if (from == null || to == null || from.id().equals(to.id())) {
        return;
      }
      ImpactEdge edge = new ImpactEdge(type, from.id(), to.id());
      List<ImpactEdge> list = outgoing.computeIfAbsent(from.id(), ignored -> new ArrayList<>());
      for (ImpactEdge existing : list) {
        if (existing.type() == type && existing.toId().equals(to.id())) {
          return;
        }
      }
      list.add(edge);
    }

    private String resolve(String value) {
      if (variables != null && !Utils.isEmpty(value)) {
        return variables.resolve(value);
      }
      return value;
    }

    private static Set<String> extractIdentifiers(String sql) {
      Set<String> identifiers = new LinkedHashSet<>();
      Matcher matcher = SQL_IDENTIFIER.matcher(sql);
      while (matcher.find()) {
        String token = matcher.group(1);
        if (token != null && token.length() > 1) {
          identifiers.add(token);
        }
      }
      return identifiers;
    }

    ImpactGraph build() {
      return new ImpactGraph(nodes, outgoing);
    }
  }
}
