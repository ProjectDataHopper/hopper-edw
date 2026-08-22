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
package org.hopper.edw.catalog.harvest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.hopper.edw.catalog.discovery.PhysicalSourceRef;
import org.hopper.edw.catalog.discovery.RecordDefinitionDiscoveryService;
import org.hopper.edw.catalog.discovery.RecordDefinitionPhysicalRefSupport;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Discovers live field layouts for harvest subjects, batching all DATABASE subjects that share a
 * Hop connection onto a single JDBC session.
 */
public final class SchemaHarvestConnectionBatcher {

  private static final ILoggingObject LOGGING_OBJECT =
      new SimpleLoggingObject("SchemaHarvest", LoggingObjectType.GENERAL, null);

  private SchemaHarvestConnectionBatcher() {}

  public record DiscoveryOutcome(
      List<SourceField> fields,
      List<org.hopper.edw.datavault.metadata.database.DiscoveredForeignKey> foreignKeys,
      String errorMessage,
      boolean connectionBatched) {

    public DiscoveryOutcome(
        List<SourceField> fields, String errorMessage, boolean connectionBatched) {
      this(fields, List.of(), errorMessage, connectionBatched);
    }
  }

  /**
   * Discover all subjects. DATABASE subjects are grouped by connection name; each group opens one
   * {@link Database} and discovers tables serially on that connection. Other source types use
   * {@link RecordDefinitionDiscoveryService} per subject.
   *
   * @return map of subjectKey → outcome (same order not guaranteed; callers index by key)
   */
  public static Map<String, DiscoveryOutcome> discoverAll(
      List<SchemaHarvestSubjectResolver.ResolvedSubject> subjects,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    Map<String, DiscoveryOutcome> outcomes = new LinkedHashMap<>();
    if (subjects == null || subjects.isEmpty()) {
      return outcomes;
    }

    List<SchemaHarvestSubjectResolver.ResolvedSubject> nonDatabase = new ArrayList<>();
    Map<String, List<SchemaHarvestSubjectResolver.ResolvedSubject>> byConnection =
        new LinkedHashMap<>();

    for (SchemaHarvestSubjectResolver.ResolvedSubject subject : subjects) {
      if (subject == null) {
        continue;
      }
      if (subject.definition() == null) {
        outcomes.put(
            subject.subjectKey(),
            new DiscoveryOutcome(List.of(), "Record definition not found in catalog", false));
        continue;
      }
      DvSourceType sourceType =
          RecordDefinitionPhysicalRefSupport.resolveSourceType(subject.definition());
      if (sourceType == DvSourceType.DATABASE) {
        String connectionName = resolveDatabaseConnectionName(subject.definition());
        if (Utils.isEmpty(connectionName)) {
          outcomes.put(
              subject.subjectKey(),
              new DiscoveryOutcome(
                  List.of(), "Database connection name missing on physical table", false));
        } else {
          byConnection.computeIfAbsent(connectionName, k -> new ArrayList<>()).add(subject);
        }
      } else {
        nonDatabase.add(subject);
      }
    }

    for (Map.Entry<String, List<SchemaHarvestSubjectResolver.ResolvedSubject>> entry :
        byConnection.entrySet()) {
      discoverDatabasePartition(
          entry.getKey(), entry.getValue(), variables, metadataProvider, outcomes);
    }

    for (SchemaHarvestSubjectResolver.ResolvedSubject subject : nonDatabase) {
      outcomes.put(subject.subjectKey(), discoverNonDatabase(subject, variables, metadataProvider));
    }

    return outcomes;
  }

