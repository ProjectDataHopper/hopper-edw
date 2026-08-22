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
package org.apache.hop.datavault.metadata.sourcemodel.publish;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionCatalogWriter;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.CatalogModelRegistrySupport;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorSupport;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.datatypemapping.SourceDataTypeMappingPublishSupport;
import org.apache.hop.datavault.metadata.datatypemapping.SourceDataTypeMappingSupport;
import org.apache.hop.datavault.metadata.json.DvJsonSource;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonFieldSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes a {@link SourceJson} as a catalog {@code DV_SOURCE} of type {@code JSON}. */
public final class SourceJsonCatalogPublisher {

  private static final Class<?> PKG = SourceJsonCatalogPublisher.class;

  private SourceJsonCatalogPublisher() {}

  public record PublishResult(String catalogName, String message) {}

  public static PublishResult publish(
      SourceModel model,
      SourceJson jsonSource,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null || jsonSource == null) {
      throw new HopException("Source model and JSON source are required to publish");
    }
    if (Utils.isEmpty(jsonSource.getName())) {
      throw new HopException("Source JSON name is required to publish");
    }
    if (jsonSource.getFields().isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SourceJsonCatalogPublisher.Error.EmptyProjection", jsonSource.getName()));
    }

    String catalogConnection = resolveCatalogConnection(model, catalogConnectionName, variables);
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceJsonCatalogPublisher.Error.NoCatalogConnection"));
    }

    String feedName =
        !Utils.isEmpty(jsonSource.getPublishedCatalogName())
            ? jsonSource.getPublishedCatalogName().trim()
            : jsonSource.getName().trim();

    List<SourceField> fields = buildFieldsFromProjection(model, jsonSource, metadataProvider);
    RecordSourceIndicatorOptions indicatorOptions =
        RecordSourceIndicatorSupport.resolveForTable(null, fields, feedName);

    DvJsonSource json = new DvJsonSource();
    json.setDescription(
        !Utils.isEmpty(jsonSource.getDescription())
            ? jsonSource.getDescription()
            : "Source JSON "
                + jsonSource.getName()
                + " from "
                + Const.NVL(model.getName(), "source model"));
    json.setFields(fields);
    String modelFilename = model.getFilename();
    if (Utils.isEmpty(modelFilename)) {
      modelFilename = model.getName();
    }
    json.setSourceModelFilename(
        CatalogModelRegistrySupport.portableModelPath(modelFilename, variables));
    json.setSourceJsonName(jsonSource.getName());

    DataVaultSource dataVaultSource = new DataVaultSource(feedName);
    dataVaultSource.setSource(json);
    dataVaultSource.setSourceIndicator(indicatorOptions.getStaticValue());
    dataVaultSource.setSourceIndicatorField(indicatorOptions.getFieldName());
    dataVaultSource.setDeliveryType(DvSourceDeliveryType.CHANGES_ONLY);

    RecordDefinitionCatalogWriter.upsertDataVaultSource(
        dataVaultSource, catalogConnection, null, variables, metadataProvider, null, null, null);

    jsonSource.setPublishedCatalogName(feedName);
    return new PublishResult(feedName, "Published JSON feed '" + feedName + "'");
  }

  public static List<SourceField> buildFieldsFromProjection(SourceJson jsonSource) {
    try {
      return buildFieldsFromProjection(null, jsonSource, null);
    } catch (HopException e) {
      return List.of();
    }
  }

  public static List<SourceField> buildFieldsFromProjection(
      SourceModel model, SourceJson jsonSource) {
    try {
      return buildFieldsFromProjection(model, jsonSource, null);
    } catch (HopException e) {
      return List.of();
    }
  }

  /**
   * Builds catalog field layout from the JSON projection, then applies data type mappings when
   * configured. When {@code model} is provided, pass-through fields with unset hop types inherit
   * the parent table/query/JSON column type. Remaining unknown types default to {@link
   * IValueMeta#TYPE_STRING}.
   */
  public static List<SourceField> buildFieldsFromProjection(
      SourceModel model, SourceJson jsonSource, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (jsonSource == null) {
      return new ArrayList<>();
    }
    // Physical baseline includes parse-time format from SourceJsonField via PhysicalSourceField.
    // Enrich hop types from parent model when present (pass-through fields).
    var physical = SourceDataTypeMappingSupport.physicalFields(jsonSource);
    for (int i = 0; i < physical.size(); i++) {
      SourceJsonField jsonField =
          i < jsonSource.getFields().size() ? jsonSource.getFields().get(i) : null;
      if (jsonField == null || physical.get(i) == null) {
        continue;
      }
      int hopType = SourceJsonFieldSupport.resolveEffectiveHopType(model, jsonSource, jsonField);
      if (hopType > 0) {
        physical.get(i).setHopType(hopType);
      }
      if (!Utils.isEmpty(jsonField.getPath())) {
        physical.get(i).setDescription("JsonPath: " + jsonField.getPath());
      }
    }
    return SourceDataTypeMappingPublishSupport.toEffectiveSourceFields(
        jsonSource, physical, metadataProvider);
  }

  private static String resolveCatalogConnection(
      SourceModel model, String override, IVariables variables) {
    String catalogConnection = Const.NVL(override, "");
    if (variables != null) {
      catalogConnection = variables.resolve(catalogConnection);
    }
    if (Utils.isEmpty(catalogConnection) && model != null) {
      catalogConnection = Const.NVL(model.getConfigurationOrDefault().getCatalogConnection(), "");
      if (variables != null) {
        catalogConnection = variables.resolve(catalogConnection);
      }
    }
    return catalogConnection;
  }
}
