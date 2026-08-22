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
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogWriter;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorSupport;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSourceDeliveryType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceDataTypeMappingPublishSupport;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceDataTypeMappingSupport;
import org.hopper.edw.datavault.metadata.pipeline.DvPipelineSource;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes a {@link SourcePipeline} as a catalog {@code DV_SOURCE} of type {@code PIPELINE}. */
public final class SourcePipelineCatalogPublisher {

  private static final Class<?> PKG = SourcePipelineCatalogPublisher.class;

  private SourcePipelineCatalogPublisher() {}

  public record PublishResult(String catalogName, String message) {}

  public static PublishResult publish(
      SourceModel model,
      SourcePipeline pipelineSource,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null || pipelineSource == null) {
      throw new HopException("Source model and pipeline source are required to publish");
    }
    if (Utils.isEmpty(pipelineSource.getName())) {
      throw new HopException("Source pipeline name is required to publish");
    }
    if (Utils.isEmpty(pipelineSource.getPipelineFilename())) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "SourcePipelineCatalogPublisher.Error.MissingFilename",
              pipelineSource.getName()));
    }
    if (Utils.isEmpty(pipelineSource.getOutputTransformName())) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "SourcePipelineCatalogPublisher.Error.MissingTransform",
              pipelineSource.getName()));
    }
    List<SourceField> fields = buildFieldsFromProjection(pipelineSource, metadataProvider);
    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "SourcePipelineCatalogPublisher.Error.EmptyProjection",
              pipelineSource.getName()));
    }

    String catalogConnection = resolveCatalogConnection(model, catalogConnectionName, variables);
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourcePipelineCatalogPublisher.Error.NoCatalogConnection"));
    }

    String feedName = pipelineSource.resolveCatalogSourceName();
    if (Utils.isEmpty(feedName)) {
      feedName = pipelineSource.getName().trim();
    }

    RecordSourceIndicatorOptions indicatorOptions =
        RecordSourceIndicatorSupport.resolveForTable(null, fields, feedName);

    DvPipelineSource pipeline = new DvPipelineSource();
    pipeline.setDescription(
        !Utils.isEmpty(pipelineSource.getDescription())
            ? pipelineSource.getDescription()
            : "Source pipeline "
                + pipelineSource.getName()
                + " from "
                + Const.NVL(model.getName(), "source model"));
    pipeline.setFields(fields);
    pipeline.setPipelineFilename(pipelineSource.getPipelineFilename());
    pipeline.setOutputTransformName(pipelineSource.getOutputTransformName());
    pipeline.setPipelineRunConfiguration(pipelineSource.getPipelineRunConfiguration());
    String modelFilename = model.getFilename();
    if (Utils.isEmpty(modelFilename)) {
      modelFilename = model.getName();
    }
    pipeline.setSourceModelFilename(
        CatalogModelRegistrySupport.portableModelPath(modelFilename, variables));
    pipeline.setSourcePipelineName(pipelineSource.getName());

    DataVaultSource dataVaultSource = new DataVaultSource(feedName);
    dataVaultSource.setSource(pipeline);
    dataVaultSource.setSourceIndicator(indicatorOptions.getStaticValue());
    dataVaultSource.setSourceIndicatorField(indicatorOptions.getFieldName());
    dataVaultSource.setDeliveryType(DvSourceDeliveryType.CHANGES_ONLY);

    RecordDefinitionCatalogWriter.upsertDataVaultSource(
        dataVaultSource, catalogConnection, null, variables, metadataProvider, null, null, null);

    pipelineSource.setCatalogSourceName(feedName);
    return new PublishResult(feedName, "Published pipeline feed '" + feedName + "'");
  }

  public static List<SourceField> buildFieldsFromProjection(SourcePipeline pipelineSource) {
    try {
      return buildFieldsFromProjection(pipelineSource, null);
    } catch (HopException e) {
      return new ArrayList<>();
    }
  }

  public static List<SourceField> buildFieldsFromProjection(
      SourcePipeline pipelineSource, IHopMetadataProvider metadataProvider) throws HopException {
    if (pipelineSource == null) {
      return new ArrayList<>();
    }
    return SourceDataTypeMappingPublishSupport.toEffectiveSourceFields(
        pipelineSource,
        SourceDataTypeMappingSupport.physicalFields(pipelineSource),
        metadataProvider);
  }

  public static String resolveCatalogSourceName(SourcePipeline pipelineSource) {
    if (pipelineSource == null) {
      return "";
    }
    return pipelineSource.resolveCatalogSourceName();
  }

  private static String resolveCatalogConnection(
      SourceModel model, String catalogConnectionName, IVariables variables) {
    if (!Utils.isEmpty(catalogConnectionName)) {
      return variables != null ? variables.resolve(catalogConnectionName) : catalogConnectionName;
    }
    if (model != null && model.getConfigurationOrDefault() != null) {
      String fromModel = model.getConfigurationOrDefault().getCatalogConnection();
      if (!Utils.isEmpty(fromModel)) {
        return variables != null ? variables.resolve(fromModel) : fromModel;
      }
    }
    return null;
  }
}
