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
package org.hopper.edw.datavault.openlineage;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Per-export context for resolving physical dataset locations. */
@Getter
public final class OpenLineageLocationContext {

  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final String catalogConnection;
  private final Map<String, DatasetLocation> locationCache = new LinkedHashMap<>();

  public OpenLineageLocationContext(
      IVariables variables, IHopMetadataProvider metadataProvider, String catalogConnection) {
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.catalogConnection = catalogConnection;
  }

  public DatasetLocation cached(String key, java.util.function.Supplier<DatasetLocation> loader) {
    if (key == null) {
      return loader.get();
    }
    return locationCache.computeIfAbsent(key, ignored -> loader.get());
  }
}
