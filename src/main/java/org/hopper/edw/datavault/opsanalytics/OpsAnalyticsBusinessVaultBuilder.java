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
package org.hopper.edw.datavault.opsanalytics;

import java.util.Locale;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLinkedTable;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BvBridge;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvDvTableReference;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlMaterialization;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitModel;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitResult;

/**
 * Business Vault over the operational analytics raw vault.
 *
 * <p>Two SQL views and one bridge. Duration uses the target dialect. Latest-load uses {@code
 * ROW_NUMBER}, which Postgres, MySQL, SingleStore, and SQL Server all accept. The bridge joins the
 * load-run links to workflow execution and model. It does not assume {@code hop_executions.id}
 * equals {@code workflow_execution_id}.
 */
public final class OpsAnalyticsBusinessVaultBuilder {

  public static final String MODEL_NAME = "ops-analytics";
  public static final String TARGET_DATABASE = "ops-analytics-bv";
  public static final String TARGET_SCHEMA = "ops_bv";
  public static final String DURATION_TABLE = "bv_load_run_duration";
  public static final String LATEST_TABLE = "bv_model_latest_load";
  public static final String BRIDGE_TABLE = "bv_workflow_models";

  private static final String HUB_LOAD_RUN = "hub_load_run";
  private static final String SAT_LOAD_RUN = "sat_load_run";
  private static final String HUB_MODEL = "hub_model";
  private static final String HUB_WORKFLOW_EXECUTION = "hub_workflow_execution";
  private static final String LNK_LOAD_RUN_MODEL = "lnk_load_run_model";
  private static final String LNK_LOAD_RUN_WORKFLOW = "lnk_load_run_workflow_execution";

  private OpsAnalyticsBusinessVaultBuilder() {}

  public static BusinessVaultModel build(
      DataVaultModel vault, SourceToVaultSplitResult split, DatabaseMeta databaseMeta)
      throws HopException {
    DataVaultModel source = vaultWithHubs(vault, split);
    DvHub loadRun = requireHub(source, HUB_LOAD_RUN);
    DvHub modelHub = requireHub(source, HUB_MODEL);
    DvHub workflow = requireHub(source, HUB_WORKFLOW_EXECUTION);
    requireTable(source, SAT_LOAD_RUN);
    requireTable(source, LNK_LOAD_RUN_MODEL);
    requireTable(source, LNK_LOAD_RUN_WORKFLOW);

    String loadRunHash = loadRun.getHashKeyFieldName();
    String modelHash = modelHub.getHashKeyFieldName();
    String workflowHash = workflow.getHashKeyFieldName();
    String runId = loadRun.getBusinessKeys().get(0).getName();
    String modelType = modelHub.getBusinessKeys().get(0).getName();
    String modelName = modelHub.getBusinessKeys().get(1).getName();
    String duration = durationMillis(databaseMeta, "s.finished_at", "s.started_at");

    BusinessVaultModel businessVault = new BusinessVaultModel();
    businessVault.setName(MODEL_NAME);
    businessVault.setDescription(
        "Operational analytics Business Vault. Duration and latest load are SQL views. "
            + "The bridge relates a workflow execution to the models it loaded.");
    BusinessVaultConfiguration configuration = new BusinessVaultConfiguration();
    configuration.setTargetDatabase(TARGET_DATABASE);
    businessVault.setConfiguration(configuration);

    businessVault
        .getTables()
        .add(durationView(loadRunHash, runId, duration));
    businessVault
        .getTables()
        .add(latestView(loadRunHash, modelHash, runId, modelType, modelName, duration));
    businessVault.getTables().add(bridge(workflowHash, modelHash, loadRunHash));

    addReferences(businessVault, split);
    return businessVault;
  }

