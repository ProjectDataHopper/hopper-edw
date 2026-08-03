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
package org.apache.hop.datavault.catalog;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.SourceField;

/** Converts between catalog source fields and Data Vault {@link SourceField} / {@link IRowMeta}. */
public final class DvSourceFieldSupport {

  private DvSourceFieldSupport() {}

  public static List<CatalogSourceField> toCatalogFields(List<SourceField> fields) {
    List<CatalogSourceField> result = new ArrayList<>();
    if (fields == null) {
      return result;
    }
    for (SourceField field : fields) {
      if (field == null) {
        continue;
      }
      CatalogSourceField catalogField = new CatalogSourceField();
      catalogField.setName(field.getName());
      catalogField.setDescription(field.getDescription());
      catalogField.setSourceDataType(field.getSourceDataType());
      catalogField.setLength(field.getLength());
      catalogField.setPrecision(field.getPrecision());
      catalogField.setHopType(field.getHopType());
      catalogField.setPrimaryKeyPosition(field.getPrimaryKeyPosition());
      catalogField.setInputOptions(
          SourceFieldInputOptionsSupport.toCatalog(field.getInputOptions()));
      result.add(catalogField);
    }
    return result;
  }

  public static List<SourceField> fromRowMeta(IRowMeta rowMeta) throws HopException {
    List<SourceField> fields = new ArrayList<>();
    if (rowMeta == null || rowMeta.isEmpty()) {
      return fields;
    }
    for (org.apache.hop.core.row.IValueMeta vm : rowMeta.getValueMetaList()) {
      SourceField sf = new SourceField(vm.getName());
      sf.setDescription("");
      sf.setSourceDataType(vm.getTypeDesc());
      sf.setLength(vm.getLength() > 0 ? String.valueOf(vm.getLength()) : "");
      sf.setPrecision(vm.getPrecision() >= 0 ? String.valueOf(vm.getPrecision()) : "");
      sf.setHopType(vm.getType());
      fields.add(sf);
    }
    return fields;
  }

  public static List<SourceField> fromCatalogFields(List<CatalogSourceField> fields) {
    List<SourceField> result = new ArrayList<>();
    if (fields == null) {
      return result;
    }
    for (CatalogSourceField field : fields) {
      if (field == null) {
        continue;
      }
      SourceField sourceField = new SourceField();
      sourceField.setName(field.getName());
      sourceField.setDescription(field.getDescription());
      sourceField.setSourceDataType(field.getSourceDataType());
      sourceField.setLength(field.getLength());
      sourceField.setPrecision(field.getPrecision());
      sourceField.setHopType(field.getHopType());
      sourceField.setPrimaryKeyPosition(field.getPrimaryKeyPosition());
      sourceField.setInputOptions(
          SourceFieldInputOptionsSupport.fromCatalog(field.getInputOptions()));
      result.add(sourceField);
    }
    return result;
  }

  public static IRowMeta toRowMeta(List<SourceField> fields, IVariables variables)
      throws HopException {
    RowMeta rowMeta = new RowMeta();
    if (fields == null) {
      return rowMeta;
    }
    for (SourceField field : fields) {
      try {
        rowMeta.addValueMeta(valueMetaFromSourceField(field, variables));
      } catch (HopPluginException e) {
        throw new HopException("Unable to map source field '" + field.getName() + "'", e);
      }
    }
    return rowMeta;
  }

  public static IRowMeta toRowMetaFromCatalog(List<CatalogSourceField> fields, IVariables variables)
      throws HopException {
    return toRowMeta(fromCatalogFields(fields), variables);
  }

  private static org.apache.hop.core.row.IValueMeta valueMetaFromSourceField(
      SourceField sf, IVariables variables) throws HopPluginException {
    String name = variables != null ? variables.resolve(sf.getName()) : sf.getName();
    int type = sf.getHopType();
    if (type <= 0) {
      type = org.apache.hop.core.row.IValueMeta.TYPE_STRING;
    }
    return ValueMetaFactory.createValueMeta(
        name,
        type,
        Const.toInt(resolveVariable(variables, sf.getLength()), -1),
        Const.toInt(resolveVariable(variables, sf.getPrecision()), -1));
  }

  private static String resolveVariable(IVariables variables, String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
