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
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Common abstract base for embedded physical source definitions (Database, CSV, Parquet, etc.). */
public abstract class DvSourceBase implements IDvSource {

  @HopMetadataProperty protected String description;

  @HopMetadataProperty(key = "field", groupKey = "fields")
  protected List<SourceField> fields = new ArrayList<>();

  @HopMetadataProperty(storeWithCode = true)
  protected DvSourceType sourceType;

  protected final ChangedFlag changedFlag = new ChangedFlag();

  protected DvSourceBase() {}

  @Override
  public String getName() {
    return null;
  }

  @Override
  public void setName(String name) {
    // Name is owned by the parent DataVaultSource metadata object when embedded.
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    if (!java.util.Objects.equals(this.description, description)) {
      setChanged();
    }
    this.description = description;
  }

  @Override
  public List<SourceField> getFields() {
    return fields;
  }

  @Override
  public void setFields(List<SourceField> fields) {
    if (!java.util.Objects.equals(this.fields, fields)) {
      setChanged();
    }
    this.fields = fields;
  }

  @Override
  public DvSourceType getSourceType() {
    return sourceType;
  }

  public void setSourceType(DvSourceType sourceType) {
    if (!java.util.Objects.equals(this.sourceType, sourceType)) {
      setChanged();
    }
    this.sourceType = sourceType;
  }

  public String getEffectiveDescription() {
    if (description != null && !description.isBlank()) {
      return description;
    }
    return getName();
  }

  @Override
  public boolean hasChanged() {
    return changedFlag.hasChanged();
  }

  @Override
  public void setChanged() {
    changedFlag.setChanged();
  }

  @Override
  public void setChanged(boolean ch) {
    changedFlag.setChanged(ch);
  }

  @Override
  public void clearChanged() {
    changedFlag.clearChanged();
  }

  @Override
  public List<RowMetaAndData> previewRecords(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    throw new HopException(
        "Preview is not supported for Data Vault source type "
            + (sourceType != null ? sourceType.name() : "unknown"));
  }
}
