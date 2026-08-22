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
package org.hopper.edw.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvPitLayoutSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvPitTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvTableBase;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBridge;
import org.hopper.edw.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmLayoutSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmTableBase;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Resolves optional primary-key and foreign-key columns for Data Vault, Business Vault, and
 * dimensional CREATE TABLE DDL.
 */
public final class DvConstraintDdlSupport {

  private DvConstraintDdlSupport() {}

  public static boolean wantsPrimaryKeyDdl(
      boolean generatePrimaryKeys, boolean generateForeignKeys, boolean fkParentTable) {
    return generatePrimaryKeys || (generateForeignKeys && fkParentTable);
  }

  public static boolean isDvFkParentTable(DvTableBase table) {
    return table instanceof DvHub || table instanceof DvLink;
  }

  public static String physicalTableName(DvTableBase table) {
    if (table == null) {
      return null;
    }
    return !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
  }

  public static String physicalTableName(BvTableBase table) {
    if (table == null) {
      return null;
    }
    return !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
  }

  public static String physicalTableName(DmTableBase table) {
    if (table == null) {
      return null;
    }
    return !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
  }

  // ---------------------------------------------------------------------------
  // Data Vault
  // ---------------------------------------------------------------------------

  public static List<String> resolveDvPrimaryKeyColumns(
      DvTableBase table,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      IRowMeta layout,
      boolean statusTrackingSatellite)
      throws HopException {
    if (table == null || config == null) {
      return List.of();
    }
    boolean wantPk =
        wantsPrimaryKeyDdl(
            config.isGeneratePrimaryKeys(),
            config.isGenerateForeignKeys(),
            isDvFkParentTable(table));
    if (!wantPk) {
      return List.of();
    }

    if (table instanceof DvHub hub) {
      return nonEmptyList(resolveHubHashKeyColumn(hub, variables));
    }
    if (table instanceof DvLink link) {
      return nonEmptyList(resolveLinkHashKeyColumn(link, variables));
    }
    if (table instanceof DvSatellite satellite) {
      return resolveSatellitePrimaryKeyColumns(satellite, model, config, variables, layout);
    }
    if (table instanceof DvReferenceTable reference) {
      return resolveReferencePrimaryKeyColumns(reference, variables);
    }
    return List.of();
  }

  private static List<String> resolveReferencePrimaryKeyColumns(
      DvReferenceTable reference, IVariables variables) {
    List<String> columns = new ArrayList<>();
    if (reference == null || reference.getNaturalKeys() == null) {
      return columns;
    }
    for (BusinessKey key : reference.getNaturalKeys()) {
      if (key == null || Utils.isEmpty(key.getName())) {
        continue;
      }
      String name = variables != null ? variables.resolve(key.getName()) : key.getName();
      addIfPresent(columns, name);
    }
    return columns;
  }

  public static List<ForeignKeySpec> resolveDvForeignKeys(
      DvTableBase table,
      DataVaultModel model,
      DataVaultConfiguration config,
      DatabaseMeta targetDatabaseMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean statusTrackingSatellite)
      throws HopException {
    if (table == null
        || config == null
        || !config.isGenerateForeignKeys()
        || !DvDdlSupport.supportsForeignKeyConstraints(targetDatabaseMeta)) {
      return List.of();
    }

    if (table instanceof DvLink link) {
      return resolveLinkForeignKeys(link, model, variables, metadataProvider);
    }
    if (table instanceof DvSatellite satellite) {
      return resolveSatelliteForeignKeys(
          satellite, model, variables, metadataProvider, statusTrackingSatellite);
    }
    return List.of();
  }

  private static List<String> resolveSatellitePrimaryKeyColumns(
      DvSatellite satellite,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      IRowMeta layout)
      throws HopException {
    List<String> columns = new ArrayList<>();
    String parentHash = resolveSatelliteParentHashColumn(satellite, model, variables);
    addIfPresent(columns, parentHash);

    if (satellite.hasDrivingKey()) {
      String drivingKey =
          variables != null
              ? variables.resolve(satellite.getDrivingKey())
              : satellite.getDrivingKey();
      addIfPresent(columns, drivingKey);
    }

    String loadDateField = config != null ? config.getLoadDateField() : null;
    if (Utils.isEmpty(loadDateField)) {
      loadDateField = "LOAD_DATE";
    }
    if (variables != null) {
      loadDateField = variables.resolve(loadDateField);
    }
    addIfPresent(columns, loadDateField);

    if (layout != null && !layout.isEmpty() && !columns.isEmpty()) {
      List<String> ordered = new ArrayList<>();
      for (int i = 0; i < layout.size(); i++) {
        String name = layout.getValueMeta(i).getName();
        if (columns.stream().anyMatch(c -> c.equalsIgnoreCase(name))) {
          ordered.add(name);
        }
      }
      if (ordered.size() == columns.size()) {
        return ordered;
      }
    }
    return columns;
  }

