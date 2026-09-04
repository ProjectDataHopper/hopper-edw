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
package org.hopper.edw.datavault.lineage;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Calculation;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMapping;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMappingValidationSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlRef;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlResolvedKind;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlSource;
import org.hopper.edw.datavault.metadata.businessvault.BvTableType;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/**
 * Builds a {@link LineageSnapshot} from a Business Vault model (SCD2, PIT, and other BV tables).
 */
public final class BvModelLineageCollector {

  private BvModelLineageCollector() {}

  public static LineageSnapshot collect(BusinessVaultModel model, IVariables variables) {
    return collect(model, variables, null);
  }

  public static LineageSnapshot collect(
      BusinessVaultModel model, IVariables variables, DataVaultModel dataVaultModel) {
    if (model == null) {
      throw new IllegalArgumentException("Business Vault model is required");
    }

    LineageSnapshot snapshot = new LineageSnapshot();
    snapshot.setId(UUID.randomUUID().toString());
    snapshot.setCapturedAt(new Date());
    snapshot.setProjectKey(DvCatalogNamespaces.resolveProjectKey(variables));
    snapshot.setModelLayer(LineageLayer.BV);
    snapshot.setModelName(model.getName());
    snapshot.setModelFilename(
        CatalogModelRegistrySupport.portableModelPath(model.getFilename(), variables));

    BusinessVaultConfiguration config = model.getConfigurationOrDefault();
    String targetDb = config != null ? config.getTargetDatabase() : null;
    DataVaultModel dvModel =
        dataVaultModel != null ? dataVaultModel : resolveDataVaultModel(model, variables);

    for (IBvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      TableLineage tableLineage;
      if (table instanceof BvScd2Table scd2) {
        tableLineage = collectScd2(scd2, model, config, variables, targetDb, dvModel);
      } else if (table instanceof BvPitTable pit) {
        tableLineage = collectPit(pit, model, config, variables, targetDb);
      } else if (table instanceof BvBusinessTable business) {
        tableLineage = collectBusinessTable(business, model, variables, targetDb);
      } else {
        tableLineage = collectGeneric(table, model, variables, targetDb);
      }
      if (tableLineage != null) {
        snapshot.addTable(tableLineage);
      }
    }
    return snapshot;
  }

  /**
   * Field lineage for one SCD2 table (dialog Lineage tab). Uses {@code dataVaultModel} when present
   * so hub business keys and other loaded columns can be named.
   */
  public static TableLineage collectScd2Table(
      BvScd2Table table,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    if (table == null || model == null) {
      return null;
    }
    BusinessVaultConfiguration config = model.getConfigurationOrDefault();
    String targetDb = config != null ? config.getTargetDatabase() : null;
    DataVaultModel dvModel =
        dataVaultModel != null ? dataVaultModel : resolveDataVaultModel(model, variables);
    return collectScd2(table, model, config, variables, targetDb, dvModel);
  }