  /** Package-visible for unit tests: grouping key for a subject list. */
  static Map<String, Integer> countByConnection(
      List<SchemaHarvestSubjectResolver.ResolvedSubject> subjects) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    if (subjects == null) {
      return counts;
    }
    for (SchemaHarvestSubjectResolver.ResolvedSubject subject : subjects) {
      if (subject == null || subject.definition() == null) {
        continue;
      }
      DvSourceType sourceType =
          RecordDefinitionPhysicalRefSupport.resolveSourceType(subject.definition());
      if (sourceType != DvSourceType.DATABASE) {
        continue;
      }
      String connectionName = resolveDatabaseConnectionName(subject.definition());
      if (Utils.isEmpty(connectionName)) {
        continue;
      }
      counts.merge(connectionName, 1, Integer::sum);
    }
    return counts;
  }

  private static void discoverDatabasePartition(
      String connectionName,
      List<SchemaHarvestSubjectResolver.ResolvedSubject> subjects,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Map<String, DiscoveryOutcome> outcomes)
      throws HopException {
    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      String msg = "Error loading database connection '" + connectionName + "': " + e.getMessage();
      for (SchemaHarvestSubjectResolver.ResolvedSubject subject : subjects) {
        outcomes.put(subject.subjectKey(), new DiscoveryOutcome(List.of(), msg, false));
      }
      return;
    }
    if (databaseMeta == null) {
      String msg = "Database connection '" + connectionName + "' was not found";
      for (SchemaHarvestSubjectResolver.ResolvedSubject subject : subjects) {
        outcomes.put(subject.subjectKey(), new DiscoveryOutcome(List.of(), msg, false));
      }
      return;
    }

    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      for (SchemaHarvestSubjectResolver.ResolvedSubject subject : subjects) {
        outcomes.put(subject.subjectKey(), discoverOneDatabaseTable(db, subject, variables));
      }
    } catch (Exception e) {
      String msg =
          "Error connecting to database '"
              + connectionName
              + "': "
              + Const.NVL(e.getMessage(), e.getClass().getSimpleName());
      for (SchemaHarvestSubjectResolver.ResolvedSubject subject : subjects) {
        outcomes.putIfAbsent(subject.subjectKey(), new DiscoveryOutcome(List.of(), msg, false));
      }
    }
  }

  private static DiscoveryOutcome discoverOneDatabaseTable(
      Database db, SchemaHarvestSubjectResolver.ResolvedSubject subject, IVariables variables) {
    try {
      PhysicalSourceRef physicalRef =
          RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(subject.definition());
      String schemaName = Const.NVL(physicalRef.getSchemaName(), "");
      String tableName = Const.NVL(physicalRef.getTableName(), "").trim();
      if (Utils.isEmpty(tableName)) {
        return new DiscoveryOutcome(List.of(), "Table name is required", true);
      }
      List<SourceField> fields =
          DvDatabaseSourceImportSupport.importFieldsFromTable(db, variables, schemaName, tableName);
      if (fields == null || fields.isEmpty()) {
        return new DiscoveryOutcome(
            List.of(), List.of(), "No columns discovered for table " + tableName, true);
      }
      List<org.hopper.edw.datavault.metadata.database.DiscoveredForeignKey> foreignKeys = List.of();
      try {
        foreignKeys =
            org.hopper.edw.datavault.metadata.database.DatabaseForeignKeyDiscoverySupport
                .discoverImportedForeignKeys(db, db.getDatabaseMeta(), schemaName, tableName);
      } catch (Exception fkEx) {
        // FK discovery is best-effort; column harvest still succeeds.
        foreignKeys = List.of();
      }
      return new DiscoveryOutcome(
          fields, foreignKeys != null ? foreignKeys : List.of(), null, true);
    } catch (Exception e) {
      return new DiscoveryOutcome(
          List.of(), List.of(), Const.NVL(e.getMessage(), e.getClass().getSimpleName()), true);
    }
  }

  private static DiscoveryOutcome discoverNonDatabase(
      SchemaHarvestSubjectResolver.ResolvedSubject subject,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    RecordDefinition definition = subject.definition();
    if (!RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition)) {
      return new DiscoveryOutcome(
          List.of(), "Source type does not support live metadata discovery", false);
    }
    try {
      DvSourceType sourceType = RecordDefinitionPhysicalRefSupport.resolveSourceType(definition);
      PhysicalSourceRef physicalRef =
          RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition);
      RecordDefinitionDiscoveryService.DiscoveryResult discovery =
          RecordDefinitionDiscoveryService.discover(
              sourceType, physicalRef, variables, metadataProvider);
      if (discovery.fields() == null || discovery.fields().isEmpty()) {
        return new DiscoveryOutcome(List.of(), "No fields discovered", false);
      }
      return new DiscoveryOutcome(discovery.fields(), null, false);
    } catch (Exception e) {
      return new DiscoveryOutcome(
          List.of(), Const.NVL(e.getMessage(), e.getClass().getSimpleName()), false);
    }
  }

  private static String resolveDatabaseConnectionName(RecordDefinition definition) {
    if (definition == null || definition.getPhysicalTable() == null) {
      return null;
    }
    return Const.NVL(definition.getPhysicalTable().getDatabaseMetaName(), "").trim();
  }

  /** Test helper: connection partition count for a list of connection names. */
  public static int partitionCount(List<String> connectionNames) {
    if (connectionNames == null || connectionNames.isEmpty()) {
      return 0;
    }
    return (int)
        connectionNames.stream()
            .filter(Objects::nonNull)
            .map(n -> n.trim().toLowerCase(Locale.ROOT))
            .filter(n -> !n.isEmpty())
            .distinct()
            .count();
  }
}
