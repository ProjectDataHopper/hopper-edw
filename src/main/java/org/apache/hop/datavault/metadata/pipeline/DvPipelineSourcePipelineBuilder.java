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
package org.apache.hop.datavault.metadata.pipeline;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSortRowsSupport;
import org.apache.hop.datavault.metadata.DvSourceFieldMappingSupport;
import org.apache.hop.datavault.metadata.DvSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.uniquerowsbyhashset.UniqueRowsByHashSetMeta;

/**
 * Base builder for {@link DvPipelineSource}: MetaInject of the user pipeline, then record-source
 * indicator (static Constant or rename), matching database/file/JSON source builders.
 */
@Getter
@Setter
public abstract class DvPipelineSourcePipelineBuilder extends DvSourcePipelineBuilder {

  protected DvPipelineSource pipelineSource;

  protected DvPipelineSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      IDvTable dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
    pipelineSource = (DvPipelineSource) dvSource;
  }

  @Override
  public void build() throws HopException {
    MetaInjectMeta metaInjectMeta =
        DvPipelineSourceSupport.buildMetaInjectMeta(pipelineSource, variables, metadataProvider);
    TransformMeta injectTransform =
        new TransformMeta(
            "MetaInject", "source_" + safeName(recordSource.getName()), metaInjectMeta);
    injectTransform.setLocation(startPoint.x, startPoint.y);
    pipelineMeta.addTransform(injectTransform);

    TransformMeta current = addRecordSourceField(injectTransform);
    resultTransform = finishSourceChain(current);
  }

  /** Optional post-processing (sort/distinct for hubs). Default no-op. */
  protected TransformMeta finishSourceChain(TransformMeta predecessor) throws HopException {
    return predecessor;
  }

  protected TransformMeta addSortRows(TransformMeta predecessor, List<String> fieldNames) {
    if (predecessor == null || fieldNames == null || fieldNames.isEmpty()) {
      return predecessor;
    }
    SortRowsMeta sortMeta = new SortRowsMeta();
    List<SortRowsField> sortFields = new ArrayList<>();
    for (String fieldName : fieldNames) {
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      SortRowsField sortField = new SortRowsField();
      sortField.setFieldName(fieldName);
      sortField.setAscending(true);
      sortFields.add(sortField);
    }
    if (sortFields.isEmpty()) {
      return predecessor;
    }
    sortMeta.setSortFields(sortFields);
    DvSortRowsSupport.applyConfiguration(sortMeta, configuration, variables);

    TransformMeta sortTransform = new TransformMeta("SortRows", "sort source rows", sortMeta);
    sortTransform.setLocation(
        predecessor.getLocation().x + TRANSFORM_SPACING_X, predecessor.getLocation().y);
    pipelineMeta.addTransform(sortTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, sortTransform));
    return sortTransform;
  }

  protected TransformMeta addUniqueRows(TransformMeta predecessor, List<String> fieldNames) {
    if (predecessor == null || fieldNames == null || fieldNames.isEmpty()) {
      return predecessor;
    }
    UniqueRowsByHashSetMeta uniqueMeta = new UniqueRowsByHashSetMeta();
    List<UniqueRowsByHashSetMeta.CompareField> compareFields = new ArrayList<>();
    for (String fieldName : fieldNames) {
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      UniqueRowsByHashSetMeta.CompareField compareField =
          new UniqueRowsByHashSetMeta.CompareField();
      compareField.setName(fieldName);
      compareFields.add(compareField);
    }
    if (compareFields.isEmpty()) {
      return predecessor;
    }
    uniqueMeta.setCompareFields(compareFields);

    TransformMeta uniqueTransform =
        new TransformMeta("UniqueRowsByHashSet", "distinct source rows", uniqueMeta);
    uniqueTransform.setLocation(
        predecessor.getLocation().x + TRANSFORM_SPACING_X, predecessor.getLocation().y);
    pipelineMeta.addTransform(uniqueTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, uniqueTransform));
    return uniqueTransform;
  }

  private TransformMeta addRecordSourceField(TransformMeta predecessor) throws HopException {
    if (predecessor == null || !DvSourceFieldMappingSupport.shouldStoreRecordSource(dvTable)) {
      return predecessor;
    }

    String targetSourceFieldName = resolveTargetRecordSourceFieldName();
    String staticRecordSource = DvSourceFieldMappingSupport.resolveRecordSourceValue(recordSource);
    if (staticRecordSource == null) {
      String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
      if (Utils.isEmpty(sourceFieldName) || sourceFieldName.equals(targetSourceFieldName)) {
        return predecessor;
      }
      SelectValuesMeta selectMeta = new SelectValuesMeta();
      selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
      SelectMetadataChange rename = new SelectMetadataChange();
      rename.setName(sourceFieldName);
      rename.setRename(targetSourceFieldName);
      selectMeta.getSelectOption().getMeta().add(rename);

      TransformMeta renameTransform =
          new TransformMeta("SelectValues", "rename record source", selectMeta);
      renameTransform.setLocation(
          predecessor.getLocation().x + TRANSFORM_SPACING_X, predecessor.getLocation().y);
      pipelineMeta.addTransform(renameTransform);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, renameTransform));
      return renameTransform;
    }

    ConstantMeta constantMeta = new ConstantMeta();
    constantMeta
        .getFields()
        .add(new ConstantField(targetSourceFieldName, "String", staticRecordSource));

    TransformMeta constantTransform =
        new TransformMeta("Constant", "add record source", constantMeta);
    constantTransform.setLocation(
        predecessor.getLocation().x + TRANSFORM_SPACING_X, predecessor.getLocation().y);
    pipelineMeta.addTransform(constantTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, constantTransform));
    return constantTransform;
  }

  private String resolveTargetRecordSourceFieldName() throws HopException {
    if (dvTable instanceof DvSatellite satellite) {
      return DvSourceFieldMappingSupport.resolveRecordSourceFieldNameForSatellite(
          configuration, model, satellite, variables);
    }
    String fieldName =
        DvSourceFieldMappingSupport.findTargetSourceFieldName(configuration, recordSource, dvTable);
    return variables != null ? variables.resolve(fieldName) : fieldName;
  }

  private static String safeName(String name) {
    if (Utils.isEmpty(name)) {
      return "pipeline";
    }
    return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
  }
}