  /**
   * Milliseconds between two timestamp expressions. Postgres, including a missing plugin id, uses
   * {@code EXTRACT(EPOCH)}. MySQL and SingleStore use {@code TIMESTAMPDIFF}. SQL Server uses
   * {@code DATEDIFF_BIG}. Snowflake uses {@code DATEDIFF}.
   */
  static String durationMillis(DatabaseMeta databaseMeta, String finished, String started) {
    String pluginId =
        databaseMeta != null && !Utils.isEmpty(databaseMeta.getPluginId())
            ? databaseMeta.getPluginId().toUpperCase(Locale.ROOT)
            : "";
    String expression =
        switch (pluginId) {
          case "MYSQL", "SINGLESTORE" ->
              "TIMESTAMPDIFF(MICROSECOND, " + started + ", " + finished + ") / 1000";
          case "MSSQL", "MSSQLNATIVE" -> "DATEDIFF_BIG(millisecond, " + started + ", " + finished + ")";
          case "SNOWFLAKE" -> "DATEDIFF('millisecond', " + started + ", " + finished + ")";
          default -> "EXTRACT(EPOCH FROM (" + finished + " - " + started + ")) * 1000";
        };
    return "CASE WHEN "
        + started
        + " IS NULL OR "
        + finished
        + " IS NULL THEN NULL ELSE "
        + expression
        + " END";
  }

  private static BvBusinessTable durationView(String loadRunHash, String runId, String duration) {
    BvBusinessTable table = sqlTable(DURATION_TABLE);
    table.setDescription("Load duration in milliseconds from sat_load_run timestamps.");
    table.setSqlQuery(
        """
        SELECT h.%s, h.%s, s.started_at, s.finished_at, s.success, %s AS duration_ms
        FROM {{ ref('%s') }} s
        JOIN {{ ref('%s') }} h ON s.%s = h.%s
        """
            .formatted(
                loadRunHash,
                runId,
                duration,
                SAT_LOAD_RUN,
                HUB_LOAD_RUN,
                loadRunHash,
                loadRunHash));
    return table;
  }

  private static BvBusinessTable latestView(
      String loadRunHash,
      String modelHash,
      String runId,
      String modelType,
      String modelName,
      String duration) {
    BvBusinessTable table = sqlTable(LATEST_TABLE);
    table.setDescription("Latest load_run per model, by started_at. Not an SCD2 version.");
    table.setSqlQuery(
        """
        SELECT model_type, model_name, run_id, started_at, finished_at, success, duration_ms
        FROM (
          SELECT m.%s AS model_type, m.%s AS model_name, h.%s AS run_id,
            s.started_at, s.finished_at, s.success, %s AS duration_ms,
            ROW_NUMBER() OVER (PARTITION BY m.%s, m.%s ORDER BY s.started_at DESC) AS load_rank
          FROM {{ ref('%s') }} l
          JOIN {{ ref('%s') }} m ON l.%s = m.%s
          JOIN {{ ref('%s') }} h ON l.%s = h.%s
          JOIN {{ ref('%s') }} s ON l.%s = s.%s
        ) ranked
        WHERE load_rank = 1
        """
            .formatted(
                modelType,
                modelName,
                runId,
                duration,
                modelType,
                modelName,
                LNK_LOAD_RUN_MODEL,
                HUB_MODEL,
                modelHash,
                modelHash,
                HUB_LOAD_RUN,
                loadRunHash,
                loadRunHash,
                SAT_LOAD_RUN,
                loadRunHash,
                loadRunHash));
    return table;
  }

  private static BvBridge bridge(String workflowHash, String modelHash, String loadRunHash) {
    BvBridge bridge = new BvBridge();
    bridge.setName(BRIDGE_TABLE);
    bridge.setTableName(BRIDGE_TABLE);
    bridge.setSchemaName(TARGET_SCHEMA);
    bridge.setDescription(
        "Workflow execution hub hash to model hub hash, through the load-run links.");
    bridge.setLocation(new Point(40, 420));
    bridge
        .getDerivatives()
        .add(new BvDerivativeRef(HUB_WORKFLOW_EXECUTION, DvTableType.HUB));
    bridge.getDerivatives().add(new BvDerivativeRef(HUB_MODEL, DvTableType.HUB));
    bridge.setSqlQuery(
        """
        SELECT DISTINCT w.%s, m.%s
        FROM {{ ref('%s') }} w
        JOIN {{ ref('%s') }} m ON w.%s = m.%s
        """
            .formatted(
                workflowHash,
                modelHash,
                LNK_LOAD_RUN_WORKFLOW,
                LNK_LOAD_RUN_MODEL,
                loadRunHash,
                loadRunHash));
    return bridge;
  }

