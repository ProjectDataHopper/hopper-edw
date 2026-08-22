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
package org.apache.hop.datavault.resourcedefinition;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.actions.sql.ActionSql;
import org.apache.hop.workflow.actions.start.ActionStart;
import org.apache.hop.workflow.actions.success.ActionSuccess;

/**
 * Writes reviewable SQL and a dedicated Hop workflow that executes target-structure DDL for schema
 * remediation. Intended for DTAP promotion: generate once, run when each environment can accept EDW
 * structure changes.
 */
public final class RemediationDdlWorkflowSupport {

  private static final Class<?> PKG = RemediationDdlWorkflowSupport.class;

  /**
   * Default project-relative folder for generated remediation workflows and SQL scripts.
   *
   * <p>Example: {@code
   * ${PROJECT_HOME}/workflows/schema-remediation/apply-ddl_customer_email_20260729-153045.hwf}
   */
  public static final String DEFAULT_FOLDER = "${PROJECT_HOME}/workflows/schema-remediation";

  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  public record ConnectionDdl(String connectionName, List<String> statements) {
    public ConnectionDdl {
      statements = statements != null ? List.copyOf(statements) : List.of();
    }
  }

  /**
   * DDL statements for a single target table on a connection. Used to emit one SQL workflow action
   * per table.
   */
  public record TableDdl(String connectionName, String tableName, List<String> statements) {
    public TableDdl {
      statements = statements != null ? List.copyOf(statements) : List.of();
    }
  }

  public record GeneratedArtifacts(
      String folder,
      String baseName,
      String sqlFilename,
      String workflowFilename,
      int statementCount,
      List<String> connectionNames,
      List<String> tableNames,
      /** Non-null when the SQL script was written but the companion workflow could not be built. */
      String workflowError) {
    public GeneratedArtifacts {
      connectionNames = connectionNames != null ? List.copyOf(connectionNames) : List.of();
      tableNames = tableNames != null ? List.copyOf(tableNames) : List.of();
    }

    /** Compatibility constructor without table names / workflow error. */
    public GeneratedArtifacts(
        String folder,
        String baseName,
        String sqlFilename,
        String workflowFilename,
        int statementCount,
        List<String> connectionNames) {
      this(
          folder,
          baseName,
          sqlFilename,
          workflowFilename,
          statementCount,
          connectionNames,
          List.of(),
          null);
    }

    public GeneratedArtifacts(
        String folder,
        String baseName,
        String sqlFilename,
        String workflowFilename,
        int statementCount,
        List<String> connectionNames,
        List<String> tableNames) {
      this(
          folder,
          baseName,
          sqlFilename,
          workflowFilename,
          statementCount,
          connectionNames,
          tableNames,
          null);
    }

    public boolean workflowWritten() {
      return !Utils.isEmpty(workflowFilename) && Utils.isEmpty(workflowError);
    }
  }

  private RemediationDdlWorkflowSupport() {}

  public static String defaultFolder(IVariables variables) {
    if (variables == null) {
      return DEFAULT_FOLDER;
    }
    String resolved = variables.resolve(DEFAULT_FOLDER);
    return Utils.isEmpty(resolved) ? DEFAULT_FOLDER : resolved;
  }

  public static String buildBaseName(String recordName, String fieldName, LocalDateTime when) {
    LocalDateTime stamp = when != null ? when : LocalDateTime.now();
    return "apply-ddl_"
        + sanitizeToken(recordName, "record")
        + "_"
        + sanitizeToken(fieldName, "field")
        + "_"
        + TIMESTAMP.format(stamp);
  }

