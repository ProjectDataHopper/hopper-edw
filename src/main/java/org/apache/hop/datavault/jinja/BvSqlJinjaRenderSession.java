/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.jinja;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvBusinessTable;
import org.apache.hop.datavault.metadata.businessvault.BvSqlRef;
import org.apache.hop.datavault.metadata.businessvault.BvSqlResolvedKind;
import org.apache.hop.datavault.metadata.businessvault.BvSqlSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Mutable per-render state shared with {@link DbtJinjaBuiltins}. */
@Getter
public final class BvSqlJinjaRenderSession {

  private final BvBusinessTable table;
  private final BusinessVaultModel bvModel;
  private final DataVaultModel dvModel;
  private final IHopMetadataProvider metadataProvider;
  private final IVariables variables;
  private final DatabaseMeta bvDatabase;
  private final DatabaseMeta dvDatabase;
  private final Map<String, String> libraryVars;
  private final List<BvSqlRef> refs = new ArrayList<>();
  private final List<BvSqlSource> sourceUsages = new ArrayList<>();
  private final Set<String> refKeys = new LinkedHashSet<>();
  private final Set<String> sourceKeys = new LinkedHashSet<>();

  public BvSqlJinjaRenderSession(
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DatabaseMeta bvDatabase,
      DatabaseMeta dvDatabase,
      Map<String, String> libraryVars) {
    this.table = table;
    this.bvModel = bvModel;
    this.dvModel = dvModel;
    this.metadataProvider = metadataProvider;
    this.variables = variables;
    this.bvDatabase = bvDatabase;
    this.dvDatabase = dvDatabase != null ? dvDatabase : bvDatabase;
    this.libraryVars = libraryVars != null ? libraryVars : Map.of();
  }

  public void addRef(BvSqlRef ref) {
    if (ref == null || Utils.isEmpty(ref.getObjectName())) {
      return;
    }
    String key =
        (ref.getModelName() == null ? "" : ref.getModelName().trim().toLowerCase(Locale.ROOT))
            + "\0"
            + ref.getObjectName().trim().toLowerCase(Locale.ROOT);
    if (refKeys.add(key)) {
      refs.add(ref);
    }
  }

  public void addSourceUsage(BvSqlSource source) {
    if (source == null
        || Utils.isEmpty(source.getSourceName())
        || Utils.isEmpty(source.getTableName())) {
      return;
    }
    String key =
        source.getSourceName().trim().toLowerCase(Locale.ROOT)
            + "\0"
            + source.getTableName().trim().toLowerCase(Locale.ROOT);
    if (sourceKeys.add(key)) {
      sourceUsages.add(source);
    }
  }

  public String libraryVar(String name) {
    if (Utils.isEmpty(name) || libraryVars.isEmpty()) {
      return null;
    }
    String direct = libraryVars.get(name);
    if (direct != null) {
      return direct;
    }
    return libraryVars.get(name.toLowerCase(Locale.ROOT));
  }

  public DatabaseMeta databaseFor(BvSqlRef ref) {
    if (ref != null && ref.getResolvedKind() == BvSqlResolvedKind.DV_TABLE) {
      return dvDatabase;
    }
    return bvDatabase;
  }

  public DatabaseMeta databaseForSource(BvSqlSource declared) {
    if (declared == null || Utils.isEmpty(declared.getDatabaseName()) || metadataProvider == null) {
      return bvDatabase;
    }
    try {
      DatabaseMeta sourceDb =
          metadataProvider.getSerializer(DatabaseMeta.class).load(declared.getDatabaseName());
      return sourceDb != null ? sourceDb : bvDatabase;
    } catch (Exception e) {
      return bvDatabase;
    }
  }

  public static Map<String, String> caseInsensitiveVars(Map<String, String> vars) {
    Map<String, String> out = new LinkedHashMap<>();
    if (vars == null) {
      return out;
    }
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      out.put(entry.getKey(), entry.getValue());
      out.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
    }
    return out;
  }
}