  private static List<ForeignKeySpec> resolveLinkForeignKeys(
      DvLink link,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<ForeignKeySpec> fks = new ArrayList<>();
    if (link == null || model == null || Utils.isEmpty(link.getHubNames())) {
      return fks;
    }
    String childTable = physicalTableName(link);
    int roleIndex = 0;
    for (String hubName : link.getHubNames()) {
      DvHub hub = model.findHub(hubName, variables, metadataProvider);
      if (hub == null) {
        continue;
      }
      String parentHash = resolveHubHashKeyColumn(hub, variables);
      String childHash =
          DvTableResolutionSupport.resolveParticipatingHubHashColumn(
              model, hubName, variables, metadataProvider);
      if (Utils.isEmpty(childHash)) {
        childHash = parentHash;
      } else if (variables != null) {
        childHash = variables.resolve(childHash);
      }
      String parentTable = physicalTableName(hub);
      if (Utils.isEmpty(childHash) || Utils.isEmpty(parentHash) || Utils.isEmpty(parentTable)) {
        continue;
      }
      // Distinct constraint names when the same parent hub is referenced by multiple role columns.
      String suffix =
          childHash.equals(parentHash) ? parentTable : parentTable + "_" + (++roleIndex);
      fks.add(
          new ForeignKeySpec(
              constraintName("fk", childTable, suffix),
              List.of(childHash),
              parentTable,
              List.of(parentHash)));
    }
    return fks;
  }

  private static List<ForeignKeySpec> resolveSatelliteForeignKeys(
      DvSatellite satellite,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean statusTrackingSatellite)
      throws HopException {
    List<ForeignKeySpec> fks = new ArrayList<>();
    if (satellite == null || model == null) {
      return fks;
    }

    String childTable =
        statusTrackingSatellite
            ? satellite.resolveStatusTableName(variables, model)
            : physicalTableName(satellite);
    String parentHash = resolveSatelliteParentHashColumn(satellite, model, variables);
    if (Utils.isEmpty(parentHash)) {
      return fks;
    }

    String parentTable = null;
    if (!Utils.isEmpty(satellite.getHubName())) {
      DvHub hub = model.findHub(satellite.getHubName(), variables, metadataProvider);
      parentTable = physicalTableName(hub);
    } else if (!Utils.isEmpty(satellite.getLinkName())) {
      DvLink link = model.findLink(satellite.getLinkName(), variables, metadataProvider);
      parentTable = physicalTableName(link);
    }
    if (Utils.isEmpty(parentTable) || Utils.isEmpty(childTable)) {
      return fks;
    }

    fks.add(
        new ForeignKeySpec(
            constraintName("fk", childTable, parentTable),
            List.of(parentHash),
            parentTable,
            List.of(parentHash)));
    return fks;
  }

  public static String resolveHubHashKeyColumn(DvHub hub, IVariables variables) {
    if (hub == null) {
      return null;
    }
    String hashKeyName = hub.getHashKeyFieldName();
    if (!Utils.isEmpty(hashKeyName)) {
      return variables != null ? variables.resolve(hashKeyName) : hashKeyName;
    }
    if (!Utils.isEmpty(hub.getBusinessKeys())) {
      String bkName = hub.getBusinessKeys().get(0).getName();
      if (variables != null) {
        bkName = variables.resolve(bkName);
      }
      return bkName + "_hk";
    }
    String name = hub.getName();
    if (variables != null) {
      name = variables.resolve(name);
    }
    return Utils.isEmpty(name) ? null : name + "_hk";
  }

  public static String resolveLinkHashKeyColumn(DvLink link, IVariables variables) {
    if (link == null) {
      return null;
    }
    String linkHashName = link.resolveLinkHashKeyFieldName(variables);
    return Utils.isEmpty(linkHashName) ? null : linkHashName;
  }

