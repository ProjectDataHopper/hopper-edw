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
package org.apache.hop.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.hopgui.lineage.LineageTabSupport;
import org.apache.hop.datavault.lineage.DmModelLineageCollector;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmAccumulatingSnapshotFact;
import org.apache.hop.datavault.metadata.dimensional.DmAggregateFact;
import org.apache.hop.datavault.metadata.dimensional.DmBridge;
import org.apache.hop.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAlias;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAttribute;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionLoadStrategySupport;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactMeasure;
import org.apache.hop.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactlessFact;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimensionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmJunkHashCodeStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmJunkSurrogateKeyStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmLayoutSupport;
import org.apache.hop.datavault.metadata.dimensional.DmModelLoadSupport;
import org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField;
import org.apache.hop.datavault.metadata.dimensional.DmPeriodicSnapshotFact;
import org.apache.hop.datavault.metadata.dimensional.DmRangeBand;
import org.apache.hop.datavault.metadata.dimensional.DmRangeDimension;
import org.apache.hop.datavault.metadata.dimensional.DmRangeDimensionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.apache.hop.datavault.metadata.dimensional.DmSourceFieldResolutionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmSourceType;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.IDmFactLikeTable;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.SQLStyledTextComp;
import org.apache.hop.ui.core.widget.StyledTextComp;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextComposite;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit a dimension or fact table on the dimensional model canvas. */
public class HopGuiDmTableDialog {
  private static final Class<?> PKG = HopGuiDmTableDialog.class;
  private static final String SKIP_LOOKUP_AUTO = "Auto";
  private static final String SKIP_LOOKUP_YES = "Y";
  private static final String SKIP_LOOKUP_NO = "N";

  private final Shell parent;
  private final IDmTable input;
  private final DimensionalModel model;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final int originalTableIndex;
  private Shell shell;

  private CTabFolder wTabFolder;
  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private Combo wSourceType;
  private MetaSelectionLine<DatabaseMeta> wSourceConnection;
  private Label wlSourceSql;
  private TextComposite wSourceSql;
  private Button wSourcePreview;
  private Label wlSourcePipelineFile;
  private Text wSourcePipelineFile;
  private Button wBrowseSourcePipeline;
  private Button wOpenSourcePipeline;
  private Label wlSourcePipelineTransform;
  private Combo wSourcePipelineTransform;
  private MetaSelectionLine<PipelineRunConfiguration> wSourcePipelineRunConfiguration;
  private Button wSourcePipelinePreviewData;
  private Button wSourcePipelinePreview;
  private MetaSelectionLine<DataCatalogMeta> wSourceCatalogConnection;
  private Label wlSourceRecordNamespace;
  private Text wSourceRecordNamespace;
  private Label wlSourceRecordName;
  private Text wSourceRecordName;
  private Button wSelectSourceRecord;
  private Button wSourceRecordPreviewData;
  private Button wSourceRecordPreviewFields;
  private DmSourceDateGeneratorGuiSupport.Widgets dateGeneratorWidgets;
  private Text wDerivedLoadStrategy;
  private Combo wSurrogateKeyStrategy;
  private Text wSurrogateKeyField;
  private Text wSurrogateKeySourceField;
  private TableView wNaturalKeys;
  private ColumnInfo naturalKeyFieldColumn;
  private TableView wAttributes;
  private ColumnInfo attributeSourceFieldColumn;
  private ColumnInfo attributeTargetFieldColumn;
  private TableView wOutriggers;
  private TableView wDimensionRoles;
  private Combo wDimensionLookupDateField;
  private ColumnInfo dimensionJoinSourceFieldColumn;
  private TableView wMeasures;
  private ColumnInfo measureFieldColumn;
  private TableView wDegenerateDimensions;
  private ColumnInfo degenerateDimensionFieldColumn;
  private TableView wRangeBands;
  private Text wFallBackLabel;
  private TableView wRangeDimensionRoles;
  private ColumnInfo rangeDimensionSourceFieldColumn;
  private TableView wJunkDimensionRoles;
  private Label wlSourceFactTable;
  private Combo wSourceFactTable;
  private Combo wHashCodeStrategy;
  private Text wHashCodeField;
  private Button wUseSurrogateKeyAsHashCodeField;
  private Text wReferencedModelFilename;
  private Button wValidateReferencedModel;
  private Combo wReferencedDimension;

  private boolean ok;
  private final boolean dimension;
  private final boolean dimensionAlias;
  private final boolean junk;
  private final boolean range;
  private final boolean bridge;
  private final boolean factLike;
  private final boolean factless;

  private int margin;
  private int middle;

