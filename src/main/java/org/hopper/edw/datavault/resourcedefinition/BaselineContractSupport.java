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
package org.hopper.edw.datavault.resourcedefinition;

import java.util.List;
import java.util.Optional;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.versioning.CatalogVersionService;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.metadata.SourceField;

/**
 * Resolves baseline (truth) field contracts from the working catalog or an immutable catalog
 * version. Never reads model attributes or target database JDBC metadata.
 */
public final class BaselineContractSupport {

  private BaselineContractSupport() {}

  public static Optional<RecordDefinition> loadBaselineDefinition(
      String catalogConnection,
      RecordDefinitionKey key,
      String baselineVersionTag,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnection) || key == null) {
      return Optional.empty();
    }
    if (!Utils.isEmpty(baselineVersionTag)) {
      return CatalogVersionService.readDefinition(
          catalogConnection, baselineVersionTag.trim(), key, variables, metadataProvider);
    }
    RecordDefinition working =
        RecordDefinitionRegistry.getInstance()
            .read(catalogConnection, key, variables, metadataProvider);
    return Optional.ofNullable(working);
  }

  public static List<SourceField> fieldsOf(RecordDefinition definition) {
    try {
      return DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    } catch (Exception e) {
      return List.of();
    }
  }

  public static SourceField findField(List<SourceField> fields, String fieldName) {
    if (fields == null || Utils.isEmpty(fieldName)) {
      return null;
    }
    for (SourceField field : fields) {
      if (field != null && field.getName() != null && fieldName.equalsIgnoreCase(field.getName())) {
        return field;
      }
    }
    return null;
  }

  public static int parsePositiveInt(String value) {
    if (Utils.isEmpty(value)) {
      return -1;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
