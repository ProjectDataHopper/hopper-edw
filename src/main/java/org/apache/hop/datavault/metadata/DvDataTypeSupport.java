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

import org.apache.hop.core.Const;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;

/**
 * Resolves Hop value-meta type ids from model field data-type labels.
 *
 * <p>Model attributes and business keys often store either:
 *
 * <ul>
 *   <li>Hop type names ({@code Integer}, {@code String}, …) from manual entry / remediation, or
 *   <li>Native SQL types ({@code int2}, {@code varchar}, …) copied by “Get attributes/keys” from
 *       {@link SourceField#getSourceDataType()}.
 * </ul>
 *
 * Only Hop names resolve via {@link ValueMetaFactory#getIdForValueMeta(String)}. When the label is
 * a SQL type, use the matching source field’s {@link SourceField#getHopType()} instead of
 * defaulting to String (which produced false type-mismatch validation errors).
 */
public final class DvDataTypeSupport {

  private DvDataTypeSupport() {}

  /**
   * Resolve a Hop type id from a free-text data type label, optionally using a source field when
   * the label is not a known Hop type name.
   *
   * @param dataType Hop type name or native SQL type (may be null/empty)
   * @param sourceField optional matching source field (hop type fallback)
   * @return a positive Hop type id, or {@link IValueMeta#TYPE_STRING} when unresolved
   */
  public static int resolveHopTypeId(String dataType, SourceField sourceField) {
    if (!Utils.isEmpty(dataType)) {
      int typeId = ValueMetaFactory.getIdForValueMeta(dataType.trim());
      if (typeId > 0) {
        return typeId;
      }
    }
    if (sourceField != null && sourceField.getHopType() > 0) {
      return sourceField.getHopType();
    }
    return IValueMeta.TYPE_STRING;
  }

  /**
   * Preferred label to store on satellite attributes / hub business keys when copying from a source
   * field: Hop type name when known, otherwise the native SQL type.
   */
  public static String preferredDataTypeLabel(SourceField sourceField) {
    if (sourceField == null) {
      return "";
    }
    if (sourceField.getHopType() > 0) {
      try {
        String hopTypeName = ValueMetaFactory.getValueMetaName(sourceField.getHopType());
        if (!Utils.isEmpty(hopTypeName) && !"-".equals(hopTypeName)) {
          return hopTypeName;
        }
      } catch (Exception ignored) {
        // Fall through to source SQL type.
      }
    }
    return Const.NVL(sourceField.getSourceDataType(), "");
  }
}
