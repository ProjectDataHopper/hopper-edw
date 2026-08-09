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

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * Enumeration of supported source system types for a Data Vault model.
 *
 * <p>Each source type describes how rows are obtained (e.g. from an RDBMS table/query, a CSV file,
 * a Parquet file, etc). The common aspects (name + expected row layout via fields) live in the
 * base; type-specific connection details live in the concrete implementations (DvDatabaseSource,
 * ...).
 */
@Getter
public enum DvSourceType implements IEnumHasCodeAndDescription {
  DATABASE("DATABASE", BaseMessages.getString(DvSourceType.class, "DvSourceType.Database")),
  CSV("CSV", BaseMessages.getString(DvSourceType.class, "DvSourceType.Csv")),
  PARQUET("PARQUET", BaseMessages.getString(DvSourceType.class, "DvSourceType.Parquet")),
  ICEBERG("ICEBERG", BaseMessages.getString(DvSourceType.class, "DvSourceType.Iceberg")),
  /**
   * Multi-table feed defined by a {@code SourceQuery} in a {@code .hsm} source model (SQL or Merge
   * Join pipeline).
   */
  COMPOSITE("COMPOSITE", BaseMessages.getString(DvSourceType.class, "DvSourceType.Composite")),
  /**
   * JSON extraction feed defined by a {@code SourceJson} in a {@code .hsm} source model (parent
   * source + JsonInput).
   */
  JSON("JSON", BaseMessages.getString(DvSourceType.class, "DvSourceType.Json")),
  /**
   * Pipeline-backed feed defined by a {@code SourcePipeline} in a {@code .hsm} source model
   * (MetaInject of a user {@code .hpl}).
   */
  PIPELINE("PIPELINE", BaseMessages.getString(DvSourceType.class, "DvSourceType.Pipeline"));

  private final String code;
  private final String description;

  DvSourceType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvSourceType.class);
  }

  public static DvSourceType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(DvSourceType.class, description, DATABASE);
  }

  public static DvSourceType lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvSourceType.class, code, DATABASE);
  }
}
