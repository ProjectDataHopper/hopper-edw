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
package org.apache.hop.datavault.openlineage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.lineage.BvModelLineageCollector;
import org.apache.hop.datavault.lineage.DmModelLineageCollector;
import org.apache.hop.datavault.lineage.DvModelLineageCollector;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Collects model lineage and exports OpenLineage events to file and/or HTTP. */
public final class OpenLineageExportService {

  private OpenLineageExportService() {}

  public static OpenLineageExportResult exportFromGroup(
      String resourceGroupName,
      OpenLineageExportOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    if (Utils.isEmpty(resourceGroupName)) {
      throw new HopException("Resource definition group is required for OpenLineage export");
    }
    if (options == null) {
      throw new HopException("OpenLineage export options are required");
    }
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(resourceGroupName, metadataProvider);
    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
    return exportFromModels(models, options, variables, metadataProvider, log);
  }

  public static OpenLineageExportResult exportFromModels(
      ValidationModels models,
      OpenLineageExportOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    if (models == null) {
      throw new HopException("Resolved models are required for OpenLineage export");
    }
    if (options == null) {
      throw new HopException("OpenLineage export options are required");
    }

    String exportRunId = UUID.randomUUID().toString();
    OpenLineageExportResult result = new OpenLineageExportResult(exportRunId);
    List<ObjectNode> events = new ArrayList<>();

    // Prefer a catalog connection from the first loaded model for source physical resolution.
    String defaultCatalog = null;
    if (!models.dataVaultModels().isEmpty() && models.dataVaultModels().get(0) != null) {
      defaultCatalog = models.dataVaultModels().get(0).catalogConnection();
    } else if (!models.businessVaultModels().isEmpty()
        && models.businessVaultModels().get(0) != null) {
      defaultCatalog = models.businessVaultModels().get(0).catalogConnection();
    } else if (!models.dimensionalModels().isEmpty() && models.dimensionalModels().get(0) != null) {
      defaultCatalog = models.dimensionalModels().get(0).catalogConnection();
    }

    if (options.isIncludeDv()) {
      for (ValidationModels.LoadedDataVaultModel loaded : models.dataVaultModels()) {
        if (loaded == null || loaded.model() == null) {
          continue;
        }
        LineageSnapshot snapshot =
            DvModelLineageCollector.collect(
                loaded.model(), variables, metadataProvider, loaded.catalogConnection());
        OpenLineageLocationContext locationContext =
            new OpenLineageLocationContext(variables, metadataProvider, loaded.catalogConnection());
        events.addAll(
            OpenLineageSnapshotMapper.toRunEvents(
                snapshot,
                options.getJobNamespace(),
                options.getDatasetNamespace(),
                options.isIncludeColumnLineage(),
                exportRunId,
                locationContext));
      }
    }
    if (options.isIncludeBv()) {
      for (ValidationModels.LoadedBusinessVaultModel loaded : models.businessVaultModels()) {
        if (loaded == null || loaded.model() == null) {
          continue;
        }
        LineageSnapshot snapshot = BvModelLineageCollector.collect(loaded.model(), variables);
        OpenLineageLocationContext locationContext =
            new OpenLineageLocationContext(
                variables,
                metadataProvider,
                !Utils.isEmpty(loaded.catalogConnection())
                    ? loaded.catalogConnection()
                    : defaultCatalog);
        events.addAll(
            OpenLineageSnapshotMapper.toRunEvents(
                snapshot,
                options.getJobNamespace(),
                options.getDatasetNamespace(),
                options.isIncludeColumnLineage(),
                exportRunId,
                locationContext));
      }
    }
    if (options.isIncludeDm()) {
      for (ValidationModels.LoadedDimensionalModel loaded : models.dimensionalModels()) {
        if (loaded == null || loaded.model() == null) {
          continue;
        }
        LineageSnapshot snapshot =
            DmModelLineageCollector.collect(loaded.model(), variables, metadataProvider);
        OpenLineageLocationContext locationContext =
            new OpenLineageLocationContext(
                variables,
                metadataProvider,
                !Utils.isEmpty(loaded.catalogConnection())
                    ? loaded.catalogConnection()
                    : defaultCatalog);
        events.addAll(
            OpenLineageSnapshotMapper.toRunEvents(
                snapshot,
                options.getJobNamespace(),
                options.getDatasetNamespace(),
                options.isIncludeColumnLineage(),
                exportRunId,
                locationContext));
      }
    }
    // Cross-model: copy output schemas onto matching input datasets (DV hub → BV, dim → fact, …).
    OpenLineageSnapshotMapper.enrichInputSchemasFromOutputs(events);

    if (options.isIncludeOperationalMetrics()) {
      try {
        OpsLineageEnricher.enrich(events, options, variables, metadataProvider, log, result);
      } catch (Exception e) {
        result.addWarning("Operational metrics enrichment failed: " + e.getMessage());
        if (log != null) {
          log.logBasic("OpenLineage ops enrichment skipped: " + e.getMessage());
        }
      }
    }

    for (int i = 0; i < events.size(); i++) {
      result.incrementEventCount();
    }

    OpenLineageDestinationMode mode = options.getDestinationMode();
    if (mode == null) {
      mode = OpenLineageDestinationMode.FILE;
    }

    if (mode.writesFiles()) {
      OpenLineageFileWriter.writeEvents(options.getOutputFolder(), events, result);
    }

    if (mode.postsHttp()) {
      OpenLineageHttpClient client =
          new OpenLineageHttpClient(
              options.getHttpUrl(),
              options.getHttpApiKeyHeader(),
              options.getHttpApiKey(),
              options.getTimeoutMs());
      for (ObjectNode event : events) {
        try {
          client.postEvent(event);
          result.incrementHttpPosted();
        } catch (Exception e) {
          result.incrementHttpFailed();
          result.addError(e.getMessage());
          if (log != null) {
            log.logError("OpenLineage HTTP post failed: " + e.getMessage());
          }
          if (options.isFailOnHttpError()) {
            throw new HopException("OpenLineage HTTP export failed", e);
          }
        }
      }
    }

    if (mode.writesFiles()) {
      OpenLineageFileWriter.writeSummary(options.getOutputFolder(), result);
    }

    if (log != null) {
      log.logBasic(
          "OpenLineage export complete: events="
              + result.getEventCount()
              + ", files="
              + result.getFilesWritten()
              + ", httpPosted="
              + result.getHttpPosted()
              + ", httpFailed="
              + result.getHttpFailed());
    }
    return result;
  }