  public static String sanitizeToken(String value, String fallback) {
    if (Utils.isEmpty(value)) {
      return fallback;
    }
    String cleaned =
        value
            .trim()
            .replaceAll("[^A-Za-z0-9._-]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
    if (cleaned.length() > 48) {
      cleaned = cleaned.substring(0, 48);
    }
    return Utils.isEmpty(cleaned) ? fallback : cleaned.toLowerCase();
  }

  /**
   * Groups DDL statements by connection and writes a SQL script plus a Start → SQL → Success
   * workflow (one SQL action per connection). Prefer {@link #writeSqlAndWorkflowForTables} for
   * per-table actions.
   */
  public static GeneratedArtifacts writeSqlAndWorkflow(
      String folder,
      String baseName,
      List<ConnectionDdl> connectionDdls,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<TableDdl> tableDdls = new ArrayList<>();
    if (connectionDdls != null) {
      for (ConnectionDdl connectionDdl : connectionDdls) {
        if (connectionDdl == null) {
          continue;
        }
        tableDdls.add(
            new TableDdl(
                connectionDdl.connectionName(),
                connectionDdl.connectionName(),
                connectionDdl.statements()));
      }
    }
    return writeSqlAndWorkflowForTables(folder, baseName, tableDdls, variables, metadataProvider);
  }

  /**
   * Writes a consolidated SQL script plus a workflow with <strong>one SQL action per target
   * table</strong> that needs DDL.
   */
  public static GeneratedArtifacts writeSqlAndWorkflowForTables(
      String folder,
      String baseName,
      List<TableDdl> tableDdls,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(folder)) {
      folder = defaultFolder(variables);
    }
    if (Utils.isEmpty(baseName)) {
      baseName = buildBaseName("record", "field", LocalDateTime.now());
    }
    if (tableDdls == null || tableDdls.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationDdlWorkflowSupport.Error.NoDdl"));
    }

    String resolvedFolder =
        HopVfs.normalize(variables != null ? variables.resolve(folder) : folder);
    ensureFolder(resolvedFolder, variables);

    String sqlFilename = appendPath(resolvedFolder, baseName + ".sql");
    String workflowFilename = appendPath(resolvedFolder, baseName + ".hwf");

    // Always write the reviewable SQL first so a workflow classloader failure cannot lose it.
    String sqlScript = formatSqlScriptForTables(tableDdls);
    writeTextFile(sqlFilename, sqlScript, variables);

    List<String> connections = new ArrayList<>();
    List<String> tables = new ArrayList<>();
    int statements = 0;
    for (TableDdl ddl : tableDdls) {
      if (ddl == null || Utils.isEmpty(ddl.connectionName()) || ddl.statements().isEmpty()) {
        continue;
      }
      if (!connections.contains(ddl.connectionName())) {
        connections.add(ddl.connectionName());
      }
      tables.add(Utils.isEmpty(ddl.tableName()) ? ddl.connectionName() : ddl.tableName());
      statements += ddl.statements().size();
    }

    String workflowError = null;
    String writtenWorkflow = workflowFilename;
    try {
      WorkflowMeta workflowMeta =
          buildWorkflowForTables(baseName, tableDdls, variables, metadataProvider);
      workflowMeta.setFilename(workflowFilename);
      String xml = workflowMeta.getXml(variables);
      writeTextFile(workflowFilename, xml, variables);
    } catch (Throwable t) {
      // NoClassDefFoundError (missing bundled ActionSql) is an Error, not Exception.
      workflowError = t.getMessage() != null ? t.getMessage() : t.getClass().getName();
      writtenWorkflow = null;
    }

    return new GeneratedArtifacts(
        resolvedFolder,
        baseName,
        sqlFilename,
        writtenWorkflow,
        statements,
        connections,
        tables,
        workflowError);
  }

  public static List<ConnectionDdl> groupByConnection(
      Map<String, List<String>> statementsByConnection) {
    if (statementsByConnection == null || statementsByConnection.isEmpty()) {
      return List.of();
    }
    List<ConnectionDdl> result = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : statementsByConnection.entrySet()) {
      if (Utils.isEmpty(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
        continue;
      }
      List<String> cleaned = new ArrayList<>();
      for (String statement : entry.getValue()) {
        if (!Utils.isEmpty(statement)) {
          cleaned.add(statement.trim());
        }
      }
      if (!cleaned.isEmpty()) {
        result.add(new ConnectionDdl(entry.getKey(), cleaned));
      }
    }
    return result;
  }

  /**
   * Groups statements keyed by {@code connectionName + "\0" + tableName} (see {@link
   * #tableMapKey}).
   */
  public static List<TableDdl> groupByTable(Map<String, List<String>> statementsByTableKey) {
    if (statementsByTableKey == null || statementsByTableKey.isEmpty()) {
      return List.of();
    }
    List<TableDdl> result = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : statementsByTableKey.entrySet()) {
      if (Utils.isEmpty(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
        continue;
      }
      String[] parts = entry.getKey().split("\0", 2);
      String connection = parts[0];
      String table = parts.length > 1 ? parts[1] : parts[0];
      List<String> cleaned = new ArrayList<>();
      for (String statement : entry.getValue()) {
        if (!Utils.isEmpty(statement)) {
          cleaned.add(statement.trim());
        }
      }
      if (!cleaned.isEmpty()) {
        result.add(new TableDdl(connection, table, cleaned));
      }
    }
    return result;
  }

  public static String tableMapKey(String connectionName, String tableName) {
    return Const.NVL(connectionName, "") + "\0" + Const.NVL(tableName, connectionName);
  }

  public static Map<String, List<String>> newConnectionMap() {
    return new LinkedHashMap<>();
  }

  public static Map<String, List<String>> newTableMap() {
    return new LinkedHashMap<>();
  }

  public static void addStatement(
      Map<String, List<String>> statementsByConnection, String connectionName, String statement) {
    if (statementsByConnection == null
        || Utils.isEmpty(connectionName)
        || Utils.isEmpty(statement)) {
      return;
    }
    statementsByConnection
        .computeIfAbsent(connectionName, key -> new ArrayList<>())
        .add(statement.trim());
  }

