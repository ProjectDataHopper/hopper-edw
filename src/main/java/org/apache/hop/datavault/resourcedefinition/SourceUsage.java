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
package org.apache.hop.datavault.resourcedefinition;

import java.util.ArrayList;
import java.util.List;

/** Where a source record definition is referenced inside a DV, BV, or DM model. */
public record SourceUsage(
    String modelType,
    String modelName,
    String modelFilename,
    String modelElementName,
    String catalogConnection,
    List<String> mappedFields) {

  public SourceUsage {
    mappedFields = mappedFields != null ? List.copyOf(mappedFields) : List.of();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String modelType;
    private String modelName;
    private String modelFilename;
    private String modelElementName;
    private String catalogConnection;
    private final List<String> mappedFields = new ArrayList<>();

    public Builder modelType(String modelType) {
      this.modelType = modelType;
      return this;
    }

    public Builder modelName(String modelName) {
      this.modelName = modelName;
      return this;
    }

    public Builder modelFilename(String modelFilename) {
      this.modelFilename = modelFilename;
      return this;
    }

    public Builder modelElementName(String modelElementName) {
      this.modelElementName = modelElementName;
      return this;
    }

    public Builder catalogConnection(String catalogConnection) {
      this.catalogConnection = catalogConnection;
      return this;
    }

    public Builder mappedField(String fieldName) {
      if (fieldName != null && !fieldName.isBlank()) {
        mappedFields.add(fieldName);
      }
      return this;
    }

    public Builder mappedFields(List<String> fields) {
      if (fields != null) {
        for (String field : fields) {
          mappedField(field);
        }
      }
      return this;
    }

    public SourceUsage build() {
      return new SourceUsage(
          modelType, modelName, modelFilename, modelElementName, catalogConnection, mappedFields);
    }
  }
}
