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
package org.apache.hop.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.base.AbstractMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.changed.ChangedFlag;
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
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.jspecify.annotations.NonNull;

/**
 * Source-system model ({@code .hsm}): physical tables, PK/FK relationships, and multi-table source
 * queries that feed Data Vault hubs, links, and satellites.
 */
@GuiPlugin
@Getter
@Setter
public class SourceModel extends HopMetadataBase
    implements IHopMetadata, org.apache.hop.core.changed.IChanged, IHasName, IHasFilename, IUndo {

  private static final Class<?> PKG = SourceModel.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "SOURCE_MODEL_DIALOG";
  public static final String FILE_EXTENSION = ".hsm";

  /** Root XML element name for {@code .hsm} documents. */
  public static final String XML_TAG = "source-model";

  /**
   * Runtime open path ({@link IHasFilename}). Never serialized — loaders bind this from the VFS
   * path used to open/save.
   */
  private String filename;

  @HopMetadataProperty(key = "name_sync_with_filename")
  private boolean nameSynchronizedWithFilename = true;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::SourceModel.Description.Label",
      toolTip = "i18n::SourceModel.Description.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String description;

  @HopMetadataProperty(key = "configuration")
  private SourceModelConfiguration configuration;

  @HopMetadataProperty(key = "table", groupKey = "tables")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceTable> tables = new ArrayList<>();

  @HopMetadataProperty(key = "relationship", groupKey = "relationships")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceRelationship> relationships = new ArrayList<>();

  @HopMetadataProperty(key = "query", groupKey = "queries")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceQuery> queries = new ArrayList<>();

  @HopMetadataProperty(key = "note", groupKey = "notes")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<DvNote> notes = new ArrayList<>();

  protected final ChangedFlag changedFlag = new ChangedFlag();

  public SourceModel() {
    super();
    ensureLists();
  }

  public SourceModelConfiguration getConfigurationOrDefault() {
    if (configuration == null) {
      configuration = SourceModelConfiguration.createDefault();
    }
    return configuration;
  }

  public @NonNull List<SourceTable> getTables() {
    if (tables == null) {
      tables = new ArrayList<>();
    }
    return tables;
  }

  public void setTables(List<SourceTable> tables) {
    this.tables = tables != null ? tables : new ArrayList<>();
  }

  public @NonNull List<SourceRelationship> getRelationships() {
    if (relationships == null) {
      relationships = new ArrayList<>();
    }
    return relationships;
  }

  public void setRelationships(List<SourceRelationship> relationships) {
    this.relationships = relationships != null ? relationships : new ArrayList<>();
  }

  public @NonNull List<SourceQuery> getQueries() {
    if (queries == null) {
      queries = new ArrayList<>();
    }
    return queries;
  }

  public void setQueries(List<SourceQuery> queries) {
    this.queries = queries != null ? queries : new ArrayList<>();
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

  private void ensureLists() {
    setTables(tables);
    setRelationships(relationships);
    setQueries(queries);
    setNotes(notes);
  }

  @Override
  public String getName() {
    return AbstractMeta.extractNameFromFilename(
        nameSynchronizedWithFilename, name, filename, FILE_EXTENSION);
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
    for (SourceTable table : getTables()) {
      if (table == null) {
        continue;
      }
      Point loc = table.getLocation();
      if (loc == null) {
        continue;
      }
      int boxW = Math.max(140, table.getDrawnBoxWidth());
      int boxH = Math.max(70, table.getDrawnBoxHeight());
      if (loc.x + boxW > maxx) {
        maxx = loc.x + boxW;
      }
      if (loc.y + boxH > maxy) {
        maxy = loc.y + boxH;
      }
    }
    for (SourceQuery query : getQueries()) {
      if (query == null) {
        continue;
      }
      Point loc = query.getLocation();
      if (loc == null) {
        continue;
      }
      int boxW = 160;
      int boxH = 80;
      if (loc.x + boxW > maxx) {
        maxx = loc.x + boxW;
      }
      if (loc.y + boxH > maxy) {
        maxy = loc.y + boxH;
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

  public SourceTable findTable(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return null;
    }
    for (SourceTable table : getTables()) {
      if (table != null && tableName.equals(table.getName())) {
        return table;
      }
    }
    return null;
  }

  public SourceRelationship findRelationship(String relationshipName) {
    if (Utils.isEmpty(relationshipName)) {
      return null;
    }
    for (SourceRelationship relationship : getRelationships()) {
      if (relationship != null && relationshipName.equals(relationship.getName())) {
        return relationship;
      }
    }
    return null;
  }

  public SourceQuery findQuery(String queryName) {
    if (Utils.isEmpty(queryName)) {
      return null;
    }
    for (SourceQuery query : getQueries()) {
      if (query != null && queryName.equals(query.getName())) {
        return query;
      }
    }
    return null;
  }

  /**
   * Structural validation for the source model (tables, relationships, queries). Expanded in later
   * PRs for generation-mode and catalog-publish checks.
   */
  public List<ICheckResult> check(IHopMetadataProvider metadataProvider, IVariables variables) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (getTables().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.NoTables"),
              null));
    }

    Set<String> tableNames = new HashSet<>();
    for (SourceTable table : getTables()) {
      if (table == null) {
        continue;
      }
      String tableName = table.getName();
      if (Utils.isEmpty(tableName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "SourceModel.CheckResult.TableMissingName"),
                null));
        continue;
      }
      if (!tableNames.add(tableName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.DuplicateTableName", tableName),
                null));
      }
    }

    Set<String> relationshipNames = new HashSet<>();
    for (SourceRelationship relationship : getRelationships()) {
      if (relationship == null) {
        continue;
      }
      String relName = relationship.getName();
      if (!Utils.isEmpty(relName) && !relationshipNames.add(relName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.DuplicateRelationshipName", relName),
                null));
      }
      if (findTable(relationship.getChildTableName()) == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.RelationshipMissingChild",
                    ConstNvl(relName),
                    ConstNvl(relationship.getChildTableName())),
                null));
      }
      if (findTable(relationship.getParentTableName()) == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.RelationshipMissingParent",
                    ConstNvl(relName),
                    ConstNvl(relationship.getParentTableName())),
                null));
      }
      if (!relationship.isValid()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.RelationshipInvalidColumns", ConstNvl(relName)),
                null));
      }
    }

    Set<String> queryNames = new HashSet<>();
    for (SourceQuery query : getQueries()) {
      if (query == null) {
        continue;
      }
      String queryName = query.getName();
      if (!Utils.isEmpty(queryName) && !queryNames.add(queryName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.DuplicateQueryName", queryName),
                null));
      }
      if (findTable(query.getDrivingTableName()) == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.QueryMissingDrivingTable",
                    ConstNvl(queryName),
                    ConstNvl(query.getDrivingTableName())),
                null));
      }
      if (query.getColumns().isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.QueryEmptyProjection", ConstNvl(queryName)),
                null));
      }
      Set<Integer> keyPositions = new HashSet<>();
      boolean hasLogicalKey = false;
      for (SourceQueryColumn column : query.getColumns()) {
        if (column == null || !column.isPrimaryKey()) {
          continue;
        }
        hasLogicalKey = true;
        int position = column.getPrimaryKeyPosition();
        if (!keyPositions.add(position)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.QueryDuplicateKeyPosition",
                      ConstNvl(queryName),
                      Integer.toString(position)),
                  null));
        }
      }
      if (!query.getColumns().isEmpty() && !hasLogicalKey) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.QueryMissingLogicalKey", ConstNvl(queryName)),
                null));
      }
      for (SourceQueryJoin join : query.getJoins()) {
        if (join == null) {
          continue;
        }
        if (findTable(join.getTableName()) == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.QueryJoinMissingTable",
                      ConstNvl(queryName),
                      ConstNvl(join.getTableName())),
                  null));
        }
      }
    }

    if (remarks.stream().noneMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.Ok"),
              null));
    }
    return remarks;
  }

  private static String ConstNvl(String value) {
    return value == null || value.isBlank() ? "?" : value;
  }

  // Undo/redo is implemented in HopGuiSourceModelGraph with model snapshots.

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
