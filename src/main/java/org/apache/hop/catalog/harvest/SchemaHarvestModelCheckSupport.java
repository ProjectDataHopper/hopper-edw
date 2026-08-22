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
package org.apache.hop.catalog.harvest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryPublisher;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HistoryConnection;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DvModelCheckCache;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Warms {@link DvModelCheckCache} with DISCOVERED field layouts from a schema harvest so detailed
 * model checks can skip live JDBC discovery when a harvest already ran in the same workflow.
 */
public final class SchemaHarvestModelCheckSupport {

  private SchemaHarvestModelCheckSupport() {}

  /**
   * Result of attempting to load harvest fields for model-check reuse.
   *
   * @param usedHarvest true when at least one cache entry was produced
   * @param harvestRunId run id loaded (if any)
   * @param subjectCount subjects considered from the harvest
   * @param cacheEntries database live-fields keys populated
   * @param message short status for logs
   */
  public record WarmResult(
      boolean usedHarvest,
      String harvestRunId,
      int subjectCount,
      int cacheEntries,
      String message) {}

  /**
   * Loads DISCOVERED fields keyed by {@link DvModelCheckCache#databaseLiveFieldsKey} for reuse
   * across parallel model checks (immutable map; apply with {@link #applyToCache}).
   */
  public static Map<String, IRowMeta> loadDiscoveredFieldsByDatabaseKey(
      DvModelCheckOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    WarmLoad loaded = loadHarvest(options, variables, metadataProvider);
    if (loaded == null) {
      return Map.of();
    }
    return buildDatabaseKeyMap(loaded.harvest(), variables, log);
  }

  /**
   * When {@link DvModelCheckOptions#isPreferHarvestForLiveFields()} is true, load the harvest and
   * pre-seed the options cache. Missing harvest is non-fatal (returns {@code usedHarvest=false});
   * live discovery remains the fallback.
   */
  public static WarmResult warmCacheIfPreferred(
      DvModelCheckOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log) {
    if (options == null || !options.isPreferHarvestForLiveFields()) {
      return new WarmResult(false, null, 0, 0, "Harvest reuse disabled");
    }
    if (!options.isDetailedDataTypeChecking()) {
      return new WarmResult(false, null, 0, 0, "Detailed type checking disabled");
    }
    try {
      WarmLoad loaded = loadHarvest(options, variables, metadataProvider);
      if (loaded == null) {
        String msg = "No harvest run available for type-check reuse (will use live discovery)";
        if (log != null) {
          log.logBasic(msg);
        }
        return new WarmResult(false, null, 0, 0, msg);
      }
      Map<String, IRowMeta> byKey = buildDatabaseKeyMap(loaded.harvest(), variables, log);
      applyToCache(options.ensureCache(), byKey);
      String msg =
          "Warmed model-check cache from harvest run "
              + loaded.runId()
              + " ("
              + byKey.size()
              + " table layout(s) from "
              + loaded.harvest().subjectCount()
              + " subject(s))";
      if (log != null) {
        log.logBasic(msg);
      }
      return new WarmResult(
          !byKey.isEmpty(), loaded.runId(), loaded.harvest().subjectCount(), byKey.size(), msg);
    } catch (Exception e) {
      String msg =
          "Unable to load harvest for type-check reuse (will use live discovery): "
              + Const.NVL(e.getMessage(), e.getClass().getSimpleName());
      if (log != null) {
        log.logBasic(msg);
      }
      return new WarmResult(false, null, 0, 0, msg);
    }
  }

