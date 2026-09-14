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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvConstraintDdlSupport;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvTableResolutionSupport;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;

/** Target table layout helpers for Business Vault bridge tables. */
public final class BvBridgeLayoutSupport {

  private BvBridgeLayoutSupport() {}

  /**
   * Hub hash-key column on a bridge, with the parent hub used for foreign keys when the DV model is
   * loaded.
   */
  public record HashKeyColumn(
      String columnName, String parentHubName, String parentTableName, String parentColumnName) {}

  public static IRowMeta buildTargetTableLayout(
      BvBridge bridge,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    RowMeta rowMeta = new RowMeta();
    if (bridge == null) {
      return rowMeta;
    }
    List<HashKeyColumn> hashKeys = listHashKeyColumns(bridge, dvModel, variables, metadataProvider);
    for (HashKeyColumn hashKey : hashKeys) {
      rowMeta.addValueMeta(
          BvScd2PipelineSupport.resolveHashKeyValueMeta(hashKey.columnName(), dvModel));
    }

    String weightField = resolveWeightField(bridge, variables);
    if (!Utils.isEmpty(weightField) && rowMeta.searchValueMeta(weightField) == null) {
      rowMeta.addValueMeta(new ValueMetaNumber(weightField));
    }

    BusinessVaultConfiguration bvConfig =
        bvModel != null ? bvModel.getConfigurationOrDefault() : null;
    if (bvConfig != null) {
      DvLoadCycleSupport.appendToLayout(
          rowMeta, bvConfig.isStoreLoadCycleId(), bvConfig.getLoadCycleIdField(), variables);
    }
    return rowMeta;
  }

  public static List<HashKeyColumn> listHashKeyColumns(
      BvBridge bridge,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    Map<String, HashKeyColumn> byColumn = new LinkedHashMap<>();
    if (bridge == null || dvModel == null) {
      return List.of();
    }
    for (BvDerivativeRef derivative : bridge.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      IDvTable dvTable = dvModel.findTable(derivative.getDvTableName());
      if (dvTable instanceof DvLink link) {
        addLinkHubHashKeys(byColumn, link, dvModel, variables, metadataProvider);
      } else if (dvTable instanceof DvHub hub || derivative.getDvTableType() == DvTableType.HUB) {
        addHubHashKey(byColumn, dvTable, dvModel, variables, metadataProvider);
      }
    }
    return new ArrayList<>(byColumn.values());
  }

  public static String resolveWeightField(BvBridge bridge, IVariables variables) {
    String field = bridge != null ? bridge.getWeightField() : null;
    if (variables != null) {
      field = variables.resolve(field);
    }
    return Utils.isEmpty(field) ? null : field.trim();
  }

  public static DvLink resolveFirstLinkDerivative(BvBridge bridge, DataVaultModel dvModel) {
    if (bridge == null || dvModel == null) {
      return null;
    }
    for (BvDerivativeRef derivative : bridge.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      IDvTable dvTable = dvModel.findTable(derivative.getDvTableName());
      if (dvTable instanceof DvLink link) {
        return link;
      }
    }
    return null;
  }

  private static void addLinkHubHashKeys(
      Map<String, HashKeyColumn> byColumn,
      DvLink link,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (link.getHubNames() == null) {
      return;
    }
    for (String hubName : link.getHubNames()) {
      if (Utils.isEmpty(hubName)) {
        continue;
      }
      String column =
          DvTableResolutionSupport.resolveParticipatingHubHashColumn(
              dvModel, hubName, variables, metadataProvider);
      if (Utils.isEmpty(column)) {
        continue;
      }
      DvHub hub =
          DvTableResolutionSupport.resolveHub(dvModel, hubName, variables, metadataProvider);
      String parentTable = DvConstraintDdlSupport.physicalTableName(hub);
      String parentColumn =
          hub != null ? DvConstraintDdlSupport.resolveHubHashKeyColumn(hub, variables) : column;
      byColumn.putIfAbsent(
          column.toLowerCase(), new HashKeyColumn(column, hubName, parentTable, parentColumn));
    }
  }

  private static void addHubHashKey(
      Map<String, HashKeyColumn> byColumn,
      IDvTable dvTable,
      DataVaultModel dvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (!(dvTable instanceof DvHub hub)) {
      return;
    }
    String column =
        DvTableResolutionSupport.resolveParticipatingHubHashColumn(
            dvModel, hub.getName(), variables, metadataProvider);
    if (Utils.isEmpty(column)) {
      column = BvPitLayoutSupport.resolveHubHashKeyFieldName(hub, variables);
    }
    if (Utils.isEmpty(column)) {
      return;
    }
    byColumn.putIfAbsent(
        column.toLowerCase(),
        new HashKeyColumn(
            column,
            hub.getName(),
            DvConstraintDdlSupport.physicalTableName(hub),
            DvConstraintDdlSupport.resolveHubHashKeyColumn(hub, variables)));
  }
}