  private static String resolveSatelliteParentHashColumn(
      DvSatellite satellite, DataVaultModel model, IVariables variables) throws HopException {
    if (satellite == null || model == null) {
      return null;
    }
    if (!Utils.isEmpty(satellite.getHubName())) {
      DvHub hub = model.findHub(satellite.getHubName(), variables, null);
      return resolveHubHashKeyColumn(hub, variables);
    }
    if (!Utils.isEmpty(satellite.getLinkName())) {
      DvLink link = model.findLink(satellite.getLinkName(), variables, null);
      return resolveLinkHashKeyColumn(link, variables);
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // Business Vault
  // ---------------------------------------------------------------------------

  public static List<String> resolveBvPrimaryKeyColumns(
      BvTableBase table,
      BusinessVaultModel model,
      BusinessVaultConfiguration config,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IRowMeta layout)
      throws HopException {
    if (table == null || config == null || !config.isGeneratePrimaryKeys()) {
      return List.of();
    }

    if (table instanceof BvScd2Table scd2) {
      return resolveScd2PrimaryKeyColumns(scd2, config, dataVaultModel, variables, layout);
    }
    if (table instanceof BvPitTable pit) {
      return resolvePitPrimaryKeyColumns(pit, dataVaultModel, variables, layout);
    }
    if (table instanceof BvBusinessTable) {
      return List.of();
    }
    return List.of();
  }

  public static List<ForeignKeySpec> resolveBvForeignKeys(
      BvTableBase table,
      BusinessVaultModel model,
      BusinessVaultConfiguration config,
      DataVaultModel dataVaultModel,
      DatabaseMeta bvTargetDatabaseMeta,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    if (table == null
        || config == null
        || dataVaultModel == null
        || !config.isGenerateForeignKeys()
        || !DvDdlSupport.supportsForeignKeyConstraints(bvTargetDatabaseMeta)) {
      return List.of();
    }

    if (!samePhysicalDatabase(bvTargetDatabaseMeta, dataVaultModel, metadataProvider, variables)) {
      return List.of();
    }

    if (table instanceof BvScd2Table scd2) {
      return resolveScd2ForeignKeys(scd2, dataVaultModel, variables);
    }
    if (table instanceof BvPitTable pit) {
      return resolvePitForeignKeys(pit, dataVaultModel, variables);
    }
    return List.of();
  }

  private static List<String> resolveScd2PrimaryKeyColumns(
      BvScd2Table scd2,
      BusinessVaultConfiguration config,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IRowMeta layout)
      throws HopException {
    List<String> columns = new ArrayList<>();
    String validFrom = BvScd2PipelineSupport.resolveValidFromField(scd2, config, variables);

    if (scd2.isIncludeHashKey() && dataVaultModel != null) {
      DvSatellite sat = resolveFirstSatellite(scd2, dataVaultModel);
      if (sat != null) {
        addIfPresent(columns, resolveSatelliteParentHashColumn(sat, dataVaultModel, variables));
      }
    }

    if (dataVaultModel != null) {
      List<DvSatellite> satellites =
          BvScd2PipelineSupport.resolveSourceSatellites(scd2, dataVaultModel);
      for (DvSatellite satellite : satellites) {
        if (satellite != null && satellite.hasDrivingKey()) {
          String drivingKey =
              variables != null
                  ? variables.resolve(satellite.getDrivingKey())
                  : satellite.getDrivingKey();
          addIfPresent(columns, drivingKey);
        }
      }
    }

    addIfPresent(columns, validFrom);

    if (layout != null && !layout.isEmpty() && !columns.isEmpty()) {
      List<String> ordered = new ArrayList<>();
      for (int i = 0; i < layout.size(); i++) {
        String name = layout.getValueMeta(i).getName();
        if (columns.stream().anyMatch(c -> c.equalsIgnoreCase(name))) {
          ordered.add(name);
        }
      }
      if (!ordered.isEmpty()) {
        return ordered;
      }
    }
    return columns;
  }

  private static List<String> resolvePitPrimaryKeyColumns(
      BvPitTable pit, DataVaultModel dataVaultModel, IVariables variables, IRowMeta layout) {
    List<String> columns = new ArrayList<>();
    if (layout != null && layout.size() >= 2) {
      columns.add(layout.getValueMeta(0).getName());
      columns.add(layout.getValueMeta(1).getName());
      return columns;
    }
    if (dataVaultModel != null && pit != null) {
      try {
        IRowMeta built = BvPitLayoutSupport.buildTargetTableLayout(pit, dataVaultModel, variables);
        if (built != null && built.size() >= 2) {
          columns.add(built.getValueMeta(0).getName());
          columns.add(built.getValueMeta(1).getName());
        }
      } catch (Exception ignored) {
        // leave empty
      }
    }
    return columns;
  }

  private static List<ForeignKeySpec> resolveScd2ForeignKeys(
      BvScd2Table scd2, DataVaultModel dataVaultModel, IVariables variables) throws HopException {
    List<ForeignKeySpec> fks = new ArrayList<>();
    if (!scd2.isIncludeHashKey()) {
      return fks;
    }
    DvSatellite satellite = resolveFirstSatellite(scd2, dataVaultModel);
    if (satellite == null) {
      return fks;
    }
    String childTable = physicalTableName(scd2);
    String hashKey = resolveSatelliteParentHashColumn(satellite, dataVaultModel, variables);
    String parentTable = null;
    if (!Utils.isEmpty(satellite.getHubName())) {
      DvHub hub = dataVaultModel.findHub(satellite.getHubName(), variables, null);
      parentTable = physicalTableName(hub);
    } else if (!Utils.isEmpty(satellite.getLinkName())) {
      DvLink link = dataVaultModel.findLink(satellite.getLinkName(), variables, null);
      parentTable = physicalTableName(link);
    }
    if (Utils.isEmpty(hashKey) || Utils.isEmpty(parentTable) || Utils.isEmpty(childTable)) {
      return fks;
    }
    fks.add(
        new ForeignKeySpec(
            constraintName("fk", childTable, parentTable),
            List.of(hashKey),
            parentTable,
            List.of(hashKey)));
    return fks;
  }

  private static List<ForeignKeySpec> resolvePitForeignKeys(
      BvPitTable pit, DataVaultModel dataVaultModel, IVariables variables) throws HopException {
    List<ForeignKeySpec> fks = new ArrayList<>();
    DvHub hub = BvPitLayoutSupport.resolveHubDerivative(pit, dataVaultModel);
    if (hub == null) {
      return fks;
    }
    String childTable = physicalTableName(pit);
    String parentTable = physicalTableName(hub);
    String hashKey = resolveHubHashKeyColumn(hub, variables);
    if (Utils.isEmpty(hashKey) || Utils.isEmpty(parentTable) || Utils.isEmpty(childTable)) {
      return fks;
    }
    fks.add(
        new ForeignKeySpec(
            constraintName("fk", childTable, parentTable),
            List.of(hashKey),
            parentTable,
            List.of(hashKey)));
    return fks;
  }

  private static DvSatellite resolveFirstSatellite(BvScd2Table scd2, DataVaultModel dataVaultModel)
      throws HopException {
    if (scd2 == null || dataVaultModel == null) {
      return null;
    }
    List<DvSatellite> satellites =
        BvScd2PipelineSupport.resolveSourceSatellites(scd2, dataVaultModel);
    return satellites.isEmpty() ? null : satellites.get(0);
  }

  private static boolean samePhysicalDatabase(
      DatabaseMeta bvTarget,
      DataVaultModel dataVaultModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (bvTarget == null || dataVaultModel == null || metadataProvider == null) {
      return false;
    }
    try {
      DataVaultConfiguration dvConfig = dataVaultModel.getConfigurationOrDefault();
      DatabaseMeta dvTarget = DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);
      if (dvTarget == null) {
        return false;
      }
      String bvName =
          variables != null ? variables.resolve(bvTarget.getName()) : bvTarget.getName();
      String dvName =
          variables != null ? variables.resolve(dvTarget.getName()) : dvTarget.getName();
      return bvName != null && bvName.equalsIgnoreCase(dvName);
    } catch (Exception e) {
      return false;
    }
  }

  // ---------------------------------------------------------------------------
  // Dimensional
  // ---------------------------------------------------------------------------

  public static List<String> resolveDmPrimaryKeyColumns(
      DmTableBase table,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (table == null || config == null) {
      return List.of();
    }
    boolean fkParent = table instanceof DmDimension || table instanceof DmJunkDimension;
    boolean wantPk =
        wantsPrimaryKeyDdl(
            config.isGeneratePrimaryKeys(), config.isGenerateForeignKeys(), fkParent);
    if (!wantPk) {
      return List.of();
    }

    if (table instanceof DmDimension dimension) {
      String sk = DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables);
      return nonEmptyList(sk);
    }
    if (table instanceof DmJunkDimension junk) {
      String sk = resolveJunkSurrogateKey(junk, config, variables);
      return nonEmptyList(sk);
    }
    if (table instanceof DmBridge bridge) {
      if (!config.isGeneratePrimaryKeys()) {
        return List.of();
      }
      return resolveBridgePrimaryKeyColumns(bridge, model, config, variables, metadataProvider);
    }
    // Facts: no PK in v1
    return List.of();
  }

