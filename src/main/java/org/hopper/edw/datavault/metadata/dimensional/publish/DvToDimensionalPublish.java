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
package org.hopper.edw.datavault.metadata.dimensional.publish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.BusinessKeySource;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.DvNoteType;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBridge;
import org.hopper.edw.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmFactlessFact;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.hopper.edw.datavault.metadata.dimensional.DmTableBase;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;

/**
 * Publishes a Data Vault model to a draft Kimball dimensional model.
 *
 * <p>Output is intended for human review in the dimensional modeler before running {@code
 * ActionDimensionalUpdate}.
 */
public final class DvToDimensionalPublish {

  private static final Class<?> PKG = DvToDimensionalPublish.class;

  private static final Set<String> NUMERIC_DATA_TYPES =
      Set.of("NUMBER", "INTEGER", "BIGNUMBER", "DECIMAL", "DOUBLE", "FLOAT");

  private DvToDimensionalPublish() {}

  public static DvPublishResult publish(DataVaultModel dataVaultModel) throws HopException {
    return publish(dataVaultModel, DvPublishOptions.defaults());
  }

  public static DvPublishResult publish(DataVaultModel dataVaultModel, DvPublishOptions options)
      throws HopException {
    if (dataVaultModel == null) {
      throw new HopException(BaseMessages.getString(PKG, "DvToDimensionalPublish.Error.NoDvModel"));
    }
    DvPublishOptions resolvedOptions = options != null ? options : DvPublishOptions.defaults();
    PublishContext context = new PublishContext(dataVaultModel, resolvedOptions);
    DimensionalModel model = buildDimensionalModel(context);
    return new DvPublishResult(model, context.warnings);
  }

  private static DimensionalModel buildDimensionalModel(PublishContext context) {
    DimensionalModel model = new DimensionalModel();
    model.setNameSynchronizedWithFilename(true);
    model.setName(resolvePublishedModelName(context));
    model.setDescription(resolvePublishedDescription(context));
    model.setConfiguration(copyDimensionalConfiguration(context.dataVaultModel));
    model.setTables(new ArrayList<>());

    for (DvHub hub : context.hubs()) {
      model.getTables().add(buildDimensionFromHub(context, hub));
    }

    for (DvSatellite satellite : context.hubSatellites()) {
      if (context.satellitesPublishedAsFacts.contains(satellite.getName())) {
        model.getTables().add(buildFactFromHubSatellite(context, satellite));
      }
    }

    for (DvLink link : context.links()) {
      IDmTable publishedLinkTable = buildPublishedLinkTable(context, link);
      if (publishedLinkTable != null) {
        model.getTables().add(publishedLinkTable);
      }
    }

    for (DvSatellite satellite : context.linkSatellites()) {
      if (hasMeasures(satellite)) {
        model.getTables().add(buildFactFromLinkSatellite(context, satellite));
      }
    }

    model.getNotes().add(buildDraftReviewNote(context));
    return model;
  }

  private static DmDimension buildDimensionFromHub(PublishContext context, DvHub hub) {
    DmDimension dimension = new DmDimension();
    String dimensionName = context.dimensionNameForHub(hub.getName());
    dimension.setName(dimensionName);
    dimension.setTableName(context.dimensionTableNameForHub(hub));
    dimension.setDescription(
        BaseMessages.getString(PKG, "DvToDimensionalPublish.Dimension.Description", hub.getName()));

    for (BusinessKey businessKey : hub.getBusinessKeys()) {
      if (businessKey == null || Utils.isEmpty(businessKey.getName())) {
        continue;
      }
      dimension.getNaturalKeys().add(new DmNaturalKeyField(businessKey.getName()));
    }

    List<DvSatellite> satellites =
        context.hubSatellitesByHub.getOrDefault(hub.getName(), List.of());
    boolean historized = !satellites.isEmpty() || context.usesLoadEndDate();
    DmScdUpdatePolicy attributePolicy =
        historized ? DmScdUpdatePolicy.TYPE2 : DmScdUpdatePolicy.TYPE1;

    Set<String> reservedForFacts = context.reservedSatelliteFields(satellites);
    Set<String> addedAttributes = new LinkedHashSet<>();
    for (DvSatellite satellite : satellites) {
      for (SatelliteAttribute attribute : satellite.getAttributes()) {
        if (!isPublishableSatelliteAttribute(context, attribute, reservedForFacts)) {
          continue;
        }
        String fieldName = attribute.getName();
        if (addedAttributes.add(fieldName)) {
          dimension.getAttributes().add(new DmDimensionAttribute(fieldName, attributePolicy));
        }
      }
    }

    dimension
        .getSourceOrDefault()
        .setSourceSql(draftSourceSql(context, hub.getTableName(), hub.getName()));
    applyHubSurrogateKeyDefaults(dimension, hub, historized);
    copyLocation(dimension, hub);
    return dimension;
  }

