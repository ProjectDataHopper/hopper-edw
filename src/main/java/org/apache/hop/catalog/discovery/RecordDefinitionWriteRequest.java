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
package org.apache.hop.catalog.discovery;

import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.file.CsvFileMetadataDiscovery;

/** Parameters for writing a discovered record definition to the data catalog. */
public final class RecordDefinitionWriteRequest {

  private final String catalogConnectionName;
  private final String namespace;
  private final String name;
  private final String description;
  private final RecordDefinitionType recordType;
  private final DvSourceType sourceType;
  private final PhysicalSourceRef physicalRef;
  private final List<SourceField> fields;
  private final CsvFileMetadataDiscovery.DiscoveryResult csvDiscovery;
  private final String sourceIndicator;
  private final String sourceIndicatorField;
  private final String group;
  private final DvSourceDeliveryType deliveryType;
  private final DataVaultModel model;
  private final Date updatedAt;
  private final String workflowName;
  private final String pipelineName;

  private RecordDefinitionWriteRequest(Builder builder) {
    this.catalogConnectionName = builder.catalogConnectionName;
    this.namespace = builder.namespace;
    this.name = builder.name;
    this.description = builder.description;
    this.recordType = builder.recordType;
    this.sourceType = builder.sourceType;
    this.physicalRef = builder.physicalRef;
    this.fields = builder.fields;
    this.csvDiscovery = builder.csvDiscovery;
    this.sourceIndicator = builder.sourceIndicator;
    this.sourceIndicatorField = builder.sourceIndicatorField;
    this.group = builder.group;
    this.deliveryType = builder.deliveryType;
    this.model = builder.model;
    this.updatedAt = builder.updatedAt;
    this.workflowName = builder.workflowName;
    this.pipelineName = builder.pipelineName;
  }

  public String getCatalogConnectionName() {
    return catalogConnectionName;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public RecordDefinitionType getRecordType() {
    return recordType;
  }

  public DvSourceType getSourceType() {
    return sourceType;
  }

  public PhysicalSourceRef getPhysicalRef() {
    return physicalRef;
  }

  public List<SourceField> getFields() {
    return fields;
  }

  public CsvFileMetadataDiscovery.DiscoveryResult getCsvDiscovery() {
    return csvDiscovery;
  }

  public String getSourceIndicator() {
    return sourceIndicator;
  }

  public String getSourceIndicatorField() {
    return sourceIndicatorField;
  }

  public String getGroup() {
    return group;
  }

  public DvSourceDeliveryType getDeliveryType() {
    return deliveryType;
  }

  public DataVaultModel getModel() {
    return model;
  }

  public Date getUpdatedAt() {
    return updatedAt;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public String getPipelineName() {
    return pipelineName;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String catalogConnectionName;
    private String namespace;
    private String name;
    private String description;
    private RecordDefinitionType recordType = RecordDefinitionType.DV_SOURCE;
    private DvSourceType sourceType;
    private PhysicalSourceRef physicalRef;
    private List<SourceField> fields;
    private CsvFileMetadataDiscovery.DiscoveryResult csvDiscovery;
    private String sourceIndicator;
    private String sourceIndicatorField;
    private String group;
    private DvSourceDeliveryType deliveryType = DvSourceDeliveryType.CHANGES_ONLY;
    private DataVaultModel model;
    private Date updatedAt = new Date();
    private String workflowName;
    private String pipelineName;

    public Builder catalogConnectionName(String value) {
      this.catalogConnectionName = value;
      return this;
    }

    public Builder namespace(String value) {
      this.namespace = value;
      return this;
    }

    public Builder name(String value) {
      this.name = value;
      return this;
    }

    public Builder description(String value) {
      this.description = value;
      return this;
    }

    public Builder recordType(RecordDefinitionType value) {
      this.recordType = value;
      return this;
    }

    public Builder sourceType(DvSourceType value) {
      this.sourceType = value;
      return this;
    }

    public Builder physicalRef(PhysicalSourceRef value) {
      this.physicalRef = value;
      return this;
    }

    public Builder fields(List<SourceField> value) {
      this.fields = value;
      return this;
    }

    public Builder csvDiscovery(CsvFileMetadataDiscovery.DiscoveryResult value) {
      this.csvDiscovery = value;
      return this;
    }

    public Builder sourceIndicator(String value) {
      this.sourceIndicator = value;
      return this;
    }

    public Builder sourceIndicatorField(String value) {
      this.sourceIndicatorField = value;
      return this;
    }

    public Builder group(String value) {
      this.group = value;
      return this;
    }

    public Builder deliveryType(DvSourceDeliveryType value) {
      this.deliveryType = value;
      return this;
    }

    public Builder model(DataVaultModel value) {
      this.model = value;
      return this;
    }

    public Builder updatedAt(Date value) {
      this.updatedAt = value;
      return this;
    }

    public Builder workflowName(String value) {
      this.workflowName = value;
      return this;
    }

    public Builder pipelineName(String value) {
      this.pipelineName = value;
      return this;
    }

    public RecordDefinitionWriteRequest build() {
      return new RecordDefinitionWriteRequest(this);
    }
  }
}
