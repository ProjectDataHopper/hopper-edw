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
package org.apache.hop.datavault.config;

import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.datavault.executionmap.ExecutionMapLineStyle;
import org.apache.hop.datavault.layout.ElkLayout;

@Getter
@Setter
public class DataVaultConfig {

  public static final String HOP_CONFIG_DATA_VAULT_CONFIG_KEY = "dataVaultConfig";
  public static final int DEFAULT_MAX_UNDO_OPERATIONS = 200;
  public static final int DEFAULT_LIVE_UPDATE_POLL_INTERVAL_SECONDS = 10;

  /** Default root for named schema-remediation packages (workflow, SQL, report). */
  public static final String DEFAULT_SCHEMA_REMEDIATION_FOLDER =
      "${PROJECT_HOME}/workflows/schema-remediation";

  private boolean drawingHashKeysInModel;
  /**
   * When true (default), model check reports a hard error if the target database is not
   * Unicode-capable for EDW string storage. When false, the same probe still runs but failures are
   * warnings only (useful for migrations onto legacy MySQL/SingleStore {@code utf8} schemas).
   */
  private boolean enforceTargetUnicodeCapability = true;

  private int maxUndoOperations = DEFAULT_MAX_UNDO_OPERATIONS;
  private DmDefaultFieldNames dimensionalDefaultFieldNames = new DmDefaultFieldNames();
  private ElkLayout elkLayout = ElkLayout.createDefault();
  private boolean suppressLocalCatalogOffer;
  private String defaultPipelineRunConfiguration;
  private String defaultWorkflowRunConfiguration;
  private int liveUpdatePollIntervalSeconds = DEFAULT_LIVE_UPDATE_POLL_INTERVAL_SECONDS;
  private ExecutionMapLineStyle executionMapLineStyle = ExecutionMapLineStyle.DIRECT_CENTER;
  private String schemaRemediationFolder = DEFAULT_SCHEMA_REMEDIATION_FOLDER;

  public DataVaultConfig() {
    drawingHashKeysInModel = true;
    enforceTargetUnicodeCapability = true;
  }

  public DataVaultConfig(DataVaultConfig config) {
    this();
    drawingHashKeysInModel = config.drawingHashKeysInModel;
    enforceTargetUnicodeCapability = config.enforceTargetUnicodeCapability;
    suppressLocalCatalogOffer = config.suppressLocalCatalogOffer;
    setMaxUndoOperations(config.maxUndoOperations);
    setDimensionalDefaultFieldNames(
        new DmDefaultFieldNames(config.getDimensionalDefaultFieldNames()));
    setElkLayout(new ElkLayout(config.getElkLayout()));
    defaultPipelineRunConfiguration = config.defaultPipelineRunConfiguration;
    defaultWorkflowRunConfiguration = config.defaultWorkflowRunConfiguration;
    setLiveUpdatePollIntervalSeconds(config.getLiveUpdatePollIntervalSeconds());
    setExecutionMapLineStyle(config.getExecutionMapLineStyleOrDefault());
    setSchemaRemediationFolder(config.getSchemaRemediationFolderOrDefault());
  }

  public String getSchemaRemediationFolderOrDefault() {
    return schemaRemediationFolder != null && !schemaRemediationFolder.isBlank()
        ? schemaRemediationFolder
        : DEFAULT_SCHEMA_REMEDIATION_FOLDER;
  }

  public void setSchemaRemediationFolder(String schemaRemediationFolder) {
    this.schemaRemediationFolder =
        schemaRemediationFolder != null && !schemaRemediationFolder.isBlank()
            ? schemaRemediationFolder
            : DEFAULT_SCHEMA_REMEDIATION_FOLDER;
  }

  public DmDefaultFieldNames getDimensionalDefaultFieldNames() {
    return dimensionalDefaultFieldNames != null
        ? dimensionalDefaultFieldNames
        : new DmDefaultFieldNames();
  }

  public void setDimensionalDefaultFieldNames(DmDefaultFieldNames dimensionalDefaultFieldNames) {
    this.dimensionalDefaultFieldNames =
        dimensionalDefaultFieldNames != null
            ? dimensionalDefaultFieldNames
            : new DmDefaultFieldNames();
  }

  public int getMaxUndoOperations() {
    return maxUndoOperations > 0 ? maxUndoOperations : DEFAULT_MAX_UNDO_OPERATIONS;
  }

  public void setMaxUndoOperations(int maxUndoOperations) {
    this.maxUndoOperations =
        maxUndoOperations > 0 ? maxUndoOperations : DEFAULT_MAX_UNDO_OPERATIONS;
  }

  public int getLiveUpdatePollIntervalSeconds() {
    return liveUpdatePollIntervalSeconds > 0
        ? liveUpdatePollIntervalSeconds
        : DEFAULT_LIVE_UPDATE_POLL_INTERVAL_SECONDS;
  }

  public void setLiveUpdatePollIntervalSeconds(int liveUpdatePollIntervalSeconds) {
    this.liveUpdatePollIntervalSeconds =
        liveUpdatePollIntervalSeconds > 0
            ? liveUpdatePollIntervalSeconds
            : DEFAULT_LIVE_UPDATE_POLL_INTERVAL_SECONDS;
  }

  /** Poll interval for live model-update metrics in the Hop GUI. */
  public long resolveLiveUpdatePollIntervalMs() {
    return TimeUnit.SECONDS.toMillis(getLiveUpdatePollIntervalSeconds());
  }

  public ElkLayout getElkLayout() {
    return elkLayout != null ? elkLayout : ElkLayout.createDefault();
  }

  public void setElkLayout(ElkLayout elkLayout) {
    this.elkLayout = elkLayout != null ? elkLayout : ElkLayout.createDefault();
  }

  public ExecutionMapLineStyle getExecutionMapLineStyleOrDefault() {
    return executionMapLineStyle != null
        ? executionMapLineStyle
        : ExecutionMapLineStyle.DIRECT_CENTER;
  }

  public void setExecutionMapLineStyle(ExecutionMapLineStyle executionMapLineStyle) {
    this.executionMapLineStyle =
        executionMapLineStyle != null ? executionMapLineStyle : ExecutionMapLineStyle.DIRECT_CENTER;
  }
}
