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
package org.apache.hop.datavault.metadata.dimensional.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionJoinValidationSupport;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmLayoutSupport;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;

/** Prepares fact stream fields and dimension lookups for fact-to-dimension joins. */
public final class DmFactDimensionJoinBuilder {

  public static final int DATE_KEY_INTEGER_LENGTH = 8;

  private static final String DATE_KEYS_FORMAT_TRANSFORM = "date_keys_format";
  private static final String DATE_KEYS_INT_TRANSFORM = "date_keys_int";
  private static final String SURROGATE_KEYS_PASSTHROUGH_TRANSFORM = "map_surrogate_keys";

  private DmFactDimensionJoinBuilder() {}

  public static TransformMeta wireDimensionRoles(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      List<DmFactDimensionRole> roles)
      throws HopException {
    if (predecessor == null || roles == null || roles.isEmpty()) {
      return predecessor;
    }

    List<DmFactDimensionRole> dateRoles = new ArrayList<>();
    List<DmFactDimensionRole> passthroughRoles = new ArrayList<>();
    List<DmFactDimensionRole> lookupRoles = new ArrayList<>();
    for (DmFactDimensionRole role : roles) {
      if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
        continue;
      }
      if (role.isTruncateToDateKey()) {
        dateRoles.add(role);
        continue;
      }
      DmDimension dimension =
          DmDimensionResolutionSupport.resolveDimension(
              model, role.getDimensionTableName(), ctx.variables, metadataProvider);
      if (dimension == null) {
        throw new HopException("references unknown dimension " + role.getDimensionTableName());
      }
      if (DmSurrogateKeySupport.shouldSkipFactDimensionLookup(
          role, dimension, ctx.config, ctx.variables)) {
        passthroughRoles.add(role);
      } else {
        lookupRoles.add(role);
      }
    }

    if (!dateRoles.isEmpty()) {
      predecessor =
          addBatchedDateKeySelectValues(
              ctx, pipelineMeta, predecessor, model, metadataProvider, dateRoles);
    }

    if (!passthroughRoles.isEmpty()) {
      predecessor =
          addSurrogateKeyPassthroughSelectValues(
              ctx, pipelineMeta, predecessor, model, metadataProvider, passthroughRoles);
    }

