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
package org.hopper.edw.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.transform.datedimensiongenerator.DateDimensionGeneratorField;
import org.hopper.edw.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic;
import org.hopper.edw.datavault.transform.datedimensiongenerator.DateDimensionGeneratorMetaFactory;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves field names produced by dimensional staging sources for validation. */
public final class DmSourceFieldResolutionSupport {

  private DmSourceFieldResolutionSupport() {}

  public static List<String> tryResolveFieldNames(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      IDmTable table) {
    IRowMeta rowMeta = tryResolveSourceRowMeta(metadataProvider, variables, model, table);
    if (rowMeta == null || rowMeta.isEmpty()) {
      return List.of();
    }
    List<String> fieldNames = new ArrayList<>();
    for (int i = 0; i < rowMeta.size(); i++) {
      String name = rowMeta.getValueMeta(i).getName();
      if (!Utils.isEmpty(name)) {
        fieldNames.add(name);
      }
    }
    return fieldNames;
  }

  public static IRowMeta tryResolveSourceRowMeta(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      IDmTable table) {
    if (metadataProvider == null || model == null || table == null) {
      return null;
    }
    DmSourceConfiguration source = table.getSourceOrDefault();
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    if (source.isPipelineSource()) {
      try {
        return DmSourcePipelineSupport.resolveSourceRowMeta(source, variables, metadataProvider);
      } catch (HopException ignored) {
        return null;
      }
    }
    if (source.isRecordDefinitionSource()) {
      return DmSourceRecordDefinitionSupport.tryResolveSourceRowMeta(
          source, config, variables, metadataProvider);
    }
    if (source.isDateGeneratorSource()) {
      try {
        DmDateGeneratorConfiguration generator =
            source.getDateGenerator() != null
                ? source.getDateGenerator()
                : DmDateGeneratorConfiguration.createDefault();
        List<DateDimensionGeneratorField> fields = new ArrayList<>(generator.getFieldsOrEmpty());
        DateDimensionGeneratorMetaFactory.ensureLoadTimestampField(
            fields, resolveLoadDateField(config, variables));
        return DateDimensionGeneratorLogic.buildOutputRowMeta(fields, table.getName(), variables);
      } catch (HopException ignored) {
        return null;
      }
    }

    String sourceSql = source.resolveSourceSql(variables);
    if (Utils.isEmpty(sourceSql)) {
      return null;
    }
    try {
      DatabaseMeta databaseMeta = resolveSourceDatabase(metadataProvider, model, table, variables);
      if (databaseMeta == null) {
        return null;
      }
      return resolveSourceRowMeta(variables, databaseMeta, sourceSql);
    } catch (HopException ignored) {
      return null;
    }
  }

  public static List<String> resolveFieldNames(
      IVariables variables, DatabaseMeta databaseMeta, String sourceSql) throws HopException {
    IRowMeta rowMeta = resolveSourceRowMeta(variables, databaseMeta, sourceSql);
    if (rowMeta == null || rowMeta.isEmpty()) {
      return List.of();
    }
    List<String> fieldNames = new ArrayList<>();
    for (int i = 0; i < rowMeta.size(); i++) {
      String name = rowMeta.getValueMeta(i).getName();
      if (!Utils.isEmpty(name)) {
        fieldNames.add(name);
      }
    }
    return fieldNames;
  }

  public static IRowMeta resolveSourceRowMeta(
      IVariables variables, DatabaseMeta databaseMeta, String sourceSql) throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Source database connection is not configured");
    }
    String sql = variables != null ? variables.resolve(sourceSql) : sourceSql;
    if (Utils.isEmpty(sql)) {
      throw new HopException("Source SQL is empty");
    }

    LoggingObject loggingObject = new LoggingObject("DmSourceFieldResolutionSupport");
    try (Database database = new Database(loggingObject, variables, databaseMeta)) {
      database.connect();
      return database.getQueryFields(sql, false);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to read fields from source SQL", e);
    }
  }

  private static String resolveLoadDateField(
      DimensionalConfiguration config, IVariables variables) {
    if (config == null) {
      return null;
    }
    String loadDateField = config.resolveLoadDateField(variables);
    return variables != null ? variables.resolve(loadDateField) : loadDateField;
  }

  private static DatabaseMeta resolveSourceDatabase(
      IHopMetadataProvider metadataProvider,
      DimensionalModel model,
      IDmTable table,
      IVariables variables)
      throws HopException {
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    String connectionName = table.getSourceOrDefault().resolveSourceConnection(config, variables);
    if (Utils.isEmpty(connectionName)) {
      return null;
    }
    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    if (databaseMeta == null) {
      throw new HopException("Source database connection not found in metadata: " + connectionName);
    }
    return databaseMeta;
  }
}