  private static TableLineage collectScd2(
      BvScd2Table table,
      BusinessVaultModel model,
      BusinessVaultConfiguration config,
      IVariables variables,
      String targetDb,
      DataVaultModel dvModel) {
    TableLineage lineage = baseTable(table, model, variables, targetDb, BvTableType.SCD2.name());
    addNamingReasons(lineage, table);

    for (BvDerivativeRef ref : table.getDerivatives()) {
      if (ref == null || Utils.isEmpty(ref.getDvTableName())) {
        continue;
      }
      String dvName = resolve(ref.getDvTableName(), variables);
      TableSourceRef source =
          new TableSourceRef(TableSourceKind.DV_TABLE, dvName, TableSourceRole.SCD2_INPUT);
      lineage.addSource(source);
      lineage.addReason(LineageReasonFactory.feedAttached(dvName, "SCD2 satellite input"));
    }

    if (table.getFieldMappings() != null && !table.getFieldMappings().isEmpty()) {
      for (BvScd2FieldMapping mapping : table.getFieldMappings()) {
        if (mapping == null
            || Utils.isEmpty(mapping.getTargetFieldName())
            || !mapping.isIncludeInTarget()) {
          continue;
        }
        String target = resolve(mapping.getTargetFieldName(), variables);
        String sat = resolve(mapping.getSatelliteName(), variables);
        String sourceField = resolve(mapping.getSourceFieldName(), variables);
        if (Utils.isEmpty(sourceField)) {
          sourceField = target;
        }
        FieldLineage field = new FieldLineage(target);
        field.setTechnical(false);
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_TABLE);
        contribution.setSourceName(sat);
        contribution.setSourceFieldName(sourceField);
        contribution.setTransform(
            target.equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
        contribution.addReason(LineageReasonFactory.bvScd2FieldMap(target, sat, sourceField));
        field.addContribution(contribution);
        lineage.addField(field);
      }
    }

    addScd2HubAndHashFields(lineage, table, dvModel, variables);

    if (table.getCalculations() != null) {
      for (BvScd2Calculation calculation : table.getCalculations()) {
        if (calculation == null || Utils.isEmpty(calculation.getTargetFieldName())) {
          continue;
        }
        String target = resolve(calculation.getTargetFieldName(), variables);
        if (lineage.findField(target).isPresent()) {
          continue;
        }
        FieldLineage field = new FieldLineage(target);
        field.setTechnical(false);
        FieldContribution contribution = new FieldContribution();
        contribution.setTransform(FieldTransform.DERIVED);
        contribution.addReason(
            LineageReasonFactory.bvScd2Calculation(
                target, resolve(calculation.getExpression(), variables)));
        field.addContribution(contribution);
        lineage.addField(field);
      }
    }

    // Technical SCD2 columns from config / table overrides
    String validFrom =
        firstNonEmpty(
            resolve(table.getValidFromField(), variables),
            config != null ? resolve(config.getValidFromField(), variables) : null,
            "valid_from");
    addTechnicalConfigField(lineage, validFrom, "validFromField");

    String validTo =
        firstNonEmpty(
            resolve(table.getValidToField(), variables),
            config != null ? resolve(config.getValidToField(), variables) : null,
            "valid_to");
    addTechnicalConfigField(lineage, validTo, "validToField");

    DataVaultConfiguration dvConfig = dvModel != null ? dvModel.getConfigurationOrDefault() : null;
    if (variables != null) {
      addTechnicalConfigField(
          lineage,
          BvScd2PipelineSupport.resolveFunctionalTimestampField(table, config, dvConfig, variables),
          "functionalTimestampField");
      addTechnicalConfigField(
          lineage,
          BvScd2PipelineSupport.resolveRecordSourceField(dvConfig, variables),
          "recordSourceField");
    }

    if (table.isIncludeHashKey()) {
      lineage.addReason(
          LineageReasonFactory.standardColumn("parent_hash_key", "includeHashKey", "true"));
    }
    if (table.isIncludeHubBusinessKeys()) {
      lineage.addReason(
          LineageReasonFactory.standardColumn(
              "hub_business_keys", "includeHubBusinessKeys", "true"));
    }

    addRemainingLoadedFields(lineage, table, config, dvModel, model, variables);
    return lineage;
  }