  public static void addTableStatement(
      Map<String, List<String>> statementsByTableKey,
      String connectionName,
      String tableName,
      String statement) {
    if (statementsByTableKey == null || Utils.isEmpty(connectionName) || Utils.isEmpty(statement)) {
      return;
    }
    statementsByTableKey
        .computeIfAbsent(tableMapKey(connectionName, tableName), key -> new ArrayList<>())
        .add(statement.trim());
  }

  static String formatSqlScript(List<ConnectionDdl> connectionDdls) {
    List<TableDdl> tables = new ArrayList<>();
    if (connectionDdls != null) {
      for (ConnectionDdl ddl : connectionDdls) {
        if (ddl != null) {
          tables.add(new TableDdl(ddl.connectionName(), ddl.connectionName(), ddl.statements()));
        }
      }
    }
    return formatSqlScriptForTables(tables);
  }

  static String formatSqlScriptForTables(List<TableDdl> tableDdls) {
    StringBuilder builder = new StringBuilder();
    builder
        .append("-- Generated by Hop Data Vault schema remediation\n")
        .append("-- Review before running on each DTAP environment.\n")
        .append("-- Workflow companion executes one SQL action per target table.\n\n");
    if (tableDdls == null) {
      return builder.toString();
    }
    for (TableDdl ddl : tableDdls) {
      if (ddl == null || ddl.statements().isEmpty()) {
        continue;
      }
      builder
          .append("-- Connection: ")
          .append(ddl.connectionName())
          .append("  Table: ")
          .append(Const.NVL(ddl.tableName(), "?"))
          .append('\n');
      for (String statement : ddl.statements()) {
        builder.append(statement.trim());
        if (!statement.trim().endsWith(";")) {
          builder.append(';');
        }
        builder.append("\n\n");
      }
    }
    return builder.toString();
  }

  private static WorkflowMeta buildWorkflowForTables(
      String baseName,
      List<TableDdl> tableDdls,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName(baseName);
    if (metadataProvider != null) {
      workflowMeta.setMetadataProvider(metadataProvider);
    }

    ActionStart start = new ActionStart("Start");
    ActionMeta startMeta = new ActionMeta(start);
    startMeta.setLocation(50, 100);
    workflowMeta.addAction(startMeta);

    ActionMeta previous = startMeta;
    int x = 250;
    for (TableDdl ddl : tableDdls) {
      if (ddl == null || Utils.isEmpty(ddl.connectionName()) || ddl.statements().isEmpty()) {
        continue;
      }
      String tableLabel = Const.NVL(ddl.tableName(), ddl.connectionName());
      String actionName = "DDL " + sanitizeToken(tableLabel, "table");
      ActionSql sqlAction = new ActionSql(actionName);
      sqlAction.setConnection(ddl.connectionName());
      // Embed table-specific SQL so each action only affects its table.
      sqlAction.setSqlFromFile(false);
      sqlAction.setSql(joinStatements(ddl.statements()));
      sqlAction.setSendOneStatement(false);
      sqlAction.setUseVariableSubstitution(true);

      ActionMeta sqlMeta = new ActionMeta(sqlAction);
      sqlMeta.setLocation(x, 100);
      workflowMeta.addAction(sqlMeta);
      workflowMeta.addWorkflowHop(new WorkflowHopMeta(previous, sqlMeta));
      previous = sqlMeta;
      x += 200;
    }

    ActionSuccess success = new ActionSuccess("Success", "");
    ActionMeta successMeta = new ActionMeta(success);
    successMeta.setLocation(x, 100);
    workflowMeta.addAction(successMeta);
    workflowMeta.addWorkflowHop(new WorkflowHopMeta(previous, successMeta));

    return workflowMeta;
  }

  private static String joinStatements(List<String> statements) {
    StringBuilder builder = new StringBuilder();
    for (String statement : statements) {
      if (Utils.isEmpty(statement)) {
        continue;
      }
      builder.append(statement.trim());
      if (!statement.trim().endsWith(";")) {
        builder.append(';');
      }
      builder.append('\n');
    }
    return builder.toString();
  }

  private static void ensureFolder(String folder, IVariables variables) throws HopException {
    try {
      FileObject dir = HopVfs.getFileObject(folder, variables);
      if (!dir.exists()) {
        dir.createFolder();
      }
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationDdlWorkflowSupport.Error.CreateFolder", folder),
          e);
    }
  }

  private static void writeTextFile(String filename, String content, IVariables variables)
      throws HopException {
    try {
      FileObject file = HopVfs.getFileObject(filename, variables);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      try (OutputStreamWriter writer =
          new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
        writer.write(content);
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationDdlWorkflowSupport.Error.WriteFile", filename),
          e);
    }
  }

  private static String appendPath(String folder, String filename) {
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder + filename;
    }
    return folder + "/" + filename;
  }
}
