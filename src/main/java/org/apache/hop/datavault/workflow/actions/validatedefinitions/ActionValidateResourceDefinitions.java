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
package org.apache.hop.datavault.workflow.actions.validatedefinitions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.versioning.CatalogVersionEntry;
import org.apache.hop.catalog.versioning.CatalogVersionService;
import org.apache.hop.core.Const;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.SchemaCompareMode;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationRequest;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationResult;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationService;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationFailureSeverity;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFileWriter;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFormatter;
import org.apache.hop.datavault.resourcedefinition.ValidationFindingFormatter;
import org.apache.hop.datavault.resourcedefinition.ValidationReport;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * CI/CD schema gate: validates source contracts for a resource definition group, optionally against
 * a tagged catalog version, writes Markdown/HTML reports, and fails by configured severity.
 */
@Action(
    id = "VALIDATE_RESOURCE_DEFINITIONS",
    name = "i18n::ActionValidateResourceDefinitions.Name",
    description = "i18n::ActionValidateResourceDefinitions.Description",
    image = "datavault-model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionValidateResourceDefinitions.Keywords",
    documentationUrl = "/workflow/actions/validatedefinitions.html")
@GuiPlugin(description = "Validate Resource Definitions action")
@Getter
@Setter
public class ActionValidateResourceDefinitions extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionValidateResourceDefinitions.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "VALIDATE_RESOURCE_DEFINITIONS_ACTION";

  /** Stable widget ids for GuiRegistry / dialog refresh (empty annotation ids are fragile). */
  public static final String WIDGET_ID_RESOURCE_GROUP = "validate-resource-group";

  public static final String WIDGET_ID_TARGET_VERSION = "validate-target-catalog-version";
  public static final String WIDGET_ID_COMPARE_MODE = "validate-compare-mode";
  public static final String WIDGET_ID_BASELINE_VERSION = "validate-baseline-catalog-version";
  public static final String WIDGET_ID_REPORT_PATH = "validate-report-output-path";
  public static final String WIDGET_ID_REPORT_BASENAME = "validate-report-file-basename";
  public static final String WIDGET_ID_REPORT_FORMAT = "validate-report-format";
  public static final String WIDGET_ID_FAILURE_SEVERITY = "validate-failure-severity";
  public static final String WIDGET_ID_FAIL_ON_WARNINGS = "validate-fail-on-warnings";
  public static final String WIDGET_ID_INCLUDE_IMPACT = "validate-include-impact";
  public static final String WIDGET_ID_CHECK_TARGET_DATABASES = "validate-check-target-databases";
  public static final String WIDGET_ID_EXPECT_AUTO_TARGET_CREATE =
      "validate-expect-auto-target-create";

  /** Extension-data key for downstream actions that want the report text. */
  public static final String RESULT_ATTR_REPORT = "schemaValidationReportText";

  @GuiWidgetElement(
      id = WIDGET_ID_RESOURCE_GROUP,
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::ActionValidateResourceDefinitions.Group.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  /**
   * Editable ComboVar (variables=true). Tag items are filled by the dialog after widgets are
   * created and when the resource group changes — not via comboValuesMethod during create — so a
   * catalog listing failure cannot blank the entire settings panel.
   */
  @GuiWidgetElement(
      id = WIDGET_ID_TARGET_VERSION,
      order = "0200",
      type = GuiElementType.COMBO,
      variables = true,
      label = "i18n::ActionValidateResourceDefinitions.TargetCatalogVersion.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.TargetCatalogVersion.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String targetCatalogVersion;

  @GuiWidgetElement(
      id = WIDGET_ID_COMPARE_MODE,
      order = "0300",
      type = GuiElementType.COMBO,
      variables = false,
      comboValuesMethod = "getCompareModeOptions",
      label = "i18n::ActionValidateResourceDefinitions.CompareMode.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.CompareMode.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String compareMode = SchemaCompareMode.LIVE_SOURCE.name();

  /** Same ComboVar pattern as {@link #targetCatalogVersion}; items filled by the dialog. */
  @GuiWidgetElement(
      id = WIDGET_ID_BASELINE_VERSION,
      order = "0400",
      type = GuiElementType.COMBO,
      variables = true,
      label = "i18n::ActionValidateResourceDefinitions.BaselineVersion.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.BaselineVersion.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String baselineCatalogVersion;

  @GuiWidgetElement(
      id = WIDGET_ID_REPORT_PATH,
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionValidateResourceDefinitions.ReportOutputPath.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.ReportOutputPath.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportOutputPath;

  @GuiWidgetElement(
      id = WIDGET_ID_REPORT_BASENAME,
      order = "0600",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionValidateResourceDefinitions.ReportFileBaseName.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.ReportFileBaseName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportFileBaseName;

  @GuiWidgetElement(
      id = WIDGET_ID_REPORT_FORMAT,
      order = "0700",
      type = GuiElementType.COMBO,
      variables = false,
      comboValuesMethod = "getReportFormatOptions",
      label = "i18n::ActionValidateResourceDefinitions.ReportFormat.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.ReportFormat.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportFormat = SchemaValidationReportFileWriter.ReportFormat.MARKDOWN.name();

  @GuiWidgetElement(
      id = WIDGET_ID_FAILURE_SEVERITY,
      order = "0800",
      type = GuiElementType.COMBO,
      variables = false,
      comboValuesMethod = "getFailureSeverityOptions",
      label = "i18n::ActionValidateResourceDefinitions.FailureSeverity.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.FailureSeverity.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String failureSeverity = SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.name();

  /**
   * Legacy checkbox kept for backward compatibility with existing workflows. When true and failure
   * severity is unset/default, behaves as {@link SchemaValidationFailureSeverity#FAIL_ON_WARNINGS}.
   */
  @GuiWidgetElement(
      id = WIDGET_ID_FAIL_ON_WARNINGS,
      order = "0900",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionValidateResourceDefinitions.FailOnWarnings.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.FailOnWarnings.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failOnWarnings;

  @GuiWidgetElement(
      id = WIDGET_ID_INCLUDE_IMPACT,
      order = "1000",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionValidateResourceDefinitions.IncludeImpact.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.IncludeImpact.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeImpact = true;

  @GuiWidgetElement(
      id = WIDGET_ID_CHECK_TARGET_DATABASES,
      order = "1100",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionValidateResourceDefinitions.CheckTargetDatabases.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.CheckTargetDatabases.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean checkTargetDatabases;

  /**
   * Y/N or Hop variable (e.g. {@code ${EXPECT_AUTOMATIC_TARGET_TABLE_CREATION}}). When true and
   * target DB checks are on, missing-table CREATE findings are omitted (vault update will create
   * them). ALTER drift on existing tables still warns.
   */
  @GuiWidgetElement(
      id = WIDGET_ID_EXPECT_AUTO_TARGET_CREATE,
      order = "1200",
      type = GuiElementType.COMBO,
      variables = true,
      comboValuesMethod = "getYesNoOptions",
      label = "i18n::ActionValidateResourceDefinitions.ExpectAutoTargetCreate.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.ExpectAutoTargetCreate.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String expectAutomaticTargetTableCreation;

  public ActionValidateResourceDefinitions() {
    super();
    this.expectAutomaticTargetTableCreation = "N";
  }

  public ActionValidateResourceDefinitions(ActionValidateResourceDefinitions meta) {
    super(meta);
    this.resourceDefinitionGroup = meta.resourceDefinitionGroup;
    this.targetCatalogVersion = meta.targetCatalogVersion;
    this.compareMode = meta.compareMode;
    this.baselineCatalogVersion = meta.baselineCatalogVersion;
    this.reportOutputPath = meta.reportOutputPath;
    this.reportFileBaseName = meta.reportFileBaseName;
    this.reportFormat = meta.reportFormat;
    this.failureSeverity = meta.failureSeverity;
    this.failOnWarnings = meta.failOnWarnings;
    this.includeImpact = meta.includeImpact;
    this.checkTargetDatabases = meta.checkTargetDatabases;
    this.expectAutomaticTargetTableCreation =
        meta.expectAutomaticTargetTableCreation != null
            ? meta.expectAutomaticTargetTableCreation
            : "N";
  }

  /**
   * Hop GUI comboValuesMethod contract: {@code (ILogChannel, IHopMetadataProvider) -> List}. With
   * {@code variables=true}, COMBO fields render as ComboVar.
   */
  public List<String> getCompareModeOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        SchemaCompareMode.LIVE_SOURCE.name(),
        SchemaCompareMode.WORKING_VS_VERSION.name(),
        SchemaCompareMode.VERSION_VS_VERSION.name());
  }

  public List<String> getReportFormatOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        SchemaValidationReportFileWriter.ReportFormat.MARKDOWN.name(),
        SchemaValidationReportFileWriter.ReportFormat.HTML.name(),
        SchemaValidationReportFileWriter.ReportFormat.BOTH.name());
  }

  public List<String> getFailureSeverityOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.name(),
        SchemaValidationFailureSeverity.FAIL_ON_WARNINGS.name(),
        SchemaValidationFailureSeverity.WARN_ONLY.name());
  }

  public List<String> getYesNoOptions(ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList("Y", "N");
  }

  /**
   * Catalog version tags for the FILE catalog of the selected resource definition group. Editable
   * ComboVar still allows free typing (including variable expressions). Never returns null; never
   * throws (Hop GuiCompositeWidgets only catches Exception around invoke, so we swallow Throwable).
   */
  public List<String> getCatalogVersionTagOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    try {
      return listCatalogVersionTags(metadataProvider, log, this);
    } catch (Throwable t) {
      if (log != null) {
        log.logError(
            "Unable to list catalog version tags for group '" + resourceDefinitionGroup + "'", t);
      }
      return new ArrayList<>();
    }
  }

  /**
   * Same as {@link #getCatalogVersionTagOptions} but usable from the dialog after group changes.
   * Pass design-time variables (dialog / HopGui project vars) so FILE catalog paths like {@code
   * ${PROJECT_HOME}/work/edw-catalog} resolve the same way as Data Catalog tagging.
   */
  public List<String> listCatalogVersionTags(
      IHopMetadataProvider metadataProvider, ILogChannel log) {
    return listCatalogVersionTags(metadataProvider, log, this);
  }

  public List<String> listCatalogVersionTags(
      IHopMetadataProvider metadataProvider, ILogChannel log, IVariables variables) {
    Set<String> tags = new LinkedHashSet<>();
    if (metadataProvider == null || Utils.isEmpty(resourceDefinitionGroup)) {
      return new ArrayList<>(tags);
    }
    IVariables vars = variables != null ? variables : this;
    try {
      ResourceDefinitionGroupMeta group =
          ResourceDefinitionGroupResolver.loadGroup(resourceDefinitionGroup, metadataProvider);
      String connection = group != null ? group.getDataCatalogConnection() : null;
      if (!Utils.isEmpty(connection)) {
        connection = vars.resolve(connection);
      }
      if (Utils.isEmpty(connection)) {
        connection =
            DvSourceCatalogService.resolvePreferredCatalogConnection(null, vars, metadataProvider);
      }
      if (Utils.isEmpty(connection)) {
        return new ArrayList<>(tags);
      }
      for (CatalogVersionEntry entry :
          CatalogVersionService.listVersions(connection, vars, metadataProvider)) {
        if (entry != null && !Utils.isEmpty(entry.getTag())) {
          tags.add(entry.getTag().trim());
        }
      }
    } catch (Throwable t) {
      if (log != null) {
        log.logDetailed(
            "Unable to list catalog version tags for group '"
                + resourceDefinitionGroup
                + "': "
                + t.getMessage());
      }
    }
    return new ArrayList<>(tags);
  }

  @Override
  public String getDialogClassName() {
    return ActionValidateResourceDefinitionsDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    if (Utils.isEmpty(resourceDefinitionGroup)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Error.MissingGroup"));
    }

    String groupName = resolve(resourceDefinitionGroup);
    String versionTag = resolveOptional(targetCatalogVersion);
    String baselineTag = resolveOptional(baselineCatalogVersion);
    SchemaCompareMode mode = parseCompareMode(compareMode);

    // LIVE_SOURCE: expected contract = target version, or baseline if target is empty.
    // WORKING_VS_VERSION: expected = baseline (or target if baseline empty); actual = working tree.
    if (mode == SchemaCompareMode.LIVE_SOURCE && versionTag == null && baselineTag != null) {
      versionTag = baselineTag;
    }
    if (mode == SchemaCompareMode.WORKING_VS_VERSION && baselineTag == null && versionTag != null) {
      baselineTag = versionTag;
    }

    boolean expectAutoCreate =
        checkTargetDatabases && resolveBooleanOption(expectAutomaticTargetTableCreation);
    if (checkTargetDatabases) {
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionValidateResourceDefinitions.Log.ExpectAutoCreate",
              Const.NVL(expectAutomaticTargetTableCreation, ""),
              Boolean.toString(expectAutoCreate)));
    }

    SchemaImpactSimulationRequest request =
        SchemaImpactSimulationRequest.builder()
            .resourceDefinitionGroup(groupName)
            .catalogVersionTag(versionTag)
            .baselineVersionTag(baselineTag)
            .compareMode(mode)
            .includeImpact(includeImpact)
            .detailedDataTypeChecking(true)
            .checkTargetDatabases(checkTargetDatabases)
            .expectAutomaticTargetTableCreation(expectAutoCreate)
            .build();

    SchemaImpactSimulationResult simulation =
        SchemaImpactSimulationService.run(request, this, getMetadataProvider());
    ValidationReport report = simulation.validationReport();

    String formatted = SchemaValidationReportFormatter.formatLog(simulation);
    if (!Utils.isEmpty(formatted)) {
      logBasic(formatted);
    }

    stashReport(formatted);

    String outputPath = resolveOptional(reportOutputPath);
    if (!Utils.isEmpty(outputPath)) {
      SchemaValidationReportFileWriter.ReportFormat format = parseReportFormat(reportFormat);
      List<String> written =
          SchemaValidationReportFileWriter.write(
              outputPath, reportFileBaseName, simulation, format, this);
      for (String path : written) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionValidateResourceDefinitions.Log.ReportWritten", path));
      }
    }

    SchemaValidationFailureSeverity severity = resolveFailureSeverity();
    boolean failed = severity.shouldFail(report);
    if (failed) {
      // Emit each gate-failing finding as ERROR so Hop GUI highlights them in red among the
      // lengthy Basic log of the full report (which stays logBasic above).
      int logged = logGateFailingFindings(report, severity);
      if (severity == SchemaValidationFailureSeverity.FAIL_ON_WARNINGS) {
        logError(
            BaseMessages.getString(
                PKG,
                "ActionValidateResourceDefinitions.Error.WarningsPresent",
                Integer.toString(
                    Math.max(logged, report != null ? report.getGateRelevantIssueCount() : 0))));
      } else {
        logError(
            BaseMessages.getString(
                PKG,
                "ActionValidateResourceDefinitions.Error.ValidationFailed",
                Integer.toString(Math.max(logged, 1))));
      }
    }

    result.setResult(!failed);
    int gateIssues = report != null ? report.getGateRelevantIssueCount() : 0;
    result.setNrErrors(failed ? Math.max(1, gateIssues) : 0);
    if (failed) {
      result.setLogText(
          BaseMessages.getString(
              PKG,
              "ActionValidateResourceDefinitions.Error.ValidationFailed",
              Integer.toString(gateIssues)));
    }
    return result;
  }

  /**
   * Logs WARNING/BLOCKING findings that fail the configured severity policy with {@link
   * #logError(String)} so they stand out in Hop GUI workflow logs.
   *
   * @return number of findings logged at ERROR
   */
  private int logGateFailingFindings(
      ValidationReport report, SchemaValidationFailureSeverity policy) {
    if (report == null) {
      return 0;
    }
    boolean warningsFailGate = policy == SchemaValidationFailureSeverity.FAIL_ON_WARNINGS;
    int logged = 0;
    for (RecordDefinitionValidation validation : report.getRecordValidations()) {
      if (validation == null || validation.issues() == null) {
        continue;
      }
      String recordKey =
          validation.key() != null
              ? validation.key().getNamespace() + "/" + validation.key().getName()
              : "?";
      for (ValidationIssue issue : validation.issues()) {
        if (issue == null || issue.severity() == null) {
          continue;
        }
        if (issue.severity() == IssueSeverity.BLOCKING
            || (warningsFailGate && issue.severity() == IssueSeverity.WARNING)) {
          logError(formatGateFindingLine(recordKey, issue));
          logged++;
        }
      }
    }
    return logged;
  }

  /** Compact one-liner: {@code BLOCKING / BASELINE_CONTRACT_MISSING ns/name — short finding}. */
  static String formatGateFindingLine(String recordKey, ValidationIssue issue) {
    String severity = issue.severity() != null ? issue.severity().name() : "?";
    String kind = issue.kind() != null ? issue.kind().name() : "?";
    StringBuilder line = new StringBuilder();
    line.append(severity).append(" / ").append(kind);
    if (!Utils.isEmpty(recordKey)) {
      line.append("  ").append(recordKey);
    }
    if (!Utils.isEmpty(issue.fieldName())) {
      line.append("  field=").append(issue.fieldName());
    }
    String finding = ValidationFindingFormatter.shortTitle(issue.message());
    if (!Utils.isEmpty(finding)) {
      line.append(" — ").append(finding);
    }
    return line.toString();
  }

  private SchemaValidationFailureSeverity resolveFailureSeverity() {
    if (failOnWarnings
        && (Utils.isEmpty(failureSeverity)
            || SchemaValidationFailureSeverity.FAIL_ON_BLOCKING
                .name()
                .equalsIgnoreCase(failureSeverity.trim()))) {
      // Legacy workflows: only failOnWarnings=Y was set.
      return SchemaValidationFailureSeverity.FAIL_ON_WARNINGS;
    }
    return SchemaValidationFailureSeverity.parse(failureSeverity);
  }

  private void stashReport(String formatted) {
    if (Utils.isEmpty(formatted)) {
      return;
    }
    getExtensionDataMap().put(RESULT_ATTR_REPORT, formatted);
    if (getParentWorkflow() != null) {
      getParentWorkflow().getExtensionDataMap().put(RESULT_ATTR_REPORT, formatted);
    }
  }

  private String resolveOptional(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    String resolved = resolve(value);
    return Utils.isEmpty(resolved) ? null : resolved.trim();
  }

  /**
   * Resolve a Y/N-style option that may contain Hop variables (including workflow parameters that
   * Hop has already activated as variables). {@link #resolve(String)} then {@link
   * Const#toBoolean(String)}.
   */
  private boolean resolveBooleanOption(String raw) {
    return Const.toBoolean(Const.trim(resolve(Const.NVL(raw, ""))));
  }

  private static SchemaCompareMode parseCompareMode(String raw) {
    if (Utils.isEmpty(raw)) {
      return SchemaCompareMode.LIVE_SOURCE;
    }
    try {
      return SchemaCompareMode.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return SchemaCompareMode.LIVE_SOURCE;
    }
  }

  private static SchemaValidationReportFileWriter.ReportFormat parseReportFormat(String raw) {
    if (Utils.isEmpty(raw)) {
      return SchemaValidationReportFileWriter.ReportFormat.MARKDOWN;
    }
    try {
      return SchemaValidationReportFileWriter.ReportFormat.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return SchemaValidationReportFileWriter.ReportFormat.MARKDOWN;
    }
  }

  @Override
  public IAction clone() {
    return new ActionValidateResourceDefinitions(this);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }
}
