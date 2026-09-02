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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableResolutionSupport;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;

/** Pure helpers for the SCD2 field-mapping dialog. */
public final class BvScd2FieldMappingDialogSupport {

  private BvScd2FieldMappingDialogSupport() {}

  /**
   * One SCD2 input name after lookup: a DV satellite (or linked satellite) or a BV source query.
   */
  public record SatelliteResolution(
      String requestedName,
      DvSatellite satellite,
      List<String> attributeNames,
      BvSourceQuery sourceQuery) {

    public SatelliteResolution {
      attributeNames = attributeNames != null ? List.copyOf(attributeNames) : List.of();
    }

    public SatelliteResolution(
        String requestedName, DvSatellite satellite, List<String> attributeNames) {
      this(requestedName, satellite, attributeNames, null);
    }

    public boolean resolved() {
      return satellite != null || sourceQuery != null;
    }

    public boolean hasAttributes() {
      return !attributeNames.isEmpty();
    }
  }

  /** Outcome of Suggest mappings / Field mappings diagnostics. Never an empty silent list. */
  public record MappingSuggestion(
      boolean dvModelPresent,
      int dvTableCount,
      String dvModelFilename,
      List<SatelliteResolution> satellites,
      List<BvScd2FieldMapping> suggestedMappings,
      int alreadyMappedCount) {

    public MappingSuggestion {
      satellites = satellites != null ? List.copyOf(satellites) : List.of();
      suggestedMappings = suggestedMappings != null ? List.copyOf(suggestedMappings) : List.of();
    }

    public List<String> resolvedNames() {
      List<String> names = new ArrayList<>();
      for (SatelliteResolution satellite : satellites) {
        if (satellite.resolved()) {
          names.add(satellite.requestedName());
        }
      }
      return names;
    }

    public List<String> missingNames() {
      List<String> names = new ArrayList<>();
      for (SatelliteResolution satellite : satellites) {
        if (!satellite.resolved()) {
          names.add(satellite.requestedName());
        }
      }
      return names;
    }

    public List<String> emptyAttributeNames() {
      List<String> names = new ArrayList<>();
      for (SatelliteResolution satellite : satellites) {
        if (satellite.resolved() && !satellite.hasAttributes()) {
          names.add(satellite.requestedName());
        }
      }
      return names;
    }

    public String attributeCountSummary() {
      StringBuilder builder = new StringBuilder();
      for (SatelliteResolution satellite : satellites) {
        if (builder.length() > 0) {
          builder.append(", ");
        }
        builder
            .append(satellite.requestedName())
            .append(": ")
            .append(satellite.attributeNames().size());
      }
      return builder.toString();
    }
  }

  public static List<String> satelliteDerivativeNames(
      BvScd2Table scd2Table, DataVaultModel dataVaultModel) {
    return satelliteDerivativeNames(scd2Table, dataVaultModel, null, null);
  }

  public static List<String> satelliteDerivativeNames(
      BvScd2Table scd2Table,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return satelliteDerivativeNames(scd2Table, dataVaultModel, null, variables, metadataProvider);
  }

  public static List<String> satelliteDerivativeNames(
      BvScd2Table scd2Table,
      DataVaultModel dataVaultModel,
      BusinessVaultModel bvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<String> names = new ArrayList<>();
    for (SatelliteResolution satellite :
        resolveSatellites(scd2Table, dataVaultModel, bvModel, variables, metadataProvider)) {
      names.add(satellite.requestedName());
    }
    return names;
  }

  public static List<String> satelliteAttributeNames(
      String satelliteName, DataVaultModel dataVaultModel) {
    return satelliteAttributeNames(satelliteName, dataVaultModel, null, null);
  }

  public static List<String> satelliteAttributeNames(
      String satelliteName,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return satelliteAttributeNames(
        satelliteName, dataVaultModel, null, variables, metadataProvider);
  }

  public static List<String> satelliteAttributeNames(
      String satelliteName,
      DataVaultModel dataVaultModel,
      BusinessVaultModel bvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    DvSatellite satellite =
        resolveSatellite(dataVaultModel, satelliteName, variables, metadataProvider);
    if (satellite != null) {
      return attributeNamesOf(satellite);
    }
    BvSourceQuery sourceQuery =
        BusinessVaultSourceQuerySupport.findSourceQuery(bvModel, satelliteName);
    if (sourceQuery != null) {
      return BvSourceQuerySqlSupport.attributeFieldNames(sourceQuery, variables);
    }
    return List.of();
  }

