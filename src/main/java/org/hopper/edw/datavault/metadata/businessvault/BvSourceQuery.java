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
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;

/**
 * Satellite-shaped Business Vault input: a table/view or SQL query that SCD2 and PIT can hop to.
 *
 * <p>Not a load target. Empty connection uses the linked Data Vault target database.
 */
@Getter
@Setter
public class BvSourceQuery extends BvTableBase {

  private static final Class<?> PKG = BvSourceQuery.class;

  @HopMetadataProperty(storeWithCode = true)
  private BvSourceQueryKind sourceKind = BvSourceQueryKind.TABLE;

  /** Optional Hop {@link DatabaseMeta} name. Empty means the Data Vault target connection. */
  @HopMetadataProperty private String connectionName;

  @HopMetadataProperty private String schemaName;

  @HopMetadataProperty private String sqlQuery;

  /** Column name in this table, view, or SQL result. */
  @HopMetadataProperty private String hashKeyField;

  /**
   * Hub / SCD2 grain hash-key name when it differs from {@link #hashKeyField} (for example source
   * {@code burger_hkey} mapped to {@code hub_burger_hkey}). Empty means the source column is used
   * as-is. Applied with Select Values after Table Input, not as SQL {@code AS}.
   */
  @HopMetadataProperty private String hubHashKeyField;

  @HopMetadataProperty private String functionalTimestampField;

  @HopMetadataProperty private String loadDateField;

  @HopMetadataProperty(key = "column", groupKey = "columns")
  private List<BvSourceQueryColumn> columns = new ArrayList<>();

  public BvSourceQuery() {
    super(BvTableType.SOURCE_QUERY);
  }

  public BvSourceQueryKind getSourceKindOrDefault() {
    return sourceKind != null ? sourceKind : BvSourceQueryKind.TABLE;
  }

  public boolean isSqlSource() {
    return getSourceKindOrDefault() == BvSourceQueryKind.SQL;
  }

  public String resolvedHashKeyField(IVariables variables) {
    return resolve(hashKeyField, variables);
  }

  /** Grain name after mapping; same as the source column when {@link #hubHashKeyField} is empty. */
  public String resolvedHubHashKeyField(IVariables variables) {
    String hubField = resolve(hubHashKeyField, variables);
    if (!Utils.isEmpty(hubField)) {
      return hubField;
    }
    return resolvedHashKeyField(variables);
  }

  public boolean hashKeyNeedsRename(IVariables variables) {
    String source = resolvedHashKeyField(variables);
    String hub = resolvedHubHashKeyField(variables);
    return !Utils.isEmpty(source) && !Utils.isEmpty(hub) && !source.equals(hub);
  }

  public List<BvSourceQueryColumn> getColumns() {
    if (columns == null) {
      columns = new ArrayList<>();
    }
    return columns;
  }

  public List<String> columnNames() {
    List<String> names = new ArrayList<>();
    for (BvSourceQueryColumn column : getColumns()) {
      if (column != null && !Utils.isEmpty(column.getName())) {
        names.add(column.getName());
      }
    }
    return names;
  }

  public boolean definesColumn(String fieldName) {
    if (Utils.isEmpty(fieldName)) {
      return false;
    }
    for (BvSourceQueryColumn column : getColumns()) {
      if (column != null && fieldName.equalsIgnoreCase(column.getName())) {
        return true;
      }
    }
    return false;
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

    BvSourceQueryKind kind = getSourceKindOrDefault();
    if (kind == BvSourceQueryKind.TABLE && Utils.isEmpty(getTableName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvSourceQuery.CheckResult.MissingTableName", getName()),
              this));
    }
    if (kind == BvSourceQueryKind.SQL && Utils.isEmpty(sqlQuery)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvSourceQuery.CheckResult.MissingSql", getName()),
              this));
    }

    String resolvedHashKey = resolve(hashKeyField, variables);
    if (Utils.isEmpty(resolvedHashKey)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvSourceQuery.CheckResult.MissingHashKey", getName()),
              this));
    } else if (!getColumns().isEmpty() && !definesColumn(resolvedHashKey)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvSourceQuery.CheckResult.UnknownHashKey", getName(), resolvedHashKey),
              this));
    }

    String resolvedTs = resolve(functionalTimestampField, variables);
    if (!Utils.isEmpty(resolvedTs) && !getColumns().isEmpty() && !definesColumn(resolvedTs)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvSourceQuery.CheckResult.UnknownFunctionalTimestamp",
                  getName(),
                  resolvedTs),
              this));
    }

    String resolvedLoadDate = resolve(loadDateField, variables);
    if (!Utils.isEmpty(resolvedLoadDate)
        && !getColumns().isEmpty()
        && !definesColumn(resolvedLoadDate)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvSourceQuery.CheckResult.UnknownLoadDate", getName(), resolvedLoadDate),
              this));
    }

    String resolvedConnection = resolve(connectionName, variables);
    if (!Utils.isEmpty(resolvedConnection) && metadataProvider != null) {
      try {
        DatabaseMeta databaseMeta =
            metadataProvider.getSerializer(DatabaseMeta.class).load(resolvedConnection);
        if (databaseMeta == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvSourceQuery.CheckResult.UnknownConnection",
                      getName(),
                      resolvedConnection),
                  this));
        }
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), this));
      }
    }
  }

  @Override
  public List<String> generateBuildDdl(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    return List.of();
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    RowMeta rowMeta = new RowMeta();
    for (BvSourceQueryColumn column : getColumns()) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      rowMeta.addValueMeta(toValueMeta(column));
    }
    return rowMeta;
  }

  static IValueMeta toValueMeta(BvSourceQueryColumn column) throws HopException {
    String dataType = column.getDataType();
    int typeId = IValueMeta.TYPE_STRING;
    if (!Utils.isEmpty(dataType)) {
      typeId = ValueMetaFactory.getIdForValueMeta(dataType);
      if (typeId <= 0) {
        typeId = IValueMeta.TYPE_STRING;
      }
    }
    try {
      IValueMeta valueMeta = ValueMetaFactory.createValueMeta(column.getName(), typeId);
      valueMeta.setLength(Const.toInt(column.getLength(), -1));
      valueMeta.setPrecision(Const.toInt(column.getPrecision(), -1));
      return valueMeta;
    } catch (HopPluginException e) {
      throw new HopException(
          "Error creating value meta for source query column " + column.getName(), e);
    }
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
