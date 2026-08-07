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
package org.apache.hop.datavault.workflow.actions.updateresourcegroup;

import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.harvest.SchemaHarvestModelCheckSupport;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.resourcedefinition.ParallelValidationSupport;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelUpdatePlanner.ModelUpdateJob;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Parallel model-check helper for {@link ActionUpdateResourceDefinitionGroup}: validates every
 * planned DV/BV/DM model with bounded concurrency (VPN-friendly) before the update wave.
 */
public final class ResourceGroupModelValidationSupport {

  private ResourceGroupModelValidationSupport() {}

  public record ModelCheckOutcome(
      ModelUpdateJob job, List<ICheckResult> remarks, Exception failure) {

    public boolean hasError() {
      if (failure != null) {
        return true;
      }
      if (remarks == null) {
        return false;
      }
      return remarks.stream()
          .anyMatch(r -> r != null && r.getType() == ICheckResult.TYPE_RESULT_ERROR);
    }

    public int errorCount() {
      if (failure != null) {
        return 1;
      }
      if (remarks == null) {
        return 0;
      }
      return (int)
          remarks.stream()
              .filter(r -> r != null && r.getType() == ICheckResult.TYPE_RESULT_ERROR)
              .count();
    }
  }

  /** Harvest reuse settings for parallel model checks. */
  public record HarvestReuseSettings(
      boolean preferHarvest,
      String harvestRunId,
      String harvestHistoryDatabase,
      String harvestHistorySchema,
      String harvestCatalogConnection,
      String harvestResourceGroup) {

    public static HarvestReuseSettings disabled() {
      return new HarvestReuseSettings(false, null, null, null, null, null);
    }
  }

  /**
   * Checks each model with at most {@code parallelism} concurrent tasks. Each task uses its own
   * {@link DvModelCheckOptions} session (no shared JDBC cache across threads). When harvest reuse
   * is enabled, DISCOVERED layouts are loaded once and pre-seeded into each session cache.
   */
  public static List<ModelCheckOutcome> checkModels(
      List<ModelUpdateJob> jobs,
      boolean detailedDataTypeChecking,
      int parallelism,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return checkModels(
        jobs,
        detailedDataTypeChecking,
        parallelism,
        HarvestReuseSettings.disabled(),
        variables,
        metadataProvider,
        null);
  }

  public static List<ModelCheckOutcome> checkModels(
      List<ModelUpdateJob> jobs,
      boolean detailedDataTypeChecking,
      int parallelism,
      HarvestReuseSettings harvestReuse,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    if (jobs == null || jobs.isEmpty()) {
      return List.of();
    }
    Map<String, IRowMeta> harvestedLayouts = Map.of();
    if (harvestReuse != null && harvestReuse.preferHarvest() && detailedDataTypeChecking) {
      DvModelCheckOptions probe = DvModelCheckOptions.forCheckRun();
      probe.setDetailedDataTypeChecking(true);
      probe.setPreferHarvestForLiveFields(true);
      probe.setHarvestRunId(harvestReuse.harvestRunId());
      probe.setHarvestHistoryDatabase(harvestReuse.harvestHistoryDatabase());
      probe.setHarvestHistorySchema(harvestReuse.harvestHistorySchema());
      probe.setHarvestCatalogConnection(harvestReuse.harvestCatalogConnection());
      probe.setHarvestResourceGroup(harvestReuse.harvestResourceGroup());
      try {
        harvestedLayouts =
            SchemaHarvestModelCheckSupport.loadDiscoveredFieldsByDatabaseKey(
                probe, variables, metadataProvider, log);
        if (log != null) {
          if (harvestedLayouts.isEmpty()) {
            log.logBasic(
                "No harvest layouts available for model-check reuse (live discovery will be used)");
          } else {
            log.logBasic(
                "Loaded "
                    + harvestedLayouts.size()
                    + " harvested table layout(s) for parallel model-check reuse");
          }
        }
      } catch (Exception e) {
        if (log != null) {
          log.logBasic(
              "Unable to load harvest for model-check reuse: "
                  + Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
        }
        harvestedLayouts = Map.of();
      } finally {
        probe.close();
      }
    }

    int parallel = ParallelValidationSupport.resolveParallelism(parallelism);
    Map<String, IRowMeta> layoutsForTasks = harvestedLayouts;
    return ParallelValidationSupport.map(
        parallel,
        jobs,
        (job, index) ->
            checkOne(job, detailedDataTypeChecking, layoutsForTasks, variables, metadataProvider));
  }

  static ModelCheckOutcome checkOne(
      ModelUpdateJob job,
      boolean detailedDataTypeChecking,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return checkOne(job, detailedDataTypeChecking, Map.of(), variables, metadataProvider);
  }

  static ModelCheckOutcome checkOne(
      ModelUpdateJob job,
      boolean detailedDataTypeChecking,
      Map<String, IRowMeta> harvestedLayouts,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (job == null) {
      return new ModelCheckOutcome(null, List.of(), null);
    }
    try {
      List<ICheckResult> remarks =
          switch (job.layer()) {
            case DATA_VAULT ->
                checkDataVault(
                    job.modelFile(),
                    detailedDataTypeChecking,
                    harvestedLayouts,
                    variables,
                    metadataProvider);
            case BUSINESS_VAULT -> checkBusinessVault(job.modelFile(), variables, metadataProvider);
            case DIMENSIONAL -> checkDimensional(job.modelFile(), variables, metadataProvider);
          };
      return new ModelCheckOutcome(job, remarks != null ? remarks : List.of(), null);
    } catch (Exception e) {
      return new ModelCheckOutcome(job, List.of(), e);
    }
  }

  private static List<ICheckResult> checkDataVault(
      String modelFile,
      boolean detailedDataTypeChecking,
      Map<String, IRowMeta> harvestedLayouts,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    DataVaultModel model =
        ResourceDefinitionGroupResolver.loadDataVaultModel(modelFile, variables, metadataProvider);
    try (DvModelCheckOptions options = DvModelCheckOptions.forCheckRun()) {
      options.setDetailedDataTypeChecking(detailedDataTypeChecking);
      if (harvestedLayouts != null && !harvestedLayouts.isEmpty()) {
        SchemaHarvestModelCheckSupport.applyToCache(options.ensureCache(), harvestedLayouts);
      }
      return model.check(metadataProvider, variables, options);
    }
  }

  private static List<ICheckResult> checkBusinessVault(
      String modelFile, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    BusinessVaultModel model =
        ResourceDefinitionGroupResolver.loadBusinessVaultModel(
            modelFile, variables, metadataProvider);
    return model.check(metadataProvider, variables);
  }

  private static List<ICheckResult> checkDimensional(
      String modelFile, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    DimensionalModel model =
        ResourceDefinitionGroupResolver.loadDimensionalModel(
            modelFile, variables, metadataProvider);
    return model.check(metadataProvider, variables);
  }

  public static String formatModelLabel(ModelUpdateJob job) {
    if (job == null) {
      return "?";
    }
    return job.layer().name() + " " + Const.NVL(job.modelFile(), "?");
  }
}
