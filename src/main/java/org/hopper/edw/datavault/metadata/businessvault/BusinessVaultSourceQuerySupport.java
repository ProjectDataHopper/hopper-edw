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
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSpecialRecordSupport;

/** Rules for hops from SCD2/PIT tables to {@link BvSourceQuery} canvas objects. */
public final class BusinessVaultSourceQuerySupport {

  private static final Class<?> PKG = BusinessVaultSourceQuerySupport.class;

  private BusinessVaultSourceQuerySupport() {}

  public static void validateRefs(
      List<ICheckResult> remarks, IBvTable consumer, BusinessVaultModel model) {
    if (remarks == null || consumer == null) {
      return;
    }
    for (BvSourceQueryRef ref : getRefs(consumer)) {
      if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "BusinessVaultSourceQuerySupport.Error.IncompleteRef", consumer.getName()),
                consumer));
        continue;
      }
      BvSourceQuery sourceQuery = findSourceQuery(model, ref.getSourceQueryName());
      if (sourceQuery == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BusinessVaultSourceQuerySupport.Error.UnknownSourceQuery",
                    consumer.getName(),
                    ref.getSourceQueryName()),
                consumer));
      }
    }
  }

  public static boolean isConsumer(IBvTable table) {
    return table instanceof BvScd2Table || table instanceof BvPitTable;
  }

  public static List<BvSourceQueryRef> getRefs(IBvTable table) {
    if (table instanceof BvTableBase base) {
      return base.getSourceQueryRefs();
    }
    return List.of();
  }

  public static boolean hasSourceQuery(IBvTable table, String sourceQueryName) {
    if (table == null || Utils.isEmpty(sourceQueryName)) {
      return false;
    }
    return getRefs(table).stream()
        .anyMatch(ref -> ref != null && sourceQueryName.equalsIgnoreCase(ref.getSourceQueryName()));
  }

  public static boolean canAddSourceQuery(IBvTable consumer, BvSourceQuery sourceQuery) {
    if (!isConsumer(consumer) || sourceQuery == null || Utils.isEmpty(sourceQuery.getName())) {
      return false;
    }
    return !hasSourceQuery(consumer, sourceQuery.getName());
  }

  public static boolean addSourceQuery(IBvTable consumer, BvSourceQuery sourceQuery) {
    if (!canAddSourceQuery(consumer, sourceQuery)) {
      return false;
    }
    getRefs(consumer).add(new BvSourceQueryRef(sourceQuery.getName()));
    return true;
  }

  public static boolean removeSourceQuery(IBvTable consumer, String sourceQueryName) {
    if (consumer == null || Utils.isEmpty(sourceQueryName)) {
      return false;
    }
    return getRefs(consumer)
        .removeIf(ref -> ref != null && sourceQueryName.equalsIgnoreCase(ref.getSourceQueryName()));
  }

  public static List<BvSourceQuery> resolveSourceQueries(
      IBvTable consumer, BusinessVaultModel model) {
    List<BvSourceQuery> resolved = new ArrayList<>();
    if (consumer == null || model == null) {
      return resolved;
    }
    for (BvSourceQueryRef ref : getRefs(consumer)) {
      if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
        continue;
      }
      BvSourceQuery sourceQuery = findSourceQuery(model, ref.getSourceQueryName());
      if (sourceQuery != null) {
        resolved.add(sourceQuery);
      }
    }
    return resolved;
  }

  public static BvSourceQuery findSourceQuery(BusinessVaultModel model, String name) {
    if (model == null || Utils.isEmpty(name)) {
      return null;
    }
    IBvTable table = model.findTable(name);
    if (table instanceof BvSourceQuery sourceQuery) {
      return sourceQuery;
    }
    return null;
  }

  public static List<String> listSourceQueryNames(BusinessVaultModel model) {
    List<String> names = new ArrayList<>();
    if (model == null) {
      return names;
    }
    for (IBvTable table : model.getTables()) {
      if (table instanceof BvSourceQuery sourceQuery && !Utils.isEmpty(sourceQuery.getName())) {
        names.add(sourceQuery.getName());
      }
    }
    return names;
  }

  /**
   * Connection used by a source query Table Input. Empty {@code connectionName} means the Data
   * Vault target database.
   */
  public static String resolveConnectionName(
      BvSourceQuery sourceQuery, DataVaultModel dvModel, IVariables variables) {
    if (sourceQuery != null && !Utils.isEmpty(sourceQuery.getConnectionName())) {
      String resolved =
          variables != null
              ? variables.resolve(sourceQuery.getConnectionName())
              : sourceQuery.getConnectionName();
      if (!Utils.isEmpty(resolved)) {
        return resolved;
      }
    }
    if (dvModel == null) {
      return null;
    }
    DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();
    String target = dvConfig != null ? dvConfig.getTargetDatabase() : null;
    if (Utils.isEmpty(target)) {
      return null;
    }
    return variables != null ? variables.resolve(target) : target;
  }

  public static DatabaseMeta loadConnection(
      BvSourceQuery sourceQuery,
      DataVaultModel dvModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    String connectionName = resolveConnectionName(sourceQuery, dvModel, variables);
    if (Utils.isEmpty(connectionName) || metadataProvider == null) {
      return null;
    }
    if (dvModel != null) {
      DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();
      String dvTarget = dvConfig != null ? dvConfig.getTargetDatabase() : null;
      if (!Utils.isEmpty(dvTarget)
          && connectionName.equals(variables != null ? variables.resolve(dvTarget) : dvTarget)) {
        return DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);
      }
    }
    return metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
  }

  public static boolean usesDvTargetConnection(
      BvSourceQuery sourceQuery, DataVaultModel dvModel, IVariables variables) {
    String connectionName = resolveConnectionName(sourceQuery, dvModel, variables);
    if (Utils.isEmpty(connectionName) || dvModel == null) {
      return true;
    }
    DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();
    String dvTarget = dvConfig != null ? dvConfig.getTargetDatabase() : null;
    if (Utils.isEmpty(dvTarget)) {
      return false;
    }
    String resolvedDv = variables != null ? variables.resolve(dvTarget) : dvTarget;
    return connectionName.equals(resolvedDv);
  }
}
