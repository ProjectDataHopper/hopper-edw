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
package org.hopper.edw.datavault.resourcedefinition;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogRefreshSupport;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.versioning.CatalogVersionService;
import org.hopper.edw.datavault.ai.DvAiProposal;
import org.hopper.edw.datavault.ai.DvAiProposalApplier;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.hopgui.resourcedefinition.ResourceDefinitionModelNavigationSupport;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvDataTypeSupport;
import org.hopper.edw.datavault.metadata.DvDdlSupport;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvReadOnlyExistingVaultSupport;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvSpecialRecordSupport;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingSupport;
import org.hopper.edw.datavault.resourcedefinition.RemediationDdlWorkflowSupport.GeneratedArtifacts;
import org.hopper.edw.datavault.resourcedefinition.RemediationDdlWorkflowSupport.TableDdl;
import org.hopper.edw.datavault.resourcedefinition.SchemaRemediationArtifactsSupport.RemediationPackage;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ValidationIssue;

/** Applies remediation proposals produced by source validation. */
public final class RemediationProposalApplySupport {

  private static final Class<?> PKG = RemediationProposalApplySupport.class;

  public enum ApplyStatus {
    APPLIED,
    NEEDS_MANUAL,
    NOT_APPLICABLE
  }

  public record ApplyResult(ApplyStatus status, String message) {}

  public record ProposalContext(
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      RecordDefinition definition,
      RecordDefinitionValidation validation,
      ValidationIssue issue,
      RemediationProposal proposal,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      /** Admin-chosen name for a schema-remediation package folder (accept path). */
      String remediationName,
      /**
       * Catalog version tag used as the explicit baseline for IGNORE_SOURCE_DRIFT / align paths.
       * Never invent values from models or target databases.
       */
      String baselineVersionTag) {

    /** Compatibility constructor without remediation name / baseline tag. */
    public ProposalContext(
        HopGui hopGui,
        ResourceDefinitionGroupMeta group,
        RecordDefinition definition,
        RecordDefinitionValidation validation,
        ValidationIssue issue,
        RemediationProposal proposal,
        IVariables variables,
        IHopMetadataProvider metadataProvider) {
      this(
          hopGui,
          group,
          definition,
          validation,
          issue,
          proposal,
          variables,
          metadataProvider,
          null,
          null);
    }

    public ProposalContext(
        HopGui hopGui,
        ResourceDefinitionGroupMeta group,
        RecordDefinition definition,
        RecordDefinitionValidation validation,
        ValidationIssue issue,
        RemediationProposal proposal,
        IVariables variables,
        IHopMetadataProvider metadataProvider,
        String remediationName) {
      this(
          hopGui,
          group,
          definition,
          validation,
          issue,
          proposal,
          variables,
          metadataProvider,
          remediationName,
          null);
    }
  }

  /**
   * Parses expected/actual lengths from diff details. Supports both legacy {@code length 50 -> 75}
   * and labeled {@code expected length 50 → actual length 75} forms. Group 1 = expected, group 2 =
   * actual.
   */
  private static final Pattern LENGTH_CHANGE =
      Pattern.compile(
          "(?:expected\\s+)?length\\s+(\\d+|\\?)\\s*(?:→|->)\\s*(?:actual\\s+length\\s+)?(\\d+|\\?)",
          Pattern.CASE_INSENSITIVE);

  private RemediationProposalApplySupport() {}

