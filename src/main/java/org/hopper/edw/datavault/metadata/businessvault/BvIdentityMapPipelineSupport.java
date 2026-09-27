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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateKeyField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateLookupField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateValue;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvSpecialRecordSupport;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.hopper.edw.datavault.metadata.HashAlgorithm;
import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.transform.identitymapassign.IdentityMapAssignMeta;

/** Upsert pipeline for a generated {@link BvIdentityMap}. The map table is never truncated. */
public final class BvIdentityMapPipelineSupport {

  private static final Class<?> PKG = BvIdentityMapPipelineSupport.class;

  static final String EDGE_TRANSFORM = "read_same_as";
  static final String EXISTING_TRANSFORM = "read_existing_map";
  static final String ASSIGN_TRANSFORM = "assign_identity";
  static final String PIPELINE_PREFIX = "bv-identity-";

  static final String EDGE_MASTER = "hk_master";
  static final String EDGE_DUPLICATE = "hk_duplicate";
  static final String EDGE_PREFERRED = "preferred_bk";
  static final String EDGE_MDM = "mdm_id";

  private BvIdentityMapPipelineSupport() {}

  public static List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvIdentityMap identityMap)
      throws HopException {
    PipelineMeta pipeline =
        generatePipeline(metadataProvider, variables, bvModel, dvModel, identityMap);
    return pipeline == null ? List.of() : List.of(pipeline);
  }

  static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvIdentityMap identityMap)
      throws HopException {
    if (metadataProvider == null || bvModel == null || identityMap == null) {
      return null;
    }
    BusinessVaultConfiguration bvConfig = bvModel.getConfigurationOrDefault();
    DatabaseMeta targetDatabase =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
    if (targetDatabase == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvIdentityMap.Error.MissingBvTargetDatabase", identityMap.getName()));
    }
    DatabaseMeta sourceDatabase = targetDatabase;
    if (dvModel != null && !Utils.isEmpty(dvModel.getConfigurationOrDefault().getTargetDatabase())) {
      DatabaseMeta dvTarget =
          DvSpecialRecordSupport.loadTargetDatabase(
              metadataProvider, dvModel.getConfigurationOrDefault());
      if (dvTarget != null) {
        sourceDatabase = dvTarget;
      }
    }

    String targetTable =
        !Utils.isEmpty(identityMap.getTableName())
            ? identityMap.getTableName()
            : identityMap.getName();
    DataVaultConfiguration dvConfig =
        dvModel != null ? dvModel.getConfigurationOrDefault() : null;
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(PIPELINE_PREFIX + targetTable);
    pipelineMeta.setMetadataProvider(metadataProvider);
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        pipelineMeta, bvModel, "identity-map", identityMap.getName(), targetTable);

    TableInputMeta edges = new TableInputMeta();
    edges.setConnection(sourceDatabase.getName());
    DvSqlSupport.assignDisplaySql(
        edges, buildSameAsSql(identityMap, dvModel, variables, sourceDatabase));
    TransformMeta edgeTransform = new TransformMeta("TableInput", EDGE_TRANSFORM, edges);
    edgeTransform.setLocation(new Point(80, 80));
    pipelineMeta.addTransform(edgeTransform);

    TableInputMeta existing = new TableInputMeta();
    existing.setConnection(targetDatabase.getName());
    DvSqlSupport.assignDisplaySql(existing, buildExistingSql(targetTable, targetDatabase, variables));
    TransformMeta existingTransform =
        new TransformMeta("TableInput", EXISTING_TRANSFORM, existing);
    existingTransform.setLocation(new Point(80, 220));
    pipelineMeta.addTransform(existingTransform);

    IdentityMapAssignMeta assignMeta = new IdentityMapAssignMeta();
    assignMeta.setEdgeTransform(EDGE_TRANSFORM);
    assignMeta.setExistingTransform(EXISTING_TRANSFORM);
    assignMeta.setMasterField(EDGE_MASTER);
    assignMeta.setDuplicateField(EDGE_DUPLICATE);
    assignMeta.setPreferredField(EDGE_PREFERRED);
    assignMeta.setMdmField(EDGE_MDM);
    assignMeta.setEdgeFromField(BvIdentityKeys.VALID_FROM);
    assignMeta.setEdgeToField(BvIdentityKeys.VALID_TO);
    assignMeta.setRecordSource(identityMap.getSameAsLinkName());
    if (dvConfig != null) {
      assignMeta.setHashAlgorithm(dvConfig.resolveHashAlgorithm().getCode());
      assignMeta.setHashKeyDataType(dvConfig.resolveHashKeyDataType().getCode());
    }
    TransformMeta assign = new TransformMeta("IdentityMapAssign", ASSIGN_TRANSFORM, assignMeta);
    assign.setLocation(new Point(320, 140));
    pipelineMeta.addTransform(assign);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(edgeTransform, assign));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(existingTransform, assign));

    TransformMeta upsert = addInsertUpdate(bvConfig, variables, targetDatabase, targetTable, assign);
    pipelineMeta.addTransform(upsert);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(assign, upsert));
    GeneratedPipelineMetadataSupport.stampWriteTarget(
        upsert, "identity-map", identityMap.getName(), targetTable, targetDatabase.getName());
    return pipelineMeta;
  }

  static String buildSameAsSql(
      BvIdentityMap identityMap,
      DataVaultModel dvModel,
      IVariables variables,
      DatabaseMeta databaseMeta) {
    String master = resolve(identityMap.getMasterHashField(), variables);
    String duplicate = resolve(identityMap.getDuplicateHashField(), variables);
    String linkTable = linkTableName(identityMap, dvModel, variables);
    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append("l.").append(quote(databaseMeta, master)).append(" AS ").append(EDGE_MASTER);
    sql.append(", l.").append(quote(databaseMeta, duplicate)).append(" AS ").append(EDGE_DUPLICATE);
    sql.append(", ").append(optionalColumn(databaseMeta, identityMap.getPreferredBusinessKeyField(), variables));
    sql.append(" AS ").append(EDGE_PREFERRED);
    sql.append(", ").append(optionalColumn(databaseMeta, identityMap.getMdmIdField(), variables));
    sql.append(" AS ").append(EDGE_MDM);
    DvSatellite effectivity = effectivitySatellite(identityMap, dvModel, variables);
    if (effectivity != null) {
      sql.append(", ")
          .append(satelliteColumn(databaseMeta, identityMap.getEffectivityFromField(), variables))
          .append(" AS ")
          .append(BvIdentityKeys.VALID_FROM);
      sql.append(", ")
          .append(satelliteColumn(databaseMeta, identityMap.getEffectivityToField(), variables))
          .append(" AS ")
          .append(BvIdentityKeys.VALID_TO);
    } else if (!Utils.isEmpty(identityMap.getEffectivityFromField())) {
      sql.append(", ")
          .append(quote(databaseMeta, resolve(identityMap.getEffectivityFromField(), variables)))
          .append(" AS ")
          .append(BvIdentityKeys.VALID_FROM);
      sql.append(", ")
          .append(
              Utils.isEmpty(identityMap.getEffectivityToField())
                  ? "NULL"
                  : quote(databaseMeta, resolve(identityMap.getEffectivityToField(), variables)))
          .append(" AS ")
          .append(BvIdentityKeys.VALID_TO);
    } else {
      sql.append(", NULL AS ").append(BvIdentityKeys.VALID_FROM);
      sql.append(", NULL AS ").append(BvIdentityKeys.VALID_TO);
    }
    sql.append(" FROM ");
    sql.append(quotedTable(databaseMeta, variables, linkTable)).append(" l");
    if (effectivity != null) {
      DvLink link = dvModel.findLink(resolve(identityMap.getSameAsLinkName(), variables), variables, null);
      String linkHash = link == null ? "link_hk" : link.resolveLinkHashKeyFieldName(variables);
      String satTable =
          !Utils.isEmpty(effectivity.getTableName())
              ? effectivity.getTableName()
              : effectivity.getName();
      sql.append(" LEFT JOIN ")
          .append(quotedTable(databaseMeta, variables, satTable))
          .append(" e ON l.")
          .append(quote(databaseMeta, linkHash))
          .append(" = e.")
          .append(quote(databaseMeta, linkHash));
    }
    return sql.toString();
  }

  static String buildExistingSql(
      String targetTable, DatabaseMeta databaseMeta, IVariables variables) {
    return "SELECT "
        + quote(databaseMeta, BvIdentityKeys.HK_RAW)
        + ", "
        + quote(databaseMeta, BvIdentityKeys.HK_DURABLE)
        + ", "
        + quote(databaseMeta, BvIdentityKeys.PREFERRED_BK)
        + ", "
        + quote(databaseMeta, BvIdentityKeys.HK_MASTER)
        + ", "
        + quote(databaseMeta, BvIdentityKeys.VALID_FROM)
        + ", "
        + quote(databaseMeta, BvIdentityKeys.VALID_TO)
        + ", "
        + quote(databaseMeta, BvIdentityKeys.RULE_VERSION)
        + " FROM "
        + quotedTable(databaseMeta, variables, targetTable);
  }

  public static IRowMeta buildTargetLayout(
      BvIdentityMap identityMap,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IVariables variables) {
    RowMeta rowMeta = new RowMeta();
    DataVaultConfiguration dvConfig =
        dvModel != null ? dvModel.getConfigurationOrDefault() : new DataVaultConfiguration();
    HashKeyDataType type = dvConfig.resolveHashKeyDataType();
    HashAlgorithm algorithm = dvConfig.resolveHashAlgorithm();
    rowMeta.addValueMeta(hashValue(BvIdentityKeys.HK_RAW, algorithm, type));
    rowMeta.addValueMeta(hashValue(BvIdentityKeys.HK_DURABLE, algorithm, type));
    ValueMetaString preferred = new ValueMetaString(BvIdentityKeys.PREFERRED_BK);
    preferred.setLength(256);
    rowMeta.addValueMeta(preferred);
    rowMeta.addValueMeta(hashValue(BvIdentityKeys.HK_MASTER, algorithm, type));
    rowMeta.addValueMeta(new ValueMetaTimestamp(BvIdentityKeys.VALID_FROM));
    rowMeta.addValueMeta(new ValueMetaTimestamp(BvIdentityKeys.VALID_TO));
    ValueMetaString recordSource = new ValueMetaString(BvIdentityKeys.RECORD_SOURCE);
    recordSource.setLength(recordSourceLength(dvConfig, variables));
    rowMeta.addValueMeta(recordSource);
    rowMeta.addValueMeta(new ValueMetaInteger(BvIdentityKeys.RULE_VERSION));
    return rowMeta;
  }

  static TransformMeta addInsertUpdate(
      BusinessVaultConfiguration bvConfig,
      IVariables variables,
      DatabaseMeta targetDatabase,
      String targetTable,
      TransformMeta predecessor) {
    InsertUpdateMeta insertUpdateMeta = new InsertUpdateMeta();
    insertUpdateMeta.setConnection(targetDatabase.getName());
    insertUpdateMeta.setCommitSize(bvConfig.resolveTargetTableCommitSize(variables));
    InsertUpdateLookupField lookup = new InsertUpdateLookupField();
    lookup.setTableName(targetTable);
    lookup.setLookupKeys(
        new ArrayList<>(List.of(new InsertUpdateKeyField(BvIdentityKeys.HK_RAW, BvIdentityKeys.HK_RAW, "="))));
    List<InsertUpdateValue> values = new ArrayList<>();
    values.add(value(BvIdentityKeys.HK_RAW, false));
    values.add(value(BvIdentityKeys.HK_DURABLE, false));
    values.add(value(BvIdentityKeys.PREFERRED_BK, true));
    values.add(value(BvIdentityKeys.HK_MASTER, true));
    values.add(value(BvIdentityKeys.VALID_FROM, false));
    values.add(value(BvIdentityKeys.VALID_TO, true));
    values.add(value(BvIdentityKeys.RECORD_SOURCE, true));
    values.add(value(BvIdentityKeys.RULE_VERSION, true));
    lookup.setValueFields(values);
    insertUpdateMeta.setInsertUpdateLookupField(lookup);
    TransformMeta transform =
        new TransformMeta("InsertUpdate", "upsert_" + targetTable, insertUpdateMeta);
    transform.setLocation(new Point(560, 140));
    return transform;
  }

  private static InsertUpdateValue value(String field, boolean update) {
    return new InsertUpdateValue(field, field, update);
  }

  private static IValueMeta hashValue(
      String name, HashAlgorithm algorithm, HashKeyDataType type) {
    try {
      IValueMeta hash =
          org.apache.hop.core.row.value.ValueMetaFactory.createValueMeta(
              name,
              org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyLogic.resultValueMetaType(type));
      hash.setLength(
          org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyLogic.resultValueMetaLength(
              algorithm, type));
      return hash;
    } catch (Exception e) {
      return new ValueMetaString(name);
    }
  }

  private static String satelliteColumn(
      DatabaseMeta databaseMeta, String field, IVariables variables) {
    String resolved = resolve(field, variables);
    if (Utils.isEmpty(resolved)) {
      return "NULL";
    }
    return "e." + quote(databaseMeta, resolved);
  }

  private static int recordSourceLength(DataVaultConfiguration dvConfig, IVariables variables) {
    if (dvConfig == null || Utils.isEmpty(dvConfig.getRecordSourceFieldLength())) {
      return 100;
    }
    String resolved = resolve(dvConfig.getRecordSourceFieldLength(), variables);
    try {
      int length = Integer.parseInt(resolved);
      return length > 0 ? length : 100;
    } catch (NumberFormatException e) {
      return 100;
    }
  }

  private static String optionalColumn(DatabaseMeta databaseMeta, String field, IVariables variables) {
    if (Utils.isEmpty(field)) {
      return "NULL";
    }
    return "l." + quote(databaseMeta, resolve(field, variables));
  }

  private static String linkTableName(
      BvIdentityMap identityMap, DataVaultModel dvModel, IVariables variables) {
    String linkName = resolve(identityMap.getSameAsLinkName(), variables);
    if (dvModel != null && !Utils.isEmpty(linkName)) {
      DvLink link = dvModel.findLink(linkName);
      if (link != null && !Utils.isEmpty(link.getTableName())) {
        return link.getTableName();
      }
    }
    return linkName;
  }

  private static DvSatellite effectivitySatellite(
      BvIdentityMap identityMap, DataVaultModel dvModel, IVariables variables) {
    if (dvModel == null || Utils.isEmpty(identityMap.getEffectivitySatelliteName())) {
      return null;
    }
    IDvTable table = dvModel.findTable(resolve(identityMap.getEffectivitySatelliteName(), variables));
    return table instanceof DvSatellite satellite ? satellite : null;
  }

  private static String quotedTable(DatabaseMeta databaseMeta, IVariables variables, String table) {
    if (databaseMeta == null) {
      return table;
    }
    return databaseMeta.getQuotedSchemaTableCombination(variables, null, table);
  }

  private static String quote(DatabaseMeta databaseMeta, String field) {
    if (databaseMeta == null || Utils.isEmpty(field)) {
      return field;
    }
    return databaseMeta.quoteField(field);
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables == null ? value.trim() : variables.resolve(value).trim();
  }
}
