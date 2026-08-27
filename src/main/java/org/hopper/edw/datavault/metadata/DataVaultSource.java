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
package org.hopper.edw.datavault.metadata;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSource;

/**
 * In-memory Data Vault record source loaded from the data catalog: DV semantics (indicator, group)
 * plus an embedded polymorphic {@link IDvSource} for how rows are read (database table today; file
 * formats later).
 */
@Getter
@Setter
public class DataVaultSource implements IHasName {

  private String name;
  private String sourceIndicatorField;
  private String sourceIndicator;
  private String group;
  private DvSourceDeliveryType deliveryType = DvSourceDeliveryType.CHANGES_ONLY;
  private IDvSource source = new DvDatabaseSource();

  public DataVaultSource() {}

  public DataVaultSource(String name) {
    this.name = name;
  }

  public IDvSource getDvSourceOrDefault() {
    if (source == null) {
      source = new DvDatabaseSource();
    }
    return source;
  }

  public DvSourceType getSourceType() {
    DvSourceType type = getDvSourceOrDefault().getSourceType();
    return type != null ? type : DvSourceType.DATABASE;
  }

  public DvSourceDeliveryType getDeliveryTypeOrDefault() {
    return deliveryType != null ? deliveryType : DvSourceDeliveryType.CHANGES_ONLY;
  }

  public boolean isFullSnapshotFeed() {
    return getDeliveryTypeOrDefault() == DvSourceDeliveryType.FULL_SNAPSHOT;
  }

  public boolean matchesRecordSourceGroup(String groupFilter, IVariables variables) {
    if (Utils.isEmpty(groupFilter)) {
      return true;
    }
    String resolvedFilter = variables != null ? variables.resolve(groupFilter) : groupFilter;
    if (Utils.isEmpty(resolvedFilter)) {
      return true;
    }
    String resolvedGroup = group;
    if (variables != null) {
      resolvedGroup = variables.resolve(resolvedGroup);
    }
    return resolvedFilter.equals(resolvedGroup);
  }

  public String getResolvedSourceIndicator(IVariables variables) {
    String staticValue = sourceIndicator;
    if (variables != null) {
      staticValue = variables.resolve(staticValue);
    }
    if (!StringUtils.isEmpty(staticValue)) {
      return staticValue;
    }
    return null;
  }

  public List<SourceField> getFields() throws HopException {
    List<SourceField> fields = getDvSourceOrDefault().getFields();
    if (fields == null || fields.isEmpty()) {
      throw new HopException(
          "Please define source fields for Data Vault Source '"
              + getName()
              + "' (import from table or edit the fields list).");
    }
    return fields;
  }

  public List<SourceField> getFields(IHopMetadataProvider metadataProvider) throws HopException {
    return getFields();
  }

  public IDvSource getDvSource(IHopMetadataProvider metadataProvider) throws HopException {
    return getDvSourceOrDefault();
  }
}