  public static ApplyResult apply(ProposalContext context) {
    if (context == null || context.proposal() == null || context.proposal().type() == null) {
      return new ApplyResult(
          ApplyStatus.NOT_APPLICABLE,
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.MissingProposal"));
    }
    try {
      return switch (context.proposal().type()) {
        case REFRESH_CATALOG_CONTRACT -> applyRefreshCatalogContract(context);
          // Legacy type: same as expand models/DDL from catalog — never rewrites the catalog.
        case UPDATE_TARGET_COLUMN_LENGTH -> applyExpandModelsFromCatalog(context);
        case ALIGN_MODELS_TO_BASELINE -> applyExpandModelsFromCatalog(context);
        case IGNORE_SOURCE_DRIFT -> applyIgnoreSourceDrift(context);
        case ADD_NEW_SATELLITE -> applyAddNewSatellite(context);
        case EXTEND_EXISTING_SATELLITE -> applyExtendExistingSatellite(context);
        case REVIEW_MAPPINGS -> applyReviewMappings(context);
        case GENERATE_TARGET_DDL_PACKAGE -> applyGenerateTargetDdlPackage(context);
        case BLOCK_UPDATE_UNTIL_RESOLVED ->
            new ApplyResult(
                ApplyStatus.NEEDS_MANUAL,
                BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.BlockManual"));
      };
    } catch (HopException e) {
      return new ApplyResult(ApplyStatus.NEEDS_MANUAL, e.getMessage());
    } catch (Exception e) {
      return new ApplyResult(
          ApplyStatus.NEEDS_MANUAL,
          e.getMessage() != null
              ? e.getMessage()
              : BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.Failed"));
    }
  }

  private static ApplyResult applyRefreshCatalogContract(ProposalContext context)
      throws HopException {
    RecordDefinition definition = context.definition();
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDefinition"));
    }
    RecordDefinitionCatalogRefreshSupport.RefreshPreview preview =
        RecordDefinitionCatalogRefreshSupport.preview(
            definition, context.variables(), context.metadataProvider());
    RecordDefinitionCatalogRefreshSupport.applyDiscoveredFields(
        definition, preview.discoveredFields(), new Date(), preview.physicalSchemaId());
    ValidationIssueSupport.pruneStaleAcknowledgements(definition, preview.diff(), null);
    RecordDefinitionRegistry.getInstance()
        .update(
            context.validation().catalogConnection(),
            definition,
            context.variables(),
            context.metadataProvider());
    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.RefreshApplied"));
  }

  /**
   * Expand model attributes and prepare target DDL using the <em>catalog</em> field length only.
   * Never reads live discovery for the length and never writes the catalog.
   */
  private static ApplyResult applyExpandModelsFromCatalog(ProposalContext context)
      throws HopException {
    return applyAlignModelsToBaseline(context);
  }

  /**
   * Dangerous (legacy): restore catalog field metadata from a catalog version. Prefer expanding
   * models from the working catalog instead of rewriting the catalog.
   */
  private static ApplyResult applyIgnoreSourceDrift(ProposalContext context) throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    if (Utils.isEmpty(fieldName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingField"));
    }
    if (Utils.isEmpty(context.baselineVersionTag())) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Error.MissingBaselineVersion", fieldName));
    }

    RecordDefinitionKey key = context.validation() != null ? context.validation().key() : null;
    if (key == null && context.definition() != null) {
      key = context.definition().getKey();
    }
    if (key == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDefinition"));
    }

    RecordDefinition baselineDef =
        CatalogVersionService.readDefinition(
                context.validation().catalogConnection(),
                context.baselineVersionTag().trim(),
                key,
                context.variables(),
                context.metadataProvider())
            .orElseThrow(
                () ->
                    new HopException(
                        BaseMessages.getString(
                            PKG,
                            "RemediationProposalApplySupport.Error.BaselineFieldMissing",
                            fieldName,
                            context.baselineVersionTag())));

    SourceField baselineField =
        BaselineContractSupport.findField(BaselineContractSupport.fieldsOf(baselineDef), fieldName);
    if (baselineField == null || Utils.isEmpty(baselineField.getLength())) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "RemediationProposalApplySupport.Error.BaselineFieldMissing",
              fieldName,
              context.baselineVersionTag()));
    }

    String previous = currentCatalogFieldLength(context.definition(), fieldName);
    setCatalogFieldLength(context.definition(), fieldName, baselineField.getLength().trim());
    // Also restore type when present on the baseline.
    if (!Utils.isEmpty(baselineField.getSourceDataType())) {
      setCatalogFieldType(context.definition(), fieldName, baselineField.getSourceDataType());
    }
    RecordDefinitionRegistry.getInstance()
        .update(
            context.validation().catalogConnection(),
            context.definition(),
            context.variables(),
            context.metadataProvider());

    String message =
        BaseMessages.getString(
            PKG,
            "RemediationProposalApplySupport.Result.IgnoreDriftAppliedFromVersion",
            fieldName,
            Const.NVL(previous, "?"),
            baselineField.getLength(),
            context.baselineVersionTag());
    return new ApplyResult(ApplyStatus.APPLIED, message);
  }

  /**
   * Expand model attributes and generate target DDL using the working catalog field length. The
   * catalog itself is never modified (working tree or version snapshots).
   */
  private static ApplyResult applyAlignModelsToBaseline(ProposalContext context)
      throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    if (Utils.isEmpty(fieldName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingField"));
    }
    if (Utils.isEmpty(context.remediationName())) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Error.MissingRemediationName"));
    }

    // Always use the working catalog definition loaded for this issue — never live discovery.
    RecordDefinition catalogDef = context.definition();
    if (catalogDef == null) {
      RecordDefinitionKey key = context.validation() != null ? context.validation().key() : null;
      String catalogConnection =
          context.validation() != null ? context.validation().catalogConnection() : null;
      catalogDef =
          BaselineContractSupport.loadBaselineDefinition(
                  catalogConnection, key, null, context.variables(), context.metadataProvider())
              .orElse(null);
    }
    if (catalogDef == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDefinition"));
    }
    SourceField catalogField =
        BaselineContractSupport.findField(BaselineContractSupport.fieldsOf(catalogDef), fieldName);
    if (catalogField == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "RemediationProposalApplySupport.Error.BaselineFieldMissing",
              fieldName,
              "working catalog"));
    }
    SourceField baselineField = catalogField;
    if (Utils.isEmpty(baselineField.getLength())
        || BaselineContractSupport.parsePositiveInt(baselineField.getLength()) <= 0) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Error.MissingCatalogLength", fieldName));
    }

    List<String> reportLines = new ArrayList<>();
    reportLines.add(
        "Expanding models and target DDL for field '"
            + fieldName
            + "' to catalog length "
            + Const.NVL(baselineField.getLength(), "?")
            + ". The catalog was not modified.");

    List<SourceUsage> usages = new ArrayList<>(relevantUsages(context, fieldName));
    int attributesUpdated = 0;
    Map<String, List<String>> ddlByTable = RemediationDdlWorkflowSupport.newTableMap();
    Map<String, DataVaultModel> modelsByFilename = new LinkedHashMap<>();
    Set<String> tablesWithDdlAttempt = new LinkedHashSet<>();

    for (SourceUsage usage : usages) {
      if (usage == null
          || !SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())
          || Utils.isEmpty(usage.modelFilename())) {
        continue;
      }
      DataVaultModel model = modelsByFilename.get(usage.modelFilename());
      if (model == null) {
        model =
            ResourceDefinitionGroupResolver.loadDataVaultModel(
                usage.modelFilename(), context.variables(), context.metadataProvider());
        modelsByFilename.put(usage.modelFilename(), model);
      }
      IDvTable table = model.findTable(usage.modelElementName());
      if (!(table instanceof DvSatellite satellite)) {
        continue;
      }
      SatelliteAttribute attribute = findAttribute(satellite, fieldName);
      if (attribute == null) {
        continue;
      }
      String oldLength = Const.NVL(attribute.getLength(), "");
      boolean changed = expandAttributeToDiscovered(attribute, baselineField);
      if (changed) {
        attributesUpdated++;
        reportLines.add(
            "Updated satellite attribute "
                + satellite.getName()
                + "."
                + fieldName
                + " length "
                + oldLength
                + " -> "
                + Const.NVL(attribute.getLength(), "")
                + " in model "
                + Const.NVL(usage.modelName(), model.getName()));
        model.setChanged(true);
      }
    }

    for (DataVaultModel model : modelsByFilename.values()) {
      if (model != null && model.hasChanged()) {
        ResourceDefinitionModelPersistenceSupport.saveDataVaultModel(
            model, context.variables(), context.metadataProvider());
        reportLines.add(
            "Saved Data Vault model " + Const.NVL(model.getFilename(), model.getName()));
      }
    }

    String ddlError = null;
    for (SourceUsage usage : usages) {
      if (usage == null
          || !SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())
          || Utils.isEmpty(usage.modelFilename())) {
        continue;
      }
      DataVaultModel model = modelsByFilename.get(usage.modelFilename());
      if (model == null) {
        continue;
      }
      IDvTable table = model.findTable(usage.modelElementName());
      if (!(table instanceof DvSatellite satellite)) {
        continue;
      }
      String tableKey = usage.modelFilename() + "#" + satellite.getName();
      if (!tablesWithDdlAttempt.add(tableKey)) {
        continue;
      }
      try {
        collectTargetDdl(ddlByTable, model, satellite, context);
      } catch (HopException e) {
        ddlError = e.getMessage();
        reportLines.add("DDL generation warning for " + satellite.getName() + ": " + ddlError);
      }
    }

    // Downstream BV/DM columns (explicit maps + SQL lineage via BV). Catalog not modified.
    try {
      collectDownstreamBvDmDdl(
          ddlByTable,
          tablesWithDdlAttempt,
          reportLines,
          context,
          fieldName,
          Const.NVL(baselineField.getLength(), ""),
          usages,
          modelsByFilename);
    } catch (Exception e) {
      reportLines.add(
          "Downstream BV/DM DDL resolution warning: "
              + Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
    }

    String packageFolder =
        SchemaRemediationArtifactsSupport.packageFolder(
            context.variables(), context.remediationName());
    GeneratedArtifacts artifacts = null;
    List<TableDdl> tableDdls = RemediationDdlWorkflowSupport.groupByTable(ddlByTable);
    if (!tableDdls.isEmpty()) {
      String baseName =
          SchemaRemediationArtifactsSupport.sanitizeRemediationName(context.remediationName())
              + "-apply-ddl";
      try {
        artifacts =
            RemediationDdlWorkflowSupport.writeSqlAndWorkflowForTables(
                packageFolder,
                baseName,
                tableDdls,
                context.variables(),
                context.metadataProvider());
        reportLines.add(
            "SQL script written ("
                + artifacts.statementCount()
                + " statement(s) for table(s) "
                + String.join(", ", artifacts.tableNames())
                + "): "
                + artifacts.sqlFilename());
        if (artifacts.workflowWritten()) {
          reportLines.add("DDL workflow written: " + artifacts.workflowFilename());
        } else if (!Utils.isEmpty(artifacts.workflowError())) {
          reportLines.add(
              "DDL workflow was NOT written (SQL script is still available). Reason: "
                  + artifacts.workflowError());
        }
      } catch (Exception e) {
        reportLines.add(
            "Failed to write SQL/workflow artifacts: "
                + Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
      }
    } else if (ddlError != null) {
      reportLines.add("No target DDL statements collected: " + ddlError);
    } else {
      reportLines.add(
          "No target DDL statements needed (physical tables already match model layout, or target database was unavailable). Models still use catalog length "
              + Const.NVL(baselineField.getLength(), "?")
              + ". Catalog was not modified.");
    }

    reportLines.add("Catalog was not modified by this remediation.");

    RemediationPackage pack =
        SchemaRemediationArtifactsSupport.writeReports(
            packageFolder,
            context.remediationName(),
            "Schema remediation: expand models and schemas from catalog length",
            reportLines,
            artifacts != null ? artifacts.workflowFilename() : null,
            artifacts != null ? artifacts.sqlFilename() : null,
            context.variables());

    StringBuilder message = new StringBuilder();
    message
        .append(
            BaseMessages.getString(
                PKG,
                "RemediationProposalApplySupport.Result.AlignModelsApplied",
                fieldName,
                Const.NVL(baselineField.getLength(), "?"),
                attributesUpdated))
        .append('\n');
    message
        .append(
            BaseMessages.getString(
                PKG,
                "RemediationProposalApplySupport.Result.RemediationPackage",
                pack.folder(),
                Const.NVL(pack.workflowFilename(), "(none)"),
                pack.reportHtmlFilename()))
        .append('\n');
    for (String line : reportLines) {
      message.append("• ").append(line).append('\n');
    }
    return new ApplyResult(ApplyStatus.APPLIED, message.toString().trim());
  }

  private static ApplyResult applyGenerateTargetDdlPackage(ProposalContext context)
      throws HopException {
    if (Utils.isEmpty(context.remediationName())) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Error.MissingRemediationName"));
    }
    List<SourceUsage> usages =
        context.validation() != null && context.validation().usages() != null
            ? context.validation().usages()
            : List.of();
    Map<String, List<String>> ddlByTable = RemediationDdlWorkflowSupport.newTableMap();
    Map<String, DataVaultModel> modelsByFilename = new LinkedHashMap<>();
    Set<String> tablesWithDdlAttempt = new LinkedHashSet<>();
    List<String> reportLines = new ArrayList<>();
    reportLines.add("Generated target DDL package only (catalog and models not modified).");

    for (SourceUsage usage : usages) {
      if (usage == null
          || !SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())
          || Utils.isEmpty(usage.modelFilename())) {
        continue;
      }
      DataVaultModel model = modelsByFilename.get(usage.modelFilename());
      if (model == null) {
        model =
            ResourceDefinitionGroupResolver.loadDataVaultModel(
                usage.modelFilename(), context.variables(), context.metadataProvider());
        modelsByFilename.put(usage.modelFilename(), model);
      }
      if (DvReadOnlyExistingVaultSupport.isReadOnly(model)) {
        continue;
      }
      IDvTable table = model.findTable(usage.modelElementName());
      if (!(table instanceof DvSatellite satellite)) {
        continue;
      }
      String tableKey = usage.modelFilename() + "#" + satellite.getName();
      if (!tablesWithDdlAttempt.add(tableKey)) {
        continue;
      }
      try {
        collectTargetDdl(ddlByTable, model, satellite, context);
      } catch (HopException e) {
        reportLines.add(
            "DDL generation warning for " + satellite.getName() + ": " + e.getMessage());
      }
    }

    List<TableDdl> tableDdls = RemediationDdlWorkflowSupport.groupByTable(ddlByTable);
    if (tableDdls.isEmpty()) {
      return new ApplyResult(
          ApplyStatus.NEEDS_MANUAL,
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.NoDdlGenerated"));
    }
    String packageFolder =
        SchemaRemediationArtifactsSupport.packageFolder(
            context.variables(), context.remediationName());
    String baseName =
        SchemaRemediationArtifactsSupport.sanitizeRemediationName(context.remediationName())
            + "-apply-ddl";
    GeneratedArtifacts artifacts =
        RemediationDdlWorkflowSupport.writeSqlAndWorkflowForTables(
            packageFolder, baseName, tableDdls, context.variables(), context.metadataProvider());
    reportLines.add(
        "Workflow: "
            + artifacts.workflowFilename()
            + " ("
            + artifacts.tableNames().size()
            + " table actions)");
    RemediationPackage pack =
        SchemaRemediationArtifactsSupport.writeReports(
            packageFolder,
            context.remediationName(),
            "Schema remediation: expand target database schemas (DDL workflow)",
            reportLines,
            artifacts.workflowFilename(),
            artifacts.sqlFilename(),
            context.variables());
    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(
            PKG,
            "RemediationProposalApplySupport.Result.RemediationPackage",
            pack.folder(),
            Const.NVL(pack.workflowFilename(), "(none)"),
            pack.reportHtmlFilename()));
  }

  /**
   * Expands (never shrinks) satellite attribute length/type from the discovered source field.
   * Package-visible for unit tests.
   */
  static boolean expandAttributeToDiscovered(
      SatelliteAttribute attribute, SourceField discoveredField) {
    boolean changed = false;
    if (discoveredField == null || attribute == null) {
      return false;
    }

    if (discoveredField.getHopType() > 0) {
      try {
        String hopTypeName = ValueMetaFactory.getValueMetaName(discoveredField.getHopType());
        if (!Utils.isEmpty(hopTypeName)
            && !"-".equals(hopTypeName)
            && !hopTypeName.equalsIgnoreCase(Const.NVL(attribute.getDataType(), ""))) {
          attribute.setDataType(hopTypeName);
          changed = true;
        }
      } catch (Exception ignored) {
        // Keep existing data type when Hop type cannot be resolved.
      }
    }

    String expanded = preferLongerLength(attribute.getLength(), discoveredField.getLength());
    if (!Utils.isEmpty(expanded) && !expanded.equals(Const.NVL(attribute.getLength(), ""))) {
      attribute.setLength(expanded);
      changed = true;
    }
    if (!Utils.isEmpty(discoveredField.getPrecision())
        && !discoveredField.getPrecision().equals(Const.NVL(attribute.getPrecision(), ""))) {
      // Precision: take the larger absolute value when both numeric.
      String prec = preferLongerLength(attribute.getPrecision(), discoveredField.getPrecision());
      if (!Utils.isEmpty(prec) && !prec.equals(Const.NVL(attribute.getPrecision(), ""))) {
        attribute.setPrecision(prec);
        changed = true;
      }
    }
    return changed;
  }

  /** Legacy name used by unit tests — expands only (never shrinks length). */
  static boolean applyDiscoveredTypeToAttribute(
      SatelliteAttribute attribute, SourceField discoveredField) {
    return expandAttributeToDiscovered(attribute, discoveredField);
  }

  static String preferLongerLength(String current, String discovered) {
    int c = parsePositiveInt(current);
    int d = parsePositiveInt(discovered);
    if (c > 0 && d > 0) {
      return Integer.toString(Math.max(c, d));
    }
    if (d > 0) {
      return discovered.trim();
    }
    return current;
  }

  static String parseLengthSide(String details, boolean oldSide) {
    if (Utils.isEmpty(details)) {
      return null;
    }
    Matcher matcher = LENGTH_CHANGE.matcher(details);
    if (!matcher.find()) {
      return null;
    }
    String token = oldSide ? matcher.group(1) : matcher.group(2);
    if (token == null || "?".equals(token)) {
      return null;
    }
    return token;
  }

  private static int parsePositiveInt(String value) {
    if (Utils.isEmpty(value)) {
      return -1;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static SourceField findField(List<SourceField> fields, String fieldName) {
    if (fields == null || Utils.isEmpty(fieldName)) {
      return null;
    }
    for (SourceField field : fields) {
      if (field != null && field.getName() != null && fieldName.equalsIgnoreCase(field.getName())) {
        return field;
      }
    }
    return null;
  }

  private static String currentCatalogFieldLength(RecordDefinition definition, String fieldName) {
    if (definition == null
        || definition.getDvSource() == null
        || definition.getDvSource().getFields() == null
        || Utils.isEmpty(fieldName)) {
      return null;
    }
    for (CatalogSourceField field : definition.getDvSource().getFields()) {
      if (field != null && field.getName() != null && fieldName.equalsIgnoreCase(field.getName())) {
        return field.getLength();
      }
    }
    return null;
  }

  private static void setCatalogFieldLength(
      RecordDefinition definition, String fieldName, String length) throws HopException {
    if (definition == null || definition.getDvSource() == null || Utils.isEmpty(fieldName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDefinition"));
    }
    List<CatalogSourceField> fields = definition.getDvSource().getFields();
    if (fields == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Error.DiscoveredFieldMissing", fieldName));
    }
    boolean found = false;
    for (CatalogSourceField field : fields) {
      if (field != null && field.getName() != null && fieldName.equalsIgnoreCase(field.getName())) {
        field.setLength(length);
        found = true;
        break;
      }
    }
    if (!found) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Error.DiscoveredFieldMissing", fieldName));
    }
    if (definition.getDvSource() != null) {
      definition.getDvSource().setFields(fields);
    }
    // Structured fields authority; regenerate derived IRowMeta.
    DvSourceFieldSupport.prepareForPersistence(definition);
  }

  private static void collectTargetDdl(
      Map<String, List<String>> ddlByTable,
      DataVaultModel model,
      DvSatellite satellite,
      ProposalContext context)
      throws HopException {
    if (DvReadOnlyExistingVaultSupport.isReadOnly(model)) {
      return;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    DatabaseMeta targetDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(context.metadataProvider(), config);
    if (targetDatabaseMeta == null || Utils.isEmpty(targetDatabaseMeta.getName())) {
      return;
    }
    try {
      for (String ddl :
          satellite.generateUpdateDdl(context.metadataProvider(), context.variables(), model)) {
        RemediationDdlWorkflowSupport.addTableStatement(
            ddlByTable, targetDatabaseMeta.getName(), satellite.getName(), ddl);
      }
    } catch (HopException e) {
      // Surface DDL connectivity problems without undoing model/catalog updates.
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "RemediationProposalApplySupport.Error.DdlGenerateFailed",
              satellite.getName(),
              e.getMessage()),
          e);
    }
  }

  /**
   * After DV satellite lengths are expanded, resolve BV SCD2 mappings and DM SQL lineage (via BV
   * target field names) and collect physical DDL. Does not change the catalog.
   */
  private static void collectDownstreamBvDmDdl(
      Map<String, List<String>> ddlByTable,
      Set<String> tablesWithDdlAttempt,
      List<String> reportLines,
      ProposalContext context,
      String sourceFieldName,
      String catalogLength,
      List<SourceUsage> dvUsages,
      Map<String, DataVaultModel> modelsByFilename)
      throws HopException {
    if (context.group() == null) {
      return;
    }
    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(
            context.group(), context.variables(), context.metadataProvider());
    Set<String> satelliteNames =
        RemediationTargetResolutionSupport.satelliteNamesFromUsages(dvUsages, sourceFieldName);
    List<RemediationTargetColumn> targets =
        RemediationTargetResolutionSupport.resolveDownstreamTargets(
            models, sourceFieldName, catalogLength, satelliteNames, context.variables());
    if (targets.isEmpty()) {
      reportLines.add(
          "No explicit BV/DM field targets resolved for '"
              + sourceFieldName
              + "' (SQL-only dimensions need a BV field name match).");
      return;
    }

    Map<String, BusinessVaultModel> bvByFile = new LinkedHashMap<>();
    Map<String, DimensionalModel> dmByFile = new LinkedHashMap<>();
    Map<String, DataVaultModel> dvByFile = new LinkedHashMap<>(modelsByFilename);

    for (ValidationModels.LoadedDataVaultModel loaded : models.dataVaultModels()) {
      if (loaded != null
          && loaded.model() != null
          && !Utils.isEmpty(loaded.model().getFilename())) {
        dvByFile.putIfAbsent(loaded.model().getFilename(), loaded.model());
      }
    }
    for (ValidationModels.LoadedBusinessVaultModel loaded : models.businessVaultModels()) {
      if (loaded != null
          && loaded.model() != null
          && !Utils.isEmpty(loaded.model().getFilename())) {
        bvByFile.put(loaded.model().getFilename(), loaded.model());
        if (loaded.dvModel() != null && !Utils.isEmpty(loaded.dvModel().getFilename())) {
          dvByFile.putIfAbsent(loaded.dvModel().getFilename(), loaded.dvModel());
        }
      }
    }
    for (ValidationModels.LoadedDimensionalModel loaded : models.dimensionalModels()) {
      if (loaded != null
          && loaded.model() != null
          && !Utils.isEmpty(loaded.model().getFilename())) {
        dmByFile.put(loaded.model().getFilename(), loaded.model());
      }
    }

    for (RemediationTargetColumn target : targets) {
      reportLines.add(
          "Downstream target "
              + target.layer()
              + " "
              + Const.NVL(target.physicalTableName(), target.tableElementName())
              + "."
              + target.targetFieldName()
              + " (from catalog field "
              + sourceFieldName
              + ", length "
              + catalogLength
              + ", confidence "
              + target.confidence()
              + ")");
      String tableKey = target.modelFilename() + "#" + target.tableElementName();
      if (!tablesWithDdlAttempt.add(tableKey)) {
        continue;
      }
      try {
        if (RemediationTargetColumn.LAYER_BV.equals(target.layer())) {
          collectBvTableDdl(ddlByTable, target, bvByFile, dvByFile, context);
        } else if (RemediationTargetColumn.LAYER_DM.equals(target.layer())) {
          collectDmTableDdl(ddlByTable, target, dmByFile, context);
        }
      } catch (HopException e) {
        reportLines.add(
            "DDL generation warning for "
                + target.tableElementName()
                + ": "
                + Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
      }
    }
  }

  private static void collectBvTableDdl(
      Map<String, List<String>> ddlByTable,
      RemediationTargetColumn target,
      Map<String, BusinessVaultModel> bvByFile,
      Map<String, DataVaultModel> dvByFile,
      ProposalContext context)
      throws HopException {
    BusinessVaultModel bvModel = findBvModel(bvByFile, target.modelFilename(), target.modelName());
    if (bvModel == null) {
      throw new HopException("Business Vault model not loaded: " + target.modelFilename());
    }
    IBvTable table = bvModel.findTable(target.tableElementName());
    if (table == null) {
      throw new HopException("Business Vault table not found: " + target.tableElementName());
    }
    DataVaultModel dvModel = null;
    for (DataVaultModel candidate : dvByFile.values()) {
      if (candidate != null) {
        dvModel = candidate;
        break;
      }
    }
    if (dvModel == null) {
      throw new HopException(
          "Data Vault model required for BV DDL of " + target.tableElementName());
    }
    String connection =
        !Utils.isEmpty(target.connectionName())
            ? target.connectionName()
            : Const.NVL(
                bvModel.getConfigurationOrDefault() != null
                    ? bvModel.getConfigurationOrDefault().getTargetDatabase()
                    : null,
                "Vault");
    IRowMeta layout =
        table.getTargetTableLayout(
            context.metadataProvider(), context.variables(), bvModel, dvModel);
    List<String> statements =
        generateDdlWithForcedFieldLength(
            layout,
            target,
            connection,
            BvTargetDatabaseSupport.loadTargetDatabase(
                context.metadataProvider(), bvModel.getConfigurationOrDefault()),
            context);
    for (String ddl : statements) {
      RemediationDdlWorkflowSupport.addTableStatement(
          ddlByTable,
          connection,
          Const.NVL(target.physicalTableName(), target.tableElementName()),
          ddl);
    }
  }

  private static void collectDmTableDdl(
      Map<String, List<String>> ddlByTable,
      RemediationTargetColumn target,
      Map<String, DimensionalModel> dmByFile,
      ProposalContext context)
      throws HopException {
    DimensionalModel dmModel = findDmModel(dmByFile, target.modelFilename(), target.modelName());
    if (dmModel == null) {
      throw new HopException("Dimensional model not loaded: " + target.modelFilename());
    }
    IDmTable table = dmModel.findTable(target.tableElementName());
    if (table == null) {
      throw new HopException("Dimensional table not found: " + target.tableElementName());
    }
    String connection =
        !Utils.isEmpty(target.connectionName())
            ? target.connectionName()
            : Const.NVL(
                dmModel.getConfigurationOrDefault() != null
                    ? dmModel.getConfigurationOrDefault().getTargetDatabase()
                    : null,
                "Vault");
    // DM attribute layouts inherit string lengths from SQL source discovery (often still the old
    // physical length). Force the catalog length on the remediating column before DDL compare.
    IRowMeta layout =
        table.getTargetTableLayout(context.metadataProvider(), context.variables(), dmModel);
    List<String> statements =
        generateDdlWithForcedFieldLength(
            layout,
            target,
            connection,
            DmTargetDatabaseSupport.loadTargetDatabase(
                context.metadataProvider(), dmModel.getConfigurationOrDefault()),
            context);
    for (String ddl : statements) {
      RemediationDdlWorkflowSupport.addTableStatement(
          ddlByTable,
          connection,
          Const.NVL(target.physicalTableName(), target.tableElementName()),
          ddl);
    }
  }

  /**
   * Builds Hop structure DDL for a table after setting {@code target.targetFieldName()} length to
   * the catalog length. Used for BV/DM where layout may still reflect old physical/SQL types.
   */
  private static List<String> generateDdlWithForcedFieldLength(
      IRowMeta layout,
      RemediationTargetColumn target,
      String connectionName,
      DatabaseMeta targetDatabaseMeta,
      ProposalContext context)
      throws HopException {
    if (layout == null || layout.isEmpty()) {
      throw new HopException(
          "Empty target layout for " + Const.NVL(target.tableElementName(), "?"));
    }
    int catalogLen = BaselineContractSupport.parsePositiveInt(target.catalogLength());
    if (catalogLen <= 0) {
      throw new HopException(
          "Catalog length missing for remediation of " + target.targetFieldName());
    }
    IRowMeta forced = layout.clone();
    IValueMeta field = forced.searchValueMeta(target.targetFieldName());
    if (field == null) {
      // Layout may use different casing; scan case-insensitively.
      for (int i = 0; i < forced.size(); i++) {
        IValueMeta candidate = forced.getValueMeta(i);
        if (candidate != null && target.targetFieldName().equalsIgnoreCase(candidate.getName())) {
          field = candidate;
          break;
        }
      }
    }
    if (field == null) {
      ValueMetaString added = new ValueMetaString(target.targetFieldName());
      added.setLength(catalogLen);
      forced.addValueMeta(added);
    } else {
      // Expand only (never shrink layout length below catalog for remediation).
      int current = field.getLength();
      if (current <= 0 || current < catalogLen) {
        field.setLength(catalogLen);
      }
    }

    if (targetDatabaseMeta == null || Utils.isEmpty(targetDatabaseMeta.getName())) {
      // No DB meta: still emit a reviewable comment so the package is not silent.
      return List.of(
          "-- "
              + Const.NVL(target.physicalTableName(), target.tableElementName())
              + "."
              + target.targetFieldName()
              + " should be at least length "
              + catalogLen
              + " (no target database metadata for connection "
              + Const.NVL(connectionName, "?")
              + ")");
    }

    String tableName = Const.NVL(target.physicalTableName(), target.tableElementName());
    SimpleLoggingObject logging =
        new SimpleLoggingObject(
            "RemediationProposalApplySupport.generateDdlWithForcedFieldLength",
            LoggingObjectType.GENERAL,
            null);
    try (Database db = new Database(logging, context.variables(), targetDatabaseMeta)) {
      db.connect();
      String ddl =
          DvDdlSupport.getTargetTableDdl(
              db,
              tableName,
              forced,
              TargetTypeMappingSupport.resolve(
                  null, targetDatabaseMeta, context.metadataProvider(), context.variables()));
      if (Utils.isEmpty(ddl)) {
        return List.of(
            "-- "
                + tableName
                + "."
                + target.targetFieldName()
                + ": no structure change vs physical table after forcing length "
                + catalogLen
                + " (already wide enough or column missing from compare)");
      }
      return List.of(ddl.trim());
    } catch (Exception e) {
      throw new HopException(
          "Error generating DDL for "
              + tableName
              + "."
              + target.targetFieldName()
              + ": "
              + Const.NVL(e.getMessage(), e.getClass().getSimpleName()),
          e);
    }
  }

  private static BusinessVaultModel findBvModel(
      Map<String, BusinessVaultModel> byFile, String filename, String modelName) {
    if (byFile == null) {
      return null;
    }
    if (!Utils.isEmpty(filename) && byFile.containsKey(filename)) {
      return byFile.get(filename);
    }
    for (Map.Entry<String, BusinessVaultModel> entry : byFile.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      if (!Utils.isEmpty(filename)
          && entry.getKey() != null
          && (entry.getKey().endsWith(filename) || filename.endsWith(entry.getKey()))) {
        return entry.getValue();
      }
      if (!Utils.isEmpty(modelName) && modelName.equalsIgnoreCase(entry.getValue().getName())) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static DimensionalModel findDmModel(
      Map<String, DimensionalModel> byFile, String filename, String modelName) {
    if (byFile == null) {
      return null;
    }
    if (!Utils.isEmpty(filename) && byFile.containsKey(filename)) {
      return byFile.get(filename);
    }
    for (Map.Entry<String, DimensionalModel> entry : byFile.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      if (!Utils.isEmpty(filename)
          && entry.getKey() != null
          && (entry.getKey().endsWith(filename) || filename.endsWith(entry.getKey()))) {
        return entry.getValue();
      }
      if (!Utils.isEmpty(modelName) && modelName.equalsIgnoreCase(entry.getValue().getName())) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static void setCatalogFieldType(
      RecordDefinition definition, String fieldName, String sourceDataType) throws HopException {
    if (definition == null || definition.getDvSource() == null || Utils.isEmpty(fieldName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDefinition"));
    }
    List<CatalogSourceField> fields = definition.getDvSource().getFields();
    if (fields == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDefinition"));
    }
    for (CatalogSourceField field : fields) {
      if (field != null && field.getName() != null && fieldName.equalsIgnoreCase(field.getName())) {
        field.setSourceDataType(sourceDataType);
        return;
      }
    }
    throw new HopException(
        BaseMessages.getString(
            PKG, "RemediationProposalApplySupport.Error.MissingField", fieldName));
  }

  private static ApplyResult applyAddNewSatellite(ProposalContext context) throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    SourceUsage usage = firstDataVaultUsage(context);
    if (usage == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDvUsage"));
    }

    DataVaultModel model =
        ResourceDefinitionGroupResolver.loadDataVaultModel(
            usage.modelFilename(), context.variables(), context.metadataProvider());
    String hubName = resolveHubForUsage(model, usage);
    if (Utils.isEmpty(hubName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingHub"));
    }

    String recordSource = resolveRecordSource(context);
    String satelliteName = suggestSatelliteName(hubName, fieldName);
    if (model.findTable(satelliteName) != null) {
      satelliteName = satelliteName + "_EXTRA";
    }

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.ADD_SATELLITE);
    proposal.setParameters(
        java.util.Map.of(
            "name",
            satelliteName,
            "hubName",
            hubName,
            "recordSource",
            recordSource,
            "attributeNames",
            Utils.isEmpty(fieldName) ? "" : fieldName));

    DvAiProposalApplier.apply(
        model, List.of(proposal), context.metadataProvider(), context.variables());
    ResourceDefinitionModelPersistenceSupport.saveDataVaultModel(
        model, context.variables(), context.metadataProvider());

    if (context.hopGui() != null) {
      ResourceDefinitionModelNavigationSupport.openDataVaultUsage(
          context.hopGui(), usage, satelliteName, context.variables());
    }

    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(
            PKG, "RemediationProposalApplySupport.Result.AddSatelliteApplied", satelliteName));
  }

  private static ApplyResult applyExtendExistingSatellite(ProposalContext context)
      throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    if (Utils.isEmpty(fieldName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingField"));
    }
    SourceUsage usage = firstDataVaultUsage(context);
    if (usage == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDvUsage"));
    }

    DataVaultModel model =
        ResourceDefinitionGroupResolver.loadDataVaultModel(
            usage.modelFilename(), context.variables(), context.metadataProvider());
    IDvTable table = model.findTable(usage.modelElementName());
    if (!(table instanceof DvSatellite satellite)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Error.NotSatellite", usage.modelElementName()));
    }
    if (findAttribute(satellite, fieldName) != null) {
      return new ApplyResult(
          ApplyStatus.APPLIED,
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Result.ExtendAlreadyPresent", fieldName));
    }

    SourceField discoveredField = resolveDiscoveredField(context, fieldName);
    SatelliteAttribute attribute = new SatelliteAttribute(fieldName);
    if (discoveredField != null) {
      String dataTypeLabel = DvDataTypeSupport.preferredDataTypeLabel(discoveredField);
      if (!Utils.isEmpty(dataTypeLabel)) {
        attribute.setDataType(dataTypeLabel);
      }
      if (!Utils.isEmpty(discoveredField.getLength())) {
        attribute.setLength(discoveredField.getLength());
      }
      if (!Utils.isEmpty(discoveredField.getPrecision())) {
        attribute.setPrecision(discoveredField.getPrecision());
      }
    }
    satellite.getAttributes().add(attribute);
    model.setChanged(true);
    ResourceDefinitionModelPersistenceSupport.saveDataVaultModel(
        model, context.variables(), context.metadataProvider());

    if (context.hopGui() != null) {
      ResourceDefinitionModelNavigationSupport.openDataVaultUsage(
          context.hopGui(), usage, usage.modelElementName(), context.variables());
    }

    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(
            PKG,
            "RemediationProposalApplySupport.Result.ExtendApplied",
            fieldName,
            usage.modelElementName()));
  }

  private static ApplyResult applyReviewMappings(ProposalContext context) throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    List<SourceUsage> usages = relevantUsages(context, fieldName);
    if (usages.isEmpty() && context.validation() != null) {
      usages = context.validation().usages();
    }
    if (usages.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingUsages"));
    }
    if (context.hopGui() == null) {
      return new ApplyResult(
          ApplyStatus.NEEDS_MANUAL,
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.ReviewManual"));
    }

    Set<String> opened = new LinkedHashSet<>();
    for (SourceUsage usage : usages) {
      String key = usage.modelType() + ":" + usage.modelFilename() + ":" + usage.modelElementName();
      if (!opened.add(key)) {
        continue;
      }
      ResourceDefinitionModelNavigationSupport.openUsage(
          context.hopGui(), usage, context.variables());
    }
    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.ReviewOpened"));
  }

  private static SourceField resolveDiscoveredField(ProposalContext context, String fieldName)
      throws HopException {
    RecordDefinition definition = context.definition();
    if (definition == null) {
      return null;
    }
    RecordDefinitionCatalogRefreshSupport.RefreshPreview preview =
        RecordDefinitionCatalogRefreshSupport.preview(
            definition, context.variables(), context.metadataProvider());
    for (SourceField field : preview.discoveredFields()) {
      if (field != null && field.getName() != null && fieldName.equalsIgnoreCase(field.getName())) {
        return field;
      }
    }
    return null;
  }

  private static List<SourceUsage> relevantUsages(ProposalContext context, String fieldName) {
    if (context.validation() == null || context.validation().usages() == null) {
      return List.of();
    }
    if (Utils.isEmpty(fieldName)) {
      return context.validation().usages();
    }
    List<SourceUsage> usages = new ArrayList<>();
    for (SourceUsage usage : context.validation().usages()) {
      if (usage.mappedFields() == null) {
        continue;
      }
      for (String mapped : usage.mappedFields()) {
        if (mapped != null && fieldName.equalsIgnoreCase(mapped)) {
          usages.add(usage);
          break;
        }
      }
    }
    return usages;
  }

  private static SourceUsage firstDataVaultUsage(ProposalContext context) {
    if (context.validation() == null) {
      return null;
    }
    for (SourceUsage usage : context.validation().usages()) {
      if (SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())) {
        return usage;
      }
    }
    return null;
  }

  private static String resolveHubForUsage(DataVaultModel model, SourceUsage usage) {
    if (usage == null || model == null) {
      return null;
    }
    IDvTable table = model.findTable(usage.modelElementName());
    if (table instanceof DvSatellite satellite && !Utils.isEmpty(satellite.getHubName())) {
      return satellite.getHubName();
    }
    if (table instanceof DvHub hub) {
      return hub.getName();
    }
    for (IDvTable candidate : model.getTables()) {
      if (candidate instanceof DvHub hub) {
        return hub.getName();
      }
    }
    return null;
  }

  private static String resolveRecordSource(ProposalContext context) {
    if (context.validation() != null
        && context.validation().key() != null
        && !Utils.isEmpty(context.validation().key().getName())) {
      return context.validation().key().getName();
    }
    RecordDefinition definition = context.definition();
    if (definition != null
        && definition.getDvSource() != null
        && !Utils.isEmpty(definition.getDvSource().getGroup())) {
      return definition.getDvSource().getGroup();
    }
    return "source";
  }

  private static String suggestSatelliteName(String hubName, String fieldName) {
    String hubToken = Utils.isEmpty(hubName) ? "SAT" : hubName;
    if (hubToken.startsWith("HUB_")) {
      hubToken = hubToken.substring(4);
    }
    String fieldToken =
        Utils.isEmpty(fieldName) ? "EXTRA" : fieldName.toUpperCase().replace(' ', '_');
    return "SAT_" + hubToken + "_" + fieldToken;
  }

  private static SatelliteAttribute findAttribute(DvSatellite satellite, String fieldName) {
    if (satellite == null || satellite.getAttributes() == null || Utils.isEmpty(fieldName)) {
      return null;
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      if (attribute != null
          && attribute.getName() != null
          && fieldName.equalsIgnoreCase(attribute.getName())) {
        return attribute;
      }
    }
    return null;
  }
}
