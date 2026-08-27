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
package org.hopper.edw.catalog.hopgui.perspective;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.catalog.discovery.RecordDefinitionPhysicalRefSupport;
import org.hopper.edw.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewRunner;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.hopper.edw.catalog.model.CatalogCustomProperty;
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.hopper.edw.catalog.model.DvCsvFormatRecord;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.PhysicalFileRef;
import org.hopper.edw.catalog.model.PhysicalIcebergTableRef;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorSupport;
import org.hopper.edw.datavault.metadata.CsvFieldOptions;
import org.hopper.edw.datavault.metadata.DvSourceDeliveryType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.SourceFieldInputOptions;

/** Detail view for a selected record definition. */
public class RecordDefinitionDetailsPanel {

  private static final Class<?> PKG = RecordDefinitionDetailsPanel.class;
  private static final String KEY_PREFIX = "RecordDefinitionDetailsPanel.";
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private static final int COL_NAME = 1;
  private static final int COL_TYPE = 2;
  private static final int COL_LENGTH = 3;
  private static final int COL_PRECISION = 4;
  private static final int COL_PRIMARY_KEY_POSITION = 5;
  private static final int COL_FORMAT = 6;
  private static final int COL_DECIMAL = 7;
  private static final int COL_GROUPING = 8;
  private static final int MIN_CSV_FORMAT_COLUMN_WIDTH = 90;

  private final Composite parent;

  /**
   * Fallback variables from construction time. Prefer {@link #activeVariables()} for any DB / path
   * resolution: HopGui replaces its variables instance when a project/environment is enabled.
   */
  private final IVariables variables;

  private final Runnable onUpdate;

  private String catalogConnectionName;
  private RecordDefinition definition;

  private Label wPlaceholder;
  private CTabFolder wTabFolder;
  private ScrolledComposite wScroll;
  private Composite wPropertiesComp;

  private Text wNamespace;
  private Text wName;
  private Text wType;
  private Text wDescription;
  private Text wModelType;
  private Text wModelName;
  private Text wModelFilename;
  private Text wModelElementName;
  private Text wHopProject;
  private Text wCreatedAt;
  private Text wUpdatedAt;
  private Text wLastDiscoveredAt;
  private Text wUpdatedBy;
  private Text wLastWorkflow;
  private Text wLastPipeline;
  private Button wGoToOrigin;
  private Text wDatabaseMetaName;
  private Text wSchemaName;
  private Text wTableName;
  private Text wFileFolder;
  private Text wIncludeFileMask;
  private Text wExcludeFileMask;
  private Button wIncludeSubfolders;
  private Text wIcebergCatalogUri;
  private Text wIcebergWarehouse;
  private Text wIcebergNamespace;
  private Text wIcebergTableName;
  private Text wIcebergSnapshotId;
  private Text wIcebergBranch;
  private Text wIcebergS3Endpoint;
  private Text wIcebergS3AccessKey;
  private Text wIcebergS3SecretKey;
  private Text wCsvDelimiter;
  private Text wCsvEnclosure;
  private Text wCsvEncoding;
  private Button wCsvHeaderPresent;
  private Text wCsvHeaderLines;
  private Text wCsvInputTransform;
  private Text wDvSourceType;
  private Text wDvSourceIndicator;
  private Text wDvSourceIndicatorField;
  private Text wDvSourceGroup;
  private Combo wDvDeliveryType;

  private TableView wFields;
  private Button wPreviewRecords;
  private Button wRefreshFromSource;
  private TableView wTags;
  private TableView wGlossaryTerms;
  private TableView wCustomProperties;
  private TableView wQualityRules;
  private Button wUpdateQualityRules;
  private Button wTestQualityMeasure;
  private Button wQualityHistory;
  private Button wSchemaHarvestHistory;

  private final List<Control> physicalTableSectionControls = new ArrayList<>();
  private final List<Control> physicalFileSectionControls = new ArrayList<>();
  private final List<Control> physicalIcebergSectionControls = new ArrayList<>();
  private final List<Control> csvFormatSectionControls = new ArrayList<>();
  private final List<Control> dvSourceSectionControls = new ArrayList<>();

  public RecordDefinitionDetailsPanel(Composite parent, IVariables variables, Runnable onUpdate) {
    this.parent = parent;
    this.variables = variables;
    this.onUpdate = onUpdate;
    createControls();
    clear();
  }

  /**
   * Current HopGui variable space (project/environment). Must not use the construction-time
   * reference alone: {@code ProjectsGuiPlugin.enableHopGuiProject} calls {@code
   * hopGui.setVariables(...)} with a new instance when the environment loads.
   */
  private IVariables activeVariables() {
    try {
      HopGui hopGui = HopGui.getInstance();
      if (hopGui != null && hopGui.getVariables() != null) {
        return hopGui.getVariables();
      }
    } catch (Exception ignored) {
      // headless / unit-test contexts without a HopGui singleton
    }
    return variables;
  }

  private void createControls() {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    parent.setLayout(new FormLayout());

    wPlaceholder = new Label(parent, SWT.CENTER | SWT.WRAP);
    PropsUi.setLook(wPlaceholder);
    FormData fdPlaceholder = new FormData();
    fdPlaceholder.left = new FormAttachment(0, margin);
    fdPlaceholder.right = new FormAttachment(100, -margin);
    fdPlaceholder.top = new FormAttachment(0, margin * 4);
    wPlaceholder.setLayoutData(fdPlaceholder);

    wTabFolder = new CTabFolder(parent, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.top = new FormAttachment(0, 0);
    fdTabFolder.bottom = new FormAttachment(100, 0);
    wTabFolder.setLayoutData(fdTabFolder);

    Composite wPropertiesTabComp =
        addTab(
            wTabFolder, messageKey("Tab.Properties.Label"), messageKey("Tab.Properties.ToolTip"));
    Composite wFieldsTabComp =
        addTab(wTabFolder, messageKey("Tab.Fields.Label"), messageKey("Tab.Fields.ToolTip"));
    Composite wTagsTabComp =
        addTab(wTabFolder, messageKey("Tab.Tags.Label"), messageKey("Tab.Tags.ToolTip"));
    Composite wGlossaryTabComp =
        addTab(
            wTabFolder,
            messageKey("Tab.GlossaryTerms.Label"),
            messageKey("Tab.GlossaryTerms.ToolTip"));
    Composite wCustomPropertiesTabComp =
        addTab(
            wTabFolder,
            messageKey("Tab.CustomProperties.Label"),
            messageKey("Tab.CustomProperties.ToolTip"));
    Composite wQualityTabComp =
        addTab(wTabFolder, messageKey("Tab.Quality.Label"), messageKey("Tab.Quality.ToolTip"));

    wScroll = new ScrolledComposite(wPropertiesTabComp, SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(wScroll);
    FormData fdScroll = new FormData();
    fdScroll.left = new FormAttachment(0, 0);
    fdScroll.right = new FormAttachment(100, 0);
    fdScroll.top = new FormAttachment(0, 0);
    fdScroll.bottom = new FormAttachment(100, 0);
    wScroll.setLayoutData(fdScroll);

    wPropertiesComp = new Composite(wScroll, SWT.NONE);
    PropsUi.setLook(wPropertiesComp);
    wPropertiesComp.setLayout(new FormLayout());

    Control lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("General.Namespace.Label"), middle, margin, null);
    wNamespace = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("General.Name.Label"), middle, margin, wNamespace);
    wName = (Text) lastControl;

    lastControl =
        addReadOnlyField(wPropertiesComp, messageKey("General.Type.Label"), middle, margin, wName);
    wType = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("General.Description.Label"), middle, margin, wType);
    wDescription = (Text) lastControl;

