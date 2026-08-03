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
package org.apache.hop.datavault.metadata.dimensional;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Staging source for a dimensional table load pipeline. */
@Getter
@Setter
public class DmSourceConfiguration {

  public static final String DEFAULT_PIPELINE_RUN_CONFIGURATION = "local";

  /** How staging rows are obtained for generated load pipelines. */
  @HopMetadataProperty private DmSourceType sourceType = DmSourceType.SQL;

  /** Optional database connection override; defaults to model source or target database. */
  @HopMetadataProperty private String sourceConnection;

  /** SQL executed by the generated source {@code TableInput}. */
  @HopMetadataProperty private String sourceSql;

  /** Hop pipeline file providing staging rows when {@link DmSourceType#PIPELINE} is selected. */
  @HopMetadataProperty private String sourcePipelineFile;

  /** Transform in {@link #sourcePipelineFile} whose output rows feed the generated pipeline. */
  @HopMetadataProperty private String sourcePipelineTransform;

  /** Run configuration for generated {@code MetaInject} transforms; defaults to {@code local}. */
  @HopMetadataProperty private String sourcePipelineRunConfiguration;

  /** Data catalog connection when {@link DmSourceType#RECORD_DEFINITION} is selected. */
  @HopMetadataProperty private String sourceCatalogConnection;

  /** Catalog namespace of the staging record definition. */
  @HopMetadataProperty private String sourceRecordNamespace;

  /** Catalog name of the staging record definition. */
  @HopMetadataProperty private String sourceRecordName;

  /** Fact table name when {@link DmSourceType#FACT_TABLE} is selected (junk dimensions). */
  @HopMetadataProperty private String sourceFactTableName;

  public DmSourceType resolveSourceType() {
    return sourceType != null ? sourceType : DmSourceType.SQL;
  }

  public boolean isSqlSource() {
    return resolveSourceType() == DmSourceType.SQL;
  }

  public boolean isPipelineSource() {
    return resolveSourceType() == DmSourceType.PIPELINE;
  }

  public boolean isRecordDefinitionSource() {
    return resolveSourceType() == DmSourceType.RECORD_DEFINITION;
  }

  public boolean isFactTableSource() {
    return resolveSourceType() == DmSourceType.FACT_TABLE;
  }

  public String resolveSourceConnection(DimensionalConfiguration config, IVariables variables) {
    String connection = sourceConnection;
    if (variables != null) {
      connection = variables.resolve(connection);
    }
    if (!Utils.isEmpty(connection)) {
      return connection;
    }
    if (config != null && !Utils.isEmpty(config.getSourceDatabase())) {
      String sourceDb = config.getSourceDatabase();
      if (variables != null) {
        sourceDb = variables.resolve(sourceDb);
      }
      if (!Utils.isEmpty(sourceDb)) {
        return sourceDb;
      }
    }
    if (config != null) {
      return config.getTargetDatabase();
    }
    return null;
  }

  public String resolveSourceSql(IVariables variables) {
    String sql = sourceSql;
    if (variables != null) {
      sql = variables.resolve(sql);
    }
    return sql;
  }

  public String resolveSourcePipelineFile(IVariables variables) {
    String filename = sourcePipelineFile;
    if (variables != null) {
      filename = variables.resolve(filename);
    }
    return filename;
  }

  public String resolveSourcePipelineTransform(IVariables variables) {
    String transform = sourcePipelineTransform;
    if (variables != null) {
      transform = variables.resolve(transform);
    }
    return transform;
  }

  public String resolveSourcePipelineRunConfiguration(IVariables variables) {
    String runConfiguration = sourcePipelineRunConfiguration;
    if (variables != null) {
      runConfiguration = variables.resolve(runConfiguration);
    }
    if (Utils.isEmpty(runConfiguration)) {
      return DEFAULT_PIPELINE_RUN_CONFIGURATION;
    }
    return runConfiguration;
  }

  public String resolveSourceCatalogConnection(IVariables variables) {
    String connection = sourceCatalogConnection;
    if (variables != null) {
      connection = variables.resolve(connection);
    }
    return connection;
  }

  public String resolveSourceRecordNamespace(IVariables variables) {
    String namespace = sourceRecordNamespace;
    if (variables != null) {
      namespace = variables.resolve(namespace);
    }
    return namespace;
  }

  public String resolveSourceRecordName(IVariables variables) {
    String name = sourceRecordName;
    if (variables != null) {
      name = variables.resolve(name);
    }
    return name;
  }

  public String resolveSourceFactTableName(IVariables variables) {
    String name = sourceFactTableName;
    if (variables != null) {
      name = variables.resolve(name);
    }
    return name;
  }
}
