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
package org.hopper.edw.datavault.transform.sqlexpression;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Calculation;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlModelPathSupport;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Loads SCD2 calculation specs from an optional Business Vault model + table reference. */
public final class SqlExpressionBvTableSupport {

  private static final Class<?> PKG = SqlExpressionMeta.class;

  private SqlExpressionBvTableSupport() {}

  public static boolean isBound(String modelFilename, String scd2TableName) {
    return !Utils.isEmpty(modelFilename) && !Utils.isEmpty(scd2TableName);
  }

  public static boolean isBound(SqlExpressionMeta meta, IVariables variables) {
    if (meta == null) {
      return false;
    }
    return isBound(
        resolve(variables, meta.getBusinessVaultModelFilename()),
        resolve(variables, meta.getScd2TableName()));
  }

  public static List<SqlExpressionSpec> resolveSpecs(
      SqlExpressionMeta meta, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (meta == null) {
      return List.of();
    }
    if (!isBound(meta, variables)) {
      return meta.toSpecs();
    }
    BvScd2Table table =
        loadScd2Table(
            resolve(variables, meta.getBusinessVaultModelFilename()),
            resolve(variables, meta.getScd2TableName()),
            variables,
            metadataProvider);
    return specsFromTable(table);
  }

  public static List<SqlExpressionSpec> specsFromTable(BvScd2Table table) {
    List<SqlExpressionSpec> specs = new ArrayList<>();
    if (table == null) {
      return specs;
    }
    for (BvScd2Calculation calculation : table.getCalculations()) {
      if (calculation == null || Utils.isEmpty(calculation.getExpression())) {
        continue;
      }
      specs.add(calculation.toSpec());
    }
    return specs;
  }

  public static List<String> listScd2TableNames(BusinessVaultModel model) {
    List<String> names = new ArrayList<>();
    if (model == null) {
      return names;
    }
    for (IBvTable table : model.getTables()) {
      if (table instanceof BvScd2Table scd2 && !Utils.isEmpty(scd2.getName())) {
        names.add(scd2.getName());
      }
    }
    return names;
  }

  public static BusinessVaultModel loadModel(
      String filename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(filename)) {
      throw new HopException(BaseMessages.getString(PKG, "SqlExpressionMeta.Error.MissingBvModel"));
    }
    String resolved = HopVfs.normalize(variables != null ? variables.resolve(filename) : filename);
    try {
      BusinessVaultModel model =
          BvSqlModelPathSupport.loadBusinessVaultModelUncached(resolved, metadataProvider);
      ModelConfigurationResolver.attach(model, metadataProvider);
      return model;
    } catch (HopException e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SqlExpressionMeta.Error.LoadBvModel", resolved, e.getMessage()),
          e);
    }
  }

  public static BvScd2Table loadScd2Table(
      String filename,
      String tableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    BusinessVaultModel model = loadModel(filename, variables, metadataProvider);
    BvScd2Table table = findScd2Table(model, tableName);
    if (table == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "SqlExpressionMeta.Error.MissingScd2Table",
              Const.NVL(tableName, ""),
              Const.NVL(model.getName(), filename)));
    }
    return table;
  }

  public static BvScd2Table findScd2Table(BusinessVaultModel model, String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return null;
    }
    for (IBvTable table : model.getTables()) {
      if (!(table instanceof BvScd2Table scd2)) {
        continue;
      }
      if (tableName.equalsIgnoreCase(scd2.getName())
          || tableName.equalsIgnoreCase(scd2.getTableName())) {
        return scd2;
      }
    }
    return null;
  }

  private static String resolve(IVariables variables, String value) {
    if (variables == null || Utils.isEmpty(value)) {
      return value;
    }
    return variables.resolve(value);
  }
}
