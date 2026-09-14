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

import java.util.List;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvConstraintDdlSupport;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvSpecialRecordSupport;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.DvTargetLoadSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;

/** Generates Business Vault bridge load pipelines (Table Input → target load). */
public final class BvBridgePipelineSupport {

  private static final Class<?> PKG = BvBridgePipelineSupport.class;

  private static final Point LOCATION_TABLE_INPUT = new Point(160, 160);
  private static final Point LOCATION_TABLE_OUTPUT = new Point(400, 160);

  private BvBridgePipelineSupport() {}

  public static List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvBridge bridge)
      throws HopException {
    try {
      DbCache.clearAll();
      PipelineMeta pipeline =
          generatePipeline(metadataProvider, variables, bvModel, dvModel, bridge);
      return pipeline == null ? List.of() : List.of(pipeline);
    } catch (Exception e) {
      throw new HopException(
          "Error generating bridge build pipeline for Business Vault table " + bridge.getName(), e);
    }
  }

  static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvBridge bridge)
      throws HopException {
    if (metadataProvider == null || bvModel == null || bridge == null) {
      return null;
    }

    BusinessVaultConfiguration bvConfig = bvModel.getConfigurationOrDefault();
    DatabaseMeta targetDatabaseMeta =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
    if (targetDatabaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvBridgePipelineSupport.Error.MissingBvTargetDatabase", bridge.getName()));
    }

    DatabaseMeta sourceDatabaseMeta = targetDatabaseMeta;
    String sourceDbName = targetDatabaseMeta.getName();
    if (dvModel != null) {
      DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();
      if (!Utils.isEmpty(dvConfig.getTargetDatabase())) {
        DatabaseMeta dvTarget =
            DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);
        if (dvTarget != null) {
          sourceDatabaseMeta = dvTarget;
          sourceDbName = dvTarget.getName();
        }
      }
    }

    String targetTableName =
        !Utils.isEmpty(bridge.getTableName()) ? bridge.getTableName() : bridge.getName();
    String pipelineName = bvConfig.buildBridgePipelineName(variables, targetTableName);
    String sql = buildSourceSql(bridge, dvModel, variables, metadataProvider, sourceDatabaseMeta);
    IRowMeta targetLayout =
        BvBridgeLayoutSupport.buildTargetTableLayout(
            bridge, bvModel, dvModel, variables, metadataProvider);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(pipelineName);
    pipelineMeta.setMetadataProvider(metadataProvider);
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        pipelineMeta, bvModel, "bridge", bridge.getName(), targetTableName);

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(sourceDbName);
    DvSqlSupport.assignDisplaySql(tableInputMeta, sql);
    TransformMeta tableInput =
        new TransformMeta("TableInput", "read_" + targetTableName, tableInputMeta);
    tableInput.setLocation(LOCATION_TABLE_INPUT);
    pipelineMeta.addTransform(tableInput);
    GeneratedPipelineMetadataSupport.stampSourceRead(tableInput, sourceDbName);

    TransformMeta writePredecessor = tableInput;
    if (bvConfig.isStoreLoadCycleId()) {
      writePredecessor =
          DvLoadCycleSupport.addConstantForLoadCycleId(
              pipelineMeta,
              tableInput,
              true,
              bvConfig.getLoadCycleIdField(),
              variables,
              null,
              new Point(LOCATION_TABLE_OUTPUT.x - 50, LOCATION_TABLE_OUTPUT.y));
    }

    DvTargetLoadSupport.TargetLoadContext targetCtx =
        new DvTargetLoadSupport.TargetLoadContext(
            bvConfig,
            variables,
            targetDatabaseMeta,
            targetDatabaseMeta.getName(),
            targetTableName,
            pipelineName,
            bvModel.getName(),
            LOCATION_TABLE_OUTPUT.x,
            LOCATION_TABLE_OUTPUT.y);
    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            targetCtx,
            pipelineMeta,
            targetLayout,
            writePredecessor,
            java.util.Collections.emptySet(),
            false);
    if (result != null && result.transformMeta != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          result.transformMeta,
          "bridge",
          bridge.getName(),
          targetTableName,
          targetDatabaseMeta.getName());
    }

    BvGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  public static String buildSourceSql(
      BvBridge bridge,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DatabaseMeta sourceDatabaseMeta)
      throws HopException {
    if (bridge == null) {
      throw new HopException("Bridge table is required");
    }
    if (!Utils.isEmpty(bridge.getSqlQuery())) {
      return bridge.getSqlQuery().trim();
    }

    DvLink link = BvBridgeLayoutSupport.resolveFirstLinkDerivative(bridge, dvModel);
    if (link == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvBridgePipelineSupport.Error.NeedLinkOrSql", bridge.getName()));
    }
    List<BvBridgeLayoutSupport.HashKeyColumn> hashKeys =
        BvBridgeLayoutSupport.listHashKeyColumns(bridge, dvModel, variables, metadataProvider);
    if (hashKeys.size() < 2) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvBridgePipelineSupport.Error.NeedTwoHashKeys", bridge.getName()));
    }

    StringBuilder sql = new StringBuilder("SELECT ");
    for (int i = 0; i < hashKeys.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append(quoteIdentifier(sourceDatabaseMeta, hashKeys.get(i).columnName()));
    }
    sql.append(" FROM ");
    sql.append(quoteIdentifier(sourceDatabaseMeta, DvConstraintDdlSupport.physicalTableName(link)));
    return sql.toString();
  }

  private static String quoteIdentifier(DatabaseMeta databaseMeta, String name) {
    if (Utils.isEmpty(name)) {
      return name;
    }
    if (databaseMeta == null) {
      return name;
    }
    return databaseMeta.quoteField(name);
  }
}
