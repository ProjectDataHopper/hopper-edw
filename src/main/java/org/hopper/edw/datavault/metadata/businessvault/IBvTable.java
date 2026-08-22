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
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Common interface for Business Vault tables on a {@link BusinessVaultModel} canvas. */
@HopMetadataObject(xmlKey = "tableType", objectFactory = IBvTable.BvTableFactory.class)
public interface IBvTable extends IGuiPosition, IBaseMeta, IHasName, IChanged, ICheckResultSource {

  String getName();

  void setName(String name);

  String getTableName();

  void setTableName(String tableName);

  String getDescription();

  void setDescription(String description);

  BvTableType getTableType();

  List<BvDerivativeRef> getDerivatives();

  void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel);

  List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException;

  List<String> generateBuildDdl(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException;

  IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException;

  final class BvTableFactory implements IHopMetadataObjectFactory {

    @Override
    public Object createObject(String id, Object parentObject) throws HopException {
      if (BvTableType.SCD2.name().equals(id)) {
        return new BvScd2Table();
      }
      if (BvTableType.PIT.name().equals(id)) {
        return new BvPitTable();
      }
      if (BvTableType.BUSINESS_TABLE.name().equals(id)) {
        return new BvBusinessTable();
      }
      throw new HopException("Unable to recognize Business Vault table type with ID '" + id + "'");
    }

    @Override
    public String getObjectId(Object object) throws HopException {
      if (!(object instanceof IBvTable bvTable)) {
        throw new HopException(
            "Object is not of class IBvTable but of " + object.getClass().getName());
      }
      BvTableType tableType = bvTable.getTableType();
      if (tableType == null) {
        throw new HopException("Business Vault table has no table type set");
      }
      return tableType.name();
    }
  }
}
