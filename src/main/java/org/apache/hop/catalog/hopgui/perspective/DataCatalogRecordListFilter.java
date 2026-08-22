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
package org.apache.hop.catalog.hopgui.perspective;

import java.util.List;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.i18n.BaseMessages;

/** Quick filters for the data catalog record tree. */
public enum DataCatalogRecordListFilter {
  ALL("DataCatalogPerspective.Filter.All"),
  SOURCES("DataCatalogPerspective.Filter.Sources", List.of(RecordDefinitionType.DV_SOURCE)),
  DIMENSIONAL_TABLES(
      "DataCatalogPerspective.Filter.DimensionalTables",
      List.of(RecordDefinitionType.DIM_TABLE, RecordDefinitionType.FACT_TABLE)),
  BUSINESS_VAULT_TABLES(
      "DataCatalogPerspective.Filter.BusinessVaultTables", List.of(RecordDefinitionType.BV_TABLE)),
  DATA_VAULT_TABLES(
      "DataCatalogPerspective.Filter.DataVaultTables",
      List.of(
          RecordDefinitionType.DV_HUB,
          RecordDefinitionType.DV_LINK,
          RecordDefinitionType.DV_SATELLITE)),
  MODEL_REGISTRY(
      "DataCatalogPerspective.Filter.ModelRegistry",
      List.of(
          RecordDefinitionType.DV_MODEL,
          RecordDefinitionType.BV_MODEL,
          RecordDefinitionType.DM_MODEL)),
  OPERATIONS("DataCatalogPerspective.Filter.Operations", "operations"),
  LOAD_METRICS("DataCatalogPerspective.Filter.LoadMetrics", "load-metrics");

  private final String labelKey;
  private final List<RecordDefinitionType> types;
  private final String tag;

  DataCatalogRecordListFilter(String labelKey) {
    this(labelKey, List.of(), null);
  }

  DataCatalogRecordListFilter(String labelKey, String tag) {
    this(labelKey, List.of(), tag);
  }

  DataCatalogRecordListFilter(String labelKey, List<RecordDefinitionType> types) {
    this(labelKey, types, null);
  }

  DataCatalogRecordListFilter(String labelKey, List<RecordDefinitionType> types, String tag) {
    this.labelKey = labelKey;
    this.types = types;
    this.tag = tag;
  }

  String label() {
    return BaseMessages.getString(DataCatalogPerspective.class, labelKey);
  }

  public RecordDefinitionQuery toQuery() {
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    if (types != null && !types.isEmpty()) {
      query.getTypes().addAll(types);
    }
    if (tag != null) {
      query.getTags().add(tag);
    }
    return query;
  }

  static DataCatalogRecordListFilter fromIndex(int index) {
    DataCatalogRecordListFilter[] values = values();
    if (index < 0 || index >= values.length) {
      return ALL;
    }
    return values[index];
  }
}