  /** Copies preloaded harvest layouts into a check-session cache. */
  public static void applyToCache(DvModelCheckCache cache, Map<String, IRowMeta> harvestedLayouts) {
    if (cache == null || harvestedLayouts == null || harvestedLayouts.isEmpty()) {
      return;
    }
    for (Map.Entry<String, IRowMeta> entry : harvestedLayouts.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        cache.putLiveFields(entry.getKey(), entry.getValue());
      }
    }
  }

  private record WarmLoad(String runId, HarvestResult harvest) {}

  private static WarmLoad loadHarvest(
      DvModelCheckOptions options, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    String catalogConnection = resolveOptional(options.getHarvestCatalogConnection(), variables);
    String historyDb = resolveOptional(options.getHarvestHistoryDatabase(), variables);
    String historySchema = resolveOptional(options.getHarvestHistorySchema(), variables);
    HistoryConnection history =
        SchemaHarvestHistoryReader.resolveConnection(
            historyDb, historySchema, catalogConnection, variables, metadataProvider);
    if (history == null) {
      return null;
    }
    DatabaseMeta databaseMeta =
        SchemaHarvestHistoryReader.loadDatabaseMeta(history.databaseMetaName(), metadataProvider);
    if (databaseMeta == null) {
      return null;
    }

    String runId = resolveOptional(options.getHarvestRunId(), variables);
    if (Utils.isEmpty(runId)) {
      runId = variableValue(variables, SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_RUN_ID);
    }
    if (Utils.isEmpty(runId)) {
      String group = resolveOptional(options.getHarvestResourceGroup(), variables);
      if (!Utils.isEmpty(group)) {
        runId =
            SchemaHarvestHistoryReader.findLatestRunId(
                databaseMeta, history.schemaName(), group, variables);
      }
    }
    if (Utils.isEmpty(runId)) {
      return null;
    }

    HarvestResult harvest =
        SchemaHarvestHistoryReader.loadHarvestResult(
            databaseMeta, history.schemaName(), runId, variables);
    return new WarmLoad(runId.trim(), harvest);
  }

  private static String resolveOptional(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    String trimmed = value.trim();
    if (variables != null) {
      String resolved = variables.resolve(trimmed);
      if (!Utils.isEmpty(resolved) && !resolved.contains("${")) {
        return resolved.trim();
      }
      // Unresolved template — treat as empty so callers can fall through.
      if (trimmed.contains("${")) {
        return null;
      }
    }
    return trimmed;
  }

  static Map<String, IRowMeta> buildDatabaseKeyMap(
      HarvestResult harvest, IVariables variables, ILogChannel log) throws HopException {
    Map<String, IRowMeta> byKey = new LinkedHashMap<>();
    if (harvest == null) {
      return byKey;
    }
    for (HarvestSubjectResult subject : harvest.subjectsView()) {
      if (subject == null || subject.getDiscoveryStatus() != DiscoveryStatus.OK) {
        continue;
      }
      if (Utils.isEmpty(subject.getDatabaseMetaName()) || Utils.isEmpty(subject.getTableName())) {
        continue;
      }
      List<SourceField> discovered = toDiscoveredSourceFields(subject.getFields());
      if (discovered.isEmpty()) {
        continue;
      }
      IRowMeta rowMeta = DvSourceFieldSupport.toRowMeta(discovered, variables);
      if (rowMeta == null || rowMeta.isEmpty()) {
        continue;
      }
      String key =
          DvModelCheckCache.databaseLiveFieldsKey(
              subject.getDatabaseMetaName(),
              subject.getSchemaName(),
              subject.getTableName(),
              variables);
      byKey.put(key, rowMeta);
    }
    return byKey;
  }

  /** Package-visible for tests: convert harvest field snapshots to SourceField list. */
  static List<SourceField> toDiscoveredSourceFields(List<HarvestedField> fields) {
    List<SourceField> result = new ArrayList<>();
    if (fields == null) {
      return result;
    }
    for (HarvestedField field : fields) {
      if (field == null || field.getRole() != FieldRole.DISCOVERED) {
        continue;
      }
      if (Utils.isEmpty(field.getFieldName())) {
        continue;
      }
      SourceField sf = new SourceField(field.getFieldName());
      sf.setHopType(resolveHopType(field.getHopType()));
      sf.setLength(Const.NVL(field.getLength(), ""));
      sf.setPrecision(Const.NVL(field.getPrecision(), ""));
      sf.setSourceDataType(Const.NVL(field.getSourceDataType(), ""));
      sf.setPrimaryKeyPosition(field.getPrimaryKeyPosition());
      result.add(sf);
    }
    return result;
  }

  static int resolveHopType(String hopTypeName) {
    if (Utils.isEmpty(hopTypeName)) {
      return IValueMeta.TYPE_STRING;
    }
    try {
      int id = ValueMetaFactory.getIdForValueMeta(hopTypeName.trim());
      return id > 0 ? id : IValueMeta.TYPE_STRING;
    } catch (Exception e) {
      // Fallback: case-insensitive enum-ish names.
      String upper = hopTypeName.trim().toUpperCase(Locale.ROOT);
      return switch (upper) {
        case "INTEGER", "INT", "LONG", "BIGINT" -> IValueMeta.TYPE_INTEGER;
        case "NUMBER", "DOUBLE", "FLOAT" -> IValueMeta.TYPE_NUMBER;
        case "BIGNUMBER", "DECIMAL", "NUMERIC" -> IValueMeta.TYPE_BIGNUMBER;
        case "DATE" -> IValueMeta.TYPE_DATE;
        case "TIMESTAMP" -> IValueMeta.TYPE_TIMESTAMP;
        case "BOOLEAN", "BOOL" -> IValueMeta.TYPE_BOOLEAN;
        case "BINARY" -> IValueMeta.TYPE_BINARY;
        default -> IValueMeta.TYPE_STRING;
      };
    }
  }

  private static String variableValue(IVariables variables, String name) {
    if (variables == null || Utils.isEmpty(name)) {
      return null;
    }
    String raw = variables.getVariable(name);
    if (!Utils.isEmpty(raw)) {
      return raw.trim();
    }
    String resolved = variables.resolve("${" + name + "}");
    if (!Utils.isEmpty(resolved) && !resolved.contains("${")) {
      return resolved.trim();
    }
    return null;
  }
}
