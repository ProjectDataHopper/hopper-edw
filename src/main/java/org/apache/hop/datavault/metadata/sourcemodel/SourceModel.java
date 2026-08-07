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
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.core.ProgressNullMonitorListener;
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
 * Source-system model ({@code .hsm}): physical tables, PK/FK relationships, multi-table source
 * queries, and JSON extractions that feed Data Vault hubs, links, and satellites.
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

  @HopMetadataProperty(key = "json-source", groupKey = "json-sources")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceJson> jsonSources = new ArrayList<>();

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

  public @NonNull List<SourceJson> getJsonSources() {
    if (jsonSources == null) {
      jsonSources = new ArrayList<>();
    }
    return jsonSources;
  }

  public void setJsonSources(List<SourceJson> jsonSources) {
    this.jsonSources = jsonSources != null ? jsonSources : new ArrayList<>();
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
    setJsonSources(jsonSources);
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
    for (SourceJson jsonSource : getJsonSources()) {
      if (jsonSource == null) {
        continue;
      }
      Point loc = jsonSource.getLocation();
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

  public SourceJson findJsonSource(String jsonSourceName) {
    if (Utils.isEmpty(jsonSourceName)) {
      return null;
    }
    for (SourceJson jsonSource : getJsonSources()) {
      if (jsonSource != null && jsonSourceName.equals(jsonSource.getName())) {
        return jsonSource;
      }
    }
    return null;
  }

  /**
   * Structural validation for the source model (tables, relationships, queries, JSON sources).
   * Expanded in later PRs for generation-mode and catalog-publish checks.
   */
  public List<ICheckResult> check(IHopMetadataProvider metadataProvider, IVariables variables) {
    return check(metadataProvider, variables, null);
  }

  /**
   * Structural validation with optional progress reporting. Cancellation stops further element
   * checks; remarks collected so far are still returned.
   */
  public List<ICheckResult> check(
      IHopMetadataProvider metadataProvider, IVariables variables, IProgressMonitor monitor) {
    if (monitor == null) {
      monitor = new ProgressNullMonitorListener();
    }

    List<ICheckResult> remarks = new ArrayList<>();
    List<SourceTable> tables = getTables();
    List<SourceRelationship> relationships = getRelationships();
    List<SourceQuery> queries = getQueries();
    List<SourceJson> jsonSources = getJsonSources();
    int totalWork = tables.size() + relationships.size() + queries.size() + jsonSources.size();
    monitor.beginTask(BaseMessages.getString(PKG, "SourceModel.Monitor.VerifyingModel"), totalWork);

    if (tables.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.NoTables"),
              null));
    }

    Set<String> tableNames = new HashSet<>();
    for (SourceTable table : tables) {
      if (monitor.isCanceled()) {
        monitor.done();
        return remarks;
      }
      if (table == null) {
        monitor.worked(1);
        continue;
      }
      String tableName = table.getName();
      monitor.subTask(
          BaseMessages.getString(PKG, "SourceModel.Monitor.VerifyingTable", ConstNvl(tableName)));
      if (Utils.isEmpty(tableName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "SourceModel.CheckResult.TableMissingName"),
                null));
        monitor.worked(1);
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
      monitor.worked(1);
    }

    Set<String> relationshipNames = new HashSet<>();
    for (SourceRelationship relationship : relationships) {
      if (monitor.isCanceled()) {
        monitor.done();
        return remarks;
      }
      if (relationship == null) {
        monitor.worked(1);
        continue;
      }
      String relName = relationship.getName();
      monitor.subTask(
          BaseMessages.getString(
              PKG, "SourceModel.Monitor.VerifyingRelationship", ConstNvl(relName)));
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
      monitor.worked(1);
    }

    Set<String> queryNames = new HashSet<>();
    for (SourceQuery query : queries) {
      if (monitor.isCanceled()) {
        monitor.done();
        return remarks;
      }
      if (query == null) {
        monitor.worked(1);
        continue;
      }
      String queryName = query.getName();
      monitor.subTask(
          BaseMessages.getString(PKG, "SourceModel.Monitor.VerifyingQuery", ConstNvl(queryName)));
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
      monitor.worked(1);
    }

    Set<String> jsonSourceNames = new HashSet<>();
    for (SourceJson jsonSource : jsonSources) {
      if (monitor.isCanceled()) {
        monitor.done();
        return remarks;
      }
      if (jsonSource == null) {
        monitor.worked(1);
        continue;
      }
      String jsonName = jsonSource.getName();
      monitor.subTask(
          BaseMessages.getString(
              PKG, "SourceModel.Monitor.VerifyingJsonSource", ConstNvl(jsonName)));
      if (!Utils.isEmpty(jsonName) && !jsonSourceNames.add(jsonName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.DuplicateJsonSourceName", jsonName),
                null));
      }
      SourceJsonParentKind parentKind = jsonSource.resolveParentSourceKind();
      String parentName = jsonSource.getParentSourceName();
      if (Utils.isEmpty(parentName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.JsonSourceMissingParent", ConstNvl(jsonName)),
                null));
      } else if (!parentExists(parentKind, parentName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.JsonSourceParentNotFound",
                    ConstNvl(jsonName),
                    parentKind != null ? parentKind.getCode() : "?",
                    ConstNvl(parentName)),
                null));
      }
      if (Utils.isEmpty(jsonSource.getJsonFieldName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.JsonSourceMissingJsonField", ConstNvl(jsonName)),
                null));
      }
      if (jsonSource.getFields().isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.JsonSourceEmptyProjection", ConstNvl(jsonName)),
                null));
      }
      // Distinct array base paths: multiple fields may expand the same array (allowed by
      // JsonInput); different array bases are not.
      Set<String> arrayBases = new HashSet<>();
      for (SourceJsonField field : jsonSource.getFields()) {
        if (field != null && field.isArrayExpandingPath()) {
          arrayBases.add(arrayBasePath(field.getPath()));
        }
      }
      if (arrayBases.size() > 1) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.JsonSourceMultipleArrays",
                    ConstNvl(jsonName),
                    Integer.toString(arrayBases.size())),
                null));
      }
      Set<Integer> keyPositions = new HashSet<>();
      boolean hasLogicalKey = false;
      for (SourceJsonField field : jsonSource.getFields()) {
        if (field == null || !field.isPrimaryKey()) {
          continue;
        }
        hasLogicalKey = true;
        int position = field.getPrimaryKeyPosition();
        if (!keyPositions.add(position)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.JsonSourceDuplicateKeyPosition",
                      ConstNvl(jsonName),
                      Integer.toString(position)),
                  null));
        }
      }
      if (!jsonSource.getFields().isEmpty() && !hasLogicalKey) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.JsonSourceMissingLogicalKey", ConstNvl(jsonName)),
                null));
      }
      if (parentKind == SourceJsonParentKind.JSON
          && !Utils.isEmpty(parentName)
          && hasJsonParentCycle(jsonName, parentName, new HashSet<>())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.JsonSourceParentCycle", ConstNvl(jsonName)),
                null));
      }
      monitor.worked(1);
    }

    if (!monitor.isCanceled()
        && remarks.stream().noneMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.Ok"),
              null));
    }
    monitor.done();
    return remarks;
  }

  private boolean parentExists(SourceJsonParentKind parentKind, String parentName) {
    if (parentKind == null || Utils.isEmpty(parentName)) {
      return false;
    }
    return switch (parentKind) {
      case TABLE -> findTable(parentName) != null;
      case QUERY -> findQuery(parentName) != null;
      case JSON -> findJsonSource(parentName) != null;
    };
  }

  /**
   * Walks JSON→JSON parent links looking for a cycle involving {@code startName}.
   *
   * @param startName name of the JSON source being validated
   * @param currentParent parent name currently under inspection
   * @param visited names already seen on this walk
   */
  private boolean hasJsonParentCycle(String startName, String currentParent, Set<String> visited) {
    if (Utils.isEmpty(currentParent)) {
      return false;
    }
    if (currentParent.equals(startName)) {
      return true;
    }
    if (!visited.add(currentParent)) {
      return true;
    }
    SourceJson parent = findJsonSource(currentParent);
    if (parent == null || parent.resolveParentSourceKind() != SourceJsonParentKind.JSON) {
      return false;
    }
    return hasJsonParentCycle(startName, parent.getParentSourceName(), visited);
  }

  /**
   * Extracts the array base (path up to and including the first array wildcard) so multiple fields
   * under the same array are treated as one expansion.
   *
   * <p>Supports JsonPath {@code [*]} and Hop sample style {@code .*}.
   */
  public static String arrayBasePath(String path) {
    if (path == null) {
      return "";
    }
    String p = path.trim();
    int bracket = p.indexOf("[*]");
    if (bracket >= 0) {
      return p.substring(0, bracket + 3);
    }
    int star = p.indexOf(".*");
    if (star >= 0) {
      return p.substring(0, star + 2);
    }
    return p;
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
