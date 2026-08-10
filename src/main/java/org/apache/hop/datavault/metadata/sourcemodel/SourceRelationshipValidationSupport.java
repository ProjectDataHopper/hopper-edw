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
package org.apache.hop.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Structural checks for a {@link SourceRelationship} (endpoints and join columns). */
public final class SourceRelationshipValidationSupport {

  private static final Class<?> PKG = SourceModel.class;

  private SourceRelationshipValidationSupport() {}

  public static List<ICheckResult> check(SourceModel model, SourceRelationship relationship) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (relationship == null) {
      return remarks;
    }
    String relName = nvl(relationship.getName());

    if (!SourceEndpointSupport.exists(
        model, relationship.resolveChildEndpointKind(), relationship.getChildTableName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.RelationshipMissingChild",
                  relName,
                  SourceEndpointSupport.displayName(
                      relationship.resolveChildEndpointKind(), relationship.getChildTableName())),
              null));
    }
    if (!SourceEndpointSupport.exists(
        model, relationship.resolveParentEndpointKind(), relationship.getParentTableName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.RelationshipMissingParent",
                  relName,
                  SourceEndpointSupport.displayName(
                      relationship.resolveParentEndpointKind(), relationship.getParentTableName())),
              null));
    }
    if (!relationship.isValid()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.RelationshipInvalidColumns", relName),
              null));
      return remarks;
    }

    Set<String> childFields =
        toLowerSet(
            SourceEndpointSupport.fieldNames(
                model, relationship.resolveChildEndpointKind(), relationship.getChildTableName()));
    Set<String> parentFields =
        toLowerSet(
            SourceEndpointSupport.fieldNames(
                model,
                relationship.resolveParentEndpointKind(),
                relationship.getParentTableName()));

    // When an endpoint has no columns yet, skip "missing column" noise (empty layout warned
    // elsewhere).
    boolean checkChild = !childFields.isEmpty();
    boolean checkParent = !parentFields.isEmpty();
    List<String> childColumns = relationship.getChildColumns();
    List<String> parentColumns = relationship.getParentColumns();
    for (int i = 0; i < childColumns.size(); i++) {
      String childCol = childColumns.get(i);
      String parentCol = parentColumns.get(i);
      if (checkChild && !Utils.isEmpty(childCol) && !childFields.contains(normalize(childCol))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.RelationshipChildColumnMissing",
                    relName,
                    childCol,
                    SourceEndpointSupport.displayName(
                        relationship.resolveChildEndpointKind(), relationship.getChildTableName())),
                null));
      }
      if (checkParent
          && !Utils.isEmpty(parentCol)
          && !parentFields.contains(normalize(parentCol))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.RelationshipParentColumnMissing",
                    relName,
                    parentCol,
                    SourceEndpointSupport.displayName(
                        relationship.resolveParentEndpointKind(),
                        relationship.getParentTableName())),
                null));
      }
    }
    return remarks;
  }

  private static Set<String> toLowerSet(List<String> names) {
    Set<String> set = new HashSet<>();
    if (names == null) {
      return set;
    }
    for (String name : names) {
      if (!Utils.isEmpty(name)) {
        set.add(normalize(name));
      }
    }
    return set;
  }

  private static String normalize(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }

  private static String nvl(String value) {
    return Utils.isEmpty(value) ? "?" : value;
  }
}
