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
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Design-time checks for a single {@link SourceJson} (dialog Validate and model check). Catches
 * missing types, parent wiring, array expansion rules, and key positions before catalog publish /
 * vault update.
 */
public final class SourceJsonValidationSupport {

  private static final Class<?> PKG = SourceModel.class;

  private SourceJsonValidationSupport() {}

  /**
   * Validates one JSON source against the model (parent existence, fields, types, keys, arrays).
   *
   * @param jsonSource projection to check (may be a dialog draft not yet on the model)
   * @param model owning source model (for parent resolution)
   * @param knownJsonNames optional set of JSON names already seen (duplicate detection); may be
   *     null
   */
  public static List<ICheckResult> check(
      SourceJson jsonSource, SourceModel model, Set<String> knownJsonNames) {
    return check(jsonSource, model, knownJsonNames, null, null);
  }

  public static List<ICheckResult> check(
      SourceJson jsonSource,
      SourceModel model,
      Set<String> knownJsonNames,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (jsonSource == null) {
      return remarks;
    }
    String jsonName = jsonSource.getName();
    if (Utils.isEmpty(jsonName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.JsonSourceMissingName"),
              null));
      jsonName = "?";
    } else if (knownJsonNames != null && !knownJsonNames.add(jsonName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.DuplicateJsonSourceName", jsonName),
              null));
    }

    SourceJsonParentKind parentKind = jsonSource.resolveParentSourceKind();
    String parentName = jsonSource.getParentSourceName();
    if (Utils.isEmpty(parentName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.JsonSourceMissingParent", nvl(jsonName)),
              null));
    } else if (model != null && !parentExists(model, parentKind, parentName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.JsonSourceParentNotFound",
                  nvl(jsonName),
                  parentKind != null ? parentKind.getCode() : "?",
                  nvl(parentName)),
              null));
    }
    if (Utils.isEmpty(jsonSource.getJsonFieldName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.JsonSourceMissingJsonField", nvl(jsonName)),
              null));
    }
    if (jsonSource.getFields().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.JsonSourceEmptyProjection", nvl(jsonName)),
              null));
    }

    Set<String> arrayBases = new HashSet<>();
    Set<Integer> keyPositions = new HashSet<>();
    boolean hasLogicalKey = false;
    int missingTypeCount = 0;
    int missingNameCount = 0;
    int passThroughMissingParent = 0;
    int extractMissingPath = 0;

    for (SourceJsonField field : jsonSource.getFields()) {
      if (field == null) {
        continue;
      }
      if (Utils.isEmpty(field.resolveName())) {
        missingNameCount++;
      }
      if (field.isPassThrough()) {
        if (Utils.isEmpty(field.getParentFieldName()) && Utils.isEmpty(field.getName())) {
          passThroughMissingParent++;
        }
      } else if (Utils.isEmpty(field.getPath())) {
        extractMissingPath++;
      }
      if (field.isArrayExpandingPath()) {
        arrayBases.add(SourceModel.arrayBasePath(field.getPath()));
      }
      int hopType = SourceJsonFieldSupport.resolveEffectiveHopType(model, jsonSource, field);
      if (hopType <= 0) {
        missingTypeCount++;
      }
      if (field.isPrimaryKey()) {
        hasLogicalKey = true;
        int position = field.getPrimaryKeyPosition();
        if (!keyPositions.add(position)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.JsonSourceDuplicateKeyPosition",
                      nvl(jsonName),
                      Integer.toString(position)),
                  null));
        }
      }
    }

    if (missingNameCount > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.JsonSourceFieldsMissingName",
                  nvl(jsonName),
                  Integer.toString(missingNameCount)),
              null));
    }
    if (passThroughMissingParent > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.JsonSourcePassThroughMissingParent",
                  nvl(jsonName),
                  Integer.toString(passThroughMissingParent)),
              null));
    }
    if (extractMissingPath > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.JsonSourceExtractMissingPath",
                  nvl(jsonName),
                  Integer.toString(extractMissingPath)),
              null));
    }
    if (missingTypeCount > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.JsonSourceFieldsMissingType",
                  nvl(jsonName),
                  Integer.toString(missingTypeCount)),
              null));
    }
    if (arrayBases.size() > 1) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.JsonSourceMultipleArrays",
                  nvl(jsonName),
                  Integer.toString(arrayBases.size())),
              null));
    }
    if (!jsonSource.getFields().isEmpty() && !hasLogicalKey) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.JsonSourceMissingLogicalKey", nvl(jsonName)),
              null));
    }
    if (model != null
        && parentKind == SourceJsonParentKind.JSON
        && !Utils.isEmpty(parentName)
        && hasJsonParentCycle(model, jsonName, parentName, new HashSet<>())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.JsonSourceParentCycle", nvl(jsonName)),
              null));
    }
    remarks.addAll(
        SourceCatalogPublishSyncSupport.checkJson(model, jsonSource, variables, metadataProvider));
    return remarks;
  }

  private static boolean parentExists(
      SourceModel model, SourceJsonParentKind parentKind, String parentName) {
    if (model == null || Utils.isEmpty(parentName)) {
      return false;
    }
    SourceJsonParentKind kind = parentKind != null ? parentKind : SourceJsonParentKind.TABLE;
    return switch (kind) {
      case TABLE -> model.findTable(parentName) != null;
      case QUERY -> model.findQuery(parentName) != null;
      case JSON -> model.findJsonSource(parentName) != null;
    };
  }

  private static boolean hasJsonParentCycle(
      SourceModel model, String currentName, String parentName, Set<String> visiting) {
    if (Utils.isEmpty(parentName)) {
      return false;
    }
    if (parentName.equals(currentName)) {
      return true;
    }
    if (!visiting.add(parentName)) {
      return true;
    }
    SourceJson parent = model.findJsonSource(parentName);
    if (parent == null || parent.resolveParentSourceKind() != SourceJsonParentKind.JSON) {
      return false;
    }
    return hasJsonParentCycle(model, currentName, parent.getParentSourceName(), visiting);
  }

  private static String nvl(String value) {
    return Utils.isEmpty(value) ? "?" : value;
  }
}