  private static void addScd2HubAndHashFields(
      TableLineage lineage, BvScd2Table table, DataVaultModel dvModel, IVariables variables) {
    if (table == null || dvModel == null) {
      return;
    }
    List<DvSatellite> satellites =
        BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(table, dvModel);
    DvHub hub = BvScd2FieldMappingValidationSupport.resolveSharedParentHub(satellites, dvModel);
    if (hub == null) {
      return;
    }
    String hubName = resolve(hub.getName(), variables);
    if (table.isIncludeHashKey()) {
      String hashField = resolve(hub.getHashKeyFieldName(), variables);
      if (!Utils.isEmpty(hashField) && lineage.findField(hashField).isEmpty()) {
        FieldLineage field = new FieldLineage(hashField);
        field.setTechnical(true);
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_TABLE);
        contribution.setSourceName(hubName);
        contribution.setSourceFieldName(hashField);
        contribution.setTransform(FieldTransform.IDENTITY);
        contribution.addReason(LineageReasonFactory.parentHashKey(hashField, hubName, hashField));
        field.addContribution(contribution);
        lineage.addField(field);
      }
    }
    if (!table.isIncludeHubBusinessKeys()) {
      return;
    }
    lineage.addSource(
        new TableSourceRef(TableSourceKind.DV_TABLE, hubName, TableSourceRole.PARENT_HUB));
    if (!table.isLoadHubBusinessKeys() || hub.getDistinctBusinessKeys() == null) {
      return;
    }
    for (BusinessKey businessKey : hub.getDistinctBusinessKeys()) {
      if (businessKey == null || Utils.isEmpty(businessKey.getName())) {
        continue;
      }
      String bkName = resolve(businessKey.getName(), variables);
      if (Utils.isEmpty(bkName) || lineage.findField(bkName).isPresent()) {
        continue;
      }
      FieldLineage field = new FieldLineage(bkName);
      field.setTechnical(false);
      FieldContribution contribution = new FieldContribution();
      contribution.setSourceKind(TableSourceKind.DV_TABLE);
      contribution.setSourceName(hubName);
      contribution.setSourceFieldName(bkName);
      contribution.setTransform(FieldTransform.IDENTITY);
      contribution.addReason(LineageReasonFactory.bvScd2HubBusinessKey(bkName, hubName));
      field.addContribution(contribution);
      lineage.addField(field);
    }
  }

  private static void addRemainingLoadedFields(
      TableLineage lineage,
      BvScd2Table table,
      BusinessVaultConfiguration config,
      DataVaultModel dvModel,
      BusinessVaultModel bvModel,
      IVariables variables) {
    if (lineage == null || table == null || dvModel == null) {
      return;
    }
    try {
      IRowMeta layout =
          BvScd2PipelineSupport.buildTargetTableLayout(table, config, dvModel, bvModel, variables);
      if (layout == null) {
        return;
      }
      String passthroughSource = firstSatelliteName(table, variables);
      for (IValueMeta valueMeta : layout.getValueMetaList()) {
        if (valueMeta == null || Utils.isEmpty(valueMeta.getName())) {
          continue;
        }
        String fieldName = valueMeta.getName();
        if (lineage.findField(fieldName).isPresent()) {
          continue;
        }
        FieldLineage field = new FieldLineage(fieldName);
        field.setTechnical(false);
        FieldContribution contribution = new FieldContribution();
        contribution.setSourceKind(TableSourceKind.DV_TABLE);
        contribution.setSourceName(passthroughSource);
        contribution.setSourceFieldName(fieldName);
        contribution.setTransform(FieldTransform.IDENTITY);
        contribution.addReason(LineageReasonFactory.bvPassthrough(fieldName, passthroughSource));
        field.addContribution(contribution);
        lineage.addField(field);
      }
    } catch (Exception ignored) {
      // Layout needs a complete DV model; mapping/calculation rows already recorded.
    }
  }

  private static String firstSatelliteName(BvScd2Table table, IVariables variables) {
    if (table == null || table.getDerivatives() == null) {
      return "";
    }
    for (BvDerivativeRef ref : table.getDerivatives()) {
      if (ref != null && !Utils.isEmpty(ref.getDvTableName())) {
        return resolve(ref.getDvTableName(), variables);
      }
    }
    return "";
  }

  private static DataVaultModel resolveDataVaultModel(
      BusinessVaultModel model, IVariables variables) {
    try {
      return BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(model, variables, null);
    } catch (Exception e) {
      return null;
    }
  }

  private static TableLineage collectPit(
      BvPitTable table,
      BusinessVaultModel model,
      BusinessVaultConfiguration config,
      IVariables variables,
      String targetDb) {
    TableLineage lineage = baseTable(table, model, variables, targetDb, BvTableType.PIT.name());
    addNamingReasons(lineage, table);

    for (BvDerivativeRef ref : table.getDerivatives()) {
      if (ref == null || Utils.isEmpty(ref.getDvTableName())) {
        continue;
      }
      String dvName = resolve(ref.getDvTableName(), variables);
      lineage.addSource(
          new TableSourceRef(TableSourceKind.DV_TABLE, dvName, TableSourceRole.SCD2_INPUT));
      lineage.addReason(LineageReasonFactory.feedAttached(dvName, "PIT derivative"));
    }

    String snapshotDate =
        firstNonEmpty(resolve(table.getSnapshotDateField(), variables), "snapshot_date");
    addTechnicalConfigField(lineage, snapshotDate, "snapshotDateField");
    return lineage;
  }

  private static TableLineage collectBusinessTable(
      BvBusinessTable table, BusinessVaultModel model, IVariables variables, String targetDb) {
    TableLineage lineage = collectGeneric(table, model, variables, targetDb);
    if (table.getSqlRefs() != null) {
      for (BvSqlRef ref : table.getSqlRefs()) {
        if (ref == null || Utils.isEmpty(ref.getObjectName())) {
          continue;
        }
        String name =
            !Utils.isEmpty(ref.getResolvedTableName())
                ? ref.getResolvedTableName()
                : ref.getObjectName();
        TableSourceKind kind =
            ref.getResolvedKind() == BvSqlResolvedKind.DV_TABLE
                ? TableSourceKind.DV_TABLE
                : TableSourceKind.BV_TABLE;
        lineage.addSource(new TableSourceRef(kind, name, TableSourceRole.OTHER));
      }
    }
    if (table.getSources() != null) {
      for (BvSqlSource source : table.getSources()) {
        if (source == null || Utils.isEmpty(source.getTableName())) {
          continue;
        }
        String label =
            !Utils.isEmpty(source.getSourceName())
                ? source.getSourceName() + "." + source.getTableName()
                : source.getTableName();
        lineage.addSource(
            new TableSourceRef(TableSourceKind.DV_SOURCE, label, TableSourceRole.OTHER));
      }
    }
    return lineage;
  }

  private static TableLineage collectGeneric(
      IBvTable table, BusinessVaultModel model, IVariables variables, String targetDb) {
    String type = table.getTableType() != null ? table.getTableType().name() : "BV";
    TableLineage lineage = baseTable(table, model, variables, targetDb, type);
    addNamingReasons(lineage, table);
    for (BvDerivativeRef ref : table.getDerivatives()) {
      if (ref == null || Utils.isEmpty(ref.getDvTableName())) {
        continue;
      }
      lineage.addSource(
          new TableSourceRef(
              TableSourceKind.DV_TABLE, ref.getDvTableName(), TableSourceRole.OTHER));
    }
    return lineage;
  }

  private static TableLineage baseTable(
      IBvTable table,
      BusinessVaultModel model,
      IVariables variables,
      String targetDb,
      String tableType) {
    TableLineage lineage = new TableLineage();
    lineage.setLayer(LineageLayer.BV);
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

  private static void addNamingReasons(TableLineage lineage, IBvTable table) {
    lineage.addReason(
        LineageReasonFactory.userExplicitName(table.getName(), lineage.getPhysicalTableName()));
    if (table.getTableType() != null) {
      lineage.addReason(
          LineageReasonFactory.tableTypeRole(table.getTableType().name(), table.getName()));
    }
  }

  private static void addTechnicalConfigField(
      TableLineage lineage, String fieldName, String configKey) {
    if (Utils.isEmpty(fieldName) || lineage.findField(fieldName).isPresent()) {
      return;
    }
    FieldLineage field = new FieldLineage(fieldName);
    field.setTechnical(true);
    FieldContribution contribution = new FieldContribution();
    contribution.setSourceKind(TableSourceKind.CONFIG);
    contribution.setSourceName("BusinessVaultConfiguration");
    contribution.setTransform(FieldTransform.DERIVED);
    contribution.addReason(LineageReasonFactory.standardColumn(fieldName, configKey, fieldName));
    field.addContribution(contribution);
    lineage.addField(field);
  }

  private static String firstNonEmpty(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (!Utils.isEmpty(v)) {
        return v;
      }
    }
    return null;
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
