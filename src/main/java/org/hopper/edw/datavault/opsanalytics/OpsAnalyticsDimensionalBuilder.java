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
package org.hopper.edw.datavault.opsanalytics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.AggregationMethod;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.BusinessKeySource;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metadata.busmatrix.BusinessProcessCatalogMeta;
import org.hopper.edw.datavault.metadata.busmatrix.BusinessProcessTerm;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBusinessProcessRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDateDimensionTemplate;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionScdType;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceType;
import org.hopper.edw.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.hopper.edw.datavault.metadata.dimensional.DmTableBase;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.metadata.dimensional.publish.DvToDimensionalPublish;
import org.hopper.edw.semantic.bind.HdmSemanticSeed;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticSelection;
import org.hopper.edw.semantic.model.SemanticSelectionField;

/**
 * Shapes a dimensional publish of the operational analytics vault into the consumption star, the
 * semantic selections, and the bus-matrix catalog.
 */
public final class OpsAnalyticsDimensionalBuilder {

  public static final String MODEL_NAME = "ops-analytics";
  public static final String DM_FOLDER = "dm";
  public static final String DIMENSIONS_MODEL = "ops-analytics-dimensions";
  public static final String DIMENSIONS_FILE = "ops-analytics-dimensions.hdm";
  public static final String TARGET_DATABASE = "ops-analytics-dm";
  public static final String BUS_CATALOG = "ops-analytics";
  public static final String DOMAIN = "Operational analytics";

  public static final String DATE_DIMENSION = DmDateDimensionTemplate.DEFAULT_DIMENSION_NAME;
  public static final String LOAD_DATE = "dim_load_date";
  public static final String MEASURED_DATE = "dim_measured_date";
  public static final String HARVEST_DATE = "dim_harvest_date";
  public static final String ALERTED_DATE = "dim_alerted_date";

  public static final String SELECTION_PIPELINE_DURATION = "pipeline-duration-by-day";
  public static final String SELECTION_MODEL_TREND = "model-load-trend";
  public static final String SELECTION_QUALITY = "quality-findings-by-severity";

  private static final Set<String> KEPT_DIMENSIONS =
      Set.of("dim_pipeline", "dim_transform", "dim_model", "dim_subject", "dim_rule");

  private static final Set<String> DEGENERATES =
      Set.of(
          "pipeline_name",
          "transform_name",
          "model_type",
          "model_name",
          "workflow_name",
          "severity",
          "subject_key",
          "rule_id",
          "rule_name",
          "element_name",
          "element_type",
          "source_name",
          "status",
          "success",
          "code",
          "rule_type",
          "field_name",
          "lifecycle",
          "run_id",
          "quality_run_id",
          "harvest_run_id",
          "overview_id",
          "workflow_execution_id",
          "load_run_id",
          "insight_seq",
          "finding_seq",
          "change_seq",
          "sequence_no");

  private static final List<FactShape> FACTS =
      List.of(
          new FactShape(
              "f_lnk_load_pipeline_metric",
              "sat_lnk_load_pipeline_metric",
              "fact_pipeline_load",
              "f_pipeline_load",
              "Load",
              DateKind.LOAD),
          new FactShape(
              "f_lnk_load_transform_metric",
              "sat_lnk_load_transform_metric",
              "fact_transform_load",
              "f_transform_load",
              "Load",
              DateKind.NONE),
          new FactShape(
              "f_lnk_load_insight",
              "sat_lnk_load_insight",
              "fact_load_insight",
              "f_load_insight",
              "Load",
              DateKind.NONE),
          new FactShape(
              "f_workflow_load_overview",
              "sat_workflow_load_overview",
              "fact_workflow_overview",
              "f_workflow_overview",
              "Load",
              DateKind.LOAD),
          new FactShape(
              "f_lnk_workflow_load_overview_model",
              "sat_lnk_workflow_load_overview_model",
              "fact_workflow_model",
              "f_workflow_model",
              "Load",
              DateKind.NONE),
          new FactShape(
              "f_quality_run",
              "sat_quality_run",
              "fact_quality_run",
              "f_quality_run",
              "Quality",
              DateKind.MEASURED),
          new FactShape(
              "f_lnk_quality_finding",
              "sat_lnk_quality_finding",
              "fact_quality_finding",
              "f_quality_finding",
              "Quality",
              DateKind.NONE),
          new FactShape(
              "f_lnk_quality_profile_subject",
              "sat_lnk_quality_profile_subject",
              "fact_quality_subject",
              "f_quality_subject",
              "Quality",
              DateKind.MEASURED),
          new FactShape(
              "f_lnk_quality_profile_field",
              "sat_lnk_quality_profile_field",
              "fact_quality_field",
              "f_quality_field",
              "Quality",
              DateKind.NONE),
          new FactShape(
              "f_schema_harvest_run",
              "sat_schema_harvest_run",
              "fact_harvest_run",
              "f_harvest_run",
              "Harvest",
              DateKind.HARVEST),
          new FactShape(
              "f_lnk_schema_harvest_subject",
              "sat_lnk_schema_harvest_subject",
              "fact_harvest_subject",
              "f_harvest_subject",
              "Harvest",
              DateKind.NONE),
          new FactShape(
              "f_lnk_schema_harvest_change",
              "sat_lnk_schema_harvest_change",
              "fact_harvest_change",
              "f_harvest_change",
              "Harvest",
              DateKind.NONE));