  private static void applyHubSurrogateKeyDefaults(
      DmDimension dimension, DvHub hub, boolean historized) {
    if (!historized || dimension == null || hub == null) {
      return;
    }
    String hashKeyField = resolveHubHashKeyField(hub);
    if (Utils.isEmpty(hashKeyField)) {
      return;
    }
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    dimension.setSurrogateKeyField(hashKeyField);
    dimension.setSurrogateKeySourceField(hashKeyField);
  }

  private static String resolveHubHashKeyField(DvHub hub) {
    if (hub == null) {
      return null;
    }
    if (!Utils.isEmpty(hub.getHashKeyFieldName())) {
      return hub.getHashKeyFieldName();
    }
    for (BusinessKey businessKey : hub.getBusinessKeys()) {
      if (businessKey != null && !Utils.isEmpty(businessKey.getName())) {
        return businessKey.getName() + "_hk";
      }
    }
    return null;
  }

  private static DmFact buildFactFromHubSatellite(PublishContext context, DvSatellite satellite) {
    DvHub parentHub = context.dataVaultModel.findHub(satellite.getHubName());
    DmFact fact = new DmFact();
    fact.setName(context.factNameForSatellite(satellite));
    fact.setTableName(context.factTableNameForSatellite(satellite));
    fact.setDescription(
        BaseMessages.getString(
            PKG,
            "DvToDimensionalPublish.FactFromHubSatellite.Description",
            satellite.getName(),
            satellite.getHubName()));

    if (parentHub != null) {
      fact.getDimensionRoles().add(context.dimensionRoleForHub(null, parentHub.getName()));
      context.warn(
          BaseMessages.getString(
              PKG,
              "DvToDimensionalPublish.Warning.HubSatelliteFactGrain",
              satellite.getName(),
              parentHub.getName()));
    }

    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      if (attribute == null || Utils.isEmpty(attribute.getName())) {
        continue;
      }
      String fieldName = attribute.getName();
      if (context.matchesOtherHubBusinessKey(fieldName)) {
        String hubName = context.hubNameForBusinessKey(fieldName);
        if (!Utils.isEmpty(hubName)) {
          fact.getDimensionRoles().add(context.dimensionRoleForHub(null, hubName));
        }
        continue;
      }
      if (isMeasureAttribute(attribute)) {
        fact.getMeasures().add(new DmFactMeasure(fieldName, true));
        continue;
      }
      if (!isTechnicalField(context, fieldName)) {
        context.warn(
            BaseMessages.getString(
                PKG,
                "DvToDimensionalPublish.Warning.UnmappedSatelliteField",
                fieldName,
                satellite.getName()));
      }
    }