    for (DmFactDimensionRole role : lookupRoles) {
      DmDimension dimension =
          DmDimensionResolutionSupport.resolveDimension(
              model, role.getDimensionTableName(), ctx.variables, metadataProvider);
      if (dimension == null) {
        throw new HopException("references unknown dimension " + role.getDimensionTableName());
      }
      predecessor =
          DmDimensionLookupBuilder.addFactDimensionLookup(
              ctx, pipelineMeta, predecessor, dimension, role);
    }
    return predecessor;
  }

  public static TransformMeta addFactDimensionJoin(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension,
      DmFactDimensionRole role) {
    if (predecessor == null || dimension == null || role == null || role.isTruncateToDateKey()) {
      return predecessor;
    }
    if (DmSurrogateKeySupport.shouldSkipFactDimensionLookup(
        role, dimension, ctx.config, ctx.variables)) {
      SurrogateKeyMapping mapping = resolveSurrogateKeyMapping(role, dimension, ctx);
      if (mapping == null) {
        return predecessor;
      }
      return addSurrogateKeyPassthroughSelectValues(
          ctx, pipelineMeta, predecessor, List.of(mapping));
    }
    return DmDimensionLookupBuilder.addFactDimensionLookup(
        ctx, pipelineMeta, predecessor, dimension, role);
  }

  public static TransformMeta addSurrogateKeyPassthroughSelectValues(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      List<DmFactDimensionRole> passthroughRoles) {
    if (predecessor == null || passthroughRoles == null || passthroughRoles.isEmpty()) {
      return predecessor;
    }

    List<SurrogateKeyMapping> mappings = new ArrayList<>();
    for (DmFactDimensionRole role : passthroughRoles) {
      if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
        continue;
      }
      DmDimension dimension =
          model != null
              ? DmDimensionResolutionSupport.resolveDimension(
                  model, role.getDimensionTableName(), ctx.variables, metadataProvider)
              : null;
      SurrogateKeyMapping mapping = resolveSurrogateKeyMapping(role, dimension, ctx);
      if (mapping != null) {
        mappings.add(mapping);
      }
    }

    return addSurrogateKeyPassthroughSelectValues(ctx, pipelineMeta, predecessor, mappings);
  }

  private static TransformMeta addSurrogateKeyPassthroughSelectValues(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<SurrogateKeyMapping> mappings) {
    if (predecessor == null || mappings == null || mappings.isEmpty()) {
      return predecessor;
    }

    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    for (SurrogateKeyMapping mapping : mappings) {
      SelectField selectField = new SelectField();
      selectField.setName(mapping.sourceField());
      if (!mapping.sourceField().equals(mapping.fkColumn())) {
        selectField.setRename(mapping.fkColumn());
      }
      selectFields.add(selectField);
    }
    addDimensionLookupDatePassthrough(ctx, selectFields);

    TransformMeta tm =
        new TransformMeta("SelectValues", SURROGATE_KEYS_PASSTHROUGH_TRANSFORM, selectMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static SurrogateKeyMapping resolveSurrogateKeyMapping(
      DmFactDimensionRole role, DmDimension dimension, DmPipelineBuilderSupport.BuildContext ctx) {
    if (role == null || dimension == null) {
      return null;
    }
    String sourceField =
        DmSurrogateKeySupport.resolveFactRoleSourceField(role, dimension, ctx.variables);
    if (Utils.isEmpty(sourceField)) {
      return null;
    }
    String fkColumn = resolveFieldName(role.getForeignKeyColumn(), ctx.variables);
    if (Utils.isEmpty(fkColumn)) {
      fkColumn =
          DmLayoutSupport.defaultFactForeignKeyColumn(dimension, role, ctx.config, ctx.variables);
    }
    if (Utils.isEmpty(fkColumn)) {
      return null;
    }
    return new SurrogateKeyMapping(sourceField, fkColumn);
  }

  public static TransformMeta addBatchedDateKeySelectValues(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      List<DmFactDimensionRole> dateRoles) {
    if (predecessor == null || dateRoles == null || dateRoles.isEmpty()) {
      return predecessor;
    }

    List<DateKeyMapping> mappings = new ArrayList<>();
    for (DmFactDimensionRole role : dateRoles) {
      if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
        continue;
      }
      DmDimension dimension =
          DmDimensionResolutionSupport.resolveDimension(
              model, role.getDimensionTableName(), ctx.variables, metadataProvider);
      if (dimension == null) {
        continue;
      }
      List<String> naturalKeys =
          DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables);
      if (naturalKeys.isEmpty()) {
        continue;
      }
      String naturalKey = naturalKeys.get(0);
      String sourceField =
          DmFactDimensionJoinValidationSupport.resolveSourceFieldName(
              role, naturalKey, ctx.variables);
      if (Utils.isEmpty(sourceField)) {
        continue;
      }
      String fkColumn = resolveFieldName(role.getForeignKeyColumn(), ctx.variables);
      if (Utils.isEmpty(fkColumn)) {
        fkColumn =
            DmLayoutSupport.defaultFactForeignKeyColumn(dimension, role, ctx.config, ctx.variables);
      }
      if (Utils.isEmpty(fkColumn)) {
        continue;
      }
      mappings.add(new DateKeyMapping(sourceField, fkColumn));
    }

    if (mappings.isEmpty()) {
      return predecessor;
    }

    predecessor = addDateKeyFormatSelectValues(ctx, pipelineMeta, predecessor, mappings);
    return addDateKeyIntegerSelectValues(ctx, pipelineMeta, predecessor, mappings);
  }

  public static List<DimensionLookupMeta.DLKey> buildFactLookupKeys(
      DmDimension dimension, DmFactDimensionRole role, DmPipelineBuilderSupport.BuildContext ctx) {
    List<DimensionLookupMeta.DLKey> keys = new ArrayList<>();
    List<String> naturalKeys =
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables);
    for (String naturalKey : naturalKeys) {
      DimensionLookupMeta.DLKey key = new DimensionLookupMeta.DLKey();
      key.setLookup(naturalKey);
      key.setName(
          DmFactDimensionJoinValidationSupport.resolveStreamKeyField(
              role, naturalKey, ctx.variables));
      keys.add(key);
    }
    return keys;
  }

  public static String resolveLookupTransformName(DmFactDimensionRole role, DmDimension dimension) {
    if (!Utils.isEmpty(role.getRoleName())) {
      return "lookup_" + role.getRoleName();
    }
    if (!Utils.isEmpty(role.getDimensionTableName())) {
      return "lookup_" + sanitizeTransformToken(role.getDimensionTableName());
    }
    return "lookup_" + sanitizeTransformToken(dimension.getName());
  }

  private static void addDimensionLookupDatePassthrough(
      DmPipelineBuilderSupport.BuildContext ctx, List<SelectField> selectFields) {
    String lookupDateField = DmPipelineBuilderSupport.resolveFactDimensionLookupDateField(ctx);
    if (Utils.isEmpty(lookupDateField)) {
      return;
    }
    SelectField passthrough = new SelectField();
    passthrough.setName(lookupDateField);
    selectFields.add(passthrough);
  }

  private static TransformMeta addDateKeyFormatSelectValues(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<DateKeyMapping> mappings) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    List<SelectMetadataChange> metaChanges = selectMeta.getSelectOption().getMeta();

    for (DateKeyMapping mapping : mappings) {
      SelectField selectField = new SelectField();
      selectField.setName(mapping.sourceField());
      if (!mapping.sourceField().equals(mapping.fkColumn())) {
        selectField.setRename(mapping.fkColumn());
      }
      selectFields.add(selectField);

      SelectMetadataChange metaChange = new SelectMetadataChange();
      metaChange.setName(mapping.fkColumn());
      metaChange.setType(ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_STRING));
      metaChange.setConversionMask(DateDimensionGeneratorLogic.MASK_DATE_KEY);
      metaChanges.add(metaChange);
    }
    addDimensionLookupDatePassthrough(ctx, selectFields);

    TransformMeta tm = new TransformMeta("SelectValues", DATE_KEYS_FORMAT_TRANSFORM, selectMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addDateKeyIntegerSelectValues(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<DateKeyMapping> mappings) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    List<SelectMetadataChange> metaChanges = selectMeta.getSelectOption().getMeta();

    for (DateKeyMapping mapping : mappings) {
      SelectMetadataChange metaChange = new SelectMetadataChange();
      metaChange.setName(mapping.fkColumn());
      metaChange.setType(ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_INTEGER));
      metaChange.setLength(DATE_KEY_INTEGER_LENGTH);
      metaChange.setPrecision(0);
      metaChange.setConversionMask("0");
      metaChanges.add(metaChange);
    }

    TransformMeta tm = new TransformMeta("SelectValues", DATE_KEYS_INT_TRANSFORM, selectMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static String sanitizeTransformToken(String value) {
    if (Utils.isEmpty(value)) {
      return "dimension";
    }
    return value.replaceAll("[^A-Za-z0-9_]+", "_");
  }

  private static String resolveFieldName(String fieldName, IVariables variables) {
    if (variables != null) {
      fieldName = variables.resolve(fieldName);
    }
    return fieldName;
  }

  private record DateKeyMapping(String sourceField, String fkColumn) {}

  private record SurrogateKeyMapping(String sourceField, String fkColumn) {}
}
