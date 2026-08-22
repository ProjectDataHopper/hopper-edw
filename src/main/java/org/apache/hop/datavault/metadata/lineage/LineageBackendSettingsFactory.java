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
package org.apache.hop.datavault.metadata.lineage;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;

/** Instantiates {@link ILineageBackendSettings} from a persisted type id. */
public class LineageBackendSettingsFactory implements IHopMetadataObjectFactory {

  @Override
  public Object createObject(String id, Object parentObject) throws HopException {
    return newSettings(id);
  }

  @Override
  public String getObjectId(Object object) throws HopException {
    if (!(object instanceof ILineageBackendSettings settings)) {
      throw new HopException("Not ILineageBackendSettings: " + object.getClass().getName());
    }
    return settings.getPluginId();
  }

  public static List<String> getKnownTypeIds() {
    return List.of(
        ILineageBackendSettings.PLUGIN_MARQUEZ,
        ILineageBackendSettings.PLUGIN_FILE_FOLDER,
        ILineageBackendSettings.PLUGIN_LOCAL_MODELS);
  }

  public static ILineageBackendSettings newSettings(String id) throws HopException {
    if (ILineageBackendSettings.PLUGIN_MARQUEZ.equals(id) || id == null || id.isBlank()) {
      return new MarquezBackendSettings();
    }
    if (ILineageBackendSettings.PLUGIN_FILE_FOLDER.equals(id)) {
      return new FileFolderBackendSettings();
    }
    if (ILineageBackendSettings.PLUGIN_LOCAL_MODELS.equals(id)) {
      return new LocalModelsBackendSettings();
    }
    throw new HopException("Unknown lineage backend type id '" + id + "'");
  }
}