  private OpsAnalyticsDimensionalBuilder() {}

  public static DimensionalModel shape(DataVaultModel vault) throws HopException {
    return shape(vault, null);
  }

  /**
   * @param targetDatabase vault connection used to spell the hub load-date column. Null keeps the
   *     configured name in lower case.
   */
  public static DimensionalModel shape(DataVaultModel vault, DatabaseMeta targetDatabase)
      throws HopException {
    if (vault == null) {
      throw new HopException("Data Vault model is required");
    }
    DimensionalModel model = DvToDimensionalPublish.publish(vault).getDimensionalModel();
    model.setName(MODEL_NAME);
    model.setNameSynchronizedWithFilename(false);
    model.setDescription(
        "Operational analytics star. Facts are insert-only. Date roles share dim_date.");
    model.getConfigurationOrDefault().setTargetDatabase(TARGET_DATABASE);
    model.getNotes().clear();

    List<IDmTable> kept = new ArrayList<>();
    for (FactShape shape : FACTS) {
      DmFact fact = factFor(model, vault, shape);
      if (fact == null) {
        continue;
      }
      shapeFact(fact, vault, shape);
      kept.add(fact);
    }
    addDateDimension(model, kept);
    for (IDmTable table : model.getTables()) {
      if (table instanceof DmDimension dimension && KEPT_DIMENSIONS.contains(dimension.getName())) {
        dimension.setScdType(DmDimensionScdType.TYPE1);
        kept.add(dimension);
      }
    }
    model.setTables(kept);
    List<CollapsedKey> collapsed = collapseCompositeDimensions(model, vault);
    for (IDmTable table : model.getTables()) {
      if (table instanceof DmFact fact) {
        finishFact(fact, vault, collapsed);
      }
    }
    ensureType1LoadTimestamp(model, vault, targetDatabase);
    return model;
  }