  /** Map a single pre-built snapshot (unit tests / CLI helpers). */
  public static OpenLineageExportResult exportSnapshot(
      LineageSnapshot snapshot, OpenLineageExportOptions options, ILogChannel log)
      throws HopException {
    String exportRunId = UUID.randomUUID().toString();
    OpenLineageExportResult result = new OpenLineageExportResult(exportRunId);
    List<ObjectNode> events =
        OpenLineageSnapshotMapper.toRunEvents(
            snapshot,
            options.getJobNamespace(),
            options.getDatasetNamespace(),
            options.isIncludeColumnLineage(),
            exportRunId,
            null);
    events.forEach(e -> result.incrementEventCount());

    OpenLineageDestinationMode mode =
        options.getDestinationMode() != null
            ? options.getDestinationMode()
            : OpenLineageDestinationMode.FILE;
    if (mode.writesFiles()) {
      OpenLineageFileWriter.writeEvents(options.getOutputFolder(), events, result);
      OpenLineageFileWriter.writeSummary(options.getOutputFolder(), result);
    }
    if (mode.postsHttp()) {
      OpenLineageHttpClient client =
          new OpenLineageHttpClient(
              options.getHttpUrl(),
              options.getHttpApiKeyHeader(),
              options.getHttpApiKey(),
              options.getTimeoutMs());
      for (ObjectNode event : events) {
        try {
          client.postEvent(event);
          result.incrementHttpPosted();
        } catch (Exception e) {
          result.incrementHttpFailed();
          result.addError(e.getMessage());
          if (options.isFailOnHttpError()) {
            throw new HopException("OpenLineage HTTP export failed", e);
          }
        }
      }
    }
    if (log != null) {
      log.logBasic("OpenLineage snapshot export: events=" + result.getEventCount());
    }
    return result;
  }
}
