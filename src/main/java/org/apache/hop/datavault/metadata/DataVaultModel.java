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
 *
 */

package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.base.AbstractMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.IUndo;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.undo.ChangeAction;
import org.apache.hop.core.reflection.StringSearchResult;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.metadata.coaching.ModelCoachingConfiguration;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.jspecify.annotations.NonNull;

/**
 * A named Data Vault 2.0 model.
 *
 * <p>This aggregates references to Hubs, Links and Satellites (via {@link IDvTable}) that belong
 * together in one enterprise data warehouse (or a subject area / release of it).
 *
 * <p>Each table in the unified {@link #getTables()} list carries its own type ({@link
 * IDvTable#getTableType()}) and full definition. The model provides the "big picture" and a single
 * place to attach a default DataVaultConfiguration.
 *
 * <p>Sources ({@link IDvSource}) describe the incoming systems (Database connections + expected row
 * layouts today). They are used by generators / transforms to know where data originates and what
 * columns to expect.
 *
 * <p>Implements IChanged (like PipelineMeta) for dirty state tracking in the visual modeler.
 */
@GuiPlugin
@Getter
@Setter
public class DataVaultModel extends HopMetadataBase
    implements IHopMetadata, IChanged, IHasName, IHasFilename, IUndo {

  private static final Class<?> PKG = DataVaultModel.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_MODEL_DIALOG";

  /**
   * Runtime open path ({@link IHasFilename}), like {@code AbstractMeta}. Never serialized — loaders
   * bind this from the VFS path used to open/save (stale host paths must not land in git).
   */
  private String filename;

  /**
   * Whether the name should be kept in sync with the filename (derived via
   * extractNameFromFilename). Default true, like AbstractMetaInfo.
   */
  @HopMetadataProperty(key = "name_sync_with_filename")
  private boolean nameSynchronizedWithFilename = true;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultModel.Description.Label",
      toolTip = "i18n::DataVaultModel.Description.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String description;

  /**
   * Configuration for hashing, target database and physical strategy used by objects in this model.
   */
  @HopMetadataProperty(key = "configuration")
  private DataVaultConfiguration configuration = new DataVaultConfiguration();

  /** Curated coaching sources for the model coach panel. */
  @HopMetadataProperty private ModelCoachingConfiguration coaching;

  /** Returns the embedded configuration, or a new default instance if none is set. */
  public DataVaultConfiguration getConfigurationOrDefault() {
    if (configuration == null) {
      configuration = new DataVaultConfiguration();
    }
    return configuration;
  }

  public ModelCoachingConfiguration getCoachingOrDefault() {
    if (coaching == null) {
      coaching = ModelCoachingConfiguration.createEmpty();
    }
    return coaching;
  }

  /**
   * All tables (Hubs, Links and Satellites) that belong to this Data Vault model. Each table knows
   * its own type via {@link IDvTable#getTableType()}. Stored using storeWithName so that the model
   * holds references to the individual table metadata objects (rather than inlining them).
   */
  // GuiElementType.LIST is not available; lists are handled by serialization and the metadata
  // perspective.
  @HopMetadataProperty(key = "table", groupKey = "tables")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<IDvTable> tables = new ArrayList<>();

  /**
   * Typed annotation notes on the visual model canvas. Documentation only; does not affect DV
   * Update or DDL generation.
   */
  @HopMetadataProperty(key = "note", groupKey = "notes")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<DvNote> notes = new ArrayList<>();

  protected final ChangedFlag changedFlag = new ChangedFlag();

  public DataVaultModel() {
    super();
    ensureLists();
  }

  public DataVaultModel(String name) {
    this.name = name;
    ensureLists();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DataVaultModel that)) return false;
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(getName(), that.getName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getName());
  }

  /**
   * All hubs, links and satellites in this model. Never {@code null} (may be empty). {@link
   * #setTables(List)} normalizes {@code null} to an empty list for XML deserialization safety.
   */
  public @NonNull List<IDvTable> getTables() {
    if (tables == null) {
      tables = new ArrayList<>();
    }
    return tables;
  }

  public void setTables(List<IDvTable> tables) {
    this.tables = tables != null ? tables : new ArrayList<>();
  }

  /**
   * Canvas annotation notes. Never {@code null} (can be empty). {@link #setNotes(List)} normalizes
   * {@code null} to an empty list for XML deserialization safety.
   */
  public @NonNull List<DvNote> getNotes() {
    if (notes == null) {
      notes = new ArrayList<>();
    }
    return notes;
  }

  public void setNotes(List<DvNote> notes) {
    this.notes = notes != null ? notes : new ArrayList<>();
  }

  private void ensureLists() {
    setTables(tables);
    setNotes(notes);
  }

  @Override
  public String getName() {
    // Derive from filename basename (without extension) if synchronized, exactly like PipelineMeta.
    // Uses AbstractMeta.extractNameFromFilename() .
    return AbstractMeta.extractNameFromFilename(
        nameSynchronizedWithFilename, name, filename, ".hdv");
  }

  private record DefinedTableNames(Set<String> hubNames, Set<String> linkNames) {}

  /**
   * Perform a series of validation checks on this Data Vault model and return the results.
   *
   * @return list of check results (errors, warnings, ok)
   */
  public List<ICheckResult> check(IHopMetadataProvider metadataProvider, IVariables variables) {
    return check(metadataProvider, variables, DvModelCheckOptions.defaults());
  }

  public List<ICheckResult> check(
      IHopMetadataProvider metadataProvider, IVariables variables, DvModelCheckOptions options) {
    List<ICheckResult> remarks = new ArrayList<>();

    List<DataVaultSource> sources =
        loadDataVaultSources(metadataProvider, variables, remarks);
    checkTargetDatabase(remarks, metadataProvider, variables);
    checkTargetLoadMode(remarks, metadataProvider);
    checkTargetLoadModeGuidance(remarks, variables, metadataProvider);
    checkTargetLoadingIntegerSettings(remarks, variables);
    checkTablesPresent(remarks);
    checkTables(remarks, metadataProvider, variables, collectDefinedHubAndLinkNames(), options);
    checkDuplicateTableNames(remarks);
    checkDuplicateTargetTableNames(remarks);
    checkDuplicateStsTableNames(remarks, variables);
    checkDuplicateSourceNames(remarks, sources);

    return remarks;
  }

  private List<DataVaultSource> loadDataVaultSources(
      IHopMetadataProvider metadataProvider, IVariables variables, List<ICheckResult> remarks) {
    try {
      return DvSourceCatalogService.listSources(
          DvSourceCatalogService.resolveCatalogConnection(this, variables, metadataProvider),
          variables,
          metadataProvider);
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DataVaultModel.CheckResult.ErrorLoadingMetadata",
                  describeCatalogLoadFailure(e)),
              null));
      return new ArrayList<>();
    }
  }

  private static String describeCatalogLoadFailure(Exception exception) {
    Throwable current = exception;
    String message = null;
    while (current != null) {
      if (!Utils.isEmpty(current.getMessage())) {
        message = current.getMessage();
        if (current instanceof HopException) {
          break;
        }
      }
      current = current.getCause();
    }
    if (!Utils.isEmpty(message)) {
      return message;
    }
    return exception.getClass().getSimpleName();
  }

  private void checkTargetLoadMode(
      List<ICheckResult> remarks, IHopMetadataProvider metadataProvider) {
    DataVaultConfiguration config = getConfigurationOrDefault();
    DvTargetLoadMode mode = config.resolveTargetLoadMode();
    if (mode == DvTargetLoadMode.TABLE_OUTPUT) {
      return;
    }
    org.apache.hop.core.database.DatabaseMeta targetDatabase = null;
    try {
      targetDatabase = DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
    } catch (HopException e) {
      // Target database validation is reported separately in checkTargetDatabase().
      return;
    }
    if (!DvBulkLoadPluginSupport.isModeAvailable(targetDatabase, mode)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DataVaultModel.CheckResult.TargetLoadModeUnavailable",
                  mode.getDescription(),
                  targetDatabase != null ? targetDatabase.getPluginId() : ""),
              null));
    }
  }

  private void checkTargetLoadModeGuidance(
      List<ICheckResult> remarks, IVariables variables, IHopMetadataProvider metadataProvider) {
    DataVaultConfiguration config = getConfigurationOrDefault();
    DvTargetLoadMode mode = config.resolveTargetLoadMode();
    if (mode == DvTargetLoadMode.TABLE_OUTPUT) {
      return;
    }

    org.apache.hop.core.database.DatabaseMeta targetDatabase = null;
    try {
      targetDatabase = DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
    } catch (HopException e) {
      return;
    }

    int parallelCopies = resolveTargetTableParallelCopies(config, variables);
    if (mode == DvTargetLoadMode.NATIVE_BULK && parallelCopies > 1) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DataVaultModel.CheckResult.TargetLoadNativeBulkIgnoresParallelCopies",
                  parallelCopies),
              null));
    }

    if (mode == DvTargetLoadMode.STAGING_FILE) {
      if (config.isBulkLoadLocalFileRequired()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "DataVaultModel.CheckResult.TargetLoadStagingLocalFileRequired"),
                null));
      }
    }

    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_COMMENT,
            BaseMessages.getString(
                PKG,
                "DataVaultModel.CheckResult.TargetLoadHashKeyPartitioningTableOutputOnly",
                mode.getDescription()),
            null));
  }

  private static int resolveTargetTableParallelCopies(
      DataVaultConfiguration config, IVariables variables) {
    try {
      return DvIntegerSettingValidationSupport.requirePositiveInteger(
          config.getTargetTableParallelCopies(),
          variables,
          DataVaultConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES,
          BaseMessages.getString(
              DvIntegerSettingValidationSupport.class,
              "DvIntegerSettingValidation.Label.TargetTableParallelCopies"));
    } catch (HopException e) {
      return 1;
    }
  }

  private void checkTargetLoadingIntegerSettings(
      List<ICheckResult> remarks, IVariables variables) {
    DataVaultConfiguration config = getConfigurationOrDefault();
    for (DvIntegerSettingValidationSupport.IntegerSettingValidation validation :
        DvIntegerSettingValidationSupport.validateModelPipelineIntegerSettings(config, variables)) {
      if (!validation.isValid()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR, validation.errorMessage(), null));
      }
    }
  }

  private void checkTargetDatabase(
      List<ICheckResult> remarks, IHopMetadataProvider metadataProvider, IVariables variables) {
    DataVaultConfiguration config = getConfigurationOrDefault();
    if (Utils.isEmpty(config.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DataVaultModel.CheckResult.NoTargetDatabase"),
              null));
      return;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK,
            BaseMessages.getString(
                PKG, "DataVaultModel.CheckResult.HasTargetDatabase", config.getTargetDatabase()),
            null));
    if (metadataProvider == null) {
      return;
    }
    try {
      org.apache.hop.core.database.DatabaseMeta targetDatabase =
          DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
      DvTargetUnicodeCapabilitySupport.checkTargetUnicodeCapability(
          remarks, targetDatabase, variables, config.getTargetDatabase());
    } catch (HopException e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DataVaultModel.CheckResult.TargetDatabaseLoadFailed",
                  config.getTargetDatabase(),
                  Const.NVL(e.getMessage(), e.getClass().getSimpleName())),
              null));
    }
  }

  private void checkTablesPresent(List<ICheckResult> remarks) {
    if (getTables().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "DataVaultModel.CheckResult.NoTables"),
              null));
    }
  }

  private DefinedTableNames collectDefinedHubAndLinkNames() {
    Set<String> hubNames = new HashSet<>();
    Set<String> linkNames = new HashSet<>();
    for (IDvTable table : getTables()) {
      if (Utils.isEmpty(table.getName())) {
        continue;
      }
      if (table.getTableType() == DvTableType.HUB) {
        hubNames.add(table.getName());
      } else if (table.getTableType() == DvTableType.LINK) {
        linkNames.add(table.getName());
      } else if (table instanceof DvTableReference reference) {
        DvTableType referencedType = reference.getReferencedTableType();
        if (referencedType == DvTableType.HUB) {
          hubNames.add(table.getName());
        } else if (referencedType == DvTableType.LINK) {
          linkNames.add(table.getName());
        }
      }
    }
    return new DefinedTableNames(hubNames, linkNames);
  }

  private void checkTables(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DefinedTableNames definedNames,
      DvModelCheckOptions options) {
    for (IDvTable table : getTables()) {
      table.check(remarks, metadataProvider, variables, options, this);
      if (table instanceof DvSatellite satellite) {
        checkSatelliteReferences(remarks, satellite, definedNames);
      } else if (table instanceof DvLink link) {
        checkLinkReferences(remarks, link, definedNames.hubNames());
      }
    }
  }

  private void checkSatelliteReferences(
      List<ICheckResult> remarks, DvSatellite satellite, DefinedTableNames definedNames) {
    if (!Utils.isEmpty(satellite.getHubName())
        && !definedNames.hubNames().contains(satellite.getHubName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DataVaultModel.CheckResult.MissingHub", satellite.getHubName()),
              satellite));
    }
    if (!Utils.isEmpty(satellite.getLinkName())
        && !definedNames.linkNames().contains(satellite.getLinkName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DataVaultModel.CheckResult.MissingLink", satellite.getLinkName()),
              satellite));
    }
  }

  private void checkLinkReferences(
      List<ICheckResult> remarks, DvLink link, Set<String> definedHubNames) {
    for (String hubName : link.getHubNames()) {
      if (!Utils.isEmpty(hubName) && !definedHubNames.contains(hubName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "DataVaultModel.CheckResult.MissingHub", hubName),
                link));
      }
    }
  }

  private void checkDuplicateTableNames(List<ICheckResult> remarks) {
    checkDuplicateNames(remarks, getTables(), "DataVaultModel.CheckResult.DuplicateTableName");
  }

  private void checkDuplicateTargetTableNames(List<ICheckResult> remarks) {
    Map<String, Integer> targetTableNameCount = new HashMap<>();
    for (IDvTable table : getTables()) {
      if (table == null) {
        continue;
      }
      // Hub/link aliases (TABLE_REFERENCE) intentionally share the physical target table of the
      // referenced hub; they do not create a second physical table and must not fail model check.
      if (table.getTableType() == DvTableType.TABLE_REFERENCE) {
        continue;
      }
      String targetTableName =
          !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
      if (Utils.isEmpty(targetTableName)) {
        continue;
      }
      targetTableNameCount.merge(targetTableName, 1, Integer::sum);
    }
    for (Map.Entry<String, Integer> entry : targetTableNameCount.entrySet()) {
      if (entry.getValue() > 1) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DataVaultModel.CheckResult.DuplicateTargetTableName", entry.getKey()),
                null));
      }
    }
  }

  private void checkDuplicateStsTableNames(List<ICheckResult> remarks, IVariables variables) {
    Map<String, Integer> stsNameCount = new HashMap<>();
    for (IDvTable table : getTables()) {
      if (!(table instanceof DvSatellite satellite) || !satellite.isStatusTrackingEnabled()) {
        continue;
      }
      String stsTable = satellite.resolveStatusTableName(variables, this);
      if (Utils.isEmpty(stsTable)) {
        continue;
      }
      stsNameCount.merge(stsTable, 1, Integer::sum);
    }
    for (Map.Entry<String, Integer> entry : stsNameCount.entrySet()) {
      if (entry.getValue() > 1) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DataVaultModel.CheckResult.DuplicateStsTableName", entry.getKey()),
                null));
      }
    }
  }

  private void checkDuplicateSourceNames(
      List<ICheckResult> remarks, List<DataVaultSource> sources) {
    checkDuplicateNames(remarks, sources, "DataVaultModel.CheckResult.DuplicateSourceName");
  }

  private void checkDuplicateNames(
      List<ICheckResult> remarks, Iterable<? extends IHasName> items, String messageKey) {
    Map<String, Integer> nameCount = new HashMap<>();
    for (IHasName item : items) {
      if (item == null || Utils.isEmpty(item.getName())) {
        continue;
      }
      nameCount.merge(item.getName(), 1, Integer::sum);
    }
    for (Map.Entry<String, Integer> entry : nameCount.entrySet()) {
      if (entry.getValue() > 1) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, messageKey, entry.getKey()),
                null));
      }
    }
  }

  // -------------------------------------------------------------------------------------
  // IChanged implementation (like PipelineMeta / AbstractMeta)
  // -------------------------------------------------------------------------------------

  @Override
  public boolean hasChanged() {
    if (changedFlag.hasChanged()) {
      return true;
    }
    for (IDvTable table : getTables()) {
      if (table.hasChanged()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setChanged() {
    changedFlag.setChanged();
  }

  @Override
  public void setChanged(boolean ch) {
    changedFlag.setChanged(ch);
  }

  @Override
  public void clearChanged() {
    changedFlag.clearChanged();
    for (IDvTable table : getTables()) {
      table.clearChanged();
    }
  }

  /**
   * Find the Hub with the given name in the model
   *
   * @param hubName The name of the hub to look for.
   * @return The Hub or null if not found.
   */
  public DvHub findHub(String hubName) {
    return DvTableResolutionSupport.resolveHub(this, hubName, null, null);
  }

  public DvHub findHub(
      String hubName, IVariables variables, IHopMetadataProvider metadataProvider) {
    return DvTableResolutionSupport.resolveHub(this, hubName, variables, metadataProvider);
  }

  /**
   * Find the Link with the given name in the model
   *
   * @param linkName The name of the link to look for.
   * @return The Link or null if not found.
   */
  public DvLink findLink(String linkName) {
    return DvTableResolutionSupport.resolveLink(this, linkName, null, null);
  }

  public DvLink findLink(
      String linkName, IVariables variables, IHopMetadataProvider metadataProvider) {
    return DvTableResolutionSupport.resolveLink(this, linkName, variables, metadataProvider);
  }

  /**
   * Find the table with the given name in the model
   *
   * @param tableName The name of the table to look for.
   * @return The table or null if not found.
   */
  public IDvTable findTable(String tableName) {
    for (IDvTable table : getTables()) {
      if (table.getName().equalsIgnoreCase(tableName)) {
        return table;
      }
    }
    return null;
  }

  /**
   * Count the number of selected tables in the model.
   *
   * @return The number of selected tables
   */
  public int nrSelectedTables() {
    int nr = 0;
    for (IDvTable table : getTables()) {
      if (table.isSelected()) {
        nr++;
      }
    }
    return nr;
  }

  public Point getMaximum() {
    int maxx = 0;
    int maxy = 0;
    for (IDvTable table : getTables()) {
      Point loc = table.getLocation();
      if (loc == null) {
        continue;
      }
      if (loc.x > maxx) {
        maxx = loc.x;
      }
      if (loc.y > maxy) {
        maxy = loc.y;
      }
    }
    for (DvNote note : getNotes()) {
      Point loc = note.getLocation();
      if (loc == null) {
        continue;
      }
      int noteMaxX = loc.x + Math.max(0, note.getWidth());
      int noteMaxY = loc.y + Math.max(0, note.getHeight());
      if (noteMaxX > maxx) {
        maxx = noteMaxX;
      }
      if (noteMaxY > maxy) {
        maxy = noteMaxY;
      }
    }
    return new Point(maxx + 100, maxy + 100);
  }

  /** Count the number of selected notes on the canvas. */
  public int nrSelectedNotes() {
    int nr = 0;
    for (DvNote note : getNotes()) {
      if (note != null && note.isSelected()) {
        nr++;
      }
    }
    return nr;
  }

  /**
   * Collects all strings from this model that may contain variable references. Used by {@link
   * #getUsedVariables()} in the same way as {@link org.apache.hop.pipeline.PipelineMeta#getStringList(boolean,
   * boolean, boolean, boolean)} supports {@link org.apache.hop.pipeline.PipelineMeta#getUsedVariables()}.
   */
  public List<StringSearchResult> getStringList() {
    List<StringSearchResult> stringList = new ArrayList<>();

    try {
      String xml = XmlMetadataUtil.serializeObjectToXml(this);
      stringList.add(
          new StringSearchResult(
              xml,
              this,
              this,
              BaseMessages.getString(PKG, "DataVaultModel.StringList.ModelXml")));
    } catch (HopException e) {
      addStringListEntry(stringList, getDescription(), "description");
      DataVaultConfiguration config = getConfigurationOrDefault();
      addStringListEntry(stringList, config.getTargetTableBatchSize(), "targetTableBatchSize");
      addStringListEntry(
          stringList, config.getTargetTableParallelCopies(), "targetTableParallelCopies");
      addStringListEntry(stringList, config.getSortRowsSize(), "sortRowsSize");
      for (IDvTable table : getTables()) {
        if (table == null) {
          continue;
        }
        addStringListEntry(stringList, table.getName(), "tableName");
        addStringListEntry(stringList, table.getTableName(), "physicalTableName");
        addStringListEntry(stringList, table.getDescription(), "tableDescription");
      }
      for (DvNote note : getNotes()) {
        if (note == null) {
          continue;
        }
        addStringListEntry(stringList, note.getText(), "note");
      }
    }

    return stringList;
  }

  /**
   * Gets a list of the variables used in this model (for example {@code OUTPUT_COPIES} in {@link
   * DataVaultConfiguration#getTargetTableParallelCopies()}).
   *
   * @return a list of the used variables in this model.
   */
  public List<String> getUsedVariables() {
    List<StringSearchResult> stringList = getStringList();

    List<String> varList = new ArrayList<>();

    for (StringSearchResult result : stringList) {
      StringUtil.getUsedVariables(result.getString(), varList, false);
    }

    return varList;
  }

  private static void addStringListEntry(
      List<StringSearchResult> stringList, String value, String fieldName) {
    if (!Utils.isEmpty(value)) {
      stringList.add(new StringSearchResult(value, value, value, fieldName));
    }
  }

  // Undo/Redo is implemented in HopGuiVaultConfig with model snapshots.
  // We're not using the Hop Undo/Redo system

  @Override
  public void addUndo(
      Object[] from,
      Object[] to,
      int[] pos,
      Point[] prev,
      Point[] curr,
      int typeOfChange,
      boolean nextAlso) {
    // Implemented in HopGuiVaultConfig.  We're not using the Hop Undo/Redo system
  }

  @Override
  public int getMaxUndo() {
    return 0;
  }

  @Override
  public void setMaxUndo(int mu) {
    // Implemented in HopGuiVaultConfig.  We're not using the Hop Undo/Redo system
  }

  @Override
  public ChangeAction previousUndo() {
    return null;
  }

  @Override
  public ChangeAction viewThisUndo() {
    return null;
  }

  @Override
  public ChangeAction viewPreviousUndo() {
    return null;
  }

  @Override
  public ChangeAction nextUndo() {
    return null;
  }

  @Override
  public ChangeAction viewNextUndo() {
    return null;
  }
}
