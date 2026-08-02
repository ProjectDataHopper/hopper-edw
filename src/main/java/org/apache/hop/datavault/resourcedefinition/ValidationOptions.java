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

import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFileWriter.ReportFormat;

/**
 * Design-time choices for resource definition validation: what is the baseline (truth) and which
 * axes to check. Maps onto {@link SchemaImpactSimulationRequest}.
 */
public record ValidationOptions(
    BaselineKind baselineKind,
    String baselineVersionTag,
    String targetVersionTag,
    boolean checkLiveSources,
    boolean checkCatalogVsVersion,
    boolean checkTargetModels,
    boolean checkTargetDatabases,
    /**
     * When target DB check is on: missing tables (CREATE DDL applied by vault update) are omitted
     * from findings. Layout drift on existing tables still warns.
     */
    boolean expectAutomaticTargetTableCreation,
    boolean includeImpact,
    boolean writeReport,
    String reportOutputPath,
    String reportFileBaseName,
    ReportFormat reportFormat) {

  public enum BaselineKind {
    /** Working-tree catalog is the contract of record (common after drift notification). */
    WORKING_CATALOG,
    /** Frozen catalog version tag is the contract of record (immutable). */
    CATALOG_VERSION
  }

  public ValidationOptions {
    baselineKind = baselineKind != null ? baselineKind : BaselineKind.WORKING_CATALOG;
    reportFormat = reportFormat != null ? reportFormat : ReportFormat.BOTH;
  }

  public static ValidationOptions defaults() {
    return new ValidationOptions(
        BaselineKind.WORKING_CATALOG,
        null,
        null,
        true,
        false,
        true,
        true,
        false,
        true,
        false,
        null,
        null,
        ReportFormat.BOTH);
  }

  /**
   * Maps GUI options onto the simulation request compare mode and version tags.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Live sources checked, baseline = working catalog → {@code LIVE_SOURCE}
   *   <li>Live sources checked, baseline = version → {@code LIVE_SOURCE} with expected = tag
   *   <li>Catalog-vs-version only → {@code WORKING_VS_VERSION}
   *   <li>Both live and catalog-vs-version: prefer live first (LIVE_SOURCE); version drift is a
   *       separate axis when {@code checkCatalogVsVersion} is true with a baseline tag
   * </ul>
   */
  public SchemaImpactSimulationRequest toSimulationRequest(
      String resourceDefinitionGroup, boolean detailedDataTypeChecking) {
    SchemaCompareMode mode;
    String catalogVersionTag = null;
    String baselineTag = trimToNull(baselineVersionTag);
    String targetTag = trimToNull(targetVersionTag);

    if (checkLiveSources) {
      mode = SchemaCompareMode.LIVE_SOURCE;
      if (baselineKind == BaselineKind.CATALOG_VERSION) {
        catalogVersionTag = baselineTag;
      }
    } else if (checkCatalogVsVersion && baselineTag != null && targetTag != null) {
      mode = SchemaCompareMode.VERSION_VS_VERSION;
      catalogVersionTag = targetTag;
    } else if (checkCatalogVsVersion && baselineTag != null) {
      mode = SchemaCompareMode.WORKING_VS_VERSION;
    } else if (checkTargetModels || checkTargetDatabases) {
      // Model/DB axes only: still need a report skeleton; use working vs nothing as LIVE without
      // discovery by running LIVE_SOURCE against working catalog (fast path when sources unreachable
      // is handled by service). Prefer WORKING_VS_VERSION only when a baseline tag is set.
      if (baselineKind == BaselineKind.CATALOG_VERSION && baselineTag != null) {
        mode = SchemaCompareMode.WORKING_VS_VERSION;
      } else {
        mode = SchemaCompareMode.LIVE_SOURCE;
      }
    } else {
      mode = SchemaCompareMode.LIVE_SOURCE;
    }

    return SchemaImpactSimulationRequest.builder()
        .resourceDefinitionGroup(resourceDefinitionGroup)
        .compareMode(mode)
        .catalogVersionTag(catalogVersionTag)
        .baselineVersionTag(baselineTag)
        .includeImpact(includeImpact)
        .detailedDataTypeChecking(detailedDataTypeChecking)
        .checkTargetModels(checkTargetModels)
        .checkTargetDatabases(checkTargetDatabases)
        .checkCatalogVsVersion(checkCatalogVsVersion && checkLiveSources)
        .expectAutomaticTargetTableCreation(expectAutomaticTargetTableCreation)
        .build();
  }

  public String describeBaseline() {
    if (baselineKind == BaselineKind.CATALOG_VERSION && !Utils.isEmpty(baselineVersionTag)) {
      return "catalog version '" + baselineVersionTag.trim() + "' (immutable snapshot)";
    }
    return "current working catalog (contract of record for this run)";
  }

  public String describeAxes() {
    StringBuilder builder = new StringBuilder();
    appendAxis(builder, checkLiveSources, "live source systems");
    appendAxis(builder, checkCatalogVsVersion, "working catalog vs version");
    appendAxis(builder, checkTargetModels, "target models (DV/BV/DM)");
    appendAxis(builder, checkTargetDatabases, "target databases (DV/BV/DM)");
    appendAxis(
        builder,
        expectAutomaticTargetTableCreation,
        "expect automatic target table creation");
    appendAxis(builder, includeImpact, "downstream impact / lineage");
    return builder.isEmpty() ? "none" : builder.toString();
  }

  private static void appendAxis(StringBuilder builder, boolean enabled, String label) {
    if (!enabled) {
      return;
    }
    if (!builder.isEmpty()) {
      builder.append(", ");
    }
    builder.append(label);
  }

  private static String trimToNull(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    String trimmed = value.trim();
    return Utils.isEmpty(trimmed) ? null : trimmed;
  }
}