  private static BvBusinessTable sqlTable(String name) {
    BvBusinessTable table = new BvBusinessTable();
    table.setName(name);
    table.setTableName(name);
    table.setSchemaName(TARGET_SCHEMA);
    table.setMaterialization(BvSqlMaterialization.VIEW);
    table.setLocation(new Point(40, DURATION_TABLE.equals(name) ? 40 : 220));
    return table;
  }

  private static void addReferences(BusinessVaultModel businessVault, SourceToVaultSplitResult split)
      throws HopException {
    if (split == null) {
      return;
    }
    String[] names = {
      HUB_LOAD_RUN,
      SAT_LOAD_RUN,
      HUB_MODEL,
      HUB_WORKFLOW_EXECUTION,
      LNK_LOAD_RUN_MODEL,
      LNK_LOAD_RUN_WORKFLOW
    };
    int index = 0;
    for (String name : names) {
      if (fileFor(split, name, SourceToVaultSplitModel.Kind.HUB) == null
          && fileFor(split, name, SourceToVaultSplitModel.Kind.LINK) == null) {
        continue;
      }
      index++;
    }
    index = 0;
    for (SourceToVaultSplitModel.Kind kind :
        new SourceToVaultSplitModel.Kind[] {
          SourceToVaultSplitModel.Kind.HUB, SourceToVaultSplitModel.Kind.LINK
        }) {
      for (String name : names) {
        String filename = fileFor(split, name, kind);
        if (filename == null) {
          continue;
        }
        IDvTable table = findOwning(split, name, kind);
        BvDvTableReference reference = new BvDvTableReference(name, table.getTableType());
        reference.setReferencedModelFilename(filename);
        reference.setLocation(new Point(700, 40 + index * 90));
        businessVault.getDvReferences().add(reference);
        index++;
      }
    }
  }

  private static String fileFor(
      SourceToVaultSplitResult split, String tableName, SourceToVaultSplitModel.Kind kind) {
    IDvTable table = findOwning(split, tableName, kind);
    if (table == null) {
      return null;
    }
    for (SourceToVaultSplitModel model : split.getModels()) {
      if (model.getKind() == kind && model.getModel().findTable(tableName) == table) {
        return model.getFilename();
      }
    }
    return null;
  }

  private static IDvTable findOwning(
      SourceToVaultSplitResult split, String tableName, SourceToVaultSplitModel.Kind kind) {
    for (SourceToVaultSplitModel model : split.getModels()) {
      if (model.getKind() != kind || model.getModel() == null) {
        continue;
      }
      IDvTable table = model.getModel().findTable(tableName);
      if (table != null && !(table instanceof DvLinkedTable)) {
        return table;
      }
    }
    return null;
  }

  /**
   * Split generation moves tables out of the applied vault into per-file models. Read hubs from the
   * applied vault when it still holds them, otherwise from those files' in-memory models.
   */
  private static DataVaultModel vaultWithHubs(
      DataVaultModel vault, SourceToVaultSplitResult split) {
    if (vault != null && vault.findHub(HUB_LOAD_RUN) != null) {
      return vault;
    }
    DataVaultModel union = new DataVaultModel();
    if (split == null) {
      return union;
    }
    for (SourceToVaultSplitModel model : split.getModels()) {
      if (model.getModel() == null) {
        continue;
      }
      for (IDvTable table : model.getModel().getTables()) {
        if (table == null
            || table instanceof DvLinkedTable
            || Utils.isEmpty(table.getName())
            || union.findTable(table.getName()) != null) {
          continue;
        }
        union.getTables().add(table);
      }
    }
    return union;
  }

  private static DvHub requireHub(DataVaultModel vault, String name) throws HopException {
    DvHub hub = vault.findHub(name);
    if (hub == null) {
      throw new HopException("Operational analytics vault is missing " + name);
    }
    return hub;
  }

  private static void requireTable(DataVaultModel vault, String name) throws HopException {
    if (vault.findTable(name) == null) {
      throw new HopException("Operational analytics vault is missing " + name);
    }
  }
}
