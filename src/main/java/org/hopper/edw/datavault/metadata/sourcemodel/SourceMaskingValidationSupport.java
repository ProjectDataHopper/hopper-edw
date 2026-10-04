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
package org.hopper.edw.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.transforms.maskfields.MaskingPattern;
import org.apache.hop.pipeline.transforms.maskfields.MaskingRules;
import org.apache.hop.pipeline.transforms.maskfields.MaskingStorage;
import org.apache.hop.pipeline.transforms.maskfields.MaskingValueSource;

/** Design-time checks for one {@link SourceMasking} card. */
public final class SourceMaskingValidationSupport {

  private static final Class<?> PKG = SourceModel.class;

  private SourceMaskingValidationSupport() {}

  public static List<ICheckResult> check(
      SourceMasking masking,
      SourceModel model,
      Set<String> knownNames,
      IHopMetadataProvider metadataProvider) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (masking == null) {
      return remarks;
    }
    String name = masking.getName();
    if (Utils.isEmpty(name)) {
      remarks.add(
          error(BaseMessages.getString(PKG, "SourceModel.CheckResult.MaskingMissingName")));
      name = "?";
    } else if (knownNames != null && !knownNames.add(name)) {
      remarks.add(
          error(
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.DuplicateMaskingName", name)));
    }

    if (Utils.isEmpty(masking.getParentSourceName())) {
      remarks.add(
          error(
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.MaskingMissingParent", nvl(name))));
    } else if (model != null && !SourceMaskingParentSupport.parentExists(model, masking)) {
      remarks.add(
          error(
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.MaskingParentNotFound",
                  nvl(name),
                  masking.resolveParentSourceKind().getCode(),
                  nvl(masking.getParentSourceName()))));
    } else if (model != null && hasCycle(model, masking)) {
      remarks.add(
          error(
              BaseMessages.getString(PKG, "SourceModel.CheckResult.MaskingParentCycle", nvl(name))));
    }

    if (masking.getFields().isEmpty()) {
      remarks.add(
          error(
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.MaskingEmptyFields", nvl(name))));
    }

    Set<String> seen = new HashSet<>();
    for (SourceMaskingField field : masking.getFields()) {
      if (field == null || Utils.isEmpty(field.resolveName())) {
        remarks.add(
            error(
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.MaskingFieldMissingName", nvl(name))));
        continue;
      }
      String fieldName = field.resolveName();
      if (!seen.add(fieldName)) {
        remarks.add(
            error(
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.MaskingDuplicateField", nvl(name), fieldName)));
      }
      if (!field.hasPattern()) {
        continue;
      }
      MaskingPattern pattern = loadPattern(metadataProvider, field.getPatternName());
      if (metadataProvider != null && pattern == null) {
        remarks.add(
            error(
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.MaskingMissingPattern",
                    nvl(name),
                    fieldName,
                    field.getPatternName().trim())));
        continue;
      }
      if (pattern == null) {
        continue;
      }
      String problem = incompatibility(field, pattern);
      if (problem != null) {
        remarks.add(
            error(
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.MaskingIncompatible",
                    nvl(name),
                    fieldName,
                    pattern.getName(),
                    problem)));
      }
      if (field.isPrimaryKey() && unstableIdentity(pattern)) {
        remarks.add(
            warning(
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.MaskingUnstableKey",
                    nvl(name),
                    fieldName,
                    pattern.getName())));
      }
      if (pattern.getStorage() == MaskingStorage.DATABASE) {
        remarks.add(
            comment(
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.MaskingDatabaseStoresOriginal",
                    nvl(name),
                    pattern.getName())));
      }
    }
    return remarks;
  }

  static boolean hasCycle(SourceModel model, SourceMasking start) {
    Set<String> seen = new HashSet<>();
    SourceMasking current = start;
    while (current != null && current.resolveParentSourceKind() == SourceMaskingParentKind.MASKING) {
      String name = current.getName();
      if (!Utils.isEmpty(name) && !seen.add(name)) {
        return true;
      }
      String parentName = current.getParentSourceName();
      if (Utils.isEmpty(parentName)) {
        return false;
      }
      if (!Utils.isEmpty(name) && name.equals(parentName.trim())) {
        return true;
      }
      current = model.findMaskingSource(parentName.trim());
    }
    return false;
  }

  private static String incompatibility(SourceMaskingField field, MaskingPattern pattern) {
    try {
      int type = field.getHopType() > 0 ? field.getHopType() : IValueMeta.TYPE_STRING;
      IValueMeta valueMeta = ValueMetaFactory.createValueMeta(field.resolveName(), type);
      return MaskingRules.incompatibility(valueMeta, pattern);
    } catch (HopException e) {
      return e.getMessage();
    }
  }

  private static boolean unstableIdentity(MaskingPattern pattern) {
    if (pattern.getStorage() == MaskingStorage.NONE) {
      return true;
    }
    MaskingValueSource source = pattern.getValueSource();
    return source == MaskingValueSource.SET_NULL || source == MaskingValueSource.SET_EMPTY;
  }

  private static MaskingPattern loadPattern(IHopMetadataProvider metadataProvider, String name) {
    if (metadataProvider == null || Utils.isEmpty(name)) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(MaskingPattern.class).load(name.trim());
    } catch (HopException e) {
      return null;
    }
  }

  private static CheckResult error(String message) {
    return new CheckResult(ICheckResult.TYPE_RESULT_ERROR, message, null);
  }

  private static CheckResult warning(String message) {
    return new CheckResult(ICheckResult.TYPE_RESULT_WARNING, message, null);
  }

  private static CheckResult comment(String message) {
    return new CheckResult(ICheckResult.TYPE_RESULT_COMMENT, message, null);
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}
