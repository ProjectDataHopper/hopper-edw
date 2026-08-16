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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvTargetLoadModelCheckSupport;
import org.apache.hop.datavault.metadata.DvTargetUnicodeCapabilitySupport;
import org.apache.hop.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.datavault.metadata.coaching.ModelCoachingConfiguration;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.jspecify.annotations.NonNull;

/** Kimball dimensional model (star/snowflake scaffold). */
@GuiPlugin
@Getter
@Setter
public class DimensionalModel extends HopMetadataBase
    implements IHopMetadata, org.apache.hop.core.changed.IChanged, IHasName, IHasFilename, IUndo {

  private static final Class<?> PKG = DimensionalModel.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DIMENSIONAL_MODEL_DIALOG";

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
      label = "i18n::DimensionalModel.Description.Label",
      toolTip = "i18n::DimensionalModel.Description.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String description;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.METADATA,
      metadata = DimensionalConfiguration.class,
      label = "i18n::DimensionalModel.ConfigurationName.Label",
      toolTip = "i18n::DimensionalModel.ConfigurationName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String configurationName;

  @HopMetadataProperty(key = "configuration")
  private DimensionalConfiguration configuration;

  /** Runtime metadata provider for {@link #configurationName}. Never serialized. */
  private transient IHopMetadataProvider metadataProvider;

  @HopMetadataProperty private ModelCoachingConfiguration coaching;

  @HopMetadataProperty(key = "table", groupKey = "tables")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<IDmTable> tables = new ArrayList<>();

  @HopMetadataProperty(key = "conformed_dimension", groupKey = "conformed_dimensions")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<DmConformedDimensionRef> conformedDimensions = new ArrayList<>();

  @HopMetadataProperty(key = "note", groupKey = "notes")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<DvNote> notes = new ArrayList<>();

  protected final ChangedFlag changedFlag = new ChangedFlag();

  public DimensionalModel() {
    super();
    ensureLists();
  }

  public DimensionalConfiguration getConfigurationOrDefault() {
    DimensionalConfiguration named =
        ModelConfigurationResolver.resolveNamed(
            configurationName, metadataProvider, DimensionalConfiguration.class);
    if (named != null) {
      return named;
    }
    if (configuration == null) {
      configuration = DimensionalConfiguration.createFromPluginDefaults();
    }
    return configuration;
  }

  public ModelCoachingConfiguration getCoachingOrDefault() {
    if (coaching == null) {
      coaching = ModelCoachingConfiguration.createEmpty();
    }
    return coaching;
  }

  public @NonNull List<IDmTable> getTables() {
    if (tables == null) {
      tables = new ArrayList<>();
    }
    return tables;
  }

  public void setTables(List<IDmTable> tables) {
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

  public @NonNull List<DmConformedDimensionRef> getConformedDimensions() {
    if (conformedDimensions == null) {
      conformedDimensions = new ArrayList<>();
    }
    return conformedDimensions;
  }

  public void setConformedDimensions(List<DmConformedDimensionRef> conformedDimensions) {
    this.conformedDimensions =
        conformedDimensions != null ? conformedDimensions : new ArrayList<>();
  }

  public @NonNull List<DmConformedDimensionRef> getConformedDimensionsOrEmpty() {
    return getConformedDimensions();
  }

  public DmDimension findConformedDimension(String logicalName) {
    if (Utils.isEmpty(logicalName)) {
      return null;
    }
    for (DmConformedDimensionRef ref : getConformedDimensionsOrEmpty()) {
      if (ref == null || !logicalName.equals(ref.getLogicalName())) {
        continue;
      }
      return DmDimensionResolutionSupport.resolveDimension(this, ref.getDimensionTableName());
    }
    IDmTable aliasTable = findTable(logicalName);
    if (aliasTable instanceof DmDimensionAlias alias) {
      return DmDimensionResolutionSupport.resolveAliasTarget(this, alias, null);
    }
    return null;
  }

  /** Removes conformed dimension registry entries that point at a deleted physical table. */
  public void removeConformedDimensionRefsForTable(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return;
    }
    getConformedDimensions()
        .removeIf(ref -> ref != null && tableName.equals(ref.getDimensionTableName()));
  }

  public void removeConformedDimensionRefsForTables(Iterable<? extends IDmTable> tables) {
    if (tables == null) {
      return;
    }
    for (IDmTable table : tables) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        removeConformedDimensionRefsForTable(table.getName());
      }
    }
  }

  private void ensureLists() {
    setTables(tables);
    setConformedDimensions(conformedDimensions);
    setNotes(notes);
  }

  @Override
  public String getName() {
    return AbstractMeta.extractNameFromFilename(
        nameSynchronizedWithFilename, name, filename, ".hdm");
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
    for (IDmTable table : getTables()) {
      if (table instanceof DmTableBase base) {
        Point loc = base.getLocation();
        if (loc == null) {
          continue;
        }
        int boxW = Math.max(140, base.getDrawnBoxWidth());
        int boxH = Math.max(70, base.getDrawnBoxHeight());
        if (loc.x + boxW > maxx) {
          maxx = loc.x + boxW;
        }
        if (loc.y + boxH > maxy) {
          maxy = loc.y + boxH;
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
    return new Point(maxx + 200, maxy + 200);
  }

  public IDmTable findTable(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return null;
    }
    for (IDmTable table : getTables()) {
      if (table != null && tableName.equals(table.getName())) {
        return table;
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
    List<IDmTable> tables = getTables();
    monitor.beginTask(
        BaseMessages.getString(PKG, "DimensionalModel.Monitor.VerifyingModel"), tables.size());
    ModelConfigurationResolver.attach(this, metadataProvider);
    ModelConfigurationResolver.checkNamedConfiguration(
        remarks,
        configurationName,
        configuration,
        metadataProvider,
        DimensionalConfiguration.class);

    DmValidationSupport.validateConfiguration(remarks, this, metadataProvider, variables);
    DimensionalConfiguration config = getConfigurationOrDefault();
    org.apache.hop.core.database.DatabaseMeta targetDatabase = null;
    try {
      targetDatabase = DmTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
    } catch (HopException e) {
      // Target database validation is reported in DmValidationSupport.
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
        DimensionalConfiguration.DEFAULT_TARGET_TABLE_BATCH_SIZE,
        DimensionalConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES);
    if (tables.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DimensionalModel.CheckResult.NoTables"),
              null));
    }
    Set<String> names = new HashSet<>();
    for (IDmTable table : tables) {
      if (monitor.isCanceled()) {
        break;
      }
      if (table == null) {
        monitor.worked(1);
        continue;
      }
      String name = table.getName();
      monitor.subTask(
          BaseMessages.getString(
              PKG, "DimensionalModel.Monitor.VerifyingTable", Const.NVL(name, "?")));
      if (!Utils.isEmpty(name)) {
        if (!names.add(name)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DimensionalModel.CheckResult.DuplicateName", name),
                  table));
        }
      }
      table.check(remarks, metadataProvider, variables, this);
      monitor.worked(1);
    }
    if (!monitor.isCanceled()
        && remarks.stream().noneMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DimensionalModel.CheckResult.Ok"),
              null));
    }
    monitor.done();
    return remarks;
  }

  // Undo/redo is implemented in HopGuiDimensionalModelGraph with model snapshots.

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