    lastControl =
        addSectionLabel(wPropertiesComp, messageKey("Origin.Label"), wDescription, margin, null);
    lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("Origin.ModelType.Label"), middle, margin, lastControl);
    wModelType = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("Origin.ModelName.Label"), middle, margin, wModelType);
    wModelName = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("Origin.ModelFilename.Label"), middle, margin, wModelName);
    wModelFilename = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp,
            messageKey("Origin.ModelElementName.Label"),
            middle,
            margin,
            wModelFilename);
    wModelElementName = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp,
            messageKey("Origin.HopProject.Label"),
            middle,
            margin,
            wModelElementName);
    wHopProject = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("Origin.CreatedAt.Label"), middle, margin, wHopProject);
    wCreatedAt = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("Origin.UpdatedAt.Label"), middle, margin, wCreatedAt);
    wUpdatedAt = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp,
            messageKey("Origin.LastDiscoveredAt.Label"),
            middle,
            margin,
            wUpdatedAt);
    wLastDiscoveredAt = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp,
            messageKey("Origin.UpdatedBy.Label"),
            middle,
            margin,
            wLastDiscoveredAt);
    wUpdatedBy = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp, messageKey("Origin.LastWorkflow.Label"), middle, margin, wUpdatedBy);
    wLastWorkflow = (Text) lastControl;

    lastControl =
        addReadOnlyField(
            wPropertiesComp,
            messageKey("Origin.LastPipeline.Label"),
            middle,
            margin,
            wLastWorkflow);
    wLastPipeline = (Text) lastControl;

    wGoToOrigin = new Button(wPropertiesComp, SWT.PUSH);
    wGoToOrigin.setText(BaseMessages.getString(PKG, messageKey("Origin.GoToOrigin.Label")));
    wGoToOrigin.setToolTipText(
        BaseMessages.getString(PKG, messageKey("Origin.GoToOrigin.ToolTip")));
    PropsUi.setLook(wGoToOrigin);
    FormData fdGoToOrigin = new FormData();
    fdGoToOrigin.right = new FormAttachment(100, 0);
    fdGoToOrigin.top = new FormAttachment(wLastPipeline, margin);
    wGoToOrigin.setLayoutData(fdGoToOrigin);
    wGoToOrigin.addListener(SWT.Selection, e -> goToOrigin());
    lastControl = wGoToOrigin;

    lastControl =
        addSectionLabel(
            wPropertiesComp,
            messageKey("PhysicalTable.Label"),
            lastControl,
            margin,
            physicalTableSectionControls);
    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalTable.Database.Label"),
            middle,
            margin,
            lastControl,
            physicalTableSectionControls);
    wDatabaseMetaName = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalTable.Schema.Label"),
            middle,
            margin,
            wDatabaseMetaName,
            physicalTableSectionControls);
    wSchemaName = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalTable.Table.Label"),
            middle,
            margin,
            wSchemaName,
            physicalTableSectionControls);
    wTableName = (Text) lastControl;

    lastControl =
        addSectionUpdateButton(
            wPropertiesComp,
            messageKey("PhysicalTable.UpdateButton.Label"),
            wTableName,
            margin,
            this::updatePhysicalTable,
            physicalTableSectionControls);

    lastControl =
        addSectionLabel(
            wPropertiesComp,
            messageKey("PhysicalFile.Label"),
            lastControl,
            margin,
            physicalFileSectionControls);
    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalFile.Folder.Label"),
            middle,
            margin,
            lastControl,
            physicalFileSectionControls);
    wFileFolder = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalFile.IncludeMask.Label"),
            middle,
            margin,
            wFileFolder,
            physicalFileSectionControls);
    wIncludeFileMask = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalFile.ExcludeMask.Label"),
            middle,
            margin,
            wIncludeFileMask,
            physicalFileSectionControls);
    wExcludeFileMask = (Text) lastControl;

    lastControl =
        addCheckboxField(
            wPropertiesComp,
            messageKey("PhysicalFile.IncludeSubfolders.Label"),
            middle,
            margin,
            wExcludeFileMask,
            physicalFileSectionControls);
    wIncludeSubfolders = (Button) lastControl;

    lastControl =
        addSectionUpdateButton(
            wPropertiesComp,
            messageKey("PhysicalFile.UpdateButton.Label"),
            wIncludeSubfolders,
            margin,
            this::updatePhysicalFile,
            physicalFileSectionControls);

    lastControl =
        addSectionLabel(
            wPropertiesComp,
            messageKey("PhysicalIceberg.Label"),
            lastControl,
            margin,
            physicalIcebergSectionControls);
    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.CatalogUri.Label"),
            middle,
            margin,
            lastControl,
            physicalIcebergSectionControls);
    wIcebergCatalogUri = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.Warehouse.Label"),
            middle,
            margin,
            wIcebergCatalogUri,
            physicalIcebergSectionControls);
    wIcebergWarehouse = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.Namespace.Label"),
            middle,
            margin,
            wIcebergWarehouse,
            physicalIcebergSectionControls);
    wIcebergNamespace = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.TableName.Label"),
            middle,
            margin,
            wIcebergNamespace,
            physicalIcebergSectionControls);
    wIcebergTableName = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.SnapshotId.Label"),
            middle,
            margin,
            wIcebergTableName,
            physicalIcebergSectionControls);
    wIcebergSnapshotId = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.Branch.Label"),
            middle,
            margin,
            wIcebergSnapshotId,
            physicalIcebergSectionControls);
    wIcebergBranch = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.S3Endpoint.Label"),
            middle,
            margin,
            wIcebergBranch,
            physicalIcebergSectionControls);
    wIcebergS3Endpoint = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.S3AccessKey.Label"),
            middle,
            margin,
            wIcebergS3Endpoint,
            physicalIcebergSectionControls);
    wIcebergS3AccessKey = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("PhysicalIceberg.S3SecretKey.Label"),
            middle,
            margin,
            wIcebergS3AccessKey,
            physicalIcebergSectionControls);
    wIcebergS3SecretKey = (Text) lastControl;

    lastControl =
        addSectionUpdateButton(
            wPropertiesComp,
            messageKey("PhysicalIceberg.UpdateButton.Label"),
            wIcebergS3SecretKey,
            margin,
            this::updatePhysicalIceberg,
            physicalIcebergSectionControls);

    lastControl =
        addSectionLabel(
            wPropertiesComp,
            messageKey("CsvFormat.Label"),
            lastControl,
            margin,
            csvFormatSectionControls);
    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("CsvFormat.Delimiter.Label"),
            middle,
            margin,
            lastControl,
            csvFormatSectionControls);
    wCsvDelimiter = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("CsvFormat.Enclosure.Label"),
            middle,
            margin,
            wCsvDelimiter,
            csvFormatSectionControls);
    wCsvEnclosure = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("CsvFormat.Encoding.Label"),
            middle,
            margin,
            wCsvEnclosure,
            csvFormatSectionControls);
    wCsvEncoding = (Text) lastControl;

    lastControl =
        addCheckboxField(
            wPropertiesComp,
            messageKey("CsvFormat.HeaderPresent.Label"),
            middle,
            margin,
            wCsvEncoding,
            csvFormatSectionControls);
    wCsvHeaderPresent = (Button) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("CsvFormat.HeaderLines.Label"),
            middle,
            margin,
            wCsvHeaderPresent,
            csvFormatSectionControls);
    wCsvHeaderLines = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("CsvFormat.InputTransform.Label"),
            middle,
            margin,
            wCsvHeaderLines,
            csvFormatSectionControls);
    wCsvInputTransform = (Text) lastControl;

    lastControl =
        addSectionUpdateButton(
            wPropertiesComp,
            messageKey("CsvFormat.UpdateButton.Label"),
            wCsvInputTransform,
            margin,
            this::updateCsvFormat,
            csvFormatSectionControls);

    lastControl =
        addSectionLabel(
            wPropertiesComp,
            messageKey("DvSource.Label"),
            lastControl,
            margin,
            dvSourceSectionControls);
    lastControl =
        addReadOnlyField(
            wPropertiesComp,
            messageKey("DvSource.SourceType.Label"),
            middle,
            margin,
            lastControl,
            dvSourceSectionControls);
    wDvSourceType = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("DvSource.SourceIndicator.Label"),
            middle,
            margin,
            wDvSourceType,
            dvSourceSectionControls);
    wDvSourceIndicator = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("DvSource.SourceIndicatorField.Label"),
            middle,
            margin,
            wDvSourceIndicator,
            dvSourceSectionControls);
    wDvSourceIndicatorField = (Text) lastControl;

    lastControl =
        addEditableField(
            wPropertiesComp,
            messageKey("DvSource.Group.Label"),
            middle,
            margin,
            wDvSourceIndicatorField,
            dvSourceSectionControls);
    wDvSourceGroup = (Text) lastControl;

    lastControl =
        addComboField(
            wPropertiesComp,
            messageKey("DvSource.DeliveryType.Label"),
            middle,
            margin,
            wDvSourceGroup,
            dvSourceSectionControls);
    wDvDeliveryType = (Combo) lastControl;
    for (String description : DvSourceDeliveryType.getDescriptions()) {
      wDvDeliveryType.add(description);
    }

    lastControl =
        addSectionUpdateButton(
            wPropertiesComp,
            messageKey("DvSource.UpdateButton.Label"),
            wDvDeliveryType,
            margin,
            this::updateDvSource,
            dvSourceSectionControls);

    FormData fdProps = new FormData();
    fdProps.left = new FormAttachment(0, 0);
    fdProps.right = new FormAttachment(100, 0);
    fdProps.top = new FormAttachment(0, 0);
    fdProps.bottom = new FormAttachment(lastControl, margin * 2);
    wPropertiesComp.setLayoutData(fdProps);

    wPropertiesComp.pack();
    wScroll.setContent(wPropertiesComp);
    wScroll.setExpandHorizontal(true);
    wScroll.setExpandVertical(true);
    wScroll.setMinSize(wPropertiesComp.computeSize(SWT.DEFAULT, SWT.DEFAULT));

    wFields = createFieldsTable(wFieldsTabComp);

    wPreviewRecords = new Button(wFieldsTabComp, SWT.PUSH);
    wPreviewRecords.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Fields.PreviewButton.Label"));
    wPreviewRecords.setToolTipText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Fields.PreviewButton.ToolTip"));
    PropsUi.setLook(wPreviewRecords);
    wPreviewRecords.setEnabled(false);

    wRefreshFromSource = new Button(wFieldsTabComp, SWT.PUSH);
    wRefreshFromSource.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Fields.RefreshButton.Label"));
    wRefreshFromSource.setToolTipText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Fields.RefreshButton.ToolTip"));
    PropsUi.setLook(wRefreshFromSource);
    wRefreshFromSource.setEnabled(false);

    Button wUpdateFields = new Button(wFieldsTabComp, SWT.PUSH);
    wUpdateFields.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Fields.UpdateButton.Label"));
    PropsUi.setLook(wUpdateFields);

    FormData fdUpdateFields = new FormData();
    fdUpdateFields.right = new FormAttachment(100, 0);
    fdUpdateFields.bottom = new FormAttachment(100, 0);
    wUpdateFields.setLayoutData(fdUpdateFields);

    FormData fdPreviewRecords = new FormData();
    fdPreviewRecords.right = new FormAttachment(wRefreshFromSource, -margin);
    fdPreviewRecords.bottom = new FormAttachment(100, 0);
    wPreviewRecords.setLayoutData(fdPreviewRecords);

    FormData fdRefreshFromSource = new FormData();
    fdRefreshFromSource.right = new FormAttachment(wUpdateFields, -margin);
    fdRefreshFromSource.bottom = new FormAttachment(100, 0);
    wRefreshFromSource.setLayoutData(fdRefreshFromSource);

    wUpdateFields.addListener(SWT.Selection, e -> updateRecordDefinitionFields());
    wPreviewRecords.addListener(SWT.Selection, e -> previewRecords());
    wRefreshFromSource.addListener(SWT.Selection, e -> refreshFromSource());

    FormData fdFields = (FormData) wFields.getLayoutData();
    fdFields.bottom = new FormAttachment(wUpdateFields, -margin);
    wFields.setLayoutData(fdFields);

    wTags = createListTable(wTagsTabComp, messageKey("Tags.Column"));
    wGlossaryTerms = createListTable(wGlossaryTabComp, messageKey("GlossaryTerms.Column"));
    wCustomProperties = createCustomPropertiesTable(wCustomPropertiesTabComp);
    wQualityRules = createQualityRulesTable(wQualityTabComp);
    wUpdateQualityRules = new Button(wQualityTabComp, SWT.PUSH);
    wUpdateQualityRules.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.UpdateButton.Label"));
    wUpdateQualityRules.setToolTipText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.UpdateButton.ToolTip"));
    PropsUi.setLook(wUpdateQualityRules);
    FormData fdUpdateQuality = new FormData();
    fdUpdateQuality.right = new FormAttachment(100, 0);
    fdUpdateQuality.bottom = new FormAttachment(100, 0);
    wUpdateQualityRules.setLayoutData(fdUpdateQuality);
    wUpdateQualityRules.addListener(SWT.Selection, e -> updateQualityRules());

    wTestQualityMeasure = new Button(wQualityTabComp, SWT.PUSH);
    wTestQualityMeasure.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.TestButton.Label"));
    wTestQualityMeasure.setToolTipText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.TestButton.ToolTip"));
    PropsUi.setLook(wTestQualityMeasure);
    FormData fdTestQuality = new FormData();
    fdTestQuality.right = new FormAttachment(wUpdateQualityRules, -PropsUi.getMargin());
    fdTestQuality.bottom = new FormAttachment(100, 0);
    wTestQualityMeasure.setLayoutData(fdTestQuality);
    wTestQualityMeasure.addListener(SWT.Selection, e -> testQualityMeasure());

    wQualityHistory = new Button(wQualityTabComp, SWT.PUSH);
    wQualityHistory.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.HistoryButton.Label"));
    wQualityHistory.setToolTipText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.HistoryButton.ToolTip"));
    PropsUi.setLook(wQualityHistory);
    FormData fdQualityHistory = new FormData();
    fdQualityHistory.right = new FormAttachment(wTestQualityMeasure, -PropsUi.getMargin());
    fdQualityHistory.bottom = new FormAttachment(100, 0);
    wQualityHistory.setLayoutData(fdQualityHistory);
    wQualityHistory.addListener(SWT.Selection, e -> openQualityHistory());

    wSchemaHarvestHistory = new Button(wQualityTabComp, SWT.PUSH);
    wSchemaHarvestHistory.setText(
        BaseMessages.getString(
            PKG, "RecordDefinitionDetailsPanel.SchemaHarvest.HistoryButton.Label"));
    wSchemaHarvestHistory.setToolTipText(
        BaseMessages.getString(
            PKG, "RecordDefinitionDetailsPanel.SchemaHarvest.HistoryButton.ToolTip"));
    PropsUi.setLook(wSchemaHarvestHistory);
    FormData fdSchemaHarvest = new FormData();
    fdSchemaHarvest.right = new FormAttachment(wQualityHistory, -PropsUi.getMargin());
    fdSchemaHarvest.bottom = new FormAttachment(100, 0);
    wSchemaHarvestHistory.setLayoutData(fdSchemaHarvest);
    wSchemaHarvestHistory.addListener(SWT.Selection, e -> openSchemaHarvestHistory());

    FormData fdQualityRules = (FormData) wQualityRules.getLayoutData();
    fdQualityRules.bottom = new FormAttachment(wUpdateQualityRules, -PropsUi.getMargin());
    wQualityRules.setLayoutData(fdQualityRules);

    wTabFolder.setSelection(0);
    wTabFolder.setVisible(false);
  }

  private Composite addTab(CTabFolder tabFolder, String titleKey, String toolTipKey) {
    CTabItem tabItem = new CTabItem(tabFolder, SWT.NONE);
    tabItem.setFont(GuiResource.getInstance().getFontDefault());
    tabItem.setText(BaseMessages.getString(PKG, titleKey));
    tabItem.setToolTipText(BaseMessages.getString(PKG, toolTipKey));

    Composite composite = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(composite);
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    composite.setLayout(layout);
    tabItem.setControl(composite);
    return composite;
  }

  private Control addSectionLabel(
      Composite composite, String messageKey, Control previous, int margin, List<Control> section) {
    Label label = new Label(composite, SWT.LEFT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, messageKey));
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(previous, margin * 2);
    label.setLayoutData(fd);
    registerSectionControl(section, label);
    return label;
  }

  private Control addEditableField(
      Composite composite,
      String messageKey,
      int middle,
      int margin,
      Control previous,
      List<Control> section) {
    return addTextField(composite, messageKey, middle, margin, previous, false, section);
  }

  private Control addReadOnlyField(
      Composite composite, String messageKey, int middle, int margin, Control previous) {
    return addTextField(composite, messageKey, middle, margin, previous, true, null);
  }

  private Control addReadOnlyField(
      Composite composite,
      String messageKey,
      int middle,
      int margin,
      Control previous,
      List<Control> section) {
    return addTextField(composite, messageKey, middle, margin, previous, true, section);
  }

  private Control addComboField(
      Composite composite,
      String messageKey,
      int middle,
      int margin,
      Control previous,
      List<Control> section) {
    Label label = new Label(composite, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, messageKey));
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    if (previous == null) {
      fdl.top = new FormAttachment(0, margin);
    } else {
      fdl.top = new FormAttachment(previous, margin);
    }
    label.setLayoutData(fdl);

    Combo combo = new Combo(composite, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(combo);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    combo.setLayoutData(fd);
    registerSectionControl(section, label);
    registerSectionControl(section, combo);
    return combo;
  }

  private Control addTextField(
      Composite composite,
      String messageKey,
      int middle,
      int margin,
      Control previous,
      boolean readOnly,
      List<Control> section) {
    Label label = new Label(composite, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, messageKey));
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    if (previous == null) {
      fdl.top = new FormAttachment(0, margin);
    } else {
      fdl.top = new FormAttachment(previous, margin);
    }
    label.setLayoutData(fdl);

    int style = SWT.SINGLE | SWT.LEFT | SWT.BORDER;
    if (readOnly) {
      style |= SWT.READ_ONLY;
    }
    Text text = new Text(composite, style);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    text.setLayoutData(fd);
    registerSectionControl(section, label);
    registerSectionControl(section, text);
    return text;
  }

  private Control addCheckboxField(
      Composite composite,
      String messageKey,
      int middle,
      int margin,
      Control previous,
      List<Control> section) {
    Label label = new Label(composite, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, messageKey));
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top = new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    Button checkbox = new Button(composite, SWT.CHECK);
    PropsUi.setLook(checkbox);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    checkbox.setLayoutData(fd);
    registerSectionControl(section, label);
    registerSectionControl(section, checkbox);
    return checkbox;
  }

  private Control addSectionUpdateButton(
      Composite composite,
      String messageKey,
      Control previous,
      int margin,
      Runnable action,
      List<Control> section) {
    Button button = new Button(composite, SWT.PUSH);
    button.setText(BaseMessages.getString(PKG, messageKey));
    PropsUi.setLook(button);
    FormData fd = new FormData();
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(previous, margin);
    button.setLayoutData(fd);
    button.addListener(SWT.Selection, e -> action.run());
    registerSectionControl(section, button);
    return button;
  }

  private static void registerSectionControl(List<Control> section, Control control) {
    if (section != null && control != null) {
      section.add(control);
    }
  }

  private void setSectionVisible(List<Control> section, boolean visible) {
    if (section == null) {
      return;
    }
    for (Control control : section) {
      control.setVisible(visible);
    }
  }

  private void updatePropertySectionVisibility(RecordDefinition definition, String sourceType) {
    boolean isDvSource =
        definition != null && definition.getType() == RecordDefinitionType.DV_SOURCE;
    boolean isDatabase = "DATABASE".equalsIgnoreCase(sourceType);
    boolean isCsv = "CSV".equalsIgnoreCase(sourceType);
    boolean isParquet = "PARQUET".equalsIgnoreCase(sourceType);
    boolean isIceberg = "ICEBERG".equalsIgnoreCase(sourceType);
    boolean isFileSource = isCsv || isParquet;

    setSectionVisible(dvSourceSectionControls, isDvSource);
    setSectionVisible(physicalTableSectionControls, isDatabase);
    setSectionVisible(physicalFileSectionControls, isFileSource);
    setSectionVisible(physicalIcebergSectionControls, isIceberg);
    setSectionVisible(csvFormatSectionControls, isCsv);
  }

  private TableView createListTable(Composite tabComp, String columnKey) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, columnKey), ColumnInfo.COLUMN_TYPE_TEXT, false),
        };
    return createTableView(tabComp, columns, 1);
  }

  private TableView createFieldsTable(Composite tabComp) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("Fields.Name.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("Fields.Type.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("Fields.Length.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("Fields.Precision.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("Fields.PrimaryKeyPosition.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("Fields.Format.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("Fields.Decimal.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("Fields.Grouping.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    return createTableView(tabComp, columns, 1);
  }

  private TableView createCustomPropertiesTable(Composite tabComp) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("CustomProperties.Name.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("CustomProperties.Type.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, messageKey("CustomProperties.Value.Column")),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    return createTableView(tabComp, columns, 1);
  }

  private TableView createTableView(Composite tabComp, ColumnInfo[] columns, int rows) {
    TableView tableView =
        new TableView(
            activeVariables(),
            tabComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            rows,
            null,
            PropsUi.getInstance());
    PropsUi.setLook(tableView);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    fd.bottom = new FormAttachment(100, 0);
    tableView.setLayoutData(fd);
    return tableView;
  }

  public void clear() {
    this.catalogConnectionName = null;
    this.definition = null;
    wPlaceholder.setText(BaseMessages.getString(PKG, messageKey("Placeholder.SelectRecord")));
    wPlaceholder.setVisible(true);
    wTabFolder.setVisible(false);
    if (wPreviewRecords != null) {
      wPreviewRecords.setEnabled(false);
    }
    if (wRefreshFromSource != null) {
      wRefreshFromSource.setEnabled(false);
    }
    parent.layout(true, true);
  }

  public void showMultipleSelected(int count) {
    this.catalogConnectionName = null;
    this.definition = null;
    wPlaceholder.setText(
        BaseMessages.getString(PKG, messageKey("Placeholder.MultipleRecordsSelected"), count));
    wPlaceholder.setVisible(true);
    wTabFolder.setVisible(false);
    if (wPreviewRecords != null) {
      wPreviewRecords.setEnabled(false);
    }
    if (wRefreshFromSource != null) {
      wRefreshFromSource.setEnabled(false);
    }
    parent.layout(true, true);
  }

  public void setRecordDefinition(String catalogConnectionName, RecordDefinition definition) {
    setRecordDefinition(catalogConnectionName, definition, false, null);
  }

  public void setRecordDefinition(
      String catalogConnectionName,
      RecordDefinition definition,
      boolean readOnlyVersion,
      String versionTag) {
    this.catalogConnectionName = catalogConnectionName;
    this.definition = definition;
    if (definition == null) {
      clear();
      return;
    }

    wPlaceholder.setVisible(false);
    wTabFolder.setVisible(true);

    if (definition.getKey() != null) {
      wNamespace.setText(Const.NVL(definition.getKey().getNamespace(), ""));
      wName.setText(Const.NVL(definition.getKey().getName(), ""));
    } else {
      wNamespace.setText("");
      wName.setText("");
    }

    wType.setText(definition.getType() != null ? definition.getType().name() : "");
    if (readOnlyVersion) {
      String banner =
          BaseMessages.getString(
              PKG, messageKey("ReadOnlyVersion.Banner"), Const.NVL(versionTag, "?"));
      String description = Const.NVL(definition.getDescription(), "");
      wDescription.setText(Utils.isEmpty(description) ? banner : description + "\n\n" + banner);
    } else {
      wDescription.setText(Const.NVL(definition.getDescription(), ""));
    }

    RecordOrigin origin = definition.getOrigin();
    if (origin != null) {
      wModelType.setText(Const.NVL(origin.getModelType(), ""));
      wModelName.setText(Const.NVL(origin.getModelName(), ""));
      wModelFilename.setText(Const.NVL(origin.getModelFilename(), ""));
      wModelElementName.setText(Const.NVL(origin.getModelElementName(), ""));
      wHopProject.setText(Const.NVL(origin.getHopProject(), ""));
      wCreatedAt.setText(formatDate(origin.getCreatedAt()));
      wUpdatedAt.setText(formatDate(origin.getUpdatedAt()));
      wLastDiscoveredAt.setText(formatDate(origin.getLastDiscoveredAt()));
      wUpdatedBy.setText(Const.NVL(origin.getUpdatedBy(), ""));
      wLastWorkflow.setText(Const.NVL(origin.getLastWorkflow(), ""));
      wLastPipeline.setText(Const.NVL(origin.getLastPipeline(), ""));
      wGoToOrigin.setEnabled(
          RecordOriginNavigationSupport.canNavigateToOrigin(origin, activeVariables()));
    } else {
      clearOriginFields();
    }

    PhysicalTableRef physicalTable = definition.getPhysicalTable();
    if (physicalTable != null) {
      wDatabaseMetaName.setText(Const.NVL(physicalTable.getDatabaseMetaName(), ""));
      wSchemaName.setText(Const.NVL(physicalTable.getSchemaName(), ""));
      wTableName.setText(Const.NVL(physicalTable.getTableName(), ""));
    } else {
      wDatabaseMetaName.setText("");
      wSchemaName.setText("");
      wTableName.setText("");
    }

    PhysicalFileRef physicalFile = definition.getPhysicalFile();
    if (physicalFile != null) {
      wFileFolder.setText(Const.NVL(physicalFile.getFolder(), ""));
      wIncludeFileMask.setText(Const.NVL(physicalFile.getIncludeFileMask(), ""));
      wExcludeFileMask.setText(Const.NVL(physicalFile.getExcludeFileMask(), ""));
      wIncludeSubfolders.setSelection(physicalFile.isIncludeSubfolders());
    } else {
      wFileFolder.setText("");
      wIncludeFileMask.setText("");
      wExcludeFileMask.setText("");
      wIncludeSubfolders.setSelection(false);
    }

    PhysicalIcebergTableRef physicalIcebergTable = definition.getPhysicalIcebergTable();
    if (physicalIcebergTable != null) {
      wIcebergCatalogUri.setText(Const.NVL(physicalIcebergTable.getCatalogUri(), ""));
      wIcebergWarehouse.setText(Const.NVL(physicalIcebergTable.getWarehouse(), ""));
      wIcebergNamespace.setText(Const.NVL(physicalIcebergTable.getNamespace(), ""));
      wIcebergTableName.setText(Const.NVL(physicalIcebergTable.getTableName(), ""));
      wIcebergSnapshotId.setText(Const.NVL(physicalIcebergTable.getSnapshotId(), ""));
      wIcebergBranch.setText(Const.NVL(physicalIcebergTable.getBranch(), ""));
      wIcebergS3Endpoint.setText(Const.NVL(physicalIcebergTable.getS3Endpoint(), ""));
      wIcebergS3AccessKey.setText(Const.NVL(physicalIcebergTable.getS3AccessKey(), ""));
      wIcebergS3SecretKey.setText(Const.NVL(physicalIcebergTable.getS3SecretKey(), ""));
    } else {
      wIcebergCatalogUri.setText("");
      wIcebergWarehouse.setText("");
      wIcebergNamespace.setText("");
      wIcebergTableName.setText("");
      wIcebergSnapshotId.setText("");
      wIcebergBranch.setText("");
      wIcebergS3Endpoint.setText("");
      wIcebergS3AccessKey.setText("");
      wIcebergS3SecretKey.setText("");
    }

    DvCsvFormatRecord csvFormat =
        definition.getDvSource() != null ? definition.getDvSource().getCsvFormat() : null;
    if (csvFormat != null) {
      wCsvDelimiter.setText(Const.NVL(csvFormat.getDelimiter(), ""));
      wCsvEnclosure.setText(Const.NVL(csvFormat.getEnclosure(), ""));
      wCsvEncoding.setText(Const.NVL(csvFormat.getEncoding(), ""));
      wCsvHeaderPresent.setSelection(csvFormat.isHeaderPresent());
      wCsvHeaderLines.setText(Integer.toString(csvFormat.getHeaderLines()));
      wCsvInputTransform.setText(Const.NVL(csvFormat.getInputTransform(), ""));
    } else {
      wCsvDelimiter.setText("");
      wCsvEnclosure.setText("");
      wCsvEncoding.setText("");
      wCsvHeaderPresent.setSelection(false);
      wCsvHeaderLines.setText("");
      wCsvInputTransform.setText("");
    }

    populateDvSourceFields(definition);
    String sourceType =
        definition.getDvSource() != null ? definition.getDvSource().getSourceType() : "";
    updatePropertySectionVisibility(definition, sourceType);
    populateFieldsTable(definition);
    populateListTable(wTags, definition.getTags());
    populateListTable(wGlossaryTerms, definition.getGlossaryTerms());
    populateCustomPropertiesTable(definition.getCustomProperties());
    populateQualityRulesTable(definition.getQualityRules());

    if (wPreviewRecords != null) {
      wPreviewRecords.setEnabled(
          !readOnlyVersion && RecordDefinitionPreviewSupport.supportsPreview(definition));
    }
    if (wUpdateQualityRules != null) {
      wUpdateQualityRules.setEnabled(!readOnlyVersion);
    }
    if (wTestQualityMeasure != null) {
      wTestQualityMeasure.setEnabled(!readOnlyVersion);
    }
    if (wQualityHistory != null) {
      wQualityHistory.setEnabled(true);
    }
    if (wSchemaHarvestHistory != null) {
      wSchemaHarvestHistory.setEnabled(true);
    }
    if (wRefreshFromSource != null) {
      wRefreshFromSource.setEnabled(
          !readOnlyVersion
              && RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition));
    }

    wPropertiesComp.pack();
    wScroll.setMinSize(wPropertiesComp.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    parent.layout(true, true);
  }

  private void previewRecords() {
    if (definition == null) {
      return;
    }
    RecordDefinitionPreviewRunner.run(
        parent.getShell(),
        definition,
        activeVariables(),
        HopGui.getInstance().getMetadataProvider());
  }

  private void refreshFromSource() {
    if (definition == null || catalogConnectionName == null) {
      return;
    }
    RecordDefinitionCatalogRefreshGuiSupport.refreshFromSource(
        parent.getShell(),
        definition,
        catalogConnectionName,
        activeVariables(),
        () -> {
          setRecordDefinition(catalogConnectionName, definition);
          if (onUpdate != null) {
            onUpdate.run();
          }
        });
  }

  private void updateRecordDefinitionFields() {
    if (!confirmUpdate(messageKey("UpdateConfirm.Fields.Message"))) {
      return;
    }

    try {
      boolean csvSource = isCsvDvSource(definition);
      List<TableItem> items = wFields.getNonEmptyItems();
      List<SourceField> sourceFields = new ArrayList<>();
      for (TableItem item : items) {
        String name = item.getText(COL_NAME);
        if (Utils.isEmpty(name)) {
          continue;
        }
        String typeDesc = item.getText(COL_TYPE);
        String lengthStr = item.getText(COL_LENGTH);
        String precisionStr = item.getText(COL_PRECISION);
        String primaryKeyPositionStr = item.getText(COL_PRIMARY_KEY_POSITION);

        SourceField sourceField = new SourceField(name);
        sourceField.setSourceDataType(typeDesc);
        sourceField.setLength(lengthStr);
        sourceField.setPrecision(precisionStr);
        // Resolve Hop names and SQL types (int4, DATETIME(6), …); do not force String for SQL
        // labels.
        int type =
            org.hopper.edw.datavault.metadata.DvDataTypeSupport.resolveHopTypeId(typeDesc, null);
        if (type <= 0) {
          type = org.apache.hop.core.row.IValueMeta.TYPE_STRING;
        }
        sourceField.setHopType(type);
        sourceField.setPrimaryKeyPosition(Const.toInt(primaryKeyPositionStr, 0));
        applyCsvOptionsFromTable(sourceField, item, csvSource);
        sourceFields.add(sourceField);
      }

      // Structured fields are authoritative for DV_SOURCE; row meta is derived.
      DvSourceFieldSupport.applyLayoutToDefinition(definition, sourceFields, activeVariables());
      persistDefinition();
    } catch (Exception e) {
      showUpdateError(e);
    }
  }

  private void updateDvSource() {
    if (!confirmUpdate(messageKey("UpdateConfirm.DvSource.Message"))) {
      return;
    }

    try {
      DvSourceRecord dvSource = definition.getDvSource();
      if (dvSource == null) {
        dvSource = new DvSourceRecord();
        definition.setDvSource(dvSource);
      }
      RecordSourceIndicatorSupport.applyToDvSourceRecord(
          dvSource, wDvSourceIndicator.getText(), wDvSourceIndicatorField.getText());
      dvSource.setGroup(wDvSourceGroup.getText().trim());
      dvSource.setDeliveryType(
          RecordSourceIndicatorSupport.parseDeliveryType(wDvDeliveryType.getText()).getCode());
      persistDefinition();
    } catch (Exception e) {
      showUpdateError(e);
    }
  }

  private void updatePhysicalTable() {
    if (!confirmUpdate(messageKey("UpdateConfirm.PhysicalTable.Message"))) {
      return;
    }

    try {
      PhysicalTableRef physicalTable = definition.getPhysicalTable();
      if (physicalTable == null) {
        physicalTable = new PhysicalTableRef();
        definition.setPhysicalTable(physicalTable);
      }
      physicalTable.setDatabaseMetaName(wDatabaseMetaName.getText().trim());
      physicalTable.setSchemaName(wSchemaName.getText().trim());
      physicalTable.setTableName(wTableName.getText().trim());
      persistDefinition();
    } catch (Exception e) {
      showUpdateError(e);
    }
  }

  private void updatePhysicalFile() {
    if (!confirmUpdate(messageKey("UpdateConfirm.PhysicalFile.Message"))) {
      return;
    }

    try {
      PhysicalFileRef physicalFile = definition.getPhysicalFile();
      if (physicalFile == null) {
        physicalFile = new PhysicalFileRef();
        definition.setPhysicalFile(physicalFile);
      }
      physicalFile.setFolder(wFileFolder.getText().trim());
      physicalFile.setIncludeFileMask(wIncludeFileMask.getText().trim());
      physicalFile.setExcludeFileMask(wExcludeFileMask.getText().trim());
      physicalFile.setIncludeSubfolders(wIncludeSubfolders.getSelection());
      persistDefinition();
    } catch (Exception e) {
      showUpdateError(e);
    }
  }

  private void updatePhysicalIceberg() {
    if (!confirmUpdate(messageKey("UpdateConfirm.PhysicalIceberg.Message"))) {
      return;
    }

    try {
      PhysicalIcebergTableRef physicalIcebergTable = definition.getPhysicalIcebergTable();
      if (physicalIcebergTable == null) {
        physicalIcebergTable = new PhysicalIcebergTableRef();
        definition.setPhysicalIcebergTable(physicalIcebergTable);
      }
      physicalIcebergTable.setCatalogUri(wIcebergCatalogUri.getText().trim());
      physicalIcebergTable.setWarehouse(wIcebergWarehouse.getText().trim());
      physicalIcebergTable.setNamespace(wIcebergNamespace.getText().trim());
      physicalIcebergTable.setTableName(wIcebergTableName.getText().trim());
      physicalIcebergTable.setSnapshotId(wIcebergSnapshotId.getText().trim());
      physicalIcebergTable.setBranch(wIcebergBranch.getText().trim());
      physicalIcebergTable.setS3Endpoint(wIcebergS3Endpoint.getText().trim());
      physicalIcebergTable.setS3AccessKey(wIcebergS3AccessKey.getText().trim());
      physicalIcebergTable.setS3SecretKey(wIcebergS3SecretKey.getText().trim());
      persistDefinition();
    } catch (Exception e) {
      showUpdateError(e);
    }
  }

  private void updateCsvFormat() {
    if (!confirmUpdate(messageKey("UpdateConfirm.CsvFormat.Message"))) {
      return;
    }

    try {
      DvSourceRecord dvSource = definition.getDvSource();
      if (dvSource == null) {
        dvSource = new DvSourceRecord();
        definition.setDvSource(dvSource);
      }
      DvCsvFormatRecord csvFormat = dvSource.getCsvFormat();
      if (csvFormat == null) {
        csvFormat = new DvCsvFormatRecord();
        dvSource.setCsvFormat(csvFormat);
      }
      csvFormat.setDelimiter(wCsvDelimiter.getText().trim());
      csvFormat.setEnclosure(wCsvEnclosure.getText().trim());
      csvFormat.setEncoding(wCsvEncoding.getText().trim());
      csvFormat.setHeaderPresent(wCsvHeaderPresent.getSelection());
      csvFormat.setHeaderLines(Const.toInt(wCsvHeaderLines.getText(), 1));
      csvFormat.setInputTransform(wCsvInputTransform.getText().trim());
      persistDefinition();
    } catch (Exception e) {
      showUpdateError(e);
    }
  }

  private boolean confirmUpdate(String messageKey) {
    if (definition == null || catalogConnectionName == null) {
      return false;
    }

    MessageBox confirm = new MessageBox(parent.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
    confirm.setText(BaseMessages.getString(PKG, messageKey("UpdateConfirm.Title")));
    confirm.setMessage(BaseMessages.getString(PKG, messageKey));
    return confirm.open() == SWT.YES;
  }

  private void persistDefinition() throws Exception {
    RecordDefinitionRegistry.getInstance()
        .update(
            catalogConnectionName,
            definition,
            activeVariables(),
            HopGui.getInstance().getMetadataProvider());

    if (onUpdate != null) {
      onUpdate.run();
    }
  }

  private void showUpdateError(Exception e) {
    new ErrorDialog(
        parent.getShell(),
        BaseMessages.getString(PKG, messageKey("UpdateError.Title")),
        BaseMessages.getString(PKG, messageKey("UpdateError.Message")),
        e);
  }

  private void populateDvSourceFields(RecordDefinition definition) {
    DvSourceRecord dvSource =
        definition.getType() == RecordDefinitionType.DV_SOURCE ? definition.getDvSource() : null;
    if (dvSource != null) {
      wDvSourceType.setText(Const.NVL(dvSource.getSourceType(), ""));
      wDvSourceIndicator.setText(Const.NVL(dvSource.getSourceIndicator(), ""));
      wDvSourceIndicatorField.setText(Const.NVL(dvSource.getSourceIndicatorField(), ""));
      wDvSourceGroup.setText(Const.NVL(dvSource.getGroup(), ""));
      wDvDeliveryType.setText(
          RecordSourceIndicatorSupport.deliveryTypeLabel(
              RecordSourceIndicatorSupport.parseDeliveryType(dvSource.getDeliveryType())));
    } else {
      clearDvSourceFields();
    }
  }

  private void clearDvSourceFields() {
    wDvSourceType.setText("");
    wDvSourceIndicator.setText("");
    wDvSourceIndicatorField.setText("");
    wDvSourceGroup.setText("");
    wDvDeliveryType.setText(DvSourceDeliveryType.CHANGES_ONLY.getDescription());
  }

  private void clearOriginFields() {
    wModelType.setText("");
    wModelName.setText("");
    wModelFilename.setText("");
    wModelElementName.setText("");
    wHopProject.setText("");
    wCreatedAt.setText("");
    wUpdatedAt.setText("");
    wLastDiscoveredAt.setText("");
    wUpdatedBy.setText("");
    wLastWorkflow.setText("");
    wLastPipeline.setText("");
    wGoToOrigin.setEnabled(false);
  }

  private void goToOrigin() {
    if (definition == null || definition.getOrigin() == null) {
      return;
    }
    try {
      RecordOriginNavigationSupport.navigateToOrigin(
          HopGui.getInstance(), definition.getOrigin(), activeVariables());
    } catch (Exception e) {
      new ErrorDialog(
          parent.getShell(),
          BaseMessages.getString(PKG, messageKey("Origin.GoToOrigin.Error.Title")),
          BaseMessages.getString(PKG, messageKey("Origin.GoToOrigin.Error.Message")),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private static String formatDate(Date date) {
    if (date == null) {
      return "";
    }
    synchronized (DATE_FORMAT) {
      return DATE_FORMAT.format(date);
    }
  }

  private void populateFieldsTable(RecordDefinition definition) {
    boolean csvSource = isCsvDvSource(definition);
    Map<String, CatalogSourceField> catalogFieldsByName = catalogFieldsByName(definition);

    List<SourceField> layoutFields = List.of();
    try {
      layoutFields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    } catch (Exception e) {
      // Fall back to empty grid; do not block opening the panel.
    }

    wFields.clearAll(false);
    for (int i = 0; i < layoutFields.size(); i++) {
      SourceField field = layoutFields.get(i);
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      TableItem item;
      if (i == 0) {
        item = wFields.getTable().getItem(0);
      } else {
        item = new TableItem(wFields.getTable(), SWT.NONE);
      }
      String name = field.getName();
      item.setText(COL_NAME, name);
      String typeLabel = Const.NVL(field.getSourceDataType(), "");
      if (Utils.isEmpty(typeLabel) || "-".equals(typeLabel)) {
        try {
          typeLabel =
              field.getHopType() > 0
                  ? ValueMetaFactory.getValueMetaName(field.getHopType())
                  : "String";
        } catch (Exception ignored) {
          typeLabel = "String";
        }
      }
      item.setText(COL_TYPE, Const.NVL(typeLabel, "String"));
      item.setText(COL_LENGTH, Const.NVL(field.getLength(), ""));
      item.setText(COL_PRECISION, Const.NVL(field.getPrecision(), ""));
      CatalogSourceField catalogField = catalogFieldsByName.get(name);
      item.setText(COL_PRIMARY_KEY_POSITION, formatPrimaryKeyPosition(catalogField));
      if (csvSource) {
        item.setText(COL_FORMAT, csvFormat(catalogField));
        item.setText(COL_DECIMAL, csvDecimalSymbol(catalogField));
        item.setText(COL_GROUPING, csvGroupingSymbol(catalogField));
      } else {
        item.setText(COL_FORMAT, "");
        item.setText(COL_DECIMAL, "");
        item.setText(COL_GROUPING, "");
      }
    }
    wFields.optimizeTableView();
    if (csvSource) {
      ensureCsvFormatColumnsVisible();
    }
  }

  private void ensureCsvFormatColumnsVisible() {
    if (wFields == null || wFields.table == null) {
      return;
    }
    for (int column : new int[] {COL_FORMAT, COL_DECIMAL, COL_GROUPING}) {
      int tableColumnIndex = tableColumnIndex(column);
      if (tableColumnIndex >= 0
          && wFields.table.getColumn(tableColumnIndex).getWidth() < MIN_CSV_FORMAT_COLUMN_WIDTH) {
        wFields.table.getColumn(tableColumnIndex).setWidth(MIN_CSV_FORMAT_COLUMN_WIDTH);
      }
    }
  }

  private int tableColumnIndex(int columnNumber) {
    return wFields != null && wFields.hasIndexColumn() ? columnNumber : columnNumber - 1;
  }

  private static boolean isCsvDvSource(RecordDefinition definition) {
    return definition != null
        && definition.getType() == RecordDefinitionType.DV_SOURCE
        && definition.getDvSource() != null
        && "CSV".equalsIgnoreCase(definition.getDvSource().getSourceType());
  }

  private static Map<String, CatalogSourceField> catalogFieldsByName(RecordDefinition definition) {
    Map<String, CatalogSourceField> fieldsByName = new HashMap<>();
    if (definition == null || definition.getDvSource() == null) {
      return fieldsByName;
    }
    List<CatalogSourceField> fields = definition.getDvSource().getFields();
    if (fields == null) {
      return fieldsByName;
    }
    for (CatalogSourceField field : fields) {
      if (field != null && !Utils.isEmpty(field.getName())) {
        fieldsByName.put(field.getName(), field);
      }
    }
    return fieldsByName;
  }

  private static void applyCsvOptionsFromTable(
      SourceField field, TableItem item, boolean csvSource) {
    if (!csvSource) {
      field.setInputOptions(null);
      return;
    }
    String format = item.getText(COL_FORMAT);
    String decimalSymbol = item.getText(COL_DECIMAL);
    String groupingSymbol = item.getText(COL_GROUPING);
    if (Utils.isEmpty(format) && Utils.isEmpty(decimalSymbol) && Utils.isEmpty(groupingSymbol)) {
      field.setInputOptions(null);
      return;
    }
    CsvFieldOptions csv = new CsvFieldOptions();
    csv.setFormat(format);
    csv.setDecimalSymbol(decimalSymbol);
    csv.setGroupingSymbol(groupingSymbol);
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setCsv(csv);
    field.setInputOptions(inputOptions);
  }

  private static String csvFormat(CatalogSourceField field) {
    if (field == null
        || field.getInputOptions() == null
        || field.getInputOptions().getCsv() == null) {
      return "";
    }
    return Const.NVL(field.getInputOptions().getCsv().getFormat(), "");
  }

  private static String csvDecimalSymbol(CatalogSourceField field) {
    if (field == null
        || field.getInputOptions() == null
        || field.getInputOptions().getCsv() == null) {
      return "";
    }
    return Const.NVL(field.getInputOptions().getCsv().getDecimalSymbol(), "");
  }

  private static String csvGroupingSymbol(CatalogSourceField field) {
    if (field == null
        || field.getInputOptions() == null
        || field.getInputOptions().getCsv() == null) {
      return "";
    }
    return Const.NVL(field.getInputOptions().getCsv().getGroupingSymbol(), "");
  }

  private static String formatDimension(int value) {
    return value >= 0 ? Integer.toString(value) : "";
  }

  private static String formatPrimaryKeyPosition(CatalogSourceField field) {
    if (field == null || field.getPrimaryKeyPosition() <= 0) {
      return "";
    }
    return Integer.toString(field.getPrimaryKeyPosition());
  }

  private static String messageKey(String suffix) {
    return KEY_PREFIX + suffix;
  }

  private void populateListTable(TableView tableView, List<String> values) {
    tableView.clearAll(false);
    List<String> items = values != null ? values : List.of();
    for (int i = 0; i < items.size(); i++) {
      TableItem item;
      if (i == 0) {
        item = tableView.getTable().getItem(0);
      } else {
        item = new TableItem(tableView.getTable(), SWT.NONE);
      }
      item.setText(1, Const.NVL(items.get(i), ""));
    }
    tableView.optimizeTableView();
  }

  private void populateCustomPropertiesTable(Map<String, CatalogCustomProperty> customProperties) {
    wCustomProperties.clearAll(false);
    List<Map.Entry<String, CatalogCustomProperty>> entries = new ArrayList<>();
    if (customProperties != null) {
      entries.addAll(customProperties.entrySet());
      entries.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
    }

    for (int i = 0; i < entries.size(); i++) {
      Map.Entry<String, CatalogCustomProperty> entry = entries.get(i);
      CatalogCustomProperty property = entry.getValue();
      TableItem item;
      if (i == 0) {
        item = wCustomProperties.getTable().getItem(0);
      } else {
        item = new TableItem(wCustomProperties.getTable(), SWT.NONE);
      }
      item.setText(1, Const.NVL(entry.getKey(), ""));
      item.setText(
          2, property != null && property.getType() != null ? property.getType().name() : "");
      item.setText(3, property != null ? Const.NVL(property.getValue(), "") : "");
    }
    wCustomProperties.optimizeTableView();
  }

  private TableView createQualityRulesTable(Composite tabComp) {
    String[] severityValues =
        new String[] {
          "",
          org.hopper.edw.quality.model.QualitySeverity.BLOCKING.name(),
          org.hopper.edw.quality.model.QualitySeverity.WARNING.name(),
          org.hopper.edw.quality.model.QualitySeverity.INFO.name()
        };
    ColumnInfo colRuleSet =
        new ColumnInfo(
            BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.RuleSet.Column"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            listDataQualityRuleSetNames(),
            false);
    ColumnInfo colRuleId =
        new ColumnInfo(
            BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.RuleId.Column"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    // When the Rule id combo is opened, load rule ids from the rule set on this row (column 1).
    colRuleId.setComboValuesSelectionListener(
        (tableItem, rowNr, colNr) -> listRuleIdsForRuleSet(tableItem.getText(1)));

    ColumnInfo[] columns =
        new ColumnInfo[] {
          colRuleSet,
          colRuleId,
          new ColumnInfo(
              BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.Field.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.Severity.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              severityValues,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.Enabled.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"Y", "N"},
              false)
        };
    TableView table =
        new TableView(
            activeVariables(),
            tabComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL,
            columns,
            1,
            false,
            null,
            PropsUi.getInstance());
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    fd.bottom = new FormAttachment(100, 0);
    table.setLayoutData(fd);
    return table;
  }

  private static String[] listDataQualityRuleSetNames() {
    try {
      List<String> names =
          HopGui.getInstance()
              .getMetadataProvider()
              .getSerializer(org.hopper.edw.quality.metadata.DataQualityRuleSetMeta.class)
              .listObjectNames();
      if (names == null || names.isEmpty()) {
        return new String[] {""};
      }
      List<String> withBlank = new ArrayList<>();
      withBlank.add("");
      withBlank.addAll(names);
      return withBlank.toArray(new String[0]);
    } catch (Exception e) {
      return new String[] {""};
    }
  }

  /**
   * Rule id choices for the quality bindings grid. Leading empty entry means "apply every rule in
   * the set" (matches {@code updateQualityRules} / {@link
   * org.hopper.edw.quality.resolve.QualityRuleResolver}).
   */
  private static String[] listRuleIdsForRuleSet(String ruleSetName) {
    if (Utils.isEmpty(ruleSetName)) {
      return new String[] {""};
    }
    try {
      org.hopper.edw.quality.metadata.DataQualityRuleSetMeta ruleSet =
          HopGui.getInstance()
              .getMetadataProvider()
              .getSerializer(org.hopper.edw.quality.metadata.DataQualityRuleSetMeta.class)
              .load(ruleSetName.trim());
      if (ruleSet == null || ruleSet.getRules() == null || ruleSet.getRules().isEmpty()) {
        return new String[] {""};
      }
      List<String> ids = new ArrayList<>();
      ids.add("");
      for (org.hopper.edw.quality.model.DataQualityRule rule : ruleSet.getRules()) {
        if (rule == null) {
          continue;
        }
        String id = Const.NVL(rule.getId(), "").trim();
        if (!Utils.isEmpty(id) && !ids.contains(id)) {
          ids.add(id);
        }
      }
      return ids.toArray(new String[0]);
    } catch (Exception e) {
      return new String[] {""};
    }
  }

  private void populateQualityRulesTable(
      List<org.hopper.edw.quality.model.RecordQualityRuleBinding> bindings) {
    if (wQualityRules == null) {
      return;
    }
    // Refresh rule-set combo values when metadata changes.
    if (wQualityRules.getColumns() != null && wQualityRules.getColumns().length > 0) {
      wQualityRules.getColumns()[0].setComboValues(listDataQualityRuleSetNames());
    }
    wQualityRules.clearAll(false);
    List<org.hopper.edw.quality.model.RecordQualityRuleBinding> items =
        bindings != null ? bindings : List.of();
    int row = 0;
    for (org.hopper.edw.quality.model.RecordQualityRuleBinding binding : items) {
      if (binding == null) {
        continue;
      }
      // Library bindings only in the editable grid; inline rules are preserved on Update.
      if (binding.getInlineRule() != null) {
        continue;
      }
      TableItem item;
      if (row == 0 && wQualityRules.getTable().getItemCount() > 0) {
        item = wQualityRules.getTable().getItem(0);
      } else {
        item = new TableItem(wQualityRules.getTable(), SWT.NONE);
      }
      item.setText(1, Const.NVL(binding.getRuleSetName(), ""));
      item.setText(2, Const.NVL(binding.getRuleId(), ""));
      item.setText(3, Const.NVL(binding.getFieldNameOverride(), ""));
      item.setText(
          4, binding.getSeverityOverride() != null ? binding.getSeverityOverride().name() : "");
      item.setText(5, binding.isEnabled() ? "Y" : "N");
      row++;
    }
    wQualityRules.optimizeTableView();
  }

  private void updateQualityRules() {
    if (!confirmUpdate(messageKey("Quality.UpdateConfirm.Message"))) {
      return;
    }
    try {
      List<org.hopper.edw.quality.model.RecordQualityRuleBinding> next = new ArrayList<>();
      // Preserve existing inline rules (library grid does not author them yet).
      if (definition.getQualityRules() != null) {
        for (org.hopper.edw.quality.model.RecordQualityRuleBinding existing :
            definition.getQualityRules()) {
          if (existing != null && existing.getInlineRule() != null) {
            next.add(existing);
          }
        }
      }
      for (TableItem item : wQualityRules.getNonEmptyItems()) {
        String ruleSet = item.getText(1).trim();
        String ruleId = item.getText(2).trim();
        String field = item.getText(3).trim();
        String severityRaw = item.getText(4).trim();
        String enabledRaw = item.getText(5).trim();
        if (Utils.isEmpty(ruleSet) && Utils.isEmpty(ruleId) && Utils.isEmpty(field)) {
          continue;
        }
        if (Utils.isEmpty(ruleSet)) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "RecordDefinitionDetailsPanel.Quality.Error.RuleSetRequired"));
        }
        org.hopper.edw.quality.model.RecordQualityRuleBinding binding =
            new org.hopper.edw.quality.model.RecordQualityRuleBinding();
        binding.setRuleSetName(ruleSet);
        binding.setRuleId(ruleId); // empty = apply entire rule set
        binding.setFieldNameOverride(field);
        if (!Utils.isEmpty(severityRaw)) {
          binding.setSeverityOverride(
              org.hopper.edw.quality.model.QualitySeverity.valueOf(severityRaw));
        }
        binding.setEnabled(!"N".equalsIgnoreCase(enabledRaw));
        next.add(binding);
      }
      definition.setQualityRules(next);
      persistDefinition();
      populateQualityRulesTable(definition.getQualityRules());
      if (wTestQualityMeasure != null) {
        wTestQualityMeasure.setEnabled(
            definition.getQualityRules() != null && !definition.getQualityRules().isEmpty());
      }
    } catch (Exception e) {
      showUpdateError(e);
    }
  }

  private void testQualityMeasure() {
    if (definition == null) {
      return;
    }
    try {
      org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider =
          org.apache.hop.ui.hopgui.HopGui.getInstance().getMetadataProvider();
      org.hopper.edw.quality.model.DataQualityReport report =
          org.hopper.edw.quality.service.DataQualityMeasureService.measureDefinitions(
              List.of(definition),
              org.hopper.edw.quality.model.QualityEvaluationMode.AUTO,
              org.hopper.edw.quality.model.QualityLifecycle.AD_HOC,
              1000,
              activeVariables(),
              metadataProvider,
              org.apache.hop.core.logging.LogChannel.GENERAL);
      String text = org.hopper.edw.quality.service.DataQualityReportFormatter.format(report);
      org.apache.hop.ui.core.dialog.EnterTextDialog dialog =
          new org.apache.hop.ui.core.dialog.EnterTextDialog(
              parent.getShell(),
              BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.Test.Title"),
              BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.Test.Message"),
              text,
              true);
      dialog.setReadOnly();
      dialog.open();
    } catch (Exception e) {
      new org.apache.hop.ui.core.dialog.ErrorDialog(
          parent.getShell(),
          BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.Test.Error.Title"),
          BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.Test.Error.Message"),
          e);
    }
  }

  private void openSchemaHarvestHistory() {
    if (definition == null) {
      return;
    }
    SchemaHarvestHistoryGuiSupport.openForSubject(
        parent.getShell(),
        catalogConnectionName,
        definition,
        activeVariables(),
        HopGui.getInstance().getMetadataProvider());
  }

  private void openQualityHistory() {
    if (definition == null) {
      return;
    }
    try {
      org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider =
          HopGui.getInstance().getMetadataProvider();
      org.hopper.edw.quality.history.DataQualityHistoryReader.HistoryConnection connection =
          org.hopper.edw.quality.history.DataQualityHistoryReader.resolveConnection(
              catalogConnectionName, activeVariables(), metadataProvider);
      if (connection == null) {
        MessageBox box = new MessageBox(parent.getShell(), SWT.OK | SWT.ICON_INFORMATION);
        box.setText(
            BaseMessages.getString(
                PKG, "RecordDefinitionDetailsPanel.Quality.History.Error.Title"));
        box.setMessage(org.hopper.edw.quality.history.DataQualityHistoryReader.MSG_NOT_CONFIGURED);
        box.open();
        return;
      }

      org.apache.hop.core.database.DatabaseMeta databaseMeta =
          org.hopper.edw.quality.history.DataQualityHistoryReader.loadDatabaseMeta(
              connection.databaseMetaName(), metadataProvider);
      if (databaseMeta == null) {
        new ErrorDialog(
            parent.getShell(),
            BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Error.Title"),
            BaseMessages.getString(
                PKG,
                "RecordDefinitionDetailsPanel.Quality.History.Error.ConnectionMissing",
                connection.databaseMetaName()),
            new HopException(
                "Quality history database connection not found: " + connection.databaseMetaName()));
        return;
      }

      String subjectKey =
          org.hopper.edw.catalog.quality.CatalogQualitySubjectSupport.subjectKey(definition);
      List<org.hopper.edw.quality.history.DataQualityHistoryReader.SubjectHistoryEntry> entries =
          org.hopper.edw.quality.history.DataQualityHistoryReader.listSubjectHistory(
              databaseMeta,
              connection.schemaName(),
              subjectKey,
              activeVariables(),
              org.hopper.edw.quality.history.DataQualityHistoryReader.DEFAULT_HISTORY_LIMIT);
      if (entries.isEmpty()) {
        MessageBox box = new MessageBox(parent.getShell(), SWT.OK | SWT.ICON_INFORMATION);
        box.setText(
            BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Title"));
        box.setMessage(org.hopper.edw.quality.history.DataQualityHistoryReader.MSG_NO_HISTORY);
        box.open();
        return;
      }

      new QualityHistoryBrowserDialog(
              parent.getShell(),
              activeVariables(),
              databaseMeta,
              connection.schemaName(),
              subjectKey,
              entries)
          .open();
    } catch (
        org.hopper.edw.quality.history.DataQualityHistoryReader.QualityHistoryTablesMissingException
            e) {
      MessageBox box = new MessageBox(parent.getShell(), SWT.OK | SWT.ICON_WARNING);
      box.setText(
          BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Error.Title"));
      box.setMessage(org.hopper.edw.quality.history.DataQualityHistoryReader.MSG_TABLES_MISSING);
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          parent.getShell(),
          BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Error.Title"),
          BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Error.Message"),
          e);
    }
  }
}