    fact.getSourceOrDefault()
        .setSourceSql(
            draftSourceSqlWithParentHub(context, satellite.getTableName(), satellite, parentHub));
    copyLocation(fact, satellite);
    return fact;
  }

  private static IDmTable buildPublishedLinkTable(PublishContext context, DvLink link) {
    List<DvSatellite> linkSatellites =
        context.linkSatellitesByLink.getOrDefault(link.getName(), List.of());
    boolean hasMeasures =
        linkSatellites.stream().anyMatch(DvToDimensionalPublish::hasMeasures)
            || link.isHasDescriptiveAttributes();

    if (hasMeasures && !linkSatellites.isEmpty()) {
      return null;
    }

    List<String> hubNames = link.getHubNames() != null ? link.getHubNames() : List.of();
    if (hubNames.isEmpty()) {
      context.warn(
          BaseMessages.getString(
              PKG, "DvToDimensionalPublish.Warning.LinkWithoutHubs", link.getName()));
      return null;
    }

    if (context.options.isBridgeForTwoHubLinks() && hubNames.size() == 2) {
      return buildBridgeFromLink(context, link, hubNames);
    }
    return buildFactlessFactFromLink(context, link, hubNames);
  }

  private static DmBridge buildBridgeFromLink(
      PublishContext context, DvLink link, List<String> hubNames) {
    DmBridge bridge = new DmBridge();
    bridge.setName(context.bridgeNameForLink(link));
    bridge.setTableName(context.bridgeTableNameForLink(link));
    bridge.setDescription(
        BaseMessages.getString(PKG, "DvToDimensionalPublish.Bridge.Description", link.getName()));

    for (String hubName : hubNames) {
      bridge
          .getDimensionRefs()
          .add(
              new DmBridgeDimensionRef(
                  context.dimensionNameForHub(hubName), context.foreignKeyColumnForHub(hubName)));
    }

    bridge
        .getSourceOrDefault()
        .setSourceSql(draftSourceSql(context, link.getTableName(), link.getName()));
    copyLocation(bridge, link);
    context.warn(
        BaseMessages.getString(
            PKG, "DvToDimensionalPublish.Warning.ReviewBridgeMapping", link.getName()));
    return bridge;
  }

  private static DmFactlessFact buildFactlessFactFromLink(
      PublishContext context, DvLink link, List<String> hubNames) {
    DmFactlessFact factless = new DmFactlessFact();
    factless.setName(context.factlessNameForLink(link));
    factless.setTableName(context.factlessTableNameForLink(link));
    factless.setDescription(
        BaseMessages.getString(PKG, "DvToDimensionalPublish.Factless.Description", link.getName()));

    for (String hubName : hubNames) {
      factless.getDimensionRoles().add(context.dimensionRoleForHub(link, hubName));
    }

    factless
        .getSourceOrDefault()
        .setSourceSql(draftSourceSql(context, link.getTableName(), link.getName()));
    copyLocation(factless, link);
    return factless;
  }

  private static DmFact buildFactFromLinkSatellite(PublishContext context, DvSatellite satellite) {
    DvLink parentLink = context.dataVaultModel.findLink(satellite.getLinkName());
    DmFact fact = new DmFact();
    fact.setName(context.factNameForSatellite(satellite));
    fact.setTableName(context.factTableNameForSatellite(satellite));
    fact.setDescription(
        BaseMessages.getString(
            PKG,
            "DvToDimensionalPublish.FactFromLinkSatellite.Description",
            satellite.getName(),
            satellite.getLinkName()));

    if (parentLink != null) {
      for (String hubName : parentLink.getHubNames()) {
        fact.getDimensionRoles().add(context.dimensionRoleForHub(parentLink, hubName));
      }
    }

    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      if (attribute == null || Utils.isEmpty(attribute.getName())) {
        continue;
      }
      String fieldName = attribute.getName();
      if (isTechnicalField(context, fieldName)) {
        continue;
      }
      if (context.matchesOtherHubBusinessKey(fieldName)) {
        continue;
      }
      if (isMeasureAttribute(attribute)) {
        fact.getMeasures().add(new DmFactMeasure(fieldName, true));
      } else {
        context.warn(
            BaseMessages.getString(
                PKG,
                "DvToDimensionalPublish.Warning.UnmappedSatelliteField",
                fieldName,
                satellite.getName()));
      }
    }

    fact.getSourceOrDefault()
        .setSourceSql(draftSourceSql(context, satellite.getTableName(), satellite.getName()));
    copyLocation(fact, satellite);
    return fact;
  }

  private static DvNote buildDraftReviewNote(PublishContext context) {
    DvNote note = new DvNote();
    note.setNoteType(DvNoteType.IMPORTANT);
    note.setText(
        BaseMessages.getString(
            PKG, "DvToDimensionalPublish.Note.DraftReview", context.dataVaultModel.getName()));
    note.setLocation(new Point(32, 400));
    note.setWidth(420);
    note.setHeight(64);
    return note;
  }

  private static DimensionalConfiguration copyDimensionalConfiguration(DataVaultModel dvModel) {
    DimensionalConfiguration config = new DimensionalConfiguration();
    DataVaultConfiguration dvConfig =
        dvModel.getConfiguration() != null
            ? dvModel.getConfiguration()
            : new DataVaultConfiguration();
    config.setTargetDatabase(dvConfig.getTargetDatabase());
    return config;
  }

  private static String resolvePublishedModelName(PublishContext context) {
    if (!Utils.isEmpty(context.options.getModelName())) {
      return context.options.getModelName();
    }
    String baseName = context.dataVaultModel.getName();
    if (Utils.isEmpty(baseName)) {
      baseName = "dimensional-model";
    }
    return context.options.isAppendDraftSuffix() ? baseName + "-draft" : baseName;
  }

  private static String resolvePublishedDescription(PublishContext context) {
    if (!Utils.isEmpty(context.options.getDescription())) {
      return context.options.getDescription();
    }
    return BaseMessages.getString(
        PKG, "DvToDimensionalPublish.Model.Description", context.dataVaultModel.getName());
  }

  private static String draftSourceSql(
      PublishContext context, String tableName, String dvObjectName) {
    String physicalTable = !Utils.isEmpty(tableName) ? tableName : dvObjectName;
    return BaseMessages.getString(
        PKG, "DvToDimensionalPublish.DraftSourceSql", physicalTable, dvObjectName);
  }

  private static String draftSourceSqlWithParentHub(
      PublishContext context, String tableName, DvSatellite satellite, DvHub parentHub) {
    String physicalTable = !Utils.isEmpty(tableName) ? tableName : satellite.getName();
    if (parentHub == null) {
      return draftSourceSql(context, physicalTable, satellite.getName());
    }
    return BaseMessages.getString(
        PKG,
        "DvToDimensionalPublish.DraftHubSatelliteFactSql",
        physicalTable,
        parentHub.getTableName(),
        satellite.getName());
  }

  private static void copyLocation(DmTableBase target, IDvTable source) {
    if (target == null || source == null) {
      return;
    }
    Point location = source.getLocation();
    if (location != null) {
      target.setLocation(new Point(location.x, location.y));
    }
  }

  private static boolean hasMeasures(DvSatellite satellite) {
    if (satellite == null || satellite.getAttributes() == null) {
      return false;
    }
    return satellite.getAttributes().stream().anyMatch(DvToDimensionalPublish::isMeasureAttribute);
  }

  private static boolean isMeasureAttribute(SatelliteAttribute attribute) {
    if (attribute == null || Utils.isEmpty(attribute.getDataType())) {
      return false;
    }
    return NUMERIC_DATA_TYPES.contains(attribute.getDataType().trim().toUpperCase(Locale.ROOT));
  }

  private static boolean isPublishableSatelliteAttribute(
      PublishContext context, SatelliteAttribute attribute, Set<String> reservedForFacts) {
    if (attribute == null || Utils.isEmpty(attribute.getName())) {
      return false;
    }
    String fieldName = attribute.getName();
    if (isTechnicalField(context, fieldName)) {
      return false;
    }
    if (reservedForFacts.contains(fieldName)) {
      return false;
    }
    if (isMeasureAttribute(attribute)
        && context.matchesOtherHubBusinessKeyExists(reservedForFacts)) {
      return false;
    }
    return true;
  }

  private static boolean isTechnicalField(PublishContext context, String fieldName) {
    if (Utils.isEmpty(fieldName)) {
      return true;
    }
    String normalized = fieldName.trim().toLowerCase(Locale.ROOT);
    if (normalized.endsWith("_hk") || normalized.endsWith("_hash_key")) {
      return true;
    }
    if ("load_date".equals(normalized)
        || "record_status".equals(normalized)
        || normalized.contains("record_source")) {
      return true;
    }
    DataVaultConfiguration config = context.dataVaultModel.getConfiguration();
    if (config != null) {
      if (!Utils.isEmpty(config.getLoadDateField())
          && normalized.equals(config.getLoadDateField().trim().toLowerCase(Locale.ROOT))) {
        return true;
      }
      if (!Utils.isEmpty(config.getRecordSourceField())
          && normalized.equals(config.getRecordSourceField().trim().toLowerCase(Locale.ROOT))) {
        return true;
      }
      if (!Utils.isEmpty(config.getLoadEndDateField())
          && normalized.equals(config.getLoadEndDateField().trim().toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private static final class PublishContext {
    private final DataVaultModel dataVaultModel;
    private final DvPublishOptions options;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Set<String>> businessKeysByHub = new LinkedHashMap<>();
    private final Map<String, String> dimensionNameByHub = new LinkedHashMap<>();
    private final Map<String, List<DvSatellite>> hubSatellitesByHub = new LinkedHashMap<>();
    private final Map<String, List<DvSatellite>> linkSatellitesByLink = new LinkedHashMap<>();
    private final Set<String> satellitesPublishedAsFacts = new LinkedHashSet<>();
    private final Map<String, String> businessKeyToHub = new HashMap<>();

    private PublishContext(DataVaultModel dataVaultModel, DvPublishOptions options) {
      this.dataVaultModel = dataVaultModel;
      this.options = options;
      indexModel();
      classifyHubSatelliteFacts();
    }

    private void indexModel() {
      for (IDvTable table : dataVaultModel.getTables()) {
        if (table instanceof DvHub hub) {
          Set<String> businessKeys = new LinkedHashSet<>();
          for (BusinessKey businessKey : hub.getBusinessKeys()) {
            if (businessKey != null && !Utils.isEmpty(businessKey.getName())) {
              businessKeys.add(businessKey.getName());
              businessKeyToHub.put(businessKey.getName(), hub.getName());
            }
          }
          businessKeysByHub.put(hub.getName(), businessKeys);
          dimensionNameByHub.put(hub.getName(), dimensionNameForHub(hub.getName()));
        }
      }

      for (IDvTable table : dataVaultModel.getTables()) {
        if (!(table instanceof DvSatellite satellite)) {
          continue;
        }
        if (!Utils.isEmpty(satellite.getHubName())) {
          hubSatellitesByHub
              .computeIfAbsent(satellite.getHubName(), key -> new ArrayList<>())
              .add(satellite);
        }
        if (!Utils.isEmpty(satellite.getLinkName())) {
          linkSatellitesByLink
              .computeIfAbsent(satellite.getLinkName(), key -> new ArrayList<>())
              .add(satellite);
        }
      }
    }

    private void classifyHubSatelliteFacts() {
      for (DvSatellite satellite : hubSatellites()) {
        if (!qualifiesAsHubSatelliteFact(satellite)) {
          continue;
        }
        satellitesPublishedAsFacts.add(satellite.getName());
        warn(
            BaseMessages.getString(
                PKG,
                "DvToDimensionalPublish.Warning.HubSatellitePublishedAsFact",
                satellite.getName(),
                satellite.getHubName()));
      }
    }

    private boolean qualifiesAsHubSatelliteFact(DvSatellite satellite) {
      if (!hasMeasures(satellite)) {
        return false;
      }
      for (SatelliteAttribute attribute : satellite.getAttributes()) {
        if (attribute == null || Utils.isEmpty(attribute.getName())) {
          continue;
        }
        if (matchesOtherHubBusinessKey(attribute.getName())) {
          return true;
        }
      }
      return false;
    }

    private List<DvHub> hubs() {
      return dataVaultModel.getTables().stream()
          .filter(DvHub.class::isInstance)
          .map(DvHub.class::cast)
          .collect(Collectors.toList());
    }

    private List<DvLink> links() {
      return dataVaultModel.getTables().stream()
          .filter(DvLink.class::isInstance)
          .map(DvLink.class::cast)
          .collect(Collectors.toList());
    }

    private List<DvSatellite> hubSatellites() {
      return dataVaultModel.getTables().stream()
          .filter(DvSatellite.class::isInstance)
          .map(DvSatellite.class::cast)
          .filter(satellite -> !Utils.isEmpty(satellite.getHubName()))
          .collect(Collectors.toList());
    }

    private List<DvSatellite> linkSatellites() {
      return dataVaultModel.getTables().stream()
          .filter(DvSatellite.class::isInstance)
          .map(DvSatellite.class::cast)
          .filter(satellite -> !Utils.isEmpty(satellite.getLinkName()))
          .collect(Collectors.toList());
    }

    private Set<String> reservedSatelliteFields(List<DvSatellite> satellites) {
      Set<String> reserved = new LinkedHashSet<>();
      for (DvSatellite satellite : satellites) {
        if (!satellitesPublishedAsFacts.contains(satellite.getName())) {
          continue;
        }
        for (SatelliteAttribute attribute : satellite.getAttributes()) {
          if (attribute == null || Utils.isEmpty(attribute.getName())) {
            continue;
          }
          String fieldName = attribute.getName();
          if (isTechnicalField(PublishContext.this, fieldName)) {
            continue;
          }
          if (matchesOtherHubBusinessKey(fieldName) || isMeasureAttribute(attribute)) {
            reserved.add(fieldName);
          }
        }
      }
      return reserved;
    }

    private boolean matchesOtherHubBusinessKeyExists(Set<String> fieldNames) {
      for (String fieldName : fieldNames) {
        if (matchesOtherHubBusinessKey(fieldName)) {
          return true;
        }
      }
      return false;
    }

    private boolean matchesOtherHubBusinessKey(String fieldName) {
      return businessKeyToHub.containsKey(fieldName);
    }

    private String hubNameForBusinessKey(String fieldName) {
      return businessKeyToHub.get(fieldName);
    }

    private boolean usesLoadEndDate() {
      DataVaultConfiguration config = dataVaultModel.getConfiguration();
      return config != null && config.isUseLoadEndDate();
    }

    private String dimensionNameForHub(String hubName) {
      return "dim_" + stripDvPrefix(hubName, "hub_");
    }

    private String dimensionTableNameForHub(DvHub hub) {
      String tableName = hub != null ? hub.getTableName() : null;
      if (!Utils.isEmpty(tableName) && tableName.startsWith("hub_")) {
        return "d_" + tableName.substring(4);
      }
      return "d_" + stripDvPrefix(hub != null ? hub.getName() : "", "hub_");
    }

    private DmFactDimensionRole dimensionRoleForHub(DvLink link, String hubName) {
      DmFactDimensionRole role = new DmFactDimensionRole();
      role.setDimensionTableName(dimensionNameForHub(hubName));
      role.setForeignKeyColumn(foreignKeyColumnForHub(hubName));
      if (link != null) {
        role.setSourceFieldName(linkSourceFieldForHub(link, hubName));
      } else {
        Set<String> businessKeys = businessKeysByHub.getOrDefault(hubName, Set.of());
        if (!businessKeys.isEmpty()) {
          role.setSourceFieldName(businessKeys.iterator().next());
        }
      }
      return role;
    }

    private String foreignKeyColumnForHub(String hubName) {
      return stripDvPrefix(hubName, "hub_") + "_key";
    }

    private String linkSourceFieldForHub(DvLink link, String hubName) {
      if (link != null && link.getLinkHubSources() != null) {
        for (DvLink.DvLinkHubSource source : link.getLinkHubSources()) {
          if (source == null || source.getHubSourceKeyFields() == null) {
            continue;
          }
          for (DvLink.HubSourceKeyField keyField : source.getHubSourceKeyFields()) {
            if (keyField == null || !hubName.equals(keyField.getHubName())) {
              continue;
            }
            if (keyField.getSourceBusinessKeyFields() != null) {
              for (BusinessKeySource businessKeySource : keyField.getSourceBusinessKeyFields()) {
                if (businessKeySource != null
                    && !Utils.isEmpty(businessKeySource.getSourceFieldName())) {
                  return businessKeySource.getSourceFieldName();
                }
              }
            }
          }
        }
      }
      Set<String> businessKeys = businessKeysByHub.getOrDefault(hubName, Set.of());
      if (!businessKeys.isEmpty()) {
        return businessKeys.iterator().next();
      }
      return foreignKeyColumnForHub(hubName);
    }

    private String factNameForSatellite(DvSatellite satellite) {
      return "fact_" + stripDvPrefix(satellite.getName(), "sat_");
    }

    private String factTableNameForSatellite(DvSatellite satellite) {
      String tableName = satellite.getTableName();
      if (!Utils.isEmpty(tableName) && tableName.startsWith("sat_")) {
        return "f_" + tableName.substring(4);
      }
      return "f_" + stripDvPrefix(satellite.getName(), "sat_");
    }

    private String factlessNameForLink(DvLink link) {
      return "factless_" + stripDvPrefix(link.getName(), "lnk_");
    }

    private String factlessTableNameForLink(DvLink link) {
      String tableName = link.getTableName();
      if (!Utils.isEmpty(tableName) && tableName.startsWith("lnk_")) {
        return "f_factless_" + tableName.substring(4);
      }
      return "f_factless_" + stripDvPrefix(link.getName(), "lnk_");
    }

    private String bridgeNameForLink(DvLink link) {
      return "bridge_" + stripDvPrefix(link.getName(), "lnk_");
    }

    private String bridgeTableNameForLink(DvLink link) {
      String tableName = link.getTableName();
      if (!Utils.isEmpty(tableName) && tableName.startsWith("lnk_")) {
        return "bridge_" + tableName.substring(4);
      }
      return "bridge_" + stripDvPrefix(link.getName(), "lnk_");
    }

    private String stripDvPrefix(String value, String prefix) {
      if (Utils.isEmpty(value)) {
        return value;
      }
      if (!Utils.isEmpty(prefix) && value.startsWith(prefix)) {
        return value.substring(prefix.length());
      }
      return value;
    }

    private void warn(String warning) {
      if (!Utils.isEmpty(warning)) {
        warnings.add(warning);
      }
    }
  }
}