  public static List<BvScd2FieldMapping> suggestMappings(
      BvScd2Table scd2Table, DataVaultModel dataVaultModel) {
    return analyze(scd2Table, dataVaultModel, null, null, null).suggestedMappings();
  }

  public static List<BvScd2FieldMapping> suggestMappings(
      BvScd2Table scd2Table, DataVaultModel dataVaultModel, BusinessVaultModel bvModel) {
    return analyze(scd2Table, dataVaultModel, bvModel, null, null).suggestedMappings();
  }

  public static MappingSuggestion analyze(
      BvScd2Table scd2Table,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return analyze(scd2Table, dataVaultModel, null, variables, metadataProvider);
  }

  public static MappingSuggestion analyze(
      BvScd2Table scd2Table,
      DataVaultModel dataVaultModel,
      BusinessVaultModel bvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    boolean dvPresent = dataVaultModel != null;
    int tableCount =
        dvPresent && dataVaultModel.getTables() != null ? dataVaultModel.getTables().size() : 0;
    String filename = dvPresent ? dataVaultModel.getFilename() : null;
    List<SatelliteResolution> satellites =
        resolveSatellites(scd2Table, dataVaultModel, bvModel, variables, metadataProvider);

    Set<String> existingKeys = new LinkedHashSet<>();
    Set<String> usedTargets = new HashSet<>();
    int alreadyMapped = 0;
    if (scd2Table != null && scd2Table.getFieldMappings() != null) {
      for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
        if (mapping == null) {
          continue;
        }
        existingKeys.add(mappingKey(mapping.getSatelliteName(), mapping.getSourceFieldName()));
        if (!Utils.isEmpty(mapping.getTargetFieldName())) {
          usedTargets.add(mapping.getTargetFieldName());
        }
        if (!Utils.isEmpty(mapping.getSatelliteName())
            && !Utils.isEmpty(mapping.getSourceFieldName())
            && !Utils.isEmpty(mapping.getTargetFieldName())) {
          alreadyMapped++;
        }
      }
    }

