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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/**
 * Resolves parent hub business-key <em>values</em> for hub satellite loads.
 *
 * <p>The hub alone defines logical business keys and hash order ({@link
 * DvHub#getDistinctBusinessKeys()}). The satellite does not re-declare or re-map those names. It
 * may only list, in that same order, which columns on its single record source carry the values
 * ({@link DvSatellite#getParentKeySourceFields()}). Empty list = same column names as the hub
 * business keys.
 */
public final class DvSatelliteParentKeySupport {

  private static final Class<?> PKG = DvSatelliteParentKeySupport.class;

  private DvSatelliteParentKeySupport() {}

  /**
   * Runtime projection: logical hub BK name (hash input after rename) and column on the satellite
   * feed. Not stored as a name→name map on the satellite.
   */
  public static final class ParentKeyField {
    private final String businessKeyName;
    private final String sourceFieldName;

    public ParentKeyField(String businessKeyName, String sourceFieldName) {
      this.businessKeyName = businessKeyName;
      this.sourceFieldName = sourceFieldName;
    }

    public String getBusinessKeyName() {
      return businessKeyName;
    }

    public String getSourceFieldName() {
      return sourceFieldName;
    }

    public boolean requiresRename() {
      return !Objects.equals(businessKeyName, sourceFieldName);
    }
  }

  /**
   * Resolve parent key fields for a hub satellite.
   *
   * @param hub parent hub (logical BK names and order)
   * @param satellite hub satellite (optional ordered {@code parentKeySourceFields})
   * @param variables variable space (may be null)
   */
  public static List<ParentKeyField> resolveParentKeyFields(
      DvHub hub, DvSatellite satellite, IVariables variables) throws HopException {
    if (hub == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSatelliteParentKeySupport.Error.HubRequired"));
    }
    List<String> logicalNames = new ArrayList<>();
    for (BusinessKey logical : hub.getDistinctBusinessKeys()) {
      if (logical == null || Utils.isEmpty(logical.getName())) {
        continue;
      }
      logicalNames.add(resolve(logical.getName(), variables));
    }
    if (logicalNames.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvSatelliteParentKeySupport.Error.NoBusinessKeys", hub.getName()));
    }

    List<String> explicitSourceFields = nonEmptySourceFields(satellite, variables);
    if (!explicitSourceFields.isEmpty() && explicitSourceFields.size() != logicalNames.size()) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvSatelliteParentKeySupport.Error.SourceFieldCountMismatch",
              satellite != null ? satellite.getName() : "?",
              hub.getName(),
              String.valueOf(explicitSourceFields.size()),
              String.valueOf(logicalNames.size())));
    }

    List<ParentKeyField> result = new ArrayList<>();
    for (int i = 0; i < logicalNames.size(); i++) {
      String businessKeyName = logicalNames.get(i);
      String sourceFieldName =
          explicitSourceFields.isEmpty() ? businessKeyName : explicitSourceFields.get(i);
      result.add(new ParentKeyField(businessKeyName, sourceFieldName));
    }
    return result;
  }

  public static List<String> resolveSourceFieldNames(
      DvHub hub, DvSatellite satellite, IVariables variables) throws HopException {
    List<String> names = new ArrayList<>();
    for (ParentKeyField field : resolveParentKeyFields(hub, satellite, variables)) {
      names.add(field.getSourceFieldName());
    }
    return names;
  }

  public static List<String> resolveBusinessKeyNames(
      DvHub hub, DvSatellite satellite, IVariables variables) throws HopException {
    List<String> names = new ArrayList<>();
    for (ParentKeyField field : resolveParentKeyFields(hub, satellite, variables)) {
      names.add(field.getBusinessKeyName());
    }
    return names;
  }

  public static List<String> quotedSelectExpressions(
      DvHub hub, DvSatellite satellite, DatabaseMeta databaseMeta, IVariables variables)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSatelliteParentKeySupport.Error.DatabaseMetaRequired"));
    }
    List<String> expressions = new ArrayList<>();
    for (ParentKeyField field : resolveParentKeyFields(hub, satellite, variables)) {
      String quotedSource = databaseMeta.quoteField(field.getSourceFieldName());
      if (field.requiresRename()) {
        expressions.add(
            quotedSource + " AS " + databaseMeta.quoteField(field.getBusinessKeyName()));
      } else {
        expressions.add(quotedSource);
      }
    }
    return expressions;
  }

  /**
   * Default source field list = hub business key names in hub order. Used by the dialog "Load
   * defaults" action.
   */
  public static List<String> defaultSourceFieldsFromHub(DvHub hub, IVariables variables) {
    List<String> fields = new ArrayList<>();
    if (hub == null) {
      return fields;
    }
    for (BusinessKey bk : hub.getDistinctBusinessKeys()) {
      if (bk == null || Utils.isEmpty(bk.getName())) {
        continue;
      }
      fields.add(resolve(bk.getName(), variables));
    }
    return fields;
  }

  /** Human-readable hub BK names in order (dialog helper text). */
  public static String formatHubKeyOrder(DvHub hub, IVariables variables) {
    List<String> names = defaultSourceFieldsFromHub(hub, variables);
    return names.isEmpty() ? "" : String.join(", ", names);
  }

  private static List<String> nonEmptySourceFields(DvSatellite satellite, IVariables variables) {
    List<String> result = new ArrayList<>();
    if (satellite == null || satellite.getParentKeySourceFields() == null) {
      return result;
    }
    for (String field : satellite.getParentKeySourceFields()) {
      if (Utils.isEmpty(field)) {
        continue;
      }
      result.add(resolve(field.trim(), variables));
    }
    return result;
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
