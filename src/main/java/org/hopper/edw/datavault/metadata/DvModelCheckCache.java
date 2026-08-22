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
package org.hopper.edw.datavault.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DvTargetUnicodeCapabilitySupport.Assessment;

/**
 * Per check-session cache for live source schema lookups, open JDBC connections, catalog name
 * lists, and target Unicode probes.
 *
 * <p>Without this, detailed model checking opens a new database connection for every table (and
 * often twice per table) when resolving live field metadata. For multi-model batches, use {@link
 * DvModelCheckOptions#forSharedCheckSession()} so one session spans many models. Callers must
 * {@link #close()} when the session ends.
 */
public final class DvModelCheckCache implements AutoCloseable {

  private final Map<String, IRowMeta> liveFieldsByKey = new LinkedHashMap<>();
  private final Map<String, Database> openDatabasesByConnection = new LinkedHashMap<>();
  private final Map<String, DatabaseMeta> databaseMetaByName = new LinkedHashMap<>();
  private final Map<String, Assessment> unicodeAssessmentsByConnection = new LinkedHashMap<>();
  private final Map<String, List<String>> catalogSourceNamesByConnection = new LinkedHashMap<>();
  private boolean closed;

  public IRowMeta getLiveFields(String cacheKey) {
    ensureOpen();
    if (Utils.isEmpty(cacheKey)) {
      return null;
    }
    return liveFieldsByKey.get(cacheKey);
  }

  public void putLiveFields(String cacheKey, IRowMeta rowMeta) {
    ensureOpen();
    if (Utils.isEmpty(cacheKey) || rowMeta == null) {
      return;
    }
    liveFieldsByKey.put(cacheKey, rowMeta);
  }

  public Database getOpenDatabase(String connectionName) {
    ensureOpen();
    if (Utils.isEmpty(connectionName)) {
      return null;
    }
    return openDatabasesByConnection.get(connectionName);
  }

  public void putOpenDatabase(String connectionName, Database database) {
    ensureOpen();
    if (Utils.isEmpty(connectionName) || database == null) {
      return;
    }
    openDatabasesByConnection.put(connectionName, database);
  }

  public DatabaseMeta getDatabaseMeta(String connectionName) {
    ensureOpen();
    if (Utils.isEmpty(connectionName)) {
      return null;
    }
    return databaseMetaByName.get(connectionName);
  }

  public void putDatabaseMeta(String connectionName, DatabaseMeta databaseMeta) {
    ensureOpen();
    if (Utils.isEmpty(connectionName) || databaseMeta == null) {
      return;
    }
    databaseMetaByName.put(connectionName, databaseMeta);
  }

  public Assessment getUnicodeAssessment(String connectionName) {
    ensureOpen();
    if (Utils.isEmpty(connectionName)) {
      return null;
    }
    return unicodeAssessmentsByConnection.get(connectionName);
  }

  public void putUnicodeAssessment(String connectionName, Assessment assessment) {
    ensureOpen();
    if (Utils.isEmpty(connectionName) || assessment == null) {
      return;
    }
    unicodeAssessmentsByConnection.put(connectionName, assessment);
  }

  public List<String> getCatalogSourceNames(String catalogConnectionName) {
    ensureOpen();
    if (Utils.isEmpty(catalogConnectionName)) {
      return null;
    }
    List<String> names = catalogSourceNamesByConnection.get(catalogConnectionName);
    return names != null ? List.copyOf(names) : null;
  }

  public void putCatalogSourceNames(String catalogConnectionName, List<String> sourceNames) {
    ensureOpen();
    if (Utils.isEmpty(catalogConnectionName) || sourceNames == null) {
      return;
    }
    catalogSourceNamesByConnection.put(catalogConnectionName, new ArrayList<>(sourceNames));
  }

  /**
   * Stable key for a live database table schema lookup: {@code connection|schema|table} after
   * variable resolution.
   */
  public static String databaseLiveFieldsKey(
      String connectionName, String schemaName, String tableName, IVariables variables) {
    String connection = resolve(connectionName, variables);
    String schema = resolve(schemaName, variables);
    String table = resolve(tableName, variables);
    return "db|" + connection + "|" + Const.NVL(schema, "") + "|" + Const.NVL(table, "");
  }

  public static String genericLiveFieldsKey(String sourceType, String identity) {
    return Const.NVL(sourceType, "source") + "|" + Const.NVL(identity, "");
  }

  /** Number of distinct live field resolutions cached (for tests/diagnostics). */
  public int liveFieldsCacheSize() {
    return liveFieldsByKey.size();
  }

  /** Number of open JDBC connections held for reuse (for tests/diagnostics). */
  public int openDatabaseCount() {
    return openDatabasesByConnection.size();
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    for (Database database : openDatabasesByConnection.values()) {
      if (database == null) {
        continue;
      }
      try {
        database.disconnect();
      } catch (Exception e) {
        // Best-effort cleanup after model check.
      }
    }
    openDatabasesByConnection.clear();
    liveFieldsByKey.clear();
    databaseMetaByName.clear();
    unicodeAssessmentsByConnection.clear();
    catalogSourceNamesByConnection.clear();
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("DvModelCheckCache is already closed");
    }
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (variables != null) {
      return Const.NVL(variables.resolve(trimmed), "").trim();
    }
    return trimmed;
  }
}