  public HopGuiDmTableDialog(
      Shell parent,
      IDmTable table,
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.input = table;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.originalTableIndex = model != null ? model.getTables().indexOf(table) : -1;
    DmTableType tableType = table.getTableType();
    this.dimension = tableType == DmTableType.DIMENSION;
    this.dimensionAlias = tableType == DmTableType.DIMENSION_ALIAS;
    this.junk = tableType == DmTableType.JUNK_DIMENSION;
    this.range = tableType == DmTableType.RANGE_DIMENSION || table instanceof DmRangeDimension;
    this.bridge = tableType == DmTableType.BRIDGE;
    this.factLike = table instanceof IDmFactLikeTable;
    this.factless = tableType == DmTableType.FACTLESS_FACT;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            "HopGuiDmTableDialog.Title",
            Const.NVL(input.getName(), input.getTableType().name())));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    margin = PropsUi.getMargin();
    middle = PropsUi.getInstance().getMiddlePct();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(
            ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> validate());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.DM_TABLE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wOk, -2 * margin).result());

    addGeneralTab();
    if (!dimensionAlias && !range) {
      addSourceTab();
    }
    if (dimensionAlias) {
      addDimensionAliasTab();
    } else if (range) {
      addRangeBandsTab();
    } else if (junk) {
      addJunkKeyFieldsTab();
    } else if (bridge) {
      addBridgeDimensionsTab();
    } else if (dimension) {
      addNaturalKeysTab();
      addAttributesTab();
      addOutriggersTab();
    } else if (factLike) {
      addDimensionRolesTab();
      if (!factless) {
        addMeasuresTab();
      }
      addDegenerateDimensionsTab();
      addRangeDimensionRolesTab();
      addJunkDimensionRolesTab();
    }
    addLineageTab();

    wTabFolder.setSelection(0);
    shell.layout(true, true);

    getData();

    BaseTransformDialog.setSize(
        shell,
        620,
        dimensionAlias
            ? 360
            : range ? 520 : dimension ? 620 : junk ? 480 : bridge ? 520 : factless ? 560 : 620);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void addLineageTab() {
    LineageTabSupport.addLazyTab(
        wTabFolder,
        variables,
        margin,
        () -> {
          if (model == null) {
            return null;
          }
          return LineageTabSupport.findTable(
              DmModelLineageCollector.collect(model, variables, metadataProvider), input.getName());
        });
  }

  private void addGeneralTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.General.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.General.ToolTip"));

    Label wlName = new Label(comp, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlTableName = new Label(comp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    wlTableName.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());

    wTableName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    wTableName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    Label wlDescription = new Label(comp, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wTableName, margin).right(middle, -margin).result());

    wDescription = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wTableName, margin).right().result());

    if (dimensionAlias) {
      wTableName.setEditable(false);
    }
    if (range) {
      wlTableName.setVisible(false);
      wTableName.setVisible(false);
      wlDescription.setLayoutData(
          new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());
      wDescription.setLayoutData(
          new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());
    }

    if (dimension) {
      Label wlDerivedLoadStrategy = new Label(comp, SWT.RIGHT);
      wlDerivedLoadStrategy.setText(
          BaseMessages.getString(PKG, "HopGuiDmTableDialog.DerivedLoadStrategy.Label"));
      wlDerivedLoadStrategy.setToolTipText(
          BaseMessages.getString(PKG, "HopGuiDmTableDialog.DerivedLoadStrategy.ToolTip"));
      PropsUi.setLook(wlDerivedLoadStrategy);
      wlDerivedLoadStrategy.setLayoutData(
          new FormDataBuilder().left().top(wDescription, margin).right(middle, -margin).result());

      wDerivedLoadStrategy = new Text(comp, SWT.READ_ONLY | SWT.BORDER);
      PropsUi.setLook(wDerivedLoadStrategy);
      wDerivedLoadStrategy.setLayoutData(
          new FormDataBuilder().left(middle, 0).top(wDescription, margin).right().result());
      addSurrogateKeyControls(comp, wDerivedLoadStrategy);
    } else if (junk) {
      addJunkSurrogateKeyControls(comp, wDescription);
    }
  }

  private void addSurrogateKeyControls(Composite comp, org.eclipse.swt.widgets.Control topControl) {
    Label wlSurrogateKeyStrategy = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyStrategy.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyStrategy.Label"));
    wlSurrogateKeyStrategy.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyStrategy.ToolTip"));
    PropsUi.setLook(wlSurrogateKeyStrategy);
    wlSurrogateKeyStrategy.setLayoutData(
        new FormDataBuilder().left().top(topControl, margin).right(middle, -margin).result());

    wSurrogateKeyStrategy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyStrategy);
    EnumDialogSupport.populateCombo(wSurrogateKeyStrategy, DmSurrogateKeyStrategy.class);
    wSurrogateKeyStrategy.addListener(SWT.Selection, e -> refreshSurrogateKeyFieldState());
    wSurrogateKeyStrategy.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(topControl, margin).right().result());

    Label wlSurrogateKeyField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyField.Label"));
    PropsUi.setLook(wlSurrogateKeyField);
    wlSurrogateKeyField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeyStrategy, margin)
            .right(middle, -margin)
            .result());

    wSurrogateKeyField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyField);
    wSurrogateKeyField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSurrogateKeyStrategy, margin).right().result());

    Label wlSurrogateKeySourceField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeySourceField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeySourceField.Label"));
    wlSurrogateKeySourceField.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeySourceField.ToolTip"));
    PropsUi.setLook(wlSurrogateKeySourceField);
    wlSurrogateKeySourceField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeyField, margin)
            .right(middle, -margin)
            .result());

    wSurrogateKeySourceField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeySourceField);
    wSurrogateKeySourceField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSurrogateKeyField, margin).right().result());
    refreshSurrogateKeyFieldState();
  }

  private void addJunkSurrogateKeyControls(
      Composite comp, org.eclipse.swt.widgets.Control topControl) {
    Label wlSurrogateKeyStrategy = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyStrategy.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyStrategy.Label"));
    PropsUi.setLook(wlSurrogateKeyStrategy);
    wlSurrogateKeyStrategy.setLayoutData(
        new FormDataBuilder().left().top(topControl, margin).right(middle, -margin).result());

    wSurrogateKeyStrategy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyStrategy);
    EnumDialogSupport.populateCombo(wSurrogateKeyStrategy, DmJunkSurrogateKeyStrategy.class);
    wSurrogateKeyStrategy.addListener(SWT.Selection, e -> refreshSurrogateKeyFieldState());
    wSurrogateKeyStrategy.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(topControl, margin).right().result());

    Label wlSurrogateKeyField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyField.Label"));
    PropsUi.setLook(wlSurrogateKeyField);
    wlSurrogateKeyField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeyStrategy, margin)
            .right(middle, -margin)
            .result());

    wSurrogateKeyField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyField);
    wSurrogateKeyField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSurrogateKeyStrategy, margin).right().result());

    Label wlSurrogateKeySourceField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeySourceField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeySourceField.Label"));
    PropsUi.setLook(wlSurrogateKeySourceField);
    wlSurrogateKeySourceField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeyField, margin)
            .right(middle, -margin)
            .result());

    wSurrogateKeySourceField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeySourceField);
    wSurrogateKeySourceField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSurrogateKeyField, margin).right().result());

    Label wlHashCodeStrategy = new Label(comp, SWT.RIGHT);
    wlHashCodeStrategy.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.HashCodeStrategy.Label"));
    PropsUi.setLook(wlHashCodeStrategy);
    wlHashCodeStrategy.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeySourceField, margin)
            .right(middle, -margin)
            .result());

    wHashCodeStrategy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wHashCodeStrategy);
    EnumDialogSupport.populateCombo(wHashCodeStrategy, DmJunkHashCodeStrategy.class);
    wHashCodeStrategy.addListener(SWT.Selection, e -> refreshJunkHashControlsState());
    wHashCodeStrategy.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSurrogateKeySourceField, margin)
            .right()
            .result());

    wUseSurrogateKeyAsHashCodeField = new Button(comp, SWT.CHECK);
    wUseSurrogateKeyAsHashCodeField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.UseSurrogateKeyAsHashCodeField.Label"));
    wUseSurrogateKeyAsHashCodeField.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.UseSurrogateKeyAsHashCodeField.ToolTip"));
    PropsUi.setLook(wUseSurrogateKeyAsHashCodeField);
    wUseSurrogateKeyAsHashCodeField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wHashCodeStrategy, margin).right().result());
    wUseSurrogateKeyAsHashCodeField.addListener(SWT.Selection, e -> refreshJunkHashControlsState());

    Label wlHashCodeField = new Label(comp, SWT.RIGHT);
    wlHashCodeField.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.HashCodeField.Label"));
    PropsUi.setLook(wlHashCodeField);
    wlHashCodeField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wUseSurrogateKeyAsHashCodeField, margin)
            .right(middle, -margin)
            .result());

    wHashCodeField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHashCodeField);
    wHashCodeField.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wUseSurrogateKeyAsHashCodeField, margin)
            .right()
            .result());

    refreshSurrogateKeyFieldState();
    refreshJunkHashControlsState();
  }

  private void refreshSurrogateKeyFieldState() {
    if (wSurrogateKeyStrategy == null || wSurrogateKeyStrategy.isDisposed()) {
      return;
    }
    boolean dimensionTable = dimension && input instanceof DmDimension;
    boolean junkTable = junk && input instanceof DmJunkDimension;
    if (!dimensionTable && !junkTable) {
      return;
    }
    boolean noneStrategy = false;
    boolean sourceStrategy = false;
    if (dimensionTable) {
      DmSurrogateKeyStrategy strategy =
          EnumDialogSupport.readCombo(
              wSurrogateKeyStrategy, DmSurrogateKeyStrategy.class, DmSurrogateKeyStrategy.NONE);
      noneStrategy = strategy == DmSurrogateKeyStrategy.NONE;
      sourceStrategy = strategy == DmSurrogateKeyStrategy.USE_SOURCE_FIELD;
    } else if (junkTable) {
      DmJunkSurrogateKeyStrategy strategy =
          EnumDialogSupport.readCombo(
              wSurrogateKeyStrategy,
              DmJunkSurrogateKeyStrategy.class,
              DmJunkSurrogateKeyStrategy.AUTO_INCREMENT);
      sourceStrategy = strategy == DmJunkSurrogateKeyStrategy.USE_SOURCE_FIELD;
    }
    if (wSurrogateKeyField != null && !wSurrogateKeyField.isDisposed()) {
      wSurrogateKeyField.setEnabled(!noneStrategy);
    }
    if (wSurrogateKeySourceField != null && !wSurrogateKeySourceField.isDisposed()) {
      wSurrogateKeySourceField.setEnabled(sourceStrategy);
    }
    refreshJunkHashControlsState();
  }

  private void refreshJunkHashControlsState() {
    if (!junk || wHashCodeStrategy == null || wHashCodeStrategy.isDisposed()) {
      return;
    }
    DmJunkHashCodeStrategy hashStrategy =
        EnumDialogSupport.readCombo(
            wHashCodeStrategy, DmJunkHashCodeStrategy.class, DmJunkHashCodeStrategy.INTEGER_LEGACY);
    boolean usesHashColumn = hashStrategy.usesHashColumn();
    if (wUseSurrogateKeyAsHashCodeField != null && !wUseSurrogateKeyAsHashCodeField.isDisposed()) {
      wUseSurrogateKeyAsHashCodeField.setEnabled(usesHashColumn);
      if (!usesHashColumn) {
        wUseSurrogateKeyAsHashCodeField.setSelection(false);
      }
    }
    boolean useSurrogateAsHash =
        usesHashColumn
            && wUseSurrogateKeyAsHashCodeField != null
            && wUseSurrogateKeyAsHashCodeField.getSelection();
    if (wHashCodeField != null && !wHashCodeField.isDisposed()) {
      wHashCodeField.setEnabled(usesHashColumn && !useSurrogateAsHash);
      if (useSurrogateAsHash) {
        wHashCodeField.setText("");
      }
    }
  }

  private void addDimensionAliasTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionAlias.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionAlias.ToolTip"));

    Label wlReferencedModelFilename = new Label(comp, SWT.RIGHT);
    wlReferencedModelFilename.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.Label"));
    wlReferencedModelFilename.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.ToolTip"));
    PropsUi.setLook(wlReferencedModelFilename);
    wlReferencedModelFilename.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    Button wBrowseReferencedModel = new Button(comp, SWT.PUSH);
    wBrowseReferencedModel.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.Browse.Label"));
    wBrowseReferencedModel.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.Browse.ToolTip"));
    PropsUi.setLook(wBrowseReferencedModel);
    wBrowseReferencedModel.setLayoutData(new FormDataBuilder().right().top(0, margin).result());
    wBrowseReferencedModel.addListener(SWT.Selection, e -> browseReferencedModelFilename());

    wValidateReferencedModel = new Button(comp, SWT.PUSH);
    wValidateReferencedModel.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.Validate.Label"));
    wValidateReferencedModel.setToolTipText(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.ReferencedModelFilename.Validate.ToolTip"));
    PropsUi.setLook(wValidateReferencedModel);
    wValidateReferencedModel.setLayoutData(
        new FormDataBuilder().right(wBrowseReferencedModel, -margin).top(0, margin).result());
    wValidateReferencedModel.addListener(SWT.Selection, e -> validateReferencedModelFilename());

    Label wlReferencedDimension = new Label(comp, SWT.RIGHT);
    wlReferencedDimension.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedDimension.Label"));
    PropsUi.setLook(wlReferencedDimension);
    wlReferencedDimension.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wReferencedModelFilename, margin)
            .right(middle, -margin)
            .result());

    wReferencedModelFilename = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wReferencedModelFilename);
    wReferencedModelFilename.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(0, margin)
            .right(wValidateReferencedModel, -margin)
            .result());

    wReferencedDimension = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wReferencedDimension);
    wReferencedDimension.setItems(listBaseDimensionNames());
    wReferencedDimension.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wReferencedModelFilename, margin)
            .right()
            .result());
  }

  private IVariables hopGuiVariables() {
    HopGui hopGui = HopGui.getInstance();
    return hopGui != null ? hopGui.getVariables() : variables;
  }

  private void browseReferencedModelFilename() {
    String selectedFile =
        BaseDialog.presentFileDialog(
            false,
            shell,
            null,
            hopGuiVariables(),
            null,
            new String[] {"*" + HopDimensionalFileType.DIMENSIONAL_FILE_EXTENSION},
            new String[] {HopDimensionalFileType.DIMENSIONAL_FILE_TYPE_DESCRIPTION},
            false);
    if (Utils.isEmpty(selectedFile)) {
      return;
    }
    try {
      String storedPath = selectedFile;
      if (!storedPath.contains("${")) {
        storedPath =
            DmModelLoadSupport.toStoredModelPath(
                selectedFile, model != null ? model.getFilename() : null, hopGuiVariables());
      }
      wReferencedModelFilename.setText(storedPath);
      validateReferencedModelFilename();
    } catch (HopException e) {
      showReferencedModelLoadError(e);
    }
  }

  private void validateReferencedModelFilename() {
    populateReferencedDimensionChoices(true);
  }

  private void populateReferencedDimensionChoices(boolean showErrors) {
    if (wReferencedDimension == null || wReferencedDimension.isDisposed()) {
      return;
    }
    String previousSelection = wReferencedDimension.getText();
    String[] dimensionNames;
    try {
      dimensionNames = listReferencedDimensionNames(showErrors);
    } catch (HopException e) {
      if (showErrors) {
        showReferencedModelLoadError(e);
      }
      dimensionNames = listBaseDimensionNames();
    }
    wReferencedDimension.setItems(dimensionNames);
    if (!Utils.isEmpty(previousSelection)) {
      int index = wReferencedDimension.indexOf(previousSelection);
      if (index >= 0) {
        wReferencedDimension.select(index);
      }
    }
  }

  private String[] listReferencedDimensionNames(boolean invalidateCache) throws HopException {
    String externalModelPath = getReferencedModelFilenameText();
    if (Utils.isEmpty(externalModelPath)) {
      return listBaseDimensionNames();
    }
    IVariables pathVariables = hopGuiVariables();
    String referringModel = model != null ? model.getFilename() : null;
    if (invalidateCache) {
      DmModelLoadSupport.invalidateCachedModel(externalModelPath, referringModel, pathVariables);
    }
    DimensionalModel externalModel =
        DmModelLoadSupport.loadDimensionalModel(
            externalModelPath, referringModel, pathVariables, metadataProvider);
    return DmModelLoadSupport.listBaseDimensionNames(externalModel);
  }

  private String getReferencedModelFilenameText() {
    if (wReferencedModelFilename == null || wReferencedModelFilename.isDisposed()) {
      return null;
    }
    return wReferencedModelFilename.getText().trim();
  }

  private void showReferencedModelLoadError(HopException exception) {
    new ErrorDialog(
        shell,
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.LoadError.Header"),
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.ReferencedModelFilename.LoadError.Message"),
        exception);
  }

  private void addSourceTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Source.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Source.ToolTip"));

    Label wlSourceType = new Label(comp, SWT.RIGHT);
    wlSourceType.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceType.Label"));
    wlSourceType.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceType.ToolTip"));
    PropsUi.setLook(wlSourceType);
    wlSourceType.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wSourceType = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wSourceType);
    populateSourceTypeCombo();
    wSourceType.addListener(SWT.Selection, e -> refreshSourcePanelVisibility());
    wSourceType.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    wlSourceFactTable = new Label(comp, SWT.RIGHT);
    wlSourceFactTable.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceFactTable.Label"));
    wlSourceFactTable.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceFactTable.ToolTip"));
    PropsUi.setLook(wlSourceFactTable);
    wlSourceFactTable.setLayoutData(
        new FormDataBuilder().left().top(wSourceType, margin).right(middle, -margin).result());

    wSourceFactTable = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wSourceFactTable);
    wSourceFactTable.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSourceType, margin).right().result());

    wSourceConnection =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            DatabaseMeta.class,
            comp,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceConnection.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceConnection.ToolTip"));
    wSourceConnection.setLayoutData(
        new FormDataBuilder().left().top(wSourceType, margin).right().result());
    try {
      wSourceConnection.fillItems();
    } catch (HopException e) {
      // best effort
    }

    wlSourceSql = new Label(comp, SWT.RIGHT | SWT.TOP);
    wlSourceSql.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceSql.Label"));
    PropsUi.setLook(wlSourceSql);
    wlSourceSql.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSourceConnection, margin)
            .right(middle, -margin)
            .result());

    int sqlStyle = SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
    if (EnvironmentUtils.getInstance().isWeb()) {
      wSourceSql = new StyledTextComp(variables, comp, sqlStyle);
    } else {
      wSourceSql = new SQLStyledTextComp(variables, comp, sqlStyle);
      wSourceSql.addLineStyleListener(getSqlReservedWords());
    }
    PropsUi.setLook(wSourceSql, Props.WIDGET_STYLE_FIXED);

    wSourcePreview = new Button(comp, SWT.PUSH);
    wSourcePreview.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePreview.Label"));
    wSourcePreview.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePreview.ToolTip"));
    PropsUi.setLook(wSourcePreview);
    wSourcePreview.setLayoutData(new FormDataBuilder().left(middle, 0).bottom().result());
    wSourcePreview.addListener(SWT.Selection, e -> previewSourceSql());

    wSourceSql.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSourceConnection, margin)
            .right()
            .bottom(wSourcePreview, -margin)
            .result());

    wlSourcePipelineFile = new Label(comp, SWT.RIGHT);
    wlSourcePipelineFile.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineFile.Label"));
    wlSourcePipelineFile.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineFile.ToolTip"));
    PropsUi.setLook(wlSourcePipelineFile);
    wlSourcePipelineFile.setLayoutData(
        new FormDataBuilder().left().top(wSourceType, margin).right(middle, -margin).result());

    wOpenSourcePipeline = new Button(comp, SWT.PUSH);
    wOpenSourcePipeline.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineFile.Open.Label"));
    wOpenSourcePipeline.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineFile.Open.ToolTip"));
    PropsUi.setLook(wOpenSourcePipeline);
    wOpenSourcePipeline.setLayoutData(
        new FormDataBuilder().right().top(wSourceType, margin).result());
    wOpenSourcePipeline.addListener(SWT.Selection, e -> openSourcePipelineFile());

    wBrowseSourcePipeline = new Button(comp, SWT.PUSH);
    wBrowseSourcePipeline.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineFile.Browse.Label"));
    wBrowseSourcePipeline.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineFile.Browse.ToolTip"));
    PropsUi.setLook(wBrowseSourcePipeline);
    wBrowseSourcePipeline.setLayoutData(
        new FormDataBuilder()
            .right(wOpenSourcePipeline, -margin)
            .top(wSourceType, margin)
            .result());
    wBrowseSourcePipeline.addListener(SWT.Selection, e -> browseSourcePipelineFile());

    wSourcePipelineFile = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourcePipelineFile);
    wSourcePipelineFile.addListener(
        SWT.Modify,
        e -> {
          refreshSourcePipelineTransformChoices();
          refreshOpenSourcePipelineButtonState();
        });
    wSourcePipelineFile.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSourceType, margin)
            .right(wBrowseSourcePipeline, -margin)
            .result());

    wlSourcePipelineTransform = new Label(comp, SWT.RIGHT);
    wlSourcePipelineTransform.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineTransform.Label"));
    wlSourcePipelineTransform.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineTransform.ToolTip"));
    PropsUi.setLook(wlSourcePipelineTransform);
    wlSourcePipelineTransform.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSourcePipelineFile, margin)
            .right(middle, -margin)
            .result());

    wSourcePipelineTransform = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wSourcePipelineTransform);
    wSourcePipelineTransform.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSourcePipelineFile, margin).right().result());

    wSourcePipelineRunConfiguration =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            PipelineRunConfiguration.class,
            comp,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelineRunConfiguration.Label"),
            BaseMessages.getString(
                PKG, "HopGuiDmTableDialog.SourcePipelineRunConfiguration.ToolTip"));
    wSourcePipelineRunConfiguration.setLayoutData(
        new FormDataBuilder().left().top(wSourcePipelineTransform, margin).right().result());
    try {
      wSourcePipelineRunConfiguration.fillItems();
    } catch (HopException e) {
      // best effort
    }

    wSourcePipelinePreviewData = new Button(comp, SWT.PUSH);
    wSourcePipelinePreviewData.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelinePreviewData.Label"));
    wSourcePipelinePreviewData.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelinePreviewData.ToolTip"));
    PropsUi.setLook(wSourcePipelinePreviewData);
    wSourcePipelinePreviewData.setLayoutData(
        new FormDataBuilder().left(middle, 0).bottom().result());
    wSourcePipelinePreviewData.addListener(SWT.Selection, e -> previewSourcePipelineData());

    wSourcePipelinePreview = new Button(comp, SWT.PUSH);
    wSourcePipelinePreview.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelinePreview.Label"));
    wSourcePipelinePreview.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePipelinePreview.ToolTip"));
    PropsUi.setLook(wSourcePipelinePreview);
    wSourcePipelinePreview.setLayoutData(
        new FormDataBuilder().left(wSourcePipelinePreviewData, margin).bottom().result());
    wSourcePipelinePreview.addListener(SWT.Selection, e -> previewSourcePipelineFields());

    wSourceCatalogConnection =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            DataCatalogMeta.class,
            comp,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceCatalogConnection.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceCatalogConnection.ToolTip"));
    wSourceCatalogConnection.setLayoutData(
        new FormDataBuilder().left().top(wSourceType, margin).right().result());
    try {
      wSourceCatalogConnection.fillItems();
    } catch (HopException e) {
      // best effort
    }

    wlSourceRecordNamespace = new Label(comp, SWT.RIGHT);
    wlSourceRecordNamespace.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordNamespace.Label"));
    wlSourceRecordNamespace.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordNamespace.ToolTip"));
    PropsUi.setLook(wlSourceRecordNamespace);
    wlSourceRecordNamespace.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSourceCatalogConnection, margin)
            .right(middle, -margin)
            .result());

    wSourceRecordNamespace = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceRecordNamespace);
    wSourceRecordNamespace.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSourceCatalogConnection, margin)
            .right()
            .result());

    wlSourceRecordName = new Label(comp, SWT.RIGHT);
    wlSourceRecordName.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordName.Label"));
    wlSourceRecordName.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordName.ToolTip"));
    PropsUi.setLook(wlSourceRecordName);
    wlSourceRecordName.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSourceRecordNamespace, margin)
            .right(middle, -margin)
            .result());

    wSelectSourceRecord = new Button(comp, SWT.PUSH);
    wSelectSourceRecord.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordSelect.Label"));
    wSelectSourceRecord.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordSelect.ToolTip"));
    PropsUi.setLook(wSelectSourceRecord);
    wSelectSourceRecord.setLayoutData(
        new FormDataBuilder().right().top(wSourceCatalogConnection, margin).result());
    wSelectSourceRecord.addListener(SWT.Selection, e -> selectSourceRecordDefinition());

    wSourceRecordName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceRecordName);
    wSourceRecordName.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSourceRecordNamespace, margin)
            .right(wSelectSourceRecord, -margin)
            .result());

    wSourceRecordPreviewData = new Button(comp, SWT.PUSH);
    wSourceRecordPreviewData.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordPreviewData.Label"));
    wSourceRecordPreviewData.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordPreviewData.ToolTip"));
    PropsUi.setLook(wSourceRecordPreviewData);
    wSourceRecordPreviewData.setLayoutData(new FormDataBuilder().left(middle, 0).bottom().result());
    wSourceRecordPreviewData.addListener(SWT.Selection, e -> previewSourceRecordData());

    wSourceRecordPreviewFields = new Button(comp, SWT.PUSH);
    wSourceRecordPreviewFields.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordPreviewFields.Label"));
    wSourceRecordPreviewFields.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordPreviewFields.ToolTip"));
    PropsUi.setLook(wSourceRecordPreviewFields);
    wSourceRecordPreviewFields.setLayoutData(
        new FormDataBuilder().left(wSourceRecordPreviewData, margin).bottom().result());
    wSourceRecordPreviewFields.addListener(SWT.Selection, e -> previewSourceRecordFields());

    String loadDateField =
        model != null ? model.getConfigurationOrDefault().resolveLoadDateField(variables) : null;
    dateGeneratorWidgets =
        DmSourceDateGeneratorGuiSupport.create(comp, variables, wSourceType, margin, loadDateField);

    refreshSourcePanelVisibility();
  }

  private void refreshOpenSourcePipelineButtonState() {
    if (wOpenSourcePipeline == null || wOpenSourcePipeline.isDisposed()) {
      return;
    }
    boolean enabled =
        isPipelineSourceSelected()
            && DmSourcePipelineOpenSupport.canOpenSourcePipeline(
                variables, wSourcePipelineFile.getText(), true);
    wOpenSourcePipeline.setEnabled(enabled);
  }

  private void openSourcePipelineFile() {
    HopGui hopGui = HopGui.getInstance();
    if (hopGui == null) {
      return;
    }
    DmSourcePipelineOpenSupport.openSourcePipelineFile(
        hopGui, shell, variables, wSourcePipelineFile.getText());
  }

  private void browseSourcePipelineFile() {
    String selectedFile =
        BaseDialog.presentFileDialog(
            false, shell, new String[] {"*.hpl"}, new String[] {"Hop pipeline files"}, false);
    if (Utils.isEmpty(selectedFile)) {
      return;
    }
    wSourcePipelineFile.setText(variables.resolve(selectedFile));
    refreshSourcePipelineTransformChoices();
    refreshOpenSourcePipelineButtonState();
  }

  private void refreshSourcePipelineTransformChoices() {
    if (wSourcePipelineTransform == null || wSourcePipelineTransform.isDisposed()) {
      return;
    }
    String previousSelection = wSourcePipelineTransform.getText();
    try {
      List<String> transforms =
          DmSourcePipelineGuiSupport.listTransformNames(
              variables, metadataProvider, wSourcePipelineFile.getText());
      wSourcePipelineTransform.setItems(transforms.toArray(new String[0]));
      if (!Utils.isEmpty(previousSelection)) {
        int index = wSourcePipelineTransform.indexOf(previousSelection);
        if (index >= 0) {
          wSourcePipelineTransform.select(index);
        }
      }
    } catch (HopException e) {
      wSourcePipelineTransform.setItems(new String[0]);
    }
  }

  private void populateSourceTypeCombo() {
    if (wSourceType == null || wSourceType.isDisposed()) {
      return;
    }
    String previousSelection = wSourceType.getText();
    String[] options =
        junk
            ? java.util.Arrays.stream(DmSourceType.values())
                .filter(type -> type != DmSourceType.DATE_GENERATOR)
                .map(DmSourceType::getDescription)
                .toArray(String[]::new)
            : java.util.Arrays.stream(DmSourceType.values())
                .filter(type -> type != DmSourceType.FACT_TABLE)
                .map(DmSourceType::getDescription)
                .toArray(String[]::new);
    wSourceType.setItems(options);
    if (!Utils.isEmpty(previousSelection)) {
      int index = wSourceType.indexOf(previousSelection);
      if (index >= 0) {
        wSourceType.select(index);
      }
    }
  }

  private void refreshSourceFactTableChoices() {
    if (wSourceFactTable == null || wSourceFactTable.isDisposed()) {
      return;
    }
    String previousSelection = wSourceFactTable.getText();
    wSourceFactTable.setItems(DmJunkDimensionSupport.listFactTableNames(model));
    if (!Utils.isEmpty(previousSelection)) {
      int index = wSourceFactTable.indexOf(previousSelection);
      if (index >= 0) {
        wSourceFactTable.select(index);
      } else {
        wSourceFactTable.setText(previousSelection);
      }
    }
  }

  private void refreshSourcePanelVisibility() {
    boolean factTableSource = isFactTableSourceSelected();
    boolean pipelineSource = isPipelineSourceSelected();
    boolean recordDefinitionSource = isRecordDefinitionSourceSelected();
    boolean dateGeneratorSource = isDateGeneratorSourceSelected();
    boolean sqlSource =
        !factTableSource && !pipelineSource && !recordDefinitionSource && !dateGeneratorSource;
    setSourceWidgetVisible(wlSourceFactTable, factTableSource);
    setSourceWidgetVisible(wSourceFactTable, factTableSource);
    if (factTableSource) {
      refreshSourceFactTableChoices();
    }
    setSourceWidgetVisible(wSourceConnection, sqlSource);
    setSourceWidgetVisible(wlSourceSql, sqlSource);
    setSourceWidgetVisible(wSourceSql, sqlSource);
    setSourceWidgetVisible(wSourcePreview, sqlSource);
    setSourceWidgetVisible(wlSourcePipelineFile, pipelineSource);
    setSourceWidgetVisible(wSourcePipelineFile, pipelineSource);
    setSourceWidgetVisible(wBrowseSourcePipeline, pipelineSource);
    setSourceWidgetVisible(wOpenSourcePipeline, pipelineSource);
    refreshOpenSourcePipelineButtonState();
    setSourceWidgetVisible(wlSourcePipelineTransform, pipelineSource);
    setSourceWidgetVisible(wSourcePipelineTransform, pipelineSource);
    setSourceWidgetVisible(wSourcePipelineRunConfiguration, pipelineSource);
    setSourceWidgetVisible(wSourcePipelinePreviewData, pipelineSource);
    setSourceWidgetVisible(wSourcePipelinePreview, pipelineSource);
    setSourceWidgetVisible(wSourceCatalogConnection, recordDefinitionSource);
    setSourceWidgetVisible(wlSourceRecordNamespace, recordDefinitionSource);
    setSourceWidgetVisible(wSourceRecordNamespace, recordDefinitionSource);
    setSourceWidgetVisible(wlSourceRecordName, recordDefinitionSource);
    setSourceWidgetVisible(wSourceRecordName, recordDefinitionSource);
    setSourceWidgetVisible(wSelectSourceRecord, recordDefinitionSource);
    setSourceWidgetVisible(wSourceRecordPreviewData, recordDefinitionSource);
    setSourceWidgetVisible(wSourceRecordPreviewFields, recordDefinitionSource);
    if (dateGeneratorWidgets != null) {
      for (org.eclipse.swt.widgets.Control control : dateGeneratorWidgets.allControls()) {
        setSourceWidgetVisible(control, dateGeneratorSource);
      }
    }
  }

  private void previewSourcePipelineData() {
    DmSourcePipelineGuiSupport.previewSourcePipelineData(
        shell,
        variables,
        metadataProvider,
        wSourcePipelineFile.getText(),
        wSourcePipelineTransform.getText());
  }

  private boolean isFactTableSourceSelected() {
    return junk && resolveSelectedSourceType() == DmSourceType.FACT_TABLE;
  }

  private boolean isPipelineSourceSelected() {
    return resolveSelectedSourceType() == DmSourceType.PIPELINE;
  }

  private boolean isRecordDefinitionSourceSelected() {
    return resolveSelectedSourceType() == DmSourceType.RECORD_DEFINITION;
  }

  private boolean isDateGeneratorSourceSelected() {
    return resolveSelectedSourceType() == DmSourceType.DATE_GENERATOR;
  }

  private DmSourceType resolveSelectedSourceType() {
    if (wSourceType == null || wSourceType.isDisposed()) {
      return DmSourceType.SQL;
    }
    return EnumDialogSupport.readCombo(wSourceType, DmSourceType.class, DmSourceType.SQL);
  }

  private String resolveSourceCatalogConnectionText() {
    String connection =
        wSourceCatalogConnection != null ? wSourceCatalogConnection.getText() : null;
    if (!Utils.isEmpty(connection)) {
      return connection;
    }
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    return config.getDataCatalogConnection();
  }

  private void selectSourceRecordDefinition() {
    try {
      String catalogConnection = resolveSourceCatalogConnectionText();
      if (Utils.isEmpty(catalogConnection)) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "DmSourceRecordDefinitionGuiSupport.Error.MissingCatalogConnection"));
      }
      List<DmSourceRecordDefinitionGuiSupport.PreviewableRecordRef> records =
          DmSourceRecordDefinitionGuiSupport.listPreviewableRecordDefinitions(
              catalogConnection, variables, metadataProvider);
      if (records.isEmpty()) {
        throw new HopException(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordSelect.Message"));
      }
      String[] choices = records.stream().map(r -> r.label()).toArray(String[]::new);
      EnterSelectionDialog dialog =
          new EnterSelectionDialog(
              shell,
              choices,
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordSelect.Title"),
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordSelect.Message"));
      if (dialog.open() == null) {
        return;
      }
      int[] indices = dialog.getSelectionIndeces();
      if (indices == null
          || indices.length == 0
          || indices[0] < 0
          || indices[0] >= records.size()) {
        return;
      }
      DmSourceRecordDefinitionGuiSupport.PreviewableRecordRef selected = records.get(indices[0]);
      wSourceRecordNamespace.setText(selected.namespace());
      wSourceRecordName.setText(selected.name());
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceRecordSelect.Title"),
          e.getMessage(),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private void previewSourceRecordFields() {
    DmSourceRecordDefinitionGuiSupport.previewFields(
        shell,
        model.getConfigurationOrDefault(),
        variables,
        metadataProvider,
        resolveSourceCatalogConnectionText(),
        wSourceRecordNamespace.getText(),
        wSourceRecordName.getText());
  }

  private void previewSourceRecordData() {
    DmSourceRecordDefinitionGuiSupport.previewData(
        shell,
        model.getConfigurationOrDefault(),
        variables,
        metadataProvider,
        resolveSourceCatalogConnectionText(),
        wSourceRecordNamespace.getText(),
        wSourceRecordName.getText());
  }

  private void setSourceWidgetVisible(Object widget, boolean visible) {
    if (widget == null) {
      return;
    }
    if (widget instanceof org.eclipse.swt.widgets.Control control && !control.isDisposed()) {
      control.setVisible(visible);
      Object layoutData = control.getLayoutData();
      if (layoutData instanceof org.eclipse.swt.layout.FormData formData) {
        if (visible) {
          formData.width = SWT.DEFAULT;
          formData.height = SWT.DEFAULT;
        } else {
          formData.width = 0;
          formData.height = 0;
        }
      }
      Composite parent = control.getParent();
      if (parent != null && !parent.isDisposed()) {
        parent.layout(true, true);
      }
    }
  }

  private void previewSourcePipelineFields() {
    DmSourcePipelineGuiSupport.previewSourcePipelineFields(
        shell,
        variables,
        metadataProvider,
        wSourcePipelineFile.getText(),
        wSourcePipelineTransform.getText());
  }

  private List<String> getSqlReservedWords() {
    DatabaseMeta connection = resolveSourceDatabaseMeta();
    if (connection == null) {
      return List.of();
    }
    return List.of(connection.getReservedWords());
  }

  private DatabaseMeta resolveSourceDatabaseMeta() {
    if (metadataProvider == null) {
      return null;
    }
    String connectionName = null;
    if (wSourceConnection != null && !wSourceConnection.isDisposed()) {
      connectionName = variables.resolve(wSourceConnection.getText());
    }
    if (Utils.isEmpty(connectionName)) {
      DimensionalConfiguration config =
          model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
      connectionName = input.getSourceOrDefault().resolveSourceConnection(config, variables);
    }
    if (Utils.isEmpty(connectionName)) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (HopException e) {
      return null;
    }
  }

  private void addJunkKeyFieldsTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.JunkKeyFields.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.JunkKeyFields.ToolTip"));

    naturalKeyFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.JunkKeyFields.Column.Field"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    ColumnInfo[] keyFieldColumns = new ColumnInfo[] {naturalKeyFieldColumn};

    Button wGetJunkKeyFields = new Button(comp, SWT.PUSH);
    wGetJunkKeyFields.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.JunkKeyFields.GetFields.Label"));
    wGetJunkKeyFields.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.JunkKeyFields.GetFields.ToolTip"));
    PropsUi.setLook(wGetJunkKeyFields);
    wGetJunkKeyFields.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetJunkKeyFields.addListener(SWT.Selection, e -> getJunkKeyFieldsFromSource());

    wNaturalKeys =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            keyFieldColumns,
            3,
            null,
            PropsUi.getInstance());
    wNaturalKeys.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(0, margin)
            .right()
            .bottom(wGetJunkKeyFields, -margin)
            .result());
  }

  private void addBridgeDimensionsTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.BridgeDimensions.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.BridgeDimensions.ToolTip"));

    String[] dimensionNames = listDimensionNames();
    ColumnInfo[] bridgeColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.BridgeDimensions.Column.Dimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              dimensionNames,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.BridgeDimensions.Column.ForeignKey"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    wOutriggers =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            bridgeColumns,
            2,
            null,
            PropsUi.getInstance());
    wOutriggers.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom().result());
  }

  private void addNaturalKeysTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.NaturalKeys.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.NaturalKeys.ToolTip"));

    naturalKeyFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.NaturalKeys.Column.Field"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    ColumnInfo[] naturalKeyColumns = new ColumnInfo[] {naturalKeyFieldColumn};

    Button wGetKeys = new Button(comp, SWT.PUSH);
    wGetKeys.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.NaturalKeys.GetKeys.Label"));
    wGetKeys.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.NaturalKeys.GetKeys.ToolTip"));
    PropsUi.setLook(wGetKeys);
    wGetKeys.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetKeys.addListener(SWT.Selection, e -> getNaturalKeysFromSource());

    wNaturalKeys =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            naturalKeyColumns,
            3,
            null,
            PropsUi.getInstance());
    wNaturalKeys.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wGetKeys, -margin).result());
  }

  private void addAttributesTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Attributes.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Attributes.ToolTip"));

    attributeSourceFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.Column.SourceField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    attributeTargetFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.Column.TargetField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    ColumnInfo[] attributeColumns =
        new ColumnInfo[] {
          attributeSourceFieldColumn,
          attributeTargetFieldColumn,
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.Column.ScdPolicy"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              EnumDialogSupport.comboOptions(DmScdUpdatePolicy.class),
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.Column.PreviousField"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };

    Button wGetAttributes = new Button(comp, SWT.PUSH);
    wGetAttributes.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.GetAttributes.Label"));
    wGetAttributes.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.GetAttributes.ToolTip"));
    PropsUi.setLook(wGetAttributes);
    wGetAttributes.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetAttributes.addListener(SWT.Selection, e -> getAttributesFromSource());

    wAttributes =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            attributeColumns,
            4,
            null,
            PropsUi.getInstance());
    wAttributes.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(0, margin)
            .right()
            .bottom(wGetAttributes, -margin)
            .result());
  }

  private void addOutriggersTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Outriggers.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Outriggers.ToolTip"));

    String[] dimensionNames = listDimensionNames();
    ColumnInfo[] outriggerColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Outriggers.Column.Dimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              dimensionNames,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Outriggers.Column.ForeignKey"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    wOutriggers =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            outriggerColumns,
            2,
            null,
            PropsUi.getInstance());
    wOutriggers.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom().result());
  }

  private void addDimensionRolesTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionRoles.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionRoles.ToolTip"));

    String[] dimensionNames = listDimensionNames();
    dimensionJoinSourceFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.SourceField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    dimensionJoinSourceFieldColumn.setToolTip(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.DimensionRoles.Column.SourceField.ToolTip"));
    ColumnInfo factKeyColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.ForeignKey"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    factKeyColumn.setToolTip(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.DimensionRoles.Column.ForeignKey.ToolTip"));
    ColumnInfo preloadCacheColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.PreloadCache"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {"N", "Y"},
            true);
    preloadCacheColumn.setToolTip(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.DimensionRoles.Column.PreloadCache.ToolTip"));
    ColumnInfo skipLookupColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.SkipLookup"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {SKIP_LOOKUP_AUTO, SKIP_LOOKUP_YES, SKIP_LOOKUP_NO},
            true);
    skipLookupColumn.setToolTip(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.DimensionRoles.Column.SkipLookup.ToolTip"));
    ColumnInfo[] roleColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.Dimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              dimensionNames,
              true),
          dimensionJoinSourceFieldColumn,
          factKeyColumn,
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiDmTableDialog.DimensionRoles.Column.TruncateToDate"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"N", "Y"},
              true),
          preloadCacheColumn,
          skipLookupColumn
        };

    Label wlDimensionLookupDate = new Label(comp, SWT.RIGHT);
    wlDimensionLookupDate.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.LookupDateField.Label"));
    PropsUi.setLook(wlDimensionLookupDate);
    wlDimensionLookupDate.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wlDimensionLookupDate.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.LookupDateField.ToolTip"));

    wDimensionLookupDateField = new Combo(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDimensionLookupDateField);
    wDimensionLookupDateField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Button wGetJoins = new Button(comp, SWT.PUSH);
    wGetJoins.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.GetJoins.Label"));
    wGetJoins.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.GetJoins.ToolTip"));
    PropsUi.setLook(wGetJoins);
    wGetJoins.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetJoins.addListener(SWT.Selection, e -> getJoinsFromSource());

    wDimensionRoles =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            roleColumns,
            3,
            null,
            PropsUi.getInstance());
    wDimensionRoles.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wDimensionLookupDateField, margin)
            .right()
            .bottom(wGetJoins, -margin)
            .result());
  }

  private void addMeasuresTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Measures.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Measures.ToolTip"));

    measureFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.Column.Field"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    ColumnInfo[] measureColumns =
        new ColumnInfo[] {
          measureFieldColumn,
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.Column.Additive"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"Y", "N"},
              true)
        };

    Button wGetMeasures = new Button(comp, SWT.PUSH);
    wGetMeasures.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.GetMeasures.Label"));
    wGetMeasures.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.GetMeasures.ToolTip"));
    PropsUi.setLook(wGetMeasures);
    wGetMeasures.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetMeasures.addListener(SWT.Selection, e -> getMeasuresFromSource());

    wMeasures =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            measureColumns,
            3,
            null,
            PropsUi.getInstance());
    wMeasures.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wGetMeasures, -margin).result());
  }

  private void addDegenerateDimensionsTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DegenerateDimensions.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DegenerateDimensions.ToolTip"));

    degenerateDimensionFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.DegenerateDimensions.Column.Field"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);

    Button wGetDegenerateDimensions = new Button(comp, SWT.PUSH);
    wGetDegenerateDimensions.setText(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.DegenerateDimensions.GetDegenerateDimensions.Label"));
    wGetDegenerateDimensions.setToolTipText(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.DegenerateDimensions.GetDegenerateDimensions.ToolTip"));
    PropsUi.setLook(wGetDegenerateDimensions);
    wGetDegenerateDimensions.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetDegenerateDimensions.addListener(SWT.Selection, e -> getDegenerateDimensionsFromSource());

    wDegenerateDimensions =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            new ColumnInfo[] {degenerateDimensionFieldColumn},
            1,
            null,
            PropsUi.getInstance());
    wDegenerateDimensions.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(0, margin)
            .right()
            .bottom(wGetDegenerateDimensions, -margin)
            .result());
  }

  private void addRangeBandsTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.RangeBands.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.RangeBands.ToolTip"));

    Label wlFallBackLabel = new Label(comp, SWT.RIGHT);
    wlFallBackLabel.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeBands.FallBackLabel.Label"));
    PropsUi.setLook(wlFallBackLabel);
    wlFallBackLabel.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wlFallBackLabel.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeBands.FallBackLabel.ToolTip"));

    wFallBackLabel = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFallBackLabel);
    wFallBackLabel.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    ColumnInfo[] bandColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeBands.Column.LowerBound"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeBands.Column.UpperBound"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeBands.Column.Label"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    bandColumns[0].setToolTip(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeBands.Column.LowerBound.ToolTip"));
    bandColumns[1].setToolTip(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeBands.Column.UpperBound.ToolTip"));

    wRangeBands =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            bandColumns,
            3,
            null,
            PropsUi.getInstance());
    wRangeBands.setLayoutData(
        new FormDataBuilder().left().top(wFallBackLabel, margin).right().bottom().result());
  }

  private void addRangeDimensionRolesTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.RangeDimensions.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.RangeDimensions.ToolTip"));

    String[] rangeDimensionNames =
        DmRangeDimensionSupport.listRangeDimensionNames(model, input.getName());
    rangeDimensionSourceFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeDimensions.Column.SourceField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    rangeDimensionSourceFieldColumn.setToolTip(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.RangeDimensions.Column.SourceField.ToolTip"));
    ColumnInfo[] roleColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiDmTableDialog.RangeDimensions.Column.RangeDimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              rangeDimensionNames,
              true),
          rangeDimensionSourceFieldColumn,
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeDimensions.Column.TargetField"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    roleColumns[2].setToolTip(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.RangeDimensions.Column.TargetField.ToolTip"));

    Button wGetRangeDimensions = new Button(comp, SWT.PUSH);
    wGetRangeDimensions.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeDimensions.GetCandidates.Label"));
    wGetRangeDimensions.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.RangeDimensions.GetCandidates.ToolTip"));
    PropsUi.setLook(wGetRangeDimensions);
    wGetRangeDimensions.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetRangeDimensions.addListener(SWT.Selection, e -> suggestRangeDimensionRolesFromSource());

    wRangeDimensionRoles =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            roleColumns,
            3,
            null,
            PropsUi.getInstance());
    wRangeDimensionRoles.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(0, margin)
            .right()
            .bottom(wGetRangeDimensions, -margin)
            .result());
  }

  private void addJunkDimensionRolesTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.JunkDimensions.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.JunkDimensions.ToolTip"));

    String[] junkDimensionNames =
        DmJunkDimensionSupport.listJunkDimensionNames(model, input.getName());
    ColumnInfo[] roleColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiDmTableDialog.JunkDimensions.Column.JunkDimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              junkDimensionNames,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.JunkDimensions.Column.ForeignKey"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };

    wJunkDimensionRoles =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            roleColumns,
            2,
            null,
            PropsUi.getInstance());
    wJunkDimensionRoles.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom().result());
  }

  private String[] listDimensionNames() {
    List<String> names = new ArrayList<>();
    if (model != null) {
      for (IDmTable table : model.getTables()) {
        if ((table instanceof DmDimension || table instanceof DmDimensionAlias)
            && !Utils.isEmpty(table.getName())) {
          if (input == null || !table.getName().equals(input.getName())) {
            names.add(table.getName());
          }
        }
      }
    }
    return names.toArray(new String[0]);
  }

  private String[] listBaseDimensionNames() {
    List<String> names = new ArrayList<>();
    if (model != null) {
      for (IDmTable table : model.getTables()) {
        if (table instanceof DmDimension && !Utils.isEmpty(table.getName())) {
          if (input == null || !table.getName().equals(input.getName())) {
            names.add(table.getName());
          }
        }
      }
    }
    return names.toArray(new String[0]);
  }

  private void getData() {
    if (!Utils.isEmpty(input.getName())) {
      wName.setText(input.getName());
    }
    if (!Utils.isEmpty(input.getTableName())) {
      wTableName.setText(input.getTableName());
    }
    if (!Utils.isEmpty(input.getDescription())) {
      wDescription.setText(input.getDescription());
    }
    if (!dimensionAlias && !range) {
      EnumDialogSupport.selectCombo(wSourceType, input.getSourceOrDefault().resolveSourceType());
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourceConnection())) {
        wSourceConnection.setText(input.getSourceOrDefault().getSourceConnection());
      }
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourceSql())) {
        wSourceSql.setText(input.getSourceOrDefault().getSourceSql());
      }
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourcePipelineFile())) {
        wSourcePipelineFile.setText(input.getSourceOrDefault().getSourcePipelineFile());
      }
      refreshSourcePipelineTransformChoices();
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourcePipelineTransform())) {
        int index =
            wSourcePipelineTransform.indexOf(
                input.getSourceOrDefault().getSourcePipelineTransform());
        if (index >= 0) {
          wSourcePipelineTransform.select(index);
        } else {
          wSourcePipelineTransform.setText(input.getSourceOrDefault().getSourcePipelineTransform());
        }
      }
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourcePipelineRunConfiguration())) {
        wSourcePipelineRunConfiguration.setText(
            input.getSourceOrDefault().getSourcePipelineRunConfiguration());
      }
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourceCatalogConnection())) {
        wSourceCatalogConnection.setText(input.getSourceOrDefault().getSourceCatalogConnection());
      } else if (!Utils.isEmpty(model.getConfigurationOrDefault().getDataCatalogConnection())) {
        wSourceCatalogConnection.setText(
            model.getConfigurationOrDefault().getDataCatalogConnection());
      }
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourceRecordNamespace())) {
        wSourceRecordNamespace.setText(input.getSourceOrDefault().getSourceRecordNamespace());
      }
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourceRecordName())) {
        wSourceRecordName.setText(input.getSourceOrDefault().getSourceRecordName());
      }
      if (junk && input instanceof DmJunkDimension junkDimension) {
        DmSourceType sourceType = junkDimension.getSourceOrDefault().resolveSourceType();
        if (sourceType == DmSourceType.SQL && junkDimension.isLoadFromFactTable()) {
          EnumDialogSupport.selectCombo(wSourceType, DmSourceType.FACT_TABLE);
        }
        refreshSourceFactTableChoices();
        String factTableName =
            DmJunkDimensionSupport.resolveFactTableName(junkDimension, variables);
        if (!Utils.isEmpty(factTableName) && wSourceFactTable != null) {
          int index = wSourceFactTable.indexOf(factTableName);
          if (index >= 0) {
            wSourceFactTable.select(index);
          } else {
            wSourceFactTable.setText(factTableName);
          }
        }
      }
      if (dateGeneratorWidgets != null) {
        DmSourceDateGeneratorGuiSupport.setData(
            dateGeneratorWidgets, input.getSourceOrDefault().getDateGenerator());
      }
      refreshSourcePanelVisibility();
      refreshOpenSourcePipelineButtonState();
    }

    if (junk && input instanceof DmJunkDimension junkDimension) {
      if (junkDimension.getSurrogateKeyStrategy() != null) {
        EnumDialogSupport.selectCombo(
            wSurrogateKeyStrategy, junkDimension.getSurrogateKeyStrategy());
      } else {
        EnumDialogSupport.selectCombo(
            wSurrogateKeyStrategy, DmSurrogateKeySupport.resolveJunkStrategy(junkDimension));
      }
      if (wSurrogateKeyField != null && !Utils.isEmpty(junkDimension.getSurrogateKeyField())) {
        wSurrogateKeyField.setText(junkDimension.getSurrogateKeyField());
      }
      if (wSurrogateKeySourceField != null
          && !Utils.isEmpty(junkDimension.getSurrogateKeySourceField())) {
        wSurrogateKeySourceField.setText(junkDimension.getSurrogateKeySourceField());
      }
      if (wHashCodeStrategy != null) {
        EnumDialogSupport.selectCombo(
            wHashCodeStrategy, junkDimension.getHashCodeStrategyOrDefault());
      }
      if (wUseSurrogateKeyAsHashCodeField != null) {
        boolean useSurrogateAsHash = junkDimension.isUseSurrogateKeyAsHashCodeField();
        if (!useSurrogateAsHash && !Utils.isEmpty(junkDimension.getHashCodeField())) {
          String surrogateField =
              DmSurrogateKeySupport.resolveJunkSurrogateKeyField(
                  junkDimension, model.getConfigurationOrDefault(), variables);
          useSurrogateAsHash =
              !Utils.isEmpty(surrogateField)
                  && surrogateField.equals(variables.resolve(junkDimension.getHashCodeField()));
        }
        wUseSurrogateKeyAsHashCodeField.setSelection(useSurrogateAsHash);
      }
      if (wHashCodeField != null && !Utils.isEmpty(junkDimension.getHashCodeField())) {
        wHashCodeField.setText(junkDimension.getHashCodeField());
      }
      refreshSurrogateKeyFieldState();
      wNaturalKeys.clearAll();
      for (DmNaturalKeyField keyField : junkDimension.getKeyFieldsOrEmpty()) {
        if (keyField == null || Utils.isEmpty(keyField.getFieldName())) {
          continue;
        }
        TableItem item = new TableItem(wNaturalKeys.table, SWT.NONE);
        item.setText(1, keyField.getFieldName());
      }
      wNaturalKeys.optimizeTableView();
    }

    if (bridge && input instanceof DmBridge dmBridge) {
      wOutriggers.clearAll();
      for (DmBridgeDimensionRef ref : dmBridge.getDimensionRefsOrEmpty()) {
        if (ref == null || Utils.isEmpty(ref.getDimensionTableName())) {
          continue;
        }
        TableItem item = new TableItem(wOutriggers.table, SWT.NONE);
        item.setText(1, ref.getDimensionTableName());
        if (!Utils.isEmpty(ref.getForeignKeyColumn())) {
          item.setText(2, ref.getForeignKeyColumn());
        }
      }
      wOutriggers.optimizeTableView();
    }

    if (dimensionAlias && input instanceof DmDimensionAlias alias) {
      if (wReferencedModelFilename != null && !Utils.isEmpty(alias.getReferencedModelFilename())) {
        wReferencedModelFilename.setText(alias.getReferencedModelFilename());
      }
      populateReferencedDimensionChoices(false);
      if (wReferencedDimension != null && !Utils.isEmpty(alias.getReferencedDimensionName())) {
        int index = wReferencedDimension.indexOf(alias.getReferencedDimensionName());
        if (index >= 0) {
          wReferencedDimension.select(index);
        }
      }
    }

    if (dimension && input instanceof DmDimension dmDimension) {
      refreshDerivedLoadStrategyLabel(dmDimension);
      if (dmDimension.getSurrogateKeyStrategy() != null) {
        EnumDialogSupport.selectCombo(wSurrogateKeyStrategy, dmDimension.getSurrogateKeyStrategy());
      } else {
        EnumDialogSupport.selectCombo(
            wSurrogateKeyStrategy, DmSurrogateKeySupport.resolveStrategy(dmDimension));
      }
      if (wSurrogateKeyField != null && !Utils.isEmpty(dmDimension.getSurrogateKeyField())) {
        wSurrogateKeyField.setText(dmDimension.getSurrogateKeyField());
      }
      if (wSurrogateKeySourceField != null
          && !Utils.isEmpty(dmDimension.getSurrogateKeySourceField())) {
        wSurrogateKeySourceField.setText(dmDimension.getSurrogateKeySourceField());
      }
      refreshSurrogateKeyFieldState();
      wNaturalKeys.clearAll();
      for (DmNaturalKeyField naturalKey : dmDimension.getNaturalKeysOrEmpty()) {
        if (naturalKey == null || Utils.isEmpty(naturalKey.getFieldName())) {
          continue;
        }
        TableItem item = new TableItem(wNaturalKeys.table, SWT.NONE);
        item.setText(1, naturalKey.getFieldName());
      }
      wNaturalKeys.optimizeTableView();
      wAttributes.clearAll();
      for (DmDimensionAttribute attribute : dmDimension.getAttributesOrEmpty()) {
        if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
          continue;
        }
        TableItem item = new TableItem(wAttributes.table, SWT.NONE);
        String sourceField =
            Utils.isEmpty(attribute.getSourceFieldName())
                ? attribute.getFieldName()
                : attribute.getSourceFieldName();
        item.setText(1, sourceField);
        item.setText(2, attribute.getFieldName());
        if (attribute.getScdUpdatePolicy() != null) {
          item.setText(3, EnumDialogSupport.descriptionOf(attribute.getScdUpdatePolicy()));
        }
        if (!Utils.isEmpty(attribute.getPreviousFieldName())) {
          item.setText(4, attribute.getPreviousFieldName());
        }
      }
      wAttributes.optimizeTableView();
      wOutriggers.clearAll();
      for (DmDimensionOutriggerRef outrigger : dmDimension.getOutriggersOrEmpty()) {
        if (outrigger == null || Utils.isEmpty(outrigger.getDimensionTableName())) {
          continue;
        }
        TableItem item = new TableItem(wOutriggers.table, SWT.NONE);
        item.setText(1, outrigger.getDimensionTableName());
        if (!Utils.isEmpty(outrigger.getForeignKeyColumn())) {
          item.setText(2, outrigger.getForeignKeyColumn());
        }
      }
      wOutriggers.optimizeTableView();
      refreshFieldComboChoices();
    }

    if (factLike && input instanceof IDmFactLikeTable factLikeTable) {
      if (wDimensionLookupDateField != null && input instanceof DmTableBase table) {
        refreshDimensionLookupDateComboChoices();
        if (!Utils.isEmpty(table.getDimensionLookupDateField())) {
          wDimensionLookupDateField.setText(table.getDimensionLookupDateField());
        }
      }
      wDimensionRoles.clearAll();
      for (DmFactDimensionRole role : factLikeTable.getDimensionRolesOrEmpty()) {
        if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
          continue;
        }
        TableItem item = new TableItem(wDimensionRoles.table, SWT.NONE);
        item.setText(1, role.getDimensionTableName());
        if (!Utils.isEmpty(role.getSourceFieldName())) {
          item.setText(2, role.getSourceFieldName());
        }
        if (!Utils.isEmpty(role.getForeignKeyColumn())) {
          item.setText(3, role.getForeignKeyColumn());
        }
        item.setText(4, role.isTruncateToDateKey() ? "Y" : "N");
        item.setText(5, role.isPreloadLookupCache() ? "Y" : "N");
        item.setText(6, formatSkipDimensionLookup(role));
      }
      wDimensionRoles.optimizeTableView();
      refreshDimensionJoinComboChoices();

      if (!factless && wMeasures != null) {
        wMeasures.clearAll();
        for (DmFactMeasure measure : factLikeTable.getMeasuresOrEmpty()) {
          if (measure == null || Utils.isEmpty(measure.getFieldName())) {
            continue;
          }
          TableItem item = new TableItem(wMeasures.table, SWT.NONE);
          item.setText(1, measure.getFieldName());
          item.setText(2, measure.isAdditive() ? "Y" : "N");
        }
        wMeasures.optimizeTableView();
        refreshMeasureComboChoices();
      }

      if (wDegenerateDimensions != null) {
        wDegenerateDimensions.clearAll();
        for (DmFactDegenerateDimension degenerateDimension :
            factLikeTable.getDegenerateDimensionsOrEmpty()) {
          if (degenerateDimension == null || Utils.isEmpty(degenerateDimension.getFieldName())) {
            continue;
          }
          TableItem item = new TableItem(wDegenerateDimensions.table, SWT.NONE);
          item.setText(1, degenerateDimension.getFieldName());
        }
        wDegenerateDimensions.optimizeTableView();
        refreshDegenerateDimensionComboChoices();
      }

      if (wRangeDimensionRoles != null) {
        wRangeDimensionRoles.clearAll();
        for (DmFactRangeDimensionRole rangeRole : factLikeTable.getRangeDimensionRolesOrEmpty()) {
          if (rangeRole == null || Utils.isEmpty(rangeRole.getRangeDimensionTableName())) {
            continue;
          }
          TableItem item = new TableItem(wRangeDimensionRoles.table, SWT.NONE);
          item.setText(1, Const.NVL(rangeRole.getRangeDimensionTableName(), ""));
          item.setText(2, Const.NVL(rangeRole.getSourceFieldName(), ""));
          item.setText(3, Const.NVL(rangeRole.getTargetFieldName(), ""));
        }
        wRangeDimensionRoles.optimizeTableView();
        refreshRangeDimensionSourceFieldChoices();
      }

      if (wJunkDimensionRoles != null) {
        wJunkDimensionRoles.clearAll();
        for (DmFactJunkDimensionRole junkRole : factLikeTable.getJunkDimensionRolesOrEmpty()) {
          if (junkRole == null || Utils.isEmpty(junkRole.getJunkDimensionTableName())) {
            continue;
          }
          TableItem item = new TableItem(wJunkDimensionRoles.table, SWT.NONE);
          item.setText(1, Const.NVL(junkRole.getJunkDimensionTableName(), ""));
          item.setText(2, Const.NVL(junkRole.getForeignKeyColumn(), ""));
        }
        wJunkDimensionRoles.optimizeTableView();
      }
    }

    if (range && input instanceof DmRangeDimension rangeDimension) {
      if (wFallBackLabel != null) {
        wFallBackLabel.setText(Const.NVL(rangeDimension.getFallBackLabel(), "unknown"));
      }
      if (wRangeBands != null) {
        wRangeBands.clearAll();
        for (DmRangeBand band : rangeDimension.getBandsOrEmpty()) {
          if (band == null) {
            continue;
          }
          TableItem item = new TableItem(wRangeBands.table, SWT.NONE);
          item.setText(1, Const.NVL(band.getLowerBound(), ""));
          item.setText(2, Const.NVL(band.getUpperBound(), ""));
          item.setText(3, Const.NVL(band.getLabel(), ""));
        }
        wRangeBands.optimizeTableView();
      }
    }
  }

  private void ok() {
    applyWidgetsToTable(input, model);
    ok = true;
    dispose();
  }

  private void validate() {
    try {
      List<ICheckResult> remarks =
          ModelDialogValidationSupport.runChecksWithBusyCursor(
              shell,
              () -> {
                DimensionalModel draft =
                    ModelDialogValidationSupport.cloneDimensionalModel(model, metadataProvider);
                IDmTable draftTable = locateDraftTable(draft);
                applyWidgetsToTable(draftTable, draft);
                List<ICheckResult> tableRemarks = new ArrayList<>();
                draftTable.check(tableRemarks, metadataProvider, variables, draft);
                return tableRemarks;
              });
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception ex) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"),
          BaseMessages.getString(
              ModelDialogValidationSupport.class,
              "ModelTableDialog.Validate.Error",
              ex.getMessage()),
          ex);
    }
  }

  private IDmTable locateDraftTable(DimensionalModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    return draft.getTables().get(originalTableIndex);
  }

  private void applyWidgetsToTable(IDmTable target, DimensionalModel contextModel) {
    target.setName(wName.getText());
    target.setDescription(wDescription.getText());
    if (!dimensionAlias && !range) {
      target.setTableName(wTableName.getText());
      target
          .getSourceOrDefault()
          .setSourceType(
              EnumDialogSupport.readCombo(wSourceType, DmSourceType.class, DmSourceType.SQL));
      target.getSourceOrDefault().setSourceConnection(wSourceConnection.getText());
      target.getSourceOrDefault().setSourceSql(wSourceSql.getText());
      target.getSourceOrDefault().setSourcePipelineFile(wSourcePipelineFile.getText());
      target.getSourceOrDefault().setSourcePipelineTransform(wSourcePipelineTransform.getText());
      target
          .getSourceOrDefault()
          .setSourcePipelineRunConfiguration(wSourcePipelineRunConfiguration.getText());
      target.getSourceOrDefault().setSourceCatalogConnection(wSourceCatalogConnection.getText());
      target.getSourceOrDefault().setSourceRecordNamespace(wSourceRecordNamespace.getText());
      target.getSourceOrDefault().setSourceRecordName(wSourceRecordName.getText());
      if (dateGeneratorWidgets != null
          && target.getSourceOrDefault().resolveSourceType() == DmSourceType.DATE_GENERATOR) {
        target
            .getSourceOrDefault()
            .setDateGenerator(DmSourceDateGeneratorGuiSupport.readData(dateGeneratorWidgets));
      }
    }

    if (range && target instanceof DmRangeDimension rangeDimension) {
      if (wFallBackLabel != null) {
        rangeDimension.setFallBackLabel(wFallBackLabel.getText());
      }
      rangeDimension.getBands().clear();
      if (wRangeBands != null) {
        for (TableItem item : wRangeBands.getNonEmptyItems()) {
          String lowerBound = item.getText(1);
          String upperBound = item.getText(2);
          String label = item.getText(3);
          if (Utils.isEmpty(label)) {
            continue;
          }
          rangeDimension.getBands().add(new DmRangeBand(lowerBound, upperBound, label));
        }
      }
    }

    if (junk && target instanceof DmJunkDimension junkDimension) {
      if (wSurrogateKeyStrategy != null) {
        junkDimension.setSurrogateKeyStrategy(
            EnumDialogSupport.readCombo(
                wSurrogateKeyStrategy,
                DmJunkSurrogateKeyStrategy.class,
                DmJunkSurrogateKeyStrategy.AUTO_INCREMENT));
      }
      if (wSurrogateKeyField != null) {
        junkDimension.setSurrogateKeyField(wSurrogateKeyField.getText());
      }
      if (wSurrogateKeySourceField != null) {
        junkDimension.setSurrogateKeySourceField(wSurrogateKeySourceField.getText());
      }
      if (wHashCodeStrategy != null) {
        junkDimension.setHashCodeStrategy(
            EnumDialogSupport.readCombo(
                wHashCodeStrategy,
                DmJunkHashCodeStrategy.class,
                DmJunkHashCodeStrategy.INTEGER_LEGACY));
      }
      boolean useSurrogateAsHash =
          wUseSurrogateKeyAsHashCodeField != null && wUseSurrogateKeyAsHashCodeField.getSelection();
      junkDimension.setUseSurrogateKeyAsHashCodeField(useSurrogateAsHash);
      if (useSurrogateAsHash) {
        junkDimension.setHashCodeField(null);
      } else if (wHashCodeField != null) {
        junkDimension.setHashCodeField(wHashCodeField.getText());
      }
      DmSourceType junkSourceType =
          EnumDialogSupport.readCombo(wSourceType, DmSourceType.class, DmSourceType.SQL);
      if (junkSourceType == DmSourceType.FACT_TABLE) {
        String factTableName = wSourceFactTable != null ? wSourceFactTable.getText() : null;
        DmJunkDimensionSupport.applyFactTableSource(junkDimension, factTableName);
      } else {
        DmJunkDimensionSupport.clearFactTableSource(junkDimension);
      }
      junkDimension.getKeyFields().clear();
      for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
        String fieldName = item.getText(1);
        if (!Utils.isEmpty(fieldName)) {
          junkDimension.getKeyFields().add(new DmNaturalKeyField(fieldName));
        }
      }
    }

    if (bridge && target instanceof DmBridge dmBridge) {
      dmBridge.getDimensionRefs().clear();
      for (TableItem item : wOutriggers.getNonEmptyItems()) {
        String dimensionName = item.getText(1);
        if (Utils.isEmpty(dimensionName)) {
          continue;
        }
        dmBridge.getDimensionRefs().add(new DmBridgeDimensionRef(dimensionName, item.getText(2)));
      }
    }

    if (dimensionAlias && target instanceof DmDimensionAlias alias) {
      if (wReferencedModelFilename != null) {
        alias.setReferencedModelFilename(wReferencedModelFilename.getText());
      }
      if (wReferencedDimension != null) {
        alias.setReferencedDimensionName(wReferencedDimension.getText());
      }
      alias.syncPhysicalTableName(contextModel, variables, metadataProvider);
      target.setTableName(alias.getTableName());
    }

    if (dimension && target instanceof DmDimension dmDimension) {
      dmDimension.setScdType(null);
      if (wSurrogateKeyStrategy != null) {
        dmDimension.setSurrogateKeyStrategy(
            EnumDialogSupport.readCombo(
                wSurrogateKeyStrategy,
                DmSurrogateKeyStrategy.class,
                DmSurrogateKeyStrategy.AUTO_INCREMENT));
      }
      if (wSurrogateKeyField != null) {
        dmDimension.setSurrogateKeyField(wSurrogateKeyField.getText());
      }
      if (wSurrogateKeySourceField != null) {
        dmDimension.setSurrogateKeySourceField(wSurrogateKeySourceField.getText());
      }
      dmDimension.getNaturalKeys().clear();
      for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
        String fieldName = item.getText(1);
        if (!Utils.isEmpty(fieldName)) {
          dmDimension.getNaturalKeys().add(new DmNaturalKeyField(fieldName));
        }
      }
      dmDimension.getAttributes().clear();
      for (TableItem item : wAttributes.getNonEmptyItems()) {
        String sourceFieldName = item.getText(1);
        String targetFieldName = item.getText(2);
        if (Utils.isEmpty(targetFieldName)) {
          continue;
        }
        if (Utils.isEmpty(sourceFieldName)) {
          sourceFieldName = targetFieldName;
        }
        DmScdUpdatePolicy policy =
            EnumDialogSupport.lookupText(
                item.getText(3), DmScdUpdatePolicy.class, DmScdUpdatePolicy.TYPE1);
        DmDimensionAttribute attribute = new DmDimensionAttribute(targetFieldName, policy);
        if (!sourceFieldName.equals(targetFieldName)) {
          attribute.setSourceFieldName(sourceFieldName);
        }
        if (!Utils.isEmpty(item.getText(4))) {
          attribute.setPreviousFieldName(item.getText(4));
        }
        dmDimension.getAttributes().add(attribute);
      }
      refreshDerivedLoadStrategyLabel(dmDimension);
      dmDimension.getOutriggers().clear();
      for (TableItem item : wOutriggers.getNonEmptyItems()) {
        String dimensionName = item.getText(1);
        if (Utils.isEmpty(dimensionName)) {
          continue;
        }
        dmDimension
            .getOutriggers()
            .add(new DmDimensionOutriggerRef(dimensionName, item.getText(2)));
      }
    }

    if (factLike && target instanceof DmTableBase table) {
      if (wDimensionLookupDateField != null) {
        table.setDimensionLookupDateField(wDimensionLookupDateField.getText());
      }
    }

    if (factLike && target instanceof IDmFactLikeTable factLikeTable) {
      List<DmFactDimensionRole> dimensionRoles = readDimensionRolesFromTable();
      List<DmFactMeasure> measures = readMeasuresFromTable();
      List<DmFactDegenerateDimension> degenerateDimensions = readDegenerateDimensionsFromTable();
      List<DmFactRangeDimensionRole> rangeDimensionRoles = readRangeDimensionRolesFromTable();
      List<DmFactJunkDimensionRole> junkDimensionRoles = readJunkDimensionRolesFromTable();
      if (factLikeTable instanceof DmFact dmFact) {
        dmFact.getDimensionRoles().clear();
        dmFact.getDimensionRoles().addAll(dimensionRoles);
        dmFact.getMeasures().clear();
        dmFact.getMeasures().addAll(measures);
        dmFact.getDegenerateDimensions().clear();
        dmFact.getDegenerateDimensions().addAll(degenerateDimensions);
        dmFact.getRangeDimensionRoles().clear();
        dmFact.getRangeDimensionRoles().addAll(rangeDimensionRoles);
        dmFact.getJunkDimensionRoles().clear();
        dmFact.getJunkDimensionRoles().addAll(junkDimensionRoles);
      } else if (factLikeTable instanceof DmFactlessFact factlessFact) {
        factlessFact.getDimensionRoles().clear();
        factlessFact.getDimensionRoles().addAll(dimensionRoles);
        factlessFact.getDegenerateDimensions().clear();
        factlessFact.getDegenerateDimensions().addAll(degenerateDimensions);
        factlessFact.getRangeDimensionRoles().clear();
        factlessFact.getRangeDimensionRoles().addAll(rangeDimensionRoles);
        factlessFact.getJunkDimensionRoles().clear();
        factlessFact.getJunkDimensionRoles().addAll(junkDimensionRoles);
      } else if (factLikeTable instanceof DmPeriodicSnapshotFact periodicFact) {
        periodicFact.getDimensionRoles().clear();
        periodicFact.getDimensionRoles().addAll(dimensionRoles);
        periodicFact.getMeasures().clear();
        periodicFact.getMeasures().addAll(measures);
        periodicFact.getDegenerateDimensions().clear();
        periodicFact.getDegenerateDimensions().addAll(degenerateDimensions);
        periodicFact.getRangeDimensionRoles().clear();
        periodicFact.getRangeDimensionRoles().addAll(rangeDimensionRoles);
        periodicFact.getJunkDimensionRoles().clear();
        periodicFact.getJunkDimensionRoles().addAll(junkDimensionRoles);
      } else if (factLikeTable instanceof DmAccumulatingSnapshotFact accumulatingFact) {
        accumulatingFact.getDimensionRoles().clear();
        accumulatingFact.getDimensionRoles().addAll(dimensionRoles);
        accumulatingFact.getMeasures().clear();
        accumulatingFact.getMeasures().addAll(measures);
        accumulatingFact.getDegenerateDimensions().clear();
        accumulatingFact.getDegenerateDimensions().addAll(degenerateDimensions);
        accumulatingFact.getRangeDimensionRoles().clear();
        accumulatingFact.getRangeDimensionRoles().addAll(rangeDimensionRoles);
        accumulatingFact.getJunkDimensionRoles().clear();
        accumulatingFact.getJunkDimensionRoles().addAll(junkDimensionRoles);
      } else if (factLikeTable instanceof DmAggregateFact aggregateFact) {
        aggregateFact.getDimensionRoles().clear();
        aggregateFact.getDimensionRoles().addAll(dimensionRoles);
        aggregateFact.getMeasures().clear();
        aggregateFact.getMeasures().addAll(measures);
        aggregateFact.getDegenerateDimensions().clear();
        aggregateFact.getDegenerateDimensions().addAll(degenerateDimensions);
        aggregateFact.getRangeDimensionRoles().clear();
        aggregateFact.getRangeDimensionRoles().addAll(rangeDimensionRoles);
        aggregateFact.getJunkDimensionRoles().clear();
        aggregateFact.getJunkDimensionRoles().addAll(junkDimensionRoles);
      }
    }
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }

  private void previewSourceSql() {
    DmSourceSqlGuiSupport.previewSourceSql(
        shell, variables, metadataProvider, resolveSourceDatabaseMeta(), wSourceSql.getText());
  }

  private void getNaturalKeysFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      applyFieldNamesToTable(wNaturalKeys, naturalKeyFieldColumn, fieldNames, null);
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private void getJunkKeyFieldsFromSource() {
    try {
      List<String> fieldNames = loadJunkKeySourceFieldNames();
      applyFieldNamesToTable(wNaturalKeys, naturalKeyFieldColumn, fieldNames, null);
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private List<String> loadJunkKeySourceFieldNames() throws HopException {
    if (isFactTableSourceSelected()) {
      String factTableName = wSourceFactTable != null ? wSourceFactTable.getText() : null;
      if (Utils.isEmpty(factTableName)) {
        throw new HopException(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.JunkKeyFields.GetFields.NoFactTable"));
      }
      IDmFactLikeTable fact =
          DmJunkDimensionSupport.resolveFactTable(model, factTableName, variables);
      if (fact == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "HopGuiDmTableDialog.JunkKeyFields.GetFields.UnknownFactTable",
                factTableName));
      }
      List<String> fieldNames =
          DmSourceFieldResolutionSupport.tryResolveFieldNames(
              metadataProvider, variables, model, fact);
      if (fieldNames.isEmpty()) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "HopGuiDmTableDialog.JunkKeyFields.GetFields.NoFields", factTableName));
      }
      return fieldNames;
    }
    return loadSourceFieldNames();
  }

  private void getMeasuresFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      applyFieldNamesToTable(wMeasures, measureFieldColumn, fieldNames, "Y");
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private void getDegenerateDimensionsFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      Set<String> reserved = collectReservedDegenerateDimensionSourceFields();
      List<String> degenerateFields = new ArrayList<>();
      for (String fieldName : fieldNames) {
        if (!Utils.isEmpty(fieldName) && !reserved.contains(fieldName)) {
          degenerateFields.add(fieldName);
        }
      }
      applyFieldNamesToTable(
          wDegenerateDimensions, degenerateDimensionFieldColumn, degenerateFields, null);
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private Set<String> collectReservedDegenerateDimensionSourceFields() {
    Set<String> reserved = new LinkedHashSet<>();
    if (wMeasures != null) {
      for (TableItem item : wMeasures.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(1))) {
          reserved.add(item.getText(1));
        }
      }
    }
    if (wDimensionRoles != null) {
      for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(2))) {
          reserved.add(item.getText(2));
        }
        if (!Utils.isEmpty(item.getText(3))) {
          reserved.add(item.getText(3));
        }
      }
    }
    if (input instanceof DmPeriodicSnapshotFact periodicSnapshotFact
        && !Utils.isEmpty(periodicSnapshotFact.getSnapshotDateField())) {
      reserved.add(periodicSnapshotFact.getSnapshotDateField());
    }
    if (input instanceof DmAccumulatingSnapshotFact accumulatingSnapshotFact) {
      for (DmNaturalKeyField grainKey : accumulatingSnapshotFact.getGrainKeysOrEmpty()) {
        if (grainKey != null && !Utils.isEmpty(grainKey.getFieldName())) {
          reserved.add(grainKey.getFieldName());
        }
      }
    }
    if (input instanceof DmTableBase table && !Utils.isEmpty(table.getDimensionLookupDateField())) {
      reserved.add(table.getDimensionLookupDateField());
    }
    return reserved;
  }

  private List<DmFactMeasure> readMeasuresFromTable() {
    List<DmFactMeasure> measures = new ArrayList<>();
    if (factless || wMeasures == null) {
      return measures;
    }
    for (TableItem item : wMeasures.getNonEmptyItems()) {
      String fieldName = item.getText(1);
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      boolean additive = !"N".equalsIgnoreCase(item.getText(2));
      measures.add(new DmFactMeasure(fieldName, additive));
    }
    return measures;
  }

  private List<DmFactDegenerateDimension> readDegenerateDimensionsFromTable() {
    List<DmFactDegenerateDimension> degenerateDimensions = new ArrayList<>();
    if (wDegenerateDimensions == null) {
      return degenerateDimensions;
    }
    for (TableItem item : wDegenerateDimensions.getNonEmptyItems()) {
      String fieldName = item.getText(1);
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      degenerateDimensions.add(new DmFactDegenerateDimension(fieldName));
    }
    return degenerateDimensions;
  }

  private List<DmFactJunkDimensionRole> readJunkDimensionRolesFromTable() {
    List<DmFactJunkDimensionRole> junkDimensionRoles = new ArrayList<>();
    if (wJunkDimensionRoles == null) {
      return junkDimensionRoles;
    }
    for (TableItem item : wJunkDimensionRoles.getNonEmptyItems()) {
      String junkDimensionName = item.getText(1);
      String foreignKeyColumn = item.getText(2);
      if (Utils.isEmpty(junkDimensionName)) {
        continue;
      }
      junkDimensionRoles.add(new DmFactJunkDimensionRole(junkDimensionName, foreignKeyColumn));
    }
    return junkDimensionRoles;
  }

  private List<DmFactRangeDimensionRole> readRangeDimensionRolesFromTable() {
    List<DmFactRangeDimensionRole> rangeDimensionRoles = new ArrayList<>();
    if (wRangeDimensionRoles == null) {
      return rangeDimensionRoles;
    }
    for (TableItem item : wRangeDimensionRoles.getNonEmptyItems()) {
      String rangeDimensionName = item.getText(1);
      String sourceFieldName = item.getText(2);
      String targetFieldName = item.getText(3);
      if (Utils.isEmpty(rangeDimensionName)) {
        continue;
      }
      rangeDimensionRoles.add(
          new DmFactRangeDimensionRole(rangeDimensionName, sourceFieldName, targetFieldName));
    }
    return rangeDimensionRoles;
  }

  private void refreshRangeDimensionSourceFieldChoices() {
    if (rangeDimensionSourceFieldColumn == null) {
      return;
    }
    try {
      List<String> fieldNames = loadSourceFieldNames();
      rangeDimensionSourceFieldColumn.setComboValues(
          ConstUi.sortFieldNames(fieldNames.toArray(new String[0])));
    } catch (HopException ignored) {
      rangeDimensionSourceFieldColumn.setComboValues(new String[] {""});
    }
  }

  private void suggestRangeDimensionRolesFromSource() {
    if (wRangeDimensionRoles == null) {
      return;
    }
    try {
      List<String> fieldNames = loadSourceFieldNames();
      refreshRangeDimensionSourceFieldChoices();
      Set<String> usedTargets = new LinkedHashSet<>();
      for (TableItem item : wRangeDimensionRoles.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(3))) {
          usedTargets.add(item.getText(3));
        }
      }
      Set<String> reserved = collectReservedDegenerateDimensionSourceFields();
      if (wRangeDimensionRoles != null) {
        for (TableItem item : wRangeDimensionRoles.getNonEmptyItems()) {
          if (!Utils.isEmpty(item.getText(2))) {
            reserved.add(item.getText(2));
          }
          if (!Utils.isEmpty(item.getText(3))) {
            reserved.add(item.getText(3));
          }
        }
      }
      org.apache.hop.core.row.IRowMeta sourceRowMeta = null;
      if (input instanceof DmTableBase factTable) {
        sourceRowMeta =
            DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
                metadataProvider, variables, model, factTable);
      }
      for (String fieldName : fieldNames) {
        if (Utils.isEmpty(fieldName)
            || reserved.contains(fieldName)
            || usedTargets.contains(fieldName)) {
          continue;
        }
        if (sourceRowMeta != null) {
          org.apache.hop.core.row.IValueMeta valueMeta = sourceRowMeta.searchValueMeta(fieldName);
          if (valueMeta != null && !valueMeta.isNumeric()) {
            continue;
          }
        }
        String targetFieldName = fieldName + "_band";
        TableItem item = new TableItem(wRangeDimensionRoles.table, SWT.NONE);
        item.setText(2, fieldName);
        item.setText(3, targetFieldName);
        usedTargets.add(targetFieldName);
      }
      wRangeDimensionRoles.optimizeTableView();
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private void getJoinsFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      Set<String> measureFields = new LinkedHashSet<>();
      if (wMeasures != null) {
        for (TableItem item : wMeasures.getNonEmptyItems()) {
          if (!Utils.isEmpty(item.getText(1))) {
            measureFields.add(item.getText(1));
          }
        }
      }
      if (dimensionJoinSourceFieldColumn != null) {
        dimensionJoinSourceFieldColumn.setComboValues(
            ConstUi.sortFieldNames(fieldNames.toArray(new String[0])));
      }
      wDimensionRoles.clearAll();
      for (String fieldName : fieldNames) {
        if (Utils.isEmpty(fieldName)
            || measureFields.contains(fieldName)
            || fieldName.toLowerCase().endsWith("_key")) {
          continue;
        }
        String dimensionName = inferDimensionNameForSourceField(fieldName);
        if (!isKnownDimensionName(dimensionName)) {
          continue;
        }
        TableItem item = new TableItem(wDimensionRoles.table, SWT.NONE);
        item.setText(1, dimensionName);
        item.setText(2, fieldName);
        item.setText(3, defaultForeignKeyForDimensionName(dimensionName));
        item.setText(4, looksLikeDateSourceField(fieldName) ? "Y" : "N");
        item.setText(5, "N");
        DmFactDimensionRole suggestedRole = new DmFactDimensionRole();
        suggestedRole.setDimensionTableName(dimensionName);
        suggestedRole.setSourceFieldName(fieldName);
        applySourceHashKeyJoinDefaults(suggestedRole, dimensionName);
        item.setText(6, formatSkipDimensionLookup(suggestedRole));
        if (!Utils.isEmpty(suggestedRole.getSourceFieldName())) {
          item.setText(2, suggestedRole.getSourceFieldName());
        }
      }
      wDimensionRoles.optimizeTableView();
      refreshDimensionJoinComboChoices();
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private void getAttributesFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      List<String> naturalKeyNames = readTableFieldNames(wNaturalKeys);
      List<String> attributeFields = new ArrayList<>();
      for (String fieldName : fieldNames) {
        if (!naturalKeyNames.contains(fieldName)) {
          attributeFields.add(fieldName);
        }
      }
      applyAttributeFieldNamesFromSource(attributeFields);
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private List<String> loadSourceFieldNames() throws HopException {
    if (isPipelineSourceSelected()) {
      return DmSourcePipelineGuiSupport.resolveFieldNames(
          variables,
          metadataProvider,
          wSourcePipelineFile.getText(),
          wSourcePipelineTransform.getText());
    }
    DatabaseMeta databaseMeta = resolveSourceDatabaseMeta();
    return DmSourceSqlGuiSupport.resolveFieldNames(variables, databaseMeta, wSourceSql.getText());
  }

  private void applyFieldNamesToTable(
      TableView tableView,
      ColumnInfo fieldColumn,
      List<String> fieldNames,
      String defaultSecondColumnValue) {
    if (fieldColumn != null) {
      fieldColumn.setComboValues(ConstUi.sortFieldNames(fieldNames.toArray(new String[0])));
    }
    tableView.clearAll();
    for (String fieldName : fieldNames) {
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      TableItem item = new TableItem(tableView.table, SWT.NONE);
      item.setText(1, fieldName);
      if (!Utils.isEmpty(defaultSecondColumnValue)) {
        item.setText(2, defaultSecondColumnValue);
      }
    }
    tableView.optimizeTableView();
  }

  private static List<String> readTableFieldNames(TableView tableView) {
    List<String> names = new ArrayList<>();
    for (TableItem item : tableView.getNonEmptyItems()) {
      String fieldName = item.getText(1);
      if (!Utils.isEmpty(fieldName)) {
        names.add(fieldName);
      }
    }
    return names;
  }

  private List<DmFactDimensionRole> readDimensionRolesFromTable() {
    List<DmFactDimensionRole> roles = new ArrayList<>();
    for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
      String dimensionName = item.getText(1);
      if (Utils.isEmpty(dimensionName)) {
        continue;
      }
      DmFactDimensionRole role = new DmFactDimensionRole();
      role.setDimensionTableName(dimensionName);
      role.setSourceFieldName(item.getText(2));
      role.setForeignKeyColumn(item.getText(3));
      role.setTruncateToDateKey("Y".equalsIgnoreCase(item.getText(4)));
      role.setPreloadLookupCache("Y".equalsIgnoreCase(item.getText(5)));
      applySkipDimensionLookupFromCombo(role, item.getText(6));
      roles.add(role);
    }
    return roles;
  }

  private String formatSkipDimensionLookup(DmFactDimensionRole role) {
    if (role == null) {
      return SKIP_LOOKUP_AUTO;
    }
    if (role.isSkipDimensionLookup()) {
      return SKIP_LOOKUP_YES;
    }
    if (role.isForceDimensionLookup()) {
      return SKIP_LOOKUP_NO;
    }
    return SKIP_LOOKUP_AUTO;
  }

  private void applySkipDimensionLookupFromCombo(DmFactDimensionRole role, String value) {
    if (role == null) {
      return;
    }
    if (SKIP_LOOKUP_YES.equalsIgnoreCase(value)) {
      role.setSkipDimensionLookup(true);
      role.setForceDimensionLookup(false);
      return;
    }
    if (SKIP_LOOKUP_NO.equalsIgnoreCase(value)) {
      role.setSkipDimensionLookup(false);
      role.setForceDimensionLookup(true);
      return;
    }
    role.setSkipDimensionLookup(false);
    role.setForceDimensionLookup(false);
  }

  private void applySourceHashKeyJoinDefaults(DmFactDimensionRole role, String dimensionName) {
    if (role == null || model == null || Utils.isEmpty(dimensionName)) {
      return;
    }
    DmDimension dimension =
        DmDimensionResolutionSupport.resolveDimension(
            model, dimensionName, variables, metadataProvider);
    if (dimension == null
        || DmSurrogateKeySupport.resolveStrategy(dimension)
            != DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      return;
    }
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    String surrogateSource =
        DmSurrogateKeySupport.resolveSurrogateKeySourceField(dimension, config, variables);
    if (!Utils.isEmpty(surrogateSource)) {
      role.setSourceFieldName(surrogateSource);
    }
  }

  private String defaultForeignKeyForDimensionName(String dimensionName) {
    DmDimension dimension =
        model != null
            ? DmDimensionResolutionSupport.resolveDimension(model, dimensionName, variables)
            : null;
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    DmFactDimensionRole tempRole = new DmFactDimensionRole();
    tempRole.setDimensionTableName(dimensionName);
    return DmLayoutSupport.defaultFactForeignKeyColumn(dimension, tempRole, config, variables);
  }

  private boolean isKnownDimensionName(String dimensionName) {
    if (model == null || Utils.isEmpty(dimensionName)) {
      return false;
    }
    return DmDimensionResolutionSupport.isDimensionLike(model, dimensionName);
  }

  private static String inferDimensionNameForSourceField(String fieldName) {
    return "dim_" + fieldName;
  }

  private static boolean looksLikeDateSourceField(String fieldName) {
    return fieldName != null && fieldName.toLowerCase().contains("date");
  }

  private void refreshDimensionJoinComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    if (wDimensionRoles != null) {
      for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(2))) {
          choices.add(item.getText(2));
        }
      }
    }
    if (dimensionJoinSourceFieldColumn != null) {
      dimensionJoinSourceFieldColumn.setComboValues(
          ConstUi.sortFieldNames(choices.toArray(new String[0])));
    }
    refreshDimensionLookupDateComboChoices(choices);
  }

  private void refreshDimensionLookupDateComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    refreshDimensionLookupDateComboChoices(choices);
  }

  private void refreshDimensionLookupDateComboChoices(Set<String> choices) {
    if (wDimensionLookupDateField == null) {
      return;
    }
    if (input instanceof DmTableBase table && !Utils.isEmpty(table.getDimensionLookupDateField())) {
      choices.add(table.getDimensionLookupDateField());
    }
    String current = wDimensionLookupDateField.getText();
    wDimensionLookupDateField.setItems(ConstUi.sortFieldNames(choices.toArray(new String[0])));
    if (!Utils.isEmpty(current)) {
      wDimensionLookupDateField.setText(current);
    }
  }

  private void refreshDegenerateDimensionComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    if (wDegenerateDimensions != null) {
      for (TableItem item : wDegenerateDimensions.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(1))) {
          choices.add(item.getText(1));
        }
      }
    }
    if (degenerateDimensionFieldColumn != null) {
      degenerateDimensionFieldColumn.setComboValues(
          ConstUi.sortFieldNames(choices.toArray(new String[0])));
    }
  }

  private void refreshMeasureComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    if (wMeasures != null) {
      for (TableItem item : wMeasures.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(1))) {
          choices.add(item.getText(1));
        }
      }
    }
    if (measureFieldColumn != null) {
      measureFieldColumn.setComboValues(ConstUi.sortFieldNames(choices.toArray(new String[0])));
    }
  }

  private void refreshFieldComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    if (wNaturalKeys != null) {
      for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(1))) {
          choices.add(item.getText(1));
        }
      }
    }
    if (wAttributes != null) {
      for (TableItem item : wAttributes.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(1))) {
          choices.add(item.getText(1));
        }
        if (!Utils.isEmpty(item.getText(2))) {
          choices.add(item.getText(2));
        }
      }
    }
    String[] sorted = ConstUi.sortFieldNames(choices.toArray(new String[0]));
    if (naturalKeyFieldColumn != null) {
      naturalKeyFieldColumn.setComboValues(sorted);
    }
    if (attributeSourceFieldColumn != null) {
      attributeSourceFieldColumn.setComboValues(sorted);
    }
    if (attributeTargetFieldColumn != null) {
      attributeTargetFieldColumn.setComboValues(sorted);
    }
    refreshDimensionJoinComboChoices();
  }

  private void applyAttributeFieldNamesFromSource(List<String> attributeFields) {
    String[] sorted = ConstUi.sortFieldNames(attributeFields.toArray(new String[0]));
    if (attributeSourceFieldColumn != null) {
      attributeSourceFieldColumn.setComboValues(sorted);
    }
    if (attributeTargetFieldColumn != null) {
      attributeTargetFieldColumn.setComboValues(sorted);
    }
    wAttributes.clearAll();
    String defaultPolicy = EnumDialogSupport.descriptionOf(DmScdUpdatePolicy.TYPE1);
    for (String fieldName : attributeFields) {
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      TableItem item = new TableItem(wAttributes.table, SWT.NONE);
      item.setText(1, fieldName);
      item.setText(2, fieldName);
      item.setText(3, defaultPolicy);
    }
    wAttributes.optimizeTableView();
    refreshDerivedLoadStrategyFromUi();
  }

  private void refreshDerivedLoadStrategyLabel(DmDimension dimension) {
    if (wDerivedLoadStrategy == null || dimension == null) {
      return;
    }
    dimension.normalizeLegacyScdType();
    wDerivedLoadStrategy.setText(DmDimensionLoadStrategySupport.resolveDisplayLabel(dimension));
  }

  private void refreshDerivedLoadStrategyFromUi() {
    if (wDerivedLoadStrategy == null) {
      return;
    }
    DmDimension preview = new DmDimension();
    for (TableItem item : wAttributes.getNonEmptyItems()) {
      String targetFieldName = item.getText(2);
      if (Utils.isEmpty(targetFieldName)) {
        continue;
      }
      String sourceFieldName = item.getText(1);
      if (Utils.isEmpty(sourceFieldName)) {
        sourceFieldName = targetFieldName;
      }
      DmScdUpdatePolicy policy =
          EnumDialogSupport.lookupText(
              item.getText(3), DmScdUpdatePolicy.class, DmScdUpdatePolicy.TYPE1);
      DmDimensionAttribute attribute = new DmDimensionAttribute(targetFieldName, policy);
      if (!sourceFieldName.equals(targetFieldName)) {
        attribute.setSourceFieldName(sourceFieldName);
      }
      preview.getAttributes().add(attribute);
    }
    wDerivedLoadStrategy.setText(DmDimensionLoadStrategySupport.resolveDisplayLabel(preview));
  }

  private void showSourceFieldError(HopException exception) {
    new ErrorDialog(
        shell,
        BaseMessages.getString(Props.class, "System.Dialog.Error.Title"),
        exception.getMessage(),
        exception);
  }
}