  /**
   * Splits a shaped star into one dimensions model and one model per fact. Fact files keep
   * dimension roles as aliases of {@code sharedModelFilename}.
   */
  public static OpsAnalyticsDimensionalModels partition(
      DimensionalModel shaped, String sharedModelFilename) throws HopException {
    if (shaped == null) {
      throw new HopException("Dimensional model is required");
    }
    if (Utils.isEmpty(sharedModelFilename)) {
      throw new HopException("The shared dimensions file is required");
    }
    DimensionalModel dimensions =
        newModel(
            shaped,
            DIMENSIONS_MODEL,
            "Conformed dimensions for operational analytics, including dim_date.");
    List<IDmTable> dimensionTables = new ArrayList<>();
    for (IDmTable table : shaped.getTables()) {
      if (table instanceof DmDimension) {
        dimensionTables.add(table);
      }
    }
    layoutGrid(dimensionTables);
    dimensions.setTables(dimensionTables);

    List<DimensionalModel> facts = new ArrayList<>();
    for (FactShape shape : FACTS) {
      if (!(shaped.findTable(shape.name()) instanceof DmFact fact)) {
        continue;
      }
      DimensionalModel factModel =
          newModel(
              shaped,
              factModelName(fact.getTableName()),
              "Insert-only fact "
                  + fact.getTableName()
                  + ". Dimension roles alias "
                  + DIMENSIONS_MODEL
                  + ".");
      List<IDmTable> tables = new ArrayList<>();
      fact.setLocation(320, 160);
      tables.add(fact);
      int y = 64;
      Set<String> added = new LinkedHashSet<>();
      for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
        if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
          continue;
        }
        String name = role.getDimensionTableName();
        if (!added.add(name)) {
          continue;
        }
        tables.add(aliasOf(shaped.findTable(name), name, sharedModelFilename, new Point(64, y)));
        y += 96;
      }
      factModel.setTables(tables);
      facts.add(factModel);
    }
    return new OpsAnalyticsDimensionalModels(dimensions, facts);
  }

  /** File name for a fact model, for example {@code ops-f-pipeline-load.hdm}. */
  public static String factFileName(String physicalTable) {
    String table = physicalTable == null ? "fact" : physicalTable.trim();
    return "ops-" + table.replace('_', '-') + ".hdm";
  }

  public static SemanticModel semanticLayer(
      DimensionalModel dimensionalModel, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    SemanticModel semantic = HdmSemanticSeed.seed(dimensionalModel, variables, metadataProvider);
    semantic.setName(
        dimensionalModel != null && !Utils.isEmpty(dimensionalModel.getName())
            ? dimensionalModel.getName()
            : MODEL_NAME);
    semantic.setDescription("Consumption layer for operational analytics.");
    ensureCountMeasure(semantic, "fact_quality_finding", "severity", "finding_count");
    if (semantic.findEntity("fact_pipeline_load") != null) {
      semantic.putSelection(
          selection(
              SELECTION_PIPELINE_DURATION,
              "fact_pipeline_load",
              List.of(new SemanticSelectionField("dim_pipeline", "pipeline_name")),
              List.of(new SemanticSelectionField(LOAD_DATE, "full_date")),
              List.of(new SemanticSelectionField("fact_pipeline_load", "duration_ms"))));
    }
    if (semantic.findEntity("fact_workflow_model") != null) {
      semantic.putSelection(
          selection(
              SELECTION_MODEL_TREND,
              "fact_workflow_model",
              List.of(
                  new SemanticSelectionField("dim_model", "model_name"),
                  new SemanticSelectionField("fact_workflow_model", "success")),
              List.of(new SemanticSelectionField("dim_model", "model_type")),
              List.of(new SemanticSelectionField("fact_workflow_model", "duration_ms"))));
    }
    if (semantic.findEntity("fact_quality_finding") != null) {
      semantic.putSelection(
          selection(
              SELECTION_QUALITY,
              "fact_quality_finding",
              List.of(new SemanticSelectionField("dim_subject", "subject_key")),
              List.of(new SemanticSelectionField("fact_quality_finding", "severity")),
              List.of(new SemanticSelectionField("fact_quality_finding", "finding_count"))));
    }
    return semantic;
  }

  public static BusinessProcessCatalogMeta busCatalog() {
    BusinessProcessCatalogMeta catalog = new BusinessProcessCatalogMeta(BUS_CATALOG);
    catalog.setDescription("Bus matrix for operational analytics facts.");
    catalog.getDomains().add(new BusinessProcessTerm(DOMAIN, "Warehouse operations", null));
    catalog
        .getLevel1()
        .add(new BusinessProcessTerm("Load", "Load timing and workflow overview", DOMAIN));
    catalog
        .getLevel1()
        .add(new BusinessProcessTerm("Quality", "Data quality history", DOMAIN));
    catalog
        .getLevel1()
        .add(new BusinessProcessTerm("Harvest", "Schema harvest history", DOMAIN));
    return catalog;
  }

  private static DmFact factFor(DimensionalModel model, DataVaultModel vault, FactShape shape) {
    for (IDmTable table : model.getTables()) {
      if (table instanceof DmFact fact && shape.publishedTable().equals(fact.getTableName())) {
        fact.setName(shape.name());
        fact.setTableName(shape.table());
        return fact;
      }
    }
    DvSatellite satellite = satellite(vault, shape.satellite());
    if (satellite == null) {
      return null;
    }
    DmFact fact = new DmFact();
    fact.setName(shape.name());
    fact.setTableName(shape.table());
    fact.setDescription("Insert-only fact from " + satellite.getName());
    DvLink link = vault.findLink(satellite.getLinkName());
    if (link != null && link.getHubNames() != null) {
      for (String hubName : link.getHubNames()) {
        String dimension = "dim_" + stripPrefix(hubName, "hub_");
        if (!KEPT_DIMENSIONS.contains(dimension)) {
          continue;
        }
        DmFactDimensionRole role =
            new DmFactDimensionRole(dimension, stripPrefix(hubName, "hub_") + "_key");
        role.setSourceFieldName(firstBusinessKey(vault, hubName));
        fact.getDimensionRoles().add(role);
      }
    }
    return fact;
  }

  private static void shapeFact(DmFact fact, DataVaultModel vault, FactShape shape) {
    fact.setGrain("One row per " + shape.table().substring(2).replace('_', ' '));
    DmBusinessProcessRef process = new DmBusinessProcessRef();
    process.setBusiness(DOMAIN);
    process.setLevel1(shape.domain());
    fact.setBusinessProcess(process);

    DvSatellite satellite = satellite(vault, shape.satellite());
    Set<String> attributeNames = attributeNames(satellite);
    List<DmFactDimensionRole> roles = new ArrayList<>();
    Set<String> roleSources = new LinkedHashSet<>();
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null || !KEPT_DIMENSIONS.contains(role.getDimensionTableName())) {
        if (role != null && DEGENERATES.contains(role.getSourceFieldName())) {
          addDegenerate(fact, role.getSourceFieldName());
        }
        continue;
      }
      roles.add(role);
      if (!Utils.isEmpty(role.getSourceFieldName())) {
        roleSources.add(role.getSourceFieldName());
      }
    }
    String dateColumn = dateColumn(shape.dateKind(), attributeNames);
    if (dateColumn != null) {
      roles.add(dateRole(shape.dateKind(), dateColumn));
    }
    fact.setDimensionRoles(roles);

    for (String name : attributeNames) {
      if (DEGENERATES.contains(name) && !roleSources.contains(name) && !name.equals(dateColumn)) {
        addDegenerate(fact, name);
      }
    }
  }

  /**
   * Fact joins accept one natural key. A hub with several business keys is stored as one
   * concatenated key so the fact can look it up.
   */
  private static List<CollapsedKey> collapseCompositeDimensions(
      DimensionalModel model, DataVaultModel vault) {
    List<CollapsedKey> collapsed = new ArrayList<>();
    for (IDmTable table : model.getTables()) {
      if (!(table instanceof DmDimension dimension) || dimension.getNaturalKeysOrEmpty().size() < 2) {
        continue;
      }
      List<String> parts = new ArrayList<>();
      for (DmNaturalKeyField key : dimension.getNaturalKeysOrEmpty()) {
        if (key != null && !Utils.isEmpty(key.getFieldName())) {
          parts.add(key.getFieldName());
        }
      }
      if (parts.size() < 2) {
        continue;
      }
      String keyName = stripPrefix(dimension.getName(), "dim_") + "_key";
      dimension.getNaturalKeysOrEmpty().clear();
      dimension.getNaturalKeysOrEmpty().add(new DmNaturalKeyField(keyName));
      for (String part : parts) {
        boolean present = false;
        for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
          if (attribute != null && part.equals(attribute.getFieldName())) {
            present = true;
            break;
          }
        }
        if (!present) {
          dimension.getAttributesOrEmpty().add(new DmDimensionAttribute(part, DmScdUpdatePolicy.TYPE1));
        }
      }
      DvHub hub = hubForDimension(vault, dimension.getName());
      String hubTable = hub != null ? hub.getTableName() : "hub_" + stripPrefix(dimension.getName(), "dim_");
      dimension
          .getSourceOrDefault()
          .setSourceSql(
              "SELECT "
                  + concatExpression("", parts)
                  + " AS "
                  + keyName
                  + ", "
                  + String.join(", ", parts)
                  + "\nFROM "
                  + hubTable);
      collapsed.add(
          new CollapsedKey(
              dimension.getName(),
              keyName,
              parts,
              hub != null ? hub.getName() : "hub_" + stripPrefix(dimension.getName(), "dim_")));
    }
    return collapsed;
  }

  private static void finishFact(DmFact fact, DataVaultModel vault, List<CollapsedKey> collapsed) {
    FactShape shape = shapeFor(fact);
    DvSatellite satellite = shape == null ? null : satellite(vault, shape.satellite());
    Set<String> satFields = attributeNames(satellite);
    if (fact.getMeasuresOrEmpty().isEmpty() && satellite != null) {
      for (SatelliteAttribute attribute : satellite.getAttributes()) {
        if (attribute != null && isNumeric(attribute.getDataType())) {
          fact.getMeasures().add(new DmFactMeasure(attribute.getName(), true));
        }
      }
    }
    boolean rowCount = false;
    if (fact.getMeasuresOrEmpty().isEmpty()) {
      fact.getMeasures().add(new DmFactMeasure("row_count", true));
      rowCount = true;
    }
    List<CollapsedKey> used = new ArrayList<>();
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      for (CollapsedKey key : collapsed) {
        if (key.dimensionName().equals(role.getDimensionTableName())) {
          role.setSourceFieldName(key.keyName());
          role.setForeignKeyColumn(key.keyName());
          role.setTruncateToDateKey(false);
          used.add(key);
        }
      }
    }
    boolean startedAtFromLoadRun = false;
    if (fact.getDimensionRolesOrEmpty().isEmpty()
        && satellite != null
        && !Utils.isEmpty(satellite.getLinkName())) {
      DvLink link = vault.findLink(satellite.getLinkName());
      if (link != null && link.getHubNames() != null && link.getHubNames().contains("hub_load_run")) {
        fact.getDimensionRoles().add(dateRole(DateKind.LOAD, "started_at"));
        startedAtFromLoadRun = true;
      }
    } else if (roleSource(fact, "started_at") && !satFields.contains("started_at")) {
      startedAtFromLoadRun = true;
    }
    List<HubField> hubFields = missingHubFields(fact, satellite, vault, satFields, used);
    boolean needsProjection =
        rowCount || startedAtFromLoadRun || !used.isEmpty() || !hubFields.isEmpty();
    if (!needsProjection && !Utils.isEmpty(fact.getSourceOrDefault().getSourceSql())) {
      return;
    }
    if (satellite == null) {
      return;
    }
    fact.getSourceOrDefault()
        .setSourceSql(factSql(satellite, vault, used, hubFields, rowCount, startedAtFromLoadRun));
  }

  private static List<HubField> missingHubFields(
      DmFact fact,
      DvSatellite satellite,
      DataVaultModel vault,
      Set<String> satFields,
      List<CollapsedKey> used) {
    Set<String> collapsedNames = new LinkedHashSet<>();
    for (CollapsedKey key : used) {
      collapsedNames.add(key.keyName());
    }
    List<HubField> fields = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role != null) {
        addHubField(fields, seen, satellite, vault, satFields, collapsedNames, role.getSourceFieldName());
      }
    }
    for (DmFactDegenerateDimension degenerate : fact.getDegenerateDimensionsOrEmpty()) {
      if (degenerate != null) {
        addHubField(
            fields, seen, satellite, vault, satFields, collapsedNames, degenerate.getFieldName());
      }
    }
    return fields;
  }

  private static void addHubField(
      List<HubField> fields,
      Set<String> seen,
      DvSatellite satellite,
      DataVaultModel vault,
      Set<String> satFields,
      Set<String> collapsedNames,
      String field) {
    if (Utils.isEmpty(field) || satFields.contains(field) || collapsedNames.contains(field)) {
      return;
    }
    HubField hubField = hubFieldFor(vault, satellite, field);
    if (hubField != null && seen.add(hubField.hubName() + "." + hubField.streamName())) {
      fields.add(hubField);
    }
  }

  /**
   * Resolves a fact stream field to a hub column. The stream name can differ from the hub business
   * key, for example source {@code load_run_id} stored as {@code run_id}.
   */
  private static HubField hubFieldFor(
      DataVaultModel vault, DvSatellite satellite, String streamField) {
    DvHub hub = reachableHubWithBusinessKey(vault, satellite, streamField);
    if (hub != null) {
      return new HubField(hub.getName(), streamField, streamField);
    }
    if (satellite == null || Utils.isEmpty(satellite.getLinkName()) || vault == null) {
      return null;
    }
    DvLink link = vault.findLink(satellite.getLinkName());
    if (link == null || link.getLinkHubSources() == null || link.getHubNames() == null) {
      return null;
    }
    for (DvLink.DvLinkHubSource source : link.getLinkHubSources()) {
      if (source == null || source.getHubSourceKeyFields() == null) {
        continue;
      }
      for (DvLink.HubSourceKeyField keyField : source.getHubSourceKeyFields()) {
        if (keyField == null
            || Utils.isEmpty(keyField.getHubName())
            || !link.getHubNames().contains(keyField.getHubName())
            || keyField.getSourceBusinessKeyFields() == null) {
          continue;
        }
        for (BusinessKeySource businessKey : keyField.getSourceBusinessKeyFields()) {
          if (businessKey == null
              || !streamField.equals(businessKey.getSourceFieldName())
              || Utils.isEmpty(businessKey.getBusinessKeyField())) {
            continue;
          }
          return new HubField(
              keyField.getHubName(), businessKey.getBusinessKeyField(), streamField);
        }
      }
    }
    return null;
  }

  /** Hub that owns {@code fieldName} and is the satellite parent or a hub on its link. */
  private static DvHub reachableHubWithBusinessKey(
      DataVaultModel vault, DvSatellite satellite, String fieldName) {
    DvHub match = null;
    for (IDvTable table : vault.getTables()) {
      if (!(table instanceof DvHub hub) || !hubHasBusinessKey(hub, fieldName)) {
        continue;
      }
      if (satellite != null && hub.getName().equals(satellite.getHubName())) {
        return hub;
      }
      DvLink link =
          satellite != null && !Utils.isEmpty(satellite.getLinkName())
              ? vault.findLink(satellite.getLinkName())
              : null;
      if (link != null && link.getHubNames() != null && link.getHubNames().contains(hub.getName())) {
        match = hub;
      }
    }
    return match;
  }

  private static boolean hubHasBusinessKey(DvHub hub, String fieldName) {
    if (hub.getBusinessKeys() == null) {
      return false;
    }
    for (BusinessKey businessKey : hub.getBusinessKeys()) {
      if (businessKey != null && fieldName.equals(businessKey.getName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Type 1 insert/update reads the dimensional load timestamp from the source stream. Hub tables
   * store that value under the Data Vault load-date column, so the dimension query aliases it.
   */
  private static void ensureType1LoadTimestamp(
      DimensionalModel model, DataVaultModel vault, DatabaseMeta targetDatabase) {
    String alias = model.getConfigurationOrDefault().resolveLoadDateField(null);
    if (Utils.isEmpty(alias)) {
      return;
    }
    String hubColumn = physicalColumn(targetDatabase, vaultLoadDateField(vault));
    String projection = hubColumn + " AS " + alias;
    String marker = " as " + alias.toLowerCase(Locale.ROOT);
    for (IDmTable table : model.getTables()) {
      if (!(table instanceof DmDimension dimension)) {
        continue;
      }
      if (dimension.getSourceOrDefault().resolveSourceType() == DmSourceType.DATE_GENERATOR) {
        continue;
      }
      String sql = dimension.getSourceOrDefault().getSourceSql();
      if (Utils.isEmpty(sql) || sql.toLowerCase(Locale.ROOT).contains(marker)) {
        continue;
      }
      dimension.getSourceOrDefault().setSourceSql(appendSelectItem(sql, projection));
    }
  }

  private static String vaultLoadDateField(DataVaultModel vault) {
    if (vault != null
        && vault.getConfigurationOrDefault() != null
        && !Utils.isEmpty(vault.getConfigurationOrDefault().getLoadDateField())) {
      return vault.getConfigurationOrDefault().getLoadDateField();
    }
    return "LOAD_DATE";
  }

  private static String physicalColumn(DatabaseMeta databaseMeta, String field) {
    if (databaseMeta != null) {
      String quoted = databaseMeta.quoteField(field);
      if (!Utils.isEmpty(quoted)) {
        return quoted;
      }
    }
    return field.toLowerCase(Locale.ROOT);
  }

  private static String appendSelectItem(String sql, String projection) {
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("(?i)\\sfrom\\s").matcher(sql);
    int from = -1;
    while (matcher.find()) {
      from = matcher.start();
    }
    if (from < 0) {
      return sql + ", " + projection;
    }
    return sql.substring(0, from).stripTrailing() + ", " + projection + sql.substring(from);
  }

  private static String factSql(
      DvSatellite satellite,
      DataVaultModel vault,
      List<CollapsedKey> keys,
      List<HubField> hubFields,
      boolean rowCount,
      boolean startedAtFromLoadRun) {
    String satTable = satellite.getTableName();
    Map<String, String> aliases = new LinkedHashMap<>();
    for (CollapsedKey key : keys) {
      aliases.putIfAbsent(key.hubName(), "h" + aliases.size());
    }
    for (HubField field : hubFields) {
      aliases.putIfAbsent(field.hubName(), "h" + aliases.size());
    }
    if (aliases.isEmpty() && !startedAtFromLoadRun) {
      return "SELECT s.*"
          + (rowCount ? ", 1 AS row_count" : "")
          + "\nFROM "
          + satTable
          + " s";
    }
    StringBuilder select = new StringBuilder("SELECT s.*");
    if (rowCount) {
      select.append(", 1 AS row_count");
    }
    for (CollapsedKey key : keys) {
      select.append(", ").append(concatExpression(aliases.get(key.hubName()), key.parts()));
      select.append(" AS ").append(key.keyName());
    }
    for (HubField field : hubFields) {
      select
          .append(", ")
          .append(aliases.get(field.hubName()))
          .append(".")
          .append(field.hubColumn());
      if (!field.hubColumn().equals(field.streamName())) {
        select.append(" AS ").append(field.streamName());
      }
    }
    if (startedAtFromLoadRun) {
      select.append(", r.started_at");
    }
    StringBuilder sql = new StringBuilder(select);
    sql.append("\nFROM ").append(satTable).append(" s");
    DvLink link =
        !Utils.isEmpty(satellite.getLinkName()) ? vault.findLink(satellite.getLinkName()) : null;
    if (link == null) {
      return "SELECT s.*"
          + (rowCount ? ", 1 AS row_count" : "")
          + "\nFROM "
          + satTable
          + " s";
    }
    String linkHash = link.getLinkHashKeyFieldName();
    sql.append("\nJOIN ")
        .append(link.getTableName())
        .append(" l ON s.")
        .append(linkHash)
        .append(" = l.")
        .append(linkHash);
    for (Map.Entry<String, String> alias : aliases.entrySet()) {
      DvHub hub = vault.findHub(alias.getKey());
      if (hub == null) {
        continue;
      }
      String hubHash = hub.getHashKeyFieldName();
      sql.append("\nJOIN ")
          .append(hub.getTableName())
          .append(" ")
          .append(alias.getValue())
          .append(" ON l.")
          .append(hubHash)
          .append(" = ")
          .append(alias.getValue())
          .append(".")
          .append(hubHash);
    }
    if (startedAtFromLoadRun) {
      DvHub loadRun = vault.findHub("hub_load_run");
      String loadRunHash = loadRun != null ? loadRun.getHashKeyFieldName() : "load_run_hk";
      sql.append("\nJOIN ")
          .append(loadRunSatelliteTable(vault))
          .append(" r ON l.")
          .append(loadRunHash)
          .append(" = r.")
          .append(loadRunHash);
    }
    return sql.toString();
  }

  private static String concatExpression(String alias, List<String> parts) {
    StringBuilder expression = new StringBuilder("CONCAT(");
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        expression.append(", '|', ");
      }
      if (!Utils.isEmpty(alias)) {
        expression.append(alias).append(".");
      }
      expression.append(parts.get(i));
    }
    expression.append(")");
    return expression.toString();
  }

  private static String loadRunSatelliteTable(DataVaultModel vault) {
    for (IDvTable table : vault.getTables()) {
      if (table instanceof DvSatellite satellite && "hub_load_run".equals(satellite.getHubName())) {
        return satellite.getTableName();
      }
    }
    return "sat_load_run";
  }

  private static DvHub hubForDimension(DataVaultModel vault, String dimensionName) {
    return vault.findHub("hub_" + stripPrefix(dimensionName, "dim_"));
  }

  private static FactShape shapeFor(DmFact fact) {
    for (FactShape shape : FACTS) {
      if (fact != null && shape.name().equals(fact.getName())) {
        return shape;
      }
    }
    return null;
  }

  private static boolean roleSource(DmFact fact, String fieldName) {
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role != null && fieldName.equals(role.getSourceFieldName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNumeric(String dataType) {
    if (Utils.isEmpty(dataType)) {
      return false;
    }
    return Set.of("NUMBER", "INTEGER", "BIGNUMBER", "DECIMAL", "DOUBLE", "FLOAT")
        .contains(dataType.trim().toUpperCase(Locale.ROOT));
  }

  private record CollapsedKey(
      String dimensionName, String keyName, List<String> parts, String hubName) {}

  private record HubField(String hubName, String hubColumn, String streamName) {}

  private static DimensionalModel newModel(
      DimensionalModel shaped, String name, String description) {
    DimensionalModel model = new DimensionalModel();
    model.setName(name);
    model.setNameSynchronizedWithFilename(true);
    model.setDescription(description);
    model.setConfigurationName(shaped.getConfigurationName());
    if (shaped.getConfiguration() != null) {
      model.setConfiguration(shaped.getConfiguration());
    } else {
      model
          .getConfigurationOrDefault()
          .setTargetDatabase(shaped.getConfigurationOrDefault().getTargetDatabase());
    }
    return model;
  }

  private static String factModelName(String physicalTable) {
    String filename = factFileName(physicalTable);
    return filename.substring(0, filename.length() - 4);
  }

  private static void layoutGrid(List<IDmTable> tables) {
    int columns = 3;
    for (int i = 0; i < tables.size(); i++) {
      if (tables.get(i) instanceof DmTableBase base) {
        base.setLocation(64 + (i % columns) * 240, 64 + (i / columns) * 140);
      }
    }
  }

  private static DmDimensionAlias aliasOf(
      IDmTable source, String name, String sharedModelFilename, Point location) {
    DmDimensionAlias alias;
    if (source instanceof DmDimensionAlias existing) {
      alias =
          DmDateDimensionTemplate.createDateAlias(
              existing.getName(), existing.getReferencedDimensionName(), location);
      alias.setTableName(existing.getTableName());
      alias.setDescription(existing.getDescription());
    } else {
      alias = new DmDimensionAlias();
      alias.setName(name);
      alias.setReferencedDimensionName(
          source instanceof DmDimension dimension && !Utils.isEmpty(dimension.getName())
              ? dimension.getName()
              : name);
      alias.setTableName(source != null ? source.getTableName() : null);
      alias.setDescription("Conformed dimension " + name);
      alias.setLocation(location != null ? location.x : 0, location != null ? location.y : 0);
    }
    alias.setReferencedModelFilename(sharedModelFilename);
    return alias;
  }

  private static void addDateDimension(DimensionalModel model, List<IDmTable> kept) {
    kept.add(DmDateDimensionTemplate.createDateDimension(new Point(40, 40)));
    int y = 160;
    for (String alias : List.of(LOAD_DATE, MEASURED_DATE, HARVEST_DATE, ALERTED_DATE)) {
      kept.add(
          DmDateDimensionTemplate.createDateAlias(alias, DATE_DIMENSION, new Point(40, y)));
      y += 80;
    }
  }

  private static DmFactDimensionRole dateRole(DateKind kind, String sourceField) {
    String alias =
        switch (kind) {
          case MEASURED -> MEASURED_DATE;
          case HARVEST -> HARVEST_DATE;
          case ALERTED -> ALERTED_DATE;
          default -> LOAD_DATE;
        };
    DmFactDimensionRole role = new DmFactDimensionRole(alias, sourceField + "_key");
    role.setSourceFieldName(sourceField);
    role.setTruncateToDateKey(true);
    return role;
  }

  private static String dateColumn(DateKind kind, Set<String> attributes) {
    List<String> candidates =
        switch (kind) {
          case LOAD -> List.of("started_at", "execution_start_date");
          case MEASURED -> List.of("measured_at", "captured_at");
          case HARVEST -> List.of("started_at");
          case ALERTED -> List.of("alerted_at");
          case NONE -> List.of();
        };
    for (String candidate : candidates) {
      if (attributes.contains(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static void addDegenerate(DmFact fact, String fieldName) {
    if (Utils.isEmpty(fieldName)) {
      return;
    }
    for (DmFactDegenerateDimension existing : fact.getDegenerateDimensionsOrEmpty()) {
      if (existing != null && fieldName.equals(existing.getFieldName())) {
        return;
      }
    }
    for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
      if (measure != null && fieldName.equals(measure.getFieldName())) {
        return;
      }
    }
    fact.getDegenerateDimensions().add(new DmFactDegenerateDimension(fieldName));
  }

  private static void ensureCountMeasure(
      SemanticModel semantic, String entityName, String fieldName, String measureName) {
    SemanticEntity entity = semantic.findEntity(entityName);
    if (entity == null) {
      return;
    }
    for (SemanticMeasure measure : entity.getMeasures()) {
      if (measure != null && measureName.equals(measure.getName())) {
        return;
      }
    }
    SemanticMeasure measure = new SemanticMeasure(measureName, fieldName);
    measure.setHeader("Finding count");
    measure.setAdditive(false);
    measure.setAggregationMethod(AggregationMethod.COUNT);
    entity.getMeasures().add(measure);
  }

  private static SemanticSelection selection(
      String name,
      String entity,
      List<SemanticSelectionField> rows,
      List<SemanticSelectionField> columns,
      List<SemanticSelectionField> measures) {
    SemanticSelection selection = new SemanticSelection();
    selection.setName(name);
    selection.setEntityName(entity);
    selection.setRows(rows);
    selection.setColumns(columns);
    selection.setMeasures(measures);
    return selection;
  }

  private static DvSatellite satellite(DataVaultModel vault, String name) {
    if (vault.findTable(name) instanceof DvSatellite satellite) {
      return satellite;
    }
    return null;
  }

  private static Set<String> attributeNames(DvSatellite satellite) {
    Set<String> names = new LinkedHashSet<>();
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

  private static String firstBusinessKey(DataVaultModel vault, String hubName) {
    if (vault.findHub(hubName) == null
        || vault.findHub(hubName).getBusinessKeys() == null
        || vault.findHub(hubName).getBusinessKeys().isEmpty()) {
      return stripPrefix(hubName, "hub_") + "_key";
    }
    return vault.findHub(hubName).getBusinessKeys().get(0).getName();
  }

  private static String stripPrefix(String value, String prefix) {
    if (value != null && value.startsWith(prefix)) {
      return value.substring(prefix.length());
    }
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private record FactShape(
      String publishedTable,
      String satellite,
      String name,
      String table,
      String domain,
      DateKind dateKind) {}

  private enum DateKind {
    LOAD,
    MEASURED,
    HARVEST,
    ALERTED,
    NONE
  }

  /** Dimensions file plus one model for each fact. */
  public record OpsAnalyticsDimensionalModels(
      DimensionalModel dimensions, List<DimensionalModel> facts) {}
}
