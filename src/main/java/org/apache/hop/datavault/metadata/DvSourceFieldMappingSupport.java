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
package org.apache.hop.datavault.metadata;

import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Shared helpers for mapping source columns to Data Vault target field names. */
public final class DvSourceFieldMappingSupport {

  private static final Class<?> PKG = DvSourceFieldMappingSupport.class;

  private DvSourceFieldMappingSupport() {}

  public static String findTargetSourceFieldName(
      DataVaultConfiguration configuration, DataVaultSource recordSource, IDvTable table)
      throws HopException {
    String targetSourceFieldName =
        switch (table.getTableType()) {
          case HUB -> ((DvHub) table).getRecordSourceFieldName();
          case SATELLITE -> null;
          case LINK -> ((DvLink) table).getRecordSourceFieldName();
          case REFERENCE -> null;
          case LINKED_TABLE, TABLE_REFERENCE -> null;
        };
    if (StringUtils.isEmpty(targetSourceFieldName) && configuration != null) {
      targetSourceFieldName = configuration.getRecordSourceField();
    }
    if (StringUtils.isEmpty(targetSourceFieldName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvSourceFieldMapping.MissingRecordSourceFieldName",
              table.getTableType(),
              table.getName()));
    }
    return targetSourceFieldName;
  }

  /** Resolves the in-stream / target record source column name for hub and link tables. */
  public static String resolveRecordSourceFieldName(
      DataVaultConfiguration configuration, IDvTable table, IVariables variables)
      throws HopException {
    String fieldName = findTargetSourceFieldName(configuration, null, table);
    if (variables != null && !Utils.isEmpty(fieldName)) {
      fieldName = variables.resolve(fieldName);
    }
    if (Utils.isEmpty(fieldName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvSourceFieldMapping.MissingRecordSourceFieldName",
              table.getTableType(),
              table.getName()));
    }
    return fieldName;
  }

  /**
   * Resolves the record source column name for a satellite pipeline, using the parent hub or link
   * override when present (hub satellites alias the indicator with the hub column name in source
   * SQL).
   */
  public static String resolveRecordSourceFieldNameForSatellite(
      DataVaultConfiguration configuration,
      DataVaultModel model,
      DvSatellite satellite,
      IVariables variables)
      throws HopException {
    IDvTable fieldNameSource = satellite;
    if (satellite != null && model != null) {
      if (!Utils.isEmpty(satellite.getHubName())) {
        DvHub hub = model.findHub(satellite.getHubName(), variables, null);
        if (hub != null) {
          fieldNameSource = hub;
        }
      } else if (!Utils.isEmpty(satellite.getLinkName())) {
        DvLink link = model.findLink(satellite.getLinkName(), variables, null);
        if (link != null) {
          fieldNameSource = link;
        }
      }
    }
    return resolveRecordSourceFieldName(configuration, fieldNameSource, variables);
  }

  public static String resolveRecordSourceValue(DataVaultSource recordSource) throws HopException {
    String sourceFieldName = recordSource.getSourceIndicatorField();
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      return null;
    }
    String sourceIndicator = recordSource.getSourceIndicator();
    if (StringUtils.isEmpty(sourceIndicator)) {
      throw new HopException(
          "Please specify a static source indicator or a field in source "
              + recordSource.getName());
    }
    return sourceIndicator;
  }

  /**
   * Whether the given vault table materializes a record-source / source-indicator column. Hubs,
   * links, and reference tables always do; satellites may opt out ({@link
   * DvSatellite#isStoreRecordSource()}).
   */
  public static boolean shouldStoreRecordSource(IDvTable table) {
    if (table instanceof DvSatellite satellite) {
      return satellite.isStoreRecordSource();
    }
    return true;
  }
}
