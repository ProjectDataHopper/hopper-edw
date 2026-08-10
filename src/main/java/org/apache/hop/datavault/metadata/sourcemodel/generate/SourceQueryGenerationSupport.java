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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.virtualization.sql.SourceModelFreeSqlTableSupport;

/** Resolves participants and effective generation mode for a {@link SourceQuery}. */
public final class SourceQueryGenerationSupport {

  private SourceQueryGenerationSupport() {}

  /**
   * Ordered unique table names for a query.
   *
   * <p>Visual modes: driving table, joins, projection tables. Free SQL: tables referenced in
   * FROM/JOIN (when parseable), falling back to visual fields if free SQL is empty/unparseable.
   */
  public static List<String> participantTableNames(SourceModel model, SourceQuery query) {
    if (query != null
        && query.resolveGenerationMode() == SourceQueryGenerationMode.FREE_SQL
        && !Utils.isEmpty(query.getFreeSql())) {
      List<String> fromSql =
          SourceModelFreeSqlTableSupport.referencedTableNames(model, query.getFreeSql());
      if (!fromSql.isEmpty()) {
        return fromSql;
      }
    }
    return participantTableNames(query);
  }

  /** Ordered unique table names: driving table first, then each join table (visual query only). */
  public static List<String> participantTableNames(SourceQuery query) {
    Set<String> names = new LinkedHashSet<>();
    if (query == null) {
      return List.of();
    }
    if (!Utils.isEmpty(query.getDrivingTableName())) {
      names.add(query.getDrivingTableName().trim());
    }
    for (SourceQueryJoin join : query.getJoins()) {
      if (join != null && !Utils.isEmpty(join.getTableName())) {
        names.add(join.getTableName().trim());
      }
    }
    for (SourceQueryColumn column : query.getColumns()) {
      if (column != null && !Utils.isEmpty(column.getTableName())) {
        names.add(column.getTableName().trim());
      }
    }
    return new ArrayList<>(names);
  }

  public static List<SourceTable> resolveParticipants(SourceModel model, SourceQuery query) {
    List<SourceTable> tables = new ArrayList<>();
    if (model == null || query == null) {
      return tables;
    }
    for (String name : participantTableNames(model, query)) {
      SourceTable table = model.findTable(name);
      if (table != null) {
        tables.add(table);
      }
    }
    return tables;
  }

  /**
   * Effective mode after resolving {@link SourceQueryGenerationMode#AUTO}: SQL when all
   * participants are DATABASE tables on one connection; otherwise PIPELINE.
   */
  public static SourceQueryGenerationMode resolveEffectiveMode(
      SourceModel model, SourceQuery query) {
    SourceQueryGenerationMode mode =
        query != null ? query.resolveGenerationMode() : SourceQueryGenerationMode.AUTO;
    if (mode == SourceQueryGenerationMode.FREE_SQL) {
      return SourceQueryGenerationMode.FREE_SQL;
    }
    if (mode != SourceQueryGenerationMode.AUTO) {
      return mode;
    }
    return canGenerateSingleConnectionSql(model, query)
        ? SourceQueryGenerationMode.SQL
        : SourceQueryGenerationMode.PIPELINE;
  }

  public static boolean canGenerateSingleConnectionSql(SourceModel model, SourceQuery query) {
    List<SourceTable> participants = resolveParticipants(model, query);
    if (participants.isEmpty()) {
      return false;
    }
    String connection = null;
    for (SourceTable table : participants) {
      if (table.resolvePhysicalType() != DvSourceType.DATABASE) {
        return false;
      }
      String db = table.getDatabaseName();
      if (Utils.isEmpty(db)) {
        return false;
      }
      if (connection == null) {
        connection = db.trim();
      } else if (!connection.equals(db.trim())) {
        return false;
      }
    }
    return true;
  }

  public static String resolveSharedDatabaseName(SourceModel model, SourceQuery query) {
    List<SourceTable> participants = resolveParticipants(model, query);
    if (participants.isEmpty()) {
      return null;
    }
    String db = participants.get(0).getDatabaseName();
    return Utils.isEmpty(db) ? null : db.trim();
  }
}
