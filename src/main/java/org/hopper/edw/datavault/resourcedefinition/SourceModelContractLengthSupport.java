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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMasking;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourceJsonCatalogPublisher;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourceMaskingCatalogPublisher;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourcePipelineCatalogPublisher;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourceQueryCatalogPublisher;

/**
 * Effective field lengths from the source-model card that published a catalog feed. Pipeline, JSON,
 * query, and masking cards apply their data type mapping profiles and field overrides, the same
 * projection catalog publish uses.
 */
public final class SourceModelContractLengthSupport {

  private SourceModelContractLengthSupport() {}

  /**
   * Field name (lower case) to effective length. Empty when the feed is not a source-model card or
   * the model cannot be read. Lengths that are missing or not a positive integer are omitted.
   */
  public static Map<String, String> effectiveLengthsByFieldName(
      RecordDefinition definition, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (definition == null || definition.getDvSource() == null || metadataProvider == null) {
      return Map.of();
    }
    try {
      List<SourceField> fields = effectiveFields(definition.getDvSource(), variables, metadataProvider);
      if (fields.isEmpty()) {
        return Map.of();
      }
      Map<String, String> lengths = new LinkedHashMap<>();
      for (SourceField field : fields) {
        if (field == null || BaselineContractSupport.parsePositiveInt(field.getLength()) <= 0) {
          continue;
        }
        putLength(lengths, field.getName(), field.getLength());
        putLength(lengths, field.getSourceStreamName(), field.getLength());
      }
      return Map.copyOf(lengths);
    } catch (Exception e) {
      // A missing source model must not fail the rest of the validation report.
      return Map.of();
    }
  }

  private static void putLength(Map<String, String> lengths, String fieldName, String length) {
    if (Utils.isEmpty(fieldName) || Utils.isEmpty(length)) {
      return;
    }
    lengths.putIfAbsent(fieldName.trim().toLowerCase(Locale.ROOT), length.trim());
  }

  private static List<SourceField> effectiveFields(
      DvSourceRecord source, IVariables variables, IHopMetadataProvider metadataProvider)
      throws Exception {
    String type = source.getSourceType() == null ? "" : source.getSourceType().trim();
    boolean unspecified = type.isEmpty();
    if ((unspecified || isType(type, DvSourceType.PIPELINE))
        && !Utils.isEmpty(source.getPipelineSourceModelFilename())
        && !Utils.isEmpty(source.getPipelineSourceName())) {
      SourceModel model =
          SourceModelLoadSupport.load(
              source.getPipelineSourceModelFilename(), variables, metadataProvider);
      SourcePipeline pipeline =
          model.findPipelineSource(resolve(source.getPipelineSourceName(), variables));
      if (pipeline == null) {
        return List.of();
      }
      return SourcePipelineCatalogPublisher.buildFieldsFromProjection(pipeline, metadataProvider);
    }
    if ((unspecified || isType(type, DvSourceType.JSON))
        && !Utils.isEmpty(source.getJsonSourceModelFilename())
        && !Utils.isEmpty(source.getJsonSourceName())) {
      SourceModel model =
          SourceModelLoadSupport.load(
              source.getJsonSourceModelFilename(), variables, metadataProvider);
      SourceJson json = model.findJsonSource(resolve(source.getJsonSourceName(), variables));
      if (json == null) {
        return List.of();
      }
      return SourceJsonCatalogPublisher.buildFieldsFromProjection(model, json, metadataProvider);
    }
    if ((unspecified || isType(type, DvSourceType.MASKING))
        && !Utils.isEmpty(source.getMaskingSourceModelFilename())
        && !Utils.isEmpty(source.getMaskingSourceName())) {
      SourceModel model =
          SourceModelLoadSupport.load(
              source.getMaskingSourceModelFilename(), variables, metadataProvider);
      SourceMasking masking =
          model.findMaskingSource(resolve(source.getMaskingSourceName(), variables));
      if (masking == null) {
        return List.of();
      }
      return SourceMaskingCatalogPublisher.buildFieldsFromProjection(masking, metadataProvider);
    }
    if ((unspecified || isType(type, DvSourceType.COMPOSITE))
        && !Utils.isEmpty(source.getCompositeSourceModelFilename())
        && !Utils.isEmpty(source.getCompositeSourceQueryName())) {
      SourceModel model =
          SourceModelLoadSupport.load(
              source.getCompositeSourceModelFilename(), variables, metadataProvider);
      SourceQuery query = model.findQuery(resolve(source.getCompositeSourceQueryName(), variables));
      if (query == null) {
        return List.of();
      }
      return SourceQueryCatalogPublisher.buildFieldsFromProjection(
          model, query, metadataProvider);
    }
    return List.of();
  }

  private static boolean isType(String actual, DvSourceType expected) {
    return expected.getCode().equalsIgnoreCase(actual);
  }

  private static String resolve(String value, IVariables variables) {
    if (variables == null || Utils.isEmpty(value)) {
      return value;
    }
    return variables.resolve(value);
  }
}
