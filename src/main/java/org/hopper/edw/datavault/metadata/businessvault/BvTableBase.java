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
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvConstraintDdlSupport;
import org.hopper.edw.datavault.metadata.DvDdlSupport;
import org.hopper.edw.datavault.metadata.ForeignKeySpec;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingSupport;

@Getter
@Setter
public abstract class BvTableBase extends HopMetadataBase implements IHopMetadata, IBvTable {

  private static final Class<?> PKG = BvTableBase.class;

  @HopMetadataProperty(key = "tableName")
  protected String tableName;

  @HopMetadataProperty protected String description;

  @HopMetadataProperty protected BvTableType tableType;

  @HopMetadataProperty(key = "derivative", groupKey = "derivatives")
  private List<BvDerivativeRef> derivatives = new ArrayList<>();

  @HopMetadataProperty(inline = true)
  private Point location = new Point(0, 0);

  private boolean selected;
  private int drawnBoxWidth = 140;
  private int drawnBoxHeight = 70;

  protected final ChangedFlag changedFlag = new ChangedFlag();

  protected BvTableBase(BvTableType tableType) {
    this.tableType = tableType;
  }

  @Override
  public List<BvDerivativeRef> getDerivatives() {
    if (derivatives == null) {
      derivatives = new ArrayList<>();
    }
    return derivatives;
  }

  @Override
  public void setLocation(int x, int y) {
    if (location == null) {
      location = new Point(x, y);
    } else {
      location.x = x;
      location.y = y;
    }
  }

  @Override
  public Point getLocation() {
    if (location == null) {
      location = new Point(0, 0);
    }
    return location;
  }

  @Override
  public boolean hasChanged() {
    return changedFlag.hasChanged();
  }

  @Override
  public void setChanged() {
    changedFlag.setChanged();
  }

  @Override
  public void setChanged(boolean changed) {
    changedFlag.setChanged(changed);
  }

  @Override
  public void clearChanged() {
    changedFlag.setChanged(false);
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    if (Utils.isEmpty(getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvTableBase.CheckResult.MissingName"),
              this));
    }
    if (Utils.isEmpty(getTableName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvTableBase.CheckResult.MissingTableName", getName()),
              this));
    }
    BusinessVaultDerivativeValidationSupport.validateDerivatives(remarks, this, dataVaultModel);
  }

  @Override
  public List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return List.of();
  }

  @Override
  public List<String> generateBuildDdl(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    List<String> result = new ArrayList<>();
    if (metadataProvider == null || model == null) {
      return result;
    }

    BusinessVaultConfiguration config = model.getConfigurationOrDefault();
    DatabaseMeta targetDatabaseMeta =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
    if (targetDatabaseMeta == null) {
      return result;
    }

    String targetTableName = !Utils.isEmpty(getTableName()) ? getTableName() : getName();
    IRowMeta targetFields =
        getTargetTableLayout(metadataProvider, variables, model, dataVaultModel);
    if (targetFields == null || targetFields.isEmpty()) {
      return result;
    }

    ILoggingObject loggingObject =
        new SimpleLoggingObject(
            getClass().getSimpleName() + ".generateBuildDdl", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(loggingObject, variables, targetDatabaseMeta)) {
      db.connect();
      List<String> primaryKeyColumns =
          DvConstraintDdlSupport.resolveBvPrimaryKeyColumns(
              this, model, config, dataVaultModel, variables, targetFields);
      List<ForeignKeySpec> foreignKeys =
          DvConstraintDdlSupport.resolveBvForeignKeys(
              this, model, config, dataVaultModel, targetDatabaseMeta, metadataProvider, variables);
      String ddl =
          DvDdlSupport.getTargetTableDdl(
              db,
              targetTableName,
              targetFields,
              null,
              primaryKeyColumns,
              foreignKeys,
              TargetTypeMappingSupport.resolve(
                  null, targetDatabaseMeta, metadataProvider, variables));
      if (!Utils.isEmpty(ddl)) {
        result.add(ddl);
      }
    } catch (Exception e) {
      throw new HopException("Error getting DDL for Business Vault table: " + targetTableName, e);
    }
    return result;
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return new RowMeta();
  }
}
