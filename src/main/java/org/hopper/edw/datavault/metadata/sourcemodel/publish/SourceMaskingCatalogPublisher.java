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
package org.hopper.edw.datavault.metadata.sourcemodel.publish;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogWriter;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorSupport;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSourceDeliveryType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceDataTypeMappingPublishSupport;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceDataTypeMappingSupport;
import org.hopper.edw.datavault.metadata.masking.DvMaskingSource;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMasking;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;

/** Publishes a {@link SourceMasking} card as a catalog {@code DV_SOURCE} of type {@code MASKING}. */
public final class SourceMaskingCatalogPublisher {

  private static final Class<?> PKG = SourceMaskingCatalogPublisher.class;

  private SourceMaskingCatalogPublisher() {}

  public record PublishResult(String catalogName, String message) {}

  public static PublishResult publish(
      SourceModel model,
      SourceMasking masking,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null || masking == null) {
      throw new HopException("Source model and masking source are required to publish");
    }
    if (Utils.isEmpty(masking.getName())) {
      throw new HopException("Source masking name is required to publish");
    }
    if (masking.getFields().isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SourceMaskingCatalogPublisher.Error.EmptyProjection", masking.getName()));
    }

    String catalogConnection = resolveCatalogConnection(model, catalogConnectionName, variables);
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceMaskingCatalogPublisher.Error.NoCatalogConnection"));
    }

    String feedName = masking.resolveCatalogName();
    List<SourceField> fields = buildFieldsFromProjection(masking, metadataProvider);
    RecordSourceIndicatorOptions indicatorOptions =
        RecordSourceIndicatorSupport.resolveForTable(null, fields, feedName);

    DvMaskingSource maskingSource = new DvMaskingSource();
    maskingSource.setDescription(
        !Utils.isEmpty(masking.getDescription())
            ? masking.getDescription()
            : "Source masking "
                + masking.getName()
                + " from "
                + Const.NVL(model.getName(), "source model"));
    maskingSource.setFields(fields);
    String modelFilename = model.getFilename();
    if (Utils.isEmpty(modelFilename)) {
      modelFilename = model.getName();
    }
    maskingSource.setSourceModelFilename(
        CatalogModelRegistrySupport.portableModelPath(modelFilename, variables));
    maskingSource.setSourceMaskingName(masking.getName());

    DataVaultSource dataVaultSource = new DataVaultSource(feedName);
    dataVaultSource.setSource(maskingSource);
    dataVaultSource.setSourceIndicator(indicatorOptions.getStaticValue());
    dataVaultSource.setSourceIndicatorField(indicatorOptions.getFieldName());
    dataVaultSource.setDeliveryType(DvSourceDeliveryType.CHANGES_ONLY);

    RecordDefinitionCatalogWriter.upsertDataVaultSource(
        dataVaultSource, catalogConnection, null, variables, metadataProvider, null, null, null);

    masking.setPublishedCatalogName(feedName);
    return new PublishResult(feedName, "Published masking feed '" + feedName + "'");
  }

  public static List<SourceField> buildFieldsFromProjection(SourceMasking masking) {
    try {
      return buildFieldsFromProjection(masking, null);
    } catch (HopException e) {
      return List.of();
    }
  }

  public static List<SourceField> buildFieldsFromProjection(
      SourceMasking masking, IHopMetadataProvider metadataProvider) throws HopException {
    if (masking == null) {
      return new ArrayList<>();
    }
    return SourceDataTypeMappingPublishSupport.toEffectiveSourceFields(
        masking, SourceDataTypeMappingSupport.physicalFields(masking), metadataProvider);
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
