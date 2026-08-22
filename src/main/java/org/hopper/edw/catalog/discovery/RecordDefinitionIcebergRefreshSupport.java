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
package org.hopper.edw.catalog.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.SourceField;

/**
 * Merges live Iceberg schema discovery into catalog contracts while preserving project-scoped
 * length and precision metadata that Iceberg does not provide.
 */
public final class RecordDefinitionIcebergRefreshSupport {

  private RecordDefinitionIcebergRefreshSupport() {}

  public static List<SourceField> mergeDiscoveredFields(
      List<SourceField> storedFields, List<SourceField> discoveredFields) {
    Map<String, SourceField> storedByName = indexByName(storedFields);
    List<SourceField> merged = new ArrayList<>();
    if (discoveredFields == null) {
      return merged;
    }
    for (SourceField discovered : discoveredFields) {
      if (discovered == null || Utils.isEmpty(discovered.getName())) {
        continue;
      }
      SourceField stored = storedByName.get(discovered.getName().trim());
      merged.add(stored != null ? mergeExistingField(stored, discovered) : copyField(discovered));
    }
    return merged;
  }

  private static SourceField mergeExistingField(SourceField stored, SourceField discovered) {
    SourceField merged = copyField(discovered);
    merged.setLength(Const.NVL(stored.getLength(), ""));
    merged.setPrecision(Const.NVL(stored.getPrecision(), ""));
    if (!Utils.isEmpty(stored.getDescription())) {
      merged.setDescription(stored.getDescription());
    }
    return merged;
  }

  private static SourceField copyField(SourceField source) {
    SourceField field = new SourceField(source.getName());
    field.setDescription(Const.NVL(source.getDescription(), ""));
    field.setSourceDataType(source.getSourceDataType());
    field.setHopType(source.getHopType());
    field.setLength(Const.NVL(source.getLength(), ""));
    field.setPrecision(Const.NVL(source.getPrecision(), ""));
    return field;
  }

  private static Map<String, SourceField> indexByName(List<SourceField> fields) {
    Map<String, SourceField> indexed = new LinkedHashMap<>();
    if (fields == null) {
      return indexed;
    }
    for (SourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      indexed.put(field.getName().trim(), field);
    }
    return indexed;
  }
}
