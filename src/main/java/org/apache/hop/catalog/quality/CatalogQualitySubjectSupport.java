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
package org.apache.hop.catalog.quality;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Loads catalog record definitions that can be measured for data quality. */
public final class CatalogQualitySubjectSupport {

  private CatalogQualitySubjectSupport() {}

  public static String subjectKey(RecordDefinition definition) {
    if (definition == null || definition.getKey() == null) {
      return "?";
    }
    return definition.getKey().getNamespace() + "/" + definition.getKey().getName();
  }

  public static List<RecordDefinition> loadByKeys(
      String catalogConnection,
      List<String> namespaceSlashNameKeys,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<RecordDefinition> result = new ArrayList<>();
    if (namespaceSlashNameKeys == null) {
      return result;
    }
    for (String keyText : namespaceSlashNameKeys) {
      if (Utils.isEmpty(keyText)) {
        continue;
      }
      int slash = keyText.lastIndexOf('/');
      if (slash <= 0 || slash >= keyText.length() - 1) {
        throw new HopException("Record definition key must be 'namespace/name', got: " + keyText);
      }
      RecordDefinitionKey key =
          new RecordDefinitionKey(keyText.substring(0, slash), keyText.substring(slash + 1));
      RecordDefinition definition =
          RecordDefinitionRegistry.getInstance()
              .read(catalogConnection, key, variables, metadataProvider);
      if (definition != null) {
        result.add(definition);
      }
    }
    return result;
  }

  public static List<RecordDefinition> loadByNamespacePrefix(
      String catalogConnection,
      String namespacePrefix,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<RecordDefinition> result = new ArrayList<>();
    if (Utils.isEmpty(namespacePrefix)) {
      return result;
    }
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNamespacePrefix(namespacePrefix);
    List<RecordDefinitionRef> refs =
        RecordDefinitionRegistry.getInstance()
            .list(catalogConnection, query, variables, metadataProvider);
    for (RecordDefinitionRef ref : refs) {
      if (ref == null || ref.getKey() == null) {
        continue;
      }
      RecordDefinition definition =
          RecordDefinitionRegistry.getInstance()
              .read(catalogConnection, ref.getKey(), variables, metadataProvider);
      if (definition != null) {
        result.add(definition);
      }
    }
    return result;
  }

  public static boolean hasPhysicalLocation(RecordDefinition definition) {
    if (definition == null) {
      return false;
    }
    PhysicalTableRef table = definition.getPhysicalTable();
    if (table != null
        && !Utils.isEmpty(table.getDatabaseMetaName())
        && !Utils.isEmpty(table.getTableName())) {
      return true;
    }
    return definition.getPhysicalFile() != null
        || definition.getPhysicalIcebergTable() != null
        || (definition.getDvSource() != null);
  }
}
