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
package org.hopper.edw.semantic.query;

import org.apache.hop.core.util.Utils;
import org.hopper.core.AggregationMethod;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabField;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSpec;
import org.hopper.edw.semantic.model.SemanticAttribute;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticSelection;
import org.hopper.edw.semantic.model.SemanticSelectionField;

/** Maps crosstab editor specs to durable semantic selections. */
public final class SemanticSelectionAdapter {

  private SemanticSelectionAdapter() {}

  public static SemanticSelection fromSpec(
      SemanticModel model, String factEntity, FactCrosstabSpec spec, String selectionName) {
    SemanticSelection selection = new SemanticSelection();
    selection.setName(selectionName);
    selection.setEntityName(factEntity);
    if (spec == null) {
      return selection;
    }
    selection.setShowingHorizontalTotals(spec.isShowingHorizontalTotals());
    selection.setShowingVerticalTotals(spec.isShowingVerticalTotals());
    copyZone(selection.getGroups(), spec.getGroups());
    copyZone(selection.getRows(), spec.getVerticalDimensions());
    copyZone(selection.getColumns(), spec.getHorizontalDimensions());
    copyZone(selection.getMeasures(), spec.getFacts());
    return selection;
  }

  public static FactCrosstabSpec toSpec(SemanticModel model, SemanticSelection selection) {
    FactCrosstabSpec spec = new FactCrosstabSpec();
    if (selection == null) {
      return spec;
    }
    spec.setFactTableName(selection.getEntityName());
    spec.setShowingHorizontalTotals(selection.isShowingHorizontalTotals());
    spec.setShowingVerticalTotals(selection.isShowingVerticalTotals());
    for (SemanticSelectionField field : selection.getGroups()) {
      spec.getGroups().add(toCrosstabField(model, field, false));
    }
    for (SemanticSelectionField field : selection.getRows()) {
      spec.getVerticalDimensions().add(toCrosstabField(model, field, false));
    }
    for (SemanticSelectionField field : selection.getColumns()) {
      spec.getHorizontalDimensions().add(toCrosstabField(model, field, false));
    }
    for (SemanticSelectionField field : selection.getMeasures()) {
      spec.getFacts().add(toCrosstabField(model, field, true));
    }
    return spec;
  }

  public static String formatMask(SemanticModel model, FactCrosstabField field) {
    if (model == null || field == null) {
      return null;
    }
    SemanticEntity entity = model.findEntity(field.getTableName());
    if (entity == null) {
      return null;
    }
    SemanticMeasure measure = entity.findMeasure(field.getColumnName());
    if (measure != null && !Utils.isEmpty(measure.getFormatMask())) {
      return measure.getFormatMask();
    }
    SemanticAttribute attribute = entity.findAttribute(field.getColumnName());
    if (attribute != null) {
      return attribute.getFormatMask();
    }
    return null;
  }

  private static void copyZone(
      java.util.List<SemanticSelectionField> target, java.util.List<FactCrosstabField> source) {
    if (source == null) {
      return;
    }
    for (FactCrosstabField field : source) {
      if (field == null) {
        continue;
      }
      SemanticSelectionField mapped =
          new SemanticSelectionField(field.getTableName(), field.getColumnName());
      mapped.setHeader(field.getHeader());
      mapped.setAggregationMethod(field.getAggregation());
      target.add(mapped);
    }
  }

  private static FactCrosstabField toCrosstabField(
      SemanticModel model, SemanticSelectionField field, boolean measureZone) {
    if (field == null) {
      return null;
    }
    String header = field.getHeader();
    AggregationMethod aggregation = field.resolveAggregation();
    if (model != null) {
      SemanticEntity entity = model.findEntity(field.getEntityName());
      if (entity != null) {
        if (measureZone) {
          SemanticMeasure measure = entity.findMeasure(field.getFieldName());
          if (measure != null) {
            if (Utils.isEmpty(header)) {
              header = measure.getHeader();
            }
            if (aggregation == null) {
              aggregation = measure.resolveAggregation();
            }
          }
        } else {
          SemanticAttribute attribute = entity.findAttribute(field.getFieldName());
          if (attribute != null && Utils.isEmpty(header)) {
            header = attribute.getHeader();
          }
        }
      }
    }
    return new FactCrosstabField(field.getEntityName(), field.getFieldName(), header, aggregation);
  }
}
