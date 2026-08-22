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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.base.AbstractMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.core.ProgressNullMonitorListener;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.IUndo;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.undo.ChangeAction;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvTargetLoadModelCheckSupport;
import org.apache.hop.datavault.metadata.DvTargetUnicodeCapabilitySupport;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.datavault.metadata.coaching.ModelCoachingConfiguration;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.jspecify.annotations.NonNull;

/** A Business Vault model referencing one Data Vault model and owning BV derivative tables. */
@GuiPlugin
@Getter
@Setter
public class BusinessVaultModel extends HopMetadataBase
    implements IHopMetadata, org.apache.hop.core.changed.IChanged, IHasName, IHasFilename, IUndo {

  private static final Class<?> PKG = BusinessVaultModel.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "BUSINESS_VAULT_MODEL_DIALOG";

  /**
   * Runtime open path ({@link IHasFilename}), like {@code AbstractMeta}. Never serialized — loaders
   * bind this from the VFS path used to open/save.
   */
  private String filename;

  @HopMetadataProperty(key = "name_sync_with_filename")
  private boolean nameSynchronizedWithFilename = true;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessVaultModel.Description.Label",
      toolTip = "i18n::BusinessVaultModel.Description.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String description;

  /**
   * Optional legacy default {@code .hdv} path. Prefer canvas {@link BvDvTableReference} aliases
   * (with optional per-alias {@code referencedModelFilename}) for multi-model DV sources. Kept for
   * backward compatibility with older {@code .hbv} files; not required and not shown in the model
   * dialog.
   */
  @HopMetadataProperty private String dataVaultModelPath;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.METADATA,
      metadata = BusinessVaultConfiguration.class,
      label = "i18n::BusinessVaultModel.ConfigurationName.Label",
      toolTip = "i18n::BusinessVaultModel.ConfigurationName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String configurationName;

  @HopMetadataProperty(key = "configuration")
  private BusinessVaultConfiguration configuration;

  /** Runtime metadata provider for {@link #configurationName}. Never serialized. */
  private transient IHopMetadataProvider metadataProvider;

  @HopMetadataProperty private ModelCoachingConfiguration coaching;

  @HopMetadataProperty(key = "table", groupKey = "tables")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<IBvTable> tables = new ArrayList<>();

  @HopMetadataProperty(key = "note", groupKey = "notes")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<DvNote> notes = new ArrayList<>();

  @HopMetadataProperty(key = "dv_reference", groupKey = "dv_references")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<BvDvTableReference> dvReferences = new ArrayList<>();

  /**
   * Canvas aliases for tables defined in another Business Vault model ({@code .hbv}), used for
   * multi-step BV layers (historize → harmonize → rename).
   */
  @HopMetadataProperty(key = "bv_reference", groupKey = "bv_references")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<BvBvTableReference> bvReferences = new ArrayList<>();

  protected final ChangedFlag changedFlag = new ChangedFlag();

  public BusinessVaultModel() {
    super();
    ensureLists();
  }

  public BusinessVaultConfiguration getConfigurationOrDefault() {
    BusinessVaultConfiguration named =
        ModelConfigurationResolver.resolveNamed(
            configurationName, metadataProvider, BusinessVaultConfiguration.class);
    if (named != null) {
      return named;
    }
    if (configuration == null) {
      configuration = new BusinessVaultConfiguration();
    }
    return configuration;
  }

  public ModelCoachingConfiguration getCoachingOrDefault() {
    if (coaching == null) {
      coaching = ModelCoachingConfiguration.createEmpty();
    }
    return coaching;
  }

  public @NonNull List<IBvTable> getTables() {
    if (tables == null) {
      tables = new ArrayList<>();
    }
    return tables;
  }

  public void setTables(List<IBvTable> tables) {
    this.tables = tables != null ? tables : new ArrayList<>();
  }

  public @NonNull List<DvNote> getNotes() {
    if (notes == null) {
      notes = new ArrayList<>();
    }
    return notes;
  }

  public void setNotes(List<DvNote> notes) {
    this.notes = notes != null ? notes : new ArrayList<>();
  }

  public @NonNull List<BvDvTableReference> getDvReferences() {
    if (dvReferences == null) {
      dvReferences = new ArrayList<>();
    }
    return dvReferences;
  }

  public void setDvReferences(List<BvDvTableReference> dvReferences) {
    this.dvReferences = dvReferences != null ? dvReferences : new ArrayList<>();
  }

  public @NonNull List<BvBvTableReference> getBvReferences() {
    if (bvReferences == null) {
      bvReferences = new ArrayList<>();
    }
    return bvReferences;
  }

  public void setBvReferences(List<BvBvTableReference> bvReferences) {
    this.bvReferences = bvReferences != null ? bvReferences : new ArrayList<>();
  }

  private void ensureLists() {
    setTables(tables);
    setNotes(notes);
    setDvReferences(dvReferences);
    setBvReferences(bvReferences);
  }

  @Override
  public String getName() {
    return AbstractMeta.extractNameFromFilename(
        nameSynchronizedWithFilename, name, filename, ".hbv");
  }

  @Override
  public boolean hasChanged() {
    return changedFlag.hasChanged();
  }

  @Override
  public void setChanged() {
    changedFlag.setChanged();
  }

  @Override
  public void setChanged(boolean changed) {
    changedFlag.setChanged(changed);
  }

  @Override
  public void clearChanged() {
    changedFlag.setChanged(false);
  }

  public Point getMaximum() {
    int maxx = 0;
    int maxy = 0;
    for (IBvTable table : getTables()) {
      if (table instanceof BvTableBase base) {
        Point loc = base.getLocation();
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
    for (BvDvTableReference reference : getDvReferences()) {
      if (reference == null) {
        continue;
      }
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      int refMaxX = loc.x + Math.max(140, reference.getDrawnBoxWidth());
      int refMaxY = loc.y + Math.max(70, reference.getDrawnBoxHeight());
      if (refMaxX > maxx) {
        maxx = refMaxX;
      }
      if (refMaxY > maxy) {
        maxy = refMaxY;
      }
    }
    for (BvBvTableReference reference : getBvReferences()) {
      if (reference == null) {
        continue;
      }
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      int refMaxX = loc.x + Math.max(140, reference.getDrawnBoxWidth());
      int refMaxY = loc.y + Math.max(70, reference.getDrawnBoxHeight());
      if (refMaxX > maxx) {
        maxx = refMaxX;
      }
      if (refMaxY > maxy) {
        maxy = refMaxY;
      }
    }
    return new Point(maxx + 200, maxy + 200);
  }

  public IBvTable findTable(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return null;
    }
    for (IBvTable table : getTables()) {
      if (table != null && tableName.equals(table.getName())) {
        return table;
      }
    }
    return null;
  }

  public BvDvTableReference findDvReference(String dvTableName) {
    if (Utils.isEmpty(dvTableName)) {
      return null;
    }
    for (BvDvTableReference reference : getDvReferences()) {
      if (reference != null && dvTableName.equalsIgnoreCase(reference.getDvTableName())) {
        return reference;
      }
    }
    return null;
  }

  public BvBvTableReference findBvReference(String bvTableName) {
    if (Utils.isEmpty(bvTableName)) {
      return null;
    }
    for (BvBvTableReference reference : getBvReferences()) {
      if (reference != null && bvTableName.equalsIgnoreCase(reference.getBvTableName())) {
        return reference;
      }
    }
    return null;
  }

  public List<ICheckResult> check(IHopMetadataProvider metadataProvider, IVariables variables) {
    return check(metadataProvider, variables, null);
  }

  /**
   * Validate this model, reporting progress per table when {@code monitor} is provided.
   * Cancellation stops further table checks; remarks collected so far are still returned.
   */
  public List<ICheckResult> check(
      IHopMetadataProvider metadataProvider, IVariables variables, IProgressMonitor monitor) {
    if (monitor == null) {
      monitor = new ProgressNullMonitorListener();
    }

    List<ICheckResult> remarks = new ArrayList<>();
    List<IBvTable> tables = getTables();
    monitor.beginTask(
        BaseMessages.getString(PKG, "BusinessVaultModel.Monitor.VerifyingModel"), tables.size());
    ModelConfigurationResolver.attach(this, metadataProvider);
    ModelConfigurationResolver.checkNamedConfiguration(
        remarks,
        configurationName,
        configuration,
        metadataProvider,
        BusinessVaultConfiguration.class);

    // DV tables come from canvas Hub/Link/Satellite references (multi-model capable).
    // Optional legacy dataVaultModelPath is only a default when an alias has no path.
    DataVaultModel dataVaultModel = null;
    try {
      dataVaultModel =
          BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
              this, variables, metadataProvider);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), null));
      // Continue with null model so table-level checks still report structure issues.
    }

    if (tables.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "BusinessVaultModel.CheckResult.NoTables"),
              null));
    }

    Set<String> names = new HashSet<>();
    Set<String> targetNames = new HashSet<>();
    for (IBvTable table : tables) {
      if (monitor.isCanceled()) {
        break;
      }
      if (table == null) {
        monitor.worked(1);
        continue;
      }
      String tableLabel = Const.NVL(table.getName(), "?");
      monitor.subTask(
          BaseMessages.getString(PKG, "BusinessVaultModel.Monitor.VerifyingTable", tableLabel));
      if (!Utils.isEmpty(table.getName()) && !names.add(table.getName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "BusinessVaultModel.CheckResult.DuplicateName", table.getName()),
                table));
      }
      if (!Utils.isEmpty(table.getTableName()) && !targetNames.add(table.getTableName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BusinessVaultModel.CheckResult.DuplicateTargetTable",
                    table.getTableName()),
                table));
      }
      table.check(remarks, metadataProvider, variables, this, dataVaultModel);
      monitor.worked(1);
    }

    if (monitor.isCanceled()) {
      monitor.done();
      return remarks;
    }

    // SQL business-table dependency graph (cycles reported once for the model).
    BvSqlValidationSupport.validateModelSqlGraph(remarks, this);

    BusinessVaultConfiguration config = getConfigurationOrDefault();
    org.apache.hop.core.database.DatabaseMeta targetDatabase = null;
    try {
      targetDatabase = BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
    } catch (HopException e) {
      // Target database validation is reported on individual tables.
    }
    if (targetDatabase != null) {
      DvTargetUnicodeCapabilitySupport.checkTargetUnicodeCapability(
          remarks, targetDatabase, variables, config.getTargetDatabase());
    }
    DvTargetLoadModelCheckSupport.checkTargetLoadMode(remarks, config, targetDatabase);
    DvTargetLoadModelCheckSupport.checkTargetLoadModeGuidance(
        remarks, config, targetDatabase, variables);
    DvTargetLoadModelCheckSupport.checkTargetLoadingIntegerSettings(
        remarks,
        config.getTargetTableBatchSize(),
        config.getTargetTableParallelCopies(),
        variables,
        BusinessVaultConfiguration.DEFAULT_TARGET_TABLE_BATCH_SIZE,
        BusinessVaultConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES);

    for (BvDvTableReference reference : getDvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getDvTableName())) {
        continue;
      }
      String modelPath = reference.getReferencedModelFilename();
      if (Utils.isEmpty(modelPath)) {
        modelPath = dataVaultModelPath;
      }
      if (Utils.isEmpty(modelPath)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BusinessVaultModel.CheckResult.DvReferenceMissingModelPath",
                    reference.getDvTableName()),
                null));
        continue;
      }
      try {
        IDvTable dvTable =
            BusinessVaultDvModelResolver.resolveDvTable(
                this, reference.getDvTableName(), variables, metadataProvider);
        if (dvTable == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BusinessVaultModel.CheckResult.UnknownDvReference",
                      reference.getDvTableName()),
                  null));
          continue;
        }
        if (reference.getDvTableType() != null
            && dvTable.getTableType() != reference.getDvTableType()) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BusinessVaultModel.CheckResult.DvReferenceTypeMismatch",
                      reference.getDvTableName(),
                      reference.getDvTableType(),
                      dvTable.getTableType()),
                  null));
        }
      } catch (HopException e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BusinessVaultModel.CheckResult.DvReferenceLoadFailed",
                    reference.getDvTableName(),
                    modelPath,
                    e.getMessage()),
                null));
      }
    }

    for (BvBvTableReference reference : getBvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getBvTableName())) {
        continue;
      }
      if (Utils.isEmpty(reference.getReferencedModelFilename())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BusinessVaultModel.CheckResult.MissingBvReferenceModelPath",
                    reference.getBvTableName()),
                null));
        continue;
      }
      try {
        BusinessVaultModel external =
            BvSqlModelPathSupport.loadBusinessVaultModelUncached(
                variables != null
                    ? variables.resolve(reference.getReferencedModelFilename())
                    : reference.getReferencedModelFilename(),
                metadataProvider);
        IBvTable bvTable = external.findTable(reference.getBvTableName());
        if (bvTable == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BusinessVaultModel.CheckResult.UnknownBvReference",
                      reference.getBvTableName(),
                      reference.getReferencedModelFilename()),
                  null));
        } else if (reference.getBvTableType() != null
            && bvTable.getTableType() != reference.getBvTableType()) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BusinessVaultModel.CheckResult.BvReferenceTypeMismatch",
                      reference.getBvTableName(),
                      reference.getBvTableType(),
                      bvTable.getTableType()),
                  null));
        }
      } catch (Exception e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BusinessVaultModel.CheckResult.BvReferenceLoadFailed",
                    reference.getBvTableName(),
                    reference.getReferencedModelFilename(),
                    e.getMessage()),
                null));
      }
    }

    boolean hasErrors =
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
    if (!hasErrors) {
      remarks.add(
          0,
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "BusinessVaultModel.CheckResult.Ok"),
              null));
    }
    monitor.done();
    return remarks;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BusinessVaultModel that)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(getName(), that.getName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getName());
  }

  // Undo/redo is implemented in HopGuiBusinessVaultGraph with model snapshots.

  @Override
  public void addUndo(
      Object[] from,
      Object[] to,
      int[] pos,
      Point[] prev,
      Point[] curr,
      int typeOfChange,
      boolean nextAlso) {
    // not used
  }

  @Override
  public int getMaxUndo() {
    return 0;
  }

  @Override
  public void setMaxUndo(int mu) {
    // not used
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
