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
package org.hopper.edw.datavault.metadata.masking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.uniquerowsbyhashset.UniqueRowsByHashSetMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvSortRowsSupport;
import org.hopper.edw.datavault.metadata.DvSourceFieldMappingSupport;
import org.hopper.edw.datavault.metadata.DvSourcePipelineBuilder;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceMaskingPipelineGenerator;

/**
 * Injects a source-masking pipeline, then adds the vault record-source indicator the same way a
 * JSON source does.
 */
@Getter
@Setter
public abstract class DvMaskingSourcePipelineBuilder extends DvSourcePipelineBuilder {

  protected DvMaskingSource maskingSource;

  protected DvMaskingSourcePipelineBuilder(
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
    maskingSource = (DvMaskingSource) dvSource;
  }

  @Override
  public void build() throws HopException {
    DvMaskingSourceResolver.ResolvedMasking resolved =
        DvMaskingSourceResolver.resolve(maskingSource, variables, metadataProvider);
    PipelineMeta generated =
        SourceMaskingPipelineGenerator.generate(
            resolved.model(), resolved.maskingSource(), variables, metadataProvider);
    mergeGeneratedPipeline(generated);
    TransformMeta current = addRecordSourceField(resultTransform);
    resultTransform = finishSourceChain(current);
  }

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

  private void mergeGeneratedPipeline(PipelineMeta generated) throws HopException {
    if (generated.getTransforms().isEmpty()) {
      throw new HopException("Generated masking source pipeline has no transforms");
    }
    Map<String, String> nameMap = new HashMap<>();
    int index = 0;
    TransformMeta lastCopy = null;
    for (TransformMeta transform : generated.getTransforms()) {
      String original = transform.getName();
      String unique = uniqueTransformName(original);
      nameMap.put(original, unique);
      TransformMeta copy = (TransformMeta) transform.clone();
      copy.setName(unique);
      copy.setLocation(startPoint.x + index * TRANSFORM_SPACING_X, startPoint.y);
      pipelineMeta.addTransform(copy);
      lastCopy = copy;
      index++;
    }
    for (PipelineHopMeta hop : generated.getPipelineHops()) {
      if (hop == null || hop.getFromTransform() == null || hop.getToTransform() == null) {
        continue;
      }
      String from = nameMap.get(hop.getFromTransform().getName());
      String to = nameMap.get(hop.getToTransform().getName());
      if (from == null || to == null) {
        continue;
      }
      TransformMeta fromMeta = pipelineMeta.findTransform(from);
      TransformMeta toMeta = pipelineMeta.findTransform(to);
      if (fromMeta != null && toMeta != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(fromMeta, toMeta));
      }
    }
    resultTransform = lastCopy;
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

  private String uniqueTransformName(String base) {
    String name = base;
    int i = 2;
    while (pipelineMeta.findTransform(name) != null) {
      name = base + " " + i;
      i++;
    }
    return name;
  }
}