  public static List<ForeignKeySpec> resolveDmForeignKeys(
      DmTableBase table,
      DimensionalModel model,
      DimensionalConfiguration config,
      DatabaseMeta targetDatabaseMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (table == null
        || config == null
        || model == null
        || !config.isGenerateForeignKeys()
        || !DvDdlSupport.supportsForeignKeyConstraints(targetDatabaseMeta)) {
      return List.of();
    }

    if (table instanceof IDmFactLikeTable fact) {
      return resolveFactForeignKeys(fact, model, config, variables, metadataProvider);
    }
    if (table instanceof DmBridge bridge) {
      return resolveBridgeForeignKeys(bridge, model, config, variables, metadataProvider);
    }
    if (table instanceof DmDimension dimension) {
      return resolveDimensionOutriggerForeignKeys(
          dimension, model, config, variables, metadataProvider);
    }
    return List.of();
  }

  private static List<String> resolveBridgePrimaryKeyColumns(
      DmBridge bridge,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<String> columns = new ArrayList<>();
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      if (ref == null) {
        continue;
      }
      String fk =
          variables != null
              ? variables.resolve(ref.getForeignKeyColumn())
              : ref.getForeignKeyColumn();
      if (Utils.isEmpty(fk) && model != null) {
        DmDimension dim =
            DmDimensionResolutionSupport.resolveDimension(
                model, ref.getDimensionTableName(), variables, metadataProvider);
        if (dim != null) {
          DmFactDimensionRole role =
              new DmFactDimensionRole(ref.getDimensionTableName(), ref.getForeignKeyColumn());
          fk = DmLayoutSupport.defaultFactForeignKeyColumn(dim, role, config, variables);
        }
      }
      addIfPresent(columns, fk);
    }
    return columns;
  }

  private static List<ForeignKeySpec> resolveFactForeignKeys(
      IDmFactLikeTable fact,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<ForeignKeySpec> fks = new ArrayList<>();
    if (!(fact instanceof DmTableBase tableBase)) {
      return fks;
    }
    String childTable = physicalTableName(tableBase);
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      addDimensionRoleFk(fks, childTable, role, model, config, variables, metadataProvider);
    }
    for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      String fk =
          variables != null
              ? variables.resolve(role.getForeignKeyColumn())
              : role.getForeignKeyColumn();
      DmJunkDimension junk =
          resolveJunkDimension(model, role.getJunkDimensionTableName(), variables);
      if (junk == null || Utils.isEmpty(fk)) {
        continue;
      }
      String parentTable = physicalTableName(junk);
      String parentKey = resolveJunkSurrogateKey(junk, config, variables);
      if (Utils.isEmpty(parentTable) || Utils.isEmpty(parentKey)) {
        continue;
      }
      fks.add(
          new ForeignKeySpec(
              constraintName("fk", childTable, parentTable),
              List.of(fk),
              parentTable,
              List.of(parentKey)));
    }
    return fks;
  }

  private static List<ForeignKeySpec> resolveBridgeForeignKeys(
      DmBridge bridge,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<ForeignKeySpec> fks = new ArrayList<>();
    String childTable = physicalTableName(bridge);
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      if (ref == null) {
        continue;
      }
      DmFactDimensionRole role =
          new DmFactDimensionRole(ref.getDimensionTableName(), ref.getForeignKeyColumn());
      addDimensionRoleFk(fks, childTable, role, model, config, variables, metadataProvider);
    }
    return fks;
  }

  private static List<ForeignKeySpec> resolveDimensionOutriggerForeignKeys(
      DmDimension dimension,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<ForeignKeySpec> fks = new ArrayList<>();
    String childTable = physicalTableName(dimension);
    for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
      if (outrigger == null) {
        continue;
      }
      String fk =
          variables != null
              ? variables.resolve(outrigger.getForeignKeyColumn())
              : outrigger.getForeignKeyColumn();
      DmDimension parent =
          DmDimensionResolutionSupport.resolveDimension(
              model, outrigger.getDimensionTableName(), variables, metadataProvider);
      if (parent == null) {
        continue;
      }
      if (Utils.isEmpty(fk)) {
        fk = DmSurrogateKeySupport.resolveSurrogateKeyField(parent, config, variables);
      }
      String parentTable = physicalTableName(parent);
      String parentKey = DmSurrogateKeySupport.resolveSurrogateKeyField(parent, config, variables);
      if (Utils.isEmpty(fk) || Utils.isEmpty(parentTable) || Utils.isEmpty(parentKey)) {
        continue;
      }
      fks.add(
          new ForeignKeySpec(
              constraintName("fk", childTable, parentTable),
              List.of(fk),
              parentTable,
              List.of(parentKey)));
    }
    return fks;
  }

  private static void addDimensionRoleFk(
      List<ForeignKeySpec> fks,
      String childTable,
      DmFactDimensionRole role,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (role == null) {
      return;
    }
    DmDimension dimension =
        DmDimensionResolutionSupport.resolveDimension(
            model, role.getDimensionTableName(), variables, metadataProvider);
    if (dimension == null) {
      return;
    }
    String fk =
        variables != null
            ? variables.resolve(role.getForeignKeyColumn())
            : role.getForeignKeyColumn();
    if (Utils.isEmpty(fk)) {
      fk = DmLayoutSupport.defaultFactForeignKeyColumn(dimension, role, config, variables);
    }
    String parentTable = physicalTableName(dimension);
    String parentKey = DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables);
    if (Utils.isEmpty(fk) || Utils.isEmpty(parentTable) || Utils.isEmpty(parentKey)) {
      return;
    }
    fks.add(
        new ForeignKeySpec(
            constraintName("fk", childTable, parentTable),
            List.of(fk),
            parentTable,
            List.of(parentKey)));
  }

  private static String resolveJunkSurrogateKey(
      DmJunkDimension junk, DimensionalConfiguration config, IVariables variables) {
    if (junk == null) {
      return null;
    }
    String explicit =
        variables != null
            ? variables.resolve(junk.getSurrogateKeyField())
            : junk.getSurrogateKeyField();
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    return config != null
        ? config.resolveDimKeyField(variables)
        : DimensionalConfiguration.DEFAULT_DIM_KEY_FIELD;
  }

  private static DmJunkDimension resolveJunkDimension(
      DimensionalModel model, String name, IVariables variables) {
    if (model == null || Utils.isEmpty(name)) {
      return null;
    }
    String resolved = variables != null ? variables.resolve(name) : name;
    if (model.getTables() == null) {
      return null;
    }
    for (var table : model.getTables()) {
      if (table instanceof DmJunkDimension junk && resolved.equalsIgnoreCase(junk.getName())) {
        return junk;
      }
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  public static String constraintName(String prefix, String childTable, String parentTable) {
    String raw =
        (prefix == null ? "fk" : prefix)
            + "_"
            + sanitizeIdentifier(childTable)
            + "_"
            + sanitizeIdentifier(parentTable);
    if (raw.length() <= 60) {
      return raw;
    }
    return raw.substring(0, 60);
  }

  private static String sanitizeIdentifier(String name) {
    if (Utils.isEmpty(name)) {
      return "x";
    }
    String cleaned = name.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT);
    return cleaned.isEmpty() ? "x" : cleaned;
  }

  private static void addIfPresent(List<String> columns, String name) {
    if (Utils.isEmpty(name)) {
      return;
    }
    for (String existing : columns) {
      if (existing != null && existing.equalsIgnoreCase(name)) {
        return;
      }
    }
    columns.add(name);
  }

  private static List<String> nonEmptyList(String value) {
    return Utils.isEmpty(value) ? List.of() : List.of(value);
  }
}
