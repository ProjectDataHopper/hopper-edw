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
package org.apache.hop.datavault.resourcedefinition;

/**
 * Parameters for {@link SchemaImpactSimulationService}.
 *
 * @param resourceDefinitionGroup group name (required when group meta is not supplied)
 * @param catalogVersionTag expected contract for LIVE_SOURCE / actual for VERSION_VS_VERSION
 * @param compareMode comparison strategy
 * @param baselineVersionTag expected side for WORKING_VS_VERSION and VERSION_VS_VERSION
 * @param includeImpact attach blast-radius labels via impact graph
 * @param detailedDataTypeChecking full type/length/PK vs types-only
 * @param checkTargetModels compare baseline contract field metadata to mapped model attributes
 * @param checkTargetDatabases detect target EDW tables that need DDL relative to models
 * @param checkCatalogVsVersion when true with LIVE_SOURCE, also run working-vs-baseline version
 *     axis
 * @param expectAutomaticTargetTableCreation when true, missing-table CREATE findings are omitted
 *     (not reported) because vault update will create the tables; ALTER drift still warns
 */
public record SchemaImpactSimulationRequest(
    String resourceDefinitionGroup,
    String catalogVersionTag,
    SchemaCompareMode compareMode,
    String baselineVersionTag,
    boolean includeImpact,
    boolean detailedDataTypeChecking,
    boolean checkTargetModels,
    boolean checkTargetDatabases,
    boolean checkCatalogVsVersion,
    boolean expectAutomaticTargetTableCreation) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String resourceDefinitionGroup;
    private String catalogVersionTag;
    private SchemaCompareMode compareMode = SchemaCompareMode.LIVE_SOURCE;
    private String baselineVersionTag;
    private boolean includeImpact = true;
    private boolean detailedDataTypeChecking = true;
    private boolean checkTargetModels;
    private boolean checkTargetDatabases;
    private boolean checkCatalogVsVersion;
    private boolean expectAutomaticTargetTableCreation;

    public Builder resourceDefinitionGroup(String resourceDefinitionGroup) {
      this.resourceDefinitionGroup = resourceDefinitionGroup;
      return this;
    }

    public Builder catalogVersionTag(String catalogVersionTag) {
      this.catalogVersionTag = catalogVersionTag;
      return this;
    }

    public Builder compareMode(SchemaCompareMode compareMode) {
      this.compareMode = compareMode != null ? compareMode : SchemaCompareMode.LIVE_SOURCE;
      return this;
    }

    public Builder baselineVersionTag(String baselineVersionTag) {
      this.baselineVersionTag = baselineVersionTag;
      return this;
    }

    public Builder includeImpact(boolean includeImpact) {
      this.includeImpact = includeImpact;
      return this;
    }

    public Builder detailedDataTypeChecking(boolean detailedDataTypeChecking) {
      this.detailedDataTypeChecking = detailedDataTypeChecking;
      return this;
    }

    public Builder checkTargetModels(boolean checkTargetModels) {
      this.checkTargetModels = checkTargetModels;
      return this;
    }

    public Builder checkTargetDatabases(boolean checkTargetDatabases) {
      this.checkTargetDatabases = checkTargetDatabases;
      return this;
    }

    public Builder checkCatalogVsVersion(boolean checkCatalogVsVersion) {
      this.checkCatalogVsVersion = checkCatalogVsVersion;
      return this;
    }

    public Builder expectAutomaticTargetTableCreation(boolean expectAutomaticTargetTableCreation) {
      this.expectAutomaticTargetTableCreation = expectAutomaticTargetTableCreation;
      return this;
    }

    public SchemaImpactSimulationRequest build() {
      return new SchemaImpactSimulationRequest(
          resourceDefinitionGroup,
          catalogVersionTag,
          compareMode != null ? compareMode : SchemaCompareMode.LIVE_SOURCE,
          baselineVersionTag,
          includeImpact,
          detailedDataTypeChecking,
          checkTargetModels,
          checkTargetDatabases,
          checkCatalogVsVersion,
          expectAutomaticTargetTableCreation);
    }
  }
}
