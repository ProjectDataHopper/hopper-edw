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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBridge;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitResult;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpsAnalyticsBusinessVaultBuilderTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void durationExpressionFollowsTheTargetDialect() {
    assertTrue(
        OpsAnalyticsBusinessVaultBuilder.durationMillis(null, "s.finished_at", "s.started_at")
            .contains("EXTRACT(EPOCH FROM"));
    assertTrue(
        OpsAnalyticsBusinessVaultBuilder.durationMillis(
                postgres(), "s.finished_at", "s.started_at")
            .contains("EXTRACT(EPOCH FROM"));
    assertTrue(
        OpsAnalyticsBusinessVaultBuilder.durationMillis(mysql(), "s.finished_at", "s.started_at")
            .contains("TIMESTAMPDIFF(MICROSECOND"));
    assertTrue(
        OpsAnalyticsBusinessVaultBuilder.durationMillis(
                databaseMetaWithPluginId("MSSQLNATIVE"), "s.finished_at", "s.started_at")
            .contains("DATEDIFF_BIG"));
    assertTrue(
        OpsAnalyticsBusinessVaultBuilder.durationMillis(
                databaseMetaWithPluginId("SNOWFLAKE"), "s.finished_at", "s.started_at")
            .contains("DATEDIFF('millisecond'"));
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    DatabaseMeta databaseMeta =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return pluginId;
          }
        };
    databaseMeta.setName(pluginId);
    return databaseMeta;
  }

  @Test
  void modelCheckHasNoErrors(@TempDir Path folder) throws Exception {
    Variables variables = new Variables();
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta target = postgres();
    target.setName(OpsAnalyticsBusinessVaultBuilder.TARGET_DATABASE);
    provider.getSerializer(DatabaseMeta.class).save(target);

    DataVaultModel vault =
        OpsAnalyticsVaultBuilder.apply(
            OpsAnalyticsSourceModelBuilder.build(), variables, provider);
    SourceToVaultSplitOptions splitOptions = new SourceToVaultSplitOptions();
    splitOptions.setOutputFolder(folder.toString());
    splitOptions.setNamePrefix(OpsAnalyticsVaultBuilder.FILE_PREFIX);
    splitOptions.setExistingFilePolicy(SourceToVaultSplitOptions.ExistingFilePolicy.REPLACE);
    SourceToVaultSplitResult split =
        SourceToVaultSplitSupport.partition(vault, splitOptions, variables, provider);
    SourceToVaultSplitSupport.write(
        split, SourceToVaultSplitOptions.ExistingFilePolicy.REPLACE, variables);

    BusinessVaultModel businessVault =
        OpsAnalyticsBusinessVaultBuilder.build(vault, split, target);
    List<ICheckResult> remarks = businessVault.check(provider, variables);
    List<ICheckResult> errors =
        remarks.stream().filter(remark -> remark.getType() == ICheckResult.TYPE_RESULT_ERROR).toList();
    assertTrue(errors.isEmpty(), () -> errors.toString());

    BvBusinessTable duration =
        (BvBusinessTable) businessVault.findTable(OpsAnalyticsBusinessVaultBuilder.DURATION_TABLE);
    assertTrue(duration.getSqlQuery().contains("{{ ref('sat_load_run') }}"));
    assertTrue(duration.getSqlQuery().contains("duration_ms"));

    BvBusinessTable latest =
        (BvBusinessTable) businessVault.findTable(OpsAnalyticsBusinessVaultBuilder.LATEST_TABLE);
    assertTrue(latest.getSqlQuery().contains("ROW_NUMBER()"));
    assertTrue(latest.getSqlQuery().contains("{{ ref('lnk_load_run_model') }}"));

    BvBridge bridge =
        (BvBridge) businessVault.findTable(OpsAnalyticsBusinessVaultBuilder.BRIDGE_TABLE);
    assertEquals(2, bridge.getDerivatives().size());
    assertFalse(bridge.getSqlQuery().contains("hop_executions"));
    assertTrue(bridge.getSqlQuery().contains("{{ ref('lnk_load_run_workflow_execution') }}"));
  }

  private static DatabaseMeta postgres() {
    return new DatabaseMeta(
        "postgres", "POSTGRESQL", "Native", "localhost", "test_ops", "5432", "test", "test");
  }

  private static DatabaseMeta mysql() {
    return new DatabaseMeta(
        "mysql", "MYSQL", "Native", "localhost", "test_ops", "3306", "test", "test");
  }
}
