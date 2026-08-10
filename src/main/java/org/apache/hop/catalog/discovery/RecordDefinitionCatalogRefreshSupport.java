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
package org.apache.hop.catalog.discovery;

import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.model.CatalogCustomProperty;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.iceberg.IcebergConnectionSettings;
import org.apache.hop.datavault.metadata.iceberg.IcebergTableMetadataDiscovery;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Re-discovers physical source schemas and refreshes catalog record-definition contracts. */
public final class RecordDefinitionCatalogRefreshSupport {

  private static final Class<?> PKG = RecordDefinitionCatalogRefreshSupport.class;

  public static final String CUSTOM_PROPERTY_PHYSICAL_SCHEMA_ID = "physicalSchemaId";

  private RecordDefinitionCatalogRefreshSupport() {}

  public record RefreshPreview(
      List<SourceField> discoveredFields,
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      Long physicalSchemaId) {}

  public static RefreshPreview preview(
      RecordDefinition definition, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (!RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionCatalogRefreshSupport.Error.Unsupported"));
    }

    DvSourceType sourceType = RecordDefinitionPhysicalRefSupport.resolveSourceType(definition);
    PhysicalSourceRef physicalRef =
        RecordDefinitionPhysicalRefSupport.toPhysicalSourceRef(definition);
    RecordDefinitionDiscoveryService.DiscoveryResult discovery =
        RecordDefinitionDiscoveryService.discover(
            sourceType, physicalRef, variables, metadataProvider);
    if (discovery.fields() == null || discovery.fields().isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionCatalogRefreshSupport.Error.NoFields"));
    }

    RecordDefinitionPhysicalRefSupport.requireDvSource(definition);
    List<SourceField> storedFields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    List<SourceField> discoveredFields = discovery.fields();
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff;
    List<SourceField> fieldsToApply = discoveredFields;
    if (sourceType == DvSourceType.ICEBERG) {
      diff = RecordDefinitionSchemaDiffSupport.diffTypesOnly(storedFields, discoveredFields);
      fieldsToApply =
          RecordDefinitionIcebergRefreshSupport.mergeDiscoveredFields(
              storedFields, discoveredFields);
    } else {
      diff = RecordDefinitionSchemaDiffSupport.diff(storedFields, discoveredFields);
    }
    Long physicalSchemaId = resolvePhysicalSchemaId(sourceType, physicalRef, variables);
    return new RefreshPreview(fieldsToApply, diff, physicalSchemaId);
  }

  public static void applyDiscoveredFields(
      RecordDefinition definition, List<SourceField> discoveredFields, Date discoveredAt)
      throws HopException {
    applyDiscoveredFields(definition, discoveredFields, discoveredAt, null);
  }

  public static void applyDiscoveredFields(
      RecordDefinition definition,
      List<SourceField> discoveredFields,
      Date discoveredAt,
      Long physicalSchemaId)
      throws HopException {
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionCatalogRefreshSupport.Error.MissingDefinition"));
    }
    if (discoveredFields == null || discoveredFields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionCatalogRefreshSupport.Error.NoFields"));
    }

    RecordDefinitionPhysicalRefSupport.requireDvSource(definition);
    DvSourceType sourceType = RecordDefinitionPhysicalRefSupport.resolveSourceType(definition);
    List<SourceField> fieldsToApply = discoveredFields;
    if (sourceType == DvSourceType.ICEBERG) {
      List<SourceField> storedFields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
      fieldsToApply =
          RecordDefinitionIcebergRefreshSupport.mergeDiscoveredFields(
              storedFields, discoveredFields);
    }
    DvSourceFieldSupport.applyLayoutToDefinition(definition, fieldsToApply, null);

    RecordOrigin origin = definition.getOrigin();
    if (origin == null) {
      origin = new RecordOrigin();
      definition.setOrigin(origin);
    }
    Date effectiveDiscoveredAt = discoveredAt != null ? discoveredAt : new Date();
    origin.setLastDiscoveredAt(effectiveDiscoveredAt);
    if (origin.getUpdatedAt() == null) {
      origin.setUpdatedAt(effectiveDiscoveredAt);
    }

    if (physicalSchemaId != null) {
      definition
          .getCustomProperties()
          .put(
              CUSTOM_PROPERTY_PHYSICAL_SCHEMA_ID,
              CatalogCustomProperty.string(Long.toString(physicalSchemaId)));
    }
  }

  private static Long resolvePhysicalSchemaId(
      DvSourceType sourceType, PhysicalSourceRef physicalRef, IVariables variables) {
    if (sourceType != DvSourceType.ICEBERG) {
      return null;
    }
    try {
      IcebergConnectionSettings settings = IcebergConnectionSettings.from(physicalRef, variables);
      return IcebergTableMetadataDiscovery.loadSchemaMetadata(settings).schemaId();
    } catch (HopException e) {
      return null;
    }
  }
}
