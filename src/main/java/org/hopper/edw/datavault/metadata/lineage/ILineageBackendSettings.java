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
package org.hopper.edw.datavault.metadata.lineage;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.lineageview.backend.LineageBackendKind;

/** Nested settings for a lineage backend connection. */
@HopMetadataObject(objectFactory = LineageBackendSettingsFactory.class)
public interface ILineageBackendSettings {

  String PLUGIN_MARQUEZ = "MARQUEZ";
  String PLUGIN_FILE_FOLDER = "FILE_FOLDER";
  String PLUGIN_LOCAL_MODELS = "LOCAL_MODELS";

  String getPluginId();

  void setPluginId(String pluginId);

  LineageBackendKind kind();

  LineageConnectionTestResult testConnection(
      IVariables variables, IHopMetadataProvider metadataProvider, ILogChannel log)
      throws HopException;
}
