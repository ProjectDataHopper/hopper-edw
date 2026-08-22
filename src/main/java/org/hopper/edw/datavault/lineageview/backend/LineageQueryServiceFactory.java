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
package org.hopper.edw.datavault.lineageview.backend;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.lineageview.backend.marquez.MarquezLineageQueryService;
import org.hopper.edw.datavault.metadata.lineage.FileFolderBackendSettings;
import org.hopper.edw.datavault.metadata.lineage.ILineageBackendSettings;
import org.hopper.edw.datavault.metadata.lineage.LineageBackendMeta;
import org.hopper.edw.datavault.metadata.lineage.LocalModelsBackendSettings;
import org.hopper.edw.datavault.metadata.lineage.MarquezBackendSettings;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Opens a headless {@link ILineageQueryService} for a lineage-backend metadata object. */
public final class LineageQueryServiceFactory {

  private LineageQueryServiceFactory() {}

  public static ILineageQueryService open(
      LineageBackendMeta meta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    if (meta == null) {
      throw new HopException("Lineage backend metadata is required");
    }
    ILineageBackendSettings settings = meta.getSettingsOrDefault();
    if (settings instanceof MarquezBackendSettings marquez) {
      String baseUrl = resolve(variables, marquez.getBaseUrl());
      int timeout = MarquezBackendSettings.parseTimeout(resolve(variables, marquez.getTimeoutMs()));
      return new MarquezLineageQueryService(
          baseUrl, resolve(variables, marquez.getApiKeyHeader()), marquez.getApiKey(), timeout);
    }
    if (settings instanceof FileFolderBackendSettings folder) {
      return new FileFolderLineageQueryService(resolve(variables, folder.getFolder()));
    }
    if (settings instanceof LocalModelsBackendSettings local) {
      return new LocalModelsLineageQueryService(
          variables,
          metadataProvider,
          local.getResourceDefinitionGroup(),
          resolve(variables, local.getJobNamespace()),
          resolve(variables, local.getDatasetNamespace()));
    }
    throw new HopException("Unsupported lineage backend: " + settings.getPluginId());
  }

  private static String resolve(IVariables variables, String value) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