    List<BvScd2FieldMapping> suggestions = new ArrayList<>();
    for (SatelliteResolution satellite : satellites) {
      if (!satellite.resolved()) {
        continue;
      }
      for (String sourceFieldName : satellite.attributeNames()) {
        String key = mappingKey(satellite.requestedName(), sourceFieldName);
        if (existingKeys.contains(key)) {
          continue;
        }
        String targetFieldName =
            suggestTargetFieldName(satellite.requestedName(), sourceFieldName, usedTargets);
        usedTargets.add(targetFieldName);
        existingKeys.add(key);
        suggestions.add(
            new BvScd2FieldMapping(satellite.requestedName(), sourceFieldName, targetFieldName));
      }
    }
    return new MappingSuggestion(
        dvPresent, tableCount, filename, satellites, suggestions, alreadyMapped);
  }

  public static List<SatelliteResolution> resolveSatellites(
      BvScd2Table scd2Table,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return resolveSatellites(scd2Table, dataVaultModel, null, variables, metadataProvider);
  }

  public static List<SatelliteResolution> resolveSatellites(
      BvScd2Table scd2Table,
      DataVaultModel dataVaultModel,
      BusinessVaultModel bvModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<SatelliteResolution> resolved = new ArrayList<>();
    if (scd2Table == null) {
      return resolved;
    }
    Set<String> seen = new LinkedHashSet<>();
    for (BvDerivativeRef derivative : scd2Table.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      if (derivative.getDvTableType() != null
          && derivative.getDvTableType() != DvTableType.SATELLITE
          && !derivative.getDvTableType().isLinkedTable()) {
        continue;
      }
      String name = derivative.getDvTableName();
      if (!seen.add(name)) {
        continue;
      }
      DvSatellite satellite = resolveSatellite(dataVaultModel, name, variables, metadataProvider);
      resolved.add(new SatelliteResolution(name, satellite, attributeNamesOf(satellite)));
    }
    for (BvSourceQueryRef ref : scd2Table.getSourceQueryRefs()) {
      if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
        continue;
      }
      String name = ref.getSourceQueryName();
      if (!seen.add(name)) {
        continue;
      }
      BvSourceQuery sourceQuery = BusinessVaultSourceQuerySupport.findSourceQuery(bvModel, name);
      List<String> attributes =
          sourceQuery != null
              ? BvSourceQuerySqlSupport.attributeFieldNames(sourceQuery, variables)
              : List.of();
      resolved.add(new SatelliteResolution(name, null, attributes, sourceQuery));
    }
    return resolved;
  }

  public static DvSatellite resolveSatellite(
      DataVaultModel dataVaultModel,
      String satelliteName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (dataVaultModel == null || Utils.isEmpty(satelliteName)) {
      return null;
    }
    DvSatellite satellite =
        DvTableResolutionSupport.resolveSatellite(
            dataVaultModel, satelliteName, variables, metadataProvider);
    if (satellite != null) {
      return satellite;
    }
    IDvTable table = dataVaultModel.findTable(satelliteName);
    return table instanceof DvSatellite found ? found : null;
  }

  static List<String> attributeNamesOf(DvSatellite satellite) {
    List<String> names = new ArrayList<>();
    if (satellite == null || satellite.getAttributes() == null) {
      return names;
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      if (attribute != null && !Utils.isEmpty(attribute.getName())) {
        names.add(attribute.getName());
      }
    }
    return names;
  }

  public static void pruneMappingsAndConfigs(BvScd2Table scd2Table, Set<String> activeSatellites) {
    if (scd2Table == null || activeSatellites == null) {
      return;
    }
    scd2Table
        .getFieldMappings()
        .removeIf(
            mapping ->
                mapping == null
                    || Utils.isEmpty(mapping.getSatelliteName())
                    || !activeSatellites.contains(mapping.getSatelliteName()));
    scd2Table
        .getSatelliteConfigs()
        .removeIf(
            config ->
                config == null
                    || Utils.isEmpty(config.getSatelliteName())
                    || !activeSatellites.contains(config.getSatelliteName()));
  }

  public static List<BvScd2SatelliteConfig> syncSatelliteConfigs(
      BvScd2Table scd2Table, List<String> satelliteNames) {
    Map<String, BvScd2SatelliteConfig> existing = new LinkedHashMap<>();
    if (scd2Table.getSatelliteConfigs() != null) {
      for (BvScd2SatelliteConfig config : scd2Table.getSatelliteConfigs()) {
        if (config != null && !Utils.isEmpty(config.getSatelliteName())) {
          existing.putIfAbsent(config.getSatelliteName(), config);
        }
      }
    }

    List<BvScd2SatelliteConfig> synced = new ArrayList<>();
    for (String satelliteName : satelliteNames) {
      BvScd2SatelliteConfig config = existing.get(satelliteName);
      if (config == null) {
        config = new BvScd2SatelliteConfig(satelliteName);
      }
      synced.add(config);
    }
    return synced;
  }

  public static List<ICheckResult> validateForDialog(
      BvScd2Table scd2Table,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    return validateForDialog(scd2Table, businessVaultModel, dataVaultModel, variables, null);
  }

  public static List<ICheckResult> validateForDialog(
      BvScd2Table scd2Table,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (scd2Table == null) {
      return remarks;
    }
    scd2Table.check(remarks, metadataProvider, variables, businessVaultModel, dataVaultModel);
    return remarks;
  }

  public static boolean hasValidationErrors(List<ICheckResult> remarks) {
    if (remarks == null) {
      return false;
    }
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  public static String formatValidationErrors(List<ICheckResult> remarks) {
    StringBuilder builder = new StringBuilder();
    if (remarks == null) {
      return builder.toString();
    }
    for (ICheckResult remark : remarks) {
      if (remark != null && remark.getType() == ICheckResult.TYPE_RESULT_ERROR) {
        if (builder.length() > 0) {
          builder.append(System.lineSeparator());
        }
        builder.append(remark.getText());
      }
    }
    return builder.toString();
  }

  static String suggestTargetFieldName(
      String satelliteName, String sourceFieldName, Set<String> usedTargets) {
    if (Utils.isEmpty(sourceFieldName)) {
      return sourceFieldName;
    }
    if (usedTargets == null || !usedTargets.contains(sourceFieldName)) {
      return sourceFieldName;
    }
    String prefix = satelliteName;
    if (!Utils.isEmpty(prefix) && prefix.startsWith("sat_")) {
      prefix = prefix.substring(4);
    }
    String candidate = prefix + "_" + sourceFieldName;
    if (!usedTargets.contains(candidate)) {
      return candidate;
    }
    int suffix = 2;
    while (usedTargets.contains(candidate + suffix)) {
      suffix++;
    }
    return candidate + suffix;
  }

  private static String mappingKey(String satelliteName, String sourceFieldName) {
    return emptyIfNull(satelliteName) + "|" + emptyIfNull(sourceFieldName);
  }

  private static String emptyIfNull(String value) {
    return value == null ? "" : value;
  }
}
